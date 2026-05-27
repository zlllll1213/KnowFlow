package embedding

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestMockProviderDeterministic(t *testing.T) {
	p := &MockProvider{dim: 128}
	ctx := context.Background()

	v1, err := p.Embed(ctx, "hello")
	if err != nil {
		t.Fatalf("Embed returned error: %v", err)
	}
	v2, err := p.Embed(ctx, "hello")
	if err != nil {
		t.Fatalf("Embed returned error: %v", err)
	}
	if len(v1) != len(v2) {
		t.Fatalf("same text produced different dimensions: %d vs %d", len(v1), len(v2))
	}
	for i := range v1 {
		if v1[i] != v2[i] {
			t.Fatalf("same text produced different value at index %d: %f vs %f", i, v1[i], v2[i])
		}
	}
}

func TestMockProviderDifferentTexts(t *testing.T) {
	p := &MockProvider{dim: 64}
	ctx := context.Background()

	v1, _ := p.Embed(ctx, "hello")
	v2, _ := p.Embed(ctx, "world")

	if len(v1) != 64 || len(v2) != 64 {
		t.Fatalf("wrong dimension: %d, %d", len(v1), len(v2))
	}
	differs := false
	for i := range v1 {
		if v1[i] != v2[i] {
			differs = true
			break
		}
	}
	if !differs {
		t.Fatal("different texts produced identical vectors")
	}
}

func TestMockProviderDefaultDim(t *testing.T) {
	p := &MockProvider{dim: 0} // should default to 1536
	ctx := context.Background()
	v, err := p.Embed(ctx, "test")
	if err != nil {
		t.Fatalf("Embed returned error: %v", err)
	}
	if len(v) != 1536 {
		t.Fatalf("default dim: expected 1536, got %d", len(v))
	}
}

func TestMockProviderCustomDim(t *testing.T) {
	p := &MockProvider{dim: 256}
	ctx := context.Background()
	v, err := p.Embed(ctx, "test")
	if err != nil {
		t.Fatalf("Embed returned error: %v", err)
	}
	if len(v) != 256 {
		t.Fatalf("custom dim: expected 256, got %d", len(v))
	}
}

func TestOllamaProviderSingleEmbed(t *testing.T) {
	// 模拟 Ollama /api/embeddings 响应
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			t.Errorf("expected POST, got %s", r.Method)
		}
		if r.URL.Path != "/api/embeddings" {
			t.Errorf("expected /api/embeddings, got %s", r.URL.Path)
		}

		var body struct {
			Model  string `json:"model"`
			Prompt string `json:"prompt"`
		}
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
			t.Errorf("failed to decode body: %v", err)
		}
		if body.Model != "nomic-embed-text" {
			t.Errorf("expected model nomic-embed-text, got %s", body.Model)
		}
		if body.Prompt != "hello world" {
			t.Errorf("expected prompt 'hello world', got %s", body.Prompt)
		}

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]any{
			"embedding": []float32{0.1, 0.2, 0.3},
		})
	}))
	defer server.Close()

	p := &OllamaProvider{
		baseURL: server.URL,
		model:   "nomic-embed-text",
		client:  server.Client(),
	}
	ctx := context.Background()
	v, err := p.Embed(ctx, "hello world")
	if err != nil {
		t.Fatalf("Embed returned error: %v", err)
	}
	if len(v) != 3 {
		t.Fatalf("expected 3 values, got %d", len(v))
	}
	if v[0] != 0.1 || v[1] != 0.2 || v[2] != 0.3 {
		t.Fatalf("unexpected embedding values: %v", v)
	}
}

func TestOllamaProviderEmptyEmbedding(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]any{
			"embedding": []float32{},
		})
	}))
	defer server.Close()

	p := &OllamaProvider{
		baseURL: server.URL,
		model:   "nomic-embed-text",
		client:  server.Client(),
	}
	_, err := p.Embed(context.Background(), "test")
	if err == nil {
		t.Fatal("expected error for empty embedding, got nil")
	}
}

func TestOllamaProviderErrorStatus(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
	}))
	defer server.Close()

	p := &OllamaProvider{
		baseURL: server.URL,
		model:   "nomic-embed-text",
		client:  server.Client(),
	}
	_, err := p.Embed(context.Background(), "test")
	if err == nil {
		t.Fatal("expected error for 500 status, got nil")
	}
}
