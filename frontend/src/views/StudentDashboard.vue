<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import MarkdownIt from 'markdown-it'

const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
})

const API_BASE = import.meta.env.VITE_API_BASE || '/api'
const STUDENT_API_URL = `${API_BASE}/student/upload_and_generate`

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

const router = useRouter()
const studentToken = ref(localStorage.getItem('student_token') || '')
const studentUser = ref(null)
const historyItems = ref([])
const historyLoading = ref(false)
const historyError = ref('')

const step = ref(1)
const currentTab = ref('outline')
const studyData = ref(null)
const cardStates = ref({})
const quizStates = ref([])

const fileInput = ref(null)
const selectedFile = ref(null)
const isDragging = ref(false)
const isLoading = ref(false)
const error = ref('')
const historyHint = ref('')
const generationProgress = ref(0)
let generationTimer = null

const outlineHtml = computed(() => {
  if (!studyData.value?.outline) return ''
  return md.render(studyData.value.outline)
})

watch(studyData, () => {
  cardStates.value = {}
  quizStates.value = studyData.value?.quiz?.map(() => ({ selected: null, correct: null })) || []
})

function triggerFileInput() {
  fileInput.value?.click()
}

function handleDragOver() {
  isDragging.value = true
}

function handleDragLeave() {
  isDragging.value = false
}

function handleDrop(event) {
  isDragging.value = false
  handleFiles(event.dataTransfer?.files)
}

function handleFileInput(event) {
  handleFiles(event.target.files)
}

function handleFiles(files) {
  if (!files || !files.length) return
  const file = files[0]
  const ext = file.name.split('.').pop()?.toLowerCase()
  if (!['pdf', 'ppt', 'pptx', 'doc', 'docx'].includes(ext)) {
    error.value = '仅支持 PDF / PPT / Word 文件'
    return
  }
  error.value = ''
  selectedFile.value = file
}

function resetUpload() {
  selectedFile.value = null
  error.value = ''
  if (fileInput.value) fileInput.value.value = ''
}

function startGenerationProgress() {
  generationProgress.value = 6
  if (generationTimer) {
    clearInterval(generationTimer)
  }
  generationTimer = window.setInterval(() => {
    if (generationProgress.value < 92) {
      generationProgress.value += 4
    }
  }, 520)
}

function stopGenerationProgress() {
  if (generationTimer) {
    clearInterval(generationTimer)
    generationTimer = null
  }
  if (generationProgress.value > 0) {
    generationProgress.value = 100
    window.setTimeout(() => {
      generationProgress.value = 0
    }, 600)
  }
}

function loadStudentUser() {
  const raw = localStorage.getItem('student_user')
  if (!raw) {
    studentUser.value = null
    return
  }
  try {
    studentUser.value = JSON.parse(raw)
  } catch (err) {
    studentUser.value = null
  }
}

function getAuthHeaders() {
  if (!studentToken.value) return {}
  return { Authorization: `Bearer ${studentToken.value}` }
}

async function fetchHistory() {
  if (!studentToken.value) {
    historyItems.value = []
    return
  }
  historyLoading.value = true
  historyError.value = ''
  try {
    const response = await fetch(`${API_BASE}/student/history`, {
      headers: getAuthHeaders(),
    })
    const data = await response.json().catch(() => ({}))
    if (!response.ok) {
      throw new Error(getApiErrorMessage(data, response.status))
    }
    historyItems.value = data.items || []
  } catch (err) {
    historyError.value = `历史记录加载失败：${err.message}`
  } finally {
    historyLoading.value = false
  }
}

async function openHistory(item) {
  if (!item?.id) return
  historyHint.value = ''
  try {
    const response = await fetch(`${API_BASE}/student/history/${item.id}`, {
      headers: getAuthHeaders(),
    })
    const data = await response.json().catch(() => ({}))
    if (!response.ok) {
      throw new Error(getApiErrorMessage(data, response.status))
    }
    studyData.value = data.data
    step.value = 2
    currentTab.value = 'outline'
  } catch (err) {
    historyHint.value = `加载失败：${err.message}`
  }
}

function goLogin() {
  router.push('/auth/student')
}

