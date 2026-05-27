package service

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"strings"
	"time"

	"github.com/knowflow/rag-go/internal/embedding"
	"github.com/knowflow/rag-go/internal/llm"
	"github.com/knowflow/rag-go/internal/prompt"
	"github.com/knowflow/rag-go/internal/retriever"
	"github.com/knowflow/rag-go/internal/types"
)

type RAGService struct {
	retriever *retriever.PgRetriever
	embedder  embedding.Provider
	llm       llm.Provider
	topK      int
	maxTopK   int
}

func New(retriever *retriever.PgRetriever, embedder embedding.Provider, llmProvider llm.Provider, topK int, maxTopK int) *RAGService {
	return &RAGService{retriever: retriever, embedder: embedder, llm: llmProvider, topK: topK, maxTopK: maxTopK}
}

// Ask 同步问答。
func (s *RAGService) Ask(ctx context.Context, req types.RagRequest) (*types.RagResponse, error) {
	start := time.Now()

	topK := s.normalizeTopK(req.TopK)

	embeddingStart := time.Now()
	queryEmbedding, err := s.embedder.Embed(ctx, req.Question)
	if err != nil {
		log.Printf("query embedding 生成失败，回退关键词检索: %v", err)
	}
	embeddingMs := time.Since(embeddingStart).Milliseconds()

	retrieveStart := time.Now()
	sources, err := s.retriever.Retrieve(ctx, req.KbId, req.Question, queryEmbedding, topK)
	if err != nil {
		return nil, fmt.Errorf("检索失败: %w", err)
	}
	retrieveMs := time.Since(retrieveStart).Milliseconds()
	confidence := confidenceFromSources(sources)
	if len(sources) == 0 || confidence < 0.2 {
		answer := insufficientAnswer()
		elapsed := time.Since(start).Milliseconds()
		s.logCall(ctx, "rag", "qa", req, topK, len(sources), retrieveMs, 0, elapsed, confidence, nil)
		return &types.RagResponse{Answer: answer, Sources: sources, LatencyMs: elapsed}, nil
	}

	// 2. 构建 Prompt
	messages := prompt.BuildMessages(req.Question, sources)

	// 3. 调用 LLM
	llmStart := time.Now()
	answer, err := s.llm.Chat(ctx, messages)
	if err != nil {
		return nil, fmt.Errorf("LLM 调用失败: %w", err)
	}
	llmMs := time.Since(llmStart).Milliseconds()

	elapsed := time.Since(start).Milliseconds()
	log.Printf("问答完成: kbId=%d, topK=%d, sources=%d, embedding=%dms, retrieve=%dms, llm=%dms, total=%dms",
		req.KbId, topK, len(sources), embeddingMs, retrieveMs, llmMs, elapsed)
	s.logCall(ctx, "rag", "qa", req, topK, len(sources), retrieveMs, llmMs, elapsed, confidence, nil)

	return &types.RagResponse{
		Answer:    answer,
		Sources:   sources,
		LatencyMs: elapsed,
	}, nil
}

// AskStream 流式问答，通过 channel 输出 SSE 事件。
func (s *RAGService) AskStream(ctx context.Context, req types.RagRequest) (<-chan string, <-chan []types.SourceChunk, <-chan error) {
	tokenCh := make(chan string, 100)
	sourceCh := make(chan []types.SourceChunk, 1)
	errCh := make(chan error, 1)

	go func() {
		defer close(tokenCh)
		defer close(errCh)

		topK := s.normalizeTopK(req.TopK)

		start := time.Now()
		queryEmbedding, err := s.embedder.Embed(ctx, req.Question)
		if err != nil {
			log.Printf("query embedding 生成失败，回退关键词检索: %v", err)
		}

		// 1. 检索
		retrieveStart := time.Now()
		sources, err := s.retriever.Retrieve(ctx, req.KbId, req.Question, queryEmbedding, topK)
		if err != nil {
			errCh <- fmt.Errorf("检索失败: %w", err)
			return
		}
		retrieveMs := time.Since(retrieveStart).Milliseconds()
		confidence := confidenceFromSources(sources)
		if len(sources) == 0 || confidence < 0.2 {
			tokenCh <- insufficientAnswer()
			sourceCh <- sources
			s.logCall(ctx, "rag", "qa", req, topK, len(sources), retrieveMs, 0, time.Since(start).Milliseconds(), confidence, nil)
			return
		}

		// 2. 构建 Prompt
		messages := prompt.BuildMessages(req.Question, sources)

		// 3. 流式调用 LLM
		llmStart := time.Now()
		if err := s.llm.ChatStream(ctx, messages, tokenCh); err != nil {
			errCh <- fmt.Errorf("LLM 流式调用失败: %w", err)
			return
		}
		llmMs := time.Since(llmStart).Milliseconds()
		s.logCall(ctx, "rag", "qa", req, topK, len(sources), retrieveMs, llmMs, time.Since(start).Milliseconds(), confidence, nil)

		// 4. 推送 sources
		sourceCh <- sources
	}()

	return tokenCh, sourceCh, errCh
}

