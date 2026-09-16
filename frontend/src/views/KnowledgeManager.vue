<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

const API_BASE = import.meta.env.VITE_API_BASE || '/api'
const router = useRouter()

const docs = ref([])
const conversations = ref([])
const loading = ref(false)
const uploading = ref(false)
const error = ref('')
const keyword = ref('')
const activeScope = ref('all')
const activeFiletype = ref('all')
const scopeCounts = ref({})
const filetypeCounts = ref({})
const teacherUser = ref(null)

const uploadFile = ref(null)
const uploadScope = ref('user')
const uploadConversationKey = ref('')
const uploadSubject = ref('')
const uploadGrade = ref('')
const uploadUnit = ref('')
const uploadTags = ref('')

const SCOPE_TABS = [
  { value: 'all', label: '全部资料', hint: '当前账号可用的所有上传资料' },
  { value: 'user', label: '教师全局资料', hint: '跨对话复用，适合作为常用教材库' },
  { value: 'session', label: '对话专用资料', hint: '只服务指定备课对话，避免串课' },
  { value: 'material', label: '历史教材资料', hint: '旧版或未标注范围的资料' },
]

const totalCount = computed(() => {
  return Object.values(scopeCounts.value || {}).reduce((sum, value) => sum + Number(value || 0), 0)
})

const activeScopeLabel = computed(() => {
  return SCOPE_TABS.find((item) => item.value === activeScope.value)?.label || '全部资料'
})

const filetypeOptions = computed(() => {
  const values = Object.entries(filetypeCounts.value || {})
    .filter(([, count]) => Number(count || 0) > 0)
    .map(([value, count]) => ({ value, label: value.toUpperCase(), count: Number(count || 0) }))
  return [{ value: 'all', label: '全部格式', count: totalCount.value }, ...values]
})

const globalCount = computed(() => Number(scopeCounts.value?.user || 0))
const sessionCount = computed(() => Number(scopeCounts.value?.session || 0))

function getTeacherToken() {
  return localStorage.getItem('teacher_token') || ''
}

function getAuthHeaders(json = false) {
  const headers = {}
  const token = getTeacherToken()
  if (token) headers.Authorization = `Bearer ${token}`
  if (json) headers['Content-Type'] = 'application/json'
  return headers
}

