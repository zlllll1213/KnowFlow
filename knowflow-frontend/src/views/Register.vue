<template>
  <div class="auth-page">
    <div class="auth-left">
      <div class="hero-content">
        <div class="hero-badge">开始您的知识管理之旅</div>
        <h1>构建您的智能知识库</h1>
        <p>上传文档、智能解析、AI 问答——KnowFlow 让团队知识永不流失。</p>
        <div class="hero-steps">
          <div class="step"><span class="step-num">01</span><span>上传 PDF / DOCX / Markdown</span></div>
          <div class="step"><span class="step-num">02</span><span>自动解析与向量化</span></div>
          <div class="step"><span class="step-num">03</span><span>智能问答，精准引用</span></div>
        </div>
      </div>
    </div>
    <div class="auth-right">
      <div class="auth-card">
        <div class="card-brand">
          <span class="brand-icon">K</span>
          <span class="brand-name">KnowFlow</span>
        </div>
        <h2 class="card-title">创建账户</h2>
        <p class="card-subtitle">免费注册，立即开始</p>
        <el-form ref="formRef" :model="form" :rules="rules" @submit.prevent="handleRegister">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="用户名" size="large" prefix-icon="User" />
          </el-form-item>
          <el-form-item prop="email">
            <el-input v-model="form.email" placeholder="邮箱" size="large" prefix-icon="Message" />
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="form.password" type="password" placeholder="密码（含字母+数字，8-20位）" size="large" prefix-icon="Lock" show-password />
          </el-form-item>
          <el-form-item prop="confirmPassword">
            <el-input v-model="form.confirmPassword" type="password" placeholder="确认密码" size="large" prefix-icon="Lock" show-password @keyup.enter="handleRegister" />
          </el-form-item>
          <el-button type="primary" size="large" class="submit-btn" :loading="loading" @click="handleRegister">注册账户</el-button>
        </el-form>
        <div class="card-footer">
          已有账户？<router-link to="/login">立即登录</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { register } from '@/api/auth'
import type { FormInstance, FormRules } from 'element-plus'

const router = useRouter()
const loading = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({ username: '', email: '', password: '', confirmPassword: '' })
const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (!/^(?=.*[a-zA-Z])(?=.*\d).{8,20}$/.test(value)) {
          callback(new Error('密码需包含字母和数字，长度 8-20 位'))
        } else callback()
      },
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.password) callback(new Error('两次密码不一致'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
}

async function handleRegister() {
  if (!await formRef.value?.validate().catch(() => false)) return
  loading.value = true
  try {
    await register({
      username: form.username.trim(),
      email: form.email.trim(),
      password: form.password,
    })
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch (e: any) {
    ElMessage.error(e.message || '注册失败')
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
  background: radial-gradient(ellipse at 30% 70%, rgba(79,70,229,.3) 0%, transparent 60%);
}
.hero-content { position: relative; z-index: 1; max-width: 400px; }
.hero-badge { display: inline-block; background: rgba(79,70,229,.3); color: #a5b4fc; border: 1px solid rgba(165,180,252,.3); border-radius: 20px; padding: 5px 14px; font-size: 12px; font-weight: 500; margin-bottom: 24px; }
.hero-content h1 { font-family: var(--font-heading); font-size: 36px; font-weight: 700; color: #fff; line-height: 1.2; margin-bottom: 16px; }
.hero-content p { font-size: 15px; color: #94a3b8; line-height: 1.7; margin-bottom: 32px; }
.hero-steps { display: flex; flex-direction: column; gap: 16px; }
.step { display: flex; align-items: center; gap: 14px; font-size: 14px; color: #cbd5e1; }
.step-num { font-family: var(--font-heading); font-size: 20px; font-weight: 700; color: #4f46e5; }
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

@media (max-width: 820px) {
  .auth-page {
    min-height: 100svh;
    height: auto;
    flex-direction: column;
    background: var(--color-bg);
  }

  .auth-left {
    flex: none;
    min-height: 240px;
    padding: 34px 24px 30px;
    align-items: flex-end;
  }

  .hero-content {
    max-width: 100%;
  }

  .hero-badge {
    margin-bottom: 14px;
  }

  .hero-content h1 {
    font-size: 30px;
    margin-bottom: 10px;
  }

  .hero-content p {
    font-size: 14px;
    line-height: 1.6;
    margin-bottom: 18px;
  }

  .hero-steps {
    gap: 10px;
  }

  .step {
    font-size: 13px;
    gap: 10px;
  }

  .step-num {
    font-size: 17px;
  }

  .auth-right {
    width: 100%;
    padding: 28px 22px 34px;
    align-items: flex-start;
  }

  .auth-card {
    max-width: none;
  }

  .card-brand {
    margin-bottom: 24px;
  }
}

@media (max-width: 480px) {
  .auth-left {
    min-height: 190px;
    padding: 28px 20px 24px;
  }

  .hero-steps {
    display: none;
  }

  .hero-content h1 {
    font-size: 28px;
  }

  .auth-right {
    padding: 24px 18px 32px;
  }
}
</style>
