<template>
  <div class="kb-card" @click="$emit('click')">
    <div class="kb-card-header">
      <div class="kb-icon">
        <el-icon><Collection /></el-icon>
      </div>
      <div class="kb-actions" @click.stop>
        <el-dropdown trigger="click" @command="handleCommand">
          <button class="action-btn"><el-icon><MoreFilled /></el-icon></button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="edit">编辑</el-dropdown-item>
              <el-dropdown-item command="delete" style="color: #ef4444">删除</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>
    <div class="kb-body">
      <h3 class="kb-name">{{ kb.name }}</h3>
      <p class="kb-desc">{{ kb.description || '暂无描述' }}</p>
    </div>
    <div class="kb-footer">
      <span class="kb-meta"><el-icon><Document /></el-icon> {{ kb.documentCount ?? 0 }} 个文档</span>
      <span class="kb-meta">{{ formatDate(kb.updatedAt) }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { KbVO } from '@/types/kb'

const props = defineProps<{ kb: KbVO }>()
const emit = defineEmits<{
  click: []
  edit: []
  delete: []
}>()

function handleCommand(cmd: string) {
  if (cmd === 'edit') emit('edit')
  if (cmd === 'delete') emit('delete')
}

function formatDate(d: string) {
  if (!d) return ''
  return new Date(d).toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}
</script>

<style scoped>
.kb-card {
  background: var(--color-surface); border-radius: var(--radius-lg);
  border: 1px solid var(--color-border); padding: 20px;
  cursor: pointer; transition: box-shadow .2s, transform .2s;
  display: flex; flex-direction: column; gap: 12px;
}
.kb-card:hover { box-shadow: var(--shadow-md); transform: translateY(-2px); }
.kb-card-header { display: flex; align-items: flex-start; justify-content: space-between; }
.kb-icon {
  width: 40px; height: 40px; background: var(--color-accent-light);
  border-radius: 10px; display: flex; align-items: center; justify-content: center;
  color: var(--color-accent); font-size: 18px;
}
.action-btn {
  background: none; border: none; cursor: pointer; padding: 4px 6px;
  border-radius: 4px; color: var(--color-text-muted);
  display: flex; align-items: center; transition: background .15s;
}
.action-btn:hover { background: var(--color-bg); }
.kb-body { flex: 1; }
.kb-name { font-family: var(--font-heading); font-size: 16px; font-weight: 600; color: var(--color-text-primary); margin-bottom: 6px; }
.kb-desc { font-size: 13px; color: var(--color-text-secondary); line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.kb-footer { display: flex; align-items: center; justify-content: space-between; padding-top: 12px; border-top: 1px solid var(--color-border); }
.kb-meta { display: flex; align-items: center; gap: 4px; font-size: 12px; color: var(--color-text-muted); }
</style>
