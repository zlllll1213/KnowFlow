package llm

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"strings"

	"github.com/knowflow/rag-go/internal/config"
)

// Provider LLM Provider 接口。
// ChatStream should write tokens to out, but the caller owns closing the channel.
type Provider interface {
	Chat(ctx context.Context, messages []Message) (string, error)
	ChatStream(ctx context.Context, messages []Message, out chan<- string) error
}

type Message struct {
	Role    string `json:"role"`
	Content string `json:"content"`
}

func NewProvider(cfg *config.Config) Provider {
	switch strings.ToLower(cfg.LLMProvider) {
	case "", "mock":
		return &MockProvider{label: "Mock"}
	case "openai", "deepseek":
		return &OpenAICompatibleProvider{
			apiKey:       cfg.LLMAPIKey,
			baseURL:      defaultString(cfg.LLMBaseURL, providerDefaultBaseURL(cfg.LLMProvider)),
			model:        defaultString(cfg.LLMModel, providerDefaultModel(cfg.LLMProvider)),
			thinkingMode: providerThinkingMode(cfg.LLMProvider, cfg.LLMThinking),
			client:       &http.Client{Timeout: cfg.RequestTimeout},
		}
	case "ollama":
		return &OllamaProvider{
			baseURL: defaultString(cfg.LLMBaseURL, "http://localhost:11434"),
			model:   defaultString(cfg.LLMModel, "llama3.1"),
			client:  &http.Client{Timeout: cfg.RequestTimeout},
		}
	default:
		log.Printf("未知 LLM provider: %s, 回退到 mock", cfg.LLMProvider)
		return &MockProvider{label: "Mock"}
	}
}

type MockProvider struct {
	label string // set once at construction; safe for concurrent reads
}

func (m *MockProvider) Chat(ctx context.Context, messages []Message) (string, error) {
	question := lastUserMessage(messages)
	return fmt.Sprintf("已收到您的问题：「%s」。\n根据知识库中的参考资料，我找到了一些相关内容。请查看右侧引用来源了解详情。", question), nil
}

func (m *MockProvider) ChatStream(ctx context.Context, messages []Message, out chan<- string) error {
	chunks := []string{
		"正在分析您的问题……\n\n",
		"根据知识库中的参考资料，",
		"我为您整理了以下信息：\n\n",
		"这是基于当前知识库内容的回答。\n",
		"请查看右侧引用来源了解具体文档片段。",
	}

	for _, chunk := range chunks {
		select {
		case <-ctx.Done():
			return ctx.Err()
		case out <- chunk:
		}
	}
	return nil
}

type OpenAICompatibleProvider struct {
	apiKey       string
	baseURL      string
	model        string
	thinkingMode string
	client       *http.Client
}

func (p *OpenAICompatibleProvider) Chat(ctx context.Context, messages []Message) (string, error) {
	var resp chatCompletionResponse
	if err := p.call(ctx, messages, false, &resp); err != nil {
		return "", err
	}
	if len(resp.Choices) == 0 {
		return "", fmt.Errorf("LLM 未返回 choices")
	}
	return resp.Choices[0].Message.Content, nil
}

func (p *OpenAICompatibleProvider) ChatStream(ctx context.Context, messages []Message, out chan<- string) error {
	body, err := p.requestBody(messages, true)
	if err != nil {
		return err
	}
	req, err := p.newRequest(ctx, body)
	if err != nil {
		return err
	}
	resp, err := p.client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return fmt.Errorf("LLM API 返回状态码 %d", resp.StatusCode)
	}

	scanner := bufio.NewScanner(resp.Body)
	scanner.Buffer(make([]byte, 0, 64*1024), 1024*1024)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || !strings.HasPrefix(line, "data:") {
			continue
		}
		payload := strings.TrimSpace(strings.TrimPrefix(line, "data:"))
		if payload == "[DONE]" {
			return nil
		}
		var event chatCompletionStreamEvent
		if err := json.Unmarshal([]byte(payload), &event); err != nil {
			continue
		}
		for _, choice := range event.Choices {
			if choice.Delta.Content == "" {
				continue
			}
			select {
			case <-ctx.Done():
				return ctx.Err()
			case out <- choice.Delta.Content:
			}
		}
	}
	return scanner.Err()
}

