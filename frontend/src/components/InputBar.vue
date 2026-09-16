<script setup>
import { ref, nextTick, onMounted, onBeforeUnmount } from 'vue'

defineProps({
  disabled: Boolean,
  ragActive: Boolean,
  uiTheme: {
    type: String,
    default: 'dark',
  },
  planStatusText: {
    type: String,
    default: '',
  },
  planStatusTone: {
    type: String,
    default: 'text-slate-400',
  },
  height: {
    type: Number,
    default: 0,
  },
})
const emit = defineEmits([
  'send',
  'extract-plan',
  'generate-image',
  'generate-ppt',
  'generate-video',
  'generate-game',
  'export-doc',
  'edit-ppt',
  'open-study-tools',
])

const inputText = ref('')
const imageFile = ref(null)
const imagePreview = ref(null)
const fileInput = ref(null)
const isRecording = ref(false)
const speechError = ref('')
const actionCenterRef = ref(null)
const showActionCenter = ref(false)
const actionCenterDirection = ref('up')
const actionCenterMaxHeight = ref(420)
let speechRecognition = null
let speechBaseText = ''
let speechFinalText = ''

const ACTION_CENTER_CATEGORIES = [
  {
    key: 'courseware',
    label: '课件与导出',
    hint: 'PPT / 教案 / 迭代修改',
    actions: [
      { label: '导出 PPT 课件', icon: '📊', event: 'generate-ppt' },
      { label: '导出 Word 教案', icon: '📄', event: 'export-doc' },
      { label: '迭代修改 PPT', icon: '🛠️', event: 'edit-ppt' },
    ],
  },
  {
    key: 'creative',
    label: '多媒体生成',
    hint: '微课 / 配图 / 互动',
    actions: [
      { label: '生成数字人微课', icon: '🎥', event: 'generate-video' },
      { label: '生成课堂配图', icon: '🎨', event: 'generate-image' },
      { label: '生成互动游戏', icon: '🎮', event: 'generate-game' },
    ],
  },
  {
    key: 'study_pack',
    label: '学习巩固',
    hint: '提纲 / 闪卡 / 题目',
    actions: [
      { label: '生成学习巩固包', icon: '🧠', event: 'open-study-tools' },
    ],
  },
]

const ACTION_CENTER_TEACHER_SECTIONS = [
  {
    key: 'teacher-common',
    label: '常用功能',
    hint: '导出与快速修改',
    actions: [
      { label: '导出 PPT 课件', icon: '📊', event: 'generate-ppt' },
      { label: '导出 Word 教案', icon: '📄', event: 'export-doc' },
      { label: '修改 PPT', icon: '🛠️', event: 'edit-ppt' },
      { label: '生成课堂配图', icon: '🎨', event: 'generate-image' },
    ],
  },
  {
    key: 'teacher-extended',
    label: '扩展功能',
    hint: '课堂素材与巩固训练',
    actions: [
      { label: '生成数字人微课', icon: '🎥', event: 'generate-video' },
      { label: '生成互动游戏', icon: '🎮', event: 'generate-game' },
      { label: '生成学习资料包', icon: '🧠', event: 'open-study-tools' },
    ],
  },
]

function closeActionCenter() {
  showActionCenter.value = false
}

function updateActionCenterDirection() {
  if (!actionCenterRef.value) return
  const hostRect = actionCenterRef.value.getBoundingClientRect()
  const visualViewport = window.visualViewport
  const viewportTop = visualViewport?.offsetTop || 0
  const viewportHeight = visualViewport?.height || window.innerHeight
  const viewportPadding = 12
  const spaceAbove = Math.max(0, hostRect.top - viewportTop - viewportPadding)
  const spaceBelow = Math.max(0, viewportTop + viewportHeight - hostRect.bottom - viewportPadding)
  const estimatedPanelHeight = 360

  if (spaceBelow >= estimatedPanelHeight) {
    actionCenterDirection.value = 'down'
  } else if (spaceAbove >= estimatedPanelHeight) {
    actionCenterDirection.value = 'up'
  } else {
    actionCenterDirection.value = spaceAbove >= spaceBelow ? 'up' : 'down'
  }

  const activeSpace = actionCenterDirection.value === 'down' ? spaceBelow : spaceAbove
  actionCenterMaxHeight.value = Math.max(180, Math.floor(activeSpace))
}

function handleViewportResize() {
  if (!showActionCenter.value) return
  updateActionCenterDirection()
}

function toggleActionCenter() {
  if (showActionCenter.value) {
    closeActionCenter()
    return
  }
  showActionCenter.value = true
  nextTick(() => {
    updateActionCenterDirection()
  })
}

function runCenterAction(eventName) {
  emit(eventName)
  closeActionCenter()
}

function handleWindowClick(event) {
  if (!showActionCenter.value) return
  const target = event.target
  if (actionCenterRef.value && target instanceof Node && actionCenterRef.value.contains(target)) {
    return
  }
  closeActionCenter()
}

