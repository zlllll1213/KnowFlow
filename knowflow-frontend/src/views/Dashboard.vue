<template>
  <div class="dashboard-page">
    <div class="dashboard-topbar">
      <div>
        <div class="page-title">工作台</div>
        <div class="page-subtitle">欢迎回来，{{ authStore.userInfo?.username || 'KnowFlow 用户' }}</div>
      </div>
      <button class="search-shell" type="button" @click="goChat">
        <el-icon><Search /></el-icon>
        <span>进入问答检索知识库...</span>
        <kbd>⌘ K</kbd>
      </button>
    </div>

    <div class="stat-grid">
      <div v-for="s in stats" :key="s.label" class="stat-card">
        <div class="stat-head">
          <span class="stat-icon"><el-icon><component :is="s.icon" /></el-icon></span>
          <span class="stat-label">{{ s.label }}</span>
        </div>
        <div class="stat-value">{{ s.value }}</div>
        <div class="stat-note">{{ s.note }}</div>
        <div class="sparkline" :style="{ '--spark': s.spark }">
          <i v-for="n in 8" :key="n" :style="{ height: `${s.bars[n - 1]}%` }"></i>
        </div>
      </div>
    </div>

    <div class="content-grid">
      <section class="panel recent-panel">
        <div class="panel-header">
          <h2>最近上传文档</h2>
          <router-link to="/documents">查看全部 <el-icon><ArrowRight /></el-icon></router-link>
        </div>
        <div v-if="recentDocs.length === 0" class="empty-tip">暂无文档</div>
        <div v-else class="doc-list">
          <div v-for="d in recentDocs" :key="d.id" class="doc-row">
            <span :class="['file-chip', fileTone(d.fileType)]">{{ fileLabel(d.fileType) }}</span>
            <div class="doc-main">
              <strong>{{ d.fileName }}</strong>
              <span>{{ formatSize(d.fileSize) }} · {{ formatDateTime(d.createdAt) }}</span>
            </div>
            <span :class="['status-badge', d.status.toLowerCase()]">{{ statusLabel[d.status] }}</span>
          </div>
        </div>
      </section>

      <section class="panel failure-panel">
        <div class="panel-header">
          <h2>最近失败任务</h2>
          <router-link to="/documents">查看文档 <el-icon><ArrowRight /></el-icon></router-link>
        </div>
        <div v-if="recentFailures.length === 0" class="empty-tip success-empty">
          <el-icon><CircleCheck /></el-icon>
          暂无失败任务
        </div>
        <div v-else class="failure-list">
          <div v-for="item in recentFailures" :key="item.taskId" class="failure-row">
            <span class="error-dot"><el-icon><CloseBold /></el-icon></span>
            <div>
              <strong>{{ item.fileName }}</strong>
              <p>{{ item.errorMessage || '解析失败' }} · {{ formatDateTime(item.updatedAt) }}</p>
            </div>
          </div>
        </div>
      </section>

      <section class="panel sessions-panel">
        <div class="panel-header">
          <h2>最近会话</h2>
          <router-link to="/chat">进入问答 <el-icon><ArrowRight /></el-icon></router-link>
        </div>
        <div v-if="recentSessions.length === 0" class="empty-tip">暂无会话</div>
        <div v-else class="session-list">
          <div v-for="s in recentSessions" :key="s.id" class="session-row">
            <span><el-icon><ChatDotRound /></el-icon></span>
            <div>
              <strong>{{ s.title }}</strong>
              <p>更新于 {{ formatDateTime(s.updatedAt) }}</p>
            </div>
          </div>
        </div>
      </section>

      <section class="panel health-panel">
        <div class="panel-header">
          <h2>知识流概览</h2>
        </div>
        <div class="success-ring" :style="{ '--rate': `${successRate * 3.6}deg` }">
          <div>
            <strong>{{ successRate }}%</strong>
            <span>解析成功率</span>
          </div>
        </div>
        <div class="health-metrics">
          <div><span>已完成</span><strong>{{ doneCount }}</strong></div>
          <div><span>失败</span><strong>{{ failedCount }}</strong></div>
          <div><span>切片总量</span><strong>{{ formatNumber(chunkCount) }}</strong></div>
          <div><span>提问次数</span><strong>{{ formatNumber(chatCount) }}</strong></div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onBeforeUnmount, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { getDashboardStats } from '@/api/dashboard'
