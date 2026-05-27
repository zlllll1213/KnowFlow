package agent

import (
	"fmt"
	"math"
	"strings"

	"github.com/knowflow/rag-go/internal/types"
)

const (
	IntentQA           = "qa"
	IntentSummarize    = "summarize"
	IntentStudyPlan    = "study_plan"
	IntentCodeAnalysis = "code_analysis"
	IntentUnknown      = "unknown"

	InsufficientAnswer = "知识库中未找到足够依据，无法回答该问题。"
	WeakEvidencePrefix = "当前知识库依据较弱，仅供参考。"
	UnknownAnswer      = "暂时无法判断该问题是否适合由当前知识库回答，请换一种更具体的问法。"
)

type GuardDecision struct {
	AllowAnswer bool
	Confidence  float64
	Prefix      string
	Detail      string
}

func RouteIntent(question string) (string, string) {
	q := strings.ToLower(strings.TrimSpace(question))
	intent := IntentQA
	detail := "识别为普通知识库问答"

	switch {
	case q == "":
		intent = IntentUnknown
		detail = "问题为空，无法判断意图"
	case containsAny(q, "总结", "summary", "概括", "归纳"):
		intent = IntentSummarize
		detail = "识别为文档总结"
	case containsAny(q, "学习计划", "study plan", "怎么学", "学习路线", "路线图"):
		intent = IntentStudyPlan
		detail = "识别为学习计划"
	case containsAny(q, "代码", "接口", "报错", "bug", "api", "堆栈", "异常", "配置"):
		intent = IntentCodeAnalysis
		detail = "识别为代码或技术文档分析"
	}

	return intent, detail
}

func BuildRetrievalQuery(question string, intent string) string {
	switch intent {
	case IntentSummarize:
		return question + " 总结 重点 结论"
	case IntentStudyPlan:
		return question + " 学习 路线 阶段 计划"
	case IntentCodeAnalysis:
		return question + " 代码 接口 错误 配置 实现"
	default:
		return question
	}
}

func EvaluateCitations(sources []types.SourceChunk) GuardDecision {
	if len(sources) == 0 {
		return GuardDecision{
			AllowAnswer: false,
			Confidence:  0,
			Detail:      "未检索到引用片段，禁止调用 LLM 编造答案",
		}
	}

	maxScore, avgScore := scoreStats(sources)
	confidence := 0.7*maxScore + 0.3*avgScore
	confidence += math.Min(0.15, 0.05*float64(len(sources)-1))
	confidence = clamp(confidence, 0, 0.95)

	details := []string{fmt.Sprintf("sources=%d maxScore=%.2f avgScore=%.2f", len(sources), maxScore, avgScore)}
	prefix := ""

	if len(sources) < 2 {
		confidence = math.Min(confidence, 0.6)
		details = append(details, "引用数量小于 2，confidence capped at 0.60")
	}
	if maxScore < 0.5 {
		confidence = math.Min(confidence, 0.5)
		prefix = WeakEvidencePrefix
		details = append(details, "最高 score 低于 0.50，回答增加弱依据提示")
	}

	return GuardDecision{
		AllowAnswer: true,
		Confidence:  round2(confidence),
		Prefix:      prefix,
		Detail:      strings.Join(details, "; "),
	}
}

func containsAny(text string, needles ...string) bool {
	for _, needle := range needles {
		if strings.Contains(text, needle) {
			return true
		}
	}
	return false
}

func scoreStats(sources []types.SourceChunk) (float64, float64) {
	var maxScore float64
	var sum float64
	for _, source := range sources {
		score := clamp(source.Score, 0, 1)
		if score > maxScore {
			maxScore = score
		}
		sum += score
	}
	return maxScore, sum / float64(len(sources))
}

func clamp(value float64, minValue float64, maxValue float64) float64 {
	if value < minValue {
		return minValue
	}
	if value > maxValue {
		return maxValue
	}
	return value
}

func round2(value float64) float64 {
	return math.Round(value*100) / 100
}
