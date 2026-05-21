<template>
  <div>
    <div class="page-header">
      <div>
        <div class="page-title">工作台</div>
        <div class="page-subtitle">欢迎回来，{{ authStore.userInfo?.username }}</div>
      </div>
    </div>

    <div class="stat-grid">
      <div class="stat-card" v-for="s in stats" :key="s.label">
        <div class="stat-icon" :style="{ background: s.bg, color: s.color }">
          <el-icon><component :is="s.icon" /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-value">{{ s.value }}</div>
          <div class="stat-label">{{ s.label }}</div>
        </div>
      </div>
    </div>

    <div class="dashboard-grid">
      <div class="card panel">
        <div class="panel-title"><el-icon><Clock /></el-icon> 最近上传文档</div>
        <div v-if="recentDocs.length === 0" class="empty-tip">暂无文档</div>
        <div v-for="d in recentDocs" :key="d.id" class="list-item">
          <div class="item-icon doc"><el-icon><Document /></el-icon></div>
          <div class="item-body">
            <div class="item-name">{{ d.fileName }}</div>
            <div class="item-meta">{{ d.fileType.toUpperCase() }} · {{ formatSize(d.fileSize) }}</div>
          </div>
          <span :class="['status-badge', d.status.toLowerCase()]">{{ statusLabel[d.status] }}</span>
        </div>
        <router-link to="/documents" class="panel-more">查看全部 →</router-link>
      </div>

      <div class="card panel">
        <div class="panel-title"><el-icon><ChatDotRound /></el-icon> 最近会话</div>
        <div v-if="recentSessions.length === 0" class="empty-tip">暂无会话</div>
        <div v-for="s in recentSessions" :key="s.id" class="list-item">
          <div class="item-icon chat"><el-icon><ChatDotRound /></el-icon></div>
          <div class="item-body">
            <div class="item-name">{{ s.title }}</div>
            <div class="item-meta">{{ formatDate(s.updatedAt) }}</div>
          </div>
        </div>
        <router-link to="/chat" class="panel-more">进入问答 →</router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { getKbList } from '@/api/kb'
import { getDocumentList } from '@/api/document'
import type { DocumentVO } from '@/types/document'
import type { ChatSessionVO } from '@/types/chat'

const authStore = useAuthStore()

const kbCount = ref(0)
const docCount = ref(0)
const doneCount = ref(0)
const recentDocs = ref<DocumentVO[]>([])
const recentSessions = ref<ChatSessionVO[]>([])

const stats = computed(() => [
  { label: '知识库', value: kbCount.value, icon: 'Collection', bg: '#eef2ff', color: '#4f46e5' },
  { label: '文档总数', value: docCount.value, icon: 'Document', bg: '#fef9c3', color: '#854d0e' },
  { label: '已完成解析', value: doneCount.value, icon: 'CircleCheck', bg: '#d1fae5', color: '#065f46' },
  { label: '活跃会话', value: recentSessions.value.length, icon: 'ChatDotRound', bg: '#ffe4e6', color: '#9f1239' },
])

onMounted(async () => {
  try {
    const kbs = await getKbList()
    kbCount.value = kbs.length
    if (kbs.length > 0) {
      const docs = await getDocumentList(kbs[0].id)
      docCount.value = docs.length
      doneCount.value = docs.filter(d => d.status === 'DONE').length
      recentDocs.value = docs.slice(0, 5)
    }
  } catch {}
})

const statusLabel: Record<string, string> = {
  UPLOADED: '已上传', PARSING: '解析中', EMBEDDING: '向量化', DONE: '完成', FAILED: '失败',
}

function formatSize(bytes: number) {
  if (bytes < 1024) return `${bytes}B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)}KB`
  return `${(bytes / 1024 / 1024).toFixed(1)}MB`
}

function formatDate(d: string) {
  if (!d) return ''
  return new Date(d).toLocaleDateString('zh-CN')
}
</script>

<style scoped>
.stat-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 28px; }
.stat-card {
  background: var(--color-surface); border: 1px solid var(--color-border);
  border-radius: var(--radius-lg); padding: 20px; display: flex; align-items: center; gap: 16px;
}
.stat-icon { width: 48px; height: 48px; border-radius: 12px; display: flex; align-items: center; justify-content: center; font-size: 22px; flex-shrink: 0; }
.stat-value { font-family: var(--font-heading); font-size: 28px; font-weight: 700; color: var(--color-text-primary); line-height: 1; }
.stat-label { font-size: 13px; color: var(--color-text-secondary); margin-top: 4px; }
.dashboard-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }
.panel { padding: 20px; }
.panel-title { display: flex; align-items: center; gap: 8px; font-size: 14px; font-weight: 600; color: var(--color-text-primary); margin-bottom: 16px; }
.empty-tip { font-size: 13px; color: var(--color-text-muted); padding: 20px 0; text-align: center; }
.list-item { display: flex; align-items: center; gap: 12px; padding: 10px 0; border-bottom: 1px solid var(--color-border); }
.list-item:last-of-type { border-bottom: none; }
.item-icon { width: 32px; height: 32px; border-radius: 8px; display: flex; align-items: center; justify-content: center; font-size: 15px; flex-shrink: 0; }
.item-icon.doc { background: #fef9c3; color: #854d0e; }
.item-icon.chat { background: #eef2ff; color: #4f46e5; }
.item-body { flex: 1; min-width: 0; }
.item-name { font-size: 13px; font-weight: 500; color: var(--color-text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.item-meta { font-size: 12px; color: var(--color-text-muted); margin-top: 2px; }
.panel-more { display: block; text-align: right; font-size: 12px; color: var(--color-accent); text-decoration: none; margin-top: 12px; }
</style>
