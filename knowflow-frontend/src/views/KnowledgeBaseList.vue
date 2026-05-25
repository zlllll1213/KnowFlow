<template>
  <div>
    <div class="page-header">
      <div>
        <div class="page-title">知识库</div>
        <div class="page-subtitle">管理您的知识库集合</div>
      </div>
      <el-button type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon> 新建知识库
      </el-button>
    </div>

    <div v-if="loading" class="loading-state">
      <el-skeleton :rows="3" animated />
    </div>
    <div v-else-if="kbs.length === 0" class="empty-state">
      <el-icon class="empty-icon"><Collection /></el-icon>
      <p>还没有知识库，立即创建一个</p>
      <el-button type="primary" @click="openCreate">新建知识库</el-button>
    </div>
    <div v-else class="kb-grid">
      <KnowledgeBaseCard
        v-for="kb in kbs"
        :key="kb.id"
        :kb="kb"
        @click="goDetail(kb.id)"
        @edit="openEdit(kb)"
        @delete="confirmDelete(kb)"
      />
    </div>

    <!-- Create / Edit Dialog -->
    <el-dialog v-model="dialogVisible" :title="editingKb ? '编辑知识库' : '新建知识库'" width="480px">
      <el-form ref="dialogFormRef" :model="dialogForm" :rules="dialogRules" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="dialogForm.name" placeholder="输入知识库名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="dialogForm.description" type="textarea" :rows="3" placeholder="简要描述该知识库的用途（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs'
import type { FormInstance, FormRules } from 'element-plus'
import KnowledgeBaseCard from '@/components/KnowledgeBaseCard.vue'
import { getKbList, createKb, updateKb, deleteKb } from '@/api/kb'
import type { KbVO } from '@/types/kb'

const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const kbs = ref<KbVO[]>([])
const dialogVisible = ref(false)
const editingKb = ref<KbVO | null>(null)
const dialogFormRef = ref<FormInstance>()
const dialogForm = reactive({ name: '', description: '' })
const dialogRules: FormRules = {
  name: [{ required: true, message: '请输入知识库名称', trigger: 'blur' }],
}

async function loadList() {
  loading.value = true
  try { kbs.value = await getKbList() } catch {} finally { loading.value = false }
}

onMounted(loadList)

function goDetail(id: number) { router.push(`/kb/${id}`) }

function openCreate() {
  editingKb.value = null
  dialogForm.name = ''
  dialogForm.description = ''
  dialogVisible.value = true
}

function openEdit(kb: KbVO) {
  editingKb.value = kb
  dialogForm.name = kb.name
  dialogForm.description = kb.description
  dialogVisible.value = true
}

async function confirmDelete(kb: KbVO) {
  await ElMessageBox.confirm(`确定删除知识库「${kb.name}」？此操作不可逆。`, '删除确认', {
    confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning',
  })
  try {
    await deleteKb(kb.id)
    ElMessage.success('已删除')
    loadList()
  } catch (e: any) { ElMessage.error(e.message) }
}

async function handleSave() {
  if (!await dialogFormRef.value?.validate().catch(() => false)) return
  saving.value = true
  try {
    if (editingKb.value) {
      await updateKb(editingKb.value.id, dialogForm)
      ElMessage.success('已更新')
    } else {
      await createKb(dialogForm)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadList()
  } catch (e: any) { ElMessage.error(e.message) } finally { saving.value = false }
}
</script>

<style scoped>
.empty-state { text-align: center; padding: 80px 0; color: var(--color-text-muted); display: flex; flex-direction: column; align-items: center; gap: 16px; }
.empty-icon { font-size: 48px; color: var(--color-border); }
.empty-state p { font-size: 15px; }
.kb-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 20px; }

@media (max-width: 560px) {
  .empty-state {
    padding: 56px 18px;
  }

  .kb-grid {
    grid-template-columns: 1fr;
    gap: 14px;
  }
}
</style>