function logout() {
  localStorage.removeItem('student_token')
  localStorage.removeItem('student_user')
  studentToken.value = ''
  studentUser.value = null
  historyItems.value = []
}

async function startGenerate() {
  if (isLoading.value) return
  if (!selectedFile.value) {
    error.value = '请先上传复习资料'
    return
  }

  error.value = ''
  isLoading.value = true
  startGenerationProgress()

  try {
    const formData = new FormData()
    formData.append('file', selectedFile.value)

    const response = await fetch(STUDENT_API_URL, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: formData,
    })

    const data = await response.json().catch(() => ({}))
    if (!response.ok) {
      throw new Error(getApiErrorMessage(data, response.status))
    }

    studyData.value = data
    step.value = 2
    currentTab.value = 'outline'
    if (studentToken.value) {
      fetchHistory()
    }
  } catch (err) {
    error.value = `生成失败：${err.message}`
  } finally {
    isLoading.value = false
    stopGenerationProgress()
  }
}

function toggleCard(index) {
  cardStates.value[index] = !cardStates.value[index]
}

function selectOption(questionIndex, option) {
  const state = quizStates.value[questionIndex]
  if (!state || state.selected) return
  const answer = studyData.value.quiz[questionIndex].answer
  const correct = option.trim().startsWith(answer)
  quizStates.value[questionIndex] = { selected: option, correct }
}

onMounted(() => {
  studentToken.value = localStorage.getItem('student_token') || ''
  loadStudentUser()
  fetchHistory()
})

onBeforeUnmount(() => {
  if (generationTimer) {
    clearInterval(generationTimer)
    generationTimer = null
  }
})
</script>

