<template>
  <div>
    <div class="page-header">
      <div>
        <div class="page-title">系统设置</div>
        <div class="page-subtitle">账户信息与偏好设置</div>
      </div>
    </div>
    <div class="settings-grid">
      <div class="card settings-card">
        <div class="settings-section-title">个人信息</div>
        <div class="info-row">
          <span class="info-label">用户名</span>
          <span class="info-value">{{ authStore.userInfo?.username }}</span>
        </div>
        <div class="info-row">
          <span class="info-label">邮箱</span>
          <span class="info-value">{{ authStore.userInfo?.email || '—' }}</span>
        </div>
        <div class="info-row">
          <span class="info-label">注册时间</span>
          <span class="info-value">{{ formatDate(authStore.userInfo?.createdAt) }}</span>
        </div>
        <el-button class="logout-action" type="danger" plain @click="handleLogout">
          <el-icon><SwitchButton /></el-icon>
          退出登录
        </el-button>
      </div>
      <div class="card settings-card">
        <div class="settings-section-title">后续功能规划</div>
        <div class="roadmap-list">
          <div class="roadmap-item" v-for="item in roadmap" :key="item.title">
            <span class="roadmap-icon"><el-icon><component :is="item.icon" /></el-icon></span>
            <div>
              <div class="roadmap-title">{{ item.title }}</div>
              <div class="roadmap-desc">{{ item.desc }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'

const authStore = useAuthStore()
const router = useRouter()

const roadmap = [
  { icon: 'Connection', title: 'SSE 流式输出', desc: '问答结果逐字流式返回，提升交互体验' },
  { icon: 'Link', title: '引用来源展示', desc: '精确展示回答依据的文档片段与相似度' },
  { icon: 'Refresh', title: '解析进度实时刷新', desc: '文档处理状态实时 Polling 或 WebSocket 推送' },
  { icon: 'Search', title: '全文检索', desc: '跨知识库关键词搜索与高亮展示' },
]

function formatDate(d?: string) {
  return d ? new Date(d).toLocaleDateString('zh-CN') : '—'
}

function handleLogout() {
  authStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.settings-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; max-width: 900px; }
.settings-card { padding: 24px; }
.settings-section-title { font-family: var(--font-heading); font-size: 15px; font-weight: 600; margin-bottom: 20px; padding-bottom: 12px; border-bottom: 1px solid var(--color-border); }
.info-row { display: flex; align-items: center; padding: 10px 0; border-bottom: 1px solid var(--color-border); }
.info-row:last-child { border-bottom: none; }
.info-label { font-size: 13px; color: var(--color-text-secondary); width: 80px; flex-shrink: 0; }
.info-value { font-size: 13px; color: var(--color-text-primary); font-weight: 500; }
.logout-action { width: 100%; margin-top: 18px; }
.roadmap-list { display: flex; flex-direction: column; gap: 16px; }
.roadmap-item { display: flex; align-items: flex-start; gap: 12px; }
.roadmap-icon { width: 32px; height: 32px; background: var(--color-accent-light); color: var(--color-accent); border-radius: 8px; display: flex; align-items: center; justify-content: center; font-size: 15px; flex-shrink: 0; }
.roadmap-title { font-size: 13px; font-weight: 600; color: var(--color-text-primary); margin-bottom: 2px; }
.roadmap-desc { font-size: 12px; color: var(--color-text-secondary); }

@media (max-width: 820px) {
  .settings-grid {
    grid-template-columns: 1fr;
    max-width: none;
  }
}

@media (max-width: 480px) {
  .settings-card {
    padding: 18px;
  }

  .info-row {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
  }

  .info-label {
    width: auto;
  }

  .info-value {
    max-width: 100%;
    overflow-wrap: anywhere;
  }
}
</style>
