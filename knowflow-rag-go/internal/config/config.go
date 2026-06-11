package config

import (
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"
)

type Config struct {
	Port              string
	DBDSN             string
	LLMProvider       string
	LLMAPIKey         string
	LLMBaseURL        string
	LLMModel          string
	LLMThinking       bool
	RequestTimeout    time.Duration
	EmbeddingProvider string
	EmbeddingAPIKey   string
	EmbeddingBaseURL  string
	EmbeddingModel    string
	EmbeddingDim      int
	DefaultTopK       int
	MaxTopK           int
}

func Load() *Config {
	return &Config{
		Port:           getEnv("RAG_PORT", "8090"),
		DBDSN:          getEnv("RAG_DB_DSN", ""),
		LLMProvider:    getEnv("RAG_LLM_PROVIDER", "mock"),
		LLMAPIKey:      getEnv("RAG_LLM_API_KEY", ""),
		LLMBaseURL:     getEnv("RAG_LLM_BASE_URL", ""),
		LLMModel:       getEnv("RAG_LLM_MODEL", ""),
		LLMThinking:    getEnvBool("RAG_LLM_THINKING_ENABLED", false),
		RequestTimeout: time.Duration(getEnvInt("RAG_REQUEST_TIMEOUT_SECONDS", 60)) * time.Second,
		// Embedding 必须独立配置，避免把 LLM API Key 误发到不匹配的 embedding endpoint。
		EmbeddingProvider: getEnv("RAG_EMBEDDING_PROVIDER", "mock"),
		EmbeddingAPIKey:   getEnv("RAG_EMBEDDING_API_KEY", ""),
		EmbeddingBaseURL:  getEnv("RAG_EMBEDDING_BASE_URL", ""),
		EmbeddingModel:    getEnv("RAG_EMBEDDING_MODEL", "text-embedding-3-small"),
		EmbeddingDim:      getEnvInt("RAG_EMBEDDING_DIM", 1536),
		DefaultTopK:       getEnvInt("RAG_DEFAULT_TOP_K", 5),
		MaxTopK:           getEnvInt("RAG_MAX_TOP_K", 20),
	}
}

func (c *Config) Validate() error {
	if c.Port == "" {
		return fmt.Errorf("RAG_PORT 不能为空")
	}
	if c.DBDSN == "" {
		return fmt.Errorf("RAG_DB_DSN 不能为空")
	}
	if c.DefaultTopK <= 0 {
		return fmt.Errorf("RAG_DEFAULT_TOP_K 必须大于 0")
	}
	if c.MaxTopK <= 0 {
		return fmt.Errorf("RAG_MAX_TOP_K 必须大于 0")
	}
	if c.DefaultTopK > c.MaxTopK {
		return fmt.Errorf("RAG_DEFAULT_TOP_K 不能大于 RAG_MAX_TOP_K")
	}
	if c.EmbeddingDim <= 0 {
		return fmt.Errorf("RAG_EMBEDDING_DIM 必须大于 0")
	}
	if c.RequestTimeout <= 0 {
		return fmt.Errorf("RAG_REQUEST_TIMEOUT_SECONDS 必须大于 0")
	}
	if err := validateLLMProvider(c.LLMProvider, c.LLMAPIKey); err != nil {
		return err
	}
	if err := validateEmbeddingProvider(c.EmbeddingProvider, c.EmbeddingAPIKey); err != nil {
		return err
	}
	return nil
}

func (c *Config) PublicSummary() map[string]any {
	return map[string]any{
		"llmProvider":       c.LLMProvider,
		"llmModel":          c.LLMModel,
		"llmThinking":       c.LLMThinking,
		"embeddingProvider": c.EmbeddingProvider,
		"embeddingModel":    c.EmbeddingModel,
		"embeddingDim":      c.EmbeddingDim,
		"defaultTopK":       c.DefaultTopK,
		"maxTopK":           c.MaxTopK,
		"requestTimeoutSec": int(c.RequestTimeout.Seconds()),
	}
}

func validateLLMProvider(provider string, apiKey string) error {
	switch strings.ToLower(provider) {
	case "mock", "ollama":
		return nil
	case "openai", "deepseek":
		if apiKey == "" {
			return fmt.Errorf("RAG_LLM_PROVIDER=%s 时必须配置 RAG_LLM_API_KEY", provider)
		}
		return nil
	default:
		return fmt.Errorf("RAG_LLM_PROVIDER 不支持: %s", provider)
	}
}

func validateEmbeddingProvider(provider string, apiKey string) error {
	switch strings.ToLower(provider) {
	case "mock", "ollama":
		return nil
	case "openai":
		if apiKey == "" {
			return fmt.Errorf("RAG_EMBEDDING_PROVIDER=%s 时必须配置 RAG_EMBEDDING_API_KEY", provider)
		}
		return nil
	case "deepseek":
		return fmt.Errorf("RAG_EMBEDDING_PROVIDER=deepseek 暂不支持；请显式使用 mock、openai 或 ollama，避免误用 DeepSeek API Key")
	default:
		return fmt.Errorf("RAG_EMBEDDING_PROVIDER 不支持: %s", provider)
	}
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

func getEnvInt(key string, fallback int) int {
	v := os.Getenv(key)
	if v == "" {
		return fallback
	}
	parsed, err := strconv.Atoi(v)
	if err != nil {
		return fallback
	}
	return parsed
}

func getEnvBool(key string, fallback bool) bool {
	v := strings.TrimSpace(os.Getenv(key))
	if v == "" {
		return fallback
	}
	parsed, err := strconv.ParseBool(v)
	if err != nil {
		return fallback
	}
	return parsed
}