function initSpeechRecognition() {
  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition
  if (!SpeechRecognition) return null
  const recognition = new SpeechRecognition()
  recognition.lang = 'zh-CN'
  recognition.interimResults = true
  recognition.continuous = false
  recognition.onstart = () => {
    isRecording.value = true
    speechError.value = ''
  }
  recognition.onend = () => {
    isRecording.value = false
  }
  recognition.onerror = (event) => {
    isRecording.value = false
    speechError.value = `语音识别失败：${event?.error || '未知错误'}`
  }
  recognition.onresult = (event) => {
    let interim = ''
    for (let i = event.resultIndex; i < event.results.length; i += 1) {
      const transcript = event.results[i][0]?.transcript || ''
      if (event.results[i].isFinal) {
        speechFinalText += transcript
      } else {
        interim += transcript
      }
    }
    inputText.value = `${speechBaseText}${speechFinalText}${interim}`.trimStart()
  }
  return recognition
}

function stopSpeechRecognition() {
  if (speechRecognition && isRecording.value) {
    speechRecognition.stop()
  }
}

function toggleSpeechRecognition() {
  speechError.value = ''
  if (!speechRecognition) {
    speechRecognition = initSpeechRecognition()
  }
  if (!speechRecognition) {
    speechError.value = '当前浏览器不支持语音输入'
    return
  }
  if (isRecording.value) {
    speechRecognition.stop()
    return
  }
  speechBaseText = inputText.value ? `${inputText.value.trim()} ` : ''
  speechFinalText = ''
  speechRecognition.start()
}

function triggerFileInput() {
  fileInput.value?.click()
}

function handleFileChange(event) {
  const file = event.target.files[0]
  if (!file) return
  imageFile.value = file
  const reader = new FileReader()
  reader.onload = (e) => {
    imagePreview.value = e.target.result
  }
  reader.readAsDataURL(file)
}

function removeImage() {
  imageFile.value = null
  imagePreview.value = null
  if (fileInput.value) {
    fileInput.value.value = ''
  }
}

function clearInput() {
  inputText.value = ''
  removeImage()
  stopSpeechRecognition()
}

function handleSend() {
  if (!inputText.value.trim() && !imageFile.value) return

  stopSpeechRecognition()

  emit('send', {
    text: inputText.value,
    imageFile: imageFile.value,
    imagePreview: imagePreview.value,
  })

  inputText.value = ''
  imageFile.value = null
  imagePreview.value = null
  if (fileInput.value) {
    fileInput.value.value = ''
  }
}

function handleKeydown(event) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    handleSend()
  }
}

onMounted(() => {
  if (window.SpeechRecognition || window.webkitSpeechRecognition) {
    speechRecognition = initSpeechRecognition()
  }
  window.addEventListener('click', handleWindowClick)
  window.addEventListener('resize', handleViewportResize)
  window.visualViewport?.addEventListener('resize', handleViewportResize)
})

onBeforeUnmount(() => {
  stopSpeechRecognition()
  speechRecognition = null
  window.removeEventListener('click', handleWindowClick)
  window.removeEventListener('resize', handleViewportResize)
  window.visualViewport?.removeEventListener('resize', handleViewportResize)
})
</script>

