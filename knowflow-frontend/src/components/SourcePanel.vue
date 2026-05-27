<template>
  <div class="source-panel">
    <div class="panel-header">
      <el-icon><Files /></el-icon>
      <span>引用来源</span>
      <span class="count" v-if="sources.length">{{ sources.length }}</span>
    </div>
    <div v-if="!sources.length" class="empty-state">
      <el-icon><DocumentCopy /></el-icon>
      <p>发送问题后，<br>引用片段将显示在此</p>
    </div>
    <div v-else class="source-list">
      <div v-for="(s, i) in sources" :key="s.chunkId ?? i" class="source-item" :class="{ weak: isWeakSource(s) }">
        <div class="source-meta">
          <span class="source-idx">{{ i + 1 }}</span>
          <span class="source-file">{{ s.fileName }}</span>
          <span v-if="hasScore(s)" class="source-score">{{ formatScore(s.score) }}</span>
        </div>
        <div class="source-detail">
          <span>文档 #{{ s.documentId }}</span>
          <span>Chunk {{ s.chunkIndex }}</span>
        </div>
        <div v-if="isWeakSource(s)" class="source-warning">匹配度较低，请结合原文确认。</div>
        <p class="source-content">{{ s.content }}</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { RagSourceChunk } from '@/types/chat'
defineProps<{ sources: RagSourceChunk[] }>()

function hasScore(source: RagSourceChunk) {
  return Number.isFinite(source.score)
}

function formatScore(score: number) {
  return `${Math.max(0, Math.min(100, score * 100)).toFixed(0)}%`
}

function isWeakSource(source: RagSourceChunk) {
  return hasScore(source) && source.score < 0.35
}
</script>

<style scoped>
.source-panel { height: 100%; display: flex; flex-direction: column; }
.panel-header {
  display: flex; align-items: center; gap: 7px; padding: 16px 16px 12px;
  font-size: 13px; font-weight: 600; color: var(--color-text-secondary);
  border-bottom: 1px solid var(--color-border);
}
.count { background: var(--color-accent); color: #fff; font-size: 11px; padding: 1px 6px; border-radius: 10px; }
.empty-state { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 10px; color: var(--color-text-muted); font-size: 13px; text-align: center; }
.empty-state .el-icon { font-size: 28px; }
.source-list { flex: 1; overflow-y: auto; padding: 12px; display: flex; flex-direction: column; gap: 10px; }
.source-item { background: var(--color-bg); border-radius: var(--radius-md); padding: 12px; border: 1px solid var(--color-border); }
.source-item.weak { border-color: #fde68a; background: #fffbeb; }
.source-meta { display: flex; align-items: center; gap: 6px; margin-bottom: 8px; }
.source-idx { width: 20px; height: 20px; background: var(--color-accent-light); color: var(--color-accent); border-radius: 50%; font-size: 11px; font-weight: 700; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.source-file { font-size: 12px; font-weight: 500; color: var(--color-text-primary); flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.source-score { font-size: 11px; color: var(--color-success); font-weight: 600; }
.source-item.weak .source-score { color: #b45309; }
.source-detail { display: flex; flex-wrap: wrap; gap: 6px; margin: -2px 0 8px; color: var(--color-text-muted); font-size: 11px; }
.source-warning { margin-bottom: 8px; color: #92400e; font-size: 11px; line-height: 1.4; }
.source-content { font-size: 12px; color: var(--color-text-secondary); line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 4; -webkit-box-orient: vertical; overflow: hidden; }
</style>
