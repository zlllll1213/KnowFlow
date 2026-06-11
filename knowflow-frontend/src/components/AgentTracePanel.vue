<template>
  <div class="agent-trace-panel">
    <div class="panel-header">
      <el-icon><Operation /></el-icon>
      <span>Agent Trace</span>
      <span v-if="intent" class="intent">{{ intentLabel }}</span>
    </div>

    <div v-if="confidence !== null || latencyMs !== null" class="agent-meta">
      <div v-if="confidence !== null" class="meta-item">
        <span class="meta-label">Confidence</span>
        <strong :class="confidenceClass">{{ formatConfidence(confidence ?? 0) }}</strong>
      </div>
      <div v-if="latencyMs !== null" class="meta-item">
        <span class="meta-label">Latency</span>
        <strong>{{ latencyMs }}ms</strong>
      </div>
    </div>

    <div v-if="!trace.length" class="empty-state">
      <el-icon><Connection /></el-icon>
      <p>Agent 模式回答后，执行轨迹将显示在此</p>
    </div>

    <div v-else class="trace-list">
      <div v-for="(step, index) in trace" :key="`${step.step}-${index}`" class="trace-step">
        <div class="step-index">{{ index + 1 }}</div>
        <div class="step-body">
          <div class="step-name">{{ stepLabel(step.step) }}</div>
          <div class="step-detail">{{ step.detail }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { AgentTraceStep, AgentResponse } from '@/types/chat'

const props = defineProps<{
  trace: AgentTraceStep[]
  confidence?: number | null
  intent?: AgentResponse['intent'] | string | null
  latencyMs?: number | null
}>()

const intentLabels: Record<string, string> = {
  qa: '问答',
  summarize: '总结',
  study_plan: '学习计划',
  code_analysis: '技术分析',
  unknown: '未知',
}

const intentLabel = computed(() => props.intent ? (intentLabels[props.intent] ?? props.intent) : '')
const confidenceClass = computed(() => {
  const value = props.confidence ?? 0
  if (value >= 0.75) return 'high'
  if (value >= 0.5) return 'medium'
  return 'low'
})

function formatConfidence(value: number) {
  return `${Math.round(Math.max(0, Math.min(1, value)) * 100)}%`
}

function stepLabel(step: string) {
  const labels: Record<string, string> = {
    router: 'Router',
    retriever: 'Retriever',
    answer: 'Answer',
    citation_guard: 'Citation Guard',
  }
  return labels[step] ?? step
}
</script>

<style scoped>
.agent-trace-panel { height: 100%; display: flex; flex-direction: column; border-top: 1px solid var(--color-border); background: rgba(7, 28, 62, .32); }
.panel-header {
  display: flex; align-items: center; gap: 7px; padding: 14px 16px 10px;
  font-size: 13px; font-weight: 900; color: var(--color-text-secondary);
  border-bottom: 1px solid var(--color-border);
}
.intent {
  margin-left: auto; padding: 2px 7px; border-radius: 6px;
  background: rgba(47, 114, 255, .16); color: #7db4ff;
  font-size: 11px; font-weight: 900;
}
.agent-meta { display: grid; grid-template-columns: 1fr 1fr; border-bottom: 1px solid var(--color-border); }
.meta-item { padding: 10px 14px; display: flex; flex-direction: column; gap: 3px; }
.meta-item + .meta-item { border-left: 1px solid var(--color-border); }
.meta-label { font-size: 10px; color: var(--color-text-muted); text-transform: uppercase; letter-spacing: .04em; }
.meta-item strong { font-size: 14px; color: var(--color-text-primary); }
.meta-item strong.high { color: var(--color-success); }
.meta-item strong.medium { color: var(--color-warning); }
.meta-item strong.low { color: var(--color-danger); }
.empty-state {
  flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 9px; padding: 20px; color: var(--color-text-muted); font-size: 12px; text-align: center; line-height: 1.45;
}
.empty-state .el-icon { font-size: 24px; }
.trace-list { flex: 1; overflow-y: auto; padding: 12px 14px; display: flex; flex-direction: column; gap: 12px; }
.trace-step { display: grid; grid-template-columns: 24px 1fr; gap: 9px; }
.step-index {
  width: 22px; height: 22px; border-radius: 50%; background: rgba(47, 114, 255, .18);
  border: 1px solid var(--color-border); color: var(--color-text-secondary);
  display: flex; align-items: center; justify-content: center; font-size: 11px; font-weight: 700;
}
.step-body { min-width: 0; padding-bottom: 12px; border-bottom: 1px solid var(--color-border); }
.trace-step:last-child .step-body { border-bottom: none; padding-bottom: 0; }
.step-name { font-size: 12px; font-weight: 700; color: var(--color-text-primary); margin-bottom: 3px; }
.step-detail { font-size: 12px; color: var(--color-text-secondary); line-height: 1.45; word-break: break-word; }
</style>
