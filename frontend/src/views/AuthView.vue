<script setup>
import { ref, computed, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()

const role = computed(() => (route.params.role === 'student' ? 'student' : 'teacher'))
const roleLabel = computed(() => (role.value === 'student' ? '学生' : '教师'))
const authTitle = computed(() => (role.value === 'student' ? '学生学习中心' : '教师工作台'))
const accountPlaceholder = computed(() => (role.value === 'student' ? '用户名 / 学号' : '用户名 / 教师工号'))

const isRegister = ref(false)
const name = ref('')
const password = ref('')
const email = ref('')
const verifyCode = ref('')
const codeSending = ref(false)
const codeCountdown = ref(0)
const loading = ref(false)
const error = ref('')
const message = ref('')
let codeTimer = null

const showForgotPassword = ref(false)
const resetName = ref('')
const resetCode = ref('')
const resetPassword = ref('')
const resetCodeSending = ref(false)
const resetCodeCountdown = ref(0)
const resetLoading = ref(false)
const resetError = ref('')
const resetMessage = ref('')
let resetCodeTimer = null

const API_BASE = import.meta.env.VITE_API_BASE || '/api'
const EMAIL_REGEX = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/i

function getApiErrorMessage(payload, status) {
  const detail = String(payload?.detail || payload?.message || '').trim()
  if (detail) return detail
  const code = Number(status || 0)
  if (code === 401) return '登录状态已失效，请重新登录后再试'
  if (code === 403) return '当前操作暂不可用，请稍后再试'
  if (code === 404) return '你要找的内容暂时不可用'
  if (code === 409 || code === 422) return '当前内容暂时无法处理，请调整后再试'
  if (code >= 500) return '服务器开小差了，请稍后再试'
  return '请求暂时无法完成，请稍后再试'
}

function clearTips() {
  error.value = ''
  message.value = ''
}

function clearCodeTimer() {
  if (codeTimer) {
    clearInterval(codeTimer)
    codeTimer = null
  }
}

function startCodeCountdown(seconds = 60) {
  clearCodeTimer()
  codeCountdown.value = Number(seconds) > 0 ? Number(seconds) : 60
  codeTimer = window.setInterval(() => {
    if (codeCountdown.value <= 1) {
      clearCodeTimer()
      codeCountdown.value = 0
      return
    }
    codeCountdown.value -= 1
  }, 1000)
}

function isValidEmail(value) {
  return EMAIL_REGEX.test(String(value || '').trim())
}

async function sendRegisterCode() {
  clearTips()
  if (!isRegister.value || codeSending.value) return

  const trimmedName = name.value.trim()
  const trimmedEmail = email.value.trim().toLowerCase()
  if (!trimmedName) {
    error.value = '请先填写用户名'
    return
  }
  if (!trimmedEmail) {
    error.value = '请先填写邮箱'
    return
  }
  if (!isValidEmail(trimmedEmail)) {
    error.value = '邮箱格式不正确（示例：name@example.com）'
    return
  }

  codeSending.value = true
  try {
    const query = new URLSearchParams({
      name: trimmedName,
      email: trimmedEmail,
      role: role.value,
      scene: 'register',
    })
    const response = await fetch(`${API_BASE}/user/code?${query.toString()}`)
    const data = await response.json().catch(() => ({}))
    if (!response.ok) {
      throw new Error(getApiErrorMessage(data, response.status))
    }
    message.value = '验证码已发送，请到邮箱查收'
    startCodeCountdown(60)
  } catch (err) {
    error.value = `验证码发送失败：${err.message}`
  } finally {
    codeSending.value = false
  }
}

function clearResetTips() {
  resetError.value = ''
  resetMessage.value = ''
}

function clearResetCodeTimer() {
  if (resetCodeTimer) {
    clearInterval(resetCodeTimer)
    resetCodeTimer = null
  }
}

function startResetCodeCountdown(seconds = 60) {
  clearResetCodeTimer()
  resetCodeCountdown.value = Number(seconds) > 0 ? Number(seconds) : 60
  resetCodeTimer = window.setInterval(() => {
    if (resetCodeCountdown.value <= 1) {
      clearResetCodeTimer()
      resetCodeCountdown.value = 0
      return
    }
    resetCodeCountdown.value -= 1
  }, 1000)
}

async function sendResetCode() {
  clearResetTips()
  if (resetCodeSending.value || resetCodeCountdown.value > 0) return

  const trimmedName = resetName.value.trim()
  if (!trimmedName) {
    resetError.value = '请先填写用户名'
    return
  }

  resetCodeSending.value = true
  try {
    const query = new URLSearchParams({
      name: trimmedName,
      role: role.value,
      scene: 'reset_password',
    })
    const response = await fetch(`${API_BASE}/user/code?${query.toString()}`)
    const data = await response.json().catch(() => ({}))
    if (!response.ok) {
      throw new Error(getApiErrorMessage(data, response.status))
    }
    const maskedEmail = String(data?.masked_email || '').trim()
    resetMessage.value = maskedEmail
      ? `验证码已发送到注册邮箱：${maskedEmail}`
      : '验证码已发送到注册邮箱，请查收'
    startResetCodeCountdown(60)
  } catch (err) {
    resetError.value = `验证码发送失败：${err.message}`
  } finally {
    resetCodeSending.value = false
  }
}

async function submitAuth() {
  clearTips()
  if (loading.value) return

  const trimmedName = name.value.trim()
  const trimmedPassword = password.value.trim()
  const trimmedEmail = email.value.trim().toLowerCase()
  const trimmedCode = verifyCode.value.trim()

  if (!trimmedName || !trimmedPassword) {
    error.value = '请完整填写用户名和密码'
    return
  }
  if (isRegister.value) {
    if (!trimmedEmail) {
      error.value = '注册时请填写邮箱'
      return
    }
    if (!isValidEmail(trimmedEmail)) {
      error.value = '邮箱格式不正确（示例：name@example.com）'
      return
    }
    if (!trimmedCode) {
      error.value = '注册时请填写邮箱验证码'
      return
    }
  }

  loading.value = true
  try {
    const payload = {
      name: trimmedName,
      password: trimmedPassword,
    }
    if (isRegister.value) {
      payload.email = trimmedEmail
      payload.code = trimmedCode
    }

    const response = await fetch(`${API_BASE}/${role.value}/${isRegister.value ? 'register' : 'login'}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    })
    const data = await response.json().catch(() => ({}))
    if (!response.ok) {
      throw new Error(getApiErrorMessage(data, response.status))
    }

    if (isRegister.value) {
      message.value = '注册成功，请登录'
      isRegister.value = false
      verifyCode.value = ''
      codeCountdown.value = 0
      clearCodeTimer()
      return
    }

    const tokenKey = `${role.value}_token`
    const userKey = `${role.value}_user`
    if (data.token) {
      localStorage.setItem(tokenKey, data.token)
    }
    if (data.user) {
      localStorage.setItem(userKey, JSON.stringify(data.user))
    }
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.trim()
      ? route.query.redirect
      : role.value === 'student' ? '/student' : '/teacher'
    router.push(redirect)
  } catch (err) {
    error.value = `操作失败：${err.message}`
  } finally {
    loading.value = false
  }
}

async function submitResetPassword() {
  clearResetTips()
  if (resetLoading.value) return

  const trimmedName = resetName.value.trim()
  const trimmedCode = resetCode.value.trim()
  const trimmedPassword = resetPassword.value.trim()

  if (!trimmedName || !trimmedCode || !trimmedPassword) {
    resetError.value = '请完整填写用户名、验证码和新密码'
    return
  }
  if (trimmedPassword.length < 6) {
    resetError.value = '新密码至少 6 位'
    return
  }

  resetLoading.value = true
  try {
    const response = await fetch(`${API_BASE}/${role.value}/reset_password`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: trimmedName,
        code: trimmedCode,
        new_password: trimmedPassword,
      }),
    })
    const data = await response.json().catch(() => ({}))
    if (!response.ok) {
      throw new Error(getApiErrorMessage(data, response.status))
    }

    resetMessage.value = '密码已重置，请使用新密码登录'
    name.value = trimmedName
    password.value = trimmedPassword
    resetCode.value = ''
    resetCodeCountdown.value = 0
    clearResetCodeTimer()
    showForgotPassword.value = false
  } catch (err) {
    resetError.value = `重置失败：${err.message}`
  } finally {
    resetLoading.value = false
  }
}

function toggleForgotPassword() {
  clearTips()
  clearResetTips()
  showForgotPassword.value = !showForgotPassword.value
  if (!showForgotPassword.value) {
    resetCode.value = ''
    resetCodeCountdown.value = 0
    clearResetCodeTimer()
  }
}

function toggleMode() {
  clearTips()
  isRegister.value = !isRegister.value
  verifyCode.value = ''
  codeCountdown.value = 0
  clearCodeTimer()
  showForgotPassword.value = false
  resetCode.value = ''
  resetCodeCountdown.value = 0
  clearResetCodeTimer()
  clearResetTips()
}

onBeforeUnmount(() => {
  clearCodeTimer()
  clearResetCodeTimer()
})
</script>

<template>
  <div class="auth-shell min-h-screen">
    <div class="auth-bg"></div>
    <div class="auth-grid-glow"></div>
    <div class="auth-page">
      <header class="auth-nav">
        <div class="brand-lockup">
          <div class="brand-mark" aria-hidden="true"><span></span><span></span></div>
          <strong>教策云枢</strong>
        </div>
        <div class="auth-nav__links">
          <button type="button">帮助中心</button>
          <i></i>
          <button type="button">关于我们</button>
        </div>
      </header>

      <main class="auth-main">
        <section class="auth-hero">
          <h1>教策云枢 · <span>AI</span> 备课助手</h1>
          <h2>让备课更智能，让教学更高效</h2>
          <p class="hero-copy">
            教策云枢是一款基于人工智能的智能备课与教学资源生成平台，
            为教师提供一站式备课支持，助力教学质量提升与个性化教学实现。
          </p>

          <div class="ai-stage" aria-hidden="true">
            <div class="stage-orbit stage-orbit--wide"></div>
            <div class="stage-orbit"></div>
            <div class="stage-platform"></div>
            <div class="ai-console">
              <div class="console-sidebar">
                <span></span><span></span><span></span><span></span>
              </div>
              <div class="console-core">AI</div>
              <div class="console-panel">
                <span></span><span></span><span></span>
              </div>
            </div>
            <div class="float-card float-card--doc">
              <i></i><span>智能生成</span>
            </div>
            <div class="float-card float-card--chart">
              <i></i><span>学情分析</span>
            </div>
            <div class="float-card float-card--media">
              <i></i><span>多模课件</span>
            </div>
            <div class="float-card float-card--sync">
              <i></i><span>云端同步</span>
            </div>
          </div>

          <div class="hero-utility hero-utility--compact">
            <article>
              <span class="utility-icon utility-icon--robot"></span>
              <div>
                <strong>智能备课</strong>
                <small>AI 生成教学方案</small>
              </div>
            </article>
            <article>
              <span class="utility-icon utility-icon--cube"></span>
              <div>
                <strong>资源丰富</strong>
                <small>多类型教材资源库</small>
              </div>
            </article>
            <article>
              <span class="utility-icon utility-icon--analysis"></span>
              <div>
                <strong>学情分析</strong>
                <small>精准教学分析</small>
              </div>
            </article>
            <article>
              <span class="utility-icon utility-icon--cloud"></span>
              <div>
                <strong>云端同步</strong>
                <small>多端实时同步</small>
              </div>
            </article>
          </div>
        </section>

        <section class="auth-card">
          <div class="auth-dot-grid" aria-hidden="true"></div>
          <div class="auth-card__head">
            <h2>{{ roleLabel }}登录</h2>
            <span>欢迎回来，请登录您的账号</span>
          </div>

          <div class="auth-panel">
            <div class="auth-fields">
              <label class="input-wrap">
                <span class="input-icon input-icon--user"></span>
                <input v-model="name" class="auth-input" :placeholder="accountPlaceholder" />
              </label>
              <label class="input-wrap">
                <span class="input-icon input-icon--lock"></span>
                <input v-model="password" class="auth-input" type="password" placeholder="密码" />
              </label>

              <div v-if="isRegister" class="register-fields">
                <label class="input-wrap">
                  <span class="input-icon">@</span>
                  <input v-model="email" class="auth-input" placeholder="邮箱（如：name@example.com）" />
                </label>
                <div class="verify-row">
                  <label class="input-wrap">
                    <span class="input-icon">#</span>
                    <input v-model="verifyCode" class="auth-input" placeholder="邮箱验证码" />
                  </label>
                  <button
                    class="ghost-action"
                    type="button"
                    :disabled="codeSending || codeCountdown > 0"
                    @click="sendRegisterCode"
                  >
                    {{ codeCountdown > 0 ? `${codeCountdown}s后重试` : (codeSending ? '发送中...' : '发送验证码') }}
                  </button>
                </div>
                <p class="field-tip">注册需先完成邮箱验证码校验</p>
              </div>

              <div v-if="!isRegister" class="login-options">
                <label class="remember-row">
                  <span></span>
                  记住我
                </label>
                <button class="text-link" type="button" @click="toggleForgotPassword">
                  {{ showForgotPassword ? '收起' : '忘记密码？' }}
                </button>
              </div>

              <p v-if="error" class="error-text">{{ error }}</p>
              <p v-if="message" class="ok-text">{{ message }}</p>

              <button class="submit-btn" type="button" @click="submitAuth" :disabled="loading">
                {{ loading ? '处理中...' : (isRegister ? '完成注册' : '登录进入') }}
              </button>

              <div v-if="!isRegister" class="other-login">
                <span></span>
                <small>其他登录方式</small>
                <span></span>
                <button type="button" title="微信登录" class="wechat-btn"></button>
              </div>

              <div v-if="!isRegister && showForgotPassword" class="forgot-panel">
                <p>通过注册邮箱验证码重置密码</p>
                <label class="input-wrap">
                  <span class="input-icon input-icon--user"></span>
                  <input v-model="resetName" class="auth-input" placeholder="用户名" />
                </label>
                <div class="verify-row">
                  <label class="input-wrap">
                    <span class="input-icon">#</span>
                    <input v-model="resetCode" class="auth-input" placeholder="邮箱验证码" />
                  </label>
                  <button
                    class="ghost-action"
                    type="button"
                    :disabled="resetCodeSending || resetCodeCountdown > 0"
                    @click="sendResetCode"
                  >
                    {{ resetCodeCountdown > 0 ? `${resetCodeCountdown}s后重试` : (resetCodeSending ? '发送中...' : '发送验证码') }}
                  </button>
                </div>
                <label class="input-wrap">
                  <span class="input-icon">▣</span>
                  <input v-model="resetPassword" class="auth-input" type="password" placeholder="新密码" />
                </label>
                <p v-if="resetError" class="error-text">{{ resetError }}</p>
                <p v-if="resetMessage" class="ok-text">{{ resetMessage }}</p>
                <button class="submit-btn" type="button" @click="submitResetPassword" :disabled="resetLoading">
                  {{ resetLoading ? '处理中...' : '确认重置密码' }}
                </button>
              </div>

              <p class="signup-tip">
                {{ isRegister ? '已有账号？' : '还没有账号？' }}
                <button class="text-link" type="button" @click="toggleMode">
                  {{ isRegister ? '立即登录' : '立即注册' }}
                </button>
              </p>
            </div>
          </div>
        </section>
      </main>

      <footer class="auth-footer">
        <section class="core-features">
          <h3>核心功能</h3>
          <article>
            <span class="core-icon core-icon--ai"></span>
            <div><strong>AI 智能备课</strong><small>输入教学需求，AI 自动生成教案、教学目标与课堂活动设计。</small></div>
          </article>
          <article>
            <span class="core-icon core-icon--doc"></span>
            <div><strong>教案与课件生成</strong><small>一键生成结构化教案与精美课件，支持多种模板与风格。</small></div>
          </article>
          <article>
            <span class="core-icon core-icon--folder"></span>
            <div><strong>教材资源库</strong><small>海量优质教材与教学资源，覆盖多学段多学科。</small></div>
          </article>
          <article>
            <span class="core-icon core-icon--bars"></span>
            <div><strong>学情分析与评价</strong><small>生成学情分析报告与评价建议，助力精准教学与个性化指导。</small></div>
          </article>
        </section>
        <p>教策云枢 · AI 备课助手 © 2024 教策科技 版权所有</p>
      </footer>
    </div>
  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Noto+Sans+SC:wght@400;500;600;700;800&display=swap');

.auth-shell {
  font-family: 'Noto Sans SC', sans-serif;
  color: #e2e8f0;
  position: relative;
  background: #02081f;
  overflow: hidden;
}

.auth-shell::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 14% 18%, rgba(30, 100, 255, 0.25), transparent 36%),
    radial-gradient(circle at 72% 28%, rgba(165, 51, 255, 0.27), transparent 42%),
    radial-gradient(circle at 86% 50%, rgba(235, 77, 196, 0.16), transparent 38%),
    linear-gradient(118deg, #06194a 0%, #040b2b 46%, #18072f 100%);
  pointer-events: none;
}

.auth-bg {
  position: absolute;
  inset: 0;
  background-image:
    radial-gradient(1px 1px at 8% 20%, rgba(191, 219, 254, 0.7), transparent 55%),
    radial-gradient(1px 1px at 27% 9%, rgba(255, 255, 255, 0.55), transparent 55%),
    radial-gradient(1px 1px at 42% 18%, rgba(125, 211, 252, 0.55), transparent 55%),
    radial-gradient(1px 1px at 68% 8%, rgba(255, 255, 255, 0.55), transparent 55%),
    radial-gradient(2px 2px at 73% 16%, rgba(167, 139, 250, 0.65), transparent 55%),
    radial-gradient(1px 1px at 94% 12%, rgba(255, 255, 255, 0.45), transparent 55%),
    radial-gradient(1px 1px at 87% 72%, rgba(125, 211, 252, 0.6), transparent 55%);
  opacity: 0.75;
  pointer-events: none;
}

.orbital-line {
  position: absolute;
  width: 60vw;
  height: 20vw;
  right: -12vw;
  bottom: 12vh;
  border: 1px solid rgba(83, 168, 255, 0.15);
  border-radius: 50%;
  transform: rotate(-16deg);
  pointer-events: none;
}

.orbital-line--two {
  right: -8vw;
  bottom: 18vh;
  width: 54vw;
  border-color: rgba(192, 132, 252, 0.13);
}

.auth-page {
  position: relative;
  z-index: 1;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  padding: clamp(24px, 3vw, 46px) clamp(22px, 5vw, 88px);
}

.auth-nav,
.auth-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}

.brand-lockup {
  display: inline-flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.brand-lockup strong {
  font-size: clamp(18px, 1.4vw, 24px);
  color: #f8fafc;
  text-shadow: 0 12px 28px rgba(59, 130, 246, 0.35);
}

.brand-lockup em {
  font-style: normal;
  border: 1px solid rgba(147, 197, 253, 0.55);
  border-radius: 999px;
  color: #bfdbfe;
  font-size: 13px;
  padding: 1px 8px;
  background: rgba(15, 23, 42, 0.45);
}

.brand-mark {
  position: relative;
  width: 44px;
  height: 44px;
}

.brand-mark span {
  position: absolute;
  width: 24px;
  height: 34px;
  border-radius: 8px;
  background: linear-gradient(145deg, rgba(103, 232, 249, 0.95), rgba(99, 102, 241, 0.9));
  box-shadow: 0 14px 30px -12px rgba(56, 189, 248, 0.8);
  transform: skewY(-28deg);
}

.brand-mark span:first-child {
  left: 5px;
  top: 3px;
}

.brand-mark span:last-child {
  right: 3px;
  bottom: 2px;
  background: linear-gradient(145deg, rgba(129, 140, 248, 0.95), rgba(217, 70, 239, 0.85));
}

.auth-nav__hint {
  color: rgba(226, 232, 240, 0.72);
  font-size: 14px;
}

.auth-main {
  flex: 1;
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(360px, 0.72fr);
  align-items: center;
  gap: clamp(34px, 5vw, 86px);
  padding: clamp(40px, 7vh, 90px) 0 34px;
}

.auth-hero {
  position: relative;
  min-width: 0;
}

.hero-pill {
  display: inline-flex;
  border-radius: 999px;
  border: 1px solid rgba(148, 163, 184, 0.32);
  background: rgba(15, 23, 42, 0.38);
  padding: 10px 24px;
  color: rgba(226, 232, 240, 0.85);
  font-size: 16px;
  backdrop-filter: blur(16px);
}

.auth-hero h1 {
  margin-top: 24px;
  color: #f8fafc;
  font-size: clamp(42px, 5vw, 68px);
  line-height: 1.1;
  font-weight: 800;
  letter-spacing: 0;
}

.auth-hero h1 span {
  margin-left: 0.35em;
  background: linear-gradient(100deg, #31d7ff, #6c8cff 48%, #d85cf4);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.hero-copy {
  margin-top: 22px;
  max-width: 650px;
  color: rgba(226, 232, 240, 0.82);
  font-size: 17px;
  line-height: 1.9;
}

.product-orbit {
  position: absolute;
  right: -1vw;
  top: 50px;
  width: 300px;
  height: 220px;
  pointer-events: none;
}

.orbit-ring {
  position: absolute;
  left: 42px;
  bottom: 20px;
  width: 210px;
  height: 84px;
  border: 2px solid rgba(56, 189, 248, 0.55);
  border-radius: 50%;
  box-shadow: 0 0 26px rgba(56, 189, 248, 0.3);
  transform: rotate(-7deg);
}

.orbit-ring--wide {
  left: 20px;
  bottom: 9px;
  width: 260px;
  height: 104px;
  border-color: rgba(168, 85, 247, 0.42);
  box-shadow: none;
}

.orbit-card {
  position: absolute;
  top: 40px;
  width: 70px;
  height: 112px;
  border-radius: 18px;
  transform: skewY(-28deg);
  border: 1px solid rgba(255, 255, 255, 0.42);
  box-shadow: 0 28px 48px -24px rgba(56, 189, 248, 0.8), inset 0 1px 16px rgba(255, 255, 255, 0.28);
}

.orbit-card--left {
  left: 102px;
  background: linear-gradient(145deg, rgba(125, 211, 252, 0.95), rgba(99, 102, 241, 0.78));
}

.orbit-card--right {
  left: 166px;
  top: 70px;
  height: 90px;
  background: linear-gradient(145deg, rgba(147, 197, 253, 0.95), rgba(217, 70, 239, 0.78));
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-top: 56px;
}

.feature-card,
.hero-utility article,
.auth-card,
.auth-panel {
  border: 1px solid rgba(148, 163, 184, 0.26);
  background: linear-gradient(145deg, rgba(30, 58, 138, 0.32), rgba(15, 23, 42, 0.42));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.08), 0 22px 50px -34px rgba(30, 64, 175, 0.8);
  backdrop-filter: blur(20px);
}

.feature-card {
  min-height: 172px;
  border-radius: 16px;
  padding: 22px 16px 18px;
  text-align: center;
}

.feature-card strong {
  display: block;
  margin-top: 13px;
  color: #f8fafc;
  font-size: 17px;
  font-weight: 700;
}

.feature-card small {
  display: block;
  margin-top: 12px;
  color: rgba(203, 213, 225, 0.84);
  font-size: 14px;
  line-height: 1.65;
}

.feature-icon {
  display: inline-grid;
  place-items: center;
  width: 54px;
  height: 54px;
  border-radius: 13px;
  color: white;
  font-weight: 800;
  font-size: 26px;
}

.feature-icon--blue {
  background: linear-gradient(145deg, #61caff, #3266ff);
}

.feature-icon--purple {
  background: linear-gradient(145deg, #c084fc, #6d5dfc);
}

.feature-icon--green {
  background: linear-gradient(145deg, #86efac, #22c55e);
}

.feature-icon--orange {
  background: linear-gradient(145deg, #fbbf24, #fb7185);
}

.hero-utility {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0;
  margin-top: 24px;
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid rgba(148, 163, 184, 0.26);
  background: rgba(15, 23, 42, 0.28);
}

.hero-utility article {
  display: grid;
  grid-template-columns: 82px minmax(0, 1fr);
  align-items: center;
  gap: 22px;
  border: 0;
  border-radius: 0;
  padding: 24px 28px;
}

.hero-utility article + article {
  border-left: 1px solid rgba(148, 163, 184, 0.26);
}

.hero-utility strong {
  display: block;
  color: #f8fafc;
  font-size: 17px;
  margin-bottom: 6px;
}

.hero-utility small {
  color: rgba(203, 213, 225, 0.78);
  font-size: 14px;
  line-height: 1.65;
}

.utility-icon {
  display: grid;
  place-items: center;
  width: 64px;
  height: 54px;
  border-radius: 24px;
  color: white;
  font-size: 34px;
  font-weight: 600;
}

.utility-icon--upload {
  background: linear-gradient(145deg, #60a5fa, #4f46e5);
}

.utility-icon--history {
  color: #a78bfa;
  border: 5px solid #8b5cf6;
  background: transparent;
}

.auth-card {
  border-radius: 24px;
  padding: 36px 30px;
  background: linear-gradient(145deg, rgba(30, 41, 91, 0.72), rgba(47, 22, 74, 0.62));
}

.auth-card__head {
  text-align: center;
  margin-bottom: 28px;
}

.auth-card__head p {
  color: #67e8f9;
  letter-spacing: 0.45em;
  font-size: 12px;
  font-weight: 700;
}

.auth-card__head h2 {
  margin-top: 14px;
  color: #f8fafc;
  font-size: clamp(36px, 3vw, 48px);
  font-weight: 800;
}

.auth-card__head span {
  display: block;
  margin-top: 13px;
  color: rgba(226, 232, 240, 0.82);
  font-size: 15px;
}

.auth-panel {
  border-radius: 22px;
  padding: 24px;
  background: rgba(15, 23, 42, 0.42);
}

.auth-panel__title {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 22px;
}

.auth-panel__title small {
  display: block;
  color: rgba(203, 213, 225, 0.78);
  font-size: 14px;
  margin-bottom: 7px;
}

.auth-panel__title strong {
  color: #f8fafc;
  font-size: 22px;
  font-weight: 800;
}

.text-link,
.forgot-link {
  color: #38d9ff;
  font-size: 14px;
  font-weight: 700;
}

.auth-fields,
.register-fields,
.forgot-panel {
  display: grid;
  gap: 14px;
}

.input-wrap {
  position: relative;
  display: block;
}

.input-icon {
  position: absolute;
  left: 18px;
  top: 50%;
  transform: translateY(-50%);
  color: rgba(226, 232, 240, 0.82);
  font-size: 18px;
  pointer-events: none;
}

.auth-input {
  width: 100%;
  height: 56px;
  border-radius: 15px;
  border: 1px solid rgba(148, 163, 184, 0.32);
  background: rgba(2, 8, 31, 0.58);
  padding: 0 18px 0 58px;
  font-size: 15px;
  color: #f8fafc;
  outline: none;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.05);
}

.auth-input:focus {
  border-color: rgba(56, 189, 248, 0.8);
  box-shadow: 0 0 0 3px rgba(56, 189, 248, 0.14), inset 0 1px 0 rgba(255, 255, 255, 0.08);
}

.auth-input::placeholder {
  color: rgba(148, 163, 184, 0.82);
}

.verify-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
}

.ghost-action {
  min-width: 118px;
  border-radius: 15px;
  border: 1px solid rgba(56, 189, 248, 0.42);
  background: rgba(56, 189, 248, 0.1);
  color: #7dd3fc;
  font-size: 13px;
  font-weight: 700;
  padding: 0 15px;
}

.ghost-action:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.field-tip,
.forgot-panel p {
  color: rgba(148, 163, 184, 0.9);
  font-size: 12px;
}

.error-text,
.ok-text {
  font-size: 13px;
}

.error-text {
  color: #fda4af;
}

.ok-text {
  color: #86efac;
}

.submit-btn {
  width: 100%;
  border-radius: 999px;
  min-height: 58px;
  padding: 14px 18px;
  background: linear-gradient(100deg, #2ed6ff 0%, #688bff 54%, #e257d3 100%);
  color: white;
  font-weight: 800;
  font-size: 16px;
  box-shadow: 0 18px 36px -18px rgba(96, 165, 250, 0.8);
  transition: transform 0.2s ease, opacity 0.2s ease, box-shadow 0.2s ease;
}

.submit-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 24px 44px -22px rgba(96, 165, 250, 0.95);
}

.submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

.forgot-link {
  justify-self: start;
  margin-top: 2px;
}

.forgot-panel {
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 16px;
  background: rgba(2, 6, 23, 0.28);
  padding: 14px;
}

.auth-footer {
  justify-content: center;
  gap: clamp(18px, 5vw, 72px);
  color: rgba(148, 163, 184, 0.78);
  font-size: 14px;
  letter-spacing: 0.08em;
}

.auth-footer i {
  width: 1px;
  height: 18px;
  background: rgba(148, 163, 184, 0.32);
}

button {
  transition: border-color 0.2s ease, background 0.2s ease, color 0.2s ease, transform 0.2s ease;
}

button:not(:disabled):hover {
  border-color: rgba(125, 211, 252, 0.72);
}

@media (max-width: 1180px) {
  .auth-main {
    grid-template-columns: 1fr;
  }

  .product-orbit {
    position: relative;
    right: auto;
    top: auto;
    margin: 18px auto -22px;
  }

  .auth-card {
    max-width: 620px;
    width: 100%;
    margin: 0 auto;
  }
}

@media (max-width: 760px) {
  .auth-page {
    padding: 18px;
  }

  .auth-nav {
    align-items: flex-start;
    gap: 16px;
  }

  .auth-nav__hint {
    display: none;
  }

  .brand-lockup strong {
    font-size: 17px;
  }

  .auth-main {
    padding-top: 34px;
  }

  .auth-hero h1 {
    font-size: 38px;
  }

  .auth-hero h1 span {
    display: block;
    margin-left: 0;
    margin-top: 8px;
  }

  .hero-copy {
    font-size: 15px;
  }

  .feature-grid,
  .hero-utility {
    grid-template-columns: 1fr;
  }

  .hero-utility article + article {
    border-left: 0;
    border-top: 1px solid rgba(148, 163, 184, 0.26);
  }

  .auth-card,
  .auth-panel {
    padding: 22px 16px;
  }

  .verify-row {
    grid-template-columns: 1fr;
  }

  .ghost-action {
    min-height: 44px;
  }

  .auth-footer {
    flex-wrap: wrap;
    gap: 12px;
    font-size: 12px;
  }
}

/* High-fidelity reference skin */
.auth-shell {
  background: #020618;
}

.auth-shell::before {
  background:
    radial-gradient(circle at 13% 38%, rgba(22, 101, 255, 0.28), transparent 34%),
    radial-gradient(circle at 78% 22%, rgba(106, 35, 255, 0.28), transparent 40%),
    radial-gradient(circle at 96% 52%, rgba(191, 36, 255, 0.18), transparent 34%),
    linear-gradient(115deg, #041337 0%, #030722 46%, #140326 100%);
}

.auth-bg {
  background-image:
    radial-gradient(1px 1px at 12% 22%, rgba(59, 130, 246, 0.95), transparent 55%),
    radial-gradient(1px 1px at 15% 8%, rgba(255, 255, 255, 0.55), transparent 55%),
    radial-gradient(1px 1px at 43% 30%, rgba(125, 211, 252, 0.65), transparent 55%),
    radial-gradient(2px 2px at 51% 46%, rgba(63, 135, 255, 0.9), transparent 55%),
    radial-gradient(1px 1px at 72% 17%, rgba(255, 255, 255, 0.58), transparent 55%),
    radial-gradient(1px 1px at 91% 6%, rgba(147, 197, 253, 0.58), transparent 55%);
}

.auth-bg::before {
  content: '';
  position: absolute;
  left: -10%;
  right: -10%;
  top: 34%;
  height: 34%;
  opacity: 0.34;
  background:
    repeating-linear-gradient(164deg, rgba(45, 126, 255, 0.2) 0 1px, transparent 1px 22px),
    repeating-linear-gradient(12deg, rgba(139, 92, 246, 0.16) 0 1px, transparent 1px 24px);
  clip-path: polygon(0 64%, 16% 45%, 31% 52%, 44% 68%, 58% 52%, 75% 35%, 100% 22%, 100% 67%, 78% 84%, 58% 76%, 40% 64%, 24% 73%, 0 88%);
  animation: authWave 16s ease-in-out infinite;
}

.auth-grid-glow {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.auth-grid-glow::before {
  content: '';
  position: absolute;
  left: 10vw;
  top: 38vh;
  width: 42vw;
  height: 18vw;
  border: 1px solid rgba(30, 144, 255, 0.3);
  border-radius: 50%;
  transform: rotate(-8deg);
  box-shadow: 0 0 45px rgba(37, 99, 235, 0.16);
}

.auth-page {
  max-width: 1740px;
  margin: 0 auto;
  padding: 34px 72px 28px;
}

.auth-nav__links {
  display: inline-flex;
  align-items: center;
  gap: 24px;
  color: rgba(226, 232, 240, 0.86);
  font-size: 16px;
}

.auth-nav__links button {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: inherit;
}

.auth-nav__links button::before {
  content: '?';
  display: inline-grid;
  place-items: center;
  width: 18px;
  height: 18px;
  border-radius: 999px;
  border: 1px solid rgba(226, 232, 240, 0.8);
  font-size: 12px;
  line-height: 1;
}

.auth-nav__links button + button::before {
  content: 'i';
}

.auth-nav__links i {
  width: 1px;
  height: 19px;
  background: rgba(148, 163, 184, 0.24);
}

.brand-mark {
  width: 44px;
  height: 42px;
}

.brand-mark span {
  width: 16px;
  height: 34px;
  border-radius: 5px;
  transform: none;
}

.brand-mark span:first-child {
  left: 2px;
  top: 5px;
  background: linear-gradient(180deg, #25e6b2, #32a6ff);
}

.brand-mark span:first-child::after,
.brand-mark span:last-child::after {
  content: '';
  position: absolute;
  width: 16px;
  height: 34px;
  border-radius: 5px;
}

.brand-mark span:first-child::after {
  left: 11px;
  top: -4px;
  background: linear-gradient(180deg, #56c8ff, #7765ff);
}

.brand-mark span:last-child {
  right: 5px;
  bottom: 5px;
  background: linear-gradient(180deg, #ffc65a, #c65dff);
}

.brand-lockup strong {
  font-size: 22px;
}

.auth-main {
  grid-template-columns: minmax(680px, 1fr) minmax(500px, 640px);
  align-items: start;
  gap: 72px;
  padding: 24px 0 16px;
}

.auth-hero h1 {
  margin: 0;
  max-width: 920px;
  font-size: clamp(50px, 3.8vw, 64px);
  line-height: 1.15;
  letter-spacing: 0;
  white-space: nowrap;
}

.auth-hero h1 span {
  color: transparent;
  background: linear-gradient(100deg, #34d5ff, #876cff);
  -webkit-background-clip: text;
  background-clip: text;
}

.auth-hero h2 {
  margin-top: 14px;
  font-size: clamp(25px, 2.2vw, 36px);
  font-weight: 800;
  color: rgba(226, 232, 240, 0.95);
}

.hero-copy {
  max-width: 720px;
  margin-top: 24px;
  font-size: 18px;
  line-height: 1.92;
  color: rgba(203, 213, 225, 0.86);
}

.ai-stage {
  position: relative;
  width: min(790px, 100%);
  height: 286px;
  margin: 10px 0 0 42px;
  transform: scale(0.94);
  transform-origin: left bottom;
}

.stage-orbit {
  position: absolute;
  left: 42px;
  right: 42px;
  bottom: 50px;
  height: 128px;
  border-radius: 50%;
  border: 1px solid rgba(56, 189, 248, 0.55);
  transform: rotate(-7deg);
  box-shadow: 0 0 42px rgba(59, 130, 246, 0.25);
}

.stage-orbit--wide {
  left: 0;
  right: 0;
  bottom: 26px;
  height: 182px;
  border-color: rgba(99, 102, 241, 0.48);
}

.stage-platform {
  position: absolute;
  left: 150px;
  right: 170px;
  bottom: 30px;
  height: 52px;
  border-radius: 50%;
  background: radial-gradient(ellipse, rgba(22, 211, 255, 0.68) 0%, rgba(37, 99, 235, 0.26) 42%, transparent 70%);
  filter: blur(1px);
  box-shadow: 0 0 38px rgba(0, 183, 255, 0.65);
}

.ai-console {
  position: absolute;
  left: 174px;
  bottom: 70px;
  width: 420px;
  height: 190px;
  border-radius: 18px;
  border: 1px solid rgba(76, 145, 255, 0.44);
  background:
    linear-gradient(145deg, rgba(15, 34, 91, 0.88), rgba(7, 15, 48, 0.8)),
    repeating-linear-gradient(90deg, rgba(125, 211, 252, 0.06) 0 1px, transparent 1px 68px);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.12), 0 28px 70px -40px rgba(37, 99, 235, 0.95);
  backdrop-filter: blur(14px);
}

.console-sidebar {
  position: absolute;
  left: 18px;
  top: 28px;
  width: 72px;
  display: grid;
  gap: 12px;
}

.console-sidebar span,
.console-panel span {
  display: block;
  height: 13px;
  border-radius: 999px;
  background: linear-gradient(90deg, rgba(56, 189, 248, 0.75), rgba(129, 140, 248, 0.25));
}

.console-core {
  position: absolute;
  left: 140px;
  top: 42px;
  width: 132px;
  height: 132px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  border: 8px solid rgba(30, 144, 255, 0.32);
  color: #e0f2fe;
  font-size: 48px;
  font-weight: 800;
  background: radial-gradient(circle, rgba(18, 72, 176, 0.9), rgba(8, 20, 62, 0.88));
  box-shadow: 0 0 28px rgba(56, 189, 248, 0.6), inset 0 0 24px rgba(56, 189, 248, 0.24);
}

.console-panel {
  position: absolute;
  right: 24px;
  top: 42px;
  width: 92px;
  display: grid;
  gap: 16px;
}

.float-card {
  position: absolute;
  width: 122px;
  height: 100px;
  border-radius: 14px;
  border: 1px solid rgba(76, 145, 255, 0.5);
  background: linear-gradient(145deg, rgba(25, 62, 147, 0.9), rgba(43, 28, 115, 0.78));
  box-shadow: 0 18px 44px -24px rgba(37, 99, 235, 0.95);
  display: grid;
  place-items: center;
  color: #dbeafe;
  font-size: 13px;
  font-weight: 800;
}

.float-card i {
  width: 46px;
  height: 46px;
  border-radius: 12px;
  background: linear-gradient(145deg, #dbeafe, #4b80ff);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.35);
}

.float-card--doc { left: 70px; bottom: 100px; }
.float-card--chart { right: 92px; top: 18px; }
.float-card--media { right: 70px; bottom: 92px; }
.float-card--sync { right: 140px; bottom: 0; width: 104px; height: 82px; }

.float-card--chart i {
  background: linear-gradient(145deg, #32d7ff, #7c3aed);
}

.float-card--media i {
  border-radius: 50%;
  background: linear-gradient(145deg, #f8fafc, #7c3aed);
}

.float-card--sync i {
  background: linear-gradient(145deg, #8b5cf6, #35d5ff);
}

.hero-utility--compact {
  width: min(820px, 100%);
  margin-top: 8px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border-radius: 16px;
  background: linear-gradient(145deg, rgba(15, 35, 83, 0.72), rgba(7, 14, 45, 0.58));
}

.hero-utility--compact article {
  grid-template-columns: 44px minmax(0, 1fr);
  gap: 14px;
  padding: 20px 22px;
}

.hero-utility--compact article + article {
  border-left: 1px solid rgba(148, 163, 184, 0.17);
}

.hero-utility--compact strong {
  font-size: 16px;
}

.utility-icon {
  width: 38px;
  height: 38px;
  border-radius: 11px;
}

.utility-icon::before {
  content: '';
  display: block;
  width: 22px;
  height: 22px;
  border-radius: 6px;
  background: linear-gradient(145deg, #5eead4, #60a5fa);
}

.utility-icon--robot::before {
  border-radius: 9px;
  box-shadow: 0 -8px 0 -5px #dbeafe;
}

.utility-icon--cube::before {
  background: linear-gradient(145deg, #8b5cf6, #22d3ee);
}

.utility-icon--analysis::before {
  background: linear-gradient(145deg, #38bdf8, #2563eb);
}

.utility-icon--cloud::before {
  border-radius: 999px;
  background: linear-gradient(145deg, #c084fc, #38bdf8);
}

.auth-card {
  position: relative;
  min-height: 572px;
  border-radius: 24px;
  padding: 46px 54px 28px;
  background: linear-gradient(150deg, rgba(10, 20, 57, 0.82), rgba(10, 7, 35, 0.72));
  border-color: rgba(148, 163, 184, 0.28);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.1), 0 34px 90px -54px rgba(37, 99, 235, 0.82);
}

.auth-card::after {
  content: '';
  position: absolute;
  right: 0;
  top: 0;
  width: 180px;
  height: 180px;
  border-top-right-radius: 24px;
  background: radial-gradient(circle at 80% 20%, rgba(99, 102, 241, 0.72), transparent 52%);
  pointer-events: none;
}

.auth-dot-grid {
  position: absolute;
  right: 70px;
  top: 58px;
  width: 76px;
  height: 76px;
  background-image: radial-gradient(circle, rgba(67, 113, 255, 0.85) 0 3px, transparent 4px);
  background-size: 18px 18px;
  opacity: 0.76;
}

.auth-card__head {
  text-align: left;
  margin-bottom: 28px;
}

.auth-card__head h2 {
  margin: 0;
  font-size: 44px;
  line-height: 1.1;
  color: transparent;
  background: linear-gradient(100deg, #45dfff, #d65cff);
  -webkit-background-clip: text;
  background-clip: text;
}

.auth-card__head span {
  margin-top: 18px;
  color: rgba(226, 232, 240, 0.84);
  font-size: 18px;
}

.auth-panel {
  border: 0;
  padding: 0;
  background: transparent;
  box-shadow: none;
  backdrop-filter: none;
}

.auth-fields {
  gap: 14px;
}

.auth-input {
  height: 54px;
  border-radius: 12px;
  border-color: rgba(148, 163, 184, 0.25);
  background: rgba(4, 9, 32, 0.54);
  color: #f8fafc;
  font-size: 17px;
}

.input-icon {
  width: 21px;
  height: 21px;
  font-size: 0;
  border: 1.8px solid rgba(226, 232, 240, 0.82);
  border-radius: 50%;
}

.input-icon--lock {
  border-radius: 5px;
}

.input-icon--lock::before {
  content: '';
  position: absolute;
  left: 4px;
  top: -9px;
  width: 12px;
  height: 10px;
  border: 1.8px solid rgba(226, 232, 240, 0.82);
  border-bottom: 0;
  border-radius: 8px 8px 0 0;
}

.login-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 2px;
}

.remember-row {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: rgba(226, 232, 240, 0.88);
  font-size: 15px;
}

.remember-row span {
  width: 20px;
  height: 20px;
  border-radius: 5px;
  border: 1px solid rgba(226, 232, 240, 0.7);
}

.submit-btn {
  min-height: 56px;
  border-radius: 9px;
  margin-top: 4px;
  font-size: 18px;
  background: linear-gradient(100deg, #28d4ff 0%, #3289ff 48%, #7c2cff 100%);
  box-shadow: 0 20px 48px -22px rgba(59, 130, 246, 0.88);
}

.other-login {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 18px;
  margin: 6px 0 0;
  color: rgba(148, 163, 184, 0.9);
  text-align: center;
}

.other-login span {
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(148, 163, 184, 0.35));
}

.other-login span:nth-child(3) {
  background: linear-gradient(90deg, rgba(148, 163, 184, 0.35), transparent);
}

.wechat-btn {
  grid-column: 1 / -1;
  justify-self: center;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  border: 1px solid rgba(148, 163, 184, 0.32);
  background: rgba(15, 23, 42, 0.38);
}

.wechat-btn::before {
  content: '';
  display: block;
  width: 24px;
  height: 18px;
  margin: 18px auto 0;
  border: 2px solid rgba(226, 232, 240, 0.86);
  border-radius: 12px;
}

.signup-tip {
  text-align: center;
  color: rgba(226, 232, 240, 0.82);
  font-size: 17px;
}

.core-features {
  position: relative;
  width: 100%;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0;
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.28);
  background: linear-gradient(145deg, rgba(15, 35, 83, 0.5), rgba(5, 12, 38, 0.56));
  padding: 48px 40px 24px;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.08);
}

.core-features h3 {
  position: absolute;
  top: 16px;
  left: 50%;
  transform: translateX(-50%);
  color: #f8fafc;
  font-size: 20px;
  font-weight: 800;
  white-space: nowrap;
}

.core-features h3::before,
.core-features h3::after {
  content: '';
  position: absolute;
  top: 50%;
  width: 80px;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(148, 163, 184, 0.58));
}

.core-features h3::before {
  right: calc(100% + 20px);
}

.core-features h3::after {
  left: calc(100% + 20px);
  background: linear-gradient(90deg, rgba(148, 163, 184, 0.58), transparent);
}

.core-features article {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr);
  gap: 16px;
  padding: 4px 18px;
}

.core-features article + article {
  border-left: 1px solid rgba(148, 163, 184, 0.18);
}

.core-features strong {
  display: block;
  color: #f8fafc;
  font-size: 18px;
  margin-bottom: 8px;
}

.core-features small {
  color: rgba(203, 213, 225, 0.82);
  font-size: 14px;
  line-height: 1.55;
}

.core-icon {
  display: grid;
  place-items: center;
  width: 62px;
  height: 62px;
  border-radius: 15px;
  background: linear-gradient(145deg, #4b8dff, #244fc6);
}

.core-icon::before {
  content: 'AI';
  color: white;
  font-weight: 800;
}

.core-icon--doc { background: linear-gradient(145deg, #a78bfa, #5532b7); }
.core-icon--folder { background: linear-gradient(145deg, #54c8ff, #1d4ed8); }
.core-icon--bars { background: linear-gradient(145deg, #fb923c, #7c2d12); }

.auth-footer {
  display: grid;
  gap: 12px;
  text-align: center;
}

.auth-footer p {
  color: rgba(148, 163, 184, 0.76);
  font-size: 14px;
}

@keyframes authWave {
  0%,
  100% {
    transform: translateY(0) scale(1);
  }
  50% {
    transform: translateY(-18px) scale(1.03);
  }
}

/* Spatial depth refinement */
.auth-page {
  background:
    radial-gradient(circle at 13% 18%, rgba(0, 109, 255, 0.46), transparent 30%),
    radial-gradient(circle at 82% 16%, rgba(118, 44, 255, 0.36), transparent 34%),
    radial-gradient(circle at 48% 88%, rgba(0, 174, 255, 0.1), transparent 34%),
    linear-gradient(128deg, #020918 0%, #061944 42%, #160629 100%);
}

.auth-page::before {
  opacity: 0.4;
  background-size: 150px 150px, 230px 230px, 340px 340px;
}

.auth-page::after {
  content: '';
  position: absolute;
  left: -12%;
  right: -12%;
  bottom: 14%;
  height: 330px;
  background:
    radial-gradient(ellipse at 22% 54%, rgba(0, 174, 255, 0.18), transparent 32%),
    radial-gradient(ellipse at 76% 48%, rgba(151, 71, 255, 0.16), transparent 34%),
    repeating-linear-gradient(11deg, rgba(65, 137, 255, 0.13) 0 1px, transparent 1px 20px),
    repeating-linear-gradient(102deg, rgba(151, 71, 255, 0.1) 0 1px, transparent 1px 24px);
  transform: perspective(840px) rotateX(62deg);
  opacity: 0.72;
  pointer-events: none;
  mask-image: linear-gradient(to bottom, transparent, #000 18%, #000 76%, transparent);
}

.auth-main {
  position: relative;
  z-index: 1;
}

.ai-stage {
  filter: drop-shadow(0 30px 78px rgba(0, 0, 0, 0.38));
}

.stage-orbit {
  box-shadow:
    0 0 32px rgba(56, 189, 248, 0.28),
    inset 0 0 28px rgba(56, 189, 248, 0.08);
}

.stage-platform {
  bottom: 14px;
  height: 74px;
  background:
    radial-gradient(ellipse, rgba(44, 217, 255, 0.86) 0%, rgba(37, 99, 235, 0.34) 42%, transparent 72%);
  filter: blur(2px);
}

.ai-console {
  border-color: rgba(95, 164, 255, 0.54);
  background:
    linear-gradient(145deg, rgba(13, 44, 122, 0.92), rgba(5, 12, 42, 0.84)),
    linear-gradient(90deg, rgba(56, 189, 248, 0.08), transparent 32%, rgba(124, 58, 237, 0.08)),
    repeating-linear-gradient(90deg, rgba(125, 211, 252, 0.055) 0 1px, transparent 1px 68px);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.16),
    inset 0 -24px 60px rgba(0, 0, 0, 0.22),
    0 34px 90px -42px rgba(37, 99, 235, 0.98);
}

.ai-console::before,
.ai-console::after {
  content: '';
  position: absolute;
  pointer-events: none;
}

.ai-console::before {
  left: 18px;
  right: 18px;
  top: 14px;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(105, 211, 255, 0.7), transparent);
}

.ai-console::after {
  left: 50%;
  bottom: -42px;
  width: 300px;
  height: 70px;
  border-radius: 50%;
  transform: translateX(-50%);
  background: radial-gradient(ellipse, rgba(0, 184, 255, 0.45), transparent 68%);
  filter: blur(8px);
}

.console-core {
  box-shadow:
    0 0 36px rgba(56, 189, 248, 0.72),
    0 0 0 16px rgba(37, 99, 235, 0.12),
    inset 0 0 26px rgba(56, 189, 248, 0.28);
}

.float-card {
  border-color: rgba(115, 174, 255, 0.55);
  background:
    radial-gradient(circle at 26% 16%, rgba(255, 255, 255, 0.18), transparent 34%),
    linear-gradient(145deg, rgba(34, 75, 171, 0.9), rgba(43, 28, 115, 0.78));
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.14),
    0 22px 52px -24px rgba(37, 99, 235, 0.98);
}

.auth-card {
  border-color: rgba(149, 179, 255, 0.32);
  background:
    radial-gradient(circle at 100% 0%, rgba(124, 58, 237, 0.28), transparent 38%),
    linear-gradient(150deg, rgba(8, 18, 52, 0.86), rgba(8, 6, 29, 0.78));
  box-shadow:
    0 36px 100px -58px rgba(0, 0, 0, 0.95),
    0 20px 70px -48px rgba(89, 199, 255, 0.55),
    inset 0 1px 0 rgba(255, 255, 255, 0.12);
}

.core-features,
.hero-utility--compact {
  background:
    linear-gradient(145deg, rgba(18, 42, 96, 0.58), rgba(5, 12, 38, 0.46));
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.1),
    0 28px 82px -56px rgba(37, 99, 235, 0.72);
}

@media (max-width: 1260px) {
  .auth-page {
    padding: 28px;
  }

  .auth-main {
    grid-template-columns: 1fr;
  }

  .auth-card {
    max-width: 640px;
    margin: 0 auto;
  }

  .core-features {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    row-gap: 22px;
  }
}

@media (max-width: 760px) {
  .auth-nav__links,
  .ai-stage {
    display: none;
  }

  .auth-hero h1 {
    font-size: 40px;
    white-space: normal;
  }

  .hero-utility--compact,
  .core-features {
    grid-template-columns: 1fr;
  }

  .auth-card {
    min-height: 0;
    padding: 28px 18px;
  }
}
</style>
