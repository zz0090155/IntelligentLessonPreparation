<script setup>
defineProps({
  collapsed: Boolean,
  width: {
    type: Number,
    default: 260,
  },
  conversations: Array,
  kbActive: Boolean,
  kbFilename: String,
  kbStatus: String,
  kbLoading: Boolean,
  kbScope: {
    type: String,
    default: 'session',
  },
  kbScopeDisabled: Boolean,
  isMobile: {
    type: Boolean,
    default: false,
  },
  uiTheme: {
    type: String,
    default: 'dark',
  },
})
const emit = defineEmits([
  'toggle',
  'new-chat',
  'select',
  'upload-doc',
  'clear-kb',
  'set-kb-scope',
  'toggle-pin',
  'delete-conversation',
  'open-resources',
  'open-knowledge',
  'open-iterations',
  'open-tasks',
])

const fileInputId = `kb-upload-${Math.random().toString(36).slice(2)}`

const historyAccents = [
  { icon: '讯', className: 'conversation-icon--violet' },
  { icon: '数', className: 'conversation-icon--cyan' },
  { icon: '来', className: 'conversation-icon--emerald' },
  { icon: 'EN', className: 'conversation-icon--blue' },
  { icon: '课', className: 'conversation-icon--amber' },
  { icon: '理', className: 'conversation-icon--indigo' },
]

function clipText(value, maxLength = 15, fallback = '新对话') {
  const text = String(value || '').replace(/\s+/g, ' ').trim()
  if (!text) return fallback
  return text.length > maxLength ? `${text.slice(0, maxLength)}...` : text
}

function getConversationAccent(index) {
  return historyAccents[index % historyAccents.length]
}

function handleFileChange(event) {
  const file = event.target.files?.[0]
  if (!file) return
  emit('upload-doc', file)
  event.target.value = ''
}

function handleTogglePin(conv) {
  emit('toggle-pin', {
    id: conv.id,
    pinned: !conv.pinned,
  })
}

function handleDelete(conv) {
  emit('delete-conversation', conv.id)
}

function handleScopeChange(event) {
  const scope = String(event?.target?.value || '').trim()
  if (!scope) return
  emit('set-kb-scope', scope)
}
</script>

