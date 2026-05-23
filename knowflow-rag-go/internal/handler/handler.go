package handler

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/knowflow/rag-go/internal/service"
	"github.com/knowflow/rag-go/internal/types"
)

type Handler struct {
	rag *service.RAGService
}

func New(rag *service.RAGService) *Handler {
	return &Handler{rag: rag}
}

// Health GET /health
func (h *Handler) Health(c *gin.Context) {
	c.JSON(http.StatusOK, gin.H{
		"status":  "ok",
		"version": "0.1.0",
	})
}

// Ask POST /rag/ask
func (h *Handler) Ask(c *gin.Context) {
	var req types.RagRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "参数错误: " + err.Error()})
		return
	}

	resp, err := h.rag.Ask(c.Request.Context(), req)
	if err != nil {
		log.Printf("RAG 问答失败: %v", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "问答失败: " + err.Error()})
		return
	}

	c.JSON(http.StatusOK, resp)
}

// AskStream POST /rag/ask/stream
func (h *Handler) AskStream(c *gin.Context) {
	var req types.RagRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "参数错误: " + err.Error()})
		return
	}

	c.Header("Content-Type", "text/event-stream")
	c.Header("Cache-Control", "no-cache")
	c.Header("Connection", "keep-alive")
	c.Header("X-Accel-Buffering", "no")

	tokenCh, sourceCh, errCh := h.rag.AskStream(c.Request.Context(), req)
	flusher, _ := c.Writer.(http.Flusher)

	for {
		select {
		case token, ok := <-tokenCh:
			if !ok {
				// token channel 关闭，检查是否有 sources
				select {
				case sources := <-sourceCh:
					data, _ := json.Marshal(gin.H{"type": "sources", "sources": sources})
					fmt.Fprintf(c.Writer, "data: %s\n\n", data)
					if flusher != nil {
						flusher.Flush()
					}
				default:
				}
				fmt.Fprintf(c.Writer, "data: {\"type\":\"done\"}\n\n")
				if flusher != nil {
					flusher.Flush()
				}
				return
			}
			data, _ := json.Marshal(gin.H{"type": "token", "content": token})
			fmt.Fprintf(c.Writer, "data: %s\n\n", data)
			if flusher != nil {
				flusher.Flush()
			}

		case err := <-errCh:
			fmt.Fprintf(c.Writer, "data: {\"type\":\"error\",\"message\":\"%s\"}\n\n", err.Error())
			if flusher != nil {
				flusher.Flush()
			}
			return
		}
	}
}
