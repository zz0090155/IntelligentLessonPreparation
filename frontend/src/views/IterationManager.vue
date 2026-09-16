<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

const API_BASE = import.meta.env.VITE_API_BASE || '/api'
const TASK_API_URL = `${API_BASE}/generation_tasks`
const router = useRouter()

const teacherUser = ref(null)
const resources = ref([])
const typeCounts = ref({})
const previewUrls = ref({})
const selectedId = ref(null)
const activeType = ref('all')
const keyword = ref('')
const instruction = ref('')
const customTitle = ref('')
const loading = ref(false)
const revising = ref(false)
const error = ref('')
const success = ref('')
const resultArtifact = ref(null)
const previewObjectUrls = []

const ITERATION_TYPES = [
  { value: 'all', label: '全部版本' },
  { value: 'ppt', label: 'PPT 课件' },
  { value: 'doc', label: 'Word 教案' },
]

const selectedArtifact = computed(() => {
  return resources.value.find((item) => item.id === selectedId.value) || null
})

const filteredTotal = computed(() => resources.value.length)

const pptCount = computed(() => Number(typeCounts.value?.ppt || 0))
const docCount = computed(() => Number(typeCounts.value?.doc || 0))

function getTeacherToken() {
  return localStorage.getItem('teacher_token') || ''
}

function getAuthHeaders() {
  const token = getTeacherToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
}

function getJsonHeaders() {
  return {
    ...getAuthHeaders(),
    'Content-Type': 'application/json',
  }
}

async function createGenerationTask(taskType, title, payload = {}) {
  const response = await fetch(TASK_API_URL, {
    method: 'POST',
    headers: getJsonHeaders(),
    body: JSON.stringify({
      task_type: taskType,
      title,
      payload,
    }),
  })
  const data = await response.json().catch(() => ({}))
  if (!response.ok) {
    throw new Error(data.detail || data.message || `HTTP ${response.status}`)
  }
  return data.task || null
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

function truncateText(value, length = 54) {
  const text = String(value || '').replace(/\s+/g, ' ').trim()
  if (!text) return ''
  return text.length > length ? `${text.slice(0, length)}...` : text
}

function getTypeCount(type) {
  if (type === 'all') return pptCount.value + docCount.value
  return Number(typeCounts.value?.[type] || 0)
}

function getTypeLabel(item) {
  return item?.type === 'ppt' ? 'PPT 课件' : 'Word 教案'
}

function getFileInitial(item) {
  return item?.type === 'ppt' ? 'P' : 'W'
}

function getVersionLabel(item) {
  const version = Number(item?.version || 1)
  return `V${version}`
}

function getLineageText(item) {
  if (!item?.parent_artifact_id) return '原始生成版本'
  return `基于 #${item.parent_artifact_id} 修订`
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
    // PPT preview is optional.
  }
}