<template>
  <div
    class="input-shell border-t border-white/10 bg-slate-900/70 px-4 py-3 backdrop-blur"
    :class="uiTheme === 'elder' ? 'input-shell--elder' : ''"
  >
    <div v-if="ragActive" class="mb-2">
      <span class="inline-flex items-center gap-1 rounded-full bg-emerald-400/10 text-emerald-200 border border-emerald-400/30 px-2 py-0.5 text-xs font-medium">
        📚 RAG 增强模式已开启
      </span>
    </div>
    <!-- 图片预览 -->
    <div v-if="imagePreview" class="mb-2 flex items-start gap-2">
      <div class="relative">
        <img
          :src="imagePreview"
          alt="预览"
          class="w-20 h-20 rounded-lg object-cover border border-white/10"
        />
        <button
          @click="removeImage"
          class="absolute -top-2 -right-2 w-5 h-5 bg-red-500 text-white rounded-full flex items-center justify-center text-xs hover:bg-red-600 transition"
        >
          ✕
        </button>
      </div>
      <span class="text-xs text-slate-400 mt-1">{{ imageFile?.name }}</span>
    </div>

    <!-- 操作按钮区 -->
    <div class="input-actions relative flex flex-wrap items-center gap-2" ref="actionCenterRef">
      <!-- 上传图片按钮 -->
      <button
        @click="triggerFileInput"
        class="input-action-btn input-action-btn--icon flex-shrink-0 p-2.5 rounded-xl text-slate-300 hover:text-sky-300 hover:bg-white/10 transition"
        title="上传教材图片"
        :disabled="disabled"
      >
        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
            d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
        </svg>
      </button>

      <!-- 语音输入按钮 -->
      <button
        @click="toggleSpeechRecognition"
        class="input-action-btn input-action-btn--icon flex-shrink-0 p-2.5 rounded-xl transition"
        :class="isRecording
          ? 'bg-rose-500/20 text-rose-200 animate-pulse'
          : 'text-slate-300 hover:text-emerald-300 hover:bg-white/10'"
        title="语音输入"
        :disabled="disabled"
      >
        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
            d="M12 3a3 3 0 013 3v6a3 3 0 11-6 0V6a3 3 0 013-3zm5 9a5 5 0 01-10 0m10 0v1a7 7 0 01-14 0v-1m7 8v3m-3 0h6" />
        </svg>
      </button>

      <!-- 生成/导出中心 -->
      <button
        @click.stop="toggleActionCenter"
        class="input-action-btn generate-center-btn flex-shrink-0 px-3 py-2 rounded-xl bg-sky-400 text-slate-900 text-xs font-semibold hover:bg-sky-300 transition disabled:opacity-40 disabled:cursor-not-allowed"
        title="打开生成/导出中心"
        :disabled="disabled"
      >
        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
            d="M3 11l18-8-8 18-2-7-8-3z" />
        </svg>
        生成 / 导出
      </button>

      <!-- 教学计划入口 -->
      <button
        @click="emit('extract-plan')"
        class="input-action-btn plan-editor-btn flex-shrink-0 px-3 py-2 rounded-xl border border-white/20 text-slate-200 text-xs font-medium hover:bg-white/10 transition disabled:opacity-40 disabled:cursor-not-allowed"
        title="教学计划分层编辑"
        :disabled="disabled"
      >
        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
            d="M8 7V3m8 4V3M5 11h14M6 5h12a2 2 0 012 2v12a2 2 0 01-2 2H6a2 2 0 01-2-2V7a2 2 0 012-2z" />
        </svg>
        教学计划编辑器
      </button>

      <button
        type="button"
        @click="clearInput"
        class="input-action-btn clear-input-btn flex-shrink-0 px-3 py-2 rounded-xl border border-white/20 text-slate-300 text-xs font-medium hover:bg-white/10 transition disabled:opacity-40 disabled:cursor-not-allowed"
        title="清空输入"
        :disabled="disabled || (!inputText && !imageFile)"
      >
        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
            d="M6 7h12m-9 0V5a1 1 0 011-1h4a1 1 0 011 1v2m-7 4v6m4-6v6M8 7l1 13h6l1-13" />
        </svg>
        清空输入
      </button>

      <div
        v-if="showActionCenter"
        class="action-center-panel"
        :class="`action-center-panel--${actionCenterDirection}`"
        :style="{ maxHeight: `${actionCenterMaxHeight}px` }"
        @click.stop
      >
        <div class="mb-2 flex items-center justify-between">
          <p class="text-[11px] text-slate-300 font-medium">功能导航</p>
          <button class="text-[11px] text-slate-400 hover:text-slate-200" @click="closeActionCenter">关闭</button>
        </div>

        <template v-if="uiTheme === 'elder'">
          <div
            v-for="section in ACTION_CENTER_TEACHER_SECTIONS"
            :key="section.key"
            class="action-section"
          >
            <p class="action-section-title">{{ section.label }}</p>
            <p class="action-category-hint">{{ section.hint }}</p>
            <button
              v-for="item in section.actions"
              :key="item.event"
              class="action-item-btn"
              @click="runCenterAction(item.event)"
            >
              <span>{{ item.icon }}</span>
              <span>{{ item.label }}</span>
            </button>
          </div>
        </template>

        <template v-else>
          <div
            v-for="category in ACTION_CENTER_CATEGORIES"
            :key="category.key"
            class="action-section"
          >
            <p class="action-section-title">{{ category.label }}</p>
            <p class="action-category-hint">{{ category.hint }}</p>
            <button
              v-for="item in category.actions"
              :key="item.event"
              class="action-item-btn"
              @click="runCenterAction(item.event)"
            >
              <span>{{ item.icon }}</span>
              <span>{{ item.label }}</span>
            </button>
          </div>
        </template>
      </div>

      <div v-if="planStatusText" class="input-helper-text basis-full text-[11px]" :class="planStatusTone">
        {{ planStatusText }}
      </div>
      <p class="input-helper-text mobile-helper-text basis-full text-[11px] text-slate-400/90">
        最简单用法：输入需求 →（可选上传教材）→ 点“生成/导出”选择具体产物；需要调整再进“教学计划编辑”。
      </p>
      <input
        ref="fileInput"
        type="file"
        accept="image/*"
        class="hidden"
        @change="handleFileChange"
      />
    </div>

    <!-- 输入区 -->
    <div class="mt-2 flex flex-nowrap items-end gap-2">
      <textarea
        v-model="inputText"
        @keydown="handleKeydown"
        :disabled="disabled"
        rows="1"
        placeholder="请输入您的备课需求，例如：请帮我设计一节小学三年级数学课的教案..."
        class="input-textarea flex-1 min-w-0 w-full resize-none rounded-2xl border border-white/10 bg-slate-900/60 px-4 py-2.5 text-sm text-slate-100 placeholder-slate-500 shadow-sm focus:outline-none focus:ring-2 focus:ring-sky-400/60 focus:border-transparent transition disabled:opacity-50"
        :style="height ? { height: `${height}px` } : { maxHeight: '120px', minHeight: '42px' }"
        @input="if (!height) { $event.target.style.height = 'auto'; $event.target.style.height = Math.min($event.target.scrollHeight, 120) + 'px' }"
      ></textarea>

      <!-- 发送按钮 -->
      <button
        @click="handleSend"
        :disabled="disabled || (!inputText.trim() && !imageFile)"
        class="send-btn flex-shrink-0 p-2.5 rounded-2xl bg-gradient-to-br from-sky-400 via-indigo-400 to-fuchsia-400 text-slate-900 hover:opacity-90 transition disabled:opacity-40 disabled:cursor-not-allowed shadow-lg shadow-sky-500/30"
        title="发送"
      >
        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
            d="M12 19V5m0 0l-7 7m7-7l7 7" />
        </svg>
      </button>
    </div>

    <!-- 底部提示 -->
    <p class="text-center text-xs text-slate-400 mt-2">
      教策云枢可能会产生不准确的信息，建议仔细核查生成的教案内容
    </p>
    <p v-if="speechError" class="text-center text-xs text-rose-300 mt-1">{{ speechError }}</p>
  </div>
