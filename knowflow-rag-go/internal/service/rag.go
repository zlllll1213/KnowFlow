package service

import (
	"context"
	"fmt"
	"log"
	"time"

	"github.com/knowflow/rag-go/internal/llm"
	"github.com/knowflow/rag-go/internal/prompt"
	"github.com/knowflow/rag-go/internal/retriever"
	"github.com/knowflow/rag-go/internal/types"
)

type RAGService struct {
	retriever *retriever.PgRetriever
	llm       llm.Provider
	topK      int
}

func New(retriever *retriever.PgRetriever, llmProvider llm.Provider, topK int) *RAGService {
	return &RAGService{retriever: retriever, llm: llmProvider, topK: topK}
}

// Ask 同步问答。
func (s *RAGService) Ask(ctx context.Context, req types.RagRequest) (*types.RagResponse, error) {
	start := time.Now()

	topK := req.TopK
	if topK <= 0 {
		topK = s.topK
	}

	// 1. 检索
	sources, err := s.retriever.Retrieve(req.KbId, req.Question, topK)
	if err != nil {
		return nil, fmt.Errorf("检索失败: %w", err)
	}

	// 2. 构建 Prompt
	messages := prompt.BuildMessages(req.Question, sources)

	// 3. 调用 LLM
	answer, err := s.llm.Chat(ctx, messages)
	if err != nil {
		return nil, fmt.Errorf("LLM 调用失败: %w", err)
	}

	elapsed := time.Since(start).Milliseconds()
	log.Printf("问答完成: kbId=%d, topK=%d, sources=%d, latency=%dms",
		req.KbId, topK, len(sources), elapsed)

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

		topK := req.TopK
		if topK <= 0 {
			topK = s.topK
		}

		// 1. 检索
		sources, err := s.retriever.Retrieve(req.KbId, req.Question, topK)
		if err != nil {
			errCh <- fmt.Errorf("检索失败: %w", err)
			return
		}

		// 2. 构建 Prompt
		messages := prompt.BuildMessages(req.Question, sources)

		// 3. 流式调用 LLM
		if err := s.llm.ChatStream(ctx, messages, tokenCh); err != nil {
			errCh <- fmt.Errorf("LLM 流式调用失败: %w", err)
			return
		}

		// 4. 推送 sources
		sourceCh <- sources
	}()

	return tokenCh, sourceCh, errCh
}
