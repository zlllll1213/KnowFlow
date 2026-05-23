package llm

import (
	"context"
	"fmt"
	"log"

	"github.com/knowflow/rag-go/internal/config"
)

// Provider LLM Provider 接口
type Provider interface {
	Chat(ctx context.Context, messages []Message) (string, error)
	ChatStream(ctx context.Context, messages []Message, out chan<- string) error
}

type Message struct {
	Role    string `json:"role"`
	Content string `json:"content"`
}

// NewProvider 根据配置创建 LLM Provider
func NewProvider(cfg *config.Config) Provider {
	switch cfg.LLMProvider {
	case "mock":
		return &MockProvider{}
	case "deepseek":
		return &MockProvider{label: "DeepSeek"} // TODO: 实现 DeepSeek
	case "openai":
		return &MockProvider{label: "OpenAI"} // TODO: 实现 OpenAI
	case "ollama":
		return &MockProvider{label: "Ollama"} // TODO: 实现 Ollama
	default:
		log.Printf("未知 LLM provider: %s, 回退到 mock", cfg.LLMProvider)
		return &MockProvider{}
	}
}

// MockProvider 模拟 LLM Provider。
// 返回基于 sources 的简单拼接说明，明确标记为 MOCK。
type MockProvider struct {
	label string
}

func (m *MockProvider) Chat(ctx context.Context, messages []Message) (string, error) {
	if m.label == "" {
		m.label = "Mock"
	}
	// 从 messages 中提取用户问题
	question := "（未知问题）"
	for _, msg := range messages {
		if msg.Role == "user" {
			question = msg.Content
			break
		}
	}
	return fmt.Sprintf("[%s 模式] 已收到您的问题：「%s」。\n根据知识库中的参考资料，我找到了一些相关内容。请查看右侧引用来源了解详情。\n\n提示：当前为 mock 回答模式。配置 RAG_LLM_PROVIDER 和 API key 后即可获得真实 AI 回答。", m.label, question), nil
}

func (m *MockProvider) ChatStream(ctx context.Context, messages []Message, out chan<- string) error {
	defer close(out)
	if m.label == "" {
		m.label = "Mock"
	}

	chunks := []string{
		fmt.Sprintf("[%s 流式模式] ", m.label),
		"正在分析您的问题……\n\n",
		"根据知识库中的参考资料，",
		"我为您整理了以下信息：\n\n",
		"这是基于当前知识库内容的回答。\n",
		"请查看右侧引用来源了解具体文档片段。\n\n",
		"💡 提示：当前为 mock 流式回答。配置真实 LLM 后可获得 AI 生成回答。",
	}

	for _, chunk := range chunks {
		select {
		case <-ctx.Done():
			return ctx.Err()
		case out <- chunk:
		}
	}
	return nil
}