</template>

<style scoped>
.input-shell {
  padding-bottom: calc(0.75rem + env(safe-area-inset-bottom, 0px));
  border-top: 0 !important;
  background: transparent !important;
  padding: 16px 22px 14px !important;
  backdrop-filter: none;
}

.input-actions {
  padding: 0 0 12px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.18);
  gap: 12px !important;
}

.input-action-btn {
  border-radius: 12px !important;
}

.generate-center-btn,
.plan-editor-btn {
  min-height: 38px;
  padding: 0 26px !important;
}

.generate-center-btn {
  border: 1px solid rgba(56, 189, 248, 0.7) !important;
  background: linear-gradient(100deg, rgba(40, 120, 255, 0.78), rgba(130, 74, 255, 0.8)) !important;
  color: #ffffff !important;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.16), 0 14px 26px -20px rgba(96, 165, 250, 0.9);
}

.plan-editor-btn {
  border-color: rgba(99, 102, 241, 0.32) !important;
  background: rgba(59, 130, 246, 0.16) !important;
  color: #dbeafe !important;
}

.clear-input-btn {
  margin-left: auto;
  min-height: 38px;
  padding: 0 18px !important;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border-color: rgba(148, 163, 184, 0.24) !important;
  background: rgba(2, 6, 23, 0.2) !important;
  color: rgba(203, 213, 225, 0.78) !important;
}

.clear-input-btn:not(:disabled):hover {
  color: #ffffff !important;
  border-color: rgba(248, 113, 113, 0.35) !important;
  background: rgba(248, 113, 113, 0.1) !important;
}

