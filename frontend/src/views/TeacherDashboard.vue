<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import Sidebar from '../components/Sidebar.vue'
import ChatView from '../components/ChatView.vue'

const MOBILE_BREAKPOINT = 980
const router = useRouter()
const sidebarCollapsed = ref(false)
const sidebarWidth = ref(330)
const isResizingSidebar = ref(false)
const isMobile = ref(false)
let sidebarStartX = 0
let sidebarStartWidth = 0
const conversations = ref([
  { id: '1', title: '新对话', active: true, pinned: false, lastMessage: '' },
])
const currentConversationId = ref('1')
const historyMessages = ref([])
const kbUploadScope = ref('session')

const KB_SCOPE_LABELS = {
  session: '当前对话',
  user: '教师资料',
}

function normalizeKbScope(rawScope) {
  const value = String(rawScope || '').trim().toLowerCase()
  return value === 'user' ? 'user' : 'session'
}

function getKbScopeLabel(rawScope) {
  const normalized = normalizeKbScope(rawScope)
  return KB_SCOPE_LABELS[normalized] || KB_SCOPE_LABELS.session
}

const ragState = ref({
  active: false,
  filename: '',
  status: '未上传教材，AI 将使用通用知识回答',
  loading: false,
})

const API_BASE = import.meta.env.VITE_API_BASE || '/api'
const teacherToken = ref(localStorage.getItem('teacher_token') || '')
const teacherUser = ref(null)
const uiTheme = ref('dark')

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

const isAuthed = computed(() => !!teacherToken.value)
const teacherName = computed(() => teacherUser.value?.name || '未登录')

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

function getAuthHeaders() {
  if (!teacherToken.value) return {}
  return { Authorization: `Bearer ${teacherToken.value}` }
}

async function refreshConversationListOnly() {
  if (!teacherToken.value) return
  try {
    const response = await fetch(`${API_BASE}/teacher/conversations`, {
      headers: getAuthHeaders(),
    })
    const data = await response.json().catch(() => ({}))
    if (!response.ok) {
      throw new Error(getApiErrorMessage(data, response.status))
    }

    const list = (data.conversations || []).map(item => ({
      id: item.conversation_key,
      title: item.title || '新对话',
      pinned: !!item.pinned,
      lastMessage: item.last_message || '',
      active: false,
    }))

    if (!list.length) return
    const preferredId = String(currentConversationId.value || '')
    const targetId = list.some(item => item.id === preferredId) ? preferredId : list[0].id
    list.forEach(item => {
      item.active = item.id === targetId
    })
    conversations.value = list
    currentConversationId.value = targetId
  } catch (err) {
    // 仅刷新列表失败时保持当前状态
  }
}

async function handleTogglePin({ id, pinned }) {
  if (!id || !teacherToken.value) return
  try {
    const response = await fetch(`${API_BASE}/teacher/conversations/${id}/pin`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
      body: JSON.stringify({ pinned: !!pinned }),
    })
    const data = await response.json().catch(() => ({}))
    if (!response.ok) {
      throw new Error(getApiErrorMessage(data, response.status))
    }
    await refreshConversationListOnly()
  } catch (err) {
    console.warn('置顶失败', err)
  }
}

async function handleDeleteConversation(conversationId) {
  if (!conversationId) return
  const confirmed = window.confirm('确认删除该对话？删除后不可恢复。')
  if (!confirmed) return

  try {
    if (teacherToken.value) {
      const response = await fetch(`${API_BASE}/teacher/conversations/${conversationId}`, {
        method: 'DELETE',
        headers: getAuthHeaders(),
      })
      const data = await response.json().catch(() => ({}))
      if (!response.ok) {
        throw new Error(getApiErrorMessage(data, response.status))
      }
    }

    const remain = conversations.value.filter(item => item.id !== conversationId)
    if (!remain.length) {
      newChat()
      return
    }

    const nextId = currentConversationId.value === conversationId ? remain[0].id : currentConversationId.value
    remain.forEach(item => {
      item.active = item.id === nextId
    })
    conversations.value = remain
    if (currentConversationId.value !== nextId) {
      currentConversationId.value = nextId
      await fetchConversationMessages(nextId)
    }
  } catch (err) {
    console.warn('删除对话失败', err)
  }
}

