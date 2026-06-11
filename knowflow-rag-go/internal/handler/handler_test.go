package handler

import (
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/gin-gonic/gin"
	"github.com/knowflow/rag-go/internal/config"
)

func TestPendingStreamErrorPrefersBufferedError(t *testing.T) {
	expected := errors.New("stream failed")
	errCh := make(chan error, 1)
	errCh <- expected
	close(errCh)

	actual, ok := pendingStreamError(errCh)
	if !ok {
		t.Fatal("expected pending error")
	}
	if actual != expected {
		t.Fatalf("expected %v, got %v", expected, actual)
	}
}

func TestPendingStreamErrorIgnoresClosedEmptyChannel(t *testing.T) {
	errCh := make(chan error)
	close(errCh)

	if err, ok := pendingStreamError(errCh); ok || err != nil {
		t.Fatalf("expected no pending error, got ok=%v err=%v", ok, err)
	}
}

func TestRequireInternalTokenRejectsMissingToken(t *testing.T) {
	gin.SetMode(gin.TestMode)
	router := gin.New()
	router.Use(RequireInternalToken("expected-token"))
	router.GET("/rag/ask", func(c *gin.Context) {
		c.Status(http.StatusOK)
	})

	req := httptest.NewRequest(http.MethodGet, "/rag/ask", nil)
	resp := httptest.NewRecorder()
	router.ServeHTTP(resp, req)

	if resp.Code != http.StatusUnauthorized {
		t.Fatalf("expected 401, got %d", resp.Code)
	}
}

func TestRequireInternalTokenRejectsWrongToken(t *testing.T) {
	gin.SetMode(gin.TestMode)
	router := gin.New()
	router.Use(RequireInternalToken("expected-token"))
	router.GET("/rag/ask", func(c *gin.Context) {
		c.Status(http.StatusOK)
	})

	req := httptest.NewRequest(http.MethodGet, "/rag/ask", nil)
	req.Header.Set(InternalTokenHeader, "wrong-token")
	resp := httptest.NewRecorder()
	router.ServeHTTP(resp, req)

	if resp.Code != http.StatusUnauthorized {
		t.Fatalf("expected 401, got %d", resp.Code)
	}
}

func TestRequireInternalTokenAllowsCorrectToken(t *testing.T) {
	gin.SetMode(gin.TestMode)
	router := gin.New()
	router.Use(RequireInternalToken("expected-token"))
	router.GET("/rag/ask", func(c *gin.Context) {
		c.Status(http.StatusNoContent)
	})

	req := httptest.NewRequest(http.MethodGet, "/rag/ask", nil)
	req.Header.Set(InternalTokenHeader, "expected-token")
	resp := httptest.NewRecorder()
	router.ServeHTTP(resp, req)

	if resp.Code != http.StatusNoContent {
		t.Fatalf("expected 204, got %d", resp.Code)
	}
}

func TestHealthOmitsConfigSummary(t *testing.T) {
	gin.SetMode(gin.TestMode)
	h := New(nil, &config.Config{LLMProvider: "openai", EmbeddingProvider: "openai"})
	router := gin.New()
	router.GET("/health", h.Health)

	req := httptest.NewRequest(http.MethodGet, "/health", nil)
	resp := httptest.NewRecorder()
	router.ServeHTTP(resp, req)

	if resp.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", resp.Code)
	}
	if body := resp.Body.String(); body == "" || contains(body, "config") || contains(body, "openai") {
		t.Fatalf("health response leaked config: %s", body)
	}
}

func contains(value string, needle string) bool {
	return len(needle) == 0 || len(value) >= len(needle) && func() bool {
		for i := 0; i <= len(value)-len(needle); i++ {
			if value[i:i+len(needle)] == needle {
				return true
			}
		}
		return false
	}()
}
