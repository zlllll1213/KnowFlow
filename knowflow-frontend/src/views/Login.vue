<template>
  <div class="auth-page" :class="`mascot-${mascotMode}`">
    <section class="visual-panel">
      <div class="glow glow-lavender"></div>
      <div class="glow glow-sun"></div>
      <div class="hero-shell">
        <div class="hero-badge">智能知识库平台</div>

        <div class="illustration-stage" aria-hidden="true">
          <div class="blob blob-main"></div>
          <div class="blob blob-warm"></div>
          <div class="orb-card card-rag">RAG</div>
          <div class="orb-card card-ai">AI</div>
          <div class="sparkle sparkle-one"></div>
          <div class="sparkle sparkle-two"></div>

          <svg class="login-mascot" viewBox="0 0 260 230" role="img" aria-label="KnowFlow 登录助手">
            <defs>
              <linearGradient id="buddyBody" x1="58" y1="42" x2="198" y2="185" gradientUnits="userSpaceOnUse">
                <stop offset="0" stop-color="#F7FDFF" />
                <stop offset=".46" stop-color="#CFFAE9" />
                <stop offset="1" stop-color="#BFD7FF" />
              </linearGradient>
              <linearGradient id="buddyEar" x1="0" y1="0" x2="1" y2="1">
                <stop offset="0" stop-color="#B78CFF" />
                <stop offset="1" stop-color="#6D5DFB" />
              </linearGradient>
              <linearGradient id="buddyPaw" x1="0" y1="0" x2="1" y2="1">
                <stop offset="0" stop-color="#A78BFA" />
                <stop offset="1" stop-color="#60A5FA" />
              </linearGradient>
              <radialGradient id="buddyGlow" cx=".34" cy=".2" r=".72">
                <stop offset="0" stop-color="#FFFFFF" stop-opacity=".96" />
                <stop offset=".55" stop-color="#FFFFFF" stop-opacity=".18" />
                <stop offset="1" stop-color="#FFFFFF" stop-opacity="0" />
              </radialGradient>
              <filter id="buddyShadow" x="-30%" y="-25%" width="160%" height="160%">
                <feDropShadow dx="0" dy="18" stdDeviation="16" flood-color="#6D28D9" flood-opacity=".22" />
              </filter>
            </defs>

            <ellipse class="mascot-ground" cx="130" cy="206" rx="72" ry="14" />
            <g class="mascot-buddy" filter="url(#buddyShadow)">
              <path class="mascot-antenna-line" d="M130 48 C126 34 130 24 139 15" />
              <circle class="mascot-antenna-dot" cx="142" cy="12" r="9" />

              <g class="mascot-ear ear-left">
                <rect x="42" y="82" width="38" height="56" rx="19" />
                <circle cx="60" cy="110" r="7" />
              </g>
              <g class="mascot-ear ear-right">
                <rect x="180" y="82" width="38" height="56" rx="19" />
                <circle cx="199" cy="110" r="7" />
              </g>

              <path
                class="mascot-body"
                d="M62 115 C62 70 90 48 130 48 C170 48 198 70 198 115 C198 162 171 185 130 185 C89 185 62 162 62 115Z"
              />
              <path class="mascot-gloss" d="M84 74 C102 54 143 57 157 73 C138 66 112 66 84 74Z" />
              <ellipse class="mascot-face" cx="130" cy="124" rx="58" ry="48" />
              <ellipse class="mascot-blush blush-left" cx="88" cy="139" rx="13" ry="7" />
              <ellipse class="mascot-blush blush-right" cx="172" cy="139" rx="13" ry="7" />

              <g class="mascot-eye eye-left">
                <ellipse class="eye-white" cx="106" cy="114" rx="18" ry="21" />
                <circle class="eye-pupil" cx="108" cy="116" r="8" />
                <circle class="eye-highlight" cx="104" cy="110" r="3" />
              </g>
              <g class="mascot-eye eye-right">
                <ellipse class="eye-white" cx="154" cy="114" rx="18" ry="21" />
                <circle class="eye-pupil" cx="156" cy="116" r="8" />
                <circle class="eye-highlight" cx="152" cy="110" r="3" />
              </g>
              <path class="mascot-smile" d="M116 146 C122 154 138 154 144 146" />

              <g class="mascot-paw paw-left">
                <rect x="58" y="150" width="48" height="44" rx="18" />
                <path d="M72 160 L72 181" />
                <path d="M84 158 L84 181" />
                <path d="M96 163 L96 181" />
              </g>
              <g class="mascot-paw paw-right">
                <rect x="154" y="150" width="48" height="44" rx="18" />
                <path d="M166 163 L166 181" />
                <path d="M178 158 L178 181" />
                <path d="M190 160 L190 181" />
              </g>

            </g>
          </svg>
        </div>

        <div class="hero-copy">
          <h1>让知识触手可及</h1>
          <p>KnowFlow 将文档转化为可对话的智能知识库，让检索、阅读与协作变得轻盈。</p>
        </div>

        <div class="hero-features">
          <div class="feature-item"><el-icon><Check /></el-icon> RAG 增强检索</div>
          <div class="feature-item"><el-icon><Check /></el-icon> 多格式文档解析</div>
          <div class="feature-item"><el-icon><Check /></el-icon> 实时向量化索引</div>
        </div>
      </div>
    </section>

    <section class="form-panel">
      <div class="auth-card">
        <div class="card-brand">
          <span class="brand-icon">K</span>
          <span class="brand-name">KnowFlow</span>
        </div>
        <h2 class="card-title">欢迎回来</h2>
        <p class="card-subtitle">登录您的账户，继续构建知识流</p>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          @submit.prevent="handleLogin"
          @focusout="resetMascotModeSoon"
        >
          <div
            class="mascot-field username-field"
            @focusin="setMascotMode('peek')"
            @pointerdown="setMascotMode('peek')"
          >
            <el-form-item prop="username">
              <el-input
                v-model="form.username"
                placeholder="用户名"
                size="large"
                prefix-icon="User"
              />
            </el-form-item>
          </div>

          <div
            class="mascot-field password-field"
            @focusin="setMascotMode('cover')"
            @pointerdown="setMascotMode('cover')"
          >
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
          </div>

          <el-button
            type="primary"
            size="large"
            class="submit-btn"
            :loading="loading"
            @click="handleLogin"
          >
            登录
          </el-button>
        </el-form>

        <div class="card-footer">
          还没有账户？<router-link to="/register">立即注册</router-link>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { useAuthStore } from '@/stores/auth'
