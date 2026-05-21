<template>
  <div class="auth-page">
    <div class="auth-left">
      <div class="hero-content">
        <div class="hero-badge">智能知识库平台</div>
        <h1>让知识触手可及</h1>
        <p>KnowFlow 将您的文档转化为智能知识库，通过 AI 问答快速获取精准信息。</p>
        <div class="hero-features">
          <div class="feature-item"><el-icon><Check /></el-icon> RAG 增强检索</div>
          <div class="feature-item"><el-icon><Check /></el-icon> 多格式文档解析</div>
          <div class="feature-item"><el-icon><Check /></el-icon> 实时向量化索引</div>
        </div>
      </div>
    </div>
    <div class="auth-right">
      <div class="auth-card">
        <div class="card-brand">
          <span class="brand-icon">K</span>
          <span class="brand-name">KnowFlow</span>
        </div>
        <h2 class="card-title">欢迎回来</h2>
        <p class="card-subtitle">登录您的账户以继续</p>
        <el-form ref="formRef" :model="form" :rules="rules" @submit.prevent="handleLogin">
          <el-form-item prop="username">
            <el-input
              v-model="form.username"
              placeholder="用户名"
              size="large"
              prefix-icon="User"
            />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="密码"
              size="large"
              prefix-icon="Lock"
              show-password
              @keyup.enter="handleLogin"
            />
          </el-form-item>
          <el-button
            type="primary" size="large" class="submit-btn"
            :loading="loading" @click="handleLogin"
          >登录</el-button>
        </el-form>
        <div class="card-footer">
          还没有账户？<router-link to="/register">立即注册</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import type { FormInstance, FormRules } from 'element-plus'

const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({ username: '', password: '' })
const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  if (!await formRef.value?.validate().catch(() => false)) return
  loading.value = true
  try {
    await authStore.login(form)
    router.push('/dashboard')
  } catch (e: any) {
    ElMessage.error(e.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page { display: flex; height: 100vh; }
.auth-left {
  flex: 1; background: linear-gradient(135deg, #0f172a 0%, #1e1b4b 60%, #312e81 100%);
  display: flex; align-items: center; justify-content: center; padding: 48px;
  position: relative; overflow: hidden;
}
.auth-left::before {
  content: ''; position: absolute; inset: 0;
  background: radial-gradient(ellipse at 70% 30%, rgba(79,70,229,.3) 0%, transparent 60%);
}
.hero-content { position: relative; z-index: 1; max-width: 400px; }
.hero-badge {
  display: inline-block; background: rgba(79,70,229,.3); color: #a5b4fc;
  border: 1px solid rgba(165,180,252,.3); border-radius: 20px;
  padding: 5px 14px; font-size: 12px; font-weight: 500; margin-bottom: 24px;
}
.hero-content h1 {
  font-family: var(--font-heading); font-size: 38px; font-weight: 700;
  color: #fff; line-height: 1.2; margin-bottom: 16px;
}
.hero-content p { font-size: 15px; color: #94a3b8; line-height: 1.7; margin-bottom: 28px; }
.hero-features { display: flex; flex-direction: column; gap: 10px; }
.feature-item { display: flex; align-items: center; gap: 10px; font-size: 14px; color: #cbd5e1; }
.feature-item .el-icon { color: #818cf8; font-size: 15px; }
.auth-right { width: 480px; flex-shrink: 0; display: flex; align-items: center; justify-content: center; background: var(--color-bg); padding: 40px; }
.auth-card { width: 100%; max-width: 380px; }
.card-brand { display: flex; align-items: center; gap: 10px; margin-bottom: 32px; }
.brand-icon { width: 32px; height: 32px; background: var(--color-accent); border-radius: 8px; display: flex; align-items: center; justify-content: center; color: #fff; font-family: var(--font-heading); font-weight: 700; font-size: 16px; }
.brand-name { font-family: var(--font-heading); font-weight: 700; font-size: 18px; color: var(--color-text-primary); }
.card-title { font-family: var(--font-heading); font-size: 26px; font-weight: 700; color: var(--color-text-primary); margin-bottom: 6px; }
.card-subtitle { font-size: 14px; color: var(--color-text-secondary); margin-bottom: 28px; }
.submit-btn { width: 100%; margin-top: 4px; height: 44px; font-size: 15px; font-weight: 600; }
.card-footer { text-align: center; margin-top: 20px; font-size: 14px; color: var(--color-text-secondary); }
.card-footer a { color: var(--color-accent); text-decoration: none; font-weight: 500; }
</style>
