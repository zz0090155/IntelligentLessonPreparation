<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { downloadMicroLessonHtml, openMicroLessonWindow } from '../utils/microLesson'

const API_BASE = import.meta.env.VITE_API_BASE || '/api'
const router = useRouter()

const resources = ref([])
const typeCounts = ref({})
const loading = ref(false)
const error = ref('')
const keyword = ref('')
const activeType = ref('all')
const teacherUser = ref(null)
const previewUrls = ref({})
const previewObjectUrls = []

const RESOURCE_TYPES = [
  { value: 'all', label: '全部资源' },
  { value: 'ppt', label: 'PPT课件' },
  { value: 'doc', label: 'Word教案' },
  { value: 'study_outline', label: '复习提纲' },
  { value: 'study_quiz', label: '练习题' },
  { value: 'flashcards', label: '闪卡资料' },
  { value: 'homework', label: '课后作业' },
  { value: 'exercise_sheet', label: '练习单' },
  { value: 'image', label: '课堂配图' },
  { value: 'video', label: '数字人微课' },
  { value: 'game', label: '互动游戏' },
]

const totalCount = computed(() => {
  return Object.values(typeCounts.value || {}).reduce((sum, item) => sum + Number(item || 0), 0)
})

const activeTypeLabel = computed(() => {
  return RESOURCE_TYPES.find((item) => item.value === activeType.value)?.label || '全部资源'
})

function getTeacherToken() {
  return localStorage.getItem('teacher_token') || ''
}

function getAuthHeaders() {
  const token = getTeacherToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
}

function loadTeacherUser() {
  const raw = localStorage.getItem('teacher_user')
  if (!raw) {
    teacherUser.value = null
    return
  }
  try {
    teacherUser.value = JSON.parse(raw)
  } catch (err) {
    teacherUser.value = null
  }
}

