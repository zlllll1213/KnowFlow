package agent

import (
	"strings"
	"testing"

	"github.com/knowflow/rag-go/internal/types"
)

func TestRouteIntent(t *testing.T) {
	cases := []struct {
		question string
		intent   string
	}{
		{"帮我总结这份文档", IntentSummarize},
		{"给我一个学习计划", IntentStudyPlan},
		{"这个接口报错怎么分析", IntentCodeAnalysis},
		{"KnowFlow 是什么", IntentQA},
		{"   ", IntentUnknown},
	}

	for _, tc := range cases {
		intent, detail := RouteIntent(tc.question)
		if intent != tc.intent {
			t.Fatalf("question=%q expected intent=%s got=%s", tc.question, tc.intent, intent)
		}
		if strings.TrimSpace(detail) == "" {
			t.Fatalf("question=%q should return trace detail", tc.question)
		}
	}
}

func TestEvaluateCitationsEmptySources(t *testing.T) {
	decision := EvaluateCitations(nil)
	if decision.AllowAnswer {
		t.Fatal("empty sources should not allow answer generation")
	}
	if decision.Confidence != 0 {
		t.Fatalf("empty sources confidence should be 0, got %.2f", decision.Confidence)
	}
}

func TestEvaluateCitationsSingleSourceCapsConfidence(t *testing.T) {
	decision := EvaluateCitations([]types.SourceChunk{{Score: 0.92}})
	if !decision.AllowAnswer {
		t.Fatal("single source should still allow answer generation")
	}
	if decision.Confidence > 0.6 {
		t.Fatalf("single source confidence should be capped at 0.6, got %.2f", decision.Confidence)
	}
}

func TestEvaluateCitationsWeakEvidenceAddsPrefix(t *testing.T) {
	decision := EvaluateCitations([]types.SourceChunk{{Score: 0.42}, {Score: 0.35}})
	if decision.Confidence > 0.5 {
		t.Fatalf("weak evidence confidence should be capped at 0.5, got %.2f", decision.Confidence)
	}
	if decision.Prefix != WeakEvidencePrefix {
		t.Fatalf("weak evidence should add prefix, got %q", decision.Prefix)
	}
}

func TestEvaluateCitationsSufficientSources(t *testing.T) {
	decision := EvaluateCitations([]types.SourceChunk{{Score: 0.86}, {Score: 0.78}, {Score: 0.74}})
	if !decision.AllowAnswer {
		t.Fatal("sufficient sources should allow answer generation")
	}
	if decision.Confidence <= 0.6 {
		t.Fatalf("sufficient sources should have confidence above 0.6, got %.2f", decision.Confidence)
	}
	if decision.Prefix != "" {
		t.Fatalf("sufficient sources should not add prefix, got %q", decision.Prefix)
	}
}