function handleConversationUpdated() {
  refreshConversationListOnly()
}

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

function syncViewportMode() {
  const mobileNow = window.innerWidth <= MOBILE_BREAKPOINT
  if (mobileNow === isMobile.value) return
  isMobile.value = mobileNow
  // Keep desktop behavior unchanged while making mobile default to collapsed.
  sidebarCollapsed.value = mobileNow
}

function closeMobileSidebar() {
  if (isMobile.value) {
    sidebarCollapsed.value = true
  }
}

function startSidebarResize(event) {
  if (sidebarCollapsed.value || isMobile.value) return
  isResizingSidebar.value = true
  sidebarStartX = event.clientX
  sidebarStartWidth = sidebarWidth.value
  document.body.style.cursor = 'col-resize'
  event.preventDefault()
}

function handleSidebarResize(event) {
  if (!isResizingSidebar.value) return
  const delta = event.clientX - sidebarStartX
  const nextWidth = Math.min(360, Math.max(260, sidebarStartWidth + delta))
  sidebarWidth.value = nextWidth
}

function stopSidebarResize() {
  if (!isResizingSidebar.value) return
  isResizingSidebar.value = false
  document.body.style.cursor = ''
}

function getFallbackConversation() {
  const id = Date.now().toString()
  return { id, title: '新对话', active: true, pinned: false, lastMessage: '' }
}

function newChat() {
  const id = Date.now().toString()
  conversations.value.forEach(item => {
    item.active = false
  })
  conversations.value.unshift({ id, title: '新对话', active: true, pinned: false, lastMessage: '' })
  currentConversationId.value = id
  historyMessages.value = []

  if (teacherToken.value) {
    fetch(`${API_BASE}/teacher/conversations`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
      body: JSON.stringify({ conversation_key: id, title: '新对话' }),
    }).catch(() => {})
  }
}

function selectConversation(id) {
  conversations.value.forEach(item => {
    item.active = item.id === id
  })
  currentConversationId.value = id
  fetchConversationMessages(id)
}

function handleNewChat() {
  newChat()
  closeMobileSidebar()
}

function handleSelectConversation(id) {
  selectConversation(id)
  closeMobileSidebar()
}

async function fetchConversations() {
  if (!teacherToken.value) return
  try {
    const response = await fetch(`${API_BASE}/teacher/conversations`, {
      headers: getAuthHeaders(),
    })
    const data = await response.json().catch(() => ({}))
    if (!response.ok) {
      throw new Error(getApiErrorMessage(data, response.status))
    }

    const list = (data.conversations || []).map(item => ({
      id: item.conversation_key,
      title: item.title || '新对话',
      pinned: !!item.pinned,
      lastMessage: item.last_message || '',
      active: false,
    }))
    if (list.length) {
      const preferredId = String(currentConversationId.value || '')
      const targetId = list.some(item => item.id === preferredId) ? preferredId : list[0].id
      list.forEach(item => {
        item.active = item.id === targetId
      })
      conversations.value = list
      currentConversationId.value = targetId
      await fetchConversationMessages(targetId)
    } else {
      conversations.value = [getFallbackConversation()]
      currentConversationId.value = conversations.value[0].id
      historyMessages.value = []
    }
  } catch (err) {
    // 登录失效或请求失败时保持本地模式
    console.warn('加载历史失败', err)
  }
}

async function fetchConversationMessages(conversationId) {
  if (!teacherToken.value || !conversationId) {
    historyMessages.value = []
    return
  }
  try {
    const response = await fetch(`${API_BASE}/teacher/conversations/${conversationId}`, {
      headers: getAuthHeaders(),
    })
    const data = await response.json().catch(() => ({}))
    if (!response.ok) {
      throw new Error(getApiErrorMessage(data, response.status))
    }
    historyMessages.value = (data.messages || []).map(item => ({
      role: item.role,
      text: item.content,
    }))
  } catch (err) {
    historyMessages.value = []
  }
}

function logout() {
  localStorage.removeItem('teacher_token')
  localStorage.removeItem('teacher_user')
  teacherToken.value = ''
  teacherUser.value = null
  kbUploadScope.value = 'session'
  conversations.value = [getFallbackConversation()]
  currentConversationId.value = conversations.value[0].id
  historyMessages.value = []
  router.replace('/auth/teacher')
}

