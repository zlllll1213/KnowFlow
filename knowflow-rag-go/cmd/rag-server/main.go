package main

import (
	"log"

	"github.com/gin-gonic/gin"
	"github.com/knowflow/rag-go/internal/config"
	"github.com/knowflow/rag-go/internal/handler"
	"github.com/knowflow/rag-go/internal/llm"
	"github.com/knowflow/rag-go/internal/retriever"
	"github.com/knowflow/rag-go/internal/service"
)

func main() {
	cfg := config.Load()

	// 初始化检索器
	ret, err := retriever.New(cfg)
	if err != nil {
		log.Fatalf("初始化检索器失败: %v", err)
	}
	defer ret.Close()

	// 初始化 LLM Provider
	llmProvider := llm.NewProvider(cfg)

	// 初始化 RAG Service
	ragService := service.New(ret, llmProvider, cfg.DefaultTopK)

	// 初始化 Handler
	h := handler.New(ragService)

	// 路由
	r := gin.Default()
	r.GET("/health", h.Health)
	r.POST("/rag/ask", h.Ask)
	r.POST("/rag/ask/stream", h.AskStream)

	log.Printf("Go RAG Service 启动: http://localhost:%s", cfg.Port)
	if err := r.Run(":" + cfg.Port); err != nil {
		log.Fatalf("服务启动失败: %v", err)
	}
}
