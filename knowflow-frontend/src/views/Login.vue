<template>
  <div class="auth-page">
    <header class="auth-mobile-brand">
      <span class="brand-mark" aria-hidden="true"><span></span></span>
      <strong>KnowFlow <em>Agent</em></strong>
    </header>

    <section class="auth-story">
      <div class="brand-row desktop-only">
        <span class="brand-mark" aria-hidden="true"><span></span></span>
        <strong>KnowFlow <em>Agent</em></strong>
      </div>

      <div class="hero-copy">
        <h1>从文档到可信回答</h1>
        <p>KnowFlow 将上传文档解析为可检索知识库，回答会带上来源片段和 Agent 执行轨迹。</p>
      </div>

      <div class="flow-board">
        <div v-for="step in flowSteps" :key="step.no" class="flow-card">
          <span class="step-index">{{ step.no }}</span>
          <el-icon><component :is="step.icon" /></el-icon>
          <div>
            <strong>{{ step.title }}</strong>
            <small>{{ step.caption }}</small>
          </div>
        </div>
      </div>

      <div class="tech-row">
        <span>RAG</span>
        <span>pgvector</span>
        <span>SSE</span>
        <span>Citation Guard</span>
      </div>
    </section>

    <section class="auth-form">
      <div class="auth-card">
        <div class="card-brand">
          <span class="brand-mark small" aria-hidden="true"><span></span></span>
          <strong>KnowFlow <em>Agent</em></strong>
        </div>
        <h2>欢迎回来</h2>
        <p>登录后继续上传、检索和核对引用来源。</p>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="handleLogin">
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" placeholder="请输入用户名" size="large" prefix-icon="User" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              size="large"
              prefix-icon="Lock"
              show-password
              @keyup.enter="handleLogin"
            />
          </el-form-item>
          <el-button type="primary" size="large" class="submit-btn" :loading="loading" @click="handleLogin">
            登录工作台
          </el-button>
        </el-form>

        <div class="card-footer">
          还没有账户？<router-link to="/register">立即注册</router-link>
        </div>
      </div>

      <div class="security-row">
        <span><el-icon><Lock /></el-icon> JWT 访问控制</span>
        <span>数据加密存储</span>
        <span>引用可追溯</span>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { useAuthStore } from '@/stores/auth'
import { safeInternalRedirect } from '@/utils/redirect'
import type { FormInstance, FormRules } from 'element-plus'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const loading = ref(false)
const formRef = ref<FormInstance>()