function goResources() {
  router.push('/teacher/resources')
}

function goKnowledge() {
  router.push('/teacher/knowledge')
}

function goIterations() {
  router.push('/teacher/iterations')
}

function goTasks() {
  router.push('/teacher/tasks')
}

onMounted(() => {
  isMobile.value = window.innerWidth <= MOBILE_BREAKPOINT
  sidebarCollapsed.value = isMobile.value
  teacherToken.value = localStorage.getItem('teacher_token') || ''
  loadTeacherUser()
  fetchConversations()
  window.addEventListener('resize', syncViewportMode)
  window.addEventListener('mousemove', handleSidebarResize)
  window.addEventListener('mouseup', stopSidebarResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncViewportMode)
  window.removeEventListener('mousemove', handleSidebarResize)
  window.removeEventListener('mouseup', stopSidebarResize)
})

function handleSetKbScope(scope) {
  const nextScope = normalizeKbScope(scope)
  if (nextScope === kbUploadScope.value) return
  kbUploadScope.value = nextScope

  if (ragState.value.loading) return
  const scopeLabel = getKbScopeLabel(nextScope)
  if (ragState.value.active) {
    ragState.value.status = `已切换上传范围为${scopeLabel}，下次上传后生效`
  } else {
    ragState.value.status = `当前上传范围：${scopeLabel}。未上传教材，AI 将使用通用知识回答`
  }
}

async function handleUploadDoc(file) {
  if (!file) return
  ragState.value.loading = true
  ragState.value.status = '正在解析教材构建知识库...'
  ragState.value.filename = file.name
  ragState.value.active = false

  try {
    const formData = new FormData()
    formData.append('file', file)

    const activeConversationKey = String(currentConversationId.value || '').trim()
    let uploadScope = normalizeKbScope(kbUploadScope.value)
    if (teacherToken.value) {
      if (uploadScope === 'session' && !activeConversationKey) {
        uploadScope = 'user'
      }
      formData.append('kb_scope', uploadScope)
      if (uploadScope === 'session' && activeConversationKey) {
        formData.append('conversation_key', activeConversationKey)
      }
    }

    const endpoint = teacherToken.value ? '/kb/upload' : '/upload_doc'
    const headers = teacherToken.value ? getAuthHeaders() : {}

    const response = await fetch(`${API_BASE}${endpoint}`, {
      method: 'POST',
      headers,
      body: formData,
    })

    const data = await response.json().catch(() => ({}))
    if (!response.ok) {
      throw new Error(getApiErrorMessage(data, response.status))
    }

    ragState.value.active = true
    ragState.value.filename = data.filename || file.name
    if (teacherToken.value) {
      const scopeLabel = getKbScopeLabel(data.source_scope || kbUploadScope.value)
      ragState.value.status = `知识库已就绪（${scopeLabel}），AI 将优先基于匹配学科资料回答`
    } else {
      ragState.value.status = '知识库已就绪，AI 将优先基于上传资料回答'
    }
  } catch (err) {
    ragState.value.active = false
    ragState.value.status = `上传失败：${err.message}`
  } finally {
    ragState.value.loading = false
  }
}

async function handleClearKb() {
  try {
    const headers = teacherToken.value ? getAuthHeaders() : {}
    await fetch(`${API_BASE}/clear_kb`, { method: 'POST', headers })
  } catch (err) {
    // 忽略清理失败的提示
  } finally {
    ragState.value.active = false
    ragState.value.filename = ''
    ragState.value.status = '知识库已清除，AI 将使用通用知识回答'
  }
}
</script>

