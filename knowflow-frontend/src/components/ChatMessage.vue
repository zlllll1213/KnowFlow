<template>
  <div class="message" :class="msg.role">
    <div class="avatar">
      <el-icon v-if="msg.role === 'assistant'"><ChatDotRound /></el-icon>
      <span v-else>{{ initial }}</span>
    </div>
    <div class="bubble-wrap">
      <div class="bubble">{{ msg.content }}</div>
      <div class="time">{{ formatTime(msg.createdAt) }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { ChatMessageVO } from '@/types/chat'
import { useAuthStore } from '@/stores/auth'
import { computed } from 'vue'

defineProps<{ msg: ChatMessageVO }>()

const authStore = useAuthStore()
const initial = computed(() => (authStore.userInfo?.username?.[0] ?? 'U').toUpperCase())

function formatTime(d: string) {
  if (!d) return ''
  return new Date(d).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
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
  white-space: pre-wrap; word-break: break-word;
}
.assistant .bubble { background: var(--color-surface); border: 1px solid var(--color-border); color: var(--color-text-primary); border-radius: 2px 12px 12px 12px; }
.user .bubble { background: var(--color-accent); color: #fff; border-radius: 12px 2px 12px 12px; }
.time { font-size: 11px; color: var(--color-text-muted); padding: 0 4px; }

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