function resolveApiUrl(url) {
  const value = String(url || '').trim()
  if (!value) return ''
  if (/^https?:\/\//i.test(value)) return value
  const base = String(API_BASE || '').replace(/\/$/, '')
  return `${base}${value.startsWith('/') ? value : `/${value}`}`
}

function loadTeacherUser() {
  const raw = localStorage.getItem('teacher_user')
  if (!raw) return
  try {
    teacherUser.value = JSON.parse(raw)
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

function formatSize(value) {
  const bytes = Number(value || 0)
  if (!bytes) return '--'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function getScopeCount(scope) {
  if (scope === 'all') return totalCount.value
  return Number(scopeCounts.value?.[scope] || 0)
}

function getScopeTone(scope) {
  const tones = {
    user: 'tone-global',
    session: 'tone-session',
    base: 'tone-base',
    material: 'tone-material',
  }
  return tones[scope] || 'tone-material'
}

function getFileSymbol(filetype) {
  const value = String(filetype || '').toLowerCase()
  if (value === 'pdf') return 'PDF'
  if (value === 'docx') return 'W'
  if (value === 'pptx') return 'P'
  if (value === 'txt') return 'TXT'
  if (['png', 'jpg', 'jpeg', 'webp', 'bmp'].includes(value)) return '图'
  if (['mp4', 'mov', 'avi'].includes(value)) return '视'
  if (['mp3', 'wav', 'm4a', 'aac'].includes(value)) return '音'
  return '资料'
}

function getDocTags(doc) {
  const tags = []
  if (doc.grade_stage) tags.push(doc.grade_stage)
  if (doc.subject) tags.push(doc.subject)
  if (doc.unit_text) tags.push(`《${doc.unit_text}》`)
  if (Array.isArray(doc.topic_tags)) tags.push(...doc.topic_tags.slice(0, 3))
  return tags
}

async function fetchConversations() {
  try {
    const response = await fetch(resolveApiUrl('/teacher/conversations'), {
      headers: getAuthHeaders(),
    })
    const data = await response.json().catch(() => ({}))
    if (response.ok) {
      conversations.value = Array.isArray(data.conversations) ? data.conversations : []
      if (!uploadConversationKey.value && conversations.value[0]) {
        uploadConversationKey.value = conversations.value[0].conversation_key
      }
    }
  } catch (err) {
    conversations.value = []
  }
}

async function fetchDocs() {
  loading.value = true
  error.value = ''
  try {
    const params = new URLSearchParams({ limit: '220', offset: '0' })
    if (activeScope.value !== 'all') params.set('scope', activeScope.value)
    if (activeFiletype.value !== 'all') params.set('filetype', activeFiletype.value)
    if (keyword.value.trim()) params.set('q', keyword.value.trim())
    const response = await fetch(resolveApiUrl(`/kb/documents?${params.toString()}`), {
      headers: getAuthHeaders(),
    })
    const data = await response.json().catch(() => ({}))
    if (!response.ok) throw new Error(data.detail || data.message || `HTTP ${response.status}`)
    docs.value = Array.isArray(data.items) ? data.items : []
    scopeCounts.value = data.scope_counts || {}
    filetypeCounts.value = data.filetype_counts || {}
  } catch (err) {
    error.value = err instanceof Error ? err.message : '知识库加载失败'
    docs.value = []
  } finally {
    loading.value = false
  }
}

function handleFileChange(event) {
  uploadFile.value = event.target.files?.[0] || null
}

async function uploadMaterial() {
  if (!uploadFile.value) {
    error.value = '请先选择要上传的教材或参考资料'
    return
  }
  if (uploadScope.value === 'session' && !uploadConversationKey.value) {
    error.value = '上传到对话专用资料时，请先选择一个对话'
    return
  }
  uploading.value = true
  error.value = ''
  try {
    const form = new FormData()
    form.append('file', uploadFile.value)
    form.append('kb_scope', uploadScope.value)
    if (uploadScope.value === 'session') form.append('conversation_key', uploadConversationKey.value)
    if (uploadSubject.value.trim()) form.append('subject', uploadSubject.value.trim())
    if (uploadGrade.value.trim()) form.append('grade_stage', uploadGrade.value.trim())
    if (uploadUnit.value.trim()) form.append('unit_text', uploadUnit.value.trim())
    if (uploadTags.value.trim()) form.append('topic_tags', uploadTags.value.trim())
    const response = await fetch(resolveApiUrl('/kb/upload'), {
      method: 'POST',
      headers: getAuthHeaders(),
      body: form,
    })
    const data = await response.json().catch(() => ({}))
    if (!response.ok) throw new Error(data.detail || data.message || `HTTP ${response.status}`)
    uploadFile.value = null
    await fetchDocs()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '上传失败'
  } finally {
    uploading.value = false
  }
}

async function downloadDoc(doc) {
  if (!doc?.download_url) return
  try {
    const response = await fetch(resolveApiUrl(doc.download_url), {
      headers: getAuthHeaders(),
    })
    const blob = await response.blob()
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = doc.filename || `kb_doc_${doc.doc_id}`
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
  } catch (err) {
    error.value = `下载失败：${err instanceof Error ? err.message : '未知错误'}`
  }
}

async function deleteDoc(doc) {
  if (!doc?.doc_id) return
  const ok = window.confirm(`确定删除资料「${doc.filename}」吗？删除后该资料不会再作为备课参考。`)
  if (!ok) return
  try {
    const response = await fetch(resolveApiUrl(`/kb/doc/${doc.doc_id}`), {
      method: 'DELETE',
      headers: getAuthHeaders(),
    })
    const data = await response.json().catch(() => ({}))
    if (!response.ok) throw new Error(data.detail || data.message || `HTTP ${response.status}`)
    await fetchDocs()
  } catch (err) {
    error.value = `删除失败：${err instanceof Error ? err.message : '未知错误'}`
  }
}

function goTeacher() {
  router.push('/teacher')
}

function goResources() {
  router.push('/teacher/resources')
}

onMounted(() => {
  loadTeacherUser()
  fetchConversations()
  fetchDocs()
})
</script>

<template>
  <div class="knowledge-page">
    <div class="knowledge-bg" aria-hidden="true">
      <i class="knowledge-grid"></i>
      <i class="knowledge-stars"></i>
      <i class="knowledge-orbit"></i>
    </div>

    <main class="knowledge-shell">
      <header class="knowledge-header">
        <button type="button" class="brand-btn" @click="goTeacher">
          <span class="brand-logo"><i></i><i></i></span>
          <span>教策云枢</span>
        </button>
        <div class="header-actions">
          <button type="button" class="ghost-btn" @click="goResources">资源库</button>
          <span class="teacher-pill">{{ teacherUser?.name || '教师账号' }}</span>
          <button type="button" class="ghost-btn" @click="goTeacher">返回工作台</button>
        </div>
      </header>

      <section class="hero-section">
        <div class="hero-copy">
          <p class="eyebrow">KNOWLEDGE BASE</p>
          <h1>教材与知识库管理</h1>
          <p>
            管理教师备课资料、对话隔离资料和常用参考库，让 AI 在生成教案、课件和学习资料时引用得更准、更有边界。
          </p>
        </div>
        <div class="hero-panel">
          <div class="kernel">
            <span></span><span></span><span></span>
          </div>
          <div class="stat-card">
            <span>全部资料</span>
            <strong>{{ totalCount }}</strong>
          </div>
          <div class="stat-card">
            <span>全局资料</span>
            <strong>{{ globalCount }}</strong>
          </div>
          <div class="stat-card">
            <span>对话专用</span>
            <strong>{{ sessionCount }}</strong>
          </div>
        </div>
      </section>

      <section class="upload-console">
        <div class="upload-title">
          <p>UPLOAD MATERIAL</p>
          <h2>上传并标注资料范围</h2>
        </div>
        <div class="upload-grid">
          <label class="file-drop">
            <input
              type="file"
              accept=".pdf,.txt,.docx,.pptx,.png,.jpg,.jpeg,.webp,.bmp,.mp4,.mov,.avi,.mp3,.wav,.m4a,.aac"
              @change="handleFileChange"
            />
            <span>{{ uploadFile?.name || '选择 PDF / Word / PPT / 图片 / 音视频资料' }}</span>
            <em>{{ uploadFile ? formatSize(uploadFile.size) : '支持教材、课件、课堂图片和讲课音视频' }}</em>
          </label>

          <div class="field-group">
            <label>
              资料范围
              <select v-model="uploadScope">
                <option value="user">教师全局资料（跨对话复用）</option>
                <option value="session">当前对话资料（隔离使用）</option>
              </select>
            </label>
            <label v-if="uploadScope === 'session'">
              关联对话
              <select v-model="uploadConversationKey">
                <option v-for="item in conversations" :key="item.conversation_key" :value="item.conversation_key">
                  {{ item.title || item.conversation_key }}
                </option>
              </select>
            </label>
          </div>

          <div class="field-row">
            <input v-model="uploadGrade" placeholder="学段/年级，如 高二" />
            <input v-model="uploadSubject" placeholder="学科，如 语文" />
            <input v-model="uploadUnit" placeholder="课题/单元，如 蜀道难" />
            <input v-model="uploadTags" placeholder="标签，用逗号分隔" />
          </div>

          <button type="button" class="primary-btn upload-btn" :disabled="uploading" @click="uploadMaterial">
            {{ uploading ? '正在解析入库...' : '上传到知识库' }}
          </button>
        </div>
      </section>

      <section class="filter-panel">
        <div class="filter-head">
          <div>
            <p>资料范围</p>
            <strong>{{ activeScopeLabel }}</strong>
          </div>
          <div class="search-box">
            <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                d="M21 21l-4.3-4.3M10.8 18a7.2 7.2 0 100-14.4 7.2 7.2 0 000 14.4z" />
            </svg>
            <input v-model="keyword" placeholder="搜索文件名、摘要" @keyup.enter="fetchDocs" />
            <select v-model="activeFiletype" @change="fetchDocs">
              <option v-for="item in filetypeOptions" :key="item.value" :value="item.value">
                {{ item.label }} · {{ item.count }}
              </option>
            </select>
            <button type="button" @click="fetchDocs">搜索</button>
          </div>
        </div>
        <div class="scope-tabs">
          <button
            v-for="item in SCOPE_TABS"
            :key="item.value"
            type="button"
            :class="['scope-tab', activeScope === item.value ? 'is-active' : '']"
            @click="activeScope = item.value; fetchDocs()"
          >
            <span>{{ item.label }}</span>
            <em>{{ getScopeCount(item.value) }}</em>
            <small>{{ item.hint }}</small>
          </button>
        </div>
      </section>

      <p v-if="error" class="error-text">{{ error }}</p>

      <section v-if="loading" class="empty-state">
        <span class="loader"></span>
        <p>正在整理知识库资料...</p>
      </section>

      <section v-else-if="!docs.length" class="empty-state">
        <div class="empty-icon"></div>
        <h2>暂无匹配资料</h2>
        <p>上传教材后，可以在这里查看资料范围、解析状态和引用情况。</p>
      </section>

      <section v-else class="doc-grid">
        <article
          v-for="doc in docs"
          :key="doc.doc_id"
          class="doc-card"
          :class="getScopeTone(doc.scope)"
        >
          <div class="doc-preview">
            <div class="file-symbol">{{ getFileSymbol(doc.filetype) }}</div>
            <div>
              <span>{{ doc.scope_label }}</span>
              <strong>{{ doc.filetype?.toUpperCase() || 'FILE' }}</strong>
            </div>
          </div>

          <div class="doc-body">
            <div class="doc-meta-row">
              <span>{{ formatSize(doc.file_size) }}</span>
              <span>{{ doc.chunk_count }} 个解析片段</span>
              <span>{{ doc.source_link_count }} 次引用</span>
            </div>
            <h2 :title="doc.filename">{{ doc.filename }}</h2>
            <p class="doc-summary">{{ doc.summary || '该资料已解析入库，可作为备课、课件生成和学习资料生成的参考依据。' }}</p>
            <div class="tag-row">
              <span v-for="tag in getDocTags(doc)" :key="tag">{{ tag }}</span>
              <span v-if="doc.conversation_title">对话：{{ doc.conversation_title }}</span>
            </div>
            <p class="date-line">{{ formatDate(doc.created_at) }}</p>
          </div>

          <div class="doc-actions">
            <button type="button" class="primary-btn" :disabled="!doc.file_exists" @click="downloadDoc(doc)">下载原文件</button>
            <button type="button" class="ghost-btn danger" @click="deleteDoc(doc)">删除</button>
          </div>
        </article>
      </section>
    </main>
  </div>
</template>

<style scoped>
.knowledge-page {
  position: relative;
  min-height: 100vh;
  overflow-x: hidden;
  color: #edf5ff;
  background: linear-gradient(118deg, #06193d 0%, #08143e 44%, #16062f 100%);
  font-family: Inter, "PingFang SC", "Microsoft YaHei", system-ui, sans-serif;
}

.knowledge-bg,
.knowledge-bg > i {
  position: fixed;
  inset: 0;
  pointer-events: none;
}

.knowledge-bg {
  overflow: hidden;
  background:
    radial-gradient(circle at 16% 16%, rgba(14, 165, 233, 0.34), transparent 30%),
    radial-gradient(circle at 82% 22%, rgba(124, 58, 237, 0.3), transparent 34%),
    linear-gradient(180deg, rgba(4, 12, 39, 0.76), rgba(8, 3, 31, 0.9));
}

.knowledge-stars {
  background-image:
    radial-gradient(circle, rgba(255, 255, 255, 0.76) 0 1px, transparent 1.6px),
    radial-gradient(circle, rgba(96, 165, 250, 0.68) 0 1px, transparent 1.4px);
  background-size: 172px 172px, 236px 236px;
  opacity: 0.24;
  animation: starDrift 30s linear infinite;
}

.knowledge-grid {
  top: auto;
  left: -18%;
  right: -18%;
  bottom: -120px;
  height: 380px;
  background:
    linear-gradient(rgba(56, 189, 248, 0.12) 1px, transparent 1px),
    linear-gradient(90deg, rgba(139, 92, 246, 0.12) 1px, transparent 1px);
  background-size: 54px 54px;
  transform: perspective(760px) rotateX(62deg);
  transform-origin: center bottom;
  mask-image: linear-gradient(to top, rgba(0, 0, 0, 0.88), transparent 82%);
}

.knowledge-orbit {
  width: 680px;
  height: 680px;
  left: auto;
  right: -160px;
  top: 110px;
  border-radius: 50%;
  border: 1px solid rgba(83, 197, 255, 0.12);
  box-shadow: inset 0 0 90px rgba(86, 92, 255, 0.08);
}

.knowledge-shell {
  position: relative;
  z-index: 1;
  width: min(1560px, calc(100% - 72px));
  margin: 0 auto;
  padding: 34px 0 60px;
}

.knowledge-header,
.upload-console,
.filter-panel,
.doc-card,
.empty-state,
.hero-panel {
  border: 1px solid rgba(156, 181, 255, 0.18);
  background: linear-gradient(145deg, rgba(18, 34, 82, 0.78), rgba(6, 12, 39, 0.64));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.1), 0 34px 100px -66px rgba(51, 144, 255, 0.86);
  backdrop-filter: blur(24px);
}

.knowledge-header {
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
.search-box,
.doc-actions,
.doc-meta-row,
.tag-row,
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
}

.brand-logo::before,
.brand-logo i {
  content: '';
  position: absolute;
  width: 21px;
  height: 31px;
  border-radius: 8px;
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
  display: flex;
  gap: 12px;
}

.teacher-pill,
.ghost-btn,
.primary-btn,
.search-box button,
.search-box select {
  height: 44px;
  border-radius: 15px;
  border: 1px solid rgba(161, 180, 230, 0.2);
  background: rgba(6, 11, 35, 0.5);
  color: rgba(235, 242, 255, 0.92);
  padding: 0 16px;
  font-size: 14px;
  font-weight: 850;
  white-space: nowrap;
}

.primary-btn,
.search-box button {
  color: #fff;
  border-color: rgba(125, 211, 252, 0.36);
  background: linear-gradient(100deg, #2bd4ff, #347cff 50%, #7b2cff);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.18), 0 18px 42px -26px rgba(56, 189, 248, 0.95);
}

.primary-btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.ghost-btn.danger {
  color: #fecdd3;
}

.hero-section {
  min-height: 290px;
  padding: 62px 6px 42px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 430px;
  gap: 52px;
  align-items: center;
}

.eyebrow {
  margin: 0;
  color: #5deaff;
  font-size: 13px;
  font-weight: 900;
  letter-spacing: 0.42em;
}

.hero-copy h1 {
  margin: 18px 0 0;
  color: #fff;
  font-size: clamp(50px, 5vw, 82px);
  font-weight: 950;
  line-height: 1.05;
  letter-spacing: 0;
}

.hero-copy p:not(.eyebrow) {
  max-width: 820px;
  margin: 22px 0 0;
  color: rgba(225, 235, 255, 0.78);
  font-size: 17px;
  line-height: 1.85;
}

.hero-panel {
  position: relative;
  min-height: 210px;
  border-radius: 28px;
  padding: 24px;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  align-items: end;
}

.kernel {
  position: absolute;
  left: 50%;
  top: -44px;
  width: 112px;
  height: 112px;
  border-radius: 34px;
  transform: translateX(-50%);
  border: 1px solid rgba(155, 176, 255, 0.34);
  background: linear-gradient(145deg, rgba(65, 92, 190, 0.38), rgba(14, 24, 76, 0.28));
  animation: cubeFloat 5s ease-in-out infinite;
}

.kernel span {
  position: absolute;
  width: 38px;
  height: 48px;
  border-radius: 10px;
}

.kernel span:nth-child(1) {
  left: 28px;
  top: 32px;
  background: linear-gradient(145deg, #75f2bd, #38bdf8);
}

.kernel span:nth-child(2) {
  left: 48px;
  top: 42px;
  background: linear-gradient(145deg, #7c5cff, #e95de0);
}

.kernel span:nth-child(3) {
  left: 66px;
  top: 52px;
  background: linear-gradient(145deg, #42d8ff, #6d5cff);
}

.stat-card {
  min-height: 106px;
  border-radius: 20px;
  padding: 18px;
  background: rgba(4, 9, 31, 0.58);
}

.stat-card span {
  display: block;
  color: rgba(190, 205, 232, 0.64);
  font-size: 12px;
  font-weight: 850;
}

.stat-card strong {
  display: block;
  margin-top: 18px;
  color: #fff;
  font-size: 38px;
  font-weight: 950;
}

.upload-console,
.filter-panel {
  border-radius: 26px;
  padding: 20px;
}

.upload-title p,
.upload-title h2 {
  margin: 0;
}

.upload-title p {
  color: #5deaff;
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 0.28em;
}

.upload-title h2 {
  margin-top: 6px;
  color: #fff;
  font-size: 22px;
  font-weight: 950;
}

.upload-grid {
  margin-top: 18px;
  display: grid;
  grid-template-columns: minmax(260px, 1.1fr) minmax(260px, 0.9fr);
  gap: 16px;
}

.file-drop {
  min-height: 128px;
  border: 1px dashed rgba(125, 211, 252, 0.38);
  border-radius: 22px;
  padding: 22px;
  display: grid;
  align-content: center;
  gap: 8px;
  background: rgba(3, 8, 30, 0.52);
  cursor: pointer;
}

.file-drop input {
  display: none;
}

.file-drop span {
  color: #fff;
  font-size: 16px;
  font-weight: 900;
}

.file-drop em {
  color: rgba(203, 216, 241, 0.68);
  font-style: normal;
  font-size: 13px;
}

.field-group,
.field-row {
  display: grid;
  gap: 12px;
}

.field-row {
  grid-column: 1 / -1;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.field-group label {
  color: rgba(214, 226, 248, 0.76);
  font-size: 12px;
  font-weight: 850;
}

.field-group select,
.field-row input {
  width: 100%;
  height: 44px;
  margin-top: 7px;
  border-radius: 15px;
  border: 1px solid rgba(161, 180, 230, 0.18);
  background: rgba(4, 9, 31, 0.62);
  color: #edf5ff;
  padding: 0 14px;
  outline: none;
}

.upload-btn {
  width: fit-content;
  min-width: 164px;
}

.filter-panel {
  position: sticky;
  top: 18px;
  z-index: 4;
  margin-top: 22px;
}

.filter-head {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) minmax(360px, 610px);
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

.search-box {
  min-height: 52px;
  gap: 10px;
  padding: 0 8px 0 16px;
  border-radius: 18px;
  border: 1px solid rgba(155, 177, 231, 0.18);
  background: rgba(4, 9, 31, 0.68);
}

.search-box svg {
  width: 18px;
  height: 18px;
  color: rgba(177, 196, 232, 0.68);
}

.search-box input {
  width: 100%;
  min-width: 0;
  height: 48px;
  border: 0;
  outline: none;
  background: transparent;
  color: #edf5ff;
}

.search-box select {
  height: 38px;
  padding: 0 10px;
}

.scope-tabs {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.scope-tab {
  min-height: 96px;
  border-radius: 20px;
  border: 1px solid rgba(155, 177, 231, 0.18);
  background: rgba(7, 13, 42, 0.52);
  color: rgba(225, 234, 250, 0.84);
  padding: 14px;
  text-align: left;
}

.scope-tab span,
.scope-tab em,
.scope-tab small {
  display: block;
}

.scope-tab span {
  color: #fff;
  font-size: 15px;
  font-weight: 950;
}

.scope-tab em {
  margin-top: 8px;
  color: #86efff;
  font-style: normal;
  font-size: 22px;
  font-weight: 950;
}

.scope-tab small {
  margin-top: 6px;
  color: rgba(203, 216, 241, 0.62);
  font-size: 12px;
  line-height: 1.4;
}

.scope-tab.is-active {
  border-color: rgba(125, 211, 252, 0.5);
  background: linear-gradient(135deg, rgba(43, 212, 255, 0.92), rgba(52, 124, 255, 0.78), rgba(123, 44, 255, 0.82));
}

.doc-grid {
  margin-top: 22px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 18px;
}

.doc-card {
  position: relative;
  overflow: hidden;
  min-height: 360px;
  border-radius: 26px;
  display: flex;
  flex-direction: column;
}

.doc-card::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: linear-gradient(135deg, rgba(56, 189, 248, 0.18), transparent 42%);
}

.doc-card.tone-session::before {
  background: linear-gradient(135deg, rgba(139, 92, 246, 0.22), transparent 42%);
}

.doc-card.tone-material::before {
  background: linear-gradient(135deg, rgba(45, 212, 191, 0.16), transparent 42%);
}

.doc-preview,
.doc-body,
.doc-actions {
  position: relative;
  z-index: 1;
}

.doc-preview {
  margin: 18px 18px 0;
  min-height: 118px;
  border-radius: 22px;
  border: 1px solid rgba(157, 181, 233, 0.2);
  background: linear-gradient(145deg, rgba(12, 26, 69, 0.85), rgba(3, 8, 29, 0.72));
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px;
}

.file-symbol {
  width: 76px;
  height: 76px;
  display: grid;
  place-items: center;
  border-radius: 24px;
  color: #fff;
  font-size: 18px;
  font-weight: 950;
  background: linear-gradient(145deg, #2bd4ff, #347cff 50%, #7b2cff);
}

.doc-preview span {
  display: block;
  color: rgba(203, 216, 241, 0.66);
  font-size: 13px;
  font-weight: 850;
}

.doc-preview strong {
  display: block;
  margin-top: 8px;
  color: #fff;
  font-size: 28px;
  font-weight: 950;
}

.doc-body {
  flex: 1;
  padding: 18px 22px 10px;
}

.doc-meta-row {
  gap: 8px;
  flex-wrap: wrap;
}

.doc-meta-row span,
.tag-row span {
  height: 28px;
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 0 10px;
  background: rgba(56, 189, 248, 0.12);
  color: #a5f3fc;
  font-size: 12px;
  font-weight: 850;
}

.doc-body h2 {
  margin: 16px 0 0;
  color: #fff;
  font-size: 21px;
  font-weight: 950;
  line-height: 1.35;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.doc-summary {
  min-height: 54px;
  margin: 12px 0 0;
  color: rgba(203, 216, 241, 0.68);
  font-size: 13px;
  line-height: 1.65;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.tag-row {
  margin-top: 12px;
  gap: 8px;
  flex-wrap: wrap;
}

.date-line {
  margin: 12px 0 0;
  color: rgba(203, 216, 241, 0.58);
  font-size: 12px;
}

.doc-actions {
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

.empty-state h2,
.empty-state p {
  margin: 0;
}

.empty-state h2 {
  color: #fff;
  font-size: 25px;
  font-weight: 950;
}

.empty-state p {
  color: rgba(203, 213, 225, 0.74);
}

.empty-icon,
.loader {
  width: 62px;
  height: 62px;
  border-radius: 20px;
  background: linear-gradient(145deg, #2bd4ff, #347cff 48%, #7b2cff);
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
    transform: translateX(-50%) translateY(0);
  }

  50% {
    transform: translateX(-50%) translateY(-10px);
  }
}

@media (max-width: 1180px) {
  .knowledge-shell {
    width: min(100% - 36px, 980px);
  }

  .hero-section,
  .filter-head,
  .upload-grid {
    grid-template-columns: 1fr;
  }

  .scope-tabs {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .knowledge-shell {
    width: min(100% - 24px, 560px);
    padding-top: 18px;
  }

  .knowledge-header {
    height: auto;
    min-height: 76px;
    flex-direction: column;
    align-items: stretch;
    padding: 16px;
  }

  .header-actions {
    flex-wrap: wrap;
  }

  .hero-panel {
    display: none;
  }

  .hero-copy h1 {
    font-size: 42px;
  }

  .field-row,
  .scope-tabs,
  .doc-grid {
    grid-template-columns: 1fr;
  }

  .filter-panel {
    position: relative;
    top: auto;
  }

  .search-box {
    flex-wrap: wrap;
    padding: 10px;
  }

  .search-box input {
    flex: 1 1 180px;
  }
}
</style>