import type { FormInstance, FormRules } from 'element-plus'

const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const formRef = ref<FormInstance>()
const mascotMode = ref<'idle' | 'peek' | 'cover'>('idle')
let mascotResetTimer: ReturnType<typeof window.setTimeout> | undefined

const form = reactive({ username: '', password: '' })
const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

function setMascotMode(mode: 'idle' | 'peek' | 'cover') {
  if (mascotResetTimer) window.clearTimeout(mascotResetTimer)
  mascotMode.value = mode
}

function resetMascotModeSoon() {
  if (mascotResetTimer) window.clearTimeout(mascotResetTimer)
  mascotResetTimer = window.setTimeout(() => {
    const activeElement = document.activeElement
    if (!(activeElement instanceof HTMLElement) || !activeElement.closest('.mascot-field')) {
      mascotMode.value = 'idle'
    }
  }, 0)
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
.auth-page {
  display: grid;
  min-height: 100vh;
  grid-template-columns: minmax(0, 1.15fr) minmax(420px, .85fr);
  overflow: hidden;
  background: #fff8f1;
}

.visual-panel {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 56px;
  overflow: hidden;
  background:
    linear-gradient(135deg, rgba(255, 247, 237, .96), rgba(238, 242, 255, .94) 46%, rgba(250, 245, 255, .98)),
    radial-gradient(circle at 18% 18%, rgba(251, 191, 36, .18), transparent 32%),
    radial-gradient(circle at 82% 22%, rgba(124, 58, 237, .16), transparent 34%);
}

.visual-panel::before {
  content: '';
  position: absolute;
  inset: 28px;
  border: 1px solid rgba(255, 255, 255, .62);
  border-radius: 36px;
  pointer-events: none;
}

.glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(22px);
  opacity: .7;
  animation: glow-drift 9s ease-in-out infinite alternate;
}

