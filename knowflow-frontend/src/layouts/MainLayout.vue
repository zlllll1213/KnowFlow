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
.main-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background:
    radial-gradient(circle at 28% -8%, rgba(47, 114, 255, .22), transparent 30%),
    radial-gradient(circle at 82% 8%, rgba(49, 199, 255, .12), transparent 26%),
    linear-gradient(135deg, #020817 0%, #041734 48%, #061a3d 100%);
}
.layout-body { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
.layout-content {
  position: relative;
  flex: 1;
  overflow-y: auto;
  padding: 28px 28px;
  background:
    linear-gradient(90deg, rgba(49, 199, 255, .04) 1px, transparent 1px),
    linear-gradient(180deg, rgba(49, 199, 255, .035) 1px, transparent 1px);
  background-size: 42px 42px;
}

.layout-content::before {
  content: '';
  position: fixed;
  inset: auto 0 0 220px;
  height: 220px;
  pointer-events: none;
  background:
    radial-gradient(ellipse at 50% 100%, rgba(47, 114, 255, .24), transparent 62%),
    linear-gradient(180deg, transparent, rgba(2, 8, 23, .44));
}

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