<template>
  <div class="student-shell min-h-screen">
    <div class="student-bg"></div>
    <div class="cosmic-mist"></div>
    <div class="student-content max-w-6xl mx-auto px-6 py-10">
      <header class="flex flex-col gap-6 md:flex-row md:items-center md:justify-between">
        <div>
          <p class="text-xs uppercase tracking-[0.4em] text-sky-300 font-semibold">Student Portal</p>
          <h1 class="mt-3 text-3xl md:text-4xl font-semibold text-slate-100">AI 学习中心</h1>
          <p class="mt-3 text-sm text-slate-300">
            上传资料，AI 自动生成复习大纲、智能闪卡与押题训练。
          </p>
        </div>
        <div class="flex items-center gap-3">
          <span class="text-xs text-slate-300">
            {{ studentToken ? `学生：${studentUser?.name || '已登录'}` : '未登录' }}
          </span>
          <button
            v-if="studentToken"
            class="inline-flex items-center gap-2 rounded-full border border-white/10 bg-slate-900/60 px-4 py-2 text-xs font-semibold text-slate-200 hover:bg-slate-900/80"
            type="button"
            @click="logout"
          >
            退出登录
          </button>
          <button
            v-else
            class="inline-flex items-center gap-2 rounded-full border border-sky-400/30 bg-slate-900/60 px-4 py-2 text-xs font-semibold text-sky-200 hover:bg-slate-900/80"
            type="button"
            @click="goLogin"
          >
            去登录
          </button>
          <router-link
            to="/auth/teacher"
            class="inline-flex items-center gap-2 rounded-full border border-white/10 bg-slate-900/60 px-4 py-2 text-xs font-semibold text-slate-200 hover:bg-slate-900/80"
          >
            返回登录页
          </router-link>
        </div>
      </header>

      <section v-if="step === 1" class="mt-10 space-y-6">
        <div class="history-panel floating-card" v-if="studentToken">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-xs uppercase tracking-[0.4em] text-slate-300">History</p>
              <h2 class="mt-2 text-lg font-semibold text-slate-100">学习记录</h2>
            </div>
            <button
              class="text-xs text-sky-300 font-semibold"
              type="button"
              @click="fetchHistory"
              :disabled="historyLoading"
            >
              {{ historyLoading ? '刷新中...' : '刷新' }}
            </button>
          </div>

          <p v-if="historyError" class="mt-3 text-xs text-rose-300">{{ historyError }}</p>
          <p v-else-if="historyItems.length === 0" class="mt-3 text-xs text-slate-300">
            暂无历史记录，生成学习包后会自动保存。
          </p>

          <div class="mt-4 grid gap-3 md:grid-cols-2" v-if="historyItems.length">
            <button
              v-for="item in historyItems"
              :key="item.id"
              class="history-card"
              type="button"
              @click="openHistory(item)"
            >
              <p class="text-sm font-semibold text-slate-100">{{ item.filename || '学习包' }}</p>
              <p class="text-xs text-slate-300">{{ item.created_at ? item.created_at.slice(0, 19).replace('T', ' ') : '未知时间' }}</p>
            </button>
          </div>
          <p v-if="historyHint" class="mt-3 text-xs text-rose-300">{{ historyHint }}</p>
        </div>
        <div class="history-panel floating-card" v-else>
          <div class="flex items-center justify-between">
            <div>
              <p class="text-xs uppercase tracking-[0.4em] text-slate-300">History</p>
              <h2 class="mt-2 text-lg font-semibold text-slate-100">学习记录</h2>
              <p class="mt-2 text-sm text-slate-300">登录后可保存与回看历史学习包。</p>
            </div>
            <button
              class="text-xs text-sky-300 font-semibold"
              type="button"
              @click="goLogin"
            >
              去登录
            </button>
          </div>
        </div>
        <div
          class="upload-panel floating-card"
          @dragover.prevent="handleDragOver"
          @dragleave="handleDragLeave"
          @drop.prevent="handleDrop"
        >
          <div :class="['upload-zone', { active: isDragging }]" @click="triggerFileInput">
            <div>
              <span class="upload-glyph" aria-hidden="true"></span>
              <h2 class="mt-4 text-xl font-semibold text-slate-100">拖拽上传复习资料</h2>
              <p class="mt-2 text-sm text-slate-300">支持 PDF / PPT / Word</p>
            </div>
            <button type="button" class="upload-btn">选择文件</button>
            <input
              ref="fileInput"
              type="file"
              accept=".pdf,.ppt,.pptx,.doc,.docx"
              class="hidden"
              @change="handleFileInput"
            />
          </div>

          <div class="upload-meta">
            <div>
              <p class="font-semibold text-slate-100">{{ selectedFile ? selectedFile.name : '尚未上传资料' }}</p>
              <p class="text-xs text-slate-300">
                {{ selectedFile ? '准备生成：复习大纲 · 闪卡 · 押题卷' : '上传后即刻进入复习计划生成' }}
              </p>
            </div>
            <div class="flex flex-wrap gap-3">
              <button
                type="button"
                class="rounded-full border border-white/10 px-4 py-2 text-xs font-semibold text-slate-300 hover:bg-slate-900/60"
                @click="resetUpload"
                :disabled="isLoading"
              >
                重新选择
              </button>
              <button
                type="button"
                class="rounded-full bg-gradient-to-r from-sky-400 via-indigo-400 to-fuchsia-400 px-5 py-2 text-xs font-semibold text-slate-900 hover:opacity-90"
                @click="startGenerate"
                :disabled="isLoading"
              >
                <span v-if="isLoading" class="inline-flex items-center gap-2">
                  <span class="spinner"></span>
                  AI 正在阅读教材并为您规划复习方案...
                </span>
                <span v-else>开始生成复习方案</span>
              </button>
            </div>
          </div>
          <div v-if="generationProgress" class="generation-progress">
            <div class="generation-progress-bar" :style="{ width: `${generationProgress}%` }"></div>
            <span>AI 正在构建学习包...</span>
          </div>
        </div>

        <p v-if="error" class="text-sm text-rose-300">{{ error }}</p>
      </section>

      <section v-else class="mt-10">
        <div class="tab-bar">
          <button
            class="tab-btn"
            :class="currentTab === 'outline' ? 'tab-active' : ''"
            @click="currentTab = 'outline'"
          >
            📝 重点大纲
          </button>
          <button
            class="tab-btn"
            :class="currentTab === 'flashcard' ? 'tab-active' : ''"
            @click="currentTab = 'flashcard'"
          >
            📇 记忆闪卡
          </button>
          <button
            class="tab-btn"
            :class="currentTab === 'quiz' ? 'tab-active' : ''"
            @click="currentTab = 'quiz'"
          >
            ✍️ 模拟押题
          </button>
        </div>

        <div class="mt-6">
          <div v-if="currentTab === 'outline'" class="panel">
            <div class="markdown-body text-sm text-slate-100" v-html="outlineHtml"></div>
          </div>

          <div v-else-if="currentTab === 'flashcard'" class="grid gap-4 md:grid-cols-2">
            <div
              v-for="(card, index) in studyData.flashcards"
              :key="index"
              class="flip-card"
              @click="toggleCard(index)"
            >
              <div :class="['flip-card-inner', cardStates[index] ? 'is-flipped' : '']">
                <div class="flip-face flip-front">
                  <h3>{{ card.front }}</h3>
                  <span>点击翻转</span>
                </div>
                <div class="flip-face flip-back">
                  <p>{{ card.back }}</p>
                </div>
              </div>
            </div>
          </div>

          <div v-else class="space-y-5">
            <div v-for="(item, idx) in studyData.quiz" :key="idx" class="panel">
              <div class="flex items-start gap-3">
                <span class="badge">Q{{ idx + 1 }}</span>
                <h3 class="text-base font-semibold text-slate-100">{{ item.question }}</h3>
              </div>
              <div class="mt-4 grid gap-2">
                <button
                  v-for="(opt, oIdx) in item.options"
                  :key="oIdx"
                  class="option"
                  :class="[
                    quizStates[idx]?.selected === opt && quizStates[idx]?.correct ? 'option-correct' : '',
                    quizStates[idx]?.selected === opt && quizStates[idx]?.correct === false ? 'option-wrong' : '',
                  ]"
                  @click="selectOption(idx, opt)"
                >
                  {{ opt }}
                </button>
              </div>
              <div v-if="quizStates[idx]?.selected" class="analysis">
                <strong>解析：</strong> {{ item.analysis }}
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Manrope:wght@400;600;700&family=ZCOOL+XiaoWei&display=swap');

