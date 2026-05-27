<template>
  <div class="message" :class="msg.role">
    <div class="avatar">
      <el-icon v-if="msg.role === 'assistant'"><ChatDotRound /></el-icon>
      <span v-else>{{ initial }}</span>
    </div>
    <div class="bubble-wrap">
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
.assistant .avatar { background: var(--color-accent-light); color: var(--color-accent); font-size: 16px; }
.user .avatar { background: var(--color-sidebar); color: #94a3b8; }
.bubble-wrap { max-width: 72%; display: flex; flex-direction: column; gap: 4px; }
.user .bubble-wrap { align-items: flex-end; }
.bubble {
  padding: 10px 14px; border-radius: 12px; font-size: 14px; line-height: 1.6;
  word-break: break-word;
}
.assistant .bubble { background: var(--color-surface); border: 1px solid var(--color-border); color: var(--color-text-primary); border-radius: 2px 12px 12px 12px; }
.user .bubble { background: var(--color-accent); color: #fff; border-radius: 12px 2px 12px 12px; }
.time { font-size: 11px; color: var(--color-text-muted); padding: 0 4px; }
.markdown-body :deep(p) { margin: 0 0 8px; }
.markdown-body :deep(p:last-child) { margin-bottom: 0; }
.markdown-body :deep(ul), .markdown-body :deep(ol) { margin: 6px 0 8px 20px; }
.markdown-body :deep(li + li) { margin-top: 3px; }
.markdown-body :deep(a) { color: var(--color-accent); text-decoration: none; font-weight: 500; }
.markdown-body :deep(a:hover) { text-decoration: underline; }
.markdown-body :deep(code:not(pre code)) {
  padding: 2px 5px; border-radius: 4px; background: rgba(15, 23, 42, .08);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; font-size: .92em;
}
.markdown-body :deep(pre) {
  margin: 8px 0; border-radius: 8px; overflow-x: auto;
  border: 1px solid var(--color-border); background: #f8fafc;
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