<template>
  <div class="teacher-shell" :class="uiTheme === 'elder' ? 'theme-elder' : ''">
    <div class="teacher-bg"></div>
    <div class="cosmic-mist"></div>
    <div
      class="teacher-content flex h-screen w-screen overflow-hidden"
      :class="isMobile ? 'teacher-content--mobile' : ''"
    >
      <Sidebar
        :collapsed="sidebarCollapsed"
        :width="sidebarWidth"
        :is-mobile="isMobile"
        :conversations="conversations"
        :kb-active="ragState.active"
        :kb-filename="ragState.filename"
        :kb-status="ragState.status"
        :kb-loading="ragState.loading"
        :kb-scope="kbUploadScope"
        :kb-scope-disabled="!isAuthed"
        :ui-theme="uiTheme"
        @toggle="toggleSidebar"
        @new-chat="handleNewChat"
        @select="handleSelectConversation"
        @toggle-pin="handleTogglePin"
        @delete-conversation="handleDeleteConversation"
        @set-kb-scope="handleSetKbScope"
        @open-resources="goResources"
        @open-knowledge="goKnowledge"
        @open-iterations="goIterations"
        @open-tasks="goTasks"
        @upload-doc="handleUploadDoc"
        @clear-kb="handleClearKb"
      />
      <button
        v-if="isMobile && !sidebarCollapsed"
        type="button"
        class="sidebar-backdrop"
        aria-label="close sidebar"
        @click="closeMobileSidebar"
      ></button>
      <div
        class="sidebar-resizer"
        @mousedown="startSidebarResize"
        :class="sidebarCollapsed || isMobile ? 'hidden' : ''"
        title="拖动调整侧边栏宽度"
      ></div>
      <div class="dashboard-main relative flex-1 min-w-0 h-full flex flex-col">
        <div
          v-if="!isMobile"
          class="dashboard-toolbar"
        >
          <button type="button" class="toolbar-bell" title="通知">
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                d="M15 17h5l-1.4-1.4A2 2 0 0118 14.2V11a6 6 0 10-12 0v3.2c0 .5-.2 1-.6 1.4L4 17h5m6 0a3 3 0 01-6 0" />
            </svg>
          </button>
          <div class="workbench-menu-card workbench-menu-card--inline">
            <span class="teacher-avatar">{{ teacherName.slice(0, 2) }}</span>
            <p class="toolbar-teacher-name">{{ isAuthed ? `教师：${teacherName}` : '未登录' }}</p>
            <button
              v-if="isAuthed"
              type="button"
              class="menu-link-btn menu-link-btn--inline"
              @click="logout"
            >
              退出登录
            </button>
          </div>
        </div>
        <ChatView
          class="flex-1 min-h-0"
          :collapsed="sidebarCollapsed"
          :conversation-id="currentConversationId"
          :rag-active="ragState.active"
          :ui-theme="uiTheme"
          :history-messages="historyMessages"
          @toggle-sidebar="toggleSidebar"
          @conversation-updated="handleConversationUpdated"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.teacher-shell {
  position: relative;
  color: #e2e8f0;
  background: #020716;
  overflow: hidden;
}