.student-shell {
  font-family: 'Manrope', 'Noto Sans SC', sans-serif;
  position: relative;
  overflow: hidden;
  color: #e2e8f0;
  background:
    radial-gradient(circle at 20% 20%, rgba(59, 130, 246, 0.22), transparent 55%),
    radial-gradient(circle at 80% 10%, rgba(236, 72, 153, 0.2), transparent 50%),
    radial-gradient(circle at 70% 80%, rgba(14, 165, 233, 0.2), transparent 55%),
    #020617;
}

.student-shell::before,
.student-shell::after {
  content: '';
  position: absolute;
  inset: -40% 20% auto -35%;
  height: 70%;
  background: radial-gradient(circle, rgba(125, 211, 252, 0.2), transparent 70%);
  opacity: 0.75;
  animation: nebulaPulse 18s ease-in-out infinite;
  pointer-events: none;
  z-index: 0;
}

.student-shell::after {
  inset: auto -35% -40% 10%;
  background: radial-gradient(circle, rgba(244, 114, 182, 0.22), transparent 70%);
  animation-delay: 6s;
}

.student-bg {
  position: absolute;
  inset: 0;
  background-image:
    radial-gradient(1px 1px at 20px 30px, rgba(255, 255, 255, 0.65), transparent 60%),
    radial-gradient(1px 1px at 80px 120px, rgba(255, 255, 255, 0.45), transparent 60%),
    radial-gradient(1px 1px at 140px 40px, rgba(255, 255, 255, 0.35), transparent 60%),
    radial-gradient(2px 2px at 40px 90px, rgba(255, 255, 255, 0.3), transparent 70%),
    radial-gradient(2px 2px at 120px 160px, rgba(255, 255, 255, 0.25), transparent 70%);
  background-size: 200px 200px;
  opacity: 0.55;
  animation: starDrift 28s linear infinite;
  pointer-events: none;
}

.student-content {
  position: relative;
  z-index: 1;
}

h1 {
  font-family: 'ZCOOL XiaoWei', serif;
}

