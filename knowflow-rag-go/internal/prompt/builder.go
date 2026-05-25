package prompt

import (
	"fmt"
	"strings"

	"github.com/knowflow/rag-go/internal/llm"
	"github.com/knowflow/rag-go/internal/types"
)

// BuildMessages 构建 LLM Chat messages。
func BuildMessages(question string, sources []types.SourceChunk) []llm.Message {
	var sb strings.Builder
	sb.WriteString("你是一个专业的知识库问答助手。请根据以下参考资料回答用户问题。\n")
	sb.WriteString("如果参考资料中没有相关信息，请明确说明无法从知识库中找到答案，不要编造。\n\n")
	sb.WriteString("参考资料：\n---\n")

	if len(sources) == 0 {
		sb.WriteString("（没有检索到相关资料）\n---\n")
	}

	for _, s := range sources {
		sb.WriteString(fmt.Sprintf("[文档：%s，片段 %d]\n%s\n---\n",
			s.FileName, s.ChunkIndex, truncate(s.Content, 500)))
	}

	messages := []llm.Message{
		{Role: "system", Content: sb.String()},
		{Role: "user", Content: question},
	}
	return messages
}

func truncate(s string, maxLen int) string {
	runes := []rune(s)
	if len(runes) <= maxLen {
		return s
	}
	return string(runes[:maxLen]) + "..."
}
