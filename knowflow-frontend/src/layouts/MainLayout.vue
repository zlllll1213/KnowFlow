<template>
  <div class="main-layout">
    <AppSidebar />
    <div class="layout-body">
      <div class="layout-content">
        <router-view />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import AppSidebar from '@/components/AppSidebar.vue'
import { useAuthStore } from '@/stores/auth'
import { onMounted } from 'vue'

const authStore = useAuthStore()
onMounted(async () => {
  if (authStore.token && !authStore.userInfo?.email) {
    await authStore.fetchUserInfo().catch(() => {})
  }
})
</script>

<style scoped>
.main-layout { display: flex; height: 100vh; overflow: hidden; }
.layout-body { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
.layout-content { flex: 1; overflow-y: auto; padding: 32px 36px; background: var(--color-bg); }

@media (max-width: 768px) {
  .main-layout {
    display: block;
    height: 100svh;
    overflow: hidden;
  }

  .layout-body {
    height: 100%;
  }

  .layout-content {
    height: 100%;
    padding: 22px 16px 88px;
    overflow-x: hidden;
    -webkit-overflow-scrolling: touch;
  }
}
</style>
