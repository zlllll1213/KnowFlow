package service

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"strings"
	"time"

	"github.com/knowflow/rag-go/internal/agent"
	"github.com/knowflow/rag-go/internal/embedding"
	"github.com/knowflow/rag-go/internal/llm"
	"github.com/knowflow/rag-go/internal/prompt"
	"github.com/knowflow/rag-go/internal/retriever"
	"github.com/knowflow/rag-go/internal/types"
)

type RAGService struct {
	retriever    *retriever.PgRetriever
	embedder     embedding.Provider
	llm          llm.Provider
	topK         int
	maxTopK      int
	embeddingDim int
}

func New(retriever *retriever.PgRetriever, embedder embedding.Provider, llmProvider llm.Provider, topK int, maxTopK int, embeddingDim int) *RAGService {
	return &RAGService{retriever: retriever, embedder: embedder, llm: llmProvider, topK: topK, maxTopK: maxTopK, embeddingDim: embeddingDim}
}

// Ask 同步问答。
func (s *RAGService) Ask(ctx context.Context, req types.RagRequest) (*types.RagResponse, error) {
	start := time.Now()

	topK := s.normalizeTopK(req.TopK)

	embeddingStart := time.Now()
	embeddingUsed := true
	queryEmbedding, err := s.embedder.Embed(ctx, req.Question)
	if err != nil {
		log.Printf("embedding 生成失败，回退关键词检索: kbId=%d, error=%v", req.KbId, err)
		embeddingUsed = false
		queryEmbedding = nil // 确保不使用零值向量，keyword-only retrieve
	} else if err := s.validateEmbeddingDim(queryEmbedding); err != nil {
		return nil, err
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
	log.Printf("问答完成: kbId=%d, topK=%d, sources=%d, embedding=%dms, retrieve=%dms, llm=%dms, total=%dms, embeddingUsed=%v",
		req.KbId, topK, len(sources), embeddingMs, retrieveMs, llmMs, elapsed, embeddingUsed)
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
			log.Printf("embedding 生成失败，回退关键词检索: kbId=%d, error=%v", req.KbId, err)
			queryEmbedding = nil
		} else if err := s.validateEmbeddingDim(queryEmbedding); err != nil {
			errCh <- err
			return
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
	intent, detail := agent.RouteIntent(req.Question)
	trace := []types.AgentTraceStep{{Step: "router", Detail: detail}}

	query := agent.BuildRetrievalQuery(req.Question, intent)

	queryEmbedding, err := s.embedder.Embed(ctx, query)
	if err != nil {
		log.Printf("agent embedding 生成失败，回退关键词检索: kbId=%d, intent=%s, error=%v", req.KbId, intent, err)
		queryEmbedding = nil
	} else if err := s.validateEmbeddingDim(queryEmbedding); err != nil {
		return nil, err
	}

	retrieveStart := time.Now()
	sources, err := s.retriever.Retrieve(ctx, req.KbId, query, queryEmbedding, topK)
	if err != nil {
		return nil, fmt.Errorf("Agent 检索失败: %w", err)
	}
	retrieveMs := time.Since(retrieveStart).Milliseconds()
	trace = append(trace, types.AgentTraceStep{Step: "retriever", Detail: fmt.Sprintf("检索到 %d 个相关片段", len(sources))})

	guard := agent.EvaluateCitations(sources)
	trace = append(trace, types.AgentTraceStep{Step: "citation_guard", Detail: guard.Detail})
	if !guard.AllowAnswer {
		elapsed := time.Since(start).Milliseconds()
		trace = append(trace, types.AgentTraceStep{Step: "answer", Detail: "资料不足，未调用 LLM"})
		s.logCall(ctx, "agent", intent, req, topK, len(sources), retrieveMs, 0, elapsed, guard.Confidence, trace)
		return &types.AgentResponse{Intent: intent, Answer: agent.InsufficientAnswer, Sources: sources, Confidence: guard.Confidence, Trace: trace, LatencyMs: elapsed}, nil
	}

	if intent == agent.IntentUnknown {
		elapsed := time.Since(start).Milliseconds()
		confidence := minFloat(guard.Confidence, 0.3)
		trace = append(trace, types.AgentTraceStep{Step: "answer", Detail: "意图 unknown，未调用 LLM"})
		s.logCall(ctx, "agent", intent, req, topK, len(sources), retrieveMs, 0, elapsed, confidence, trace)
		return &types.AgentResponse{Intent: intent, Answer: agent.UnknownAnswer, Sources: sources, Confidence: confidence, Trace: trace, LatencyMs: elapsed}, nil
	}

	llmStart := time.Now()
	answer, err := s.llm.Chat(ctx, prompt.BuildMessagesForIntent(req.Question, sources, intent))
	if err != nil {
		return nil, fmt.Errorf("Agent 回答生成失败: %w", err)
	}
	llmMs := time.Since(llmStart).Milliseconds()
	if guard.Prefix != "" {
		answer = guard.Prefix + "\n\n" + answer
	}
	trace = append(trace, types.AgentTraceStep{Step: "answer", Detail: fmt.Sprintf("基于资料生成回答，intent=%s llmMs=%d", intent, llmMs)})
	elapsed := time.Since(start).Milliseconds()
	s.logCall(ctx, "agent", intent, req, topK, len(sources), retrieveMs, llmMs, elapsed, guard.Confidence, trace)

	return &types.AgentResponse{Intent: intent, Answer: answer, Sources: sources, Confidence: guard.Confidence, Trace: trace, LatencyMs: elapsed}, nil
}

func (s *RAGService) AskAgentStream(ctx context.Context, req types.RagRequest) (<-chan string, <-chan []types.SourceChunk, <-chan types.AgentResponse, <-chan error) {
	tokenCh := make(chan string, 100)
	sourceCh := make(chan []types.SourceChunk, 1)
	metaCh := newAgentMetaChannel()
	errCh := make(chan error, 1)
	go func() {
		defer close(tokenCh)
		defer close(sourceCh)
		defer close(metaCh)
		defer close(errCh)

		start := time.Now()
		topK := s.normalizeTopK(req.TopK)
		intent, detail := agent.RouteIntent(req.Question)
		trace := []types.AgentTraceStep{{Step: "router", Detail: detail}}

		query := agent.BuildRetrievalQuery(req.Question, intent)

		queryEmbedding, err := s.embedder.Embed(ctx, query)
		if err != nil {
			log.Printf("agent embedding 生成失败，回退关键词检索: kbId=%d, intent=%s, error=%v", req.KbId, intent, err)
			queryEmbedding = nil
		} else if err := s.validateEmbeddingDim(queryEmbedding); err != nil {
			errCh <- err
			return
		}

		retrieveStart := time.Now()
		sources, err := s.retriever.Retrieve(ctx, req.KbId, query, queryEmbedding, topK)
		if err != nil {
			errCh <- fmt.Errorf("Agent 检索失败: %w", err)
			return
		}
		retrieveMs := time.Since(retrieveStart).Milliseconds()
		trace = append(trace, types.AgentTraceStep{Step: "retriever", Detail: fmt.Sprintf("检索到 %d 个相关片段", len(sources))})

		guard := agent.EvaluateCitations(sources)
		trace = append(trace, types.AgentTraceStep{Step: "citation_guard", Detail: guard.Detail})

		if !guard.AllowAnswer || intent == agent.IntentUnknown {
			var answer string
			confidence := guard.Confidence
			if !guard.AllowAnswer {
				answer = agent.InsufficientAnswer
				trace = append(trace, types.AgentTraceStep{Step: "answer", Detail: "资料不足，未调用 LLM"})
			} else {
				answer = agent.UnknownAnswer
				confidence = minFloat(guard.Confidence, 0.3)
				trace = append(trace, types.AgentTraceStep{Step: "answer", Detail: "意图 unknown，未调用 LLM"})
			}
			elapsed := time.Since(start).Milliseconds()
			s.logCall(ctx, "agent", intent, req, topK, len(sources), retrieveMs, 0, elapsed, confidence, trace)
			metaCh <- types.AgentResponse{Intent: intent, Sources: sources, Confidence: confidence, Trace: trace, LatencyMs: elapsed}
			tokenCh <- answer
			sourceCh <- sources
			return
		}

		// Send meta before streaming tokens
		metaCh <- types.AgentResponse{
			Intent:     intent,
			Sources:    sources,
			Confidence: guard.Confidence,
			Trace:      trace,
		}

		// Stream LLM tokens
		messages := prompt.BuildMessagesForIntent(req.Question, sources, intent)
		llmStart := time.Now()
		if guard.Prefix != "" {
			tokenCh <- guard.Prefix + "\n\n"
		}
		if err := s.llm.ChatStream(ctx, messages, tokenCh); err != nil {
			errCh <- fmt.Errorf("Agent LLM 流式调用失败: %w", err)
			return
		}
		llmMs := time.Since(llmStart).Milliseconds()
		trace = append(trace, types.AgentTraceStep{Step: "answer", Detail: fmt.Sprintf("基于资料生成回答，intent=%s llmMs=%d", intent, llmMs)})
		elapsed := time.Since(start).Milliseconds()

		// Update meta with final timing
		metaCh <- types.AgentResponse{
			Intent:     intent,
			Sources:    sources,
			Confidence: guard.Confidence,
			Trace:      trace,
			LatencyMs:  elapsed,
		}

		s.logCall(ctx, "agent", intent, req, topK, len(sources), retrieveMs, llmMs, elapsed, guard.Confidence, trace)
		sourceCh <- sources
	}()
	return tokenCh, sourceCh, metaCh, errCh
}

func newAgentMetaChannel() chan types.AgentResponse {
	// Agent streaming emits initial routing metadata and final timing metadata.
	return make(chan types.AgentResponse, 2)
}

func (s *RAGService) normalizeTopK(topK int) int {
	if topK <= 0 {
		topK = s.topK
	}
	if topK <= 0 {
		topK = 5
	}
	if s.maxTopK > 0 && topK > s.maxTopK {
		log.Printf("topK=%d 超过上限，截断为 %d", topK, s.maxTopK)
		return s.maxTopK
	}
	return topK
}

func (s *RAGService) validateEmbeddingDim(vector []float32) error {
	if len(vector) == 0 {
		return nil
	}
	if s.embeddingDim <= 0 {
		return nil
	}
	if len(vector) != s.embeddingDim {
		return fmt.Errorf("embedding dimension mismatch: expected=%d, actual=%d", s.embeddingDim, len(vector))
	}
	return nil
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

func minFloat(a float64, b float64) float64 {
	if a < b {
		return a
	}
	return b
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
