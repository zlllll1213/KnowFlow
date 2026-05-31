<template>
  <div>
    <div class="page-header">
      <div>
        <div class="back-link" @click="router.back()"><el-icon><ArrowLeft /></el-icon> 知识库</div>
        <div class="page-title">{{ kb?.name || '加载中…' }}</div>
        <div class="page-subtitle">{{ kb?.description }}</div>
      </div>
      <div class="header-actions">
        <el-button @click="uploadVisible = true"><el-icon><Upload /></el-icon> 上传文档</el-button>
        <el-button type="primary" @click="goChat"><el-icon><ChatDotRound /></el-icon> 进入问答</el-button>
      </div>
    </div>

    <div class="card table-card">
      <div class="table-toolbar">
        <span class="table-title">文档列表</span>
        <el-button size="small" text @click="loadDocs"><el-icon><Refresh /></el-icon> 刷新</el-button>
      </div>
      <el-table :data="docs" v-loading="loading" empty-text="暂无文档" style="width:100%">
        <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip />
        <el-table-column prop="fileType" label="类型" width="80">
          <template #default="{ row }">
            <span class="type-badge">{{ row.fileType.toUpperCase() }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="fileSize" label="大小" width="100">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <div class="status-cell">
              <el-tooltip v-if="row.status === 'FAILED' && row.errorMessage" :content="row.errorMessage">
                <span :class="['status-badge', row.status.toLowerCase()]">
                  <el-icon :class="{ spin: isProcessing(row.status) }"><component :is="statusIcon[row.status]" /></el-icon>
                  {{ statusLabel[row.status] }}
                </span>
              </el-tooltip>
              <span v-else :class="['status-badge', row.status.toLowerCase()]">
                <el-icon :class="{ spin: isProcessing(row.status) }"><component :is="statusIcon[row.status]" /></el-icon>
                {{ statusLabel[row.status] }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="chunkCount" label="切片" width="80" />
        <el-table-column prop="createdAt" label="上传时间" width="150">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ row }">
            <el-button size="small" text type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
	      </el-table>
	      <el-pagination
	        v-if="total > pageSize"
	        class="pager"
	        layout="prev, pager, next"
	        :current-page="page"
	        :page-size="pageSize"
	        :total="total"
	        @current-change="handlePageChange"
	      />
	    </div>

    <el-dialog v-model="uploadVisible" title="上传文档" width="520px">
      <DocumentUpload @files="handleUpload" />
      <div v-if="uploading" class="upload-progress">
        <div class="progress-label">正在上传 {{ currentFile }}…</div>
        <el-progress :percentage="uploadProgress" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs'
import DocumentUpload from '@/components/DocumentUpload.vue'
import { getKbDetail } from '@/api/kb'
import { getDocumentDetail, getDocumentList, getDocumentStatus, uploadDocument, deleteDocument } from '@/api/document'
import type { KbVO } from '@/types/kb'
import type { DocumentVO } from '@/types/document'

const route = useRoute()
const router = useRouter()
const kbId = Number(route.params.id)

const kb = ref<KbVO | null>(null)
const docs = ref<DocumentVO[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const uploadVisible = ref(false)
const uploading = ref(false)
const uploadProgress = ref(0)
const currentFile = ref('')
const STATUS_POLL_INTERVAL_MS = 2000
const MAX_STATUS_POLLS = 180
const activeStatuses = ['UPLOADED', 'PARSING', 'EMBEDDING']
const terminalStatuses = ['DONE', 'FAILED']

const statusLabel: Record<string, string> = {
  UPLOADED: '已上传', PARSING: '解析中', EMBEDDING: '向量化', DONE: '已完成', FAILED: '失败',
}
const statusIcon: Record<string, string> = {
  UPLOADED: 'Clock', PARSING: 'Loading', EMBEDDING: 'Loading', DONE: 'Check', FAILED: 'WarningFilled',
}

function isProcessing(status: string) {
  return activeStatuses.includes(status)
}

async function loadData() {
  loading.value = true
  try {
    const [kbDetail, docPage] = await Promise.all([getKbDetail(kbId), getDocumentList(kbId, page.value, pageSize.value)])
    kb.value = kbDetail
    docs.value = docPage.records
    total.value = docPage.total
    syncPolling()
  } catch (e: unknown) {
    console.error('加载知识库详情失败', e)
  } finally { loading.value = false }
}

async function loadDocs() {
  loading.value = true
  try {
    const result = await getDocumentList(kbId, page.value, pageSize.value)
    docs.value = result.records
    total.value = result.total
    syncPolling()
  } catch (e: unknown) {
    console.error('加载文档列表失败', e)
  } finally { loading.value = false }
}

onMounted(loadData)
const pollTimers = new Map<number, number>()
const pollAttempts = new Map<number, number>()
onBeforeUnmount(() => {
  stopPolling()
})

async function handleUpload(files: File[]) {
  uploading.value = true
  uploadProgress.value = 0
  for (let i = 0; i < files.length; i++) {
    currentFile.value = files[i].name
    try {
      const uploadedDoc = await uploadDocument(kbId, files[i], (percent) => {
        uploadProgress.value = Math.min(99, Math.round(((i + percent / 100) / files.length) * 100))
      })
      upsertDocument(uploadedDoc)
      startDocumentPolling(uploadedDoc.id)
      uploadProgress.value = Math.round((i + 1) / files.length * 100)
    } catch (e: any) { ElMessage.error(`${files[i].name} 上传失败: ${e.message}`) }
  }
  uploading.value = false
  uploadVisible.value = false
  ElMessage.success('上传完成')
  await loadDocs()
}

function syncPolling() {
  const visibleIds = new Set(docs.value.map(d => d.id))
  for (const doc of docs.value) {
    if (activeStatuses.includes(doc.status)) {
      startDocumentPolling(doc.id)
    }
    if (terminalStatuses.includes(doc.status)) {
      stopDocumentPolling(doc.id)
    }
  }
  for (const docId of pollTimers.keys()) {
    if (!visibleIds.has(docId)) stopDocumentPolling(docId)
  }
}

function startDocumentPolling(docId: number) {
  if (pollTimers.has(docId)) return
  pollAttempts.set(docId, 0)
  const timer = window.setInterval(() => {
    pollDocumentStatus(docId)
  }, STATUS_POLL_INTERVAL_MS)
  pollTimers.set(docId, timer)
  pollDocumentStatus(docId)
}

async function pollDocumentStatus(docId: number) {
  const attempts = (pollAttempts.get(docId) ?? 0) + 1
  pollAttempts.set(docId, attempts)
  if (attempts > MAX_STATUS_POLLS) {
    stopDocumentPolling(docId)
    ElMessage.warning('文档解析仍未完成，请稍后手动刷新状态')
    return
  }

  try {
    const status = await getDocumentStatus(docId)
    patchDocument(docId, { status })
    if (!terminalStatuses.includes(status)) return

    stopDocumentPolling(docId)
    const detail = await getDocumentDetail(docId)
    upsertDocument(detail)
    if (detail.status === 'FAILED' && detail.errorMessage) {
      ElMessage.error(`${detail.fileName} 解析失败：${detail.errorMessage}`)
    }
  } catch (e: any) {
    stopDocumentPolling(docId)
    ElMessage.warning(e.message || '文档状态刷新失败')
  }
}

function stopDocumentPolling(docId: number) {
  const timer = pollTimers.get(docId)
  if (timer) window.clearInterval(timer)
  pollTimers.delete(docId)
  pollAttempts.delete(docId)
}

function stopPolling() {
  for (const timer of pollTimers.values()) {
    window.clearInterval(timer)
  }
  pollTimers.clear()
  pollAttempts.clear()
}

function handlePageChange(nextPage: number) {
  page.value = nextPage
  loadDocs()
}

async function handleDelete(doc: DocumentVO) {
  await ElMessageBox.confirm(`确定删除「${doc.fileName}」？`, '删除确认', { type: 'warning' })
  try {
    stopDocumentPolling(doc.id)
    await deleteDocument(doc.id)
    ElMessage.success('已删除')
    loadDocs()
  } catch (e: any) { ElMessage.error(e.message) }
}

function upsertDocument(doc: DocumentVO) {
  const idx = docs.value.findIndex(d => d.id === doc.id)
  if (idx >= 0) {
    docs.value[idx] = { ...docs.value[idx], ...doc }
  } else {
    docs.value.unshift(doc)
    total.value += 1
  }
}

function patchDocument(docId: number, patch: Partial<DocumentVO>) {
  const idx = docs.value.findIndex(d => d.id === docId)
  if (idx >= 0) {
    docs.value[idx] = { ...docs.value[idx], ...patch }
  }
}

function goChat() { router.push({ path: '/chat', query: { kbId } }) }

function formatSize(b: number) {
  if (b < 1024) return `${b}B`
  if (b < 1048576) return `${(b / 1024).toFixed(1)}KB`
  return `${(b / 1048576).toFixed(1)}MB`
}

function formatDate(d: string) {
  return d ? new Date(d).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) : ''
}
</script>

<style scoped>
.back-link { display: flex; align-items: center; gap: 4px; font-size: 13px; color: var(--color-text-muted); cursor: pointer; margin-bottom: 4px; }
.back-link:hover { color: var(--color-accent); }
.header-actions { display: flex; gap: 10px; }
.table-card { padding: 0; overflow: hidden; }
.pager { padding: 14px 16px; justify-content: center; }
.table-toolbar { display: flex; align-items: center; justify-content: space-between; padding: 16px 20px; border-bottom: 1px solid var(--color-border); }
.table-title { font-size: 14px; font-weight: 600; color: var(--color-text-primary); }
.type-badge { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: 4px; padding: 2px 6px; font-size: 11px; font-weight: 600; color: var(--color-text-secondary); }
.status-cell { display: flex; align-items: center; gap: 6px; }
.spin { animation: spin 1s linear infinite; }
.upload-progress { margin-top: 16px; }
.progress-label { font-size: 13px; color: var(--color-text-secondary); margin-bottom: 8px; }
@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }

@media (max-width: 768px) {
  .header-actions {
    width: 100%;
    align-items: stretch;
    flex-direction: column;
    gap: 8px;
  }

  .header-actions :deep(.el-button) {
    width: 100%;
  }

  .table-toolbar {
    padding: 14px 16px;
  }

  .table-card {
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
  }

  .table-card :deep(.el-table) {
    min-width: 720px;
  }
}
</style>