<template>
  <div
    class="sidebar-shell flex flex-col text-white transition-all duration-300 ease-in-out"
    :class="[
      collapsed ? 'overflow-hidden' : '',
      isMobile ? 'sidebar-shell--mobile' : '',
      uiTheme === 'elder' ? 'sidebar-shell--elder' : '',
    ]"
    :style="collapsed
      ? { width: '0px', minWidth: '0px' }
      : (isMobile
        ? { width: 'min(86vw, 340px)', minWidth: 'min(86vw, 340px)' }
        : { width: `${width}px`, minWidth: '200px' })"
  >
    <!-- 顶部 -->
    <div class="sidebar-top flex items-center justify-between">
      <span class="sidebar-brand truncate">
        <span class="sidebar-logo" aria-hidden="true"><i></i><i></i></span>
        <span>教策云枢</span>
        <em>AI</em>
      </span>
      <button
        @click="emit('toggle')"
        class="sidebar-collapse-btn"
        title="收起侧边栏"
      >
        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
            d="M11 19l-7-7 7-7m8 14l-7-7 7-7" />
        </svg>
      </button>
    </div>

    <!-- 新建对话按钮 -->
    <button
      @click="emit('new-chat')"
      class="sidebar-new-chat-btn"
    >
      <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
      </svg>
      新建对话
    </button>

    <button
      @click="emit('new-chat')"
      class="sidebar-current-chat-btn"
    >
      <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
          d="M8 10h8M8 14h5m7-2a8 8 0 11-3.1-6.3L20 5v7z" />
      </svg>
      新对话
    </button>

    <button
      @click="emit('open-resources')"
      class="sidebar-current-chat-btn"
    >
      <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
          d="M4 7a2 2 0 012-2h4l2 2h6a2 2 0 012 2v8a2 2 0 01-2 2H6a2 2 0 01-2-2V7z" />
      </svg>
      资源库
    </button>

    <button
      @click="emit('open-knowledge')"
      class="sidebar-current-chat-btn"
    >
      <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
          d="M12 6.5A5.5 5.5 0 016.5 12H4a8 8 0 018-8v2.5zm0 0A5.5 5.5 0 0117.5 12H20a8 8 0 00-8-8v2.5zM4 12h2.5A5.5 5.5 0 0012 17.5V20a8 8 0 01-8-8zm16 0h-2.5A5.5 5.5 0 0112 17.5V20a8 8 0 008-8z" />
      </svg>
      知识库
    </button>

    <button
      @click="emit('open-iterations')"
      class="sidebar-current-chat-btn"
    >
      <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
          d="M4 4v6h6M20 20v-6h-6M5.5 15A7 7 0 0018.3 9M18.5 9A7 7 0 005.7 15" />
      </svg>
      迭代修改
    </button>

    <button
      @click="emit('open-tasks')"
      class="sidebar-current-chat-btn"
    >
      <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
          d="M9 6h11M9 12h11M9 18h11M4 6h.01M4 12h.01M4 18h.01" />
      </svg>
      任务中心
    </button>

    <!-- 对话列表 -->
    <div class="history-title">
      <span>最近对话</span>
      <svg class="history-search-icon" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
          d="M21 21l-4.3-4.3M10.8 18a7.2 7.2 0 100-14.4 7.2 7.2 0 000 14.4z" />
      </svg>
    </div>
    <div class="conversation-list flex-1 overflow-y-auto">
      <div
        v-for="(conv, index) in conversations"
        :key="conv.id"
        class="conversation-row group flex items-start gap-2 transition"
        :class="conv.active
          ? 'bg-white/20 border-sky-300 text-white'
          : 'bg-white/10 border-white/10 text-slate-200 hover:border-sky-300 hover:text-white'"
      >
        <button
          @click="emit('select', conv.id)"
          class="conversation-main-btn min-w-0 flex-1 text-left"
        >
          <div class="conversation-title flex items-center gap-2 text-sm">
            <span
              class="conversation-icon"
              :class="getConversationAccent(index).className"
            >
              {{ conv.pinned ? '顶' : getConversationAccent(index).icon }}
            </span>
            <span class="conversation-copy">
              <strong>{{ clipText(conv.title, 14) }}</strong>
              <small>{{ clipText(conv.lastMessage, 16, conv.active ? '当前对话' : '已发送会话') }}</small>
            </span>
          </div>
        </button>

        <div class="conversation-actions flex items-center gap-1 transition">
          <button
            @click.stop="handleTogglePin(conv)"
            class="conversation-action-btn conversation-pin-btn rounded-md px-1.5 py-1 text-[11px] border transition"
            :class="conv.pinned
              ? 'border-amber-300/50 bg-amber-300/20 text-amber-100'
              : 'border-white/20 text-slate-300 hover:text-amber-100 hover:border-amber-300/40'"
            :title="conv.pinned ? '取消置顶' : '置顶对话'"
          >
            <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                d="M14 4l6 6-4 1-4 8-2-2 4-8 1-4zM4 20l6-6" />
            </svg>
          </button>
          <button
            @click.stop="handleDelete(conv)"
            class="conversation-action-btn conversation-delete-btn rounded-md px-1.5 py-1 text-[11px] border border-white/20 text-slate-300 hover:text-rose-100 hover:border-rose-300/50 transition"
            title="删除对话"
          >
            <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                d="M6 7h12m-9 0V5a1 1 0 011-1h4a1 1 0 011 1v2m-7 4v6m4-6v6M8 7l1 13h6l1-13" />
            </svg>
          </button>
        </div>
      </div>
    </div>

    <!-- 知识库上传区 -->
    <div class="kb-panel">
      <div class="kb-title text-xs uppercase tracking-wider text-slate-300 mb-2">上传教材</div>
      <label class="kb-scope-label mb-2 block text-[11px] text-slate-300">
        上传范围
        <select
          :value="kbScope"
          class="kb-scope-select mt-1 w-full rounded-lg border border-white/15 bg-slate-900/60 px-2 py-1.5 text-[11px] text-slate-100 focus:outline-none focus:ring-1 focus:ring-sky-300 disabled:opacity-60"
          :disabled="kbLoading || kbScopeDisabled"
          @change="handleScopeChange"
        >
          <option value="session">当前对话（隔离）</option>
          <option value="user">教师资料（跨对话）</option>
        </select>
      </label>
      <label
        :for="fileInputId"
        class="sidebar-upload-btn"
      >
        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
            d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14" />
        </svg>
        上传教材 (PDF/TXT/DOCX/PPTX/视频)
      </label>
      <input
        :id="fileInputId"
        type="file"
        accept=".pdf,.txt,.docx,.pptx,.mp4,.mov,.avi,.mp3,.wav,.m4a,.aac"
        class="hidden"
        @change="handleFileChange"
      />

      <div class="kb-status-card mt-3 rounded-lg bg-white/10 px-3 py-2 text-xs text-slate-100 border border-white/10">
        <div class="flex items-center justify-between gap-2">
          <span class="truncate">{{ kbFilename || '暂无教材' }}</span>
          <span
            class="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px]"
            :class="kbActive ? 'bg-sky-400/20 text-sky-100' : 'bg-white/10 text-slate-200'"
          >
            {{ kbActive ? '已启用' : '未启用' }}
          </span>
        </div>
        <p class="kb-status-text mt-1 text-[11px] text-slate-300">
          {{ kbLoading ? '正在解析教材构建知识库...' : (kbStatus || '等待上传教材') }}
        </p>
        <button
          v-if="kbActive"
          @click="emit('clear-kb')"
          class="kb-clear-btn mt-2 text-[11px] text-sky-200 hover:text-sky-100"
        >
          清除知识库
        </button>
      </div>
    </div>

    <!-- 底部信息 -->
    <div class="sidebar-footer">
      AI 将基于教材内容生成更精准的教案与资源
    </div>
  </div>