.teacher-shell::before,
.teacher-shell::after {
  content: '';
  position: absolute;
  inset: 0;
  height: auto;
  background:
    radial-gradient(circle at 22% 20%, rgba(32, 111, 255, 0.22), transparent 34%),
    radial-gradient(circle at 74% 25%, rgba(91, 61, 255, 0.24), transparent 43%),
    radial-gradient(circle at 94% 50%, rgba(217, 70, 239, 0.18), transparent 36%),
    linear-gradient(118deg, #061945 0%, #030821 48%, #160421 100%);
  opacity: 1;
  animation: none;
  pointer-events: none;
}

.teacher-shell::after {
  inset: auto -16vw 5vh auto;
  width: 76vw;
  height: 28vw;
  border: 1px solid rgba(125, 211, 252, 0.13);
  border-radius: 50%;
  background: transparent;
  transform: rotate(-13deg);
}

.teacher-bg {
  position: absolute;
  inset: 0;
  background-image:
    radial-gradient(1px 1px at 8% 20%, rgba(191, 219, 254, 0.7), transparent 60%),
    radial-gradient(1px 1px at 27% 9%, rgba(255, 255, 255, 0.55), transparent 60%),
    radial-gradient(1px 1px at 42% 18%, rgba(125, 211, 252, 0.55), transparent 60%),
    radial-gradient(1px 1px at 68% 8%, rgba(255, 255, 255, 0.55), transparent 60%),
    radial-gradient(2px 2px at 73% 16%, rgba(167, 139, 250, 0.65), transparent 60%),
    radial-gradient(1px 1px at 94% 12%, rgba(255, 255, 255, 0.45), transparent 60%),
    radial-gradient(1px 1px at 87% 72%, rgba(125, 211, 252, 0.6), transparent 60%);
  opacity: 0.7;
  animation: none;
  pointer-events: none;
}

.teacher-bg::before {
  content: '';
  position: absolute;
  left: 18%;
  right: -8%;
  top: 26%;
  height: 42%;
  opacity: 0.42;
  background:
    repeating-linear-gradient(164deg, rgba(56, 139, 255, 0.2) 0 1px, transparent 1px 20px),
    repeating-linear-gradient(14deg, rgba(132, 71, 255, 0.18) 0 1px, transparent 1px 24px);
  clip-path: polygon(0 62%, 14% 42%, 30% 49%, 45% 62%, 61% 44%, 78% 29%, 100% 18%, 100% 63%, 79% 78%, 58% 70%, 40% 58%, 22% 69%, 0 84%);
  filter: drop-shadow(0 0 32px rgba(56, 189, 248, 0.16));
  animation: meshFloat 18s ease-in-out infinite;
}

.teacher-content {
  position: relative;
  z-index: 1;
}

.teacher-content--mobile {
  overflow: hidden;
}

.sidebar-backdrop {
  position: absolute;
  inset: 0;
  z-index: 24;
  border: 0;
  background: rgba(2, 6, 23, 0.58);
  backdrop-filter: blur(1px);
}

.dashboard-main {
  min-height: 0;
  border-left: 1px solid rgba(122, 150, 225, 0.2);
  background: rgba(4, 12, 40, 0.05);
  box-shadow: inset 1px 0 0 rgba(255, 255, 255, 0.04);
}

.dashboard-toolbar {
  position: absolute;
  top: 12px;
  right: 22px;
  z-index: 22;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 18px;
  min-height: auto;
  padding: 0;
  flex-shrink: 0;
  border-bottom: 0;
  background: transparent;
  backdrop-filter: none;
}

.toolbar-trigger {
  width: 100%;
  border-radius: 999px;
  border: 1px solid rgba(148, 163, 184, 0.28);
  background: rgba(2, 6, 23, 0.78);
  color: #e2e8f0;
  font-size: 12px;
  font-weight: 600;
  padding: 8px 14px;
  backdrop-filter: blur(10px);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.toolbar-trigger:hover {
  border-color: rgba(125, 211, 252, 0.6);
  box-shadow: 0 10px 24px -18px rgba(56, 189, 248, 0.6);
}

.workbench-menu-card {
  margin-top: 8px;
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.24);
  background: rgba(5, 12, 38, 0.56);
  padding: 10px;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(18px);
}

.workbench-menu-card--inline {
  margin-top: 0;
  display: flex;
  align-items: center;
  gap: 12px;
  border-radius: 999px;
  padding: 7px 8px 7px 12px;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.09), 0 16px 36px -28px rgba(15, 23, 42, 0.9);
}

.workbench-menu-card--inline .menu-section-title,
.workbench-menu-card--inline .toolbar-teacher-name {
  margin: 0;
}

.workbench-menu-card--inline .toolbar-teacher-name {
  white-space: nowrap;
}

.menu-section-title {
  font-size: 11px;
  color: #94a3b8;
  letter-spacing: 0.04em;
}

.menu-item-btn,
.menu-link-btn {
  width: 100%;
  border-radius: 10px;
  border: 1px solid rgba(148, 163, 184, 0.24);
  background: rgba(15, 23, 42, 0.82);
  color: #dbeafe;
  text-align: left;
  font-size: 12px;
  padding: 8px 10px;
  transition: border-color 0.2s ease, background 0.2s ease;
}

.menu-link-btn {
  margin-top: 6px;
}

.menu-link-btn--inline {
  margin-top: 0;
  width: auto;
  border-radius: 999px;
  padding: 8px 14px;
  text-align: center;
  white-space: nowrap;
}

.menu-item-btn + .menu-item-btn {
  margin-top: 6px;
}

.menu-item-btn:hover,
.menu-link-btn:hover {
  border-color: rgba(125, 211, 252, 0.55);
  background: rgba(15, 23, 42, 0.96);
}

.menu-item-btn.is-active {
  border-color: rgba(56, 189, 248, 0.75);
  background: rgba(56, 189, 248, 0.2);
  color: #f8fafc;
}