import { getKbList } from '@/api/kb'
import { getDocumentList } from '@/api/document'
import { listSessions } from '@/api/chat'
import type { DocumentVO } from '@/types/document'
import type { ChatSessionVO } from '@/types/chat'
import type { RecentFailedTaskVO } from '@/types/dashboard'
import type { KbVO } from '@/types/kb'

const router = useRouter()
const authStore = useAuthStore()

const kbCount = ref(0)
const docCount = ref(0)
const doneCount = ref(0)
const failedCount = ref(0)
const chunkCount = ref(0)
const chatCount = ref(0)
const recentDocs = ref<DocumentVO[]>([])
const recentSessions = ref<ChatSessionVO[]>([])
const recentFailures = ref<RecentFailedTaskVO[]>([])
const statsSource = ref<'dashboard' | 'aggregated'>('dashboard')

const successRate = computed(() => {
  const total = doneCount.value + failedCount.value
  if (!total) return 100
  return Math.round((doneCount.value / total) * 1000) / 10
})

const stats = computed(() => [
  { label: '知识库', value: formatNumber(kbCount.value), note: '当前可检索空间', icon: 'Box', spark: '#2f72ff', bars: [18, 26, 22, 46, 34, 52, 44, 68] },
  { label: '文档总数', value: formatNumber(docCount.value), note: `${doneCount.value} 个已完成解析`, icon: 'Document', spark: '#31c7ff', bars: [22, 30, 28, 38, 44, 36, 56, 62] },
  { label: '解析分块数', value: formatNumber(chunkCount.value), note: 'pgvector 检索语料', icon: 'Grid', spark: '#43e0a8', bars: [20, 34, 44, 40, 58, 48, 64, 72] },
  {
    label: statsSource.value === 'dashboard' ? '提问次数 (Ask)' : '会话数',
    value: formatNumber(chatCount.value),
    note: statsSource.value === 'dashboard' ? 'RAG / Agent 调用' : '由会话接口聚合',
    icon: 'ChatDotRound',
    spark: '#8aa8ff',
    bars: [16, 28, 22, 40, 32, 50, 46, 60],
  },
  { label: '成功率', value: `${successRate.value}%`, note: `${failedCount.value} 个失败任务`, icon: 'Finished', spark: '#43e0a8', bars: [42, 38, 52, 50, 64, 58, 72, 68] },
])

onMounted(async () => {
  window.addEventListener('keydown', handleCommandShortcut)
  try {
    const stats = await getDashboardStats()
    statsSource.value = 'dashboard'
    kbCount.value = stats.kbCount
    docCount.value = stats.docCount
    doneCount.value = stats.doneDocCount
    failedCount.value = stats.failedDocCount
    chunkCount.value = stats.chunkCount
    chatCount.value = stats.chatCount
    recentDocs.value = stats.recentDocs ?? []
    recentSessions.value = stats.recentSessions ?? []
    recentFailures.value = stats.recentFailedTasks ?? []
  } catch {
    await loadAggregatedStats()
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleCommandShortcut)
})

const statusLabel: Record<string, string> = {
  UPLOADED: '已上传',
  PARSING: '解析中',
  EMBEDDING: '向量化中',
  DONE: '已完成',
  FAILED: '失败',
}

function formatNumber(value: number) {
  return new Intl.NumberFormat('zh-CN').format(value)
}

function formatSize(bytes: number) {
  if (bytes < 1024) return `${bytes}B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)}KB`
  return `${(bytes / 1024 / 1024).toFixed(1)}MB`
}

function formatDateTime(d: string) {
  if (!d) return ''
  return new Date(d).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

function fileLabel(type: string) {
  return (type || 'DOC').slice(0, 4).toUpperCase()
}

function fileTone(type: string) {
  const normalized = (type || '').toLowerCase()
  if (normalized.includes('pdf')) return 'pdf'
  if (normalized.includes('doc')) return 'word'
  if (normalized.includes('ppt')) return 'ppt'
  if (normalized.includes('xls')) return 'xls'
  return 'file'
}

function goChat() {
  router.push('/chat')
}

function handleCommandShortcut(event: KeyboardEvent) {
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    goChat()
  }
}