function resolveApiUrl(url) {
  const value = String(url || '').trim()
  if (!value) return ''
  if (/^https?:\/\//i.test(value)) return value
  const base = String(API_BASE || '').replace(/\/$/, '')
  return `${base}${value.startsWith('/') ? value : `/${value}`}`
}

function formatDate(value) {
  if (!value) return '--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleString()
}

function formatSize(value) {
  const bytes = Number(value || 0)
  if (!bytes) return '--'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function getTypeCount(type) {
  if (type === 'all') return totalCount.value
  return Number(typeCounts.value?.[type] || 0)
}

function getResourceTone(type) {
  const tones = {
    ppt: 'tone-blue',
    doc: 'tone-purple',
    study_outline: 'tone-cyan',
    study_quiz: 'tone-emerald',
    flashcards: 'tone-violet',
    homework: 'tone-orange',
    exercise_sheet: 'tone-teal',
    image: 'tone-amber',
    video: 'tone-indigo',
    game: 'tone-pink',
  }
  return tones[type] || 'tone-blue'
}

function getResourceInitial(item) {
  const symbolMap = {
    ppt: 'P',
    doc: 'W',
    study_outline: '复',
    study_quiz: '题',
    flashcards: '卡',
    homework: '作',
    exercise_sheet: '练',
    image: '图',
    video: '视',
    game: '玩',
  }
  if (symbolMap[item?.type]) return symbolMap[item.type]
  const label = String(item?.type_label || item?.title || '资源').trim()
  return label.slice(0, 2) || '资源'
}

function clearPreviews() {
  previewObjectUrls.forEach((url) => URL.revokeObjectURL(url))
  previewObjectUrls.length = 0
  previewUrls.value = {}
}

async function loadPptPreview(item) {
  if (!item || item.type !== 'ppt' || !item.page_count || previewUrls.value[item.id]) return
  try {
    const response = await fetch(resolveApiUrl(`/artifact/${item.id}/preview/1`), {
      headers: getAuthHeaders(),
    })
    if (!response.ok) return
    const blob = await response.blob()
    const objectUrl = URL.createObjectURL(blob)
    previewObjectUrls.push(objectUrl)
    previewUrls.value = { ...previewUrls.value, [item.id]: objectUrl }
  } catch (err) {
    // Preview is optional; leave the resource card usable if it fails.
  }
}

async function fetchResources() {
  loading.value = true
  error.value = ''
  clearPreviews()
  try {
    const params = new URLSearchParams({
      limit: '120',
      offset: '0',
    })
    if (activeType.value && activeType.value !== 'all') {
      params.set('type', activeType.value)
    }
    if (keyword.value.trim()) {
      params.set('q', keyword.value.trim())
    }
    const response = await fetch(resolveApiUrl(`/resources/artifacts?${params.toString()}`), {
      headers: getAuthHeaders(),
    })
    const data = await response.json().catch(() => ({}))
    if (!response.ok) {
      throw new Error(data.detail || data.message || `HTTP ${response.status}`)
    }
    resources.value = Array.isArray(data.items) ? data.items : []
    typeCounts.value = data.type_counts || {}
    resources.value.slice(0, 8).forEach((item) => {
      loadPptPreview(item)
    })
  } catch (err) {
    error.value = err instanceof Error ? err.message : '资源加载失败'
    resources.value = []
  } finally {
    loading.value = false
  }
}

async function downloadResource(item) {
  if (!item?.download_url) return
  try {
    const response = await fetch(resolveApiUrl(item.download_url), {
      headers: getAuthHeaders(),
    })
    const data = await response.blob()
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }
    const url = URL.createObjectURL(data)
    const link = document.createElement('a')
    link.href = url
    link.download = item.download_filename || `${item.type_label || 'resource'}_${item.id}`
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
  } catch (err) {
    error.value = `下载失败：${err instanceof Error ? err.message : '未知错误'}`
  }
}

function isMicrocourseResource(item) {
  return item?.type === 'video'
}

function buildMicrocourseMessageFromResource(item) {
  const metadata = item?.metadata || {}
  const videoUrl = metadata.video_url || metadata.videoUrl || item?.download_url || ''
  const scriptText = metadata.script_text || metadata.scriptText || ''
  return {
    type: 'video',
    title: item?.title || '数字人微课',
    text: scriptText,
    scriptText,
    videoUrl: resolveApiUrl(videoUrl),
    microcourseJson: metadata.microcourse_json || metadata.microcourseJson || null,
    scenePreviews: metadata.scene_previews || metadata.scenePreviews || [],
    boardContent: metadata.board_content || metadata.boardContent || [],
  }
}

function canOpenMicrocourseResource(item) {
  return isMicrocourseResource(item) && Boolean(buildMicrocourseMessageFromResource(item).videoUrl)
}

function openResourceMicroLesson(item) {
  if (!canOpenMicrocourseResource(item)) return
  openMicroLessonWindow(buildMicrocourseMessageFromResource(item))
}

function downloadResourceMicroLesson(item) {
  if (!canOpenMicrocourseResource(item)) return
  downloadMicroLessonHtml(buildMicrocourseMessageFromResource(item))
}

function goTeacher() {
  router.push('/teacher')
}

function goIterations() {
  router.push('/teacher/iterations')
}

watch([activeType], fetchResources)

onMounted(() => {
  loadTeacherUser()
  fetchResources()
})

onBeforeUnmount(() => {
  clearPreviews()
})
</script>

<template>
  <div class="resource-page">
    <div class="resource-bg" aria-hidden="true">
      <i class="grid-plane"></i>
      <i class="light-rail light-rail--one"></i>
      <i class="light-rail light-rail--two"></i>
      <i class="star-field"></i>
    </div>

    <main class="resource-shell">
      <header class="resource-header">
        <button type="button" class="brand-btn" @click="goTeacher">
          <span class="brand-logo"><i></i><i></i></span>
          <span>教策云枢</span>
        </button>
        <div class="header-actions">
          <button type="button" class="ghost-btn" @click="goIterations">迭代修改</button>
          <span class="teacher-pill">
            <span>{{ (teacherUser?.name || 'yy').slice(0, 2) }}</span>
            教师：{{ teacherUser?.name || '已登录' }}
          </span>
          <button type="button" class="ghost-btn" @click="goTeacher">返回工作台</button>
        </div>
      </header>

      <section class="hero-section">
        <div class="hero-copy-block">
          <p class="eyebrow">RESOURCE LIBRARY</p>
          <h1>资源生成管理</h1>
          <p class="hero-copy">
            查看当前账号在备课过程中生成的课件、教案、学习资料和互动资源，随时找回、复用和下载。
          </p>
        </div>

        <div class="hero-visual" aria-hidden="true">
          <div class="resource-cube">
            <span></span>
            <span></span>
            <span></span>
          </div>
          <div class="orbit-ring"></div>
          <div class="hero-stats">
            <div>
              <span>总资源</span>
              <strong>{{ totalCount }}</strong>
            </div>
            <div>
              <span>{{ activeTypeLabel }}</span>
              <strong>{{ resources.length }}</strong>
            </div>
          </div>
        </div>
      </section>

      <section class="filter-panel">
        <div class="filter-head">
          <div>
            <p>资源类型</p>
            <strong>{{ activeTypeLabel }}</strong>
          </div>
          <div class="search-box">
            <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                d="M21 21l-4.3-4.3M10.8 18a7.2 7.2 0 100-14.4 7.2 7.2 0 000 14.4z" />
            </svg>
            <input
              v-model="keyword"
              placeholder="搜索标题、对话、教学计划"
              @keyup.enter="fetchResources"
            />
            <button type="button" @click="fetchResources">搜索</button>
          </div>
        </div>
        <div class="type-tabs">
          <button
            v-for="item in RESOURCE_TYPES"
            :key="item.value"
            type="button"
            :class="['type-tab', activeType === item.value ? 'is-active' : '']"
            @click="activeType = item.value"
          >
            <span>{{ item.label }}</span>
            <em>{{ getTypeCount(item.value) }}</em>
          </button>
        </div>
      </section>

      <p v-if="error" class="error-text">{{ error }}</p>

      <section v-if="loading" class="empty-state">
        <span class="loader"></span>
        <p>正在加载资源库...</p>
      </section>

      <section v-else-if="!resources.length" class="empty-state">
        <div class="empty-icon"></div>
        <h2>暂无生成资源</h2>
        <p>生成 PPT、Word 教案、学习资料或配图后，会自动保存到这里。</p>
      </section>

      <section v-else class="resource-grid">
        <article
          v-for="item in resources"
          :key="item.id"
          class="resource-card"
          :class="getResourceTone(item.type)"
        >
          <div class="card-preview">
            <div class="preview-topline">
              <span>{{ item.type_label }}</span>
              <em>{{ formatSize(item.file_size) }}</em>
            </div>
            <div class="preview-stage">
              <img v-if="previewUrls[item.id]" :src="previewUrls[item.id]" alt="PPT 预览" />
              <div v-else class="preview-symbol">
                <span>{{ getResourceInitial(item) }}</span>
              </div>
            </div>
          </div>

          <div class="card-body">
            <div class="card-title-row">
              <span class="type-badge">{{ item.type_label }}</span>
              <span class="time-line">{{ formatDate(item.created_at) }}</span>
            </div>
            <h2 :title="item.title">{{ item.title }}</h2>
            <p class="meta-line">
              {{ item.conversation_title || item.conversation_key || '未关联对话' }}
              <span v-if="item.plan_title || item.plan_id"> · {{ item.plan_title || `Plan #${item.plan_id}` }}</span>
            </p>
          </div>

          <div class="card-actions">
            <button
              v-if="canOpenMicrocourseResource(item)"
              type="button"
              class="primary-btn micro-open-btn"
              @click="openResourceMicroLesson(item)"
            >
              打开微课
            </button>
            <button type="button" class="primary-btn" :disabled="!item.file_exists" @click="downloadResource(item)">
              <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                  d="M12 4v10m0 0l-4-4m4 4l4-4M5 20h14" />
              </svg>
              <span>下载</span>
            </button>
            <button
              v-if="canOpenMicrocourseResource(item)"
              type="button"
              class="ghost-btn small"
              @click="downloadResourceMicroLesson(item)"
            >
              下载微课页
            </button>
            <button
              v-if="item.type === 'ppt'"
              type="button"
              class="ghost-btn small"
              @click="router.push(`/teacher?artifact=${item.id}`)"
            >
              回工作台
            </button>
          </div>
        </article>
      </section>
    </main>
  </div>
</template>

<style scoped>
.resource-page {
  position: relative;
  min-height: 100vh;
  overflow-x: hidden;
  color: #eef5ff;
  background:
    linear-gradient(118deg, #06193d 0%, #0a1744 42%, #150630 100%);
  font-family:
    Inter, "PingFang SC", "Microsoft YaHei", system-ui, -apple-system, BlinkMacSystemFont, sans-serif;
}

.resource-bg,
.resource-bg > i {
  position: fixed;
  inset: 0;
  pointer-events: none;
}

.resource-bg {
  overflow: hidden;
  background:
    linear-gradient(180deg, rgba(9, 31, 78, 0.92), rgba(7, 13, 40, 0.34) 42%, rgba(8, 3, 28, 0.76)),
    conic-gradient(from 218deg at 78% 18%, rgba(123, 92, 255, 0.36), transparent 22%, rgba(33, 188, 255, 0.3), transparent 54%, rgba(14, 165, 233, 0.28));
}

.star-field {
  background-image:
    radial-gradient(circle, rgba(255, 255, 255, 0.8) 0 1px, transparent 1.6px),
    radial-gradient(circle, rgba(96, 165, 250, 0.75) 0 1px, transparent 1.4px);
  background-size: 170px 170px, 236px 236px;
  opacity: 0.28;
  animation: starDrift 28s linear infinite;
}

.grid-plane {
  top: auto;
  left: -18%;
  right: -18%;
  bottom: -110px;
  height: 380px;
  background:
    linear-gradient(rgba(56, 189, 248, 0.12) 1px, transparent 1px),
    linear-gradient(90deg, rgba(139, 92, 246, 0.12) 1px, transparent 1px);
  background-size: 54px 54px;
  transform: perspective(760px) rotateX(62deg);
  transform-origin: center bottom;
  mask-image: linear-gradient(to top, rgba(0, 0, 0, 0.9), transparent 82%);
}

.light-rail {
  height: 260px;
  top: 310px;
  opacity: 0.42;
  background:
    repeating-linear-gradient(108deg, transparent 0 35px, rgba(56, 189, 248, 0.12) 36px, transparent 38px),
    linear-gradient(90deg, transparent, rgba(34, 211, 238, 0.2), rgba(168, 85, 247, 0.16), transparent);
  transform: skewY(-8deg);
  filter: blur(0.2px);
}

.light-rail--two {
  top: 250px;
  transform: skewY(9deg) translateX(7%);
  opacity: 0.28;
}

.resource-shell {
  position: relative;
  z-index: 1;
  width: min(1560px, calc(100% - 72px));
  margin: 0 auto;
  padding: 34px 0 60px;
}

.resource-header,
.filter-panel,
.resource-card,
.empty-state,
.hero-stats {
  border: 1px solid rgba(156, 181, 255, 0.18);
  background:
    linear-gradient(145deg, rgba(18, 34, 82, 0.78), rgba(6, 12, 39, 0.64));
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.1),
    0 34px 100px -66px rgba(51, 144, 255, 0.86);
  backdrop-filter: blur(24px);
}

.resource-header {
  height: 76px;
  border-radius: 24px;
  padding: 0 18px 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.brand-btn,
.header-actions,
.teacher-pill,
.type-tab,
.search-box,
.card-title-row,
.card-actions,
.primary-btn {
  display: flex;
  align-items: center;
}

.brand-btn {
  gap: 14px;
  color: #fff;
  font-size: 21px;
  font-weight: 900;
}

.brand-logo {
  position: relative;
  width: 38px;
  height: 38px;
  transform-style: preserve-3d;
}

.brand-logo::before,
.brand-logo i {
  content: '';
  position: absolute;
  width: 21px;
  height: 31px;
  border-radius: 8px;
  box-shadow: 0 18px 38px -22px rgba(56, 189, 248, 0.95);
}

.brand-logo::before {
  left: 0;
  top: 4px;
  background: linear-gradient(150deg, #3fe0ff, #5578ff);
}

.brand-logo i:first-child {
  left: 9px;
  top: 0;
  background: linear-gradient(150deg, #75f2bd, #38bdf8);
}

.brand-logo i:last-child {
  left: 17px;
  top: 7px;
  background: linear-gradient(150deg, #8b5cf6, #f15beb);
}

.header-actions {
  gap: 12px;
}

.teacher-pill,
.ghost-btn,
.primary-btn,
.search-box button {
  height: 44px;
  border-radius: 15px;
  border: 1px solid rgba(161, 180, 230, 0.2);
  background: rgba(6, 11, 35, 0.5);
  color: rgba(235, 242, 255, 0.92);
  padding: 0 16px;
  font-size: 14px;
  font-weight: 850;
  white-space: nowrap;
  transition:
    transform 0.22s ease,
    border-color 0.22s ease,
    background 0.22s ease,
    box-shadow 0.22s ease;
}

.teacher-pill {
  gap: 10px;
}

.teacher-pill span {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border-radius: 11px;
  color: #fff;
  font-size: 12px;
  background: linear-gradient(145deg, #33d6ff, #7653ff);
}

.ghost-btn:hover,
.primary-btn:hover,
.search-box button:hover {
  transform: translateY(-1px);
  border-color: rgba(125, 211, 252, 0.48);
}

.hero-section {
  min-height: 310px;
  padding: 66px 6px 48px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 430px;
  gap: 52px;
  align-items: center;
}

.hero-copy-block {
  min-width: 0;
}

.eyebrow {
  margin: 0;
  color: #5deaff;
  font-size: 13px;
  font-weight: 900;
  letter-spacing: 0.42em;
}

.hero-section h1 {
  margin: 18px 0 0;
  color: #fff;
  font-size: clamp(54px, 5.4vw, 88px);
  font-weight: 950;
  line-height: 1.05;
  letter-spacing: 0;
  text-shadow:
    0 10px 34px rgba(37, 99, 235, 0.34),
    0 36px 110px rgba(56, 189, 248, 0.28);
}

.hero-copy {
  max-width: 760px;
  margin: 22px 0 0;
  color: rgba(225, 235, 255, 0.78);
  font-size: 17px;
  line-height: 1.85;
}

.hero-visual {
  position: relative;
  min-height: 220px;
  display: grid;
  place-items: center;
}

.resource-cube {
  position: absolute;
  top: -18px;
  width: 118px;
  height: 118px;
  border-radius: 34px;
  border: 1px solid rgba(155, 176, 255, 0.34);
  background:
    linear-gradient(145deg, rgba(65, 92, 190, 0.38), rgba(14, 24, 76, 0.28));
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.14),
    0 26px 80px -38px rgba(91, 141, 255, 0.94);
  animation: cubeFloat 5.2s ease-in-out infinite;
}

.resource-cube span {
  position: absolute;
  width: 40px;
  height: 50px;
  border-radius: 10px;
  box-shadow: 0 18px 38px -20px rgba(56, 189, 248, 0.9);
}

.resource-cube span:nth-child(1) {
  left: 35px;
  top: 31px;
  background: linear-gradient(145deg, #77f4b8, #31c8ff);
}

.resource-cube span:nth-child(2) {
  left: 52px;
  top: 42px;
  background: linear-gradient(145deg, #ff5fb4, #5a7cff);
}

.resource-cube span:nth-child(3) {
  left: 65px;
  top: 52px;
  background: linear-gradient(145deg, #42d8ff, #7c5cff);
}

.orbit-ring {
  position: absolute;
  top: 74px;
  width: 270px;
  height: 62px;
  border: 1px solid rgba(90, 185, 255, 0.3);
  border-left-color: rgba(125, 92, 255, 0.55);
  border-radius: 50%;
  transform: rotate(-10deg);
  filter: drop-shadow(0 0 18px rgba(43, 212, 255, 0.22));
}

.hero-stats {
  width: min(100%, 350px);
  margin-top: 88px;
  border-radius: 26px;
  padding: 18px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.hero-stats div {
  min-height: 116px;
  border-radius: 20px;
  padding: 20px 18px;
  background:
    linear-gradient(180deg, rgba(6, 12, 42, 0.86), rgba(5, 8, 32, 0.74));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.06);
}

.hero-stats span {
  display: block;
  color: rgba(190, 205, 232, 0.62);
  font-size: 13px;
  font-weight: 800;
}

.hero-stats strong {
  display: block;
  margin-top: 18px;
  color: #fff;
  font-size: 42px;
  font-weight: 950;
  line-height: 1;
}

.filter-panel {
  position: sticky;
  top: 18px;
  z-index: 3;
  border-radius: 26px;
  padding: 18px;
}

.filter-head {
  display: grid;
  grid-template-columns: minmax(190px, 1fr) minmax(360px, 520px);
  gap: 18px;
  align-items: center;
  margin-bottom: 16px;
}

.filter-head p,
.filter-head strong {
  margin: 0;
}

.filter-head p {
  color: rgba(173, 194, 230, 0.68);
  font-size: 12px;
  font-weight: 850;
}

.filter-head strong {
  display: block;
  margin-top: 5px;
  color: #fff;
  font-size: 20px;
  font-weight: 950;
}

.type-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.type-tab {
  gap: 8px;
  height: 40px;
  padding: 0 14px;
  border-radius: 14px;
  border: 1px solid rgba(155, 177, 231, 0.18);
  background: rgba(7, 13, 42, 0.52);
  color: rgba(225, 234, 250, 0.78);
  font-size: 13px;
  font-weight: 850;
  white-space: nowrap;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
  transition:
    transform 0.2s ease,
    background 0.2s ease,
    border-color 0.2s ease;
}

.type-tab:hover {
  transform: translateY(-1px);
  border-color: rgba(125, 211, 252, 0.36);
}

.type-tab em {
  min-width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  border-radius: 999px;
  background: rgba(120, 155, 220, 0.16);
  color: #93eaff;
  font-style: normal;
  font-size: 12px;
}

.type-tab.is-active {
  color: #fff;
  border-color: rgba(125, 211, 252, 0.5);
  background: linear-gradient(105deg, #2bd4ff 0%, #377cff 52%, #7b45ff 100%);
  box-shadow: 0 16px 42px -24px rgba(59, 130, 246, 0.96);
}

.type-tab.is-active em {
  color: #fff;
  background: rgba(255, 255, 255, 0.18);
}

.search-box {
  height: 52px;
  gap: 10px;
  padding: 0 8px 0 16px;
  border-radius: 18px;
  border: 1px solid rgba(155, 177, 231, 0.18);
  background: rgba(4, 9, 31, 0.68);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
}

.search-box svg {
  width: 18px;
  height: 18px;
  color: rgba(177, 196, 232, 0.68);
  flex: 0 0 auto;
}

.search-box input {
  width: 100%;
  min-width: 0;
  height: 100%;
  border: 0;
  outline: none;
  background: transparent;
  color: #edf5ff;
  font-size: 14px;
}

.search-box input::placeholder {
  color: rgba(190, 205, 232, 0.48);
}

.primary-btn,
.search-box button {
  color: #fff;
  border-color: rgba(125, 211, 252, 0.36);
  background: linear-gradient(100deg, #2bd4ff, #347cff 50%, #7b2cff);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.18),
    0 18px 42px -26px rgba(56, 189, 248, 0.95);
}

.primary-btn {
  gap: 8px;
  justify-content: center;
}

.primary-btn svg {
  width: 17px;
  height: 17px;
}

.primary-btn:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.ghost-btn.small {
  height: 42px;
  padding: 0 14px;
}

.resource-grid {
  margin-top: 22px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 18px;
}

.resource-card {
  position: relative;
  overflow: hidden;
  min-height: 404px;
  border-radius: 26px;
  display: flex;
  flex-direction: column;
  transform: translateZ(0);
  transition:
    transform 0.24s ease,
    border-color 0.24s ease,
    box-shadow 0.24s ease;
}

.resource-card:hover {
  transform: translateY(-5px);
  border-color: rgba(125, 211, 252, 0.34);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.1),
    0 38px 108px -62px rgba(56, 189, 248, 0.9);
}

.resource-card::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  opacity: 0.82;
  background:
    linear-gradient(135deg, rgba(56, 189, 248, 0.18), transparent 42%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.04), transparent 34%);
}

.resource-card.tone-purple::before {
  background: linear-gradient(135deg, rgba(168, 85, 247, 0.2), transparent 42%);
}

.resource-card.tone-emerald::before,
.resource-card.tone-teal::before {
  background: linear-gradient(135deg, rgba(45, 212, 191, 0.2), transparent 42%);
}

.resource-card.tone-violet::before,
.resource-card.tone-indigo::before {
  background: linear-gradient(135deg, rgba(129, 140, 248, 0.21), transparent 42%);
}

.resource-card.tone-orange::before,
.resource-card.tone-amber::before {
  background: linear-gradient(135deg, rgba(251, 146, 60, 0.2), transparent 42%);
}

.resource-card.tone-pink::before {
  background: linear-gradient(135deg, rgba(236, 72, 153, 0.22), transparent 42%);
}

.card-preview,
.card-body,
.card-actions {
  position: relative;
  z-index: 1;
}

.card-preview {
  margin: 18px 18px 0;
}

.preview-topline {
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: rgba(218, 230, 255, 0.68);
  font-size: 12px;
  font-weight: 850;
}

.preview-topline em {
  color: rgba(165, 243, 252, 0.8);
  font-style: normal;
}

.preview-stage {
  position: relative;
  height: 176px;
  overflow: hidden;
  border-radius: 22px;
  border: 1px solid rgba(157, 181, 233, 0.2);
  background:
    linear-gradient(145deg, rgba(12, 26, 69, 0.85), rgba(3, 8, 29, 0.72));
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.08),
    inset 0 -22px 70px rgba(30, 64, 175, 0.14);
}

.preview-stage::after {
  content: '';
  position: absolute;
  left: 12%;
  right: 12%;
  bottom: -24px;
  height: 54px;
  border-radius: 50%;
  background: rgba(56, 189, 248, 0.12);
  filter: blur(18px);
}

.preview-stage img {
  position: relative;
  z-index: 1;
  width: 100%;
  height: 100%;
  object-fit: cover;
  opacity: 0.94;
}

.preview-symbol {
  position: relative;
  z-index: 1;
  height: 100%;
  display: grid;
  place-items: center;
}

.preview-symbol span {
  width: 82px;
  height: 82px;
  display: grid;
  place-items: center;
  border-radius: 26px;
  color: #fff;
  font-size: 18px;
  font-weight: 950;
  background:
    linear-gradient(145deg, rgba(47, 214, 255, 0.95), rgba(52, 124, 255, 0.9) 50%, rgba(124, 58, 237, 0.96));
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.2),
    0 24px 54px -28px rgba(56, 189, 248, 0.98);
  transform: perspective(380px) rotateX(8deg) rotateY(-10deg);
}

.card-body {
  min-height: 138px;
  padding: 18px 22px 10px;
  flex: 1;
}

.card-title-row {
  justify-content: space-between;
  gap: 12px;
}

.type-badge {
  flex: 0 0 auto;
  height: 30px;
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 0 11px;
  background: rgba(56, 189, 248, 0.14);
  color: #a5f3fc;
  font-size: 12px;
  font-weight: 950;
}

.time-line,
.meta-line {
  color: rgba(203, 216, 241, 0.66);
  font-size: 12px;
}

.time-line {
  flex: 1;
  min-width: 0;
  text-align: right;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.card-body h2 {
  margin: 16px 0 0;
  min-height: 56px;
  color: #fff;
  font-size: 21px;
  font-weight: 950;
  line-height: 1.35;
  letter-spacing: 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.meta-line {
  margin: 12px 0 0;
  line-height: 1.65;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-actions {
  gap: 12px;
  padding: 0 22px 22px;
}

.error-text {
  margin: 16px 0 0;
  border-radius: 16px;
  border: 1px solid rgba(248, 113, 113, 0.28);
  background: rgba(127, 29, 29, 0.24);
  color: #fecdd3;
  padding: 13px 16px;
  font-size: 13px;
}

.empty-state {
  margin-top: 22px;
  min-height: 300px;
  border-radius: 26px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 12px;
  text-align: center;
}

.empty-state h2 {
  margin: 0;
  color: #fff;
  font-size: 25px;
  font-weight: 950;
}

.empty-state p {
  margin: 0;
  color: rgba(203, 213, 225, 0.74);
}

.empty-icon,
.loader {
  width: 62px;
  height: 62px;
  border-radius: 20px;
  background: linear-gradient(145deg, #2bd4ff, #347cff 48%, #7b2cff);
  box-shadow: 0 20px 46px -26px rgba(56, 189, 248, 0.92);
}

.loader {
  border-radius: 999px;
  border: 3px solid rgba(255, 255, 255, 0.18);
  border-top-color: #67e8f9;
  background: transparent;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes starDrift {
  from {
    transform: translate3d(0, 0, 0);
  }

  to {
    transform: translate3d(-80px, 60px, 0);
  }
}

@keyframes cubeFloat {
  0%,
  100% {
    transform: translateY(0) rotateX(0deg) rotateY(0deg);
  }

  50% {
    transform: translateY(-10px) rotateX(4deg) rotateY(-5deg);
  }
}

@media (max-width: 1180px) {
  .resource-shell {
    width: min(100% - 36px, 980px);
  }

  .hero-section {
    grid-template-columns: 1fr;
    gap: 26px;
    padding-top: 46px;
  }

  .hero-visual {
    min-height: 170px;
    justify-items: start;
  }

  .resource-cube {
    left: 20px;
    top: 0;
  }

  .orbit-ring {
    left: 0;
  }

  .hero-stats {
    margin-top: 44px;
    margin-left: 180px;
  }

  .filter-head {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .resource-shell {
    width: min(100% - 24px, 560px);
    padding-top: 18px;
  }

  .resource-header {
    height: auto;
    min-height: 76px;
    flex-direction: column;
    align-items: stretch;
    padding: 16px;
  }

  .header-actions {
    flex-wrap: wrap;
  }

  .teacher-pill,
  .ghost-btn {
    flex: 1 1 160px;
    justify-content: center;
  }

  .hero-section h1 {
    font-size: 46px;
  }

  .hero-copy {
    font-size: 15px;
  }

  .hero-visual {
    display: none;
  }

  .filter-panel {
    position: relative;
    top: auto;
  }

  .search-box {
    height: auto;
    min-height: 52px;
    flex-wrap: wrap;
    padding: 10px;
  }

  .search-box input {
    flex: 1 1 180px;
    height: 38px;
  }

  .search-box button {
    flex: 0 0 86px;
  }

  .resource-grid {
    grid-template-columns: 1fr;
  }
}
</style>
