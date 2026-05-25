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
            <span :class="['status-badge', row.status.toLowerCase()]">{{ statusLabel[row.status] }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="上传时间" width="150">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ row }">
            <el-button size="small" text type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="uploadVisible" title="上传文档" width="520px">
      <DocumentUpload @files="handleUpload" />
      <div v-if="uploading" class="upload-progress">
        <el-progress :percentage="uploadProgress" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs'
import DocumentUpload from '@/components/DocumentUpload.vue'
import { getKbDetail } from '@/api/kb'
import { getDocumentList, uploadDocument, deleteDocument } from '@/api/document'
import type { KbVO } from '@/types/kb'
import type { DocumentVO } from '@/types/document'

const route = useRoute()
const router = useRouter()
const kbId = Number(route.params.id)

const kb = ref<KbVO | null>(null)
const docs = ref<DocumentVO[]>([])
const loading = ref(false)
const uploadVisible = ref(false)
const uploading = ref(false)
const uploadProgress = ref(0)

const statusLabel: Record<string, string> = {
  UPLOADED: '已上传', PARSING: '解析中', EMBEDDING: '向量化', DONE: '已完成', FAILED: '失败',
}

async function loadData() {
  loading.value = true
  try {
    [kb.value, docs.value] = await Promise.all([getKbDetail(kbId), getDocumentList(kbId)])
  } catch {} finally { loading.value = false }
}

async function loadDocs() {
  loading.value = true
  try { docs.value = await getDocumentList(kbId) } catch {} finally { loading.value = false }
}

onMounted(loadData)

async function handleUpload(files: File[]) {
  uploading.value = true
  uploadProgress.value = 0
  for (let i = 0; i < files.length; i++) {
    try {
      await uploadDocument(kbId, files[i])
      uploadProgress.value = Math.round((i + 1) / files.length * 100)
    } catch (e: any) { ElMessage.error(`${files[i].name} 上传失败: ${e.message}`) }
  }
  uploading.value = false
  uploadVisible.value = false
  ElMessage.success('上传完成')
  loadDocs()
}

async function handleDelete(doc: DocumentVO) {
  await ElMessageBox.confirm(`确定删除「${doc.fileName}」？`, '删除确认', { type: 'warning' })
  try {
    await deleteDocument(doc.id)
    ElMessage.success('已删除')
    loadDocs()
  } catch (e: any) { ElMessage.error(e.message) }
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
.table-toolbar { display: flex; align-items: center; justify-content: space-between; padding: 16px 20px; border-bottom: 1px solid var(--color-border); }
.table-title { font-size: 14px; font-weight: 600; color: var(--color-text-primary); }
.type-badge { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: 4px; padding: 2px 6px; font-size: 11px; font-weight: 600; color: var(--color-text-secondary); }
.upload-progress { margin-top: 16px; }

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
