package prompt

import (
	"fmt"
	"strings"

	"github.com/knowflow/rag-go/internal/llm"
	"github.com/knowflow/rag-go/internal/types"
)

// BuildMessages 构建 LLM Chat messages。
func BuildMessages(question string, sources []types.SourceChunk) []llm.Message {
	return BuildMessagesForIntent(question, sources, "qa")
}

func BuildMessagesForIntent(question string, sources []types.SourceChunk, intent string) []llm.Message {
	var sb strings.Builder
	sb.WriteString("你是一个专业的知识库问答助手。请根据以下参考资料回答用户问题。\n")
	sb.WriteString("如果参考资料中没有相关信息，请明确说明无法从知识库中找到答案，不要编造。\n\n")
	switch intent {
	case "summarize":
		sb.WriteString("用户需要总结，请用结构化段落提炼重点，并保留基于资料的边界。\n")
	case "study_plan":
		sb.WriteString("用户需要学习计划，请按阶段输出目标、材料依据和行动建议。\n")
	case "code_analysis":
		sb.WriteString("用户需要代码或技术文档分析，请输出问题定位、依据片段和建议。\n")
	case "report":
		sb.WriteString("用户需要报告草稿，请只基于资料生成大纲和要点，不要扩写无依据内容。\n")
	}
	sb.WriteString("\n")
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
