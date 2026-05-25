package prompt

import (
	"strings"
	"testing"

	"github.com/knowflow/rag-go/internal/types"
)

func TestBuildMessagesIncludesSources(t *testing.T) {
	messages := BuildMessages("问题", []types.SourceChunk{
		{FileName: "guide.md", ChunkIndex: 2, Content: "KnowFlow 是知识库问答平台"},
	})

	if len(messages) != 2 {
		t.Fatalf("expected 2 messages, got %d", len(messages))
	}
	if messages[0].Role != "system" || !strings.Contains(messages[0].Content, "guide.md") {
		t.Fatalf("system message missing source: %+v", messages[0])
	}
	if messages[1].Role != "user" || messages[1].Content != "问题" {
		t.Fatalf("user message mismatch: %+v", messages[1])
	}
}

func TestBuildMessagesHandlesNoSources(t *testing.T) {
	messages := BuildMessages("问题", nil)
	if !strings.Contains(messages[0].Content, "没有检索到相关资料") {
		t.Fatalf("system message should mention empty retrieval: %s", messages[0].Content)
	}
}
