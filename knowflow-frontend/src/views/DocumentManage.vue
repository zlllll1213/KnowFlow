<template>
  <div>
    <div class="page-header">
      <div>
        <div class="page-title">文档管理</div>
        <div class="page-subtitle">上传与管理各知识库的文档</div>
      </div>
      <div class="header-actions">
        <el-select v-model="selectedKbId" placeholder="选择知识库" style="width:200px" @change="onKbChange">
          <el-option v-for="kb in kbs" :key="kb.id" :label="kb.name" :value="kb.id" />
        </el-select>
        <el-button type="primary" :disabled="!selectedKbId" @click="uploadVisible = true">
          <el-icon><Upload /></el-icon> 上传文档
        </el-button>
        <el-button :disabled="!selectedKbId" @click="loadDocs"><el-icon><Refresh /></el-icon> 刷新</el-button>
      </div>
    </div>

    <div v-if="!selectedKbId" class="empty-state">
      <el-icon class="empty-icon"><Folder /></el-icon>
      <p>请先选择一个知识库</p>
    </div>
    <div v-else class="card table-card">
      <el-table :data="docs" v-loading="loading" empty-text="该知识库暂无文档" style="width:100%">
        <el-table-column prop="fileName" label="文件名" min-width="220" show-overflow-tooltip />
        <el-table-column prop="fileType" label="类型" width="80">
          <template #default="{ row }">
            <span class="type-badge">{{ row.fileType.toUpperCase() }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="fileSize" label="大小" width="100">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="130">
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
        <el-table-column prop="createdAt" label="上传时间" width="160">
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
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs'
import DocumentUpload from '@/components/DocumentUpload.vue'
import { getKbList } from '@/api/kb'
import { getDocumentDetail, getDocumentList, getDocumentStatus, uploadDocument, deleteDocument } from '@/api/document'
import type { KbVO } from '@/types/kb'
import type { DocumentVO } from '@/types/document'

const kbs = ref<KbVO[]>([])
const docs = ref<DocumentVO[]>([])
const selectedKbId = ref<number | null>(null)
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
  UPLOADED: '已上传', PARSING: '解析中', EMBEDDING: '向量化中', DONE: '已完成', FAILED: '失败',
}
const statusIcon: Record<string, string> = {
  UPLOADED: 'Clock', PARSING: 'Loading', EMBEDDING: 'Loading', DONE: 'Check', FAILED: 'WarningFilled',
}

function isProcessing(status: string) {
  return activeStatuses.includes(status)
}

onMounted(async () => {
  kbs.value = (await getKbList().catch(() => ({ records: [] }))).records
  if (kbs.value.length > 0) {
    selectedKbId.value = kbs.value[0].id
    await loadDocs()
  }
})

async function loadDocs() {
  if (!selectedKbId.value) return
  loading.value = true
  try {
    const result = await getDocumentList(selectedKbId.value, page.value, pageSize.value)
    docs.value = result.records
    total.value = result.total
    syncPollingFromDocs()
  } catch {} finally { loading.value = false }
}

function onKbChange() {
  stopPolling()
  page.value = 1
  loadDocs()
}

function handlePageChange(nextPage: number) {
  page.value = nextPage
  loadDocs()
}

const pollTimers = new Map<number, number>()
const pollAttempts = new Map<number, number>()
onBeforeUnmount(() => {
  stopPolling()
})

async function handleUpload(files: File[]) {
  if (!selectedKbId.value) return
  uploading.value = true
  uploadProgress.value = 0
  for (let i = 0; i < files.length; i++) {
    currentFile.value = files[i].name
    try {
      const uploadedDoc = await uploadDocument(selectedKbId.value, files[i], (percent) => {
        uploadProgress.value = Math.min(99, Math.round(((i + percent / 100) / files.length) * 100))
      })
      upsertDocument(uploadedDoc)
      startDocumentPolling(uploadedDoc.id)
      uploadProgress.value = Math.round((i + 1) / files.length * 100)
    } catch (e: any) { ElMessage.error(`${files[i].name}: ${e.message}`) }
  }
  uploading.value = false
  uploadVisible.value = false
  ElMessage.success('上传完成')
  await loadDocs()
}

function syncPollingFromDocs() {
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

async function handleDelete(doc: DocumentVO) {
  await ElMessageBox.confirm(`确定删除「${doc.fileName}」？`, '删除确认', { type: 'warning' })
  try {
    stopDocumentPolling(doc.id)
    await deleteDocument(doc.id)
    ElMessage.success('已删除')
    await loadDocs()
  } catch (e: any) { ElMessage.error(e.message) }
}

function formatSize(b: number) {
  if (b < 1024) return `${b}B`
  if (b < 1048576) return `${(b / 1024).toFixed(1)}KB`
  return `${(b / 1048576).toFixed(1)}MB`
}

function formatDate(d: string) {
  return d ? new Date(d).toLocaleString('zh-CN') : ''
}

function patchDocument(docId: number, patch: Partial<DocumentVO>) {
  docs.value = docs.value.map(doc => doc.id === docId ? { ...doc, ...patch } : doc)
}

function upsertDocument(doc: DocumentVO) {
  const index = docs.value.findIndex(item => item.id === doc.id)
  if (index >= 0) {
    docs.value[index] = { ...docs.value[index], ...doc }
    return
  }
  docs.value = [doc, ...docs.value]
}
</script>

<style scoped>
.header-actions { display: flex; gap: 10px; align-items: center; }
.empty-state { text-align: center; padding: 80px; color: var(--color-text-muted); display: flex; flex-direction: column; align-items: center; gap: 16px; }
.empty-icon { font-size: 48px; color: var(--color-border); }
.table-card { overflow: hidden; padding: 0; }
.pager { padding: 14px 16px; justify-content: center; }
.type-badge { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: 4px; padding: 2px 6px; font-size: 11px; font-weight: 600; color: var(--color-text-secondary); }
.status-cell { display: flex; align-items: center; gap: 6px; }
.spin { animation: spin 1s linear infinite; }
@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
.upload-progress { margin-top: 16px; }
.progress-label { font-size: 13px; color: var(--color-text-secondary); margin-bottom: 8px; }

@media (max-width: 768px) {
  .header-actions {
    width: 100%;
    align-items: stretch;
    flex-direction: column;
    gap: 8px;
  }

  .header-actions :deep(.el-select),
  .header-actions :deep(.el-button) {
    width: 100% !important;
  }

  .empty-state {
    padding: 56px 18px;
  }

  .table-card {
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
  }

  .table-card :deep(.el-table) {
    min-width: 760px;
  }
}
</style>
