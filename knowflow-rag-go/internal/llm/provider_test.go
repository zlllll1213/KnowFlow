package llm

import (
	"context"
	"strings"
	"testing"
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