const flowSteps = [
  { no: '01', title: '上传文档', caption: 'PDF、Word、Markdown', icon: 'UploadFilled' },
  { no: '02', title: '解析切片', caption: '抽取文本并生成 chunk', icon: 'DocumentChecked' },
  { no: '03', title: '向量检索', caption: '在知识库中召回依据', icon: 'Share' },
  { no: '04', title: 'Agent 回答', caption: '按意图生成结果', icon: 'ChatDotRound' },
  { no: '05', title: '来源校验', caption: '展示 sources 与 trace', icon: 'Finished' },
]

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
    router.push(safeInternalRedirect(route.query.redirect))
  } catch (e: any) {
    ElMessage.error(e.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(420px, 520px);
  overflow: hidden;
  background: #eef5ff;
}

.auth-mobile-brand {
  display: none;
}

.auth-story {
  position: relative;
  min-height: 100vh;
  padding: 52px clamp(36px, 5vw, 72px);
  overflow: hidden;
  color: #fff;
  background:
    radial-gradient(circle at 80% 18%, rgba(33, 103, 216, .36), transparent 30%),
    radial-gradient(circle at 36% 88%, rgba(37, 211, 255, .18), transparent 34%),
    linear-gradient(145deg, #030712 0%, #061733 48%, #08285c 100%);
}

.auth-story::before,
.auth-story::after {
  content: '';
  position: absolute;
  pointer-events: none;
}

.auth-story::before {
  inset: 0;
  background:
    linear-gradient(90deg, rgba(139, 180, 255, .08) 1px, transparent 1px),
    linear-gradient(180deg, rgba(139, 180, 255, .06) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: linear-gradient(180deg, rgba(0,0,0,.78), transparent 88%);
}

.auth-story::after {
  left: 0;
  right: 0;
  bottom: 0;
  height: 30%;
  background:
    radial-gradient(ellipse at 50% 100%, rgba(47, 114, 255, .34), transparent 62%),
    repeating-linear-gradient(5deg, rgba(49, 199, 255, .16) 0 1px, transparent 1px 18px);
  opacity: .58;
}

.brand-row,
.card-brand,
.auth-mobile-brand {
  position: relative;
  z-index: 1;
  align-items: center;
  gap: 12px;
}

.brand-row,
.card-brand {
  display: flex;
}

.desktop-only {
  display: flex;
}

.brand-row strong,
.card-brand strong,
.auth-mobile-brand strong {
  font-size: 19px;
  font-weight: 800;
}

.brand-row em,
.card-brand em,
.auth-mobile-brand em {
  color: #7da7ff;
  font-style: normal;
}

.brand-mark {
  position: relative;
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #25d3ff, #315dff);
  clip-path: polygon(50% 0, 93% 25%, 93% 75%, 50% 100%, 7% 75%, 7% 25%);
  box-shadow: 0 0 24px rgba(49, 199, 255, .42);
}

.brand-mark span {
  width: 16px;
  height: 18px;
  border: 4px solid rgba(255,255,255,.82);
  border-top: 0;
  border-radius: 0 0 11px 11px;
  transform: translateY(2px);
}

.brand-mark.small {
  width: 40px;
  height: 40px;
}

.hero-copy {
  position: relative;
  z-index: 1;
  max-width: 660px;
  margin-top: clamp(68px, 10vh, 116px);
}

.hero-copy h1 {
  max-width: 11ch;
  color: #fff;
  font-size: clamp(56px, 7.4vw, 92px);
  line-height: .98;
  font-weight: 900;
  letter-spacing: 0;
  text-wrap: balance;
}

.hero-copy p {
  max-width: 60ch;
  margin-top: 26px;
  color: #d6e3f8;
  font-size: 17px;
  line-height: 1.72;
  text-wrap: pretty;
}

.flow-board {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  max-width: 780px;
  gap: 10px;
  margin-top: 42px;
}

.flow-card {
  display: grid;
  grid-template-columns: 38px 32px minmax(0, 1fr);
  align-items: center;
  gap: 12px;
  min-height: 74px;
  padding: 12px;
  border: 1px solid rgba(138, 180, 255, .24);
  border-radius: 8px;
  background: rgba(3, 16, 39, .7);
  box-shadow: inset 0 1px 0 rgba(255,255,255,.08);
}

.step-index {
  display: grid;
  place-items: center;
  width: 34px;
  height: 30px;
  border-radius: 7px;
  background: #1c4fbd;
  color: #eaf2ff;
  font-size: 12px;
  font-weight: 900;
}

.flow-card .el-icon {
  color: #5dd6ff;
  font-size: 24px;
}

.flow-card strong {
  display: block;
  color: #f2f7ff;
  font-size: 14px;
  font-weight: 800;
}

.flow-card small {
  display: block;
  margin-top: 4px;
  overflow: hidden;
  color: #9fb4d5;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tech-row {
  position: relative;
  z-index: 1;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 28px;
}

.tech-row span {
  padding: 9px 14px;
  border: 1px solid rgba(138, 180, 255, .24);
  border-radius: 8px;
  background: rgba(3, 16, 39, .58);
  color: #cfe0f8;
  text-align: center;
  font-size: 13px;
  font-weight: 700;
}

.auth-form {
  position: relative;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 44px;
  background:
    radial-gradient(circle at 100% 0, rgba(47, 114, 255, .1), transparent 38%),
    linear-gradient(145deg, #f7fbff, #edf4ff);
}

.auth-card {
  width: min(100%, 430px);
  padding: 42px 38px 34px;
  border: 1px solid rgba(123, 145, 180, .28);
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 28px 70px rgba(23, 49, 92, .13);
}

.card-brand {
  justify-content: center;
  color: #081a3d;
}

.auth-card h2 {
  margin-top: 30px;
  color: #0b1a3b;
  text-align: center;
  font-size: 28px;
  font-weight: 900;
  text-wrap: balance;
}

.auth-card p {
  margin: 10px 0 28px;
  color: #5f6f88;
  text-align: center;
  font-size: 15px;
  line-height: 1.55;
}

.auth-card :deep(.el-form-item__label) {
  color: #15233d !important;
  font-weight: 800;
}

.auth-card :deep(.el-input__wrapper) {
  height: 52px;
  background: #fff !important;
  box-shadow: 0 0 0 1px #d8e2f2 inset !important;
}

.auth-card :deep(.el-input__wrapper:hover),
.auth-card :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #2f72ff inset, 0 0 0 4px rgba(47, 114, 255, .12) !important;
}

.auth-card :deep(.el-input__inner) {
  color: #10203d !important;
}

.submit-btn {
  width: 100%;
  height: 54px;
  margin-top: 8px;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 900;
  box-shadow: 0 16px 34px rgba(47, 114, 255, .28);
}

.card-footer {
  margin-top: 28px;
  color: #667891;
  text-align: center;
}

.card-footer a {
  color: #2f72ff;
  font-weight: 800;
  text-decoration: none;
}

.card-footer a:hover {
  text-decoration: underline;
}

.security-row {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 18px;
  margin-top: 34px;
  color: #667891;
  font-size: 13px;
}

.security-row span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

@media (max-width: 1120px) {
  .auth-page {
    min-height: 100vh;
    grid-template-columns: 1fr;
    overflow-y: auto;
    background:
      radial-gradient(circle at 80% 0, rgba(47, 114, 255, .2), transparent 34%),
      linear-gradient(145deg, #061733 0%, #edf4ff 58%);
  }

  .auth-mobile-brand {
    position: relative;
    z-index: 2;
    display: flex;
    padding: 24px 24px 0;
    color: #fff;
  }

  .auth-mobile-brand strong {
    font-size: 18px;
    font-weight: 900;
  }

  .desktop-only {
    display: none;
  }

  .auth-form {
    order: 1;
    min-height: auto;
    padding: 28px 24px 24px;
  }

  .auth-story {
    order: 2;
    min-height: auto;
    padding: 28px 24px 38px;
  }

  .hero-copy {
    margin-top: 0;
  }

  .hero-copy h1 {
    max-width: 18ch;
    font-size: 40px;
    line-height: 1.08;
  }

  .hero-copy p {
    max-width: 62ch;
    margin-top: 16px;
    font-size: 15px;
  }

  .flow-board {
    max-width: none;
    margin-top: 24px;
  }

  .auth-card {
    width: min(100%, 520px);
    margin: 0 auto;
  }
}

@media (max-width: 720px) {
  .auth-mobile-brand {
    padding: 18px 16px 0;
  }

  .auth-form {
    padding: 18px 16px;
  }

  .auth-card {
    padding: 28px 20px 24px;
  }

  .card-brand {
    justify-content: flex-start;
  }

  .auth-card h2,
  .auth-card p {
    text-align: left;
  }

  .auth-card h2 {
    margin-top: 24px;
    font-size: 26px;
  }

  .flow-board {
    grid-template-columns: 1fr;
  }

  .flow-card {
    min-height: 64px;
  }

  .tech-row span {
    flex: 1 1 calc(50% - 10px);
  }

  .security-row {
    margin-top: 18px;
    justify-content: flex-start;
  }
}
</style>
