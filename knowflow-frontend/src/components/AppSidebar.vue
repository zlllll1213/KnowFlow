<template>
  <nav class="sidebar">
    <div class="sidebar-logo">
      <span class="logo-icon" aria-hidden="true">
        <span></span>
      </span>
      <span class="logo-text">KnowFlow <em>Agent</em></span>
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
  { path: '/dashboard', label: '工作台', icon: 'HomeFilled' },
  { path: '/kb', label: '知识库', icon: 'Collection' },
  { path: '/documents', label: '文档管理', icon: 'Document' },
  { path: '/chat', label: '对话 / Chat', icon: 'ChatDotRound' },
  { path: '/settings', label: '设置', icon: 'Setting' },
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
  width: 220px; min-width: 220px;
  background:
    radial-gradient(circle at 15% 8%, rgba(47, 114, 255, .2), transparent 30%),
    linear-gradient(180deg, #020b1d 0%, #031124 100%);
  border-right: 1px solid rgba(112, 158, 222, .18);
  display: flex; flex-direction: column; height: 100%;
}
.sidebar-logo {
  display: flex; align-items: center; gap: 10px;
  padding: 28px 24px 20px; border-bottom: 1px solid rgba(112, 158, 222, .12);
}
.logo-icon {
  position: relative;
  width: 28px; height: 28px;
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #25d3ff, #315dff);
  clip-path: polygon(50% 0, 93% 25%, 93% 75%, 50% 100%, 7% 75%, 7% 25%);
  box-shadow: 0 0 22px rgba(49, 199, 255, .45);
}
.logo-icon span {
  width: 13px;
  height: 15px;
  border: 3px solid rgba(255,255,255,.78);
  border-top: 0;
  border-radius: 0 0 9px 9px;
  transform: translateY(2px);
}
.logo-text { font-family: var(--font-heading); font-weight: 800; font-size: 16px; color: #fff; white-space: nowrap; }
.logo-text em { color: #75a3ff; font-style: normal; font-weight: 800; }
.nav-list { list-style: none; padding: 16px 12px; flex: 1; }
.nav-item {
  display: flex; align-items: center; gap: 10px;
  padding: 12px 14px; border-radius: 8px;
  color: #a8bad7; text-decoration: none; font-size: 14px; font-weight: 700;
  transition: all .15s;
}
.nav-item:hover { background: var(--color-sidebar-hover); color: #eaf2ff; }
.nav-item.active {
  background: linear-gradient(90deg, rgba(47, 114, 255, .9), rgba(47, 114, 255, .26));
  color: #fff;
  box-shadow: inset 3px 0 0 #31c7ff, 0 12px 26px rgba(47, 114, 255, .16);
}
.nav-icon { font-size: 17px; color: inherit; }
.sidebar-footer {
  margin: 0 12px 16px;
  padding: 14px 12px;
  border: 1px solid rgba(112, 158, 222, .18);
  border-radius: 10px;
  background: rgba(7, 28, 62, .58);
  display: flex; align-items: center; gap: 8px;
}
.user-info { display: flex; align-items: center; gap: 10px; flex: 1; min-width: 0; }
.avatar {
  width: 32px; height: 32px; border-radius: 50%; background: linear-gradient(135deg, #17386f, #2f72ff);
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 13px; font-weight: 700; flex-shrink: 0;
}
.user-detail { min-width: 0; }
.username { font-size: 13px; color: #e2e8f0; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; overflow: hidden; }
.user-role { font-size: 11px; color: #7891b8; }
.logout-btn {
  background: none; border: none; cursor: pointer;
  color: #64748b; padding: 4px; border-radius: 4px;
  display: flex; align-items: center; transition: color .15s;
}
.logout-btn:hover { color: #94a3b8; }

@media (max-width: 768px) {
  .sidebar {
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 30;
    width: 100%;
    min-width: 0;
    height: 70px;
    background: rgba(15, 23, 42, .98);
    border-top: 1px solid rgba(255,255,255,.08);
    box-shadow: 0 -10px 28px rgba(15, 23, 42, .18);
    backdrop-filter: blur(14px);
  }

  .sidebar-logo,
  .sidebar-footer {
    display: none;
  }

  .nav-list {
    flex: none;
    height: 100%;
    display: grid;
    grid-template-columns: repeat(5, minmax(0, 1fr));
    gap: 2px;
    padding: 7px 6px calc(7px + env(safe-area-inset-bottom));
  }

  .nav-list li {
    min-width: 0;
  }

  .nav-item {
    height: 56px;
    flex-direction: column;
    justify-content: center;
    gap: 4px;
    padding: 6px 2px;
    border-radius: 10px;
    font-size: 11px;
    line-height: 1.1;
    text-align: center;
  }

  .nav-item span {
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .nav-icon {
    font-size: 17px;
  }
}
</style>
