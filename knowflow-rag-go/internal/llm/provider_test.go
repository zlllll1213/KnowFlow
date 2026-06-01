package llm

import (
	"context"
	"encoding/json"
	"strings"
	"testing"

	"github.com/knowflow/rag-go/internal/config"
)

func TestMockProviderChatStreamDoesNotCloseOutput(t *testing.T) {
	provider := &MockProvider{}
	out := make(chan string, 16)

	if err := provider.ChatStream(context.Background(), []Message{{Role: "user", Content: "测试"}}, out); err != nil {
		t.Fatalf("ChatStream returned error: %v", err)
	}

	select {
	case out <- "caller still owns channel":
	default:
		t.Fatal("output channel is unexpectedly blocked")
	}
	close(out)
}

func TestMockProviderChatIncludesQuestion(t *testing.T) {
	provider := &MockProvider{}
	answer, err := provider.Chat(context.Background(), []Message{{Role: "user", Content: "什么是 RAG？"}})
	if err != nil {
		t.Fatalf("Chat returned error: %v", err)
	}
	if !strings.Contains(answer, "什么是 RAG？") {
		t.Fatalf("answer does not include question: %s", answer)
	}
}

func TestDeepSeekProviderDefaultsUseCurrentOfficialEndpoint(t *testing.T) {
	provider := NewProvider(&config.Config{
		LLMProvider: "deepseek",
		LLMAPIKey:   "test-key",
	})

	compatible, ok := provider.(*OpenAICompatibleProvider)
	if !ok {
		t.Fatalf("expected OpenAICompatibleProvider, got %T", provider)
	}
	if compatible.baseURL != "https://api.deepseek.com" {
		t.Fatalf("unexpected DeepSeek base URL: %s", compatible.baseURL)
	}
	if compatible.model != "deepseek-v4-flash" {
		t.Fatalf("unexpected DeepSeek model: %s", compatible.model)
	}
	if compatible.thinkingMode != "disabled" {
		t.Fatalf("unexpected DeepSeek thinking mode: %s", compatible.thinkingMode)
	}
}

func TestDeepSeekRequestBodyDisablesThinkingByDefault(t *testing.T) {
	provider := NewProvider(&config.Config{
		LLMProvider: "deepseek",
		LLMAPIKey:   "test-key",
	})
	compatible := provider.(*OpenAICompatibleProvider)

	body, err := compatible.requestBody([]Message{{Role: "user", Content: "hi"}}, false)
	if err != nil {
		t.Fatalf("requestBody returned error: %v", err)
	}

	var payload map[string]any
	if err := json.Unmarshal(body, &payload); err != nil {
		t.Fatalf("unmarshal request body: %v", err)
	}
	thinking, ok := payload["thinking"].(map[string]any)
	if !ok {
		t.Fatalf("missing thinking payload: %v", payload)
	}
	if thinking["type"] != "disabled" {
		t.Fatalf("unexpected thinking mode: %v", thinking["type"])
	}
}

func TestDeepSeekThinkingCanBeEnabled(t *testing.T) {
	provider := NewProvider(&config.Config{
		LLMProvider: "deepseek",
		LLMAPIKey:   "test-key",
		LLMThinking: true,
	})

	compatible := provider.(*OpenAICompatibleProvider)
	if compatible.thinkingMode != "enabled" {
		t.Fatalf("unexpected DeepSeek thinking mode: %s", compatible.thinkingMode)
	}
}