func (p *OpenAICompatibleProvider) call(ctx context.Context, messages []Message, stream bool, target any) error {
	body, err := p.requestBody(messages, stream)
	if err != nil {
		return err
	}
	req, err := p.newRequest(ctx, body)
	if err != nil {
		return err
	}
	resp, err := p.client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		msg, _ := io.ReadAll(io.LimitReader(resp.Body, 4096))
		return fmt.Errorf("LLM API 返回状态码 %d: %s", resp.StatusCode, strings.TrimSpace(string(msg)))
	}
	return json.NewDecoder(resp.Body).Decode(target)
}

func (p *OpenAICompatibleProvider) requestBody(messages []Message, stream bool) ([]byte, error) {
	if p.apiKey == "" {
		return nil, fmt.Errorf("LLM API key 未配置")
	}
	payload := map[string]any{
		"model":    p.model,
		"messages": messages,
		"stream":   stream,
	}
	if p.thinkingMode != "" {
		payload["thinking"] = map[string]string{"type": p.thinkingMode}
	}
	return json.Marshal(payload)
}

func (p *OpenAICompatibleProvider) newRequest(ctx context.Context, body []byte) (*http.Request, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, strings.TrimRight(p.baseURL, "/")+"/chat/completions", bytes.NewReader(body))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+p.apiKey)
	return req, nil
}

type OllamaProvider struct {
	baseURL string
	model   string
	client  *http.Client
}

func (p *OllamaProvider) Chat(ctx context.Context, messages []Message) (string, error) {
	body, err := json.Marshal(map[string]any{
		"model":    p.model,
		"messages": messages,
		"stream":   false,
	})
	if err != nil {
		return "", err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, strings.TrimRight(p.baseURL, "/")+"/api/chat", bytes.NewReader(body))
	if err != nil {
		return "", err
	}
	req.Header.Set("Content-Type", "application/json")
	resp, err := p.client.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return "", fmt.Errorf("Ollama 返回状态码 %d", resp.StatusCode)
	}
	var data struct {
		Message Message `json:"message"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&data); err != nil {
		return "", err
	}
	return data.Message.Content, nil
}

func (p *OllamaProvider) ChatStream(ctx context.Context, messages []Message, out chan<- string) error {
	body, err := json.Marshal(map[string]any{
		"model":    p.model,
		"messages": messages,
		"stream":   true,
	})
	if err != nil {
		return err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, strings.TrimRight(p.baseURL, "/")+"/api/chat", bytes.NewReader(body))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	resp, err := p.client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return fmt.Errorf("Ollama 返回状态码 %d", resp.StatusCode)
	}
	scanner := bufio.NewScanner(resp.Body)
	scanner.Buffer(make([]byte, 0, 64*1024), 1024*1024)
	for scanner.Scan() {
		var data struct {
			Message Message `json:"message"`
			Done    bool    `json:"done"`
		}
		if err := json.Unmarshal(scanner.Bytes(), &data); err != nil {
			continue
		}
		if data.Done {
			return nil
		}
		if data.Message.Content == "" {
			continue
		}
		select {
		case <-ctx.Done():
			return ctx.Err()
		case out <- data.Message.Content:
		}
	}
	return scanner.Err()
}

type chatCompletionResponse struct {
	Choices []struct {
		Message Message `json:"message"`
	} `json:"choices"`
}

type chatCompletionStreamEvent struct {
	Choices []struct {
		Delta Message `json:"delta"`
	} `json:"choices"`
}

func lastUserMessage(messages []Message) string {
	for i := len(messages) - 1; i >= 0; i-- {
		if messages[i].Role == "user" {
			return messages[i].Content
		}
	}
	return "（未知问题）"
}

func providerDefaultBaseURL(provider string) string {
	if strings.EqualFold(provider, "deepseek") {
		return "https://api.deepseek.com"
	}
	return "https://api.openai.com/v1"
}

func providerDefaultModel(provider string) string {
	if strings.EqualFold(provider, "deepseek") {
		return "deepseek-v4-flash"
	}
	return "gpt-4o-mini"
}

func providerThinkingMode(provider string, thinkingEnabled bool) string {
	if !strings.EqualFold(provider, "deepseek") {
		return ""
	}
	if thinkingEnabled {
		return "enabled"
	}
	return "disabled"
}

func defaultString(value, fallback string) string {
	if value != "" {
		return value
	}
	return fallback
}