</template>

<style scoped>
.sidebar-shell {
  position: relative;
  background:
    linear-gradient(180deg, rgba(5, 18, 52, 0.98), rgba(3, 10, 32, 0.97)),
    radial-gradient(circle at 20% 18%, rgba(73, 130, 255, 0.18), transparent 34%);
  border-right: 1px solid rgba(118, 144, 212, 0.24);
  box-shadow: inset -1px 0 0 rgba(255, 255, 255, 0.06), 22px 0 46px -44px rgba(50, 114, 255, 0.86);
  overflow: hidden;
}

.sidebar-shell--mobile {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  z-index: 30;
  box-shadow: 10px 0 30px -18px rgba(2, 6, 23, 0.9);
}

.sidebar-shell::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(1px 1px at 14% 10%, rgba(255, 255, 255, 0.72), transparent 60%),
    radial-gradient(1px 1px at 68% 20%, rgba(147, 197, 253, 0.55), transparent 60%),
    radial-gradient(1px 1px at 35% 72%, rgba(255, 255, 255, 0.45), transparent 60%);
  opacity: 0.5;
  pointer-events: none;
}

.sidebar-shell::after {
  content: '';
  position: absolute;
  left: 20px;
  right: 20px;
  bottom: 132px;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(96, 165, 250, 0.42), transparent);
  pointer-events: none;
}

.sidebar-top {
  position: relative;
  z-index: 1;
  min-height: 68px;
  padding: 18px 20px 10px;
}

.sidebar-brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  color: #f8fafc;
  font-weight: 800;
  font-size: 20px;
}

.sidebar-brand em {
  font-style: normal;
  border: 1px solid rgba(96, 165, 250, 0.55);
  color: #60a5fa;
  border-radius: 999px;
  font-size: 12px;
  line-height: 1;
  padding: 3px 6px;
  background: rgba(15, 23, 42, 0.45);
}

.sidebar-logo {
  position: relative;
  width: 28px;
  height: 30px;
  flex: 0 0 auto;
}

.sidebar-logo i {
  position: absolute;
  width: 17px;
  height: 24px;
  border-radius: 5px;
  transform: skewY(-26deg);
  background: linear-gradient(145deg, #67e8f9, #4f46e5);
  box-shadow: 0 10px 18px -10px rgba(56, 189, 248, 0.9);
}

.sidebar-logo i:first-child {
  left: 2px;
  top: 1px;
}

.sidebar-logo i:last-child {
  right: 1px;
  bottom: 1px;
  background: linear-gradient(145deg, #818cf8, #d946ef);
}

.sidebar-collapse-btn {
  border: 0;
  color: #dbeafe;
  border-radius: 12px;
  padding: 7px;
  background: transparent;
  transition: background 0.2s ease, color 0.2s ease;
}

.sidebar-collapse-btn:hover {
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
}

.sidebar-new-chat-btn {
  position: relative;
  z-index: 1;
  margin: 8px 20px 10px;
  min-height: 46px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  border: 0;
  border-radius: 11px;
  background: linear-gradient(100deg, #3d8bff 0%, #7c4dff 100%);
  color: white;
  font-size: 16px;
  font-weight: 700;
  box-shadow: 0 18px 34px -20px rgba(96, 165, 250, 0.9);
}

.sidebar-new-chat-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 24px 44px -24px rgba(124, 77, 255, 0.9);
}

.sidebar-current-chat-btn {
  position: relative;
  z-index: 1;
  min-height: 42px;
  margin: 0 20px 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-radius: 10px;
  border: 1px solid rgba(148, 163, 184, 0.28);
  background: rgba(4, 12, 37, 0.56);
  color: rgba(226, 232, 240, 0.92);
  font-size: 14px;
  font-weight: 700;
  transition: border-color 0.2s ease, background 0.2s ease, transform 0.2s ease;
}

.sidebar-current-chat-btn:hover {
  transform: translateY(-1px);
  border-color: rgba(125, 211, 252, 0.48);
  background: rgba(30, 64, 175, 0.22);
}

.history-title {
  position: relative;
  z-index: 1;
  padding: 0 20px;
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: rgba(203, 213, 225, 0.88);
  font-size: 13px;
  font-weight: 700;
}

.history-search-icon {
  width: 16px;
  height: 16px;
  color: rgba(148, 163, 184, 0.9);
}

.conversation-list {
  position: relative;
  z-index: 1;
  padding: 0 18px 0 20px;
  display: grid;
  align-content: start;
  gap: 6px;
}

.conversation-row {
  border-radius: 10px;
  border: 1px solid rgba(148, 163, 184, 0.12);
  padding: 10px 9px;
  background: transparent;
  min-height: 58px;
}

.conversation-row.bg-white\/20 {
  background: linear-gradient(100deg, rgba(59, 97, 206, 0.38), rgba(34, 57, 122, 0.24)) !important;
  border-color: rgba(71, 132, 255, 0.92) !important;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.1), 0 14px 26px -24px rgba(96, 165, 250, 0.85);
}

.conversation-row.bg-white\/10 {
  background: rgba(255, 255, 255, 0.055) !important;
  border-color: rgba(148, 163, 184, 0.12) !important;
}

.conversation-row:hover {
  background: rgba(59, 130, 246, 0.14) !important;
  border-color: rgba(96, 165, 250, 0.35) !important;
}

.conversation-icon {
  display: inline-grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  color: #ffffff;
  font-size: 11px;
  font-weight: 800;
  flex: 0 0 auto;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.18);
}

