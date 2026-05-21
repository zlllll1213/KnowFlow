<template>
  <header class="app-header">
    <div class="header-brand">
      <span class="brand-icon">K</span>
      <span class="brand-name">KnowFlow</span>
    </div>
    <div class="header-right">
      <span class="user-name">{{ authStore.userInfo?.username || '用户' }}</span>
      <el-button size="small" text @click="handleLogout">
        <el-icon><SwitchButton /></el-icon> 退出
      </el-button>
    </div>
  </header>
</template>

<script setup lang="ts">
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'

const authStore = useAuthStore()
const router = useRouter()

function handleLogout() {
  authStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.app-header {
  height: 56px; background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 24px; flex-shrink: 0;
}
.header-brand { display: flex; align-items: center; gap: 10px; }
.brand-icon {
  width: 30px; height: 30px; background: var(--color-accent);
  border-radius: 8px; display: flex; align-items: center; justify-content: center;
  color: #fff; font-family: var(--font-heading); font-weight: 700; font-size: 15px;
}
.brand-name { font-family: var(--font-heading); font-weight: 700; font-size: 17px; color: var(--color-text-primary); }
.header-right { display: flex; align-items: center; gap: 12px; }
.user-name { font-size: 14px; color: var(--color-text-secondary); }
</style>