async function loadAggregatedStats() {
  statsSource.value = 'aggregated'
  const kbResult = await getKbList().catch(() => ({ records: [] as KbVO[], total: 0, page: 1, size: 100 }))
  const kbs = recordsOf<KbVO>(kbResult)
  kbCount.value = kbs.length

  const docResults = await Promise.all(
    kbs.map(kb => getDocumentList(kb.id, 1, 20).catch(() => ({ records: [] as DocumentVO[], total: 0, page: 1, size: 20 })))
  )
  const docs = docResults.flatMap(result => recordsOf<DocumentVO>(result))
  docCount.value = docs.length
  doneCount.value = docs.filter(doc => doc.status === 'DONE').length
  failedCount.value = docs.filter(doc => doc.status === 'FAILED').length
  chunkCount.value = docs.reduce((total, doc) => total + (doc.chunkCount || 0), 0)
  recentDocs.value = docs.sort(byUpdatedAt).slice(0, 5)
  recentFailures.value = docs
    .filter(doc => doc.status === 'FAILED')
    .sort(byUpdatedAt)
    .slice(0, 5)
    .map(doc => ({
      taskId: doc.parseTaskId ?? doc.id,
      documentId: doc.id,
      fileName: doc.fileName,
      errorMessage: doc.errorMessage || '解析失败',
      updatedAt: doc.updatedAt,
    }))

  const sessionResults = await Promise.all(
    kbs.map(kb => listSessions(kb.id, 1, 20).catch(() => ({ records: [] as ChatSessionVO[], total: 0, page: 1, size: 20 })))
  )
  const sessions = sessionResults.flatMap(result => recordsOf<ChatSessionVO>(result))
  chatCount.value = sessions.length
  recentSessions.value = sessions.sort(byUpdatedAt).slice(0, 5)
}

function recordsOf<T>(result: { records?: T[] } | T[] | null | undefined) {
  if (Array.isArray(result)) return result
  return result?.records ?? []
}

function byUpdatedAt(a: { updatedAt?: string; createdAt?: string }, b: { updatedAt?: string; createdAt?: string }) {
  return Date.parse(b.updatedAt || b.createdAt || '') - Date.parse(a.updatedAt || a.createdAt || '')
}
</script>

<style scoped>
.dashboard-page {
  position: relative;
  z-index: 1;
}

.dashboard-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 22px;
  margin-bottom: 20px;
}

.search-shell {
  width: min(440px, 42vw);
  height: 42px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: rgba(7, 28, 62, .74);
  color: var(--color-text-muted);
  cursor: pointer;
  font-size: 13px;
  font-family: inherit;
  text-align: left;
  transition: border-color .16s, background .16s, color .16s;
}

.search-shell:hover,
.search-shell:focus-visible {
  border-color: rgba(49, 199, 255, .48);
  background: rgba(11, 39, 84, .9);
  color: #dce8ff;
  outline: none;
}

.search-shell span {
  flex: 1;
}

.search-shell kbd {
  height: 24px;
  min-width: 44px;
  display: inline-grid;
  place-items: center;
  border: 1px solid rgba(112, 158, 222, .28);
  border-radius: 6px;
  background: rgba(2, 8, 23, .34);
  color: #cbd9ef;
  font-family: inherit;
  font-size: 12px;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.stat-card {
  min-height: 124px;
  padding: 16px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: linear-gradient(180deg, rgba(11, 39, 84, .88), rgba(5, 22, 49, .74));
  box-shadow: inset 0 1px 0 rgba(255,255,255,.08), 0 16px 40px rgba(0, 12, 32, .22);
  overflow: hidden;
}

.stat-head {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--color-text-secondary);
  font-size: 13px;
  font-weight: 800;
}

.stat-icon {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: rgba(47, 114, 255, .2);
  color: #7db4ff;
  font-size: 18px;
}

.stat-value {
  margin-top: 10px;
  color: #fff;
  font-size: 28px;
  font-weight: 900;
  line-height: 1;
}

.stat-note {
  margin-top: 7px;
  color: var(--color-success);
  font-size: 12px;
}

.sparkline {
  display: flex;
  align-items: end;
  gap: 5px;
  height: 28px;
  margin-top: 10px;
}

.sparkline i {
  flex: 1;
  min-width: 4px;
  border-radius: 999px 999px 0 0;
  background: linear-gradient(180deg, var(--spark), rgba(47, 114, 255, .06));
  opacity: .88;
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.18fr) minmax(300px, .82fr) minmax(280px, .78fr);
  gap: 14px;
  align-items: stretch;
}