.upload-panel {
  background: rgba(15, 23, 42, 0.75);
  border-radius: 24px;
  padding: 28px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  box-shadow: 0 18px 50px -34px rgba(15, 23, 42, 0.7);
  backdrop-filter: blur(18px);
}

.history-panel {
  background: rgba(15, 23, 42, 0.7);
  border-radius: 22px;
  padding: 22px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  box-shadow: 0 14px 32px -26px rgba(15, 23, 42, 0.6);
  backdrop-filter: blur(18px);
}

.history-card {
  text-align: left;
  padding: 14px 16px;
  border-radius: 16px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  background: rgba(15, 23, 42, 0.6);
  color: #e2e8f0;
  transition: all 0.2s ease;
}

.history-card:hover {
  border-color: rgba(56, 189, 248, 0.5);
  box-shadow: 0 10px 24px -18px rgba(56, 189, 248, 0.3);
  transform: translateY(-2px);
}

.upload-zone {
  border: 2px dashed rgba(148, 163, 184, 0.35);
  border-radius: 20px;
  padding: 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  cursor: pointer;
  background: rgba(15, 23, 42, 0.55);
  transition: all 0.2s ease;
}

.upload-zone.active {
  border-color: rgba(56, 189, 248, 0.7);
  background: rgba(30, 41, 59, 0.7);
  transform: translateY(-2px);
}

.upload-btn {
  background: linear-gradient(135deg, #38bdf8, #a78bfa);
  color: #020617;
  border-radius: 999px;
  padding: 10px 18px;
  font-size: 12px;
  font-weight: 600;
}

.upload-meta {
  margin-top: 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
}

.generation-progress {
  position: relative;
  margin-top: 18px;
  height: 22px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.7);
  border: 1px solid rgba(148, 163, 184, 0.2);
  overflow: hidden;
  display: flex;
  align-items: center;
  padding-left: 12px;
  font-size: 11px;
  color: #e2e8f0;
  box-shadow: 0 10px 22px -18px rgba(15, 23, 42, 0.7);
}

.generation-progress-bar {
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, rgba(56, 189, 248, 0.45), rgba(129, 140, 248, 0.45));
  transition: width 0.4s ease;
  z-index: 0;
}

.generation-progress span {
  position: relative;
  z-index: 1;
}

.spinner {
  width: 16px;
  height: 16px;
  border-radius: 999px;
  border: 2px solid rgba(255, 255, 255, 0.4);
  border-top-color: #ffffff;
  display: inline-block;
  animation: spin 0.8s linear infinite;
}

.tab-bar {
  display: inline-flex;
  padding: 6px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.7);
  border: 1px solid rgba(148, 163, 184, 0.2);
  gap: 6px;
}

.tab-btn {
  padding: 10px 18px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
  color: #cbd5f5;
  transition: all 0.2s ease;
}

