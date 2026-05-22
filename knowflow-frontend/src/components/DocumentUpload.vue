<template>
  <div
    class="upload-area"
    :class="{ dragging }"
    @dragover.prevent="dragging = true"
    @dragleave="dragging = false"
    @drop.prevent="onDrop"
    @click="fileInput?.click()"
  >
    <el-icon class="upload-icon"><UploadFilled /></el-icon>
    <div class="upload-text">拖拽文件到此处，或 <span class="link">点击上传</span></div>
    <div class="upload-hint">支持 PDF、DOCX、TXT、Markdown，单文件最大 50MB</div>
    <input ref="fileInput" type="file" :accept="accept" multiple style="display:none" @change="onFileChange" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const props = withDefaults(defineProps<{
  accept?: string
}>(), {
  accept: '.pdf,.docx,.txt,.md,.markdown',
})

const emit = defineEmits<{ files: [files: File[]] }>()

const dragging = ref(false)
const fileInput = ref<HTMLInputElement>()

function onDrop(e: DragEvent) {
  dragging.value = false
  const files = Array.from(e.dataTransfer?.files ?? [])
  if (files.length) emit('files', files)
}

function onFileChange(e: Event) {
  const files = Array.from((e.target as HTMLInputElement).files ?? [])
  if (files.length) emit('files', files)
  if (fileInput.value) fileInput.value.value = ''
}
</script>

<style scoped>
.upload-area {
  border: 2px dashed var(--color-border); border-radius: var(--radius-lg);
  padding: 40px 24px; text-align: center; cursor: pointer;
  transition: all .2s; background: var(--color-bg);
}
.upload-area:hover, .upload-area.dragging {
  border-color: var(--color-accent); background: var(--color-accent-light);
}
.upload-icon { font-size: 36px; color: var(--color-text-muted); margin-bottom: 12px; }
.upload-text { font-size: 14px; color: var(--color-text-secondary); margin-bottom: 4px; }
.link { color: var(--color-accent); font-weight: 500; }
.upload-hint { font-size: 12px; color: var(--color-text-muted); }

@media (max-width: 480px) {
  .upload-area {
    padding: 30px 16px;
  }

  .upload-text {
    line-height: 1.5;
  }
}
</style>
