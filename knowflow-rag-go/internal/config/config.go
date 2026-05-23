package config

import "os"

type Config struct {
	Port            string
	DBDSN           string
	LLMProvider     string
	LLMAPIKey       string
	LLMBaseURL      string
	LLMModel        string
	EmbeddingDim    int
	DefaultTopK     int
}

func Load() *Config {
	return &Config{
		Port:         getEnv("RAG_PORT", "8090"),
		DBDSN:        getEnv("RAG_DB_DSN", "postgres://knowflow:knowflow123@localhost:5432/knowflow?sslmode=disable"),
		LLMProvider:  getEnv("RAG_LLM_PROVIDER", "mock"),
		LLMAPIKey:    getEnv("RAG_LLM_API_KEY", ""),
		LLMBaseURL:   getEnv("RAG_LLM_BASE_URL", ""),
		LLMModel:     getEnv("RAG_LLM_MODEL", ""),
		EmbeddingDim: 1536,
		DefaultTopK:  5,
	}
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
