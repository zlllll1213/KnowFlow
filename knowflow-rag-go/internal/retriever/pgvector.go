package retriever

import (
	"database/sql"
	"fmt"
	"log"

	_ "github.com/lib/pq"
	"github.com/knowflow/rag-go/internal/config"
	"github.com/knowflow/rag-go/internal/types"
)

type PgRetriever struct {
	db *sql.DB
}

func New(cfg *config.Config) (*PgRetriever, error) {
	db, err := sql.Open("postgres", cfg.DBDSN)
	if err != nil {
		return nil, fmt.Errorf("连接数据库失败: %w", err)
	}
	if err := db.Ping(); err != nil {
		return nil, fmt.Errorf("数据库 ping 失败: %w", err)
	}
	log.Println("PostgreSQL 连接成功")
	return &PgRetriever{db: db}, nil
}

// Retrieve 从 document_chunk 检索与 query 相关的 TopK 个片段。
// 第一版使用 keyword LIKE 检索；后续接入 pgvector：ORDER BY embedding <=> $1。
func (r *PgRetriever) Retrieve(kbId int64, query string, topK int) ([]types.SourceChunk, error) {
	if topK <= 0 {
		topK = 5
	}

	// 第一版：简单关键词匹配（ILIKE）
	// 后续替换为 pgvector 向量检索:
	//   SELECT dc.id, dc.document_id, dc.chunk_index, dc.content,
	//          d.file_name, 1 - (dc.embedding <=> $1) AS score
	//   FROM document_chunk dc
	//   JOIN document d ON d.id = dc.document_id
	//   WHERE dc.kb_id = $2 AND d.is_deleted = 0
	//   ORDER BY dc.embedding <=> $1 LIMIT $3

	rows, err := r.db.Query(`
		SELECT dc.id, dc.document_id, dc.chunk_index, dc.content, d.file_name
		FROM document_chunk dc
		JOIN document d ON d.id = dc.document_id
		WHERE dc.kb_id = $1 AND d.is_deleted = 0
		  AND dc.content ILIKE '%' || $2 || '%'
		ORDER BY dc.chunk_index
		LIMIT $3
	`, kbId, query, topK)

	if err != nil {
		return nil, fmt.Errorf("检索失败: %w", err)
	}
	defer rows.Close()

	var sources []types.SourceChunk
	for rows.Next() {
		var s types.SourceChunk
		if err := rows.Scan(&s.ChunkId, &s.DocumentId, &s.ChunkIndex, &s.Content, &s.FileName); err != nil {
			return nil, fmt.Errorf("扫描行失败: %w", err)
		}
		s.Score = 0.5 // keyword match 无精确分数，统一 0.5
		sources = append(sources, s)
	}

	// 关键词未命中时，返回知识库中前 topK 个 chunk 作为 fallback
	if len(sources) == 0 {
		rows2, err := r.db.Query(`
			SELECT dc.id, dc.document_id, dc.chunk_index, dc.content, d.file_name
			FROM document_chunk dc
			JOIN document d ON d.id = dc.document_id
			WHERE dc.kb_id = $1 AND d.is_deleted = 0
			ORDER BY dc.chunk_index
			LIMIT $2
		`, kbId, topK)
		if err != nil {
			return nil, fmt.Errorf("fallback 检索失败: %w", err)
		}
		defer rows2.Close()
		for rows2.Next() {
			var s types.SourceChunk
			if err := rows2.Scan(&s.ChunkId, &s.DocumentId, &s.ChunkIndex, &s.Content, &s.FileName); err != nil {
				return nil, err
			}
			s.Score = 0.3
			sources = append(sources, s)
		}
	}

	log.Printf("检索完成: kbId=%d, topK=%d, found=%d", kbId, topK, len(sources))
	return sources, nil
}

func (r *PgRetriever) Close() {
	if r.db != nil {
		r.db.Close()
	}
}