.input-action-btn--icon {
  width: 38px;
  height: 38px;
  color: #e0f2fe !important;
  padding: 0 !important;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.input-helper-text {
  display: block;
  margin-top: 2px;
}

.mobile-helper-text {
  display: none;
}

.input-textarea {
  min-height: 102px !important;
  border-radius: 16px !important;
  border-color: rgba(148, 163, 184, 0.22) !important;
  background: rgba(5, 12, 40, 0.34) !important;
  padding: 18px 20px !important;
  color: #f8fafc !important;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
}

.input-textarea::placeholder {
  color: rgba(148, 163, 184, 0.86);
}

.send-btn {
  width: 52px;
  height: 52px;
  min-height: 52px;
  border-radius: 999px !important;
  background: linear-gradient(145deg, #356dff, #7253ff) !important;
  color: white !important;
  box-shadow: 0 18px 34px -20px rgba(96, 165, 250, 0.9) !important;
}

.send-btn svg {
  transform: rotate(90deg);
}

@media (max-width: 768px) {
  .input-shell {
    padding-top: 0.5rem;
    padding-bottom: calc(0.5rem + env(safe-area-inset-bottom, 0px));
  }

  .mobile-helper-text {
    display: none;
  }

  .input-actions {
    gap: 0.4rem;
  }

  .clear-input-btn {
    margin-left: 0;
  }

  .input-textarea {
    min-height: 36px !important;
    max-height: 84px !important;
    padding-top: 0.5rem;
    padding-bottom: 0.5rem;
  }
}

.action-center-panel {
  position: absolute;
  left: 0;
  z-index: 120;
  width: min(340px, calc(100vw - 32px));
  max-height: min(62vh, 420px);
  overflow-y: auto;
  overscroll-behavior: contain;
  border-radius: 14px;
  border: 1px solid rgba(148, 163, 184, 0.28);
  background: rgba(2, 6, 23, 0.95);
  padding: 10px;
  box-shadow: 0 18px 32px -24px rgba(2, 6, 23, 0.8);
  backdrop-filter: blur(14px);
}

.action-center-panel--down {
  top: calc(100% + 8px);
  bottom: auto;
}

.action-center-panel--up {
  top: auto;
  bottom: calc(100% + 8px);
}

.action-category-btn,
.action-item-btn {
  width: 100%;
  border-radius: 10px;
  border: 1px solid rgba(148, 163, 184, 0.24);
  background: rgba(15, 23, 42, 0.85);
  color: #e2e8f0;
  padding: 9px 10px;
  text-align: left;
  transition: border-color 0.2s ease, transform 0.2s ease, background 0.2s ease;
}

.action-category-btn + .action-category-btn,
.action-item-btn + .action-item-btn {
  margin-top: 8px;
}

.action-category-btn:hover,
.action-item-btn:hover {
  border-color: rgba(125, 211, 252, 0.6);
  background: rgba(15, 23, 42, 1);
  transform: translateY(-1px);
}

.action-category-title {
  display: block;
  font-size: 12px;
  font-weight: 600;
}

.action-category-hint {
  display: block;
  margin-top: 3px;
  font-size: 11px;
  color: rgba(148, 163, 184, 0.95);
}

.action-item-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.action-section + .action-section {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed rgba(148, 163, 184, 0.28);
}

.action-section-title {
  font-size: 12px;
  font-weight: 700;
  color: #dbeafe;
  margin-bottom: 6px;
}

.input-shell--elder {
  background: #f7f8fa !important;
  border-top-color: #d8dee8 !important;
  color: #1f2937;
}

.input-shell--elder .text-slate-400,
.input-shell--elder .text-slate-300,
.input-shell--elder .text-slate-200 {
  color: #475569 !important;
}

.input-shell--elder .text-slate-400\/90 {
  color: #4b5563 !important;
}

.input-shell--elder .text-slate-100 {
  color: #0f172a !important;
}

.input-shell--elder .input-actions {
  gap: 10px;
}

.input-shell--elder .input-action-btn {
  font-size: 14px !important;
  font-weight: 600;
}

.input-shell--elder .input-action-btn--icon {
  width: 44px;
  height: 44px;
  padding: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #d8dee8;
  background: #ffffff;
  color: #334155 !important;
}

.input-shell--elder .input-action-btn--icon:hover {
  background: #f8fafc;
}

.input-shell--elder .generate-center-btn {
  background: #2563eb !important;
  color: #ffffff !important;
  font-size: 14px;
}

.input-shell--elder .generate-center-btn:hover {
  background: #1d4ed8 !important;
}

.input-shell--elder .plan-editor-btn {
  border-color: #d8dee8 !important;
  background: #ffffff !important;
  color: #1f2937 !important;
}

.input-shell--elder .input-textarea {
  font-size: 16px !important;
  line-height: 1.65;
  min-height: 48px;
  border-color: #d8dee8;
  background: #ffffff;
  color: #0f172a;
}

.input-shell--elder .input-textarea::placeholder {
  color: #64748b;
}

.input-shell--elder .action-center-panel {
  background: #ffffff;
  border-color: #d8dee8;
  max-height: min(68vh, 460px);
}

.input-shell--elder .action-category-btn,
.input-shell--elder .action-item-btn {
  background: #fafbfc;
  color: #1f2937;
  border-color: #d8dee8;
  padding: 12px;
}

.input-shell--elder .action-section + .action-section {
  border-top-color: #d8dee8;
}

.input-shell--elder .action-category-title {
  font-size: 15px;
}

.input-shell--elder .action-section-title {
  font-size: 14px;
  color: #1e293b;
}

.input-shell--elder .action-category-hint {
  font-size: 13px;
  color: #6b7280;
}

.input-shell--elder .input-helper-text {
  font-size: 13px;
  line-height: 1.6;
}

.input-shell--elder .send-btn {
  background: #2563eb !important;
  color: #ffffff !important;
  box-shadow: none;
}

.input-shell--elder .send-btn:hover {
  background: #1d4ed8 !important;
}

/* Reference-grade input bar */
.input-shell {
  padding: 18px 22px 14px !important;
}

.input-actions {
  min-height: 48px;
  padding-bottom: 13px;
  border-bottom-color: rgba(148, 163, 184, 0.18);
}

.input-action-btn--icon {
  width: 44px;
  height: 44px;
  border: 1px solid rgba(148, 163, 184, 0.24);
  background: rgba(2, 8, 28, 0.22);
  border-radius: 14px !important;
}

.generate-center-btn,
.plan-editor-btn,
.clear-input-btn {
  min-height: 44px;
  border-radius: 13px !important;
  display: inline-flex;
  align-items: center;
  gap: 9px;
}

.generate-center-btn {
  padding: 0 30px !important;
  background: linear-gradient(100deg, #2f86ff 0%, #654cff 100%) !important;
  border-color: rgba(88, 166, 255, 0.72) !important;
}

.plan-editor-btn {
  padding: 0 22px !important;
  border-color: rgba(99, 102, 241, 0.34) !important;
  background: rgba(36, 78, 171, 0.2) !important;
}

.clear-input-btn {
  padding: 0 20px !important;
  border-color: rgba(148, 163, 184, 0.24) !important;
}

.input-helper-text:not(.mobile-helper-text) {
  flex-basis: 100%;
  padding-left: 2px;
}

.input-textarea {
  min-height: 106px !important;
  border-radius: 15px !important;
  border-color: rgba(148, 163, 184, 0.25) !important;
  background: rgba(3, 9, 35, 0.36) !important;
  padding: 19px 22px !important;
}

.send-btn {
  width: 54px;
  height: 54px;
  min-height: 54px;
  align-self: flex-end;
  margin-bottom: 6px;
  background: linear-gradient(145deg, #3159df, #5943d7) !important;
  box-shadow: 0 18px 38px -19px rgba(79, 70, 229, 0.95) !important;
}

.action-center-panel {
  background: rgba(3, 8, 28, 0.96);
  border-color: rgba(96, 165, 250, 0.28);
  box-shadow: 0 22px 50px -26px rgba(2, 6, 23, 0.9);
}

.input-shell:not(.input-shell--elder) .action-center-panel {
  width: min(430px, calc(100vw - 32px));
  padding: 14px;
  border-radius: 20px;
  border-color: rgba(126, 164, 255, 0.3);
  background:
    radial-gradient(circle at 12% 0%, rgba(42, 213, 255, 0.15), transparent 35%),
    radial-gradient(circle at 86% 0%, rgba(196, 89, 255, 0.13), transparent 35%),
    linear-gradient(145deg, rgba(10, 22, 61, 0.98), rgba(4, 9, 31, 0.96));
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.1),
    0 34px 90px -50px rgba(0, 0, 0, 0.9),
    0 20px 58px -46px rgba(56, 189, 248, 0.72);
  backdrop-filter: blur(26px) saturate(118%);
}

.input-shell:not(.input-shell--elder) .action-center-panel::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  border-radius: inherit;
  background:
    linear-gradient(90deg, rgba(56, 189, 248, 0.28), rgba(124, 58, 237, 0.12), transparent 78%),
    radial-gradient(circle at 18% 16%, rgba(255, 255, 255, 0.16) 0 1px, transparent 2px),
    radial-gradient(circle at 72% 22%, rgba(125, 211, 252, 0.2) 0 1px, transparent 2px);
  opacity: 0.68;
  mask-image: linear-gradient(to bottom, #000, transparent 48%);
}

.input-shell:not(.input-shell--elder) .action-section,
.input-shell:not(.input-shell--elder) .action-category-btn,
.input-shell:not(.input-shell--elder) .action-item-btn {
  position: relative;
  z-index: 1;
}

.input-shell:not(.input-shell--elder) .action-category-btn,
.input-shell:not(.input-shell--elder) .action-item-btn {
  min-height: 62px;
  border-radius: 16px;
  border-color: rgba(148, 163, 184, 0.2);
  background:
    radial-gradient(circle at 10% 18%, rgba(56, 189, 248, 0.12), transparent 38%),
    linear-gradient(145deg, rgba(16, 35, 78, 0.74), rgba(5, 11, 35, 0.62));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.07);
}

.input-shell:not(.input-shell--elder) .action-category-btn:hover,
.input-shell:not(.input-shell--elder) .action-item-btn:hover {
  border-color: rgba(125, 211, 252, 0.58);
  background:
    radial-gradient(circle at 10% 18%, rgba(56, 189, 248, 0.18), transparent 38%),
    linear-gradient(145deg, rgba(27, 58, 124, 0.82), rgba(9, 19, 55, 0.72));
  box-shadow: 0 16px 34px -28px rgba(56, 189, 248, 0.7);
}

.input-shell:not(.input-shell--elder) .action-category-title,
.input-shell:not(.input-shell--elder) .action-section-title {
  color: #f8fbff;
  font-weight: 800;
}

.input-shell:not(.input-shell--elder) .action-category-hint {
  color: rgba(203, 213, 225, 0.74);
}

.input-shell:not(.input-shell--elder) .action-section + .action-section {
  border-top-color: rgba(148, 163, 184, 0.18);
}

/* Composer polish: layered command deck matching the dashboard hero */
.input-shell:not(.input-shell--elder) {
  position: relative;
  padding: 18px 26px 18px !important;
  border-top: 0 !important;
  background: transparent !important;
}

.input-shell:not(.input-shell--elder)::before {
  content: '';
  position: absolute;
  inset: 18px 24px auto;
  height: 1px;
  pointer-events: none;
  background: linear-gradient(90deg, transparent, rgba(125, 211, 252, 0.32), rgba(196, 181, 253, 0.22), transparent);
}

.input-shell:not(.input-shell--elder) .input-actions {
  min-height: 54px;
  padding: 0 0 15px;
  gap: 12px !important;
  border-bottom: 1px solid rgba(148, 163, 184, 0.2);
}

.input-shell:not(.input-shell--elder) .input-action-btn {
  position: relative;
  border-radius: 14px !important;
  transition:
    transform 0.22s ease,
    border-color 0.22s ease,
    background 0.22s ease,
    box-shadow 0.22s ease;
}

.input-shell:not(.input-shell--elder) .input-action-btn:not(:disabled):hover {
  transform: translateY(-2px);
}

.input-shell:not(.input-shell--elder) .input-action-btn--icon {
  width: 46px;
  height: 46px;
  border: 1px solid rgba(125, 211, 252, 0.22);
  color: rgba(226, 232, 240, 0.92) !important;
  background:
    radial-gradient(circle at 35% 20%, rgba(255, 255, 255, 0.12), transparent 34%),
    linear-gradient(145deg, rgba(15, 31, 74, 0.7), rgba(4, 12, 37, 0.62));
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.08),
    0 16px 30px -26px rgba(56, 189, 248, 0.78);
}

.input-shell:not(.input-shell--elder) .input-action-btn--icon:hover {
  border-color: rgba(125, 211, 252, 0.58);
  background:
    radial-gradient(circle at 35% 20%, rgba(255, 255, 255, 0.16), transparent 36%),
    linear-gradient(145deg, rgba(21, 52, 121, 0.76), rgba(6, 16, 46, 0.68));
}

.input-shell:not(.input-shell--elder) .generate-center-btn {
  min-height: 46px;
  padding: 0 34px !important;
  border: 1px solid rgba(125, 211, 252, 0.58) !important;
  color: #ffffff !important;
  background:
    radial-gradient(circle at 18% 0%, rgba(255, 255, 255, 0.18), transparent 36%),
    linear-gradient(100deg, #3188ff 0%, #4f6bff 47%, #7b3dff 100%) !important;
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.2),
    0 20px 40px -25px rgba(56, 189, 248, 0.92);
}

.input-shell:not(.input-shell--elder) .plan-editor-btn {
  min-height: 46px;
  padding: 0 24px !important;
  border: 1px solid rgba(96, 165, 250, 0.32) !important;
  color: rgba(226, 232, 240, 0.95) !important;
  background:
    radial-gradient(circle at 12% 0%, rgba(96, 165, 250, 0.16), transparent 40%),
    linear-gradient(145deg, rgba(18, 43, 98, 0.64), rgba(6, 15, 45, 0.58)) !important;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.08);
}