.tab-active {
  background: linear-gradient(135deg, #38bdf8, #a78bfa);
  color: #020617;
  box-shadow: 0 12px 24px -18px rgba(56, 189, 248, 0.4);
}

.panel {
  background: rgba(15, 23, 42, 0.75);
  border-radius: 20px;
  padding: 22px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  box-shadow: 0 14px 32px -26px rgba(15, 23, 42, 0.6);
}

.flip-card {
  perspective: 1200px;
  height: 180px;
}

.flip-card-inner {
  position: relative;
  width: 100%;
  height: 100%;
  transform-style: preserve-3d;
  transition: transform 0.6s ease;
}

.is-flipped {
  transform: rotateY(180deg);
}

.flip-face {
  position: absolute;
  inset: 0;
  border-radius: 18px;
  padding: 18px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  backface-visibility: hidden;
}

.flip-front {
  background: linear-gradient(135deg, #38bdf8, #a78bfa);
  color: #020617;
  font-weight: 600;
}

.flip-front span {
  margin-top: 10px;
  font-size: 12px;
  color: rgba(2, 6, 23, 0.7);
}

.flip-back {
  background: rgba(2, 6, 23, 0.7);
  color: #e2e8f0;
  border: 1px solid rgba(148, 163, 184, 0.25);
  transform: rotateY(180deg);
  font-size: 14px;
}

.badge {
  background: rgba(56, 189, 248, 0.2);
  color: #7dd3fc;
  font-size: 12px;
  font-weight: 700;
  padding: 4px 10px;
  border-radius: 999px;
}

.option {
  text-align: left;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  background: rgba(15, 23, 42, 0.7);
  font-size: 13px;
  color: #e2e8f0;
  transition: all 0.2s ease;
}

.option:hover {
  background: rgba(30, 41, 59, 0.8);
}

.option-correct {
  background: rgba(34, 197, 94, 0.2);
  border-color: rgba(34, 197, 94, 0.6);
  color: #bbf7d0;
}

.option-wrong {
  background: rgba(244, 63, 94, 0.2);
  border-color: rgba(244, 63, 94, 0.6);
  color: #fecdd3;
}

.analysis {
  margin-top: 12px;
  background: rgba(30, 41, 59, 0.8);
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 12px;
  padding: 12px;
  font-size: 12px;
  color: #fef3c7;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes starDrift {
  0% {
    background-position: 0 0;
  }
  100% {
    background-position: -200px 240px;
  }
}

@keyframes nebulaPulse {
  0%,
  100% {
    transform: translateY(0);
    opacity: 0.7;
  }
  50% {
    transform: translateY(-16px) scale(1.02);
    opacity: 1;
  }
}

@media (max-width: 768px) {
  .upload-zone {
    flex-direction: column;
    align-items: flex-start;
  }
  .upload-meta {
    flex-direction: column;
    align-items: flex-start;
  }
  .tab-bar {
    width: 100%;
    flex-wrap: wrap;
  }
}

/* Unified premium skin */
.student-shell {
  isolation: isolate;
  background:
    radial-gradient(circle at 18% 12%, rgba(0, 132, 255, 0.34), transparent 32%),
    radial-gradient(circle at 84% 16%, rgba(124, 58, 237, 0.28), transparent 34%),
    linear-gradient(128deg, #061b47 0%, #08143b 44%, #17072e 100%);
}

.student-shell::before {
  background:
    linear-gradient(165deg, transparent 8%, rgba(49, 116, 255, 0.18) 38%, transparent 42%),
    repeating-linear-gradient(172deg, rgba(78, 147, 255, 0.1) 0 1px, transparent 1px 18px);
  inset: auto -16% 10% -18%;
  height: 42%;
  transform: skewY(-9deg);
  opacity: 0.74;
  filter: none;
  mask-image: linear-gradient(to top, #000, transparent 88%);
}

.student-shell::after {
  background: radial-gradient(circle at 70% 40%, rgba(210, 93, 255, 0.24), transparent 66%);
  opacity: 0.86;
}

.student-content {
  max-width: 1280px;
}

.student-content > header {
  min-height: 128px;
  padding: 20px 4px 10px;
}

.student-content > header h1 {
  color: #fff;
  font-family: 'Manrope', 'Noto Sans SC', sans-serif;
  font-size: clamp(36px, 4vw, 56px);
  font-weight: 800;
  letter-spacing: 0;
  text-shadow: 0 24px 70px rgba(59, 130, 246, 0.46);
}

.student-content > header p:first-child {
  color: #55e4ff;
  font-size: 12px;
  font-weight: 800;
}

.student-content > header p:last-child {
  color: rgba(226, 232, 240, 0.82);
  font-size: 16px;
}

.student-content > header button,
.history-panel button,
.upload-meta button,
.tab-btn {
  backdrop-filter: blur(18px);
}

.student-content > header button {
  border-color: rgba(148, 163, 184, 0.24) !important;
  background: rgba(6, 13, 43, 0.48) !important;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.08);
}

.history-panel,
.upload-panel,
.panel {
  border-radius: 20px;
  border: 1px solid rgba(148, 163, 184, 0.24);
  background: linear-gradient(145deg, rgba(14, 30, 75, 0.76), rgba(6, 12, 38, 0.66));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.08), 0 24px 70px -48px rgba(37, 99, 235, 0.82);
}

.history-panel {
  padding: 24px;
}

.history-card {
  position: relative;
  min-height: 74px;
  padding: 16px 18px 16px 58px;
  border-radius: 15px;
  border-color: rgba(148, 163, 184, 0.2);
  background: linear-gradient(145deg, rgba(26, 48, 104, 0.7), rgba(7, 14, 45, 0.62));
}

.history-card::before {
  content: '';
  position: absolute;
  left: 16px;
  top: 18px;
  width: 28px;
  height: 28px;
  border-radius: 9px;
  background: linear-gradient(145deg, #38d5ff, #7b5cff);
  box-shadow: 0 12px 30px -14px rgba(56, 189, 248, 0.9);
}

.history-card p {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.upload-panel {
  padding: 26px;
}

.upload-zone {
  min-height: 208px;
  border: 1px dashed rgba(96, 165, 250, 0.52);
  border-radius: 18px;
  background:
    radial-gradient(circle at 18% 20%, rgba(56, 189, 248, 0.18), transparent 38%),
    rgba(4, 10, 34, 0.45);
}

.upload-zone.active {
  border-color: rgba(45, 212, 191, 0.78);
  background:
    radial-gradient(circle at 18% 20%, rgba(45, 212, 191, 0.2), transparent 42%),
    rgba(9, 20, 54, 0.68);
}

.upload-glyph {
  position: relative;
  display: inline-grid;
  place-items: center;
  width: 66px;
  height: 54px;
  border-radius: 18px;
  background: linear-gradient(145deg, rgba(37, 99, 235, 0.9), rgba(124, 58, 237, 0.75));
  box-shadow: 0 24px 56px -28px rgba(56, 189, 248, 0.95);
}

.upload-glyph::before {
  content: '';
  width: 28px;
  height: 28px;
  border-radius: 8px;
  border: 2px solid rgba(226, 232, 240, 0.92);
  border-top-color: transparent;
  transform: translateY(2px);
}

.upload-btn,
.upload-meta .bg-gradient-to-r {
  min-height: 42px;
  border-radius: 12px;
  color: #f8fbff;
  background: linear-gradient(100deg, #2bd4ff, #347cff 48%, #7b2cff);
  box-shadow: 0 18px 42px -22px rgba(56, 189, 248, 0.86);
}

.generation-progress {
  height: 26px;
  border-color: rgba(96, 165, 250, 0.32);
  background: rgba(3, 8, 28, 0.62);
}

.generation-progress-bar {
  background: linear-gradient(90deg, rgba(45, 212, 191, 0.7), rgba(59, 130, 246, 0.72), rgba(124, 58, 237, 0.72));
}

.tab-bar {
  border-radius: 16px;
  border-color: rgba(148, 163, 184, 0.24);
  background: rgba(5, 11, 35, 0.58);
  padding: 7px;
}

.tab-btn {
  border-radius: 12px;
  color: rgba(203, 213, 225, 0.84);
}

.tab-active {
  color: #fff;
  background: linear-gradient(100deg, #2bd4ff, #347cff 48%, #7b2cff);
}

.flip-card {
  height: 210px;
}

.flip-face {
  border-radius: 20px;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.12);
}

.flip-front {
  background:
    radial-gradient(circle at 18% 18%, rgba(255, 255, 255, 0.32), transparent 32%),
    linear-gradient(145deg, rgba(48, 119, 255, 0.95), rgba(124, 58, 237, 0.86));
  color: #fff;
}

.flip-back {
  background: linear-gradient(145deg, rgba(12, 28, 75, 0.92), rgba(7, 14, 45, 0.88));
}

.badge {
  border: 1px solid rgba(56, 189, 248, 0.34);
  background: rgba(56, 189, 248, 0.14);
}

.option {
  border-radius: 14px;
  border-color: rgba(148, 163, 184, 0.22);
  background: rgba(5, 11, 35, 0.54);
}

.option:hover {
  border-color: rgba(56, 189, 248, 0.48);
  background: rgba(18, 38, 87, 0.72);
}

.analysis {
  border-color: rgba(45, 212, 191, 0.24);
  background: rgba(8, 30, 55, 0.72);
  color: #dbeafe;
}
</style>