async function fetchResources(preferredId = null) {
  loading.value = true
  error.value = ''
  success.value = ''
  clearPreviews()
  try {
    const params = new URLSearchParams({
      limit: '200',
      offset: '0',
    })
    if (activeType.value !== 'all') {
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
    const items = Array.isArray(data.items) ? data.items : []
    resources.value = items.filter((item) => item.type === 'ppt' || item.type === 'doc')
    typeCounts.value = data.type_counts || {}
    resources.value.slice(0, 10).forEach((item) => loadPptPreview(item))

    const nextSelected = preferredId
      || (selectedId.value && resources.value.some((item) => item.id === selectedId.value) ? selectedId.value : null)
      || resources.value[0]?.id
      || null
    selectedId.value = nextSelected
  } catch (err) {
    error.value = err instanceof Error ? err.message : '版本资源加载失败'
    resources.value = []
    selectedId.value = null
  } finally {
    loading.value = false
  }
}

function selectArtifact(item) {
  selectedId.value = item?.id || null
  success.value = ''
  error.value = ''
  resultArtifact.value = null
  if (item?.type === 'ppt') {
    loadPptPreview(item)
  }
}

async function downloadArtifact(item) {
  if (!item?.download_url) return
  try {
    const response = await fetch(resolveApiUrl(item.download_url), {
      headers: getAuthHeaders(),
    })
    const blob = await response.blob()
    if (!response.ok) {
      const text = await blob.text().catch(() => '')
      throw new Error(text || `HTTP ${response.status}`)
    }
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = item.download_filename || `${getTypeLabel(item)}_${item.id}`
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
  } catch (err) {
    error.value = `下载失败：${err instanceof Error ? err.message : '未知错误'}`
  }
}

async function fetchArtifactDetail(id) {
  if (!id) return null
  const response = await fetch(resolveApiUrl(`/resources/artifacts/${id}`), {
    headers: getAuthHeaders(),
  })
  const data = await response.json().catch(() => ({}))
  if (!response.ok) return null
  return data.artifact || null
}

async function revisePpt(item, text) {
  const task = await createGenerationTask('edit_ppt', `PPT 迭代修改：${item.title || `#${item.id}`}`, {
    artifact_id: item.id,
    conversation_key: item.conversation_key || undefined,
    instruction: text,
  })
  success.value = `PPT 修订任务已提交${task?.id ? `（任务 #${task.id}）` : ''}，可到任务中心查看进度，完成后会进入资源库。`
}

async function reviseDoc(item, text) {
  const task = await createGenerationTask('edit_doc', `Word 迭代修改：${customTitle.value.trim() || item.title || `#${item.id}`}`, {
    artifact_id: item.id,
    instruction: text,
    title: customTitle.value.trim() || undefined,
  })
  success.value = `Word 修订任务已提交${task?.id ? `（任务 #${task.id}）` : ''}，可到任务中心查看进度，完成后会进入资源库。`
}

async function submitRevision() {
  const item = selectedArtifact.value
  const text = instruction.value.trim()
  if (!item) {
    error.value = '请先选择一个 PPT 或 Word 历史版本'
    return
  }
  if (!text) {
    error.value = '请填写本次迭代修改要求'
    return
  }
  revising.value = true
  error.value = ''
  success.value = ''
  resultArtifact.value = null
  try {
    if (item.type === 'ppt') {
      await revisePpt(item, text)
    } else {
      await reviseDoc(item, text)
    }
    instruction.value = ''
    customTitle.value = ''
  } catch (err) {
    error.value = err instanceof Error ? err.message : '迭代生成失败'
  } finally {
    revising.value = false
  }
}

function goTeacher() {
  router.push('/teacher')
}

function goResources() {
  router.push('/teacher/resources')
}

function goTasks() {
  router.push('/teacher/tasks')
}

watch(activeType, () => fetchResources())

onMounted(() => {
  loadTeacherUser()
  fetchResources()
})

onBeforeUnmount(() => {
  clearPreviews()
})
</script>

