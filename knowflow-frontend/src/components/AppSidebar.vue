<template>
  <nav class="sidebar">
    <div class="sidebar-logo">
      <span class="logo-icon">K</span>
      <span class="logo-text">KnowFlow</span>
    </div>
    <ul class="nav-list">
      <li v-for="item in navItems" :key="item.path">
        <router-link :to="item.path" class="nav-item" :class="{ active: isActive(item.path) }">
          <el-icon class="nav-icon"><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </router-link>
      </li>
    </ul>
    <div class="sidebar-footer">
      <div class="user-info">
        <div class="avatar">{{ initial }}</div>
        <div class="user-detail">
          <div class="username">{{ authStore.userInfo?.username || '用户' }}</div>
          <div class="user-role">知识库管理员</div>
        </div>
      </div>
      <el-tooltip content="退出登录" placement="right">
        <button class="logout-btn" @click="handleLogout">
          <el-icon><SwitchButton /></el-icon>
        </button>
      </el-tooltip>
    </div>
  </nav>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const navItems = [
  { path: '/dashboard', label: '工作台', icon: 'Odometer' },
  { path: '/kb', label: '知识库', icon: 'Collection' },
  { path: '/documents', label: '文档管理', icon: 'Document' },
  { path: '/chat', label: '智能问答', icon: 'ChatDotRound' },
  { path: '/settings', label: '系统设置', icon: 'Setting' },
]

const initial = computed(() => (authStore.userInfo?.username?.[0] ?? 'U').toUpperCase())

function isActive(path: string) {
  return route.path.startsWith(path)
}

function handleLogout() {
  authStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.sidebar {
  width: 220px; min-width: 220px; background: var(--color-sidebar);
  display: flex; flex-direction: column; height: 100%;
}
.sidebar-logo {
  display: flex; align-items: center; gap: 10px;
  padding: 20px 20px 16px; border-bottom: 1px solid rgba(255,255,255,.06);
}
.logo-icon {
  width: 32px; height: 32px; background: var(--color-accent);
  border-radius: 8px; display: flex; align-items: center; justify-content: center;
  color: #fff; font-family: var(--font-heading); font-weight: 700; font-size: 16px;
}
.logo-text { font-family: var(--font-heading); font-weight: 700; font-size: 18px; color: #fff; }
.nav-list { list-style: none; padding: 12px 10px; flex: 1; }
.nav-item {
  display: flex; align-items: center; gap: 10px;
  padding: 9px 12px; border-radius: var(--radius-sm);
  color: #94a3b8; text-decoration: none; font-size: 14px; font-weight: 500;
  transition: all .15s;
}
.nav-item:hover { background: var(--color-sidebar-hover); color: #e2e8f0; }
.nav-item.active { background: var(--color-accent); color: #fff; }
.nav-icon { font-size: 16px; }
.sidebar-footer {
  padding: 14px 10px 18px; border-top: 1px solid rgba(255,255,255,.06);
  display: flex; align-items: center; gap: 8px;
}
.user-info { display: flex; align-items: center; gap: 10px; flex: 1; min-width: 0; }
.avatar {
  width: 32px; height: 32px; border-radius: 50%; background: #334155;
  display: flex; align-items: center; justify-content: center;
  color: #94a3b8; font-size: 13px; font-weight: 600; flex-shrink: 0;
}
.user-detail { min-width: 0; }
.username { font-size: 13px; color: #e2e8f0; font-weight: 500; truncate: ellipsis; white-space: nowrap; overflow: hidden; }
.user-role { font-size: 11px; color: #64748b; }
.logout-btn {
  background: none; border: none; cursor: pointer;
  color: #64748b; padding: 4px; border-radius: 4px;
  display: flex; align-items: center; transition: color .15s;
}
.logout-btn:hover { color: #94a3b8; }
</style>
