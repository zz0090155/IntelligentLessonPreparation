<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { downloadMicroLessonHtml, openMicroLessonWindow } from '../utils/microLesson'

const API_BASE = import.meta.env.VITE_API_BASE || '/api'
const router = useRouter()

const tasks = ref([])
const statusCounts = ref({})
const loading = ref(false)
const error = ref('')
const activeStatus = ref('all')
const teacherUser = ref(null)
let pollTimer = null

const STATUS_TABS = [
  { value: 'all', label: '全部任务' },
  { value: 'queued', label: '排队中' },
  { value: 'running', label: '生成中' },
  { value: 'completed', label: '已完成' },
  { value: 'failed', label: '失败' },
]

const runningCount = computed(() => Number(statusCounts.value?.queued || 0) + Number(statusCounts.value?.running || 0))
const completedCount = computed(() => Number(statusCounts.value?.completed || 0))
const failedCount = computed(() => Number(statusCounts.value?.failed || 0))

function getTeacherToken() {
  return localStorage.getItem('teacher_token') || ''
}

function getAuthHeaders(json = false) {
  const token = getTeacherToken()
  return {
    ...(json ? { 'Content-Type': 'application/json' } : {}),
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  }
}

function resolveApiUrl(url) {
  const value = String(url || '').trim()
  if (!value) return ''
  if (/^https?:\/\//i.test(value)) return value
  const base = String(API_BASE || '').replace(/\/$/, '')
  return `${base}${value.startsWith('/') ? value : `/${value}`}`
}

function loadTeacherUser() {
  try {
    teacherUser.value = JSON.parse(localStorage.getItem('teacher_user') || 'null')
  } catch (err) {
    teacherUser.value = null
  }
}

function formatDate(value) {
  if (!value) return '--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleString()
}

function getStatusLabel(status) {
  const map = {
    queued: '排队中',
    running: '生成中',
    completed: '已完成',
    failed: '失败',
  }
  return map[status] || status || '--'
}

function getStatusTone(status) {
  if (status === 'completed') return 'is-completed'
  if (status === 'failed') return 'is-failed'
  if (status === 'running') return 'is-running'
  return 'is-queued'
}

function getTypeIcon(task) {
  const type = String(task?.task_type || '')
  if (type.includes('ppt')) return 'P'
  if (type.includes('doc')) return 'W'
  if (type.includes('video')) return 'V'
  return 'AI'
}

function getCount(status) {
  if (status === 'all') {
    return Object.values(statusCounts.value || {}).reduce((sum, item) => sum + Number(item || 0), 0)
  }
  return Number(statusCounts.value?.[status] || 0)
}

function getDownloadUrl(task) {
  return task?.result?.download_url || task?.result?.resource_download_url || task?.result?.artifact?.download_url || ''
}

function getDownloadName(task) {
  return task?.result?.artifact?.download_filename || task?.result?.download_filename || `${task?.title || '生成资源'}`
}

function getTaskArtifact(task) {
  return task?.result?.artifact || {}
}

function getTaskMetadata(task) {
  return getTaskArtifact(task)?.metadata || {}
}

function isMicrocourseTask(task) {
  const taskType = String(task?.task_type || '').toLowerCase()
  const artifactType = String(getTaskArtifact(task)?.type || '').toLowerCase()
  return taskType.includes('video') || artifactType === 'video'
}

function buildMicrocourseMessageFromTask(task) {
  const result = task?.result || {}
  const metadata = getTaskMetadata(task)
  const videoUrl =
    result.video_url ||
    result.videoUrl ||
    metadata.video_url ||
    metadata.videoUrl ||
    getDownloadUrl(task)
  const scriptText =
    result.script_text ||
    result.scriptText ||
    metadata.script_text ||
    metadata.scriptText ||
    ''
  return {
    type: 'video',
    title: task?.title || '数字人微课',
    text: scriptText,
    scriptText,
    videoUrl: resolveApiUrl(videoUrl),
    microcourseJson:
      result.microcourse_json ||
      result.microcourseJson ||
      metadata.microcourse_json ||
      metadata.microcourseJson ||
      null,
    scenePreviews:
      result.scene_previews ||
      result.scenePreviews ||
      metadata.scene_previews ||
      metadata.scenePreviews ||
      [],
    boardContent:
      result.board_content ||
      result.boardContent ||
      metadata.board_content ||
      metadata.boardContent ||
      [],
  }
}

function canOpenMicrocourseTask(task) {
  return task?.status === 'completed' && isMicrocourseTask(task) && Boolean(buildMicrocourseMessageFromTask(task).videoUrl)
}

function openTaskMicroLesson(task) {
  if (!canOpenMicrocourseTask(task)) return
  openMicroLessonWindow(buildMicrocourseMessageFromTask(task))
}

function downloadTaskMicroLesson(task) {
  if (!canOpenMicrocourseTask(task)) return
  downloadMicroLessonHtml(buildMicrocourseMessageFromTask(task))
}

async function fetchTasks({ silent = false } = {}) {
  if (!silent) loading.value = true
  error.value = ''
  try {
    const params = new URLSearchParams({ limit: '120', offset: '0' })
    if (activeStatus.value && activeStatus.value !== 'all') {
      params.set('status', activeStatus.value)
    }
    const response = await fetch(resolveApiUrl(`/generation_tasks?${params.toString()}`), {
      headers: getAuthHeaders(),
    })
    const data = await response.json().catch(() => ({}))
    if (!response.ok) {
      throw new Error(data.detail || data.message || `HTTP ${response.status}`)
    }
    tasks.value = Array.isArray(data.items) ? data.items : []
    statusCounts.value = data.status_counts || {}
  } catch (err) {
    error.value = err instanceof Error ? err.message : '任务加载失败'
  } finally {
    loading.value = false
  }
}

async function downloadTask(task) {
  const downloadUrl = getDownloadUrl(task)
  if (!downloadUrl) return
  try {
    const response = await fetch(resolveApiUrl(downloadUrl), {
      headers: getAuthHeaders(),
    })
    const blob = await response.blob()
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = getDownloadName(task)
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
  } catch (err) {
    error.value = `下载失败：${err instanceof Error ? err.message : '未知错误'}`
  }
}

function goTeacher() {
  router.push('/teacher')
}

function goResources() {
  router.push('/teacher/resources')
}

function restartPolling() {
  if (pollTimer) window.clearInterval(pollTimer)
  pollTimer = window.setInterval(() => {
    if (runningCount.value > 0 || activeStatus.value === 'running' || activeStatus.value === 'queued') {
      fetchTasks({ silent: true })
    }
  }, 3000)
}

function selectStatus(status) {
  activeStatus.value = status
  fetchTasks()
}

onMounted(() => {
  loadTeacherUser()
  fetchTasks()
  restartPolling()
})

onBeforeUnmount(() => {
  if (pollTimer) window.clearInterval(pollTimer)
})
</script>

<template>
  <div class="task-page">
    <div class="task-bg" aria-hidden="true">
      <i class="star-field"></i>
      <i class="grid-plane"></i>
      <i class="aurora aurora-one"></i>
      <i class="aurora aurora-two"></i>
    </div>

    <main class="task-shell">
      <header class="task-header">
        <button type="button" class="brand-btn" @click="goTeacher">
          <span class="brand-logo"><i></i><i></i></span>
          <span>教策云枢</span>
        </button>
        <div class="header-actions">
          <button type="button" class="ghost-btn" @click="goResources">资源库</button>
          <button type="button" class="ghost-btn" @click="goTeacher">返回工作台</button>
          <span class="teacher-pill">
            <b>{{ (teacherUser?.name || '教师').slice(0, 2) }}</b>
            教师：{{ teacherUser?.name || '已登录' }}
          </span>
        </div>
      </header>

      <section class="hero-section">
        <div>
          <p class="eyebrow">GENERATION TASK CENTER</p>
          <h1>全局任务进度中心</h1>
          <p>
            PPT、Word、数字人微课和迭代修改可以在后台继续生成。你可以回到工作台继续对话，任务完成后在这里或资源库下载。
          </p>
        </div>
        <div class="hero-stats">
          <div>
            <span>进行中</span>
            <strong>{{ runningCount }}</strong>
          </div>
          <div>
            <span>已完成</span>
            <strong>{{ completedCount }}</strong>
          </div>
          <div>
            <span>失败</span>
            <strong>{{ failedCount }}</strong>
          </div>
        </div>
      </section>

      <section class="filter-panel">
        <div class="tabs">
          <button
            v-for="item in STATUS_TABS"
            :key="item.value"
            type="button"
            :class="['tab-btn', activeStatus === item.value ? 'is-active' : '']"
            @click="selectStatus(item.value)"
          >
            <span>{{ item.label }}</span>
            <em>{{ getCount(item.value) }}</em>
          </button>
        </div>
        <button type="button" class="refresh-btn" @click="fetchTasks()">刷新任务</button>
      </section>

      <p v-if="error" class="error-text">{{ error }}</p>

      <section v-if="loading" class="empty-card">
        <span class="loader"></span>
        <p>正在读取任务状态...</p>
      </section>

      <section v-else-if="!tasks.length" class="empty-card">
        <div class="empty-icon"></div>
        <h2>暂无任务</h2>
        <p>从工作台提交 PPT、Word 或微课生成后，任务会自动出现在这里。</p>
      </section>

      <section v-else class="task-list">
        <article v-for="task in tasks" :key="task.id" class="task-card" :class="getStatusTone(task.status)">
          <div class="task-icon">{{ getTypeIcon(task) }}</div>
          <div class="task-main">
            <div class="task-title-row">
              <div>
                <p>{{ task.type_label }}</p>
                <h2>{{ task.title }}</h2>
              </div>
              <span class="status-pill">{{ getStatusLabel(task.status) }}</span>
            </div>
            <div class="progress-track">
              <span :style="{ width: `${Math.max(4, Number(task.progress || 0))}%` }"></span>
            </div>
            <div class="task-meta">
              <span>{{ task.stage || getStatusLabel(task.status) }}</span>
              <span>{{ Number(task.progress || 0) }}%</span>
              <span>创建：{{ formatDate(task.created_at) }}</span>
            </div>
            <p v-if="task.error" class="task-error">{{ task.error }}</p>
          </div>
          <div class="task-actions">
            <button
              v-if="canOpenMicrocourseTask(task)"
              type="button"
              class="primary-btn micro-open-btn"
              @click="openTaskMicroLesson(task)"
            >
              打开微课
            </button>
            <button
              type="button"
              class="primary-btn"
              :disabled="task.status !== 'completed' || !getDownloadUrl(task)"
              @click="downloadTask(task)"
            >
              下载结果
            </button>
            <button type="button" class="ghost-btn small" @click="goResources">资源库</button>
          </div>
        </article>
      </section>
    </main>
  </div>
</template>

<style scoped>
.task-page {
  position: relative;
  min-height: 100vh;
  overflow-x: hidden;
  color: #eef6ff;
  background: linear-gradient(120deg, #06183d 0%, #081240 48%, #18052e 100%);
  font-family: Inter, "PingFang SC", "Microsoft YaHei", system-ui, sans-serif;
}

.task-bg,
.task-bg > i {
  position: fixed;
  inset: 0;
  pointer-events: none;
}

.star-field {
  background-image:
    radial-gradient(circle, rgba(255, 255, 255, .78) 0 1px, transparent 1.5px),
    radial-gradient(circle, rgba(71, 188, 255, .7) 0 1px, transparent 1.4px);
  background-size: 160px 160px, 230px 230px;
  opacity: .26;
  animation: drift 28s linear infinite;
}

.grid-plane {
  top: auto;
  left: -16%;
  right: -16%;
  bottom: -130px;
  height: 360px;
  background:
    linear-gradient(rgba(56, 189, 248, .12) 1px, transparent 1px),
    linear-gradient(90deg, rgba(139, 92, 246, .12) 1px, transparent 1px);
  background-size: 52px 52px;
  transform: perspective(780px) rotateX(64deg);
  transform-origin: bottom;
  mask-image: linear-gradient(to top, rgba(0, 0, 0, .84), transparent 84%);
}

.aurora {
  height: 420px;
  top: 160px;
  background:
    repeating-linear-gradient(108deg, transparent 0 40px, rgba(56, 189, 248, .1) 41px, transparent 43px),
    radial-gradient(ellipse at 72% 40%, rgba(127, 86, 255, .34), transparent 62%),
    radial-gradient(ellipse at 20% 58%, rgba(34, 211, 238, .22), transparent 56%);
  opacity: .56;
  transform: skewY(-7deg);
  mask-image: linear-gradient(90deg, transparent, #000 16%, #000 84%, transparent);
}

.aurora-two {
  top: 300px;
  opacity: .28;
  transform: skewY(9deg);
}

.task-shell {
  position: relative;
  z-index: 1;
  width: min(1560px, calc(100% - 72px));
  margin: 0 auto;
  padding: 34px 0 60px;
}

.task-header,
.filter-panel,
.task-card,
.empty-card,
.hero-stats {
  border: 1px solid rgba(156, 181, 255, .18);
  background: linear-gradient(145deg, rgba(18, 34, 82, .8), rgba(6, 12, 39, .64));
  box-shadow: 0 24px 70px rgba(1, 8, 28, .36), inset 0 1px 0 rgba(255, 255, 255, .06);
  backdrop-filter: blur(18px);
}

.task-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 16px 24px;
  border-radius: 24px;
}

.brand-btn,
.ghost-btn,
.refresh-btn,
.primary-btn {
  border: 0;
  color: inherit;
  font: inherit;
  cursor: pointer;
}

.brand-btn {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  background: transparent;
  font-size: 20px;
  font-weight: 900;
}

.brand-logo {
  position: relative;
  width: 34px;
  height: 38px;
}

.brand-logo::before,
.brand-logo i {
  content: '';
  position: absolute;
  width: 18px;
  height: 32px;
  border-radius: 7px;
}

.brand-logo::before {
  left: 0;
  top: 3px;
  background: linear-gradient(180deg, #22d3ee, #4f7cff);
}

.brand-logo i:first-child {
  left: 9px;
  top: 0;
  background: linear-gradient(180deg, #72f6a4, #37a7ff);
}

.brand-logo i:last-child {
  left: 16px;
  top: 7px;
  background: linear-gradient(180deg, #ffd36e, #c653ff);
}

.header-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.ghost-btn,
.refresh-btn {
  border-radius: 999px;
  border: 1px solid rgba(156, 181, 255, .2);
  background: rgba(5, 11, 34, .56);
  padding: 10px 16px;
  color: #dbeafe;
}

.ghost-btn.small {
  padding: 9px 13px;
}

.teacher-pill {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  border-radius: 999px;
  border: 1px solid rgba(156, 181, 255, .22);
  background: rgba(5, 11, 34, .58);
  padding: 7px 14px 7px 7px;
  color: #dbeafe;
  font-weight: 700;
}

.teacher-pill b {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 999px;
  background: linear-gradient(135deg, #35d6ff, #7c3cff);
}

.hero-section {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
  gap: 28px;
  padding: 70px 8px 42px;
}

.eyebrow {
  margin: 0 0 18px;
  color: #5ee7ff;
  letter-spacing: .38em;
  font-size: 13px;
  font-weight: 900;
}

.hero-section h1 {
  margin: 0;
  font-size: clamp(42px, 5vw, 76px);
  line-height: 1;
  font-weight: 950;
}

.hero-section p {
  max-width: 760px;
  margin: 22px 0 0;
  color: #b8c8e8;
  font-size: 17px;
  line-height: 1.9;
}

.hero-stats {
  display: grid;
  grid-template-columns: repeat(3, 120px);
  gap: 12px;
  padding: 18px;
  border-radius: 26px;
}

.hero-stats div {
  border-radius: 18px;
  background: rgba(3, 8, 31, .58);
  padding: 18px;
}

.hero-stats span {
  display: block;
  color: #91a4c8;
  font-size: 13px;
  font-weight: 700;
}

.hero-stats strong {
  display: block;
  margin-top: 12px;
  font-size: 34px;
  line-height: 1;
}

.filter-panel {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 18px;
  border-radius: 24px;
  padding: 18px;
}

.tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.tab-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border-radius: 999px;
  border: 1px solid rgba(156, 181, 255, .2);
  background: rgba(4, 10, 32, .66);
  color: #c7d2fe;
  padding: 10px 14px;
  font-weight: 800;
}

.tab-btn.is-active {
  color: white;
  border-color: rgba(56, 189, 248, .65);
  background: linear-gradient(135deg, #25c8ff, #743cff);
}

.tab-btn em {
  min-width: 24px;
  border-radius: 999px;
  background: rgba(255, 255, 255, .13);
  padding: 2px 7px;
  font-style: normal;
}

.error-text {
  margin: 18px 0 0;
  color: #fecdd3;
  font-weight: 700;
}

.empty-card {
  display: grid;
  place-items: center;
  min-height: 340px;
  margin-top: 22px;
  border-radius: 28px;
  text-align: center;
}

.loader {
  width: 28px;
  height: 28px;
  border-radius: 999px;
  border: 3px solid rgba(255, 255, 255, .2);
  border-top-color: #38bdf8;
  animation: spin 1s linear infinite;
}

.empty-icon {
  width: 72px;
  height: 72px;
  border-radius: 24px;
  background: linear-gradient(135deg, rgba(56, 189, 248, .9), rgba(124, 58, 237, .9));
  box-shadow: 0 18px 45px rgba(56, 189, 248, .22);
}

.task-list {
  display: grid;
  gap: 16px;
  margin-top: 22px;
}

.task-card {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr) auto;
  align-items: center;
  gap: 18px;
  border-radius: 24px;
  padding: 18px;
}

.task-icon {
  display: grid;
  place-items: center;
  width: 58px;
  height: 58px;
  border-radius: 18px;
  background: linear-gradient(135deg, rgba(56, 189, 248, .92), rgba(124, 58, 237, .9));
  font-weight: 950;
  font-size: 22px;
}

.task-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.task-title-row p {
  margin: 0 0 4px;
  color: #67e8f9;
  font-size: 12px;
  font-weight: 900;
  letter-spacing: .18em;
}

.task-title-row h2 {
  margin: 0;
  font-size: 20px;
}

.status-pill {
  flex: 0 0 auto;
  border-radius: 999px;
  padding: 7px 11px;
  background: rgba(255, 255, 255, .1);
  color: #dbeafe;
  font-size: 12px;
  font-weight: 800;
}

.progress-track {
  height: 8px;
  margin-top: 14px;
  overflow: hidden;
  border-radius: 999px;
  background: rgba(4, 10, 32, .72);
}

.progress-track span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #2fd3ff, #7c3cff, #f062d4);
  transition: width .35s ease;
}

.task-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 10px;
  color: #9fb1d1;
  font-size: 13px;
}

.task-error {
  margin: 10px 0 0;
  color: #fecdd3;
  font-size: 13px;
}

.task-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.primary-btn {
  border-radius: 14px;
  background: linear-gradient(135deg, #27c8ff, #7043ff);
  color: white;
  padding: 11px 17px;
  font-weight: 900;
}

.primary-btn:disabled {
  cursor: not-allowed;
  opacity: .45;
}

.task-card.is-failed .progress-track span {
  background: linear-gradient(90deg, #fb7185, #f97316);
}

.task-card.is-completed .progress-track span {
  background: linear-gradient(90deg, #34d399, #38bdf8);
}

@keyframes drift {
  to {
    background-position: -220px 260px;
  }
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 900px) {
  .task-shell {
    width: min(100% - 28px, 760px);
  }

  .task-header,
  .hero-section,
  .filter-panel,
  .task-card {
    grid-template-columns: 1fr;
  }

  .hero-stats {
    grid-template-columns: repeat(3, 1fr);
    width: 100%;
  }

  .task-actions {
    justify-content: flex-start;
  }
}
</style>