.input-shell:not(.input-shell--elder) .clear-input-btn {
  min-height: 46px;
  margin-left: auto;
  padding: 0 22px !important;
  border: 1px solid rgba(148, 163, 184, 0.2) !important;
  color: rgba(203, 213, 225, 0.72) !important;
  background: rgba(3, 8, 28, 0.22) !important;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
}

.input-shell:not(.input-shell--elder) .clear-input-btn:not(:disabled):hover {
  border-color: rgba(248, 113, 113, 0.38) !important;
  color: rgba(254, 226, 226, 0.95) !important;
  background: rgba(127, 29, 29, 0.18) !important;
}

.input-shell:not(.input-shell--elder) .input-helper-text:not(.mobile-helper-text) {
  margin-top: -4px;
  padding-left: 3px;
  color: rgba(148, 163, 184, 0.78) !important;
}

.input-shell:not(.input-shell--elder) .mt-2.flex.flex-nowrap {
  position: relative;
  margin-top: 15px !important;
  gap: 12px !important;
  align-items: stretch !important;
}

.input-shell:not(.input-shell--elder) .mt-2.flex.flex-nowrap::before {
  content: '';
  position: absolute;
  inset: -8px 78px -8px -8px;
  pointer-events: none;
  border-radius: 19px;
  background:
    radial-gradient(circle at 12% 0%, rgba(56, 189, 248, 0.08), transparent 40%),
    linear-gradient(145deg, rgba(255, 255, 255, 0.035), transparent 62%);
}