.conversation-icon--violet {
  background: linear-gradient(145deg, #9b6cff, #604bff);
}

.conversation-icon--cyan {
  background: linear-gradient(145deg, #36e2ff, #2676ff);
}

.conversation-icon--emerald {
  background: linear-gradient(145deg, #5df2a4, #12b887);
}

.conversation-icon--blue {
  background: linear-gradient(145deg, #7cc6ff, #5865ff);
  font-size: 10px;
}

.conversation-icon--amber {
  background: linear-gradient(145deg, #ffbd4a, #f59e0b);
}

.conversation-icon--indigo {
  background: linear-gradient(145deg, #8b7cff, #5545d9);
}

.conversation-title {
  color: #f8fafc;
  font-weight: 700;
  line-height: 1.35;
}

.conversation-copy {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.conversation-copy strong,
.conversation-copy small {
  display: block;
  max-width: 145px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-copy small {
  color: rgba(148, 163, 184, 0.88);
  font-size: 11px;
  font-weight: 500;
  line-height: 1.35;
}

.conversation-actions {
  flex: 0 0 auto;
  opacity: 0.92;
}

.conversation-row:hover .conversation-actions {
  opacity: 1;
}

.conversation-action-btn {
  display: inline-grid;
  place-items: center;
  min-width: 26px;
  height: 26px;
  padding: 0 !important;
  background: rgba(15, 23, 42, 0.48);
  border-color: rgba(148, 163, 184, 0.22) !important;
  color: rgba(203, 213, 225, 0.82);
}

.conversation-delete-btn {
  color: rgba(248, 113, 113, 0.9);
}

.kb-panel {
  position: relative;
  z-index: 1;
  margin: 14px 20px 0;
  border: 1px solid rgba(148, 163, 184, 0.24);
  border-radius: 12px;
  background:
    radial-gradient(circle at 88% 32%, rgba(70, 96, 255, 0.22), transparent 38%),
    rgba(14, 29, 67, 0.38);
  padding: 14px;
}

.kb-title {
  color: #f8fafc !important;
  font-size: 14px !important;
  font-weight: 800;
  letter-spacing: 0 !important;
  text-transform: none;
}

.kb-scope-label {
  color: rgba(203, 213, 225, 0.82) !important;
}

.kb-scope-select {
  min-height: 34px;
  border-radius: 9px !important;
  background: rgba(2, 6, 23, 0.58) !important;
}

.sidebar-upload-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  margin-top: 10px;
  border-radius: 9px;
  border: 0;
  background: linear-gradient(100deg, #2f87ff, #835bff);
  color: #ffffff;
  padding: 10px 12px;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
  box-shadow: 0 14px 28px -18px rgba(96, 165, 250, 0.9);
}

.sidebar-upload-btn:hover {
  transform: translateY(-1px);
}

.kb-status-card {
  border-color: rgba(148, 163, 184, 0.18) !important;
  background: rgba(2, 6, 23, 0.36) !important;
}

.sidebar-footer {
  position: relative;
  z-index: 1;
  padding: 12px 20px 18px;
  color: rgba(148, 163, 184, 0.75);
  font-size: 12px;
  line-height: 1.5;
  text-align: left;
}

.sidebar-shell--elder {
  color: var(--teacher-text-primary, #1f2937);
  background: var(--teacher-sidebar-bg, #eceff3);
  box-shadow: inset -1px 0 0 var(--teacher-border-color, #d8dee8);
  font-size: 15px;
  line-height: 1.65;
}

.sidebar-shell--elder::before,
.sidebar-shell--elder::after {
  opacity: 0.04;
}

.sidebar-shell--elder .text-slate-300,
.sidebar-shell--elder .text-slate-200,
.sidebar-shell--elder .text-slate-400,
.sidebar-shell--elder .text-white,
.sidebar-shell--elder .text-sky-200,
.sidebar-shell--elder .text-sky-100,
.sidebar-shell--elder .text-amber-100,
.sidebar-shell--elder .text-rose-100 {
  color: var(--teacher-text-secondary, #4b5563) !important;
}

.sidebar-shell--elder .bg-white\/10,
.sidebar-shell--elder .bg-slate-900\/60,
.sidebar-shell--elder .bg-slate-900\/40 {
  background: var(--teacher-muted-card-bg, #fafbfc) !important;
}

.sidebar-shell--elder .border-white\/10,
.sidebar-shell--elder .border-white\/20,
.sidebar-shell--elder .border-sky-300\/40 {
  border-color: var(--teacher-border-color, #d8dee8) !important;
}

.sidebar-shell--elder .bg-white\/20 {
  background: #dbeafe !important;
  border-color: #93c5fd !important;
  color: var(--teacher-text-primary, #1f2937) !important;
}

.sidebar-shell--elder .sidebar-brand {
  font-size: 17px;
  font-weight: 800;
  color: var(--teacher-text-primary, #1f2937) !important;
}

.sidebar-shell--elder .sidebar-new-chat-btn,
.sidebar-shell--elder .sidebar-upload-btn {
  min-height: 44px;
  font-size: 15px;
  font-weight: 700;
}

.sidebar-shell--elder .conversation-row {
  border-radius: 12px;
  padding: 10px;
}

.sidebar-shell--elder .conversation-title {
  font-size: 15px !important;
  font-weight: 700;
}

.sidebar-shell--elder .conversation-preview,
.sidebar-shell--elder .kb-scope-label,
.sidebar-shell--elder .kb-status-text,
.sidebar-shell--elder .sidebar-footer,
.sidebar-shell--elder .kb-clear-btn {
  font-size: 13px !important;
  line-height: 1.6;
}

.sidebar-shell--elder .conversation-action-btn {
  min-width: 32px;
  min-height: 32px;
  font-size: 12px;
}

.sidebar-shell--elder .kb-title {
  font-size: 13px;
  color: #6b7280 !important;
}

.sidebar-shell--elder .kb-scope-select {
  min-height: 40px;
  font-size: 13px !important;
}

.sidebar-shell--elder .kb-status-card {
  border-radius: 12px;
}

@keyframes floatGlow {
  0%,
  100% {
    transform: translateY(0);
    opacity: 0.8;
  }
  50% {
    transform: translateY(-12px);
    opacity: 1;
  }
}

/* Reference-grade dashboard sidebar polish */
.sidebar-shell {
  background:
    radial-gradient(circle at 20% 10%, rgba(46, 100, 255, 0.14), transparent 30%),
    linear-gradient(180deg, rgba(4, 15, 44, 0.98), rgba(2, 8, 28, 0.98));
  border-right-color: rgba(100, 126, 198, 0.26);
  box-shadow: inset -1px 0 0 rgba(255, 255, 255, 0.06), 20px 0 55px -48px rgba(50, 114, 255, 0.92);
}

.sidebar-shell::before {
  opacity: 0.42;
}

.sidebar-brand {
  font-size: 21px;
  gap: 12px;
}

.sidebar-brand em {
  display: none;
}

.sidebar-logo {
  width: 30px;
  height: 30px;
}

.sidebar-logo i {
  width: 13px;
  height: 27px;
  border-radius: 4px;
  transform: none;
}

.sidebar-logo i:first-child {
  left: 1px;
  top: 3px;
  background: linear-gradient(180deg, #2af1b1, #32a9ff);
}

.sidebar-logo i:first-child::after {
  content: '';
  position: absolute;
  left: 8px;
  top: -4px;
  width: 13px;
  height: 27px;
  border-radius: 4px;
  background: linear-gradient(180deg, #60d8ff, #7067ff);
}

.sidebar-logo i:last-child {
  right: 2px;
  bottom: 2px;
  background: linear-gradient(180deg, #ffc857, #c45cff);
}

.sidebar-new-chat-btn {
  margin: 12px 20px 12px;
  min-height: 48px;
  border-radius: 10px;
  background: linear-gradient(100deg, #477dff 0%, #7235ee 100%);
  box-shadow: 0 18px 36px -22px rgba(84, 132, 255, 0.92);
}

.sidebar-current-chat-btn {
  min-height: 44px;
  margin-bottom: 24px;
  border-color: rgba(148, 163, 184, 0.3);
  background: rgba(2, 8, 28, 0.52);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
}

.history-title {
  font-size: 14px;
  color: rgba(203, 213, 225, 0.8);
}

.conversation-list {
  gap: 7px;
  padding-right: 14px;
}

.conversation-row {
  min-height: 60px;
  border-radius: 10px;
  padding: 9px 8px 9px 10px;
  background: rgba(255, 255, 255, 0.048) !important;
  border-color: rgba(148, 163, 184, 0.12) !important;
}

.conversation-row.bg-white\/20 {
  background: linear-gradient(110deg, rgba(59, 85, 194, 0.42), rgba(22, 42, 98, 0.38)) !important;
  border-color: rgba(76, 132, 255, 0.86) !important;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.12), 0 16px 36px -30px rgba(74, 134, 255, 0.9);
}

.conversation-row:hover {
  background: rgba(71, 115, 255, 0.16) !important;
}

.conversation-icon {
  width: 31px;
  height: 31px;
  border-radius: 9px;
  font-size: 12px;
}

.conversation-copy strong {
  max-width: 168px;
  font-size: 14px;
}

.conversation-copy small {
  max-width: 168px;
  font-size: 12px;
}

.conversation-action-btn {
  min-width: 25px;
  height: 25px;
  border-radius: 8px !important;
  background: rgba(2, 8, 28, 0.42);
}

.kb-panel {
  margin-top: 16px;
  border-radius: 12px;
  padding: 16px;
  background:
    radial-gradient(circle at 20% 34%, rgba(72, 105, 180, 0.24), transparent 34%),
    linear-gradient(150deg, rgba(14, 31, 76, 0.82), rgba(6, 14, 42, 0.72));
  border-color: rgba(148, 163, 184, 0.25);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.07);
}

.kb-title {
  font-size: 15px !important;
}

.kb-title::after {
  content: '支持 PDF / TXT / DOCX / PPTX / 图片';
  display: block;
  margin-top: 5px;
  color: rgba(203, 213, 225, 0.7);
  font-size: 11px;
  font-weight: 500;
}

.sidebar-upload-btn {
  min-height: 46px;
  border-radius: 10px;
  background: linear-gradient(100deg, #3289ff, #7c4dff);
  font-size: 13px;
}

.kb-status-card {
  border-radius: 10px !important;
  background: rgba(2, 8, 28, 0.5) !important;
}

.kb-status-card::after {
  content: '';
  display: block;
  margin-top: 10px;
  width: 100%;
  height: 5px;
  border-radius: 999px;
  background:
    linear-gradient(90deg, #5c7cff 0 24%, rgba(71, 85, 105, 0.35) 24% 100%);
}

.sidebar-footer {
  color: rgba(148, 163, 184, 0.7);
}

/* 2026 depth pass: compact navigation, stable 3-item history window */
.sidebar-shell:not(.sidebar-shell--elder) {
  background:
    radial-gradient(circle at 14% 4%, rgba(52, 211, 255, 0.18), transparent 28%),
    radial-gradient(circle at 86% 34%, rgba(124, 58, 237, 0.14), transparent 32%),
    linear-gradient(180deg, rgba(4, 15, 45, 0.985), rgba(2, 8, 27, 0.985));
}

.sidebar-shell:not(.sidebar-shell--elder)::after {
  content: '';
  position: absolute;
  inset: 7px 8px 7px 7px;
  pointer-events: none;
  border: 1px solid rgba(125, 211, 252, 0.14);
  border-radius: 14px;
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.08),
    inset -1px 0 0 rgba(124, 58, 237, 0.18);
}

.sidebar-shell:not(.sidebar-shell--elder) .sidebar-top {
  min-height: 58px;
  padding: 14px 18px 6px;
}

.sidebar-shell:not(.sidebar-shell--elder) .sidebar-brand {
  font-size: 20px;
  line-height: 1;
}

.sidebar-shell:not(.sidebar-shell--elder) .sidebar-logo {
  width: 28px;
  height: 28px;
}

.sidebar-shell:not(.sidebar-shell--elder) .sidebar-new-chat-btn {
  min-height: 43px;
  margin: 8px 18px 10px;
  border-radius: 10px;
  font-size: 15px;
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.18),
    0 16px 34px -24px rgba(84, 132, 255, 0.96);
}

.sidebar-shell:not(.sidebar-shell--elder) .sidebar-current-chat-btn {
  justify-content: flex-start;
  min-height: 39px;
  margin: 0 18px 8px;
  padding: 0 18px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 700;
  color: rgba(226, 232, 240, 0.92);
  background:
    linear-gradient(135deg, rgba(12, 24, 62, 0.84), rgba(4, 10, 31, 0.78));
  border-color: rgba(148, 163, 184, 0.22);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.045),
    0 12px 30px -26px rgba(59, 130, 246, 0.74);
}

.sidebar-shell:not(.sidebar-shell--elder) .sidebar-current-chat-btn:first-of-type {
  border-color: rgba(76, 132, 255, 0.72);
  background:
    radial-gradient(circle at 96% 50%, rgba(124, 58, 237, 0.2), transparent 30%),
    linear-gradient(135deg, rgba(17, 40, 92, 0.86), rgba(4, 11, 35, 0.82));
}

.sidebar-shell:not(.sidebar-shell--elder) .sidebar-current-chat-btn svg {
  width: 15px;
  height: 15px;
  margin-right: 8px;
  filter: drop-shadow(0 0 8px rgba(56, 189, 248, 0.35));
}

.sidebar-shell:not(.sidebar-shell--elder) .sidebar-current-chat-btn:hover {
  transform: translateX(3px);
  border-color: rgba(56, 189, 248, 0.58);
  background:
    radial-gradient(circle at 96% 50%, rgba(124, 58, 237, 0.26), transparent 34%),
    linear-gradient(135deg, rgba(20, 50, 116, 0.9), rgba(5, 13, 39, 0.82));
}

.sidebar-shell:not(.sidebar-shell--elder) .history-title {
  margin: 8px 0 9px;
  padding: 0 18px;
  font-size: 13px;
}

.sidebar-shell:not(.sidebar-shell--elder) .conversation-list {
  flex: 0 0 198px;
  min-height: 198px;
  max-height: 198px;
  gap: 7px;
  padding: 0 12px 0 18px;
  overflow-y: auto;
  scrollbar-gutter: stable;
}

.sidebar-shell:not(.sidebar-shell--elder) .conversation-row {
  min-height: 58px;
  max-height: 58px;
  padding: 8px 7px 8px 9px;
  align-items: center;
  background:
    linear-gradient(145deg, rgba(20, 34, 74, 0.76), rgba(5, 12, 35, 0.74)) !important;
  border-color: rgba(148, 163, 184, 0.16) !important;
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.045),
    0 14px 30px -28px rgba(24, 78, 180, 0.75);
}

.sidebar-shell:not(.sidebar-shell--elder) .conversation-row.bg-white\/20 {
  background:
    radial-gradient(circle at 0% 24%, rgba(45, 212, 255, 0.18), transparent 28%),
    linear-gradient(110deg, rgba(42, 66, 151, 0.7), rgba(22, 32, 93, 0.62)) !important;
  border-color: rgba(76, 132, 255, 0.88) !important;
}

.sidebar-shell:not(.sidebar-shell--elder) .conversation-row:hover {
  transform: translateX(2px);
  border-color: rgba(56, 189, 248, 0.44) !important;
}

.sidebar-shell:not(.sidebar-shell--elder) .conversation-icon {
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  border-radius: 9px;
}

.sidebar-shell:not(.sidebar-shell--elder) .conversation-copy strong {
  max-width: 148px;
  font-size: 13px;
  line-height: 1.22;
}

.sidebar-shell:not(.sidebar-shell--elder) .conversation-copy small {
  max-width: 148px;
  margin-top: 2px;
  font-size: 11px;
  line-height: 1.22;
}

.sidebar-shell:not(.sidebar-shell--elder) .conversation-action-btn {
  min-width: 24px;
  height: 24px;
  border-radius: 8px !important;
}

.sidebar-shell:not(.sidebar-shell--elder) .kb-panel {
  margin: 12px 18px 0;
  padding: 12px;
  border-radius: 12px;
}

.sidebar-shell:not(.sidebar-shell--elder) .kb-title {
  font-size: 14px !important;
  line-height: 1.25;
}

.sidebar-shell:not(.sidebar-shell--elder) .kb-title::after {
  content: '支持 PDF / TXT / DOCX / PPTX / 图片';
  margin-top: 4px;
  font-size: 10px;
}

.sidebar-shell:not(.sidebar-shell--elder) .kb-scope-label {
  margin-top: 10px !important;
  margin-bottom: 5px !important;
  font-size: 10px !important;
}

.sidebar-shell:not(.sidebar-shell--elder) .kb-scope-select {
  min-height: 34px;
  font-size: 11px !important;
}

.sidebar-shell:not(.sidebar-shell--elder) .sidebar-upload-btn {
  min-height: 40px;
  margin-top: 8px !important;
  padding: 8px 10px;
  font-size: 11px;
}

.sidebar-shell:not(.sidebar-shell--elder) .kb-status-card {
  margin-top: 9px !important;
  padding: 8px 10px !important;
}

.sidebar-shell:not(.sidebar-shell--elder) .kb-status-text,
.sidebar-shell:not(.sidebar-shell--elder) .kb-clear-btn {
  font-size: 11px !important;
  line-height: 1.35;
}

.sidebar-shell:not(.sidebar-shell--elder) .kb-status-card::after {
  margin-top: 7px;
  height: 4px;
}

.sidebar-shell:not(.sidebar-shell--elder) .sidebar-footer {
  padding: 9px 18px 13px;
  font-size: 10px;
  line-height: 1.4;
}

/* Layout rebalance: less cramped while still showing 3 recent conversations */
.sidebar-shell:not(.sidebar-shell--elder) .sidebar-top {
  min-height: 68px;
  padding: 18px 20px 8px;
}

.sidebar-shell:not(.sidebar-shell--elder) .sidebar-brand {
  font-size: 21px;
}

.sidebar-shell:not(.sidebar-shell--elder) .sidebar-logo {
  width: 30px;
  height: 30px;
}

.sidebar-shell:not(.sidebar-shell--elder) .sidebar-new-chat-btn {
  min-height: 48px;
  margin: 10px 20px 12px;
  font-size: 16px;
}

.sidebar-shell:not(.sidebar-shell--elder) .sidebar-current-chat-btn {
  min-height: 42px;
  margin: 0 20px 9px;
  padding: 0 20px;
  font-size: 14px;
}

.sidebar-shell:not(.sidebar-shell--elder) .history-title {
  margin: 13px 0 10px;
  padding: 0 20px;
  font-size: 14px;
}

.sidebar-shell:not(.sidebar-shell--elder) .conversation-list {
  flex: 0 0 258px;
  min-height: 258px;
  max-height: 258px;
  padding: 0 14px 0 20px;
  gap: 9px;
}

.sidebar-shell:not(.sidebar-shell--elder) .conversation-row {
  min-height: 78px;
  max-height: 78px;
  padding: 11px 9px 11px 10px;
  border-radius: 12px;
}

.sidebar-shell:not(.sidebar-shell--elder) .conversation-icon {
  width: 34px;
  height: 34px;
  flex-basis: 34px;
  border-radius: 10px;
}

.sidebar-shell:not(.sidebar-shell--elder) .conversation-copy strong {
  max-width: 150px;
  font-size: 14px;
  line-height: 1.35;
}

.sidebar-shell:not(.sidebar-shell--elder) .conversation-copy small {
  max-width: 150px;
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.35;
}

.sidebar-shell:not(.sidebar-shell--elder) .conversation-action-btn {
  min-width: 27px;
  height: 27px;
}

.sidebar-shell:not(.sidebar-shell--elder) .kb-panel {
  margin: 16px 20px 0;
  padding: 16px;
  border-radius: 14px;
}

.sidebar-shell:not(.sidebar-shell--elder) .kb-title {
  font-size: 16px !important;
}

.sidebar-shell:not(.sidebar-shell--elder) .kb-title::after {
  margin-top: 6px;
  font-size: 11px;
}

.sidebar-shell:not(.sidebar-shell--elder) .kb-scope-label {
  margin-top: 13px !important;
  margin-bottom: 7px !important;
  font-size: 11px !important;
}

.sidebar-shell:not(.sidebar-shell--elder) .kb-scope-select {
  min-height: 38px;
  font-size: 12px !important;
}

.sidebar-shell:not(.sidebar-shell--elder) .sidebar-upload-btn {
  min-height: 48px;
  margin-top: 10px !important;
  padding: 10px 12px;
  font-size: 12px;
}

.sidebar-shell:not(.sidebar-shell--elder) .kb-status-card {
  margin-top: 12px !important;
  padding: 12px !important;
}

.sidebar-shell:not(.sidebar-shell--elder) .kb-status-text,
.sidebar-shell:not(.sidebar-shell--elder) .kb-clear-btn {
  font-size: 12px !important;
  line-height: 1.45;
}

.sidebar-shell:not(.sidebar-shell--elder) .kb-status-card::after {
  margin-top: 9px;
  height: 5px;
}

.sidebar-shell:not(.sidebar-shell--elder) .sidebar-footer {
  padding: 13px 20px 18px;
  font-size: 11px;
}

/* Final sidebar fit: keep the material panel fully visible */
.sidebar-shell:not(.sidebar-shell--elder) .sidebar-top {
  min-height: 64px;
  padding: 16px 20px 6px;
}

.sidebar-shell:not(.sidebar-shell--elder) .sidebar-new-chat-btn {
  min-height: 46px;
  margin: 9px 20px 11px;
}

.sidebar-shell:not(.sidebar-shell--elder) .sidebar-current-chat-btn {
  min-height: 40px;
  margin-bottom: 8px;
}

.sidebar-shell:not(.sidebar-shell--elder) .history-title {
  margin: 10px 0 9px;
}

.sidebar-shell:not(.sidebar-shell--elder) .conversation-list {
  flex: 0 0 223px;
  min-height: 223px;
  max-height: 223px;
  gap: 8px;
}

.sidebar-shell:not(.sidebar-shell--elder) .conversation-row {
  min-height: 69px;
  max-height: 69px;
  padding-top: 9px;
  padding-bottom: 9px;
}

.sidebar-shell:not(.sidebar-shell--elder) .conversation-icon {
  width: 32px;
  height: 32px;
  flex-basis: 32px;
}

.sidebar-shell:not(.sidebar-shell--elder) .kb-panel {
  margin-top: 12px;
  padding: 13px;
}

.sidebar-shell:not(.sidebar-shell--elder) .kb-title {
  font-size: 15px !important;
}

.sidebar-shell:not(.sidebar-shell--elder) .kb-scope-label {
  margin-top: 10px !important;
  margin-bottom: 6px !important;
}

.sidebar-shell:not(.sidebar-shell--elder) .kb-scope-select {
  min-height: 36px;
}

.sidebar-shell:not(.sidebar-shell--elder) .sidebar-upload-btn {
  min-height: 44px;
  margin-top: 9px !important;
  padding: 9px 11px;
}

.sidebar-shell:not(.sidebar-shell--elder) .kb-status-card {
  margin-top: 10px !important;
  padding: 10px !important;
}

.sidebar-shell:not(.sidebar-shell--elder) .sidebar-footer {
  padding: 9px 20px 12px;
}
</style>
