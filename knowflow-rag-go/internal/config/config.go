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
		Port:              getEnv("RAG_PORT", "8090"),
		DBDSN:             getEnv("RAG_DB_DSN", "postgres://knowflow:knowflow123@localhost:5432/knowflow?sslmode=disable"),
		LLMProvider:       getEnv("RAG_LLM_PROVIDER", "mock"),
		LLMAPIKey:         getEnv("RAG_LLM_API_KEY", ""),
		LLMBaseURL:        getEnv("RAG_LLM_BASE_URL", ""),
		LLMModel:          getEnv("RAG_LLM_MODEL", ""),
		RequestTimeout:    time.Duration(getEnvInt("RAG_REQUEST_TIMEOUT_SECONDS", 60)) * time.Second,
		EmbeddingProvider: getEnv("RAG_EMBEDDING_PROVIDER", getEnv("RAG_LLM_PROVIDER", "mock")),
		EmbeddingAPIKey:   getEnv("RAG_EMBEDDING_API_KEY", getEnv("RAG_LLM_API_KEY", "")),
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
	if err := validateProvider("RAG_LLM_PROVIDER", c.LLMProvider, c.LLMAPIKey); err != nil {
		return err
	}
	if err := validateProvider("RAG_EMBEDDING_PROVIDER", c.EmbeddingProvider, c.EmbeddingAPIKey); err != nil {
		return err
	}
	return nil
}

func (c *Config) PublicSummary() map[string]any {
	return map[string]any{
		"llmProvider":       c.LLMProvider,
		"llmModel":          c.LLMModel,
		"embeddingProvider": c.EmbeddingProvider,
		"embeddingModel":    c.EmbeddingModel,
		"embeddingDim":      c.EmbeddingDim,
		"defaultTopK":       c.DefaultTopK,
		"maxTopK":           c.MaxTopK,
		"requestTimeoutSec": int(c.RequestTimeout.Seconds()),
	}
}

func validateProvider(envName string, provider string, apiKey string) error {
	switch strings.ToLower(provider) {
	case "mock", "ollama":
		return nil
	case "openai", "deepseek":
		if apiKey == "" {
			return fmt.Errorf("%s=%s 时必须配置 API key", envName, provider)
		}
		return nil
	default:
		return fmt.Errorf("%s 不支持: %s", envName, provider)
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