<template>
  <div class="iteration-page">
    <div class="iteration-bg" aria-hidden="true">
      <i class="star-layer"></i>
      <i class="wave-layer"></i>
      <i class="grid-layer"></i>
    </div>

    <main class="iteration-shell">
      <header class="topbar">
        <button type="button" class="brand" @click="goTeacher">
          <span class="brand-mark"><i></i><i></i><i></i></span>
          <span>教策云枢</span>
        </button>
        <div class="top-actions">
          <button type="button" class="nav-btn" @click="goTasks">任务中心</button>
          <button type="button" class="nav-btn" @click="goResources">资源库</button>
          <button type="button" class="nav-btn" @click="goTeacher">返回工作台</button>
          <span class="teacher-pill">
            <b>{{ (teacherUser?.name || '教师').slice(0, 2) }}</b>
            教师：{{ teacherUser?.name || '已登录' }}
          </span>
        </div>
      </header>

      <section class="hero">
        <div>
          <p class="eyebrow">RESOURCE REVISION HUB</p>
          <h1>课件与教案迭代修改</h1>
          <p>
            集中查看当前账号生成过的 PPT 与 Word 历史版本，选择任意一版作为底稿，填写修改要求后生成新的修订版本。
          </p>
        </div>
        <div class="hero-card">
          <span>可迭代资源</span>
          <strong>{{ pptCount + docCount }}</strong>
          <div>
            <em>PPT {{ pptCount }}</em>
            <em>Word {{ docCount }}</em>
          </div>
        </div>
      </section>

      <section class="workspace">
        <aside class="version-panel">
          <div class="panel-head">
            <div>
              <p>选择底稿版本</p>
              <strong>{{ filteredTotal }} 个历史版本</strong>
            </div>
          </div>

          <div class="type-tabs">
            <button
              v-for="item in ITERATION_TYPES"
              :key="item.value"
              type="button"
              :class="{ active: activeType === item.value }"
              @click="activeType = item.value"
            >
              {{ item.label }}
              <span>{{ getTypeCount(item.value) }}</span>
            </button>
          </div>

          <div class="search-row">
            <input v-model="keyword" placeholder="搜索标题、对话或文件名" @keyup.enter="fetchResources()" />
            <button type="button" @click="fetchResources()">搜索</button>
          </div>

          <div v-if="loading" class="list-state">正在整理历史版本...</div>
          <div v-else-if="!resources.length" class="list-state">暂无可迭代的 PPT 或 Word</div>
          <div v-else class="version-list">
            <button
              v-for="item in resources"
              :key="item.id"
              type="button"
              :class="['version-item', { active: selectedId === item.id }]"
              @click="selectArtifact(item)"
            >
              <span class="file-icon" :class="item.type">{{ getFileInitial(item) }}</span>
              <span class="version-copy">
                <strong :title="item.title">{{ truncateText(item.title, 28) }}</strong>
                <small>{{ getTypeLabel(item) }} · {{ getVersionLabel(item) }} · #{{ item.id }}</small>
                <small>{{ truncateText(item.conversation_title || item.conversation_key || getLineageText(item), 34) }}</small>
              </span>
              <em>{{ formatDate(item.created_at).slice(0, 10) }}</em>
            </button>
          </div>
        </aside>

        <section class="editor-panel">
          <div class="selected-card" v-if="selectedArtifact">
            <div class="preview-box">
              <img v-if="previewUrls[selectedArtifact.id]" :src="previewUrls[selectedArtifact.id]" alt="PPT 预览" />
              <span v-else>{{ getFileInitial(selectedArtifact) }}</span>
            </div>
            <div class="selected-info">
              <p>{{ getTypeLabel(selectedArtifact) }} · {{ getVersionLabel(selectedArtifact) }}</p>
              <h2>{{ selectedArtifact.title }}</h2>
              <div class="meta-grid">
                <span>资源编号：#{{ selectedArtifact.id }}</span>
                <span>{{ getLineageText(selectedArtifact) }}</span>
                <span>文件大小：{{ formatSize(selectedArtifact.file_size) }}</span>
                <span>生成时间：{{ formatDate(selectedArtifact.created_at) }}</span>
              </div>
              <p v-if="selectedArtifact.edit_instruction" class="last-edit">
                上次修改：{{ selectedArtifact.edit_instruction }}
              </p>
            </div>
            <button type="button" class="ghost-action" @click="downloadArtifact(selectedArtifact)">下载底稿</button>
          </div>

          <div v-else class="empty-editor">
            <h2>请选择一个历史版本</h2>
            <p>左侧会列出你在备课过程中生成过的 PPT 与 Word，可选择任意版本继续迭代。</p>
          </div>

          <div class="revision-form" :class="{ disabled: !selectedArtifact }">
            <div class="form-title">
              <div>
                <p>本次修改要求</p>
                <h3>描述你希望这一版怎么改</h3>
              </div>
              <span>会基于选中的历史版本生成新版本，不覆盖原文件</span>
            </div>

            <input
              v-if="selectedArtifact?.type === 'doc'"
              v-model="customTitle"
              class="title-input"
              placeholder="可选：给修订后的 Word 教案命名"
              :disabled="!selectedArtifact || revising"
            />

            <textarea
              v-model="instruction"
              :disabled="!selectedArtifact || revising"
              placeholder="例如：把第 3 页活动改成小组探究；补充课堂评价；整体语言更适合高一学生；Word 中增加分层作业与板书设计..."
            ></textarea>

            <div class="form-actions">
              <p v-if="error" class="feedback error">{{ error }}</p>
              <p v-else-if="success" class="feedback success">{{ success }}</p>
              <p v-else class="hint">建议写清楚页码、段落、风格、增删内容或使用场景。</p>
              <button type="button" class="primary-action" :disabled="!selectedArtifact || revising" @click="submitRevision">
                {{ revising ? '正在生成修订版...' : '生成修订版' }}
              </button>
            </div>
          </div>

          <div v-if="resultArtifact" class="result-card">
            <div>
              <p>最新修订结果</p>
              <strong>{{ resultArtifact.title }}</strong>
              <span>{{ getTypeLabel(resultArtifact) }} · {{ getVersionLabel(resultArtifact) }} · #{{ resultArtifact.id }}</span>
            </div>
            <button type="button" class="ghost-action" @click="downloadArtifact(resultArtifact)">下载新版本</button>
          </div>
        </section>
      </section>
    </main>
  </div>
