<template>
  <div class="register-page">
    <section class="register-visual">
      <div class="brand-row">
        <span class="brand-mark" aria-hidden="true"><span></span></span>
        <strong>KnowFlow <em>Agent</em></strong>
      </div>
      <div class="hero-copy">
        <h1>构建团队的<br><span>可追溯知识流</span></h1>
        <p>创建账户后即可接入知识库、上传文档，并用 RAG 与 Agent 问答沉淀团队知识。</p>
      </div>
      <div class="hero-steps">
        <div class="step"><b>01</b><span>创建知识库</span></div>
        <div class="step"><b>02</b><span>上传 PDF / DOCX / Markdown</span></div>
        <div class="step"><b>03</b><span>获得带引用的 Agent 回答</span></div>
      </div>
    </section>

    <section class="register-form">
      <div class="auth-card">
        <div class="card-brand">
          <span class="brand-mark small" aria-hidden="true"><span></span></span>
          <strong>KnowFlow <em>Agent</em></strong>
        </div>
        <h2>创建账户</h2>
        <p>注册后即可开始构建知识库</p>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="handleRegister">
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" placeholder="请输入用户名" size="large" prefix-icon="User" />
          </el-form-item>
          <el-form-item label="邮箱" prop="email">
            <el-input v-model="form.email" placeholder="请输入邮箱" size="large" prefix-icon="Message" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" type="password" placeholder="含字母+数字，8-20位" size="large" prefix-icon="Lock" show-password />
          </el-form-item>
          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input v-model="form.confirmPassword" type="password" placeholder="再次输入密码" size="large" prefix-icon="Lock" show-password @keyup.enter="handleRegister" />
          </el-form-item>
          <el-button type="primary" size="large" class="submit-btn" :loading="loading" @click="handleRegister">注册账户</el-button>
        </el-form>
        <div class="card-footer">
          已有账户？<router-link to="/login">立即登录</router-link>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { register } from '@/api/auth'
import type { FormInstance, FormRules } from 'element-plus'

const router = useRouter()
const loading = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({ username: '', email: '', password: '', confirmPassword: '' })
const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
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
.register-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(500px, .95fr) minmax(420px, 1.05fr);
  background: #eef5ff;
}

.register-visual {
  position: relative;
  padding: 56px;
  overflow: hidden;
  color: #fff;
  background:
    radial-gradient(circle at 18% 12%, rgba(49, 199, 255, .28), transparent 34%),
    radial-gradient(circle at 88% 70%, rgba(47, 114, 255, .32), transparent 34%),
    linear-gradient(135deg, #020817, #031b43 58%, #05296a);
}

.register-visual::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    linear-gradient(90deg, rgba(49,199,255,.08) 1px, transparent 1px),
    linear-gradient(180deg, rgba(49,199,255,.06) 1px, transparent 1px);
  background-size: 48px 48px;
}

.brand-row,
.card-brand {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-row strong,
.card-brand strong {
  font-size: 20px;
  font-weight: 800;
}

em { color: #7da7ff; font-style: normal; }

.brand-mark {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #25d3ff, #315dff);
  clip-path: polygon(50% 0, 93% 25%, 93% 75%, 50% 100%, 7% 75%, 7% 25%);
  box-shadow: 0 0 24px rgba(49, 199, 255, .5);
}

.brand-mark span {
  width: 16px;
  height: 18px;
  border: 4px solid rgba(255,255,255,.82);
  border-top: 0;
  border-radius: 0 0 11px 11px;
  transform: translateY(2px);
}

.brand-mark.small { width: 40px; height: 40px; }

.hero-copy {
  position: relative;
  z-index: 1;
  margin-top: 92px;
  max-width: 520px;
}

.hero-copy h1 {
  font-size: clamp(40px, 5vw, 60px);
  line-height: 1.16;
  font-weight: 900;
}

.hero-copy span {
  color: #3dbdff;
}

.hero-copy p {
  margin-top: 22px;
  color: #b8c8e6;
  font-size: 17px;
  line-height: 1.75;
}

.hero-steps {
  position: relative;
  z-index: 1;
  display: grid;
  gap: 16px;
  margin-top: 56px;
}

.step {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px;
  border: 1px solid rgba(108, 163, 255, .36);
  border-radius: 10px;
  background: rgba(8, 31, 73, .64);
}

.step b {
  color: #3dbdff;
  font-size: 18px;
}

.step span {
  color: #eef5ff;
  font-weight: 800;
}

.register-form {
  display: grid;
  place-items: center;
  padding: 48px;
  background:
    radial-gradient(circle at 100% 0, rgba(47, 114, 255, .12), transparent 38%),
    linear-gradient(135deg, #f7fbff, #e8f1ff);
}

.auth-card {
  width: min(100%, 500px);
  padding: 44px;
  border: 1px solid rgba(154, 176, 214, .45);
  border-radius: 12px;
  background: rgba(255, 255, 255, .9);
  box-shadow: 0 28px 80px rgba(23, 49, 92, .16);
}

.card-brand { justify-content: center; color: #081a3d; }

.auth-card h2 {
  margin-top: 28px;
  color: #0b1a3b;
  text-align: center;
  font-size: 30px;
  font-weight: 900;
}

.auth-card p {
  margin: 10px 0 26px;
  color: #7d8da8;
  text-align: center;
}

.auth-card :deep(.el-form-item__label) {
  color: #15233d !important;
  font-weight: 800;
}

.auth-card :deep(.el-input__wrapper) {
  height: 50px;
  background: #fff !important;
  box-shadow: 0 0 0 1px #d8e2f2 inset !important;
}

.auth-card :deep(.el-input__inner) {
  color: #10203d !important;
}

.submit-btn {
  width: 100%;
  height: 52px;
  margin-top: 6px;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 900;
}

.card-footer {
  margin-top: 24px;
  color: #7d8da8;
  text-align: center;
}

.card-footer a {
  color: #2f72ff;
  font-weight: 800;
  text-decoration: none;
}

@media (max-width: 980px) {
  .register-page {
    grid-template-columns: 1fr;
    background:
      radial-gradient(circle at 80% 0, rgba(47, 114, 255, .18), transparent 34%),
      linear-gradient(145deg, #061733 0%, #edf4ff 58%);
  }

  .register-form {
    order: 1;
    padding: 28px 24px 24px;
  }

  .register-visual {
    order: 2;
    min-height: auto;
    padding: 28px 24px 38px;
  }

  .hero-copy {
    margin-top: 36px;
  }

  .hero-copy h1 {
    font-size: 40px;
    line-height: 1.08;
  }

  .hero-copy p {
    font-size: 15px;
  }

  .hero-steps {
    margin-top: 26px;
    gap: 10px;
  }

  .step {
    padding: 13px 14px;
  }
}

@media (max-width: 560px) {
  .hero-copy h1 { font-size: 36px; }
  .hero-steps { margin-top: 30px; }
  .auth-card { padding: 32px 22px; }
}
</style>
