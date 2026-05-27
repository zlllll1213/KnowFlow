package retriever

import (
	"context"
	"database/sql"
	"fmt"
	"log"
	"regexp"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/knowflow/rag-go/internal/config"
	"github.com/knowflow/rag-go/internal/types"
	"github.com/lib/pq"
)

type PgRetriever struct {
	db *sql.DB
}

type CallLog struct {
	KbID              int64
	UserID            int64
	SessionID         int64
	Mode              string
	Intent            string
	QuestionSummary   string
	TopK              int
	SourceCount       int
	RetrieveLatencyMs int64
	LLMLatencyMs      int64
	TotalLatencyMs    int64
	Confidence        float64
	TraceJSON         string
}

func New(cfg *config.Config) (*PgRetriever, error) {
	db, err := sql.Open("postgres", cfg.DBDSN)
	if err != nil {
		return nil, fmt.Errorf("连接数据库失败: %w", err)
	}
	if err := db.Ping(); err != nil {
		return nil, fmt.Errorf("数据库 ping 失败: %w", err)
	}
	db.SetMaxOpenConns(10)
	db.SetMaxIdleConns(5)
	db.SetConnMaxLifetime(30 * time.Minute)
	log.Println("PostgreSQL 连接成功")
	return &PgRetriever{db: db}, nil
}

// Retrieve 从 document_chunk 检索与 query 相关的 TopK 个片段。
// 当 queryEmbedding 可用时使用 pgvector 相似度检索，否则退回关键词检索。
func (r *PgRetriever) Retrieve(ctx context.Context, kbId int64, query string, queryEmbedding []float32, topK int) ([]types.SourceChunk, error) {
	if topK <= 0 {
		topK = 5
	}
	if len(queryEmbedding) > 0 {
		sources, err := r.retrieveByVector(ctx, kbId, queryEmbedding, topK)
		if err == nil {
			log.Printf("向量检索完成: kbId=%d, topK=%d, found=%d", kbId, topK, len(sources))
			return sources, nil
		}
		log.Printf("向量检索失败，回退关键词检索: %v", err)
	}

	terms := keywordTerms(query)
	rows, err := r.db.QueryContext(ctx, `
		WITH terms AS (
			SELECT unnest($2::text[]) AS term
		)
		SELECT dc.id,
		       dc.document_id,
		       dc.chunk_index,
		       dc.content,
		       d.file_name,
		       COUNT(t.term) AS matched_terms
		FROM document_chunk dc
		JOIN document d ON d.id = dc.document_id
		JOIN terms t ON dc.content ILIKE '%' || t.term || '%'
		WHERE dc.kb_id = $1
		  AND d.is_deleted = 0
		GROUP BY dc.id, dc.document_id, dc.chunk_index, dc.content, d.file_name
		ORDER BY matched_terms DESC, dc.chunk_index
		LIMIT $3
	`, kbId, pq.Array(terms), topK)

	if err != nil {
		return nil, fmt.Errorf("检索失败: %w", err)
	}
	defer rows.Close()

	sources := make([]types.SourceChunk, 0)
	for rows.Next() {
		var s types.SourceChunk
		var matchedTerms int
		if err := rows.Scan(&s.ChunkId, &s.DocumentId, &s.ChunkIndex, &s.Content, &s.FileName, &matchedTerms); err != nil {
			return nil, fmt.Errorf("扫描行失败: %w", err)
		}
		s.Score = 0.4 + 0.1*float64(matchedTerms)
		if s.Score > 0.9 {
			s.Score = 0.9
		}
		sources = append(sources, s)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("读取检索结果失败: %w", err)
	}

	log.Printf("关键词检索完成: kbId=%d, topK=%d, terms=%v, found=%d", kbId, topK, terms, len(sources))
	return sources, nil
}

func (r *PgRetriever) retrieveByVector(ctx context.Context, kbId int64, queryEmbedding []float32, topK int) ([]types.SourceChunk, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT dc.id,
		       dc.document_id,
		       dc.chunk_index,
		       dc.content,
		       d.file_name,
		       1 - (dc.embedding <=> $2::vector) AS score
		FROM document_chunk dc
		JOIN document d ON d.id = dc.document_id
		WHERE dc.kb_id = $1
		  AND d.is_deleted = 0
		  AND dc.embedding IS NOT NULL
		ORDER BY dc.embedding <=> $2::vector
		LIMIT $3
	`, kbId, vectorLiteral(queryEmbedding), topK)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	sources := make([]types.SourceChunk, 0)
	for rows.Next() {
		var s types.SourceChunk
		if err := rows.Scan(&s.ChunkId, &s.DocumentId, &s.ChunkIndex, &s.Content, &s.FileName, &s.Score); err != nil {
			return nil, err
		}
		sources = append(sources, s)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	return sources, nil
}

func vectorLiteral(values []float32) string {
	var b strings.Builder
	b.WriteByte('[')
	for i, v := range values {
		if i > 0 {
			b.WriteByte(',')
		}
		b.WriteString(fmt.Sprintf("%g", v))
	}
	b.WriteByte(']')
	return b.String()
}

var keywordPattern = regexp.MustCompile(`[A-Za-z0-9][A-Za-z0-9_-]*|[\p{Han}]+`)

func keywordTerms(query string) []string {
	seen := make(map[string]struct{})
	terms := make([]string, 0)

	add := func(term string) {
		term = strings.TrimSpace(term)
		if term == "" || utf8.RuneCountInString(term) < 2 {
			return
		}
		if utf8.RuneCountInString(term) > 64 {
			runes := []rune(term)
			term = string(runes[:64])
		}
		key := strings.ToLower(term)
		if _, ok := seen[key]; ok {
			return
		}
		seen[key] = struct{}{}
		terms = append(terms, term)
	}

	for _, match := range keywordPattern.FindAllString(query, -1) {
		add(match)
		if isHanRun(match) {
			runes := []rune(match)
			for i := 0; i+1 < len(runes); i++ {
				add(string(runes[i : i+2]))
			}
		}
	}

	if len(terms) == 0 {
		add(query)
	}
	return terms
}

func isHanRun(s string) bool {
	for _, r := range s {
		if r < '\u4e00' || r > '\u9fff' {
			return false
		}
	}
	return s != ""
}

func (r *PgRetriever) Close() {
	if r.db != nil {
		r.db.Close()
	}
}

func (r *PgRetriever) LogCall(ctx context.Context, logEntry CallLog) {
	if r.db == nil {
		return
	}
	if logEntry.TraceJSON == "" {
		logEntry.TraceJSON = "[]"
	}
	if logEntry.Mode == "" {
		logEntry.Mode = "rag"
	}
	_, err := r.db.ExecContext(ctx, `
		INSERT INTO rag_call_log (
			kb_id, user_id, session_id, mode, intent, question_summary, top_k,
			source_count, retrieve_latency_ms, llm_latency_ms, total_latency_ms,
			confidence, trace
		) VALUES ($1, NULLIF($2, 0), NULLIF($3, 0), $4, $5, $6, $7, $8, $9, $10, $11, $12, $13::jsonb)
	`, logEntry.KbID, logEntry.UserID, logEntry.SessionID, logEntry.Mode, logEntry.Intent,
		logEntry.QuestionSummary, logEntry.TopK, logEntry.SourceCount, logEntry.RetrieveLatencyMs,
		logEntry.LLMLatencyMs, logEntry.TotalLatencyMs, logEntry.Confidence, logEntry.TraceJSON)
	if err != nil {
		log.Printf("RAG 调用日志写入失败: %v", err)
	}
}