.toolbar-teacher-name {
  margin-top: 0;
  font-size: 12px;
  color: #cbd5f5;
}

.toolbar-bell {
  display: inline-grid;
  place-items: center;
  width: 40px;
  height: 40px;
  color: #e2e8f0;
  border: 0;
  border-radius: 999px;
  background: transparent;
  transition: background 0.2s ease, color 0.2s ease;
}

.toolbar-bell:hover {
  color: #ffffff;
  background: rgba(255, 255, 255, 0.08);
}

.teacher-avatar {
  display: inline-grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 999px;
  background: linear-gradient(135deg, #6d5dfc, #8b5cf6);
  color: white;
  font-size: 12px;
  font-weight: 800;
}

.sidebar-resizer {
  width: 2px;
  cursor: col-resize;
  background: linear-gradient(180deg, rgba(56, 189, 248, 0.46), rgba(236, 72, 153, 0.28));
  box-shadow: 0 0 12px rgba(56, 189, 248, 0.2);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.sidebar-resizer:hover {
  transform: scaleX(1.2);
  box-shadow: 0 0 14px rgba(56, 189, 248, 0.45);
}

.teacher-shell.theme-elder {
  color: #1f2937;
  --teacher-page-bg: #f3f4f6;
  --teacher-sidebar-bg: #eceff3;
  --teacher-main-bg: #f7f8fa;
  --teacher-card-bg: #ffffff;
  --teacher-muted-card-bg: #fafbfc;
  --teacher-border-color: #d8dee8;
  --teacher-text-primary: #1f2937;
  --teacher-text-secondary: #374151;
  background: var(--teacher-page-bg);
  font-size: 15px;
  line-height: 1.6;
}

.teacher-shell.theme-elder::before,
.teacher-shell.theme-elder::after,
.teacher-shell.theme-elder .teacher-bg,
.teacher-shell.theme-elder .cosmic-mist {
  opacity: 0.04;
}

.teacher-shell.theme-elder .teacher-content {
  background: var(--teacher-main-bg);
}

.teacher-shell.theme-elder .toolbar-trigger {
  background: var(--teacher-card-bg);
  border-color: var(--teacher-border-color);
  color: var(--teacher-text-primary);
  font-size: 13px;
}

.teacher-shell.theme-elder .workbench-menu-card {
  background: var(--teacher-card-bg);
  border-color: var(--teacher-border-color);
}

.teacher-shell.theme-elder .menu-section-title {
  color: #6b7280;
}

.teacher-shell.theme-elder .toolbar-teacher-name {
  color: var(--teacher-text-secondary);
  font-size: 13px;
}

.teacher-shell.theme-elder .menu-item-btn,
.teacher-shell.theme-elder .menu-link-btn {
  font-size: 13px;
  color: var(--teacher-text-primary);
  padding: 9px 12px;
  background: var(--teacher-muted-card-bg) !important;
  border-color: var(--teacher-border-color) !important;
}

.teacher-shell.theme-elder .menu-item-btn.is-active {
  background: rgba(37, 99, 235, 0.14) !important;
  border-color: rgba(37, 99, 235, 0.45) !important;
  color: #1d4ed8;
}

.teacher-shell.theme-elder .text-slate-300 {
  color: var(--teacher-text-secondary) !important;
}

.teacher-shell.theme-elder .text-slate-200 {
  color: var(--teacher-text-primary) !important;
}

.teacher-shell.theme-elder .sidebar-resizer {
  background: linear-gradient(180deg, rgba(216, 222, 232, 0.95), rgba(216, 222, 232, 0.72));
  box-shadow: inset 1px 0 0 rgba(148, 163, 184, 0.35);
}

.teacher-shell.theme-elder button {
  font-size: 13px;
}

@media (max-width: 980px) {
  .dashboard-toolbar {
    left: auto;
    right: 10px;
    max-width: min(280px, calc(100% - 20px));
  }

  .toolbar-trigger {
    font-size: 11px;
    padding: 7px 12px;
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

@keyframes meshFloat {
  0%,
  100% {
    transform: translate3d(0, 0, 0) scale(1);
    opacity: 0.34;
  }
  50% {
    transform: translate3d(2%, -5%, 0) scale(1.03);
    opacity: 0.52;
  }
}
</style>