.input-shell:not(.input-shell--elder) .input-textarea {
  position: relative;
  z-index: 1;
  min-height: 94px !important;
  border-radius: 18px !important;
  border: 1px solid rgba(148, 163, 184, 0.26) !important;
  background:
    radial-gradient(circle at 10% 0%, rgba(56, 189, 248, 0.08), transparent 36%),
    linear-gradient(145deg, rgba(5, 12, 39, 0.5), rgba(3, 8, 28, 0.42)) !important;
  padding: 20px 22px !important;
  color: #f8fbff !important;
  line-height: 1.65;
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.07),
    inset 0 0 0 1px rgba(255, 255, 255, 0.015);
}

.input-shell:not(.input-shell--elder) .input-textarea:focus {
  border-color: rgba(125, 211, 252, 0.62) !important;
  box-shadow:
    0 0 0 3px rgba(56, 189, 248, 0.1),
    inset 0 1px 0 rgba(255, 255, 255, 0.08) !important;
}

.input-shell:not(.input-shell--elder) .input-textarea::placeholder {
  color: rgba(148, 163, 184, 0.78);
}

.input-shell:not(.input-shell--elder) .send-btn {
  position: relative;
  z-index: 1;
  width: 62px;
  height: 62px;
  min-height: 62px;
  align-self: center;
  margin: 0 0 0 2px;
  border: 1px solid rgba(125, 211, 252, 0.28);
  color: #ffffff !important;
  background:
    radial-gradient(circle at 32% 22%, rgba(255, 255, 255, 0.24), transparent 34%),
    linear-gradient(145deg, #30c9ff 0%, #426cff 42%, #6e45e8 100%) !important;
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.25),
    0 20px 44px -22px rgba(79, 70, 229, 0.98),
    0 0 0 8px rgba(79, 70, 229, 0.08) !important;
}

