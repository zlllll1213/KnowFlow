package handler

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
	"github.com/knowflow/rag-go/internal/config"
	"github.com/knowflow/rag-go/internal/service"
	"github.com/knowflow/rag-go/internal/types"
)

type Handler struct {
	rag *service.RAGService
	cfg *config.Config
}

func New(rag *service.RAGService, cfg *config.Config) *Handler {
	return &Handler{rag: rag, cfg: cfg}
}

func (h *Handler) Health(c *gin.Context) {
	c.JSON(http.StatusOK, gin.H{
		"status":  "ok",
		"version": "0.1.0",
		"config":  h.cfg.PublicSummary(),
	})
}

func (h *Handler) Ask(c *gin.Context) {
	var req types.RagRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "参数错误"})
		return
	}

	ctx, cancel := context.WithTimeout(c.Request.Context(), h.cfg.RequestTimeout)
	defer cancel()
	resp, err := h.rag.Ask(ctx, req)
	if err != nil {
		log.Printf("RAG 问答失败: %v", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "问答失败，请稍后重试"})
		return
	}
	c.JSON(http.StatusOK, resp)
}

func (h *Handler) AskStream(c *gin.Context) {
	var req types.RagRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "参数错误"})
		return
	}

	prepareSSE(c)
	ctx, cancel := context.WithTimeout(c.Request.Context(), h.cfg.RequestTimeout)
	defer cancel()
	tokenCh, sourceCh, errCh := h.rag.AskStream(ctx, req)
	flusher, ok := c.Writer.(http.Flusher)
	if !ok {
		log.Println("WARNING: ResponseWriter 不支持 http.Flusher，SSE 推送可能延迟")
	}

	for {
		select {
		case token, ok := <-tokenCh:
			if !ok {
				if err, hasErr := pendingStreamError(errCh); hasErr {
					writeSSE(c, flusher, "error", gin.H{"type": "error", "message": err.Error()})
					return
				}
				select {
				case sources := <-sourceCh:
					writeSSE(c, flusher, "sources", gin.H{"type": "sources", "sources": sources})
				default:
				}
				if err, hasErr := pendingStreamError(errCh); hasErr {
					writeSSE(c, flusher, "error", gin.H{"type": "error", "message": err.Error()})
					return
				}
				writeSSE(c, flusher, "done", gin.H{"type": "done"})
				return
			}
			writeSSE(c, flusher, "token", gin.H{"type": "token", "content": token})
		case err, ok := <-errCh:
			if !ok {
				errCh = nil
				continue
			}
			writeSSE(c, flusher, "error", gin.H{"type": "error", "message": err.Error()})
			return
		}
	}
}

func (h *Handler) AskAgent(c *gin.Context) {
	var req types.RagRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "参数错误"})
		return
	}

	ctx, cancel := context.WithTimeout(c.Request.Context(), h.cfg.RequestTimeout)
	defer cancel()
	resp, err := h.rag.AskAgent(ctx, req)
	if err != nil {
		log.Printf("Agent 问答失败: %v", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Agent 问答失败，请稍后重试"})
		return
	}
	c.JSON(http.StatusOK, resp)
}

func (h *Handler) AskAgentStream(c *gin.Context) {
	var req types.RagRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "参数错误"})
		return
	}

	prepareSSE(c)
	ctx, cancel := context.WithTimeout(c.Request.Context(), h.cfg.RequestTimeout)
	defer cancel()
	tokenCh, sourceCh, metaCh, errCh := h.rag.AskAgentStream(ctx, req)
	flusher, ok := c.Writer.(http.Flusher)
	if !ok {
		log.Println("WARNING: ResponseWriter 不支持 http.Flusher，SSE 推送可能延迟")
	}

	for {
		select {
		case meta, ok := <-metaCh:
			if !ok {
				metaCh = nil
				continue
			}
			writeSSE(c, flusher, "meta", gin.H{
				"type":       "meta",
				"intent":     meta.Intent,
				"confidence": meta.Confidence,
				"trace":      meta.Trace,
				"latencyMs":  meta.LatencyMs,
			})
		case token, ok := <-tokenCh:
			if !ok {
				if err, hasErr := pendingStreamError(errCh); hasErr {
					writeSSE(c, flusher, "error", gin.H{"type": "error", "message": err.Error()})
					return
				}
				select {
				case sources := <-sourceCh:
					writeSSE(c, flusher, "sources", gin.H{"type": "sources", "sources": sources})
				default:
				}
				if err, hasErr := pendingStreamError(errCh); hasErr {
					writeSSE(c, flusher, "error", gin.H{"type": "error", "message": err.Error()})
					return
				}
				writeSSE(c, flusher, "done", gin.H{"type": "done"})
				return
			}
			writeSSE(c, flusher, "token", gin.H{"type": "token", "content": token})
		case err, ok := <-errCh:
			if !ok {
				errCh = nil
				continue
			}
			writeSSE(c, flusher, "error", gin.H{"type": "error", "message": err.Error()})
			return
		}
	}
}

// RequestID 中间件：为每个请求注入唯一 ID 并写入响应头。
func RequestID() gin.HandlerFunc {
	return func(c *gin.Context) {
		requestID := c.GetHeader("X-Request-ID")
		if requestID == "" {
			requestID = uuid.NewString()
		}
		c.Set("requestId", requestID)
		c.Header("X-Request-ID", requestID)
		c.Next()
	}
}

func prepareSSE(c *gin.Context) {
	c.Header("Content-Type", "text/event-stream")
	c.Header("Cache-Control", "no-cache")
	c.Header("Connection", "keep-alive")
	c.Header("X-Accel-Buffering", "no")
}

func writeSSE(c *gin.Context, flusher http.Flusher, event string, payload any) {
	data, _ := json.Marshal(payload)
	fmt.Fprintf(c.Writer, "event: %s\n", event)
	fmt.Fprintf(c.Writer, "data: %s\n\n", data)
	if flusher != nil {
		flusher.Flush()
	}
}

func pendingStreamError(errCh <-chan error) (error, bool) {
	select {
	case err, ok := <-errCh:
		return err, ok && err != nil
	default:
		return nil, false
	}
}