.panel {
  min-height: 258px;
  padding: 16px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: rgba(7, 28, 62, .72);
  box-shadow: inset 0 1px 0 rgba(255,255,255,.06);
  backdrop-filter: blur(18px);
}

.recent-panel {
  grid-row: span 2;
}

.health-panel {
  min-height: 306px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.panel-header h2 {
  color: #f6f9ff;
  font-size: 16px;
  font-weight: 900;
}

.panel-header a {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: #6aa5ff;
  font-size: 12px;
  font-weight: 800;
  text-decoration: none;
}

.empty-tip {
  display: grid;
  place-items: center;
  min-height: 160px;
  color: var(--color-text-muted);
  font-size: 13px;
}

.success-empty {
  gap: 8px;
  color: var(--color-success);
}

.doc-list,
.failure-list,
.session-list {
  display: grid;
}

.doc-row,
.failure-row,
.session-row {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  padding: 12px 0;
  border-bottom: 1px solid rgba(112, 158, 222, .14);
}

.doc-row:last-child,
.failure-row:last-child,
.session-row:last-child {
  border-bottom: 0;
}

.file-chip {
  width: 38px;
  height: 34px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  border-radius: 8px;
  background: rgba(120, 145, 184, .16);
  color: #dce8ff;
  font-size: 10px;
  font-weight: 900;
}

.file-chip.pdf { background: rgba(255, 77, 97, .2); color: #ff8997; }
.file-chip.word { background: rgba(47, 114, 255, .22); color: #83b3ff; }
.file-chip.ppt { background: rgba(248, 193, 74, .2); color: #ffd56e; }
.file-chip.xls { background: rgba(67, 224, 168, .18); color: #72e9bd; }

.doc-main,
.failure-row div,
.session-row div {
  flex: 1;
  min-width: 0;
}

.doc-main strong,
.failure-row strong,
.session-row strong {
  display: block;
  overflow: hidden;
  color: #eef5ff;
  font-size: 13px;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-main span,
.failure-row p,
.session-row p {
  margin-top: 4px;
  overflow: hidden;
  color: var(--color-text-muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.error-dot,
.session-row > span {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  border-radius: 8px;
}

.error-dot {
  background: rgba(255, 77, 97, .16);
  color: var(--color-danger);
}

.session-row > span {
  background: rgba(47, 114, 255, .16);
  color: #7db4ff;
}

.success-ring {
  width: 160px;
  height: 160px;
  display: grid;
  place-items: center;
  margin: 8px auto 18px;
  border-radius: 50%;
  background: conic-gradient(var(--color-success) var(--rate), rgba(112,158,222,.14) 0deg);
}

.success-ring > div {
  width: 122px;
  height: 122px;
  display: grid;
  place-items: center;
  align-content: center;
  border-radius: 50%;
  background: #071c3e;
}

.success-ring strong {
  color: #fff;
  font-size: 32px;
  font-weight: 900;
}

.success-ring span {
  margin-top: 5px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.health-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.health-metrics div {
  padding: 12px;
  border: 1px solid rgba(112, 158, 222, .16);
  border-radius: 8px;
  background: rgba(2, 8, 23, .22);
}

.health-metrics span {
  display: block;
  color: var(--color-text-muted);
  font-size: 12px;
}

.health-metrics strong {
  display: block;
  margin-top: 6px;
  color: #fff;
  font-size: 18px;
}

@media (max-width: 1280px) {
  .stat-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .content-grid { grid-template-columns: 1fr 1fr; }
  .recent-panel { grid-row: auto; }
}

@media (max-width: 860px) {
  .dashboard-topbar { align-items: flex-start; flex-direction: column; }
  .search-shell { width: 100%; }
  .stat-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .content-grid { grid-template-columns: 1fr; }

  .stat-card {
    min-height: 108px;
    padding: 13px;
  }

  .stat-icon {
    width: 30px;
    height: 30px;
  }

  .stat-value {
    font-size: 24px;
  }

  .sparkline {
    height: 20px;
    margin-top: 8px;
  }
}

@media (max-width: 520px) {
  .stat-grid { grid-template-columns: 1fr; }

  .stat-card {
    min-height: 96px;
  }

  .sparkline {
    display: none;
  }
}
</style>
