<template>
  <div class="message" :class="msg.role">
    <div class="avatar">
      <el-icon v-if="msg.role === 'assistant'"><ChatDotRound /></el-icon>
      <span v-else>{{ initial }}</span>
    </div>
    <div class="bubble-wrap">
      <div v-if="msg.agentMode" class="agent-badge">
        <el-icon><Operation /></el-icon>
        <span>Agent</span>
        <span v-if="msg.intent" class="agent-intent">{{ intentLabel }}</span>
        <span v-if="typeof msg.confidence === 'number'" class="agent-confidence">{{ formatConfidence(msg.confidence) }}</span>
      </div>
      <div class="bubble markdown-body" v-html="renderedContent"></div>
      <div class="time">{{ formatTime(msg.createdAt) }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { ChatMessageVO } from '@/types/chat'
import { useAuthStore } from '@/stores/auth'
import { computed } from 'vue'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js/lib/common'
import { Renderer, marked } from 'marked'
import 'highlight.js/styles/github.css'

const props = defineProps<{ msg: ChatMessageVO }>()

const authStore = useAuthStore()
const initial = computed(() => (authStore.userInfo?.username?.[0] ?? 'U').toUpperCase())
const intentLabel = computed(() => {
  const labels: Record<string, string> = {
    qa: '问答',
    summarize: '总结',
    study_plan: '学习计划',
    code_analysis: '技术分析',
    unknown: '未知',
  }
  return props.msg.intent ? (labels[props.msg.intent] ?? props.msg.intent) : ''
})
const renderer = new Renderer()

renderer.code = ({ text, lang }) => {
  const language = (lang ?? '').trim().split(/\s+/)[0]
  const highlighted = language && hljs.getLanguage(language)
    ? hljs.highlight(text, { language }).value
    : hljs.highlightAuto(text).value
  const className = language ? ` language-${escapeHtml(language)}` : ''
  return `<pre><code class="hljs${className}">${highlighted}</code></pre>`
}

renderer.link = ({ href, title, tokens }) => {
  const text = marked.parser(tokens)
  const safeHref = escapeHtml(href)
  const safeTitle = title ? ` title="${escapeHtml(title)}"` : ''
  return `<a href="${safeHref}"${safeTitle} target="_blank" rel="noopener noreferrer">${text}</a>`
}

const renderedContent = computed(() => {
  const html = marked.parse(props.msg.content || '', {
    async: false,
    breaks: true,
    gfm: true,
    renderer,
  })
  return DOMPurify.sanitize(html, {
    ADD_ATTR: ['target', 'rel'],
  })
})

function formatTime(d: string) {
  if (!d) return ''
  return new Date(d).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

function formatConfidence(value: number) {
  return `${Math.round(Math.max(0, Math.min(1, value)) * 100)}%`
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}
</script>

<style scoped>
.message { display: flex; gap: 12px; padding: 4px 0; }
.message.user { flex-direction: row-reverse; }
.avatar {
  width: 34px; height: 34px; border-radius: 50%; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  font-size: 13px; font-weight: 600;
}
.assistant .avatar { background: linear-gradient(135deg, #25d3ff, #315dff); color: #fff; font-size: 16px; box-shadow: 0 0 22px rgba(49, 199, 255, .28); }
.user .avatar { background: #17386f; color: #dce8ff; }
.bubble-wrap { max-width: 72%; display: flex; flex-direction: column; gap: 4px; }
.user .bubble-wrap { align-items: flex-end; }
.agent-badge {
  width: fit-content; display: inline-flex; align-items: center; gap: 5px;
  padding: 3px 7px; border: 1px solid var(--color-border); border-radius: 6px;
  background: rgba(47, 114, 255, .12); color: #7db4ff; font-size: 11px; font-weight: 800;
}
.agent-badge .el-icon { font-size: 13px; }
.agent-intent, .agent-confidence {
  color: var(--color-text-secondary); font-weight: 600;
}
.agent-confidence {
  color: var(--color-success);
}
.bubble {
  padding: 12px 15px; border-radius: 10px; font-size: 14px; line-height: 1.65;
  word-break: break-word;
}
.assistant .bubble { background: rgba(7, 28, 62, .82); border: 1px solid var(--color-border); color: var(--color-text-primary); border-radius: 2px 10px 10px 10px; box-shadow: inset 0 1px 0 rgba(255,255,255,.05); }
.user .bubble { background: linear-gradient(135deg, #174da8, #2f72ff); color: #fff; border-radius: 10px 2px 10px 10px; }
.time { font-size: 11px; color: var(--color-text-muted); padding: 0 4px; }
.markdown-body :deep(p) { margin: 0 0 8px; }
.markdown-body :deep(p:last-child) { margin-bottom: 0; }
.markdown-body :deep(ul), .markdown-body :deep(ol) { margin: 6px 0 8px 20px; }
.markdown-body :deep(li + li) { margin-top: 3px; }
.markdown-body :deep(a) { color: var(--color-accent); text-decoration: none; font-weight: 500; }
.markdown-body :deep(a:hover) { text-decoration: underline; }
.markdown-body :deep(code:not(pre code)) {
  padding: 2px 5px; border-radius: 4px; background: rgba(49, 199, 255, .12);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; font-size: .92em;
}
.markdown-body :deep(pre) {
  margin: 8px 0; border-radius: 8px; overflow-x: auto;
  border: 1px solid var(--color-border); background: #020817;
}
.markdown-body :deep(pre code) {
  display: block; padding: 12px; background: transparent;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; font-size: 12px; line-height: 1.55;
}
.markdown-body :deep(blockquote) {
  margin: 8px 0; padding-left: 10px; border-left: 3px solid var(--color-border);
  color: var(--color-text-secondary);
}
.markdown-body :deep(table) { width: 100%; border-collapse: collapse; margin: 8px 0; font-size: 13px; }
.markdown-body :deep(th), .markdown-body :deep(td) { border: 1px solid var(--color-border); padding: 6px 8px; text-align: left; }
.user .markdown-body :deep(a) { color: #fff; text-decoration: underline; }
.user .markdown-body :deep(code:not(pre code)) { background: rgba(255, 255, 255, .18); }
.user .markdown-body :deep(pre) { color: var(--color-text-primary); }

@media (max-width: 560px) {
  .message {
    gap: 9px;
  }

  .avatar {
    width: 30px;
    height: 30px;
  }

  .bubble-wrap {
    max-width: calc(100% - 42px);
  }

  .bubble {
    padding: 9px 12px;
    font-size: 13px;
  }
}
</style>