.glow-lavender {
  width: 260px;
  height: 260px;
  left: 9%;
  top: 12%;
  background: rgba(167, 139, 250, .38);
}

.glow-sun {
  width: 220px;
  height: 220px;
  right: 10%;
  bottom: 14%;
  background: rgba(251, 146, 60, .28);
  animation-delay: -2s;
}

.hero-shell {
  position: relative;
  z-index: 1;
  width: min(520px, 100%);
  animation: panel-enter .7s ease both;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  height: 34px;
  padding: 0 16px;
  margin-bottom: 24px;
  border: 1px solid rgba(124, 58, 237, .18);
  border-radius: 999px;
  background: rgba(255, 255, 255, .58);
  color: #6d28d9;
  font-size: 13px;
  font-weight: 700;
  box-shadow: 0 12px 30px rgba(124, 58, 237, .1);
  backdrop-filter: blur(14px);
}

.illustration-stage {
  position: relative;
  width: min(420px, 88vw);
  aspect-ratio: 1.18;
  margin: 0 0 28px;
  display: grid;
  place-items: center;
}

.blob {
  position: absolute;
  border-radius: 45% 55% 58% 42% / 48% 44% 56% 52%;
  transform-origin: center;
}

.blob-main {
  width: 78%;
  height: 76%;
  background:
    radial-gradient(circle at 32% 28%, rgba(255, 255, 255, .9), transparent 18%),
    linear-gradient(135deg, #c4b5fd 0%, #f9a8d4 48%, #fbbf24 100%);
  box-shadow:
    0 40px 90px rgba(124, 58, 237, .25),
    inset 0 0 0 1px rgba(255, 255, 255, .5);
  animation: blob-breathe 6s ease-in-out infinite;
}

.blob-warm {
  width: 44%;
  height: 40%;
  right: 4%;
  bottom: 8%;
  background: linear-gradient(135deg, #fed7aa, #fde68a);
  opacity: .86;
  animation: blob-float 7s ease-in-out infinite;
}

.orb-card {
  position: absolute;
  display: grid;
  place-items: center;
  width: 62px;
  height: 62px;
  border-radius: 22px;
  background: rgba(255, 255, 255, .74);
  color: #4c1d95;
  font-family: var(--font-heading);
  font-size: 17px;
  font-weight: 800;
  box-shadow: 0 18px 40px rgba(88, 28, 135, .14);
  backdrop-filter: blur(14px);
  animation: soft-float 4.8s ease-in-out infinite;
}

.card-rag {
  left: 3%;
  top: 34%;
}

.card-ai {
  right: 4%;
  top: 18%;
  color: #c2410c;
  animation-delay: -1.4s;
}

.sparkle {
  position: absolute;
  width: 14px;
  height: 14px;
  border-radius: 5px;
  background: #facc15;
  transform: rotate(45deg);
  animation: sparkle-pulse 2.6s ease-in-out infinite;
}

.sparkle-one {
  left: 22%;
  top: 14%;
}

.sparkle-two {
  right: 18%;
  bottom: 22%;
  width: 10px;
  height: 10px;
  background: #a78bfa;
  animation-delay: -.8s;
}

.login-mascot {
  position: relative;
  z-index: 2;
  width: 238px;
  height: auto;
  overflow: visible;
  animation: mascot-float 4.8s ease-in-out infinite;
}

.mascot-ground {
  fill: rgba(76, 29, 149, .14);
  filter: blur(2px);
}

.mascot-buddy,
.mascot-antenna-line,
.mascot-ear,
.mascot-eye,
.eye-pupil,
.mascot-smile,
.mascot-paw {
  transform-box: fill-box;
  transform-origin: center;
}

.mascot-antenna-line {
  fill: none;
  stroke: #67e8f9;
  stroke-width: 5;
  stroke-linecap: round;
  transition: transform .3s ease;
}

.mascot-antenna-dot {
  fill: #fde68a;
  stroke: rgba(255, 255, 255, .85);
  stroke-width: 4;
}

.mascot-body {
  fill: url(#buddyBody);
  stroke: rgba(255, 255, 255, .86);
  stroke-width: 3;
}

.mascot-gloss {
  fill: url(#buddyGlow);
}

.mascot-ear rect {
  fill: url(#buddyEar);
  stroke: rgba(255, 255, 255, .8);
  stroke-width: 3;
}

.mascot-ear circle {
  fill: rgba(255, 255, 255, .34);
}

.ear-left {
  transform: rotate(-7deg);
}

.ear-right {
  transform: rotate(7deg);
}

.mascot-face {
  fill: rgba(255, 255, 255, .9);
}

.eye-white {
  fill: #fff;
  stroke: rgba(76, 29, 149, .1);
  stroke-width: 2;
}

.eye-pupil {
  fill: #20134a;
  transition: transform .26s ease;
}

.eye-highlight {
  fill: #fff;
  transition: transform .26s ease;
}

.mascot-eye {
  animation: blink 5.4s infinite;
}

.mascot-blush {
  fill: rgba(244, 114, 182, .42);
  transition: opacity .24s ease, transform .24s ease;
}

.mascot-smile {
  fill: none;
  stroke: #2e2550;
  stroke-width: 4;
  stroke-linecap: round;
  transition: transform .24s ease, stroke-width .24s ease;
}

.mascot-paw {
  transition: transform .34s cubic-bezier(.2, .9, .28, 1.18);
}

.mascot-paw rect {
  fill: url(#buddyPaw);
  stroke: rgba(255, 255, 255, .82);
  stroke-width: 3;
}

.mascot-paw path {
  fill: none;
  stroke: rgba(255, 255, 255, .7);
  stroke-width: 5;
  stroke-linecap: round;
}

.paw-left {
  transform: rotate(-11deg) translate(-8px, 4px);
}

.paw-right {
  transform: rotate(11deg) translate(8px, 4px);
}

.auth-page.mascot-peek .login-mascot .mascot-antenna-line,
.auth-page:has(.username-field:focus-within) .login-mascot .mascot-antenna-line {
  transform: rotate(8deg);
}

.auth-page.mascot-peek .login-mascot .eye-pupil,
.auth-page.mascot-peek .login-mascot .eye-highlight,
.auth-page:has(.username-field:focus-within) .login-mascot .eye-pupil,
.auth-page:has(.username-field:focus-within) .login-mascot .eye-highlight {
  transform: translateX(7px);
}

.auth-page.mascot-peek .login-mascot .paw-right,
.auth-page:has(.username-field:focus-within) .login-mascot .paw-right {
  transform: rotate(-8deg) translate(-20px, -17px);
}

.auth-page.mascot-peek .login-mascot .paw-left,
.auth-page:has(.username-field:focus-within) .login-mascot .paw-left {
  transform: rotate(-14deg) translate(-18px, 8px);
}

.auth-page.mascot-peek .login-mascot .mascot-smile,
.auth-page:has(.username-field:focus-within) .login-mascot .mascot-smile {
  transform: translateY(2px) scaleX(1.12);
}

.auth-page.mascot-cover .login-mascot .mascot-antenna-line,
.auth-page:has(.password-field:focus-within) .login-mascot .mascot-antenna-line {
  transform: rotate(-12deg);
}

.auth-page.mascot-cover .login-mascot .mascot-eye,
.auth-page:has(.password-field:focus-within) .login-mascot .mascot-eye {
  animation: none;
}

.auth-page.mascot-cover .login-mascot .eye-pupil,
.auth-page.mascot-cover .login-mascot .eye-highlight,
.auth-page:has(.password-field:focus-within) .login-mascot .eye-pupil,
.auth-page:has(.password-field:focus-within) .login-mascot .eye-highlight {
  transform: translateX(5px);
}

.auth-page.mascot-cover .login-mascot .paw-left,
.auth-page:has(.password-field:focus-within) .login-mascot .paw-left {
  transform: rotate(7deg) translate(30px, -48px);
}

.auth-page.mascot-cover .login-mascot .paw-right,
.auth-page:has(.password-field:focus-within) .login-mascot .paw-right {
  animation: shy-right-peek 3.2s ease-in-out infinite;
  transform: rotate(-7deg) translate(-30px, -48px);
}

.auth-page.mascot-cover .login-mascot .mascot-smile,
.auth-page:has(.password-field:focus-within) .login-mascot .mascot-smile {
  transform: translateY(8px) scaleX(.72);
  stroke-width: 3;
}

.auth-page.mascot-cover .login-mascot .mascot-blush,
.auth-page:has(.password-field:focus-within) .login-mascot .mascot-blush {
  opacity: .72;
  transform: translateY(3px);
}

.auth-page.mascot-cover .login-mascot .eye-right,
.auth-page:has(.password-field:focus-within) .login-mascot .eye-right {
  animation: shy-real-eye-peek 3.2s ease-in-out infinite;
}

.hero-copy h1 {
  max-width: 460px;
  margin: 0 0 16px;
  color: #2e1065;
  font-family: var(--font-heading);
  font-size: clamp(36px, 5vw, 58px);
  font-weight: 800;
  line-height: 1.04;
  letter-spacing: 0;
}

.hero-copy p {
  max-width: 430px;
  margin: 0 0 28px;
  color: #6b5b7f;
  font-size: 16px;
  line-height: 1.8;
}

.hero-features {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.feature-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 38px;
  padding: 0 14px;
  border: 1px solid rgba(124, 58, 237, .13);
  border-radius: 999px;
  background: rgba(255, 255, 255, .66);
  color: #4c1d95;
  font-size: 13px;
  font-weight: 700;
  box-shadow: 0 12px 30px rgba(124, 58, 237, .08);
  backdrop-filter: blur(12px);
}

.feature-item .el-icon {
  color: #f97316;
  font-size: 14px;
}

.form-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 56px;
  background:
    radial-gradient(circle at 82% 18%, rgba(253, 230, 138, .22), transparent 30%),
    linear-gradient(180deg, #fff 0%, #fffaf5 100%);
}

.auth-card {
  width: min(100%, 430px);
  padding: 38px;
  border: 1px solid rgba(226, 232, 240, .78);
  border-radius: 30px;
  background: rgba(255, 255, 255, .82);
  box-shadow: 0 28px 80px rgba(88, 28, 135, .12), 0 1px 0 rgba(255, 255, 255, .7) inset;
  backdrop-filter: blur(18px);
  animation: card-enter .72s .08s ease both;
}

.card-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 34px;
}

.brand-icon {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 14px;
  background: linear-gradient(135deg, #7c3aed, #f97316);
  color: #fff;
  font-family: var(--font-heading);
  font-size: 18px;
  font-weight: 800;
  box-shadow: 0 14px 28px rgba(124, 58, 237, .28);
}

.brand-name {
  color: #1f1633;
  font-family: var(--font-heading);
  font-size: 19px;
  font-weight: 800;
}

.card-title {
  margin: 0 0 8px;
  color: #1f1633;
  font-family: var(--font-heading);
  font-size: 30px;
  font-weight: 800;
  letter-spacing: 0;
}

.card-subtitle {
  margin: 0 0 30px;
  color: #7b6f8d;
  font-size: 14px;
}

.mascot-field {
  transition: transform .22s ease;
}

.mascot-field:focus-within {
  transform: translateY(-1px);
}

.auth-card :deep(.el-form-item) {
  margin-bottom: 18px;
}

.auth-card :deep(.el-input__wrapper) {
  min-height: 50px;
  border-radius: 16px;
  background: rgba(255, 255, 255, .92);
  box-shadow: 0 0 0 1px rgba(226, 232, 240, .95) inset, 0 10px 28px rgba(88, 28, 135, .05);
  transition: box-shadow .22s ease, transform .22s ease, background .22s ease;
}

.auth-card :deep(.el-input__wrapper.is-focus) {
  background: #fff;
  box-shadow: 0 0 0 2px rgba(124, 58, 237, .22) inset, 0 16px 34px rgba(124, 58, 237, .12);
  transform: translateY(-1px);
}

.auth-card :deep(.el-input__inner) {
  color: #241b35;
  font-weight: 600;
}

.auth-card :deep(.el-input__inner::placeholder) {
  color: #a59bb4;
  font-weight: 500;
}

.submit-btn {
  width: 100%;
  height: 50px;
  margin-top: 6px;
  border: 0;
  border-radius: 16px;
  background: linear-gradient(135deg, #7c3aed 0%, #f97316 100%) !important;
  color: #fff;
  font-size: 15px;
  font-weight: 800;
  box-shadow: 0 18px 34px rgba(124, 58, 237, .22);
  transition: transform .2s ease, box-shadow .2s ease, filter .2s ease;
}

.submit-btn:hover {
  filter: saturate(1.06) brightness(1.02);
  transform: translateY(-2px);
  box-shadow: 0 24px 46px rgba(124, 58, 237, .28);
}

.submit-btn:active {
  transform: translateY(0) scale(.99);
}

.card-footer {
  margin-top: 22px;
  color: #7b6f8d;
  font-size: 14px;
  text-align: center;
}

.card-footer a {
  color: #7c3aed;
  font-weight: 800;
  text-decoration: none;
  transition: color .2s ease;
}

.card-footer a:hover {
  color: #f97316;
}

@keyframes panel-enter {
  from { opacity: 0; transform: translateY(18px) scale(.98); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}

@keyframes card-enter {
  from { opacity: 0; transform: translateX(18px); }
  to { opacity: 1; transform: translateX(0); }
}

@keyframes glow-drift {
  from { transform: translate3d(-10px, 0, 0) scale(.96); }
  to { transform: translate3d(18px, 16px, 0) scale(1.06); }
}

@keyframes blob-breathe {
  0%, 100% { border-radius: 45% 55% 58% 42% / 48% 44% 56% 52%; transform: scale(1) rotate(0deg); }
  50% { border-radius: 58% 42% 44% 56% / 44% 56% 44% 56%; transform: scale(1.035) rotate(3deg); }
}

@keyframes blob-float {
  0%, 100% { transform: translateY(0) rotate(-6deg); }
  50% { transform: translateY(-16px) rotate(5deg); }
}

@keyframes soft-float {
  0%, 100% { transform: translateY(0) rotate(-4deg); }
  50% { transform: translateY(-12px) rotate(4deg); }
}

@keyframes sparkle-pulse {
  0%, 100% { opacity: .45; transform: rotate(45deg) scale(.82); }
  50% { opacity: 1; transform: rotate(45deg) scale(1.18); }
}

@keyframes mascot-float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-10px); }
}

@keyframes blink {
  0%, 91%, 100% { transform: scaleY(1); }
  94%, 96% { transform: scaleY(.12); }
}

@keyframes shy-right-peek {
  0%, 39%, 70%, 100% { transform: rotate(-7deg) translate(-30px, -48px); }
  48%, 58% { transform: rotate(-20deg) translate(28px, -42px); }
}

@keyframes shy-real-eye-peek {
  0%, 39%, 70%, 100% { transform: translateX(0) scaleY(.82); }
  48%, 58% { transform: translateX(7px) scaleY(1); }
}

@media (max-width: 980px) {
  .auth-page {
    grid-template-columns: 1fr;
    min-height: 100svh;
  }

  .visual-panel {
    min-height: auto;
    padding: 34px 24px 30px;
  }

  .visual-panel::before {
    inset: 14px;
    border-radius: 28px;
  }

  .hero-shell {
    width: min(100%, 640px);
  }

  .illustration-stage {
    width: min(340px, 82vw);
    margin-bottom: 18px;
  }

  .hero-copy h1 {
    font-size: clamp(34px, 8vw, 48px);
  }

  .hero-copy p {
    margin-bottom: 20px;
  }

  .form-panel {
    min-height: auto;
    padding: 26px 20px 38px;
  }

  .auth-card {
    padding: 30px 24px;
    border-radius: 24px;
  }
}

@media (max-width: 520px) {
  .visual-panel {
    padding: 28px 18px 24px;
  }

  .hero-badge {
    height: 30px;
    margin-bottom: 14px;
    font-size: 12px;
  }

  .illustration-stage {
    width: min(270px, 88vw);
  }

  .login-mascot {
    width: 138px;
    height: 129px;
  }

  .orb-card {
    width: 50px;
    height: 50px;
    border-radius: 18px;
    font-size: 14px;
  }

  .hero-features {
    gap: 8px;
  }

  .feature-item {
    height: 34px;
    padding: 0 12px;
    font-size: 12px;
  }

  .form-panel {
    padding: 22px 16px 34px;
  }

  .auth-card {
    padding: 26px 20px;
  }

  .card-brand {
    margin-bottom: 26px;
  }

  .card-title {
    font-size: 27px;
  }
}
</style>