</template>

<style scoped>
.iteration-page {
  min-height: 100vh;
  position: relative;
  overflow: hidden;
  color: #edf6ff;
  background: linear-gradient(120deg, #041637 0%, #081347 48%, #19052f 100%);
  font-family: Inter, "PingFang SC", "Microsoft YaHei", system-ui, sans-serif;
}

.iteration-bg,
.iteration-bg i {
  position: fixed;
  inset: 0;
  pointer-events: none;
}

.star-layer {
  background-image:
    radial-gradient(circle, rgba(255, 255, 255, .8) 0 1px, transparent 1.4px),
    radial-gradient(circle, rgba(95, 178, 255, .8) 0 1px, transparent 1.5px);
  background-size: 180px 180px, 260px 260px;
  opacity: .28;
  animation: drift 30s linear infinite;
}

.wave-layer {
  top: 24%;
  height: 470px;
  background:
    repeating-linear-gradient(108deg, transparent 0 38px, rgba(56, 189, 248, .11) 39px, transparent 41px),
    radial-gradient(ellipse at 82% 34%, rgba(141, 80, 255, .34), transparent 58%),
    radial-gradient(ellipse at 18% 58%, rgba(14, 165, 233, .24), transparent 52%);
  transform: skewY(-8deg);
  opacity: .62;
  mask-image: linear-gradient(90deg, transparent, #000 18%, #000 82%, transparent);
}

.grid-layer {
  top: auto;
  left: -18%;
  right: -18%;
  bottom: -150px;
  height: 400px;
  background:
    linear-gradient(rgba(56, 189, 248, .11) 1px, transparent 1px),
    linear-gradient(90deg, rgba(139, 92, 246, .12) 1px, transparent 1px);
  background-size: 52px 52px;
  transform: perspective(780px) rotateX(64deg);
  transform-origin: bottom;
  mask-image: linear-gradient(to top, rgba(0, 0, 0, .86), transparent 82%);
}

.iteration-shell {
  position: relative;
  z-index: 1;
  width: min(1560px, calc(100% - 64px));
  margin: 0 auto;
  padding: 32px 0 54px;
}

.topbar,
.workspace,
.version-panel,
.editor-panel,
.hero-card,
.selected-card,
.revision-form,
.result-card {
  border: 1px solid rgba(151, 178, 255, .2);
  background: linear-gradient(145deg, rgba(17, 33, 78, .78), rgba(5, 10, 34, .72));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, .09), 0 34px 100px -70px rgba(45, 156, 255, .9);
  backdrop-filter: blur(24px);
}

.topbar {
  height: 78px;
  border-radius: 24px;
  padding: 0 20px 0 26px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

button {
  font: inherit;
}

.brand,
.top-actions,
.teacher-pill,
.type-tabs,
.search-row,
.form-actions,
.result-card {
  display: flex;
  align-items: center;
}

.brand {
  gap: 14px;
  color: #fff;
  font-size: 22px;
  font-weight: 900;
}

.brand-mark {
  width: 38px;
  height: 38px;
  position: relative;
}

.brand-mark i {
  position: absolute;
  width: 21px;
  height: 30px;
  border-radius: 8px;
  box-shadow: 0 18px 34px -19px rgba(58, 205, 255, .95);
}

.brand-mark i:nth-child(1) {
  left: 0;
  top: 5px;
  background: linear-gradient(150deg, #36e2ff, #536fff);
}

.brand-mark i:nth-child(2) {
  left: 9px;
  top: 0;
  background: linear-gradient(150deg, #74f4bd, #37c9ff);
}

.brand-mark i:nth-child(3) {
  left: 18px;
  top: 7px;
  background: linear-gradient(150deg, #8b5cf6, #ef5dea);
}

.top-actions {
  gap: 10px;
  min-width: 0;
}

.nav-btn,
.teacher-pill,
.ghost-action,
.primary-action,
.search-row button,
.type-tabs button {
  border: 1px solid rgba(164, 186, 240, .2);
  background: rgba(6, 11, 36, .55);
  color: rgba(237, 246, 255, .9);
  font-weight: 850;
  transition: transform .22s ease, border-color .22s ease, background .22s ease, box-shadow .22s ease;
}

.nav-btn {
  height: 42px;
  border-radius: 14px;
  padding: 0 16px;
}

.teacher-pill {
  height: 46px;
  border-radius: 18px;
  gap: 10px;
  padding: 0 16px 0 10px;
}

.teacher-pill b {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: linear-gradient(145deg, #38d4ff, #7b4dff);
  color: #fff;
  font-size: 12px;
}

.hero {
  min-height: 260px;
  padding: 60px 10px 42px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 330px;
  align-items: center;
  gap: 40px;
}

.eyebrow {
  margin: 0 0 16px;
  color: #5deaff;
  font-size: 13px;
  font-weight: 950;
  letter-spacing: .42em;
}

.hero h1 {
  margin: 0;
  color: #fff;
  font-size: clamp(42px, 4.6vw, 74px);
  font-weight: 950;
  line-height: 1.08;
}

.hero p:not(.eyebrow) {
  max-width: 820px;
  margin: 20px 0 0;
  color: rgba(225, 235, 255, .75);
  font-size: 17px;
  line-height: 1.8;
}

.hero-card {
  min-height: 170px;
  border-radius: 28px;
  padding: 26px;
  background:
    radial-gradient(circle at 82% 0, rgba(121, 86, 255, .44), transparent 45%),
    linear-gradient(145deg, rgba(20, 45, 102, .85), rgba(6, 10, 36, .76));
}

.hero-card span,
.hero-card em {
  color: rgba(204, 219, 250, .66);
  font-style: normal;
  font-weight: 850;
}

.hero-card strong {
  display: block;
  margin: 12px 0 22px;
  color: #fff;
  font-size: 58px;
  line-height: 1;
}

.hero-card div {
  display: flex;
  gap: 10px;
}

.hero-card em {
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(12, 22, 58, .74);
  border: 1px solid rgba(146, 175, 255, .18);
}

.workspace {
  min-height: 620px;
  border-radius: 30px;
  padding: 18px;
  display: grid;
  grid-template-columns: 420px minmax(0, 1fr);
  gap: 18px;
}

.version-panel,
.editor-panel {
  min-width: 0;
  border-radius: 24px;
}

.version-panel {
  padding: 18px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.panel-head p,
.panel-head strong {
  margin: 0;
}

.panel-head p,
.form-title p,
.selected-info p,
.result-card p {
  color: #5deaff;
  font-size: 12px;
  font-weight: 950;
  letter-spacing: .22em;
}

.panel-head strong {
  display: block;
  margin-top: 5px;
  color: #fff;
  font-size: 22px;
}

.type-tabs {
  gap: 8px;
  margin-bottom: 12px;
}

.type-tabs button {
  flex: 1;
  height: 40px;
  border-radius: 14px;
  font-size: 13px;
}

.type-tabs button.active {
  color: #fff;
  border-color: rgba(84, 220, 255, .58);
  background: linear-gradient(135deg, rgba(42, 199, 255, .86), rgba(122, 74, 255, .88));
  box-shadow: 0 14px 34px -22px rgba(58, 208, 255, .9);
}

.type-tabs span {
  margin-left: 6px;
  color: rgba(255, 255, 255, .72);
}

.search-row {
  gap: 8px;
  margin-bottom: 14px;
}

.search-row input,
.title-input,
.revision-form textarea {
  width: 100%;
  min-width: 0;
  border: 1px solid rgba(156, 181, 255, .18);
  background: rgba(3, 8, 30, .62);
  color: #edf6ff;
  outline: none;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, .04);
}

.search-row input {
  height: 42px;
  border-radius: 14px;
  padding: 0 14px;
}

.search-row button {
  height: 42px;
  border-radius: 14px;
  padding: 0 16px;
  white-space: nowrap;
}

.version-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding-right: 4px;
}

.version-item {
  width: 100%;
  min-width: 0;
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  min-height: 84px;
  padding: 12px;
  margin-bottom: 10px;
  border: 1px solid rgba(151, 178, 255, .16);
  border-radius: 18px;
  background: rgba(8, 17, 49, .58);
  color: #edf6ff;
  text-align: left;
}

.version-item.active {
  border-color: rgba(75, 213, 255, .7);
  background: linear-gradient(135deg, rgba(20, 67, 124, .78), rgba(42, 35, 114, .78));
  box-shadow: 0 16px 48px -34px rgba(55, 204, 255, .95);
}

.file-icon {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  color: #fff;
  font-weight: 950;
  background: linear-gradient(145deg, #38cfff, #6555ff);
}

.file-icon.doc {
  background: linear-gradient(145deg, #52e6a5, #2fa7ff);
}

.version-copy {
  min-width: 0;
}

.version-copy strong,
.version-copy small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.version-copy strong {
  color: #fff;
  font-size: 14px;
  margin-bottom: 4px;
}

.version-copy small,
.version-item em {
  color: rgba(205, 219, 245, .62);
  font-size: 12px;
  font-style: normal;
  line-height: 1.55;
}

.list-state {
  flex: 1;
  display: grid;
  place-items: center;
  color: rgba(218, 231, 255, .66);
}

.editor-panel {
  padding: 22px;
  display: flex;
  flex-direction: column;
  gap: 18px;
  overflow: hidden;
}

.selected-card {
  border-radius: 24px;
  padding: 18px;
  display: grid;
  grid-template-columns: 210px minmax(0, 1fr) auto;
  gap: 18px;
  align-items: center;
}

.preview-box {
  aspect-ratio: 16 / 10;
  border-radius: 18px;
  overflow: hidden;
  display: grid;
  place-items: center;
  background:
    linear-gradient(135deg, rgba(26, 201, 255, .12), rgba(130, 83, 255, .15)),
    rgba(4, 10, 34, .72);
  border: 1px solid rgba(143, 173, 255, .18);
}

.preview-box img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.preview-box span {
  width: 62px;
  height: 62px;
  display: grid;
  place-items: center;
  border-radius: 20px;
  color: #fff;
  font-size: 28px;
  font-weight: 950;
  background: linear-gradient(145deg, #38d4ff, #7a51ff);
}

.selected-info {
  min-width: 0;
}

.selected-info p,
.result-card p {
  margin: 0 0 8px;
}

.selected-info h2 {
  margin: 0 0 12px;
  color: #fff;
  font-size: 26px;
  line-height: 1.25;
}

.meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 16px;
  color: rgba(211, 225, 250, .68);
  font-size: 13px;
}

.meta-grid span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.last-edit {
  margin: 12px 0 0 !important;
  color: rgba(189, 231, 255, .76) !important;
  letter-spacing: 0 !important;
  font-size: 13px !important;
}

.ghost-action,
.primary-action {
  height: 46px;
  border-radius: 16px;
  padding: 0 18px;
  white-space: nowrap;
}

.primary-action {
  min-width: 154px;
  color: #fff;
  background: linear-gradient(135deg, #27d3ff, #6c55ff 72%, #8b48ff);
  border-color: rgba(91, 219, 255, .52);
  box-shadow: 0 18px 48px -28px rgba(40, 210, 255, .9);
}

.primary-action:disabled,
.ghost-action:disabled {
  opacity: .48;
  cursor: not-allowed;
}

.revision-form {
  flex: 1;
  min-height: 330px;
  border-radius: 24px;
  padding: 22px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.revision-form.disabled {
  opacity: .7;
}

.form-title {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.form-title h3 {
  margin: 0;
  color: #fff;
  font-size: 25px;
}

.form-title span {
  color: rgba(206, 219, 245, .62);
  font-size: 13px;
  white-space: nowrap;
}

.title-input {
  height: 46px;
  border-radius: 16px;
  padding: 0 16px;
}

.revision-form textarea {
  flex: 1;
  min-height: 180px;
  resize: vertical;
  border-radius: 20px;
  padding: 18px;
  line-height: 1.7;
}

.form-actions {
  justify-content: space-between;
  gap: 16px;
}

.feedback,
.hint {
  margin: 0;
  min-width: 0;
  color: rgba(210, 224, 250, .66);
  font-size: 13px;
}

.feedback.error {
  color: #ff9cab;
}

.feedback.success {
  color: #7cf5c5;
}

.result-card {
  justify-content: space-between;
  gap: 18px;
  border-radius: 22px;
  padding: 18px;
}

.result-card div {
  min-width: 0;
}

.result-card strong,
.result-card span {
  display: block;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.result-card strong {
  color: #fff;
  font-size: 18px;
}

.result-card span {
  margin-top: 6px;
  color: rgba(205, 219, 245, .66);
  font-size: 13px;
}

.empty-editor {
  min-height: 180px;
  border-radius: 24px;
  border: 1px dashed rgba(151, 178, 255, .22);
  display: grid;
  place-items: center;
  text-align: center;
  padding: 28px;
  color: rgba(222, 234, 255, .68);
}

.empty-editor h2 {
  margin: 0 0 8px;
  color: #fff;
}

button:hover:not(:disabled) {
  transform: translateY(-1px);
  border-color: rgba(95, 220, 255, .55);
}

@keyframes drift {
  from { transform: translate3d(0, 0, 0); }
  to { transform: translate3d(-120px, 70px, 0); }
}

@media (max-width: 1100px) {
  .iteration-shell {
    width: min(100% - 24px, 760px);
  }

  .topbar,
  .hero,
  .workspace,
  .selected-card {
    grid-template-columns: 1fr;
  }

  .topbar {
    height: auto;
    padding: 16px;
    align-items: flex-start;
  }

  .top-actions {
    flex-wrap: wrap;
  }

  .hero {
    padding-top: 38px;
  }

  .workspace {
    min-height: auto;
  }

  .version-panel {
    max-height: 520px;
  }

  .form-title,
  .form-actions,
  .result-card {
    align-items: flex-start;
    flex-direction: column;
  }

  .form-title span {
    white-space: normal;
  }

  .meta-grid {
    grid-template-columns: 1fr;
  }
}
</style>
