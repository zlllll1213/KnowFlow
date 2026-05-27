package embedding

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"

	"github.com/knowflow/rag-go/internal/config"
)

type Provider interface {
	Embed(ctx context.Context, text string) ([]float32, error)
}

type MockProvider struct {
	dim int
}

func NewProvider(cfg *config.Config) Provider {
	switch strings.ToLower(cfg.EmbeddingProvider) {
	case "", "mock":
		return &MockProvider{dim: cfg.EmbeddingDim}
	case "openai", "deepseek":
		return &OpenAIProvider{
			apiKey:  cfg.EmbeddingAPIKey,
			baseURL: defaultString(cfg.EmbeddingBaseURL, "https://api.openai.com/v1"),
			model:   cfg.EmbeddingModel,
			client:  &http.Client{Timeout: cfg.RequestTimeout},
		}
	case "ollama":
		return &OllamaProvider{
			baseURL: defaultString(cfg.EmbeddingBaseURL, "http://localhost:11434"),
			model:   defaultString(cfg.EmbeddingModel, "nomic-embed-text"),
			client:  &http.Client{Timeout: cfg.RequestTimeout},
		}
	default:
		return &MockProvider{dim: cfg.EmbeddingDim}
	}
}

func (m *MockProvider) Embed(ctx context.Context, text string) ([]float32, error) {
	dim := m.dim
	if dim <= 0 {
		dim = 1536
	}
	// 基于输入文本产生确定性变体向量，而非所有查询全相同
	vec := make([]float32, dim)
	seed := uint32(0)
	for _, r := range text {
		seed = seed*31 + uint32(r)
	}
	for i := range vec {
		// 用 seed + index 产生一个 -0.1 ~ 0.1 的伪随机值叠加在 0.05 基线上
		h := (seed + uint32(i)) * 2654435761 // Knuth multiplicative hash
		vec[i] = 0.05 + (float32(h%200)-100)/1000.0
	}
	return vec, nil
}

type OpenAIProvider struct {
	apiKey  string
	baseURL string
	model   string
	client  *http.Client
}

func (p *OpenAIProvider) Embed(ctx context.Context, text string) ([]float32, error) {
	if p.apiKey == "" {
		return nil, fmt.Errorf("embedding API key 未配置")
	}
	body, err := json.Marshal(map[string]any{
		"model": p.model,
		"input": text,
	})
	if err != nil {
		return nil, err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, strings.TrimRight(p.baseURL, "/")+"/embeddings", bytes.NewReader(body))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+p.apiKey)

	resp, err := p.client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil, fmt.Errorf("embedding API 返回状态码 %d", resp.StatusCode)
	}

	var data struct {
		Data []struct {
			Embedding []float32 `json:"embedding"`
		} `json:"data"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&data); err != nil {
		return nil, err
	}
	if len(data.Data) == 0 {
		return nil, fmt.Errorf("embedding API 未返回向量")
	}
	return data.Data[0].Embedding, nil
}

type OllamaProvider struct {
	baseURL string
	model   string
	client  *http.Client
}

func (p *OllamaProvider) Embed(ctx context.Context, text string) ([]float32, error) {
	body, err := json.Marshal(map[string]any{
		"model":  p.model,
		"prompt": text,
	})
	if err != nil {
		return nil, err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, strings.TrimRight(p.baseURL, "/")+"/api/embeddings", bytes.NewReader(body))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := p.client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil, fmt.Errorf("ollama embedding 返回状态码 %d", resp.StatusCode)
	}

	var data struct {
		Embedding []float32 `json:"embedding"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&data); err != nil {
		return nil, err
	}
	if len(data.Embedding) == 0 {
		return nil, fmt.Errorf("ollama 未返回向量")
	}
	return data.Embedding, nil
}

func defaultString(value, fallback string) string {
	if value != "" {
		return value
	}
	return fallback
}