func (s *RAGService) AskAgent(ctx context.Context, req types.RagRequest) (*types.AgentResponse, error) {
	start := time.Now()
	topK := s.normalizeTopK(req.TopK)
	intent, trace := routeIntent(req.Question)

	query := buildRetrievalQuery(req.Question, intent)
	trace = append(trace, types.AgentTraceStep{Step: "retriever", Detail: "query=" + query})

	queryEmbedding, err := s.embedder.Embed(ctx, query)
	if err != nil {
		log.Printf("agent query embedding 生成失败，回退关键词检索: %v", err)
	}

	retrieveStart := time.Now()
	sources, err := s.retriever.Retrieve(ctx, req.KbId, query, queryEmbedding, topK)
	if err != nil {
		return nil, fmt.Errorf("Agent 检索失败: %w", err)
	}
	retrieveMs := time.Since(retrieveStart).Milliseconds()
	confidence := confidenceFromSources(sources)
	trace = append(trace, types.AgentTraceStep{Step: "citation_guard", Detail: fmt.Sprintf("sources=%d confidence=%.2f", len(sources), confidence)})

	if len(sources) == 0 || confidence < 0.2 || intent == "unknown" {
		answer := insufficientAnswer()
		elapsed := time.Since(start).Milliseconds()
		s.logCall(ctx, "agent", intent, req, topK, len(sources), retrieveMs, 0, elapsed, confidence, trace)
		return &types.AgentResponse{Intent: intent, Answer: answer, Sources: sources, Confidence: confidence, Trace: trace}, nil
	}

	llmStart := time.Now()
	answer, err := s.llm.Chat(ctx, prompt.BuildMessagesForIntent(req.Question, sources, intent))
	if err != nil {
		return nil, fmt.Errorf("Agent 回答生成失败: %w", err)
	}
	llmMs := time.Since(llmStart).Milliseconds()
	trace = append(trace, types.AgentTraceStep{Step: "answer", Detail: fmt.Sprintf("intent=%s llmMs=%d", intent, llmMs)})
	elapsed := time.Since(start).Milliseconds()
	s.logCall(ctx, "agent", intent, req, topK, len(sources), retrieveMs, llmMs, elapsed, confidence, trace)

	return &types.AgentResponse{Intent: intent, Answer: answer, Sources: sources, Confidence: confidence, Trace: trace}, nil
}

func (s *RAGService) AskAgentStream(ctx context.Context, req types.RagRequest) (<-chan string, <-chan []types.SourceChunk, <-chan types.AgentResponse, <-chan error) {
	tokenCh := make(chan string, 100)
	sourceCh := make(chan []types.SourceChunk, 1)
	metaCh := make(chan types.AgentResponse, 1)
	errCh := make(chan error, 1)
	go func() {
		defer close(tokenCh)
		defer close(sourceCh)
		defer close(metaCh)
		defer close(errCh)
		resp, err := s.AskAgent(ctx, req)
		if err != nil {
			errCh <- err
			return
		}
		metaCh <- *resp
		tokenCh <- resp.Answer
		sourceCh <- resp.Sources
	}()
	return tokenCh, sourceCh, metaCh, errCh
}

func (s *RAGService) normalizeTopK(topK int) int {
	if topK <= 0 {
		topK = s.topK
	}
	if topK <= 0 {
		topK = 5
	}
	if s.maxTopK > 0 && topK > s.maxTopK {
		return s.maxTopK
	}
	return topK
}

func routeIntent(question string) (string, []types.AgentTraceStep) {
	q := strings.ToLower(question)
	intent := "qa"
	switch {
	case strings.Contains(q, "总结") || strings.Contains(q, "summary") || strings.Contains(q, "概括"):
		intent = "summarize"
	case strings.Contains(q, "学习计划") || strings.Contains(q, "study plan") || strings.Contains(q, "怎么学"):
		intent = "study_plan"
	case strings.Contains(q, "代码") || strings.Contains(q, "接口") || strings.Contains(q, "报错") || strings.Contains(q, "bug") || strings.Contains(q, "api"):
		intent = "code_analysis"
	case strings.Contains(q, "报告") || strings.Contains(q, "report"):
		intent = "report"
	case strings.TrimSpace(q) == "":
		intent = "unknown"
	}
	return intent, []types.AgentTraceStep{{Step: "router", Detail: "intent=" + intent}}
}

func buildRetrievalQuery(question string, intent string) string {
	switch intent {
	case "summarize":
		return question + " 总结 重点 结论"
	case "study_plan":
		return question + " 学习 路线 阶段 计划"
	case "code_analysis":
		return question + " 代码 接口 错误 配置 实现"
	case "report":
		return question + " 报告 背景 结论 依据"
	default:
		return question
	}
}

func confidenceFromSources(sources []types.SourceChunk) float64 {
	if len(sources) == 0 {
		return 0
	}
	score := sources[0].Score
	if score < 0 {
		return 0
	}
	if score > 1 {
		return 1
	}
	return score
}

func insufficientAnswer() string {
	return "知识库中未找到足够依据回答该问题。请补充相关文档，或换一种更具体的问法。"
}

func (s *RAGService) logCall(ctx context.Context, mode string, intent string, req types.RagRequest, topK int, sourceCount int, retrieveMs int64, llmMs int64, totalMs int64, confidence float64, trace []types.AgentTraceStep) {
	traceBytes, _ := json.Marshal(trace)
	s.retriever.LogCall(ctx, retriever.CallLog{
		KbID:              req.KbId,
		Mode:              mode,
		Intent:            intent,
		QuestionSummary:   summarizeQuestion(req.Question),
		TopK:              topK,
		SourceCount:       sourceCount,
		RetrieveLatencyMs: retrieveMs,
		LLMLatencyMs:      llmMs,
		TotalLatencyMs:    totalMs,
		Confidence:        confidence,
		TraceJSON:         string(traceBytes),
	})
}

func summarizeQuestion(question string) string {
	runes := []rune(strings.TrimSpace(question))
	if len(runes) > 180 {
		return string(runes[:180])
	}
	return string(runes)
}