.input-shell:not(.input-shell--elder) .send-btn:not(:disabled):hover {
  transform: translateY(-2px) scale(1.03);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.26),
    0 24px 50px -22px rgba(56, 189, 248, 0.95),
    0 0 0 9px rgba(56, 189, 248, 0.09) !important;
}

.input-shell:not(.input-shell--elder) .send-btn:disabled {
  filter: saturate(0.55);
}

.input-shell:not(.input-shell--elder) > p.text-center {
  margin-top: 12px !important;
  color: rgba(148, 163, 184, 0.72) !important;
}

@media (max-width: 1280px) {
  .input-shell:not(.input-shell--elder) .generate-center-btn {
    padding: 0 24px !important;
  }

  .input-shell:not(.input-shell--elder) .plan-editor-btn {
    padding: 0 18px !important;
  }

  .input-shell:not(.input-shell--elder) .clear-input-btn {
    padding: 0 16px !important;
  }
}

/* Final composer fit: match the compact reference input deck */
.input-shell:not(.input-shell--elder) {
  padding: 12px 26px 13px !important;
}

.input-shell:not(.input-shell--elder)::before {
  display: none;
}

.input-shell:not(.input-shell--elder) .input-actions {
  min-height: 46px;
  padding-bottom: 12px;
  align-items: center;
  border-bottom-color: rgba(148, 163, 184, 0.24);
}

.input-shell:not(.input-shell--elder) .input-action-btn--icon {
  width: 42px;
  height: 42px;
  border-radius: 12px !important;
}

.input-shell:not(.input-shell--elder) .generate-center-btn,
.input-shell:not(.input-shell--elder) .plan-editor-btn,
.input-shell:not(.input-shell--elder) .clear-input-btn {
  min-height: 42px;
  border-radius: 12px !important;
}

.input-shell:not(.input-shell--elder) .generate-center-btn {
  padding: 0 28px !important;
}

.input-shell:not(.input-shell--elder) .plan-editor-btn {
  padding: 0 22px !important;
}

.input-shell:not(.input-shell--elder) .clear-input-btn {
  padding: 0 18px !important;
}

.input-shell:not(.input-shell--elder) .input-helper-text:not(.mobile-helper-text) {
  display: none !important;
}

.input-shell:not(.input-shell--elder) .mt-2.flex.flex-nowrap {
  margin-top: 13px !important;
  gap: 0 !important;
  align-items: center !important;
}

.input-shell:not(.input-shell--elder) .mt-2.flex.flex-nowrap::before {
  inset: -8px -8px -8px -8px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  background:
    radial-gradient(circle at 8% 0%, rgba(56, 189, 248, 0.09), transparent 34%),
    linear-gradient(145deg, rgba(5, 12, 39, 0.42), rgba(3, 8, 28, 0.28));
}

.input-shell:not(.input-shell--elder) .input-textarea {
  height: 106px !important;
  min-height: 106px !important;
  padding: 20px 82px 20px 22px !important;
  border-radius: 17px !important;
  border-color: rgba(148, 163, 184, 0.28) !important;
}

.input-shell:not(.input-shell--elder) .send-btn {
  position: absolute;
  right: 18px;
  top: 50%;
  width: 54px;
  height: 54px;
  min-height: 54px;
  margin: 0;
  transform: translateY(-50%);
}

.input-shell:not(.input-shell--elder) .send-btn:not(:disabled):hover {
  transform: translateY(calc(-50% - 2px)) scale(1.03);
}

.input-shell:not(.input-shell--elder) > p.text-center {
  margin-top: 11px !important;
  font-size: 11px;
}
</style>
