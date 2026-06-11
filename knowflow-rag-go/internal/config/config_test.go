package config

import (
	"strings"
	"testing"
)

func TestLoadDoesNotInheritLLMKeyForEmbedding(t *testing.T) {
	t.Setenv("RAG_LLM_PROVIDER", "deepseek")
	t.Setenv("RAG_LLM_API_KEY", "test-deepseek-key")
	t.Setenv("RAG_EMBEDDING_PROVIDER", "")
	t.Setenv("RAG_EMBEDDING_API_KEY", "")

	cfg := Load()

	if cfg.EmbeddingProvider != "mock" {
		t.Fatalf("embedding provider should default to mock, got %q", cfg.EmbeddingProvider)
	}
	if cfg.EmbeddingAPIKey != "" {
		t.Fatal("embedding API key should not inherit RAG_LLM_API_KEY")
	}
}

func TestValidateRejectsDeepSeekEmbeddingProvider(t *testing.T) {
	cfg := Load()
	cfg.DBDSN = "postgres://knowflow:secret@localhost:5432/knowflow?sslmode=disable"
	cfg.LLMProvider = "deepseek"
	cfg.LLMAPIKey = "test-deepseek-key"
	cfg.EmbeddingProvider = "deepseek"
	cfg.EmbeddingAPIKey = "test-deepseek-key"

	err := cfg.Validate()
	if err == nil {
		t.Fatal("expected deepseek embedding provider to be rejected")
	}
	if !strings.Contains(err.Error(), "RAG_EMBEDDING_PROVIDER=deepseek 暂不支持") {
		t.Fatalf("unexpected error: %v", err)
	}
}

func TestValidateDeepSeekLLMRequiresOnlyLLMKey(t *testing.T) {
	cfg := Load()
	cfg.DBDSN = "postgres://knowflow:secret@localhost:5432/knowflow?sslmode=disable"
	cfg.LLMProvider = "deepseek"
	cfg.LLMAPIKey = "test-deepseek-key"
	cfg.EmbeddingProvider = "mock"
	cfg.EmbeddingAPIKey = ""

	if err := cfg.Validate(); err != nil {
		t.Fatalf("Validate returned error: %v", err)
	}
}

func TestValidateRequiresExplicitDBDSN(t *testing.T) {
	cfg := Load()
	cfg.DBDSN = ""

	err := cfg.Validate()
	if err == nil {
		t.Fatal("expected empty RAG_DB_DSN to be rejected")
	}
	if !strings.Contains(err.Error(), "RAG_DB_DSN 不能为空") {
		t.Fatalf("unexpected error: %v", err)
	}
}
