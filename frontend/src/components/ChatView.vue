<script setup>
import { ref, computed, nextTick, watch, onMounted, onBeforeUnmount } from 'vue'
import MarkdownIt from 'markdown-it'
import texmath from 'markdown-it-texmath'
import katex from 'katex'
import InputBar from './InputBar.vue'
import GenerationSteps from './UI/GenerationSteps.vue'
import { downloadMicroLessonHtml, openMicroLessonWindow } from '../utils/microLesson'

const props = defineProps({
  collapsed: Boolean,
  conversationId: [Number, String],
  ragActive: Boolean,
  uiTheme: {
    type: String,
    default: 'dark',
  },
  historyMessages: Array,
})
const emit = defineEmits(['toggle-sidebar', 'conversation-updated'])

const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
})
md.use(texmath, {
  engine: katex,
  delimiters: 'dollars',
  katexOptions: {
    throwOnError: false,
    strict: 'ignore',
    output: 'html',
  },
})

const messages = ref([])
const isLoading = ref(false)
const chatContainer = ref(null)
const showImageModal = ref(false)
const imagePrompt = ref('')
const imageLoading = ref(false)
const imageError = ref('')
const showPptModal = ref(false)
const pptTopic = ref('')
const pptLoading = ref(false)
const pptError = ref('')
const pptStatus = ref('')
const showPptEditModal = ref(false)
const pptEditInstruction = ref('')
const pptEditLoading = ref(false)
const pptEditError = ref('')
const pptArtifact = ref(null)
const pptVersionHistory = ref([])
const pptPreviewUrls = ref([])
const pptPreviewLoading = ref(false)
const pptPreviewError = ref('')
const showCitationEvidenceModal = ref(false)
const activeCitationEvidence = ref(null)
const showDocModal = ref(false)
const docPrompt = ref('')
const docTitle = ref('')
const docLoading = ref(false)
const docError = ref('')
const showPlanModal = ref(false)
const planModalStage = ref('preferences')
const planConstraint = ref('')
const planId = ref(null)
const planTitle = ref('')
const planDraft = ref('')
const showPlanJsonAdvanced = ref(false)
const planMissingQuestions = ref([])
const planConfidence = ref({})
const planSourceLinks = ref([])
const planMessageCount = ref(0)
const planLastMessageAt = ref('')
const planLoading = ref(false)
const planSaving = ref(false)
const planLinkLoading = ref(false)
const planError = ref('')
const planLinkError = ref('')
const planStatus = ref('')
const usePlanForPpt = ref(true)
const usePlanForDoc = ref(true)
const linkDocId = ref('')
const linkChunkId = ref('')
const linkConceptId = ref('')
const linkLocator = ref('')
const linkNote = ref('')
const kbDocs = ref([])
const kbDocsLoading = ref(false)
const kbDocError = ref('')
const kbOutline = ref([])
const kbOutlineLoading = ref(false)
const kbOutlineError = ref('')
const showMissingQuestionsModal = ref(false)
const missingQuestionsModalLoading = ref(false)
const missingQuestionsModalError = ref('')
const missingQuestionsForModal = ref([])
const missingTaskAnswers = ref({
  class_level: '',
  unified_answer_sheet: null,
  include_animation: null,
  group_work: null,
  common_mistakes: [],
  extra_notes: '',
})
const planForm = ref({
  gradeStage: '',
  subject: '',
  unitText: '',
  topicTagsText: '',
  scopePolicy: 'session_only',
  lessonDurationMin: '',
  teachingGoals: [''],
  keyConcepts: [''],
  focusDifficulties: [''],
  activityDesign: [{ name: '', duration: '' }],
})
const isDeveloperMode = ref(false)
const planPreferenceQuick = ref({
  teachingStyle: '',
  practiceDifficulty: '',
  courseStructure: '',
  needQuiz: false,
  needHomework: false,
  needBoardDesign: false,
})
const showGameModal = ref(false)
const gamePrompt = ref('')
const gameLoading = ref(false)
const gameError = ref('')
const showStudyPackModal = ref(false)
const studyPackSourceMode = ref('conversation')
const studyPackConversationScope = ref('latest_3_rounds')
const studyPackCustomText = ref('')
const studyPackFile = ref(null)
const studyPackFileName = ref('')
const studyPackFileInput = ref(null)
const studyPackLoading = ref(false)
const studyPackError = ref('')
const studyPackResult = ref(null)
const studyPackTab = ref('outline')
const studyPackCardStates = ref({})
const studyPackExporting = ref('')
const showVideoModal = ref(false)
const videoText = ref('')
const videoAutoScript = ref(true)
const videoUseDefault = ref(true)
const videoImagePreview = ref('')
const videoImageFilename = ref('')
const videoLoading = ref(false)
const videoUploading = ref(false)
const videoError = ref('')
const videoStatus = ref('')
const videoFileInput = ref(null)
const videoStep = ref(0)
const videoDurationSec = ref(12)
const videoResolution = ref('720P')
const videoEditInstruction = ref('')
const videoDraftPayload = ref(null)
const videoPreviewAudioUrl = ref('')
const videoPreviewLoading = ref(false)
const videoSteps = ['上下文建模', '脚本分镜', '视频导出']
const avatarList = [
  {
    id: 1,
    name: '严谨男教授',
    theme: '#1e3a8a',
    voice: '你好，我是林教授。',
    voiceId: '',
    img: 'https://api.dicebear.com/7.x/bottts/png?seed=1',
  },
  {
    id: 2,
    name: '亲切女教师',
    theme: '#db2777',
    voice: '嗨，我是小雅老师！',
    voiceId: '',
    img: 'https://api.dicebear.com/7.x/bottts/png?seed=2',
  },
]
const selectedAvatar = ref(null)
const voicePreviewError = ref('')
const isVoicePreviewing = ref(false)
let voicePreviewAudio = null
let pptPreviewObjectUrls = []
let missingQuestionsModalResolver = null
const showPlanMoreMenu = ref(false)
const planAutoSyncEnabled = ref(false)
const planAutoSyncHintPushed = ref(false)

let planAutoSyncRunning = false
let planAutoSyncQueued = false

let pptStageTimer = null
let chatProgressTimer = null
let imageProgressTimer = null
let pptProgressTimer = null
let videoProgressTimer = null
let chatProgressStartedAt = 0
let imageProgressStartedAt = 0
let pptProgressStartedAt = 0
let videoProgressStartedAt = 0
let chatProgressHasOutput = false

const PROGRESS_DEFAULTS = {
  chat: 12000,
  image: 18000,
  ppt: 24000,
  video: 36000,
}

async function openPptEditModal() {
  pptEditError.value = ''
  pptEditInstruction.value = ''
  pptPreviewError.value = ''
  showPptEditModal.value = true
  await loadLatestPptArtifact(props.conversationId)
}

function closePptEditModal() {
  if (pptEditLoading.value) return
  showPptEditModal.value = false
  closeCitationEvidenceModal()
}

function openPptModalFromEdit() {
  if (pptEditLoading.value) return
  closePptEditModal()
  openPptModal()
}

async function handleEditPpt() {
  if (pptEditLoading.value) return
  if (!pptEditInstruction.value.trim()) {
    pptEditError.value = '请输入修改要求'
    return
  }
  if (!pptArtifact.value?.id && props.conversationId) {
    await loadLatestPptArtifact(props.conversationId, { silent: true })
  }
  if (!pptArtifact.value?.id && !props.conversationId) {
    pptEditError.value = '请先生成一次 PPT'
    return
  }
  if (!pptArtifact.value?.id) {
    pptEditError.value = '未找到可修改的 PPT 版本，请先生成一次 PPT'
    return
  }

  pptEditLoading.value = true
  pptEditError.value = ''
  try {
    const task = await createGenerationTask('edit_ppt', 'PPT 迭代修改', {
      instruction: pptEditInstruction.value,
      conversation_key: props.conversationId ? String(props.conversationId) : undefined,
      artifact_id: pptArtifact.value?.id || undefined,
      theme_name: getPptThemeName(),
    })
    appendTaskSubmittedMessage(task, 'PPT 迭代修改')
    pptEditInstruction.value = ''
    showPptEditModal.value = false
  } catch (err) {
    pptEditError.value = `修改失败：${err.message}`
  } finally {
    pptEditLoading.value = false
  }
}

function buildConstraintPayload(rawText = '') {
  const composedText = composePlanConstraintText(rawText)
  const parsed = parseConstraintText(composedText)
  const preferencePayload = buildPlanPreferencePayload()
  if (!parsed && !preferencePayload) {
    return undefined
  }
  const merged = parsed && typeof parsed === 'object' ? { ...parsed } : {}
  if (preferencePayload) {
    merged.teacher_preferences = {
      ...(merged.teacher_preferences && typeof merged.teacher_preferences === 'object' ? merged.teacher_preferences : {}),
      ...preferencePayload,
    }
  }
  return Object.keys(merged).length ? merged : undefined
}

function getLatestAssistantText() {
  for (let i = messages.value.length - 1; i >= 0; i -= 1) {
    const msg = messages.value[i]
    if (msg?.role === 'assistant' && msg?.text) {
      return msg.text
    }
  }
  return ''
}

function openDocModal() {
  docError.value = ''
  docTitle.value = ''
  docPrompt.value = getLatestAssistantText() || '请生成一份完整教案，包含教学目标、重难点、教学过程和板书设计。'
  planStatus.value = getPlanPreparationStateText()
  showDocModal.value = true
  if (props.conversationId && getTeacherToken() && !planId.value) {
    preloadLatestPlan(props.conversationId)
  }
}

function closeDocModal() {
  if (docLoading.value) return
  showDocModal.value = false
}

async function handleExportDoc() {
  if (docLoading.value) return
  const promptText = docPrompt.value.trim()
  const token = getTeacherToken()
  const canUsePlanPipeline = usePlanForDoc.value && !!token && !!props.conversationId
  const enablePlan = canUsePlanPipeline && !!planId.value
  if (!promptText && !canUsePlanPipeline) {
    docError.value = '请输入教案内容或需求'
    return
  }

  docLoading.value = true
  docError.value = ''
  try {
    let finalPlanId = enablePlan ? Number(planId.value) : null
    if (canUsePlanPipeline) {
      finalPlanId = await ensurePlanForGeneration({
        autoLink: true,
        topK: 2,
        silentAutoLink: true,
        failOnMissingCancel: true,
      })
    }

    const requestBody = {
      prompt: promptText || '请根据教学计划生成一份完整教案，包含教学目标、重难点、教学过程和板书设计。',
      title: docTitle.value.trim() || undefined,
      plan_id: finalPlanId ? Number(finalPlanId) : undefined,
    }

    const task = await createGenerationTask('generate_doc', docTitle.value.trim() || planTitle.value || 'Word 教案生成', requestBody)
    appendTaskSubmittedMessage(task, 'Word 教案')
    showDocModal.value = false
  } catch (err) {
    if (String(err?.message || '').includes('已取消补齐缺失信息')) {
      docError.value = '已取消导出：请先补齐关键信息后再试'
    } else {
      docError.value = `导出失败：${err.message}`
    }
  } finally {
    docLoading.value = false
  }
}

function openGameModal() {
  gameError.value = ''
  if (!gamePrompt.value.trim()) {
    const latestUser = [...messages.value]
      .reverse()
      .find((msg) => msg?.role === 'user' && String(msg?.text || '').trim())
    const seed = String(latestUser?.text || getLatestAssistantText() || '')
      .replace(/\s+/g, ' ')
      .slice(0, 80)
    gamePrompt.value = seed
      ? `请只从“${seed}”相关内容中抽取学科知识点，生成概念-解释翻牌小游戏；不要把课时、学段、年级、教材版本、标题或教学流程当作卡片。`
      : '请基于当前课堂对话生成一个知识点翻牌小游戏，只保留核心概念、术语、方法、考点或易错点与对应解释。'
  }
  showGameModal.value = true
}

function closeGameModal() {
  if (gameLoading.value) return
  showGameModal.value = false
}

function getConversationRowsForGame() {
  return messages.value
    .filter((item) => (item?.role === 'user' || item?.role === 'assistant') && !item?.type)
    .filter((item) => String(item?.text || '').trim())
}

function buildGameConversationContext() {
  const rows = getConversationRowsForGame()
  if (!rows.length) return ''
  const merged = rows
    .slice(-10)
    .map((item) => `[${item.role === 'user' ? '教师' : '助手'}] ${String(item.text || '').trim()}`)
    .join('\n\n')
  return merged.length > 6000 ? merged.slice(-6000) : merged
}

function openGame(message) {
  const lessonWindow = window.open('', '_blank')
  if (!lessonWindow) return
  lessonWindow.document.open()
  lessonWindow.document.write(message?.html || '')
  lessonWindow.document.close()
}

function downloadGame(message) {
  const html = message?.html || ''
  if (!html) return
  const blob = new Blob([html], { type: 'text/html;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `interactive_game_${Date.now()}.html`
  link.click()
  URL.revokeObjectURL(url)
}

async function handleGenerateGame() {
  if (gameLoading.value) return
  const promptText = String(gamePrompt.value || '').trim()
  const contextText = buildGameConversationContext()
  if (!promptText && !contextText) {
    gameError.value = '请先输入需求，或先完成一轮对话再生成游戏'
    return
  }
  gameLoading.value = true
  gameError.value = ''
  try {
    const response = await fetch(GAME_API_URL, {
      method: 'POST',
      headers: getTeacherAuthHeaders(true),
      body: JSON.stringify({
        prompt: promptText,
        context_text: contextText,
      }),
    })
    const data = await response.json().catch(() => ({}))
    if (!response.ok) {
      throw new Error(data.detail || `HTTP ${response.status}`)
    }
    const gameHtml = String(data.html || '').trim()
    if (!gameHtml || !/<html[\s>]/i.test(gameHtml) || !/<script[\s>]/i.test(gameHtml)) {
      throw new Error('返回结果不是可运行的游戏页面，请重试')
    }
    messages.value.push({
      role: 'assistant',
      type: 'game',
      html: gameHtml,
      prompt: promptText || '基于当前对话自动生成记忆翻牌游戏',
    })
    showGameModal.value = false
    scrollToBottom()
  } catch (err) {
    gameError.value = `生成失败：${err.message}`
  } finally {
    gameLoading.value = false
  }
}

function resetStudyPackResultView() {
  studyPackResult.value = null
  studyPackTab.value = 'outline'
  studyPackCardStates.value = {}
  studyPackExporting.value = ''
}

function normalizeMathText(textValue) {
  return String(textValue || '')
    .replace(/\\\[/g, '$$')
    .replace(/\\\]/g, '$$')
    .replace(/\\\(/g, '$')
    .replace(/\\\)/g, '$')
}

function renderInlineMarkdown(textValue) {
  return md.renderInline(normalizeMathText(String(textValue || '')))
}

const STUDY_PACK_CARD_THEMES = [
  'from-sky-400 via-indigo-500 to-violet-500',
  'from-emerald-400 via-teal-500 to-cyan-500',
  'from-amber-400 via-orange-500 to-rose-500',
  'from-fuchsia-500 via-purple-500 to-indigo-500',
]

function getStudyPackCardTheme(index) {
  return STUDY_PACK_CARD_THEMES[index % STUDY_PACK_CARD_THEMES.length]
}

function toggleStudyPackCard(index) {
  studyPackCardStates.value[index] = !studyPackCardStates.value[index]
}

const studyPackQuizRows = computed(() => {
  if (!Array.isArray(studyPackResult.value?.quiz)) return []
  return studyPackResult.value.quiz
})

function buildStudyPackTitle(pack) {
  const outline = String(pack?.outline || '')
  const heading = outline.match(/^\s*#\s+(.+)$/m)
  if (heading?.[1]) return heading[1].trim()
  return '学习巩固包'
}

function openStudyPackModal() {
  studyPackError.value = ''
  if (!studyPackCustomText.value.trim()) {
    const latestAssistant = getLatestAssistantText()
    if (latestAssistant) {
      studyPackCustomText.value = `请基于以下课堂内容，生成复习大纲、记忆闪卡和练习题：\n${latestAssistant.slice(0, 500)}`
    }
  }
  studyPackCardStates.value = {}
  if (props.conversationId) {
    studyPackSourceMode.value = 'conversation'
  }
  showStudyPackModal.value = true
}

function closeStudyPackModal() {
  if (studyPackLoading.value) return
  showStudyPackModal.value = false
}

function triggerStudyPackFileInput() {
  studyPackFileInput.value?.click()
}

function handleStudyPackFileChange(event) {
  const file = event?.target?.files?.[0]
  if (!file) return
  studyPackFile.value = file
  studyPackFileName.value = file.name
  studyPackError.value = ''
}

function clearStudyPackFile() {
  studyPackFile.value = null
  studyPackFileName.value = ''
  if (studyPackFileInput.value) {
    studyPackFileInput.value.value = ''
  }
}

function getConversationRowsForStudyPack() {
  return messages.value
    .filter((item) => (item?.role === 'user' || item?.role === 'assistant') && !item?.type)
    .filter((item) => String(item?.text || '').trim())
}

function buildStudyPackConversationText(scope = studyPackConversationScope.value) {
  const allRows = getConversationRowsForStudyPack()
  if (!allRows.length) return ''

  let selectedRows = allRows
  if (scope === 'latest_turn') {
    selectedRows = allRows.slice(-2)
  } else if (scope === 'latest_3_rounds') {
    selectedRows = allRows.slice(-6)
  } else if (scope === 'latest_8_messages') {
    selectedRows = allRows.slice(-8)
  }

  const merged = selectedRows
    .map((item) => {
      const roleLabel = item.role === 'user' ? '教师' : '助手'
      return `[${roleLabel}] ${String(item.text || '').trim()}`
    })
    .join('\n\n')

  return merged.length > 8000 ? merged.slice(-8000) : merged
}

function buildStudyPackPseudoFileFromText(textValue) {
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-')
  return new File([textValue], `teacher_context_${timestamp}.txt`, {
    type: 'text/plain;charset=utf-8',
  })
}

function normalizeStudyPackPayload(payload) {
  const flashcardsRaw = Array.isArray(payload?.flashcards) ? payload.flashcards : []
  const quizRaw = Array.isArray(payload?.quiz) ? payload.quiz : []
  return {
    outline: normalizeMathText(String(payload?.outline || '')),
    flashcards: flashcardsRaw
      .map((item) => {
        if (item && typeof item === 'object') {
          return {
            front: normalizeMathText(item.front || item.question || item.q || ''),
            back: normalizeMathText(item.back || item.answer || item.a || ''),
          }
        }
        return {
          front: normalizeMathText(item),
          back: '',
        }
      })
      .filter((item) => item.front || item.back),
    quiz: quizRaw
      .map((item) => {
        if (!item || typeof item !== 'object') {
          return null
        }
        const rawOptions = Array.isArray(item.options) ? item.options : []
        return {
          question: normalizeMathText(item.question || item.stem || item.title || ''),
          options: rawOptions.map((option) => normalizeMathText(option)).filter(Boolean),
          answer: normalizeMathText(item.answer || item.correct_answer || item.key || ''),
          analysis: normalizeMathText(item.analysis || item.explanation || item['解析'] || ''),
        }
      })
      .filter(Boolean),
  }
}

function getStudyPackOutlineHtml(pack) {
  return md.render(normalizeMathText(String(pack?.outline || '')))
}

function openStudyPackFromMessage(packPayload) {
  studyPackResult.value = normalizeStudyPackPayload(packPayload)
  studyPackTab.value = 'outline'
  studyPackError.value = ''
  studyPackCardStates.value = {}
  showStudyPackModal.value = true
}

async function handleExportStudyPackDoc(exportType) {
  if (!studyPackResult.value || studyPackExporting.value) return

  const type = exportType === 'quiz' ? 'quiz' : 'outline'
  const payload =
    type === 'outline'
      ? {
          export_type: 'outline',
          title: `${buildStudyPackTitle(studyPackResult.value)} · 复习提纲`,
          outline: studyPackResult.value.outline || '',
          conversation_key: props.conversationId ? String(props.conversationId) : undefined,
          plan_id: planId.value ? Number(planId.value) : undefined,
        }
      : {
          export_type: 'quiz',
          title: `${buildStudyPackTitle(studyPackResult.value)} · 练习题与解析`,
          quiz: studyPackQuizRows.value,
          conversation_key: props.conversationId ? String(props.conversationId) : undefined,
          plan_id: planId.value ? Number(planId.value) : undefined,
        }

  studyPackExporting.value = type
  studyPackError.value = ''

  try {
    const response = await fetch(STUDY_PACK_EXPORT_DOC_API_URL, {
      method: 'POST',
      headers: getTeacherAuthHeaders(true),
      body: JSON.stringify(payload),
    })
    if (!response.ok) {
      const data = await response.json().catch(() => ({}))
      throw new Error(data.detail || `HTTP ${response.status}`)
    }

    const blob = await response.blob()
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    const suffix = type === 'outline' ? '复习提纲' : '练习题与解析'
    const fallbackStem = sanitizeFilenameStem(payload.title || suffix, suffix, 56)
    link.download = resolveDownloadFilename(response, `${fallbackStem}.docx`)
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
  } catch (err) {
    studyPackError.value = `导出失败：${err.message}`
  } finally {
    studyPackExporting.value = ''
  }
}

async function handleGenerateStudyPack() {
  if (studyPackLoading.value) return

  let targetFile = null
  if (studyPackSourceMode.value === 'file') {
    if (!studyPackFile.value) {
      studyPackError.value = '请先上传资料文件'
      return
    }
    targetFile = studyPackFile.value
  } else {
    const conversationText = buildStudyPackConversationText(studyPackConversationScope.value)
    const customText = String(studyPackCustomText.value || '').trim()
    const mergedText = [conversationText, customText ? `补充要求：${customText}` : '']
      .filter(Boolean)
      .join('\n\n')
      .trim()

    if (!mergedText) {
      studyPackError.value = '当前对话内容不足，请先完成至少一轮教案对话'
      return
    }
    targetFile = buildStudyPackPseudoFileFromText(mergedText)
  }

  studyPackLoading.value = true
  studyPackError.value = ''
  try {
    const formData = new FormData()
    formData.append('file', targetFile)

    const response = await fetch(STUDY_PACK_API_URL, {
      method: 'POST',
      headers: getTeacherAuthHeaders(),
      body: formData,
    })
    const data = await response.json().catch(() => ({}))
    if (!response.ok) {
      throw new Error(data.detail || `HTTP ${response.status}`)
    }

    const normalized = normalizeStudyPackPayload(data)
    studyPackResult.value = normalized
    studyPackTab.value = 'outline'
    studyPackCardStates.value = {}

    messages.value.push({
      role: 'assistant',
      type: 'study_pack',
      studyPack: normalized,
      sourceMode: studyPackSourceMode.value,
      sourceLabel: studyPackSourceMode.value === 'file' ? (studyPackFileName.value || '上传资料') : '当前对话上下文',
    })
    scrollToBottom()
  } catch (err) {
    studyPackError.value = `生成失败：${err.message}`
  } finally {
    studyPackLoading.value = false
  }
}

const inputHeight = ref(110)
const isResizingInput = ref(false)
let inputStartY = 0
let inputStartHeight = 0
const chatProgress = ref(0)
const imageProgress = ref(0)
const pptProgress = ref(0)
const videoProgress = ref(0)
const webResourceSuggestion = ref(null)
const webResourceLoading = ref(false)
const webResourceError = ref('')
const webResourceExpanded = ref(false)
let webResourcePollTimer = null

const API_BASE = import.meta.env.VITE_API_BASE || '/api'
const API_URL = `${API_BASE}/chat`
const AGENT_CHAT_API_URL = `${API_BASE}/agent/chat`
const IMAGE_API_URL = `${API_BASE}/generate_image`
const PPT_API_URL = `${API_BASE}/generate_ppt`
const PPT_EDIT_API_URL = `${API_BASE}/edit_ppt`
const PPT_ARTIFACT_BY_CONVERSATION_API_URL = `${API_BASE}/artifact/ppt/conversation`
const PPT_ARTIFACT_API_URL = `${API_BASE}/artifact`
const VIDEO_API_URL = `${API_BASE}/generate_video`
const VIDEO_EDIT_API_URL = `${API_BASE}/edit_video`
const VIDEO_EXPORT_API_URL = `${API_BASE}/video/export`
const VIDEO_PREVIEW_AUDIO_API_URL = `${API_BASE}/video/preview_audio`
const IMAGE_UPLOAD_URL = `${API_BASE}/upload_image`
const GAME_API_URL = `${API_BASE}/generate_game`
const DOC_API_URL = `${API_BASE}/generate_doc`
const GENERATION_TASK_API_URL = `${API_BASE}/generation_tasks`
const STUDY_PACK_API_URL = `${API_BASE}/student/upload_and_generate`
const STUDY_PACK_EXPORT_DOC_API_URL = `${API_BASE}/study_pack/export_docx`
const PLAN_EXTRACT_API_URL = `${API_BASE}/plan/extract`
const PLAN_LINK_SOURCES_API_URL = `${API_BASE}/plan/link_sources`
const PLAN_AUTO_LINK_API_URL_BASE = `${API_BASE}/plan`
const PLAN_BY_CONVERSATION_API_URL = `${API_BASE}/plan/conversation`
const KB_DOCS_API_URL = `${API_BASE}/kb/docs`
const WEB_RESOURCES_API_URL = `${API_BASE}/web_resources/conversation`
const DEV_MODE_STORAGE_KEY = 'smart_sketch_dev_mode'

const CLASS_LEVEL_OPTIONS = ['薄弱', '一般', '较强']
const DEFAULT_COMMON_MISTAKE_OPTIONS = ['审题偏差', '概念混淆', '答题要点不全', '术语表达不规范', '逻辑不清']
const SUBJECT_COMMON_MISTAKE_OPTIONS = {
  chinese: ['错别字/病句', '文意理解偏差', '审题偏差', '答题要点不全', '术语表达不规范'],
  math: ['审题偏差', '计算错误', '步骤不完整', '单位/符号错误', '概念混淆'],
  english: ['时态误用', '主谓一致错误', '拼写错误', '词汇搭配不当', '阅读定位偏差'],
  physics: ['方向错', '漏受力', '单位错', '公式套用错误', '概念混淆'],
  chemistry: ['化学方程式配平错误', '化学用语不规范', '实验现象与结论混淆', '计算与单位错误', '概念混淆'],
  biology: ['概念混淆', '术语误用', '图表信息读取偏差', '过程顺序错误', '答题要点不全'],
  history: ['时序混乱', '史实张冠李戴', '因果关系不清', '概念混淆', '材料解读偏差'],
  geography: ['读图定位偏差', '区域特征混淆', '成因分析不完整', '术语使用不规范', '概念混淆'],
  politics: ['概念混淆', '观点片面', '理论联系实际不足', '术语表达不规范', '材料论证不充分'],
}
const TEACHING_STYLE_OPTIONS = ['严谨', '启发式', '活泼']
const PRACTICE_DIFFICULTY_OPTIONS = ['基础', '标准', '提高']
const COURSE_STRUCTURE_OPTIONS = ['讲解为主', '练习为主', '探究为主']
const STUDY_PACK_SCOPE_OPTIONS = [
  { value: 'latest_turn', label: '最近 1 轮对话' },
  { value: 'latest_3_rounds', label: '最近 3 轮对话（推荐）' },
  { value: 'latest_8_messages', label: '最近 8 条消息' },
  { value: 'all', label: '当前会话全部对话' },
]
const PLAN_MODAL_STAGE_OPTIONS = [
  { value: 'preferences', label: '偏好设置', hint: '约束、风格、快速更新' },
  { value: 'structure', label: '教学要素', hint: '目标、知识点、活动流程' },
  { value: 'evidence', label: '教材依据', hint: '缺失任务、引用来源、质量提示' },
]
const PLAN_AUTO_SYNC_INTENT_REGEX = /(教案|教学设计|教学方案|教学计划|备课|课件|导出\s*PPT|导出\s*教案|教学目标|重难点|板书设计)/
const PLAN_AUTO_SYNC_REPLY_SIGNALS = [
  /教学目标/,
  /(教学重难点|重点难点|重难点)/,
  /教学过程/,
  /(导入|新授|练习|总结|小结)/,
  /板书设计/,
]

function normalizeSubjectKey(rawSubject) {
  const subject = String(rawSubject || '').trim().toLowerCase()
  if (!subject) return ''

  if (subject.includes('语文') || subject.includes('中文') || subject.includes('国文')) return 'chinese'
  if (subject.includes('数学') || subject.includes('代数') || subject.includes('几何') || subject.includes('函数')) return 'math'
  if (subject.includes('英语') || subject.includes('english')) return 'english'
  if (subject.includes('物理') || subject.includes('physics')) return 'physics'
  if (subject.includes('化学') || subject.includes('chemistry')) return 'chemistry'
  if (subject.includes('生物') || subject.includes('biology')) return 'biology'
  if (subject.includes('历史') || subject.includes('history')) return 'history'
  if (subject.includes('地理') || subject.includes('geography')) return 'geography'
  if (subject.includes('政治') || subject.includes('思政') || subject.includes('道法') || subject.includes('道德与法治')) return 'politics'
  return ''
}

function getCommonMistakeOptionsBySubject(rawSubject) {
  const key = normalizeSubjectKey(rawSubject)
  return SUBJECT_COMMON_MISTAKE_OPTIONS[key] || DEFAULT_COMMON_MISTAKE_OPTIONS
}

function normalizeMissingQuestionList(rows = []) {
  const source = Array.isArray(rows) ? rows : []
  const result = []
  const seen = new Set()
  source.forEach((item) => {
    const text = String(item || '').trim()
    if (!text) return
    const key = text.toLowerCase()
    if (seen.has(key)) return
    seen.add(key)
    result.push(text)
  })
  return result
}

function getPlanFallbackMissingQuestions(planObject = getNormalizedPlanFromDraft()) {
  const plan = planObject && typeof planObject === 'object' ? planObject : {}
  const rows = []

  const classProfile = plan.class_profile && typeof plan.class_profile === 'object'
    ? plan.class_profile
    : {}
  if (!String(classProfile.level || '').trim()) {
    rows.push('班级当前学习基础（薄弱/一般/较强）？')
  }

  const deliveryPreferences = plan.delivery_preferences && typeof plan.delivery_preferences === 'object'
    ? plan.delivery_preferences
    : {}
  if (typeof deliveryPreferences.unified_answer_sheet !== 'boolean') {
    rows.push('是否需要统一答题卡格式（便于课堂集中讲评）？')
  }
  if (typeof deliveryPreferences.include_animation !== 'boolean') {
    rows.push('是否需要动画仿真或动态演示？')
  }
  if (typeof deliveryPreferences.group_work !== 'boolean') {
    rows.push('是否安排小组合作角色分工？')
  }

  const commonMistakes = Array.isArray(plan.common_mistakes)
    ? plan.common_mistakes
      .map((item) => String(item || '').trim())
      .filter(Boolean)
    : []
  if (!commonMistakes.length) {
    rows.push('本节课常见错误类型有哪些（可多选）？')
  }

  return normalizeMissingQuestionList(rows)
}

function getResolvedMissingQuestionsForModal() {
  const backendQuestions = normalizeMissingQuestionList(planMissingQuestions.value)
  const fallbackQuestions = getPlanFallbackMissingQuestions(getNormalizedPlanFromDraft())
  return normalizeMissingQuestionList([...backendQuestions, ...fallbackQuestions])
}

const activePlanSubjectForMissing = computed(() => {
  const formSubject = String(planForm.value.subject || '').trim()
  if (formSubject) return formSubject
  return String(getNormalizedPlanFromDraft().subject || '').trim()
})

const commonMistakeOptionsForModal = computed(() => getCommonMistakeOptionsBySubject(activePlanSubjectForMissing.value))

function getTeacherToken() {
  return localStorage.getItem('teacher_token') || ''
}

function getPptThemeName() {
  return props.uiTheme === 'elder' ? 'teaching_gray_v1' : 'teaching_blue_v1'
}

function getMicrocourseThemeName() {
  return props.uiTheme === 'elder' ? 'teaching_board_gray_v1' : 'teaching_board_v1'
}

function resolveApiMediaUrl(url) {
  const value = String(url || '').trim()
  if (!value) return ''
  if (/^https?:\/\//i.test(value)) {
    return value
  }
  if (value.startsWith('/')) {
    return `${API_BASE}${value}`
  }
  return `${API_BASE}/${value}`
}

function getTeacherAuthHeaders(withJson = false) {
  const headers = {}
  if (withJson) {
    headers['Content-Type'] = 'application/json'
  }
  const token = getTeacherToken()
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  return headers
}

async function createGenerationTask(taskType, title, payload = {}) {
  const response = await fetch(GENERATION_TASK_API_URL, {
    method: 'POST',
    headers: getTeacherAuthHeaders(true),
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

function appendTaskSubmittedMessage(task, label = '生成资源') {
  const taskId = task?.id ? `#${task.id}` : ''
  messages.value.push({
    role: 'assistant',
    text: `已提交${label}后台生成任务${taskId}。你可以继续对话或使用其他功能，稍后到左侧「任务中心」查看进度；完成后也会进入「资源库」下载。`,
  })
  scrollToBottom()
}

const webResourceItems = computed(() => {
  const items = webResourceSuggestion.value?.items
  return Array.isArray(items) ? items : []
})

const shouldShowWebResources = computed(() => {
  const status = String(webResourceSuggestion.value?.status || '').trim()
  return webResourceLoading.value || webResourceError.value || status === 'searching' || status === 'done' || status === 'failed'
})

function clearWebResourcePoll() {
  if (webResourcePollTimer) {
    clearTimeout(webResourcePollTimer)
    webResourcePollTimer = null
  }
}

function scheduleWebResourcePoll(delay = 4200) {
  clearWebResourcePoll()
  if (!props.conversationId || !getTeacherToken()) return
  webResourcePollTimer = window.setTimeout(() => {
    loadWebResourceSuggestion({ silent: true, poll: true }).catch(() => {})
  }, delay)
}

async function loadWebResourceSuggestion(options = {}) {
  const conversationKey = String(props.conversationId || '').trim()
  if (!conversationKey || !getTeacherToken()) {
    webResourceSuggestion.value = null
    webResourceError.value = ''
    clearWebResourcePoll()
    return
  }

  if (!options.silent) {
    webResourceLoading.value = true
  }
  webResourceError.value = ''
  try {
    const response = await fetch(`${WEB_RESOURCES_API_URL}/${encodeURIComponent(conversationKey)}/latest`, {
      headers: getTeacherAuthHeaders(),
    })
    const data = await response.json().catch(() => ({}))
    if (!response.ok) {
      throw new Error(data.detail || `HTTP ${response.status}`)
    }
    webResourceSuggestion.value = data.suggestion || null
    const status = String(webResourceSuggestion.value?.status || '').trim()
    if (status === 'searching') {
      scheduleWebResourcePoll(options.poll ? 3600 : 4800)
    } else {
      clearWebResourcePoll()
    }
  } catch (err) {
    webResourceError.value = err instanceof Error ? err.message : '备课资源加载失败'
    clearWebResourcePoll()
  } finally {
    webResourceLoading.value = false
  }
}

async function refreshWebResourceSuggestion() {
  const conversationKey = String(props.conversationId || '').trim()
  if (!conversationKey || !getTeacherToken()) return
  webResourceLoading.value = true
  webResourceError.value = ''
  try {
    const response = await fetch(`${WEB_RESOURCES_API_URL}/${encodeURIComponent(conversationKey)}/refresh`, {
      method: 'POST',
      headers: getTeacherAuthHeaders(),
    })
    const data = await response.json().catch(() => ({}))
    if (!response.ok) {
      throw new Error(data.detail || `HTTP ${response.status}`)
    }
    webResourceSuggestion.value = {
      ...(webResourceSuggestion.value || {}),
      status: 'searching',
      items: webResourceItems.value,
    }
    webResourceExpanded.value = true
    scheduleWebResourcePoll(1800)
  } catch (err) {
    webResourceError.value = err instanceof Error ? err.message : '备课资源刷新失败'
  } finally {
    webResourceLoading.value = false
  }
}

function openWebResource(url) {
  const target = String(url || '').trim()
  if (!target) return
  window.open(target, '_blank', 'noopener,noreferrer')
}

function isApiOriginUrl(url) {
  const value = String(url || '').trim()
  if (!value) return false
  try {
    const targetUrl = new URL(value, window.location.origin)
    const apiUrl = new URL(API_BASE, window.location.origin)
    return targetUrl.origin === apiUrl.origin
  } catch {
    return false
  }
}

function parseFilenameFromContentDisposition(value) {
  const contentDisposition = String(value || '')
  if (!contentDisposition) return ''

  const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8Match?.[1]) {
    try {
      return decodeURIComponent(utf8Match[1])
    } catch {
      return String(utf8Match[1] || '').trim()
    }
  }

  const basicMatch = contentDisposition.match(/filename="?([^";]+)"?/i)
  return String(basicMatch?.[1] || '').trim()
}

function sanitizeFilenameStem(value, fallback = '文件', maxLen = 48) {
  let text = String(value || '')
    .trim()
    .replace(/[\\/:*?"<>|\r\n]+/g, '_')
    .replace(/[《》“”]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
  if (!text) {
    text = fallback
  }
  if (text.length > maxLen) {
    text = text.slice(0, maxLen).trim()
  }
  return text || fallback
}

function extractTopicHintForFilename(text) {
  const source = String(text || '').trim()
  if (!source) return ''

  const unitMatch = source.match(/《\s*([^《》\n]{1,30})\s*》/)
  if (unitMatch?.[1]) {
    return sanitizeFilenameStem(unitMatch[1], '', 30)
  }

  const keywordMatch = source.match(/([^\n，,。！？!?]{2,36}?)(?:教案|课件|PPT|ppt|微课)/)
  if (keywordMatch?.[1]) {
    return sanitizeFilenameStem(keywordMatch[1], '', 30)
  }

  const first = source.split(/[\n，,。！？!?]/)[0] || ''
  return sanitizeFilenameStem(first, '', 30)
}

function resolveDownloadFilename(response, fallbackName = '') {
  const headerFilename = parseFilenameFromContentDisposition(response?.headers?.get('content-disposition'))
  if (headerFilename) return headerFilename
  return sanitizeFilenameStem(String(fallbackName || '').replace(/\.[^.]+$/, ''), '文件', 60)
    + (String(fallbackName || '').match(/\.[^.]+$/)?.[0] || '')
}

function parseFilenameFromUrl(value) {
  const urlText = String(value || '').trim()
  if (!urlText) return ''
  try {
    const parsed = new URL(urlText, window.location.origin)
    const tail = decodeURIComponent((parsed.pathname || '').split('/').pop() || '')
    if (!tail || tail.toLowerCase() === 'download' || !tail.includes('.')) {
      return ''
    }
    return tail
  } catch {
    return ''
  }
}

function inferArtifactFilename(message, fallbackUrl = '') {
  const byUrl = parseFilenameFromUrl(fallbackUrl)
  if (byUrl) return byUrl

  const hint = `${String(message?.badge || '')} ${String(message?.title || '')}`.toLowerCase()
  const titleHint = sanitizeFilenameStem(message?.title || message?.badge || '', '', 36)
  if (hint.includes('ppt') || hint.includes('课件')) {
    return `${titleHint || '课堂课件'}.pptx`
  }
  if (hint.includes('word') || hint.includes('教案')) {
    const stem = titleHint || '教案'
    return `${stem.includes('教案') ? stem : `${stem}教案`}.docx`
  }
  return `${titleHint || `artifact_${Date.now()}`}`
}

function buildApiErrorMessage(payload, status) {
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

async function readHttpErrorMessage(response) {
  const status = Number(response?.status || 0)
  const contentType = String(response?.headers?.get('content-type') || '').toLowerCase()
  if (contentType.includes('application/json')) {
    const payload = await response.json().catch(() => ({}))
    return buildApiErrorMessage(payload, status)
  }
  return buildApiErrorMessage({}, status)
}

function pushArtifactActionError(prefix, error) {
  const detail = String(error?.message || '').trim() || '请稍后重试'
  messages.value.push({
    role: 'assistant',
    text: `${prefix}：${detail}`,
  })
  scrollToBottom()
}

function openUrlDirectly(url) {
  const raw = String(url || '').trim()
  if (!raw) return
  const hasScheme = /^[a-z][a-z0-9+.-]*:/i.test(raw)
  const href = hasScheme ? raw : resolveApiMediaUrl(raw)
  if (!href) return
  const link = document.createElement('a')
  link.href = href
  link.target = '_blank'
  link.rel = 'noopener'
  link.click()
}

async function handleFileArtifactDownload(message) {
  const targetUrl = resolveApiMediaUrl(message?.downloadUrl)
  if (!targetUrl) return

  if (!isApiOriginUrl(targetUrl)) {
    const directLink = document.createElement('a')
    directLink.href = targetUrl
    directLink.download = inferArtifactFilename(message, targetUrl)
    directLink.target = '_blank'
    directLink.rel = 'noopener'
    directLink.click()
    return
  }

  try {
    const response = await fetch(targetUrl, {
      headers: getTeacherAuthHeaders(),
    })
    if (!response.ok) {
      throw new Error(await readHttpErrorMessage(response))
    }

    const blob = await response.blob()
    const filename =
      parseFilenameFromContentDisposition(response.headers.get('content-disposition')) ||
      inferArtifactFilename(message, targetUrl)
    const objectUrl = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = objectUrl
    link.download = filename
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(objectUrl)
  } catch (error) {
    pushArtifactActionError('文件下载失败', error)
  }
}

async function handleFileArtifactPreview(message) {
  const targetUrl = resolveApiMediaUrl(message?.previewUrl)
  if (!targetUrl) return

  if (!isApiOriginUrl(targetUrl)) {
    openUrlDirectly(targetUrl)
    return
  }

  try {
    const response = await fetch(targetUrl, {
      headers: getTeacherAuthHeaders(),
    })
    if (!response.ok) {
      throw new Error(await readHttpErrorMessage(response))
    }

    const blob = await response.blob()
    const objectUrl = window.URL.createObjectURL(blob)
    const previewWindow = window.open(objectUrl, '_blank', 'noopener')
    if (!previewWindow) {
      openUrlDirectly(objectUrl)
    }
    window.setTimeout(() => {
      window.URL.revokeObjectURL(objectUrl)
    }, 60_000)
  } catch (error) {
    pushArtifactActionError('文件预览失败', error)
  }
}

function hasDeveloperRole() {
  try {
    const roleCandidates = [
      localStorage.getItem('role'),
      localStorage.getItem('user_role'),
      localStorage.getItem('teacher_role'),
    ]
    return roleCandidates.some((value) => /(admin|dev)/i.test(String(value || '').trim()))
  } catch {
    return false
  }
}

function getDeveloperModeFromStorage() {
  try {
    return localStorage.getItem(DEV_MODE_STORAGE_KEY) === '1'
  } catch {
    return false
  }
}

function getDeveloperModeFromUrl() {
  try {
    const params = new URLSearchParams(window.location.search)
    return params.get('dev') === '1'
  } catch {
    return false
  }
}

function setDeveloperModeEnabled(enabled, options = {}) {
  const shouldPersist = options?.persist !== false
  isDeveloperMode.value = !!enabled
  if (!isDeveloperMode.value) {
    showPlanJsonAdvanced.value = false
  }
  if (shouldPersist) {
    try {
      localStorage.setItem(DEV_MODE_STORAGE_KEY, isDeveloperMode.value ? '1' : '0')
    } catch {
      // ignore
    }
  }
}

function initializeDeveloperMode() {
  const enabled = getDeveloperModeFromStorage() || getDeveloperModeFromUrl() || hasDeveloperRole()
  setDeveloperModeEnabled(enabled, { persist: true })
}

function handleDeveloperModeHotkey(event) {
  if (!event?.ctrlKey || !event?.shiftKey) return
  if (String(event?.key || '').toLowerCase() !== 'd') return
  event.preventDefault()
  setDeveloperModeEnabled(!isDeveloperMode.value, { persist: true })
  planStatus.value = isDeveloperMode.value ? '开发者模式已开启' : '开发者模式已关闭'
}

function togglePlanPreferenceOption(field, option) {
  const normalized = String(option || '').trim()
  if (!normalized) return
  planPreferenceQuick.value[field] = planPreferenceQuick.value[field] === normalized ? '' : normalized
}

function togglePlanPreferenceNeed(field) {
  planPreferenceQuick.value[field] = !planPreferenceQuick.value[field]
}

function buildPlanPreferenceSummaryRows() {
  const rows = []
  if (planPreferenceQuick.value.teachingStyle) {
    rows.push(`课堂风格：${planPreferenceQuick.value.teachingStyle}`)
  }
  if (planPreferenceQuick.value.practiceDifficulty) {
    rows.push(`练习难度：${planPreferenceQuick.value.practiceDifficulty}`)
  }
  if (planPreferenceQuick.value.courseStructure) {
    rows.push(`课件结构：${planPreferenceQuick.value.courseStructure}`)
  }
  const needRows = []
  if (planPreferenceQuick.value.needQuiz) needRows.push('随堂测')
  if (planPreferenceQuick.value.needHomework) needRows.push('作业')
  if (planPreferenceQuick.value.needBoardDesign) needRows.push('板书设计')
  if (needRows.length) {
    rows.push(`需要：${needRows.join('、')}`)
  }
  return rows
}

function buildPlanPreferencePayload() {
  const payload = {}
  if (planPreferenceQuick.value.teachingStyle) {
    payload.teaching_style = planPreferenceQuick.value.teachingStyle
  }
  if (planPreferenceQuick.value.practiceDifficulty) {
    payload.practice_difficulty = planPreferenceQuick.value.practiceDifficulty
  }
  if (planPreferenceQuick.value.courseStructure) {
    payload.course_structure = planPreferenceQuick.value.courseStructure
  }
  const requiredOutputs = []
  if (planPreferenceQuick.value.needQuiz) requiredOutputs.push('随堂测')
  if (planPreferenceQuick.value.needHomework) requiredOutputs.push('作业')
  if (planPreferenceQuick.value.needBoardDesign) requiredOutputs.push('板书设计')
  if (requiredOutputs.length) {
    payload.required_outputs = requiredOutputs
  }
  return Object.keys(payload).length ? payload : null
}

function composePlanConstraintText(rawText = '') {
  const text = String(rawText || '').trim()
  const preferenceRows = buildPlanPreferenceSummaryRows()
  if (!preferenceRows.length) {
    return text
  }
  const preferenceText = preferenceRows.join('；')
  if (!text) {
    return preferenceText
  }
  return `${text}\n偏好：${preferenceText}`
}

function parseConstraintText(text) {
  const constraintText = String(text || '').trim()
  if (!constraintText) {
    return undefined
  }
  try {
    const parsed = JSON.parse(constraintText)
    if (parsed && typeof parsed === 'object') {
      return parsed
    }
    return { note: constraintText }
  } catch {
    return { note: constraintText }
  }
}

function getConversationUserMessageCount() {
  return messages.value.filter((msg) => {
    if (msg?.role !== 'user') return false
    const body = String(msg?.text || msg?.content || '').trim()
    return Boolean(body)
  }).length
}

function getPendingPlanMessageCount() {
  const baseCount = Number(planMessageCount.value || 0)
  const pending = getConversationUserMessageCount() - baseCount
  return pending > 0 ? pending : 0
}

function getPlanPreparationStateText() {
  if (!planId.value) {
    return '教学计划：未生成'
  }
  const pending = getPendingPlanMessageCount()
  if (pending > 0) {
    return `教学计划：需根据新需求更新（${pending}条）`
  }
  return '教学计划：已准备'
}

function getPlanPreparationTone() {
  if (!planId.value) return 'text-slate-400'
  return getPendingPlanMessageCount() > 0 ? 'text-amber-300' : 'text-emerald-300'
}

function getCurrentPlanModalStageOption() {
  return PLAN_MODAL_STAGE_OPTIONS.find((item) => item.value === planModalStage.value) || PLAN_MODAL_STAGE_OPTIONS[0]
}

function setPlanModalStage(nextStage) {
  const normalized = String(nextStage || '').trim()
  if (!PLAN_MODAL_STAGE_OPTIONS.some((item) => item.value === normalized)) {
    planModalStage.value = PLAN_MODAL_STAGE_OPTIONS[0].value
    return
  }
  planModalStage.value = normalized
}

function backToPlanOverview() {
  planModalStage.value = PLAN_MODAL_STAGE_OPTIONS[0].value
}

function isPlanIntentText(rawText = '') {
  const text = String(rawText || '').trim()
  if (!text) return false
  return PLAN_AUTO_SYNC_INTENT_REGEX.test(text)
}

function isPlanLikeAssistantText(rawText = '') {
  const text = String(rawText || '').trim()
  if (!text) return false
  const hitCount = PLAN_AUTO_SYNC_REPLY_SIGNALS.reduce((count, pattern) => {
    return pattern.test(text) ? count + 1 : count
  }, 0)
  return hitCount >= 2
}

function hasPlanCardMessage(planIdValue = null) {
  const targetPlanId = Number(planIdValue || 0)
  return messages.value.some((item) => {
    if (item?.type !== 'plan') return false
    if (!targetPlanId) return true
    return Number(item?.planId || 0) === targetPlanId
  })
}

function pushPlanHintCard(payload) {
  const nextPlanId = Number(payload?.plan_id || payload?.id || planId.value || 0)
  if (nextPlanId > 0 && hasPlanCardMessage(nextPlanId)) {
    return
  }

  messages.value.push({
    role: 'assistant',
    type: 'plan',
    planId: nextPlanId > 0 ? nextPlanId : undefined,
    title: payload?.title || planTitle.value || '教学计划',
    keyConcepts: Array.isArray(payload?.plan?.key_concepts) ? payload.plan.key_concepts : [],
    missingQuestions: Array.isArray(payload?.missing_questions) ? payload.missing_questions : [],
  })
  scrollToBottom()
}

async function queueAutoSyncPlanFromConversation(options = {}) {
  const token = getTeacherToken()
  const conversationKey = String(options?.conversationKey || props.conversationId || '').trim()
  if (!token || !conversationKey) {
    return Number(planId.value || 0) || null
  }

  const force = !!options?.force
  const userText = String(options?.userText || '').trim()
  const assistantText = String(options?.assistantText || '').trim()
  if (!force) {
    const hasPlan = !!planId.value
    const hasPlanSignal = isPlanIntentText(userText) || isPlanLikeAssistantText(assistantText)
    const shouldEnable = (!hasPlan && planAutoSyncEnabled.value) || hasPlanSignal
    if (!shouldEnable) {
      return Number(planId.value || 0) || null
    }
  }

  planAutoSyncEnabled.value = true
  planAutoSyncQueued = true
  if (planAutoSyncRunning) {
    return Number(planId.value || 0) || null
  }

  planAutoSyncRunning = true
  try {
    while (planAutoSyncQueued) {
      planAutoSyncQueued = false
      if (String(props.conversationId || '') !== conversationKey) {
        break
      }

      const hadPlanBeforeExtract = !!planId.value
      const data = await extractPlanByConversation(conversationKey, planConstraint.value)
      const nextPlanId = Number(data?.plan_id || planId.value || 0)

      if (!hadPlanBeforeExtract && nextPlanId > 0 && !planAutoSyncHintPushed.value) {
        pushPlanHintCard(data)
        planAutoSyncHintPushed.value = true
      }

      if (!hadPlanBeforeExtract && nextPlanId > 0) {
        planStatus.value = '已自动生成教学计划，后续对话会持续更新'
      } else if (nextPlanId > 0) {
        planStatus.value = '教学计划已随对话自动更新'
      }
    }
  } catch (err) {
    console.warn('[plan-auto-sync] failed:', err)
    if (showPlanModal.value) {
      planError.value = `自动更新计划失败：${err.message}`
    }
  } finally {
    planAutoSyncRunning = false
  }

  return Number(planId.value || 0) || null
}

function formatSecondsToClock(value) {
  const seconds = Number(value)
  const safe = Number.isFinite(seconds) ? Math.max(0, Math.floor(seconds)) : 0
  const mm = String(Math.floor(safe / 60)).padStart(2, '0')
  const ss = String(safe % 60).padStart(2, '0')
  return `${mm}:${ss}`
}

function getReadableLocator(locatorValue) {
  const raw = String(locatorValue || '').trim()
  if (!raw) return '未定位'
  const pageMatch = raw.match(/^(?:c|chunk|p|page)\s*[-_#:]?\s*(\d+)$/i)
  if (pageMatch) {
    return `第${Number(pageMatch[1])}页`
  }
  const pageChineseMatch = raw.match(/^第?\s*(\d+)\s*页$/)
  if (pageChineseMatch) {
    return `第${Number(pageChineseMatch[1])}页`
  }
  const secondRangeMatch = raw.match(/^(?:t)?\s*(\d{1,5})\s*[-~—]\s*(\d{1,5})$/i)
  if (secondRangeMatch) {
    return `${formatSecondsToClock(secondRangeMatch[1])}–${formatSecondsToClock(secondRangeMatch[2])}`
  }
  const clockRangeMatch = raw.match(/^(\d{1,2}:\d{2}(?::\d{2})?)\s*[-~—]\s*(\d{1,2}:\d{2}(?::\d{2})?)$/)
  if (clockRangeMatch) {
    return `${clockRangeMatch[1]}–${clockRangeMatch[2]}`
  }
  return raw.replace(/\bc(\d+)\b/ig, (_, pageNo) => `第${Number(pageNo)}页`)
}

function getSourceLocatorFromItem(item) {
  if (!item || typeof item !== 'object') {
    return ''
  }
  const directLocator = String(item.locator || '').trim()
  if (directLocator) {
    return directLocator
  }
  const sourceLabel = String(item.source_label || '')
  if (!sourceLabel.includes('·')) {
    return ''
  }
  return sourceLabel
    .split('·')
    .slice(1)
    .join('·')
    .trim()
}

function getReadableSourceLabel(item) {
  if (!item || typeof item !== 'object') {
    return '未匹配教材依据'
  }
  const filename = getReadableDocFilename(item)
  const locatorSource = getSourceLocatorFromItem(item)
  return `${filename} · ${getReadableLocator(locatorSource)}`
}

function getReadableDocFilename(item) {
  const raw = String(item?.doc_filename || '').trim()
  if (!raw) return '教学资料'
  if (/^文档#\d+$/i.test(raw)) {
    return '教学资料'
  }
  return raw
}

function getPlanStructureSummaryText() {
  const rows = Array.isArray(planForm.value.activityDesign) ? planForm.value.activityDesign : []
  const normalized = rows
    .map((row) => {
      const name = String(row?.name || '').trim()
      const duration = Number(row?.duration)
      if (!name) return null
      const durationLabel = Number.isFinite(duration) && duration > 0 ? `${duration}’` : ''
      return `${name}${durationLabel}`
    })
    .filter(Boolean)
  if (normalized.length) {
    return normalized.slice(0, 5).join(' → ')
  }
  const lessonDuration = Number(planForm.value.lessonDurationMin)
  if (Number.isFinite(lessonDuration) && lessonDuration > 0) {
    return `建议按 ${lessonDuration} 分钟节奏组织导入、讲解、练习与总结`
  }
  return '导入 → 讲解 → 例题 → 练习 → 小结'
}

function getPlanKeyPointSummaryText() {
  const keyConcepts = (Array.isArray(planForm.value.keyConcepts) ? planForm.value.keyConcepts : [])
    .map((item) => String(item || '').trim())
    .filter(Boolean)
  if (keyConcepts.length) {
    return keyConcepts.slice(0, 3).join('、')
  }
  const goals = (Array.isArray(planForm.value.teachingGoals) ? planForm.value.teachingGoals : [])
    .map((item) => String(item || '').trim())
    .filter(Boolean)
  return goals.length ? goals.slice(0, 2).join('、') : '待补充'
}

function getPlanCommonMistakeSummaryText() {
  const normalized = getNormalizedPlanFromDraft()
  const mistakes = (Array.isArray(normalized.common_mistakes) ? normalized.common_mistakes : [])
    .map((item) => String(item || '').trim())
    .filter(Boolean)
  if (mistakes.length) {
    return mistakes.slice(0, 3).join('、')
  }
  const focusRows = (Array.isArray(planForm.value.focusDifficulties) ? planForm.value.focusDifficulties : [])
    .map((item) => String(item || '').trim())
    .filter(Boolean)
  return focusRows.length ? focusRows.slice(0, 2).join('、') : '待补充'
}

function getPlanMissingEvidenceCount() {
  const conceptCount = getPlanConceptOptions().length
  const linkedCount = Array.isArray(planSourceLinks.value) ? planSourceLinks.value.length : 0
  if (!conceptCount) return 0
  return Math.max(0, conceptCount - linkedCount)
}

function getPlanCompletenessPercent() {
  if (!planId.value) return 0
  const confidenceRaw = Number(
    planConfidence.value?.plan_completeness
    ?? planConfidence.value?.completeness
    ?? planConfidence.value?.score
    ?? NaN,
  )
  if (Number.isFinite(confidenceRaw)) {
    const normalized = confidenceRaw <= 1 ? confidenceRaw * 100 : confidenceRaw
    return Math.max(0, Math.min(100, Math.round(normalized)))
  }
  const missingPenalty = Math.min(planMissingQuestions.value.length * 12, 48)
  const evidencePenalty = Math.min(getPlanMissingEvidenceCount() * 7, 28)
  const hasFocus = (Array.isArray(planForm.value.focusDifficulties) ? planForm.value.focusDifficulties : [])
    .some((item) => String(item || '').trim())
  const focusPenalty = hasFocus ? 0 : 10
  const base = 100 - missingPenalty - evidencePenalty - focusPenalty
  return Math.max(35, Math.min(100, Math.round(base)))
}

function getPlanRiskHints() {
  if (!planId.value) {
    return ['尚未生成教学计划，建议先点击“更新并应用”']
  }
  const hints = []
  if (planMissingQuestions.value.length) {
    hints.push(`关键信息待补齐（${planMissingQuestions.value.length}项）`)
  }
  const missingEvidence = getPlanMissingEvidenceCount()
  if (missingEvidence > 0) {
    hints.push(`引用证据不足（${missingEvidence}条知识点尚未匹配教材）`)
  }
  const hasFocus = (Array.isArray(planForm.value.focusDifficulties) ? planForm.value.focusDifficulties : [])
    .some((item) => String(item || '').trim())
  if (!hasFocus) {
    hints.push('难点未明确（建议补一句）')
  }
  return hints.length ? hints : ['风险较低，可直接生成内容。']
}

function getPlanRiskSummaryText() {
  return getPlanRiskHints().join(' / ')
}

function getPlanCompletenessBarClass() {
  const score = getPlanCompletenessPercent()
  if (score >= 90) return 'bg-emerald-300'
  if (score >= 70) return 'bg-amber-300'
  return 'bg-rose-300'
}

function extractCitationMarkerForPreviewPage(index) {
  const slideRows = Array.isArray(pptArtifact.value?.slides?.slides)
    ? pptArtifact.value.slides.slides
    : []
  if (!slideRows.length || index < 0) {
    return null
  }
  const slide = slideRows[index]
  if (!slide || typeof slide !== 'object') {
    return null
  }
  const rows = []
  if (Array.isArray(slide.content)) {
    rows.push(...slide.content)
  } else if (slide.content) {
    rows.push(slide.content)
  }
  if (Array.isArray(slide.bullets)) {
    rows.push(...slide.bullets)
  }
  if (Array.isArray(slide.left_items)) {
    rows.push(...slide.left_items)
  }
  if (Array.isArray(slide.right_items)) {
    rows.push(...slide.right_items)
  }
  if (Array.isArray(slide.citations)) {
    rows.push(...slide.citations)
  }
  const mergedText = [slide.title, ...rows]
    .map((item) => String(item || '').trim())
    .filter(Boolean)
    .join(' ')
  if (!mergedText) {
    return null
  }
  const marker = mergedText.match(/【d(\d+)(?::([^】]+))?】/)
  if (!marker) {
    return null
  }
  const docId = Number(marker[1])
  if (!Number.isInteger(docId) || docId <= 0) {
    return null
  }
  const locator = String(marker[2] || '').trim()
  return { docId, locator }
}

function findSourceLinkByMarker(docId, locator = '') {
  if (!Array.isArray(planSourceLinks.value) || !planSourceLinks.value.length) {
    return null
  }
  const normalizedLocator = String(locator || '').trim().toLowerCase()
  if (normalizedLocator) {
    const exact = planSourceLinks.value.find((item) => {
      const itemDocId = Number(item?.doc_id)
      if (!Number.isInteger(itemDocId) || itemDocId !== docId) {
        return false
      }
      const itemLocator = String(item?.locator || '').toLowerCase()
      return itemLocator.includes(normalizedLocator)
    })
    if (exact) {
      return exact
    }
  }
  return planSourceLinks.value.find((item) => Number(item?.doc_id) === docId) || null
}

function getSourceLinkForPreviewPage(index) {
  if (!Array.isArray(planSourceLinks.value) || !planSourceLinks.value.length) {
    return null
  }
  const marker = extractCitationMarkerForPreviewPage(index)
  if (marker) {
    const matched = findSourceLinkByMarker(marker.docId, marker.locator)
    if (matched) {
      return matched
    }
  }
  return planSourceLinks.value[index % planSourceLinks.value.length]
}

function openCitationEvidence(index) {
  const link = getSourceLinkForPreviewPage(index)
  if (!link) return
  activeCitationEvidence.value = link
  showCitationEvidenceModal.value = true
}

function closeCitationEvidenceModal() {
  showCitationEvidenceModal.value = false
  activeCitationEvidence.value = null
}

function parseTopicTags(rawValue) {
  const normalize = (item) => {
    if (item && typeof item === 'object') {
      return String(item.label || item.name || item.title || item.text || item.value || '').trim()
    }
    return String(item || '').trim()
  }
  const dedupe = (rows) => {
    const result = []
    const seen = new Set()
    rows.forEach((item) => {
      const normalized = normalize(item)
      if (!normalized) return
      const key = normalized.toLowerCase()
      if (seen.has(key)) return
      seen.add(key)
      result.push(normalized)
    })
    return result
  }
  if (Array.isArray(rawValue)) {
    return dedupe(rawValue)
  }
  return dedupe(String(rawValue || '')
    .split(/[、,，;；\n]/)
    .map((item) => item.trim()))
}

function normalizePlanScopePolicy(value) {
  if (value === 'session') return 'session_only'
  if (value === 'user') return 'user_only'
  if (value === 'base') return 'base_only'
  if (['session_first', 'session_only', 'user_only', 'base_only'].includes(value)) {
    return value
  }
  return 'session_only'
}

function getNormalizedPlanFromDraft() {
  let parsed = {}
  try {
    parsed = JSON.parse(planDraft.value || '{}')
  } catch {
    parsed = {}
  }
  if (!parsed || typeof parsed !== 'object') {
    parsed = {}
  }
  const topicTags = parseTopicTags(parsed.topic_tags)

  return {
    ...parsed,
    grade_stage: parsed.grade_stage || '',
    subject: parsed.subject || '',
    unit_text: parsed.unit_text || '',
    topic_tags: topicTags,
    scope_policy: normalizePlanScopePolicy(parsed.scope_policy),
    lesson_duration_min: parsed.lesson_duration_min ?? '',
    teaching_goals: Array.isArray(parsed.teaching_goals) ? parsed.teaching_goals : [],
    key_concepts: Array.isArray(parsed.key_concepts) ? parsed.key_concepts : [],
    focus_difficulties: Array.isArray(parsed.focus_difficulties) ? parsed.focus_difficulties : [],
    activity_design: Array.isArray(parsed.activity_design) ? parsed.activity_design : [],
    style: parsed.style && typeof parsed.style === 'object' ? parsed.style : {},
    logic_flow: Array.isArray(parsed.logic_flow) ? parsed.logic_flow : [],
    assessment: Array.isArray(parsed.assessment) ? parsed.assessment : [],
    evidence_policy: parsed.evidence_policy && typeof parsed.evidence_policy === 'object'
      ? parsed.evidence_policy
      : { must_cite_sources: true },
    missing_questions: Array.isArray(parsed.missing_questions) ? parsed.missing_questions : [],
    confidence: parsed.confidence && typeof parsed.confidence === 'object' ? parsed.confidence : {},
    common_mistakes: Array.isArray(parsed.common_mistakes) ? parsed.common_mistakes : [],
    teacher_extra_prompt: parsed.teacher_extra_prompt || '',
  }
}

function toEditableStringList(rawValue) {
  const source = Array.isArray(rawValue) ? rawValue : []
  const list = source
    .map((item) => {
      if (typeof item === 'string') return item.trim()
      if (item && typeof item === 'object') {
        return String(item.label || item.name || item.title || item.text || '').trim()
      }
      return String(item || '').trim()
    })
    .filter(Boolean)
  return list.length ? list : ['']
}

function toEditableActivityRows(rawRows) {
  const source = Array.isArray(rawRows) ? rawRows : []
  const rows = source
    .map((item) => {
      if (item && typeof item === 'object') {
        return {
          name: String(item.name || item.title || '').trim(),
          duration: String(item.duration_min ?? item.duration ?? '').trim(),
        }
      }
      return null
    })
    .filter((item) => item && (item.name || item.duration))
  return rows.length ? rows : [{ name: '', duration: '' }]
}

function syncPlanFormFromPlanObject(planObject = getNormalizedPlanFromDraft()) {
  planForm.value = {
    gradeStage: String(planObject.grade_stage || ''),
    subject: String(planObject.subject || ''),
    unitText: String(planObject.unit_text || ''),
    topicTagsText: parseTopicTags(planObject.topic_tags).join('、'),
    scopePolicy: normalizePlanScopePolicy(planObject.scope_policy),
    lessonDurationMin: planObject.lesson_duration_min === '' || planObject.lesson_duration_min === null
      ? ''
      : String(planObject.lesson_duration_min),
    teachingGoals: toEditableStringList(planObject.teaching_goals),
    keyConcepts: toEditableStringList(planObject.key_concepts),
    focusDifficulties: toEditableStringList(planObject.focus_difficulties),
    activityDesign: toEditableActivityRows(planObject.activity_design),
  }
}

function syncPlanFormFromDraft() {
  syncPlanFormFromPlanObject(getNormalizedPlanFromDraft())
}

function buildPlanPayloadFromForm() {
  const merged = getNormalizedPlanFromDraft()
  const lessonDuration = Number(planForm.value.lessonDurationMin)
  merged.grade_stage = String(planForm.value.gradeStage || '').trim() || null
  merged.subject = String(planForm.value.subject || '').trim() || null
  merged.unit_text = String(planForm.value.unitText || '').trim() || null
  merged.topic_tags = parseTopicTags(planForm.value.topicTagsText)
  merged.scope_policy = normalizePlanScopePolicy(planForm.value.scopePolicy)
  merged.lesson_duration_min = Number.isFinite(lessonDuration) && lessonDuration > 0 ? lessonDuration : null
  merged.teaching_goals = planForm.value.teachingGoals
    .map((item) => String(item || '').trim())
    .filter(Boolean)
  merged.key_concepts = planForm.value.keyConcepts
    .map((item) => String(item || '').trim())
    .filter(Boolean)
  merged.focus_difficulties = planForm.value.focusDifficulties
    .map((item) => String(item || '').trim())
    .filter(Boolean)
  merged.activity_design = planForm.value.activityDesign
    .map((row) => {
      const name = String(row?.name || '').trim()
      const duration = Number(row?.duration)
      if (!name && !Number.isFinite(duration)) {
        return null
      }
      return {
        name: name || '教学活动',
        duration_min: Number.isFinite(duration) && duration > 0 ? duration : null,
      }
    })
    .filter(Boolean)
  return merged
}

function setPlanDraftFromForm() {
  planDraft.value = JSON.stringify(buildPlanPayloadFromForm(), null, 2)
}

async function persistPlanDraft(options = {}) {
  if (!planId.value) {
    return Number(planId.value || 0) || null
  }
  const token = getTeacherToken()
  if (!token) {
    return Number(planId.value || 0) || null
  }

  const throwOnError = !!options?.throwOnError
  const silent = options?.silent !== false

  if (!showPlanJsonAdvanced.value) {
    setPlanDraftFromForm()
  }

  let parsedPlan
  try {
    parsedPlan = JSON.parse(planDraft.value || '{}')
  } catch (err) {
    if (!silent) {
      planError.value = `计划数据格式错误：${err.message}`
    }
    if (throwOnError) {
      throw err
    }
    return Number(planId.value || 0) || null
  }

  try {
    await patchPlanObject(parsedPlan)
  } catch (err) {
    if (!silent) {
      planError.value = `保存失败：${err.message}`
    } else {
      console.warn('[plan-save] persist draft failed:', err)
    }
    if (throwOnError) {
      throw err
    }
    return Number(planId.value || 0) || null
  }

  return Number(planId.value || 0) || null
}

function addPlanListItem(field) {
  if (!Array.isArray(planForm.value[field])) return
  planForm.value[field].push('')
}

function removePlanListItem(field, index) {
  if (!Array.isArray(planForm.value[field])) return
  if (planForm.value[field].length <= 1) {
    planForm.value[field][0] = ''
    return
  }
  planForm.value[field].splice(index, 1)
}

function addPlanActivityRow() {
  planForm.value.activityDesign.push({ name: '', duration: '' })
}

function removePlanActivityRow(index) {
  if (planForm.value.activityDesign.length <= 1) {
    planForm.value.activityDesign[0] = { name: '', duration: '' }
    return
  }
  planForm.value.activityDesign.splice(index, 1)
}

function getPlanConceptOptions() {
  const normalized = getNormalizedPlanFromDraft()
  const rows = Array.isArray(normalized.key_concepts) ? normalized.key_concepts : []
  const options = rows
    .map((item, index) => {
      if (item && typeof item === 'object') {
        const value = String(item.id || item.key || `kc_${index + 1}`)
        const label = String(item.label || item.name || item.title || item.text || value)
        return { value, label }
      }
      const label = String(item || '').trim()
      if (!label) return null
      return { value: `kc_${index + 1}`, label }
    })
    .filter(Boolean)
  return options
}

function getPlanConceptLabelById(value) {
  const normalized = String(value || '').trim()
  if (!normalized) return '未指定知识点'
  const matched = getPlanConceptOptions().find((item) => item.value === normalized)
  return matched?.label || normalized
}

function getSourceLinkStatusText(item) {
  const hasDoc = Number(item?.doc_id) > 0
  if (!hasDoc) return '来源缺失'
  const hasLocator = Number(item?.chunk_id) > 0 || Boolean(String(item?.locator || '').trim())
  return hasLocator ? '已定位' : '待补充定位'
}

async function prepareSourceLinkForEdit(item) {
  if (!item || typeof item !== 'object') return
  setPlanModalStage('evidence')
  linkDocId.value = Number(item.doc_id) > 0 ? String(item.doc_id) : ''
  linkConceptId.value = String(item.key_concept_id || '').trim()
  linkChunkId.value = Number(item.chunk_id) > 0 ? String(item.chunk_id) : ''
  linkLocator.value = String(item.locator || '').trim()
  linkNote.value = String(item.note || '').trim()

  if (linkDocId.value) {
    await loadKbOutline(linkDocId.value)
  } else {
    kbOutline.value = []
  }
}

function resetMissingTaskAnswers() {
  missingTaskAnswers.value = {
    class_level: '',
    unified_answer_sheet: null,
    include_animation: null,
    group_work: null,
    common_mistakes: [],
    extra_notes: '',
  }
}

function toggleCommonMistake(option) {
  const normalized = String(option || '').trim()
  if (!normalized) return
  const current = Array.isArray(missingTaskAnswers.value.common_mistakes)
    ? [...missingTaskAnswers.value.common_mistakes]
    : []
  const index = current.indexOf(normalized)
  if (index >= 0) {
    current.splice(index, 1)
  } else {
    current.push(normalized)
  }
  missingTaskAnswers.value.common_mistakes = current
}

function openMissingQuestionsTaskModal(questions = []) {
  resetMissingTaskAnswers()
  missingQuestionsForModal.value = Array.isArray(questions) ? questions : []
  missingQuestionsModalError.value = ''
  showMissingQuestionsModal.value = true
  return new Promise((resolve) => {
    missingQuestionsModalResolver = resolve
  })
}

function closeMissingQuestionsTaskModal() {
  showMissingQuestionsModal.value = false
  missingQuestionsForModal.value = []
}

function cancelMissingQuestionsTaskModal() {
  if (missingQuestionsModalLoading.value) return
  closeMissingQuestionsTaskModal()
  if (typeof missingQuestionsModalResolver === 'function') {
    missingQuestionsModalResolver(null)
    missingQuestionsModalResolver = null
  }
}

function submitMissingQuestionsTaskModal() {
  if (missingQuestionsModalLoading.value) return
  closeMissingQuestionsTaskModal()
  if (typeof missingQuestionsModalResolver === 'function') {
    missingQuestionsModalResolver({ ...missingTaskAnswers.value })
    missingQuestionsModalResolver = null
  }
}

function closePlanMoreMenu() {
  showPlanMoreMenu.value = false
}

function togglePlanMoreMenu() {
  showPlanMoreMenu.value = !showPlanMoreMenu.value
}

async function handleRefreshPlanDetailFromMenu() {
  closePlanMoreMenu()
  if (!planId.value) return
  planError.value = ''
  try {
    await loadPlanDetail(planId.value)
  } catch (err) {
    planError.value = `刷新失败：${err.message}`
  }
}

function handleTogglePlanEditorFromMenu() {
  closePlanMoreMenu()
  if (!isDeveloperMode.value) return
  togglePlanEditorMode()
}

async function handleAutoLinkSourcesFromMenu() {
  closePlanMoreMenu()
  await handleAutoLinkSources()
}

function buildPlanDiagnosticsPayload() {
  let rawPlan = {}
  try {
    rawPlan = JSON.parse(planDraft.value || '{}')
  } catch (err) {
    rawPlan = {
      _parse_error: String(err?.message || err || 'invalid plan draft'),
      _raw: String(planDraft.value || ''),
    }
  }
  return {
    generated_at: new Date().toISOString(),
    conversation_id: props.conversationId ? String(props.conversationId) : null,
    plan_id: planId.value ? Number(planId.value) : null,
    plan_title: planTitle.value || '',
    pending_messages: getPendingPlanMessageCount(),
    missing_questions: [...planMissingQuestions.value],
    preference_text: composePlanConstraintText(planConstraint.value),
    plan_data: rawPlan,
    source_links: Array.isArray(planSourceLinks.value) ? planSourceLinks.value : [],
  }
}

async function copyTextToClipboard(text) {
  if (!text) return false
  if (navigator?.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(text)
      return true
    } catch {
      // fallback to execCommand
    }
  }
  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', 'readonly')
  textarea.style.position = 'fixed'
  textarea.style.left = '-9999px'
  document.body.appendChild(textarea)
  textarea.select()
  try {
    return document.execCommand('copy')
  } catch {
    return false
  } finally {
    textarea.remove()
  }
}

async function copyPlanDiagnostics() {
  if (!isDeveloperMode.value) return
  planError.value = ''
  const payload = buildPlanDiagnosticsPayload()
  const ok = await copyTextToClipboard(JSON.stringify(payload, null, 2))
  if (ok) {
    planStatus.value = '诊断信息已复制到剪贴板'
  } else {
    planError.value = '复制失败，请检查浏览器剪贴板权限'
  }
}

async function handleCopyPlanDiagnosticsFromMenu() {
  closePlanMoreMenu()
  await copyPlanDiagnostics()
}

function exportPlanInternalData() {
  if (!isDeveloperMode.value) return
  const payload = buildPlanDiagnosticsPayload()
  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json;charset=utf-8' })
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  const safePlanId = Number(planId.value) || 'draft'
  link.href = url
  link.download = `plan_internal_${safePlanId}_${Date.now()}.json`
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.URL.revokeObjectURL(url)
  planStatus.value = '内部计划数据已导出'
}

function handleExportPlanDataFromMenu() {
  closePlanMoreMenu()
  exportPlanInternalData()
}

function togglePlanEditorMode() {
  if (!isDeveloperMode.value) return
  if (showPlanJsonAdvanced.value) {
    try {
      syncPlanFormFromDraft()
      showPlanJsonAdvanced.value = false
      planError.value = ''
    } catch (err) {
      planError.value = `计划数据无法解析：${err.message}`
    }
    return
  }
  setPlanDraftFromForm()
  showPlanJsonAdvanced.value = true
}

function revokePptPreviewUrls() {
  for (const url of pptPreviewObjectUrls) {
    try {
      window.URL.revokeObjectURL(url)
    } catch {
      // ignore
    }
  }
  pptPreviewObjectUrls = []
  pptPreviewUrls.value = []
}

function resetPptArtifactState() {
  pptArtifact.value = null
  pptVersionHistory.value = []
  pptPreviewError.value = ''
  pptPreviewLoading.value = false
  revokePptPreviewUrls()
  closeCitationEvidenceModal()
}

async function loadPptVersionHistory(conversationKey = props.conversationId) {
  if (!conversationKey) {
    pptVersionHistory.value = []
    return []
  }
  if (!getTeacherToken()) {
    pptVersionHistory.value = []
    return []
  }

  const response = await fetch(`${PPT_ARTIFACT_BY_CONVERSATION_API_URL}/${encodeURIComponent(String(conversationKey))}`, {
    headers: getTeacherAuthHeaders(),
  })
  if (response.status === 404) {
    pptVersionHistory.value = []
    return []
  }
  const data = await response.json().catch(() => ({}))
  if (!response.ok) {
    throw new Error(data.detail || `HTTP ${response.status}`)
  }

  const items = Array.isArray(data.items) ? data.items : []
  pptVersionHistory.value = items
  return items
}

async function loadPptPreviewImages(artifactId, previewPages = []) {
  revokePptPreviewUrls()
  const pages = Array.isArray(previewPages) ? previewPages : []
  if (!pages.length) {
    return []
  }

  const loaded = []
  for (const pageNumber of pages) {
    const response = await fetch(`${PPT_ARTIFACT_API_URL}/${artifactId}/preview/${pageNumber}`, {
      headers: getTeacherAuthHeaders(),
    })
    if (!response.ok) {
      const data = await response.json().catch(() => ({}))
      throw new Error(data.detail || `HTTP ${response.status}`)
    }
    const blob = await response.blob()
    const objectUrl = window.URL.createObjectURL(blob)
    pptPreviewObjectUrls.push(objectUrl)
    loaded.push(objectUrl)
  }

  pptPreviewUrls.value = loaded
  return loaded
}

async function loadPptArtifactDetail(artifactId) {
  const response = await fetch(`${PPT_ARTIFACT_API_URL}/${artifactId}`, {
    headers: getTeacherAuthHeaders(),
  })
  const data = await response.json().catch(() => ({}))
  if (!response.ok) {
    throw new Error(data.detail || `HTTP ${response.status}`)
  }

  const artifact = data?.artifact && typeof data.artifact === 'object' ? data.artifact : null
  if (!artifact?.id) {
    throw new Error('未返回有效课件版本信息')
  }
  pptArtifact.value = {
    ...artifact,
    slides: data?.slides && typeof data.slides === 'object' ? data.slides : {},
  }

  const previewPages = Array.isArray(data?.preview_pages) ? data.preview_pages : []
  await loadPptPreviewImages(artifact.id, previewPages)
  return pptArtifact.value
}

async function loadPptArtifactById(artifactId, conversationKey = props.conversationId) {
  const numericId = Number(artifactId || 0)
  if (!Number.isFinite(numericId) || numericId <= 0) {
    return null
  }
  if (conversationKey) {
    await loadPptVersionHistory(conversationKey)
  }
  return loadPptArtifactDetail(numericId)
}

async function loadLatestPptArtifact(conversationKey = props.conversationId, options = {}) {
  const silent = !!options?.silent
  if (!conversationKey) {
    resetPptArtifactState()
    if (!silent) {
      pptPreviewError.value = '当前会话暂无可预览的课件版本'
    }
    return null
  }
  if (!getTeacherToken()) {
    resetPptArtifactState()
    if (!silent) {
      pptPreviewError.value = '请先以教师身份登录后再查看课件版本'
    }
    return null
  }

  if (!silent) {
    pptPreviewLoading.value = true
  }
  pptPreviewError.value = ''

  try {
    const response = await fetch(`${PPT_ARTIFACT_BY_CONVERSATION_API_URL}/${encodeURIComponent(String(conversationKey))}/latest`, {
      headers: getTeacherAuthHeaders(),
    })
    if (response.status === 404) {
      resetPptArtifactState()
      if (!silent) {
        pptPreviewError.value = '当前会话尚未生成 PPT 版本'
      }
      return null
    }
    const data = await response.json().catch(() => ({}))
    if (!response.ok) {
      throw new Error(data.detail || `HTTP ${response.status}`)
    }

    const artifact = data?.artifact && typeof data.artifact === 'object' ? data.artifact : null
    if (!artifact?.id) {
      resetPptArtifactState()
      return null
    }
    await Promise.all([
      loadPptVersionHistory(conversationKey),
      loadPptArtifactDetail(artifact.id),
    ])
    return artifact
  } catch (err) {
    if (!silent) {
      pptPreviewError.value = `加载预览失败：${err.message}`
    }
    return null
  } finally {
    if (!silent) {
      pptPreviewLoading.value = false
    }
  }
}

function resetPlanState() {
  planAutoSyncEnabled.value = false
  planAutoSyncHintPushed.value = false
  planAutoSyncRunning = false
  planAutoSyncQueued = false
  planModalStage.value = 'preferences'
  planId.value = null
  planTitle.value = ''
  planDraft.value = ''
  showPlanJsonAdvanced.value = false
  showPlanMoreMenu.value = false
  planMissingQuestions.value = []
  planConfidence.value = {}
  planSourceLinks.value = []
  planMessageCount.value = 0
  planLastMessageAt.value = ''
  planError.value = ''
  planLinkError.value = ''
  planStatus.value = ''
  linkDocId.value = ''
  linkChunkId.value = ''
  linkConceptId.value = ''
  linkLocator.value = ''
  linkNote.value = ''
  planPreferenceQuick.value = {
    teachingStyle: '',
    practiceDifficulty: '',
    courseStructure: '',
    needQuiz: false,
    needHomework: false,
    needBoardDesign: false,
  }
  usePlanForPpt.value = true
  usePlanForDoc.value = true
  kbOutline.value = []
  kbOutlineError.value = ''
  missingQuestionsModalError.value = ''
  showMissingQuestionsModal.value = false
  missingQuestionsForModal.value = []
  if (typeof missingQuestionsModalResolver === 'function') {
    missingQuestionsModalResolver(null)
    missingQuestionsModalResolver = null
  }
  resetMissingTaskAnswers()
  syncPlanFormFromPlanObject()
}

function applyPlanPayload(payload) {
  const hadPlan = !!planId.value
  const nextId = Number(payload?.plan_id || payload?.id || 0)
  if (Number.isFinite(nextId) && nextId > 0) {
    planId.value = nextId
    planAutoSyncEnabled.value = true
    if (!hadPlan) {
      usePlanForPpt.value = true
      usePlanForDoc.value = true
    }
  }
  planTitle.value = payload?.title || planTitle.value || '教学计划'
  const planObject = payload?.plan && typeof payload.plan === 'object' ? payload.plan : {}
  planDraft.value = JSON.stringify(planObject, null, 2)
  planMessageCount.value = Number(payload?.message_count || 0)
  planLastMessageAt.value = String(payload?.last_message_at || '')
  planMissingQuestions.value = Array.isArray(payload?.missing_questions) ? payload.missing_questions : []
  planConfidence.value = payload?.confidence && typeof payload.confidence === 'object' ? payload.confidence : {}
  planSourceLinks.value = Array.isArray(payload?.source_links) ? payload.source_links : []
  syncPlanFormFromDraft()
  if (!linkConceptId.value) {
    const options = getPlanConceptOptions()
    if (options.length) {
      linkConceptId.value = options[0].value
    }
  }
}

async function loadPlanDetail(targetPlanId = planId.value) {
  const id = Number(targetPlanId)
  if (!Number.isFinite(id) || id <= 0) return
  const token = getTeacherToken()
  if (!token) return

  const response = await fetch(`${API_BASE}/plan/${id}`, {
    headers: getTeacherAuthHeaders(),
  })
  const data = await response.json().catch(() => ({}))
  if (!response.ok) {
    throw new Error(data.detail || `HTTP ${response.status}`)
  }
  applyPlanPayload(data)
}

async function loadLatestPlanByConversation(conversationKey = props.conversationId) {
  if (!conversationKey) return null
  const token = getTeacherToken()
  if (!token) return null

  const response = await fetch(`${PLAN_BY_CONVERSATION_API_URL}/${encodeURIComponent(String(conversationKey))}/latest`, {
    headers: getTeacherAuthHeaders(),
  })
  if (response.status === 404) {
    return null
  }
  const data = await response.json().catch(() => ({}))
  if (!response.ok) {
    throw new Error(data.detail || `HTTP ${response.status}`)
  }
  applyPlanPayload(data)
  return data
}

async function preloadLatestPlan(conversationKey = props.conversationId) {
  if (!conversationKey) return
  try {
    await loadLatestPlanByConversation(conversationKey)
  } catch {
    // 切换会话时静默预加载，不阻断对话流程
  }
}

async function extractPlanByConversation(conversationKey = props.conversationId, constraintText = planConstraint.value) {
  const response = await fetch(PLAN_EXTRACT_API_URL, {
    method: 'POST',
    headers: getTeacherAuthHeaders(true),
    body: JSON.stringify({
      conversation_key: String(conversationKey),
      explicit_constraints: buildConstraintPayload(constraintText),
    }),
  })
  const data = await response.json().catch(() => ({}))
  if (!response.ok) {
    throw new Error(data.detail || `HTTP ${response.status}`)
  }
  applyPlanPayload(data)
  return data
}

async function patchPlanObject(nextPlan) {
  const response = await fetch(`${API_BASE}/plan/${planId.value}`, {
    method: 'PATCH',
    headers: getTeacherAuthHeaders(true),
    body: JSON.stringify({ plan: nextPlan }),
  })
  const data = await response.json().catch(() => ({}))
  if (!response.ok) {
    throw new Error(data.detail || `HTTP ${response.status}`)
  }
  applyPlanPayload(data)
  return data
}

async function patchPlanWithMissingAnswers(answers = {}) {
  const nextPlan = buildPlanPayloadFromForm()
  const classLevel = String(answers?.class_level || '').trim()
  if (classLevel) {
    nextPlan.class_profile = {
      ...(nextPlan.class_profile && typeof nextPlan.class_profile === 'object' ? nextPlan.class_profile : {}),
      level: classLevel,
    }
  }

  nextPlan.delivery_preferences = {
    ...(nextPlan.delivery_preferences && typeof nextPlan.delivery_preferences === 'object' ? nextPlan.delivery_preferences : {}),
    unified_answer_sheet: answers?.unified_answer_sheet,
    include_animation: answers?.include_animation,
    group_work: answers?.group_work,
  }

  if (Array.isArray(answers?.common_mistakes) && answers.common_mistakes.length) {
    nextPlan.common_mistakes = answers.common_mistakes
  }

  const extraNotes = String(answers?.extra_notes || '').trim()
  if (extraNotes) {
    nextPlan.teacher_extra_prompt = [
      String(nextPlan.teacher_extra_prompt || '').trim(),
      `补充约束：${extraNotes}`,
    ]
      .filter(Boolean)
      .join('\n')
  }

  nextPlan.missing_questions = []
  planDraft.value = JSON.stringify(nextPlan, null, 2)
  return patchPlanObject(nextPlan)
}

async function runAutoLinkSources(options = {}) {
  if (!planId.value) {
    return []
  }
  const topK = Number(options?.topK || 2)
  const silent = !!options?.silent
  if (!silent) {
    planLinkLoading.value = true
    planLinkError.value = ''
  }

  try {
    const response = await fetch(`${PLAN_AUTO_LINK_API_URL_BASE}/${planId.value}/auto_link_sources`, {
      method: 'POST',
      headers: getTeacherAuthHeaders(true),
      body: JSON.stringify({
        top_k: Number.isFinite(topK) && topK > 0 ? topK : 2,
        clear_existing: true,
      }),
    })
    const data = await response.json().catch(() => ({}))
    if (!response.ok) {
      throw new Error(data.detail || `HTTP ${response.status}`)
    }
    planSourceLinks.value = Array.isArray(data.source_links) ? data.source_links : []
    return planSourceLinks.value
  } catch (err) {
    if (!silent) {
      planLinkError.value = `自动匹配失败：${err.message}`
    }
    throw err
  } finally {
    if (!silent) {
      planLinkLoading.value = false
    }
  }
}

async function ensurePlanForGeneration(options = {}) {
  const token = getTeacherToken()
  if (!token || !props.conversationId) {
    return null
  }

  const shouldExtract = !!options?.forceExtract || !planId.value
  if (shouldExtract) {
    await extractPlanByConversation(props.conversationId, planConstraint.value)
    planStatus.value = '教学计划已更新'
  } else if (planId.value) {
    await persistPlanDraft({
      silent: true,
      throwOnError: true,
    })
  }

  const shouldResolveMissing = options?.resolveMissing !== false
  const resolvedMissingQuestions = getResolvedMissingQuestionsForModal()
  if (!planMissingQuestions.value.length && resolvedMissingQuestions.length) {
    planMissingQuestions.value = [...resolvedMissingQuestions]
  }
  if (shouldResolveMissing && resolvedMissingQuestions.length) {
    const answers = await openMissingQuestionsTaskModal(resolvedMissingQuestions)
    if (!answers) {
      if (options?.failOnMissingCancel === false) {
        return Number(planId.value || 0) || null
      }
      throw new Error('已取消补齐缺失信息')
    }
    missingQuestionsModalLoading.value = true
    missingQuestionsModalError.value = ''
    try {
      await patchPlanWithMissingAnswers(answers)
    } finally {
      missingQuestionsModalLoading.value = false
    }
  }

  if (options?.autoLink !== false && planId.value) {
    await runAutoLinkSources({
      topK: Number(options?.topK || 2),
      silent: !!options?.silentAutoLink,
    })
  }

  return Number(planId.value)
}

async function handleCompleteMissingTasks() {
  const resolvedMissingQuestions = getResolvedMissingQuestionsForModal()
  if (!planId.value || !resolvedMissingQuestions.length) return
  if (!planMissingQuestions.value.length) {
    planMissingQuestions.value = [...resolvedMissingQuestions]
  }
  try {
    const answers = await openMissingQuestionsTaskModal(resolvedMissingQuestions)
    if (!answers) return
    missingQuestionsModalLoading.value = true
    await patchPlanWithMissingAnswers(answers)
    try {
      await runAutoLinkSources({ topK: 2, silent: true })
      planLinkError.value = ''
    } catch (linkErr) {
      planLinkError.value = `自动匹配失败：${linkErr.message}`
    }
    planStatus.value = '缺失信息已补齐，已自动应用并完成引用匹配'
  } catch (err) {
    planError.value = `补齐失败：${err.message}`
  } finally {
    missingQuestionsModalLoading.value = false
  }
}

async function handleQuickUpdatePlan() {
  if (planLoading.value || pptLoading.value) return
  if (!props.conversationId) {
    planError.value = '请先创建会话并完成至少一轮对话'
    return
  }
  const token = getTeacherToken()
  if (!token) {
    planError.value = '请先登录教师账号'
    return
  }

  planLoading.value = true
  planError.value = ''
  planLinkError.value = ''
  try {
    const ensuredPlanId = await ensurePlanForGeneration({
      forceExtract: true,
      autoLink: true,
      topK: 2,
      silentAutoLink: true,
      failOnMissingCancel: false,
    })
    if (ensuredPlanId) {
      planStatus.value = '教学计划已更新并应用'
    }
  } catch (err) {
    planError.value = `更新计划失败：${err.message}`
  } finally {
    planLoading.value = false
  }
}

async function loadKbDocsForPlan() {
  const token = getTeacherToken()
  if (!token) return

  kbDocsLoading.value = true
  kbDocError.value = ''
  try {
    const response = await fetch(KB_DOCS_API_URL, {
      headers: getTeacherAuthHeaders(),
    })
    const data = await response.json().catch(() => ({}))
    if (!response.ok) {
      throw new Error(data.detail || `HTTP ${response.status}`)
    }
    kbDocs.value = Array.isArray(data.items) ? data.items : []
  } catch (err) {
    kbDocError.value = `加载资料失败：${err.message}`
  } finally {
    kbDocsLoading.value = false
  }
}

async function loadKbOutline(docIdValue = linkDocId.value) {
  const docId = Number(docIdValue)
  if (!Number.isInteger(docId) || docId <= 0) {
    kbOutline.value = []
    kbOutlineError.value = ''
    return
  }

  const token = getTeacherToken()
  if (!token) return

  kbOutlineLoading.value = true
  kbOutlineError.value = ''
  try {
    const response = await fetch(`${API_BASE}/kb/doc/${docId}/outline`, {
      headers: getTeacherAuthHeaders(),
    })
    const data = await response.json().catch(() => ({}))
    if (!response.ok) {
      throw new Error(data.detail || `HTTP ${response.status}`)
    }
    kbOutline.value = Array.isArray(data.outline) ? data.outline : []
  } catch (err) {
    kbOutline.value = []
    kbOutlineError.value = `加载片段失败：${err.message}`
  } finally {
    kbOutlineLoading.value = false
  }
}

function openPlanModal() {
  planError.value = ''
  planLinkError.value = ''
  planStatus.value = ''
  setPlanModalStage('preferences')
  showPlanMoreMenu.value = false
  showPlanModal.value = true
  loadKbDocsForPlan().catch(() => {})

  const token = getTeacherToken()
  if (!token) {
    planError.value = '请先以教师身份登录后再使用教学计划功能'
    return
  }

  if (!props.conversationId) {
    planError.value = '请先创建会话并进行对话'
    return
  }

  if (planId.value) {
    loadPlanDetail(planId.value).catch((err) => {
      planError.value = `加载教学计划失败：${err.message}`
    })
    return
  }

  loadLatestPlanByConversation(props.conversationId)
    .then((data) => {
      if (!data) {
        if (getConversationUserMessageCount() > 0) {
          planStatus.value = '正在根据当前对话自动生成教学计划...'
          queueAutoSyncPlanFromConversation({
            conversationKey: props.conversationId,
            force: true,
          }).catch(() => {})
        } else {
          planStatus.value = '当前会话尚未生成教学计划'
        }
      } else {
        planStatus.value = getPlanPreparationStateText()
      }
    })
    .catch((err) => {
      planError.value = `加载教学计划失败：${err.message}`
    })
}

async function closePlanModal() {
  if (planLoading.value || planSaving.value || planLinkLoading.value) return
  showPlanMoreMenu.value = false
  planModalStage.value = 'preferences'

  if (planId.value) {
    planSaving.value = true
    planError.value = ''
    try {
      await persistPlanDraft({
        silent: true,
        throwOnError: true,
      })
      planStatus.value = '教学计划已自动保存'
    } catch (err) {
      planError.value = `自动保存失败：${err.message}`
      planSaving.value = false
      return
    } finally {
      planSaving.value = false
    }
  }

  showPlanModal.value = false
}

async function handleExtractPlan() {
  if (planLoading.value) return
  if (!props.conversationId) {
    planError.value = '请先创建会话并完成至少一轮对话'
    return
  }
  const token = getTeacherToken()
  if (!token) {
    planError.value = '请先以教师身份登录后再生成教学计划'
    return
  }

  planLoading.value = true
  planError.value = ''
  planStatus.value = ''
  try {
    const data = await extractPlanByConversation(props.conversationId, planConstraint.value)
    planStatus.value = `已生成教学计划（${Array.isArray(data.missing_questions) ? data.missing_questions.length : 0} 项待补齐）`
    planAutoSyncEnabled.value = true
    planAutoSyncHintPushed.value = true
    pushPlanHintCard(data)
  } catch (err) {
    planError.value = `提取失败：${err.message}`
  } finally {
    planLoading.value = false
  }
}

async function handleSavePlan() {
  if (planSaving.value) return
  if (!planId.value) {
    planError.value = '请先生成教学计划'
    return
  }
  const token = getTeacherToken()
  if (!token) {
    planError.value = '请先登录教师账号'
    return
  }

  planSaving.value = true
  planError.value = ''
  planStatus.value = ''
  try {
    await persistPlanDraft({
      silent: false,
      throwOnError: true,
    })
    planStatus.value = '教学计划已保存，后续对话与生成会参考此版本'
  } catch (err) {
    planError.value = `保存失败：${err.message}`
  } finally {
    planSaving.value = false
  }
}

async function handleAutoLinkSources() {
  if (planLinkLoading.value) return
  if (!planId.value) {
    planLinkError.value = '请先生成教学计划'
    return
  }
  const token = getTeacherToken()
  if (!token) {
    planLinkError.value = '请先登录教师账号'
    return
  }

  planLinkError.value = ''
  try {
    const links = await runAutoLinkSources({ topK: 2 })
    planStatus.value = `已自动匹配 ${links.length} 条引用（可继续手动修正）`
  } catch {
    // 错误已由 runAutoLinkSources 写入
  }
}

async function handleLinkSource() {
  if (planLinkLoading.value) return
  if (!planId.value) {
    planLinkError.value = '请先生成教学计划'
    return
  }
  const token = getTeacherToken()
  if (!token) {
    planLinkError.value = '请先登录教师账号'
    return
  }

  const docId = Number(linkDocId.value)
  if (!Number.isInteger(docId) || docId <= 0) {
    planLinkError.value = '请先选择资料文档'
    return
  }

  let chunkId = null
  const rawChunk = String(linkChunkId.value || '').trim()
  if (rawChunk) {
    const parsedChunk = Number(rawChunk)
    if (!Number.isInteger(parsedChunk) || parsedChunk <= 0) {
      planLinkError.value = '片段 ID 必须是正整数'
      return
    }
    chunkId = parsedChunk
  }

  planLinkLoading.value = true
  planLinkError.value = ''
  try {
    const outlineItem = kbOutline.value.find((item) => Number(item.chunk_id) === chunkId)
    const locatorValue = linkLocator.value.trim() || outlineItem?.locator || null

    const response = await fetch(PLAN_LINK_SOURCES_API_URL, {
      method: 'POST',
      headers: getTeacherAuthHeaders(true),
      body: JSON.stringify({
        plan_id: Number(planId.value),
        links: [
          {
            key_concept_id: linkConceptId.value.trim() || null,
            doc_id: docId,
            chunk_id: chunkId,
            locator: locatorValue,
            note: linkNote.value.trim() || null,
          },
        ],
      }),
    })
    const data = await response.json().catch(() => ({}))
    if (!response.ok) {
      throw new Error(data.detail || `HTTP ${response.status}`)
    }

    planSourceLinks.value = Array.isArray(data.source_links) ? data.source_links : []
    linkDocId.value = ''
    linkChunkId.value = ''
    linkConceptId.value = ''
    linkLocator.value = ''
    linkNote.value = ''
    kbOutline.value = []
    planStatus.value = '已手动绑定引用来源'
  } catch (err) {
    planLinkError.value = `来源链接失败：${err.message}`
  } finally {
    planLinkLoading.value = false
  }
}

// 切换对话时清空或加载历史消息
watch(
  () => [props.conversationId, props.historyMessages],
  () => {
    if (props.historyMessages && props.historyMessages.length) {
      messages.value = [...props.historyMessages]
    } else {
      messages.value = []
    }
  },
  { immediate: true },
)

watch(
  () => props.conversationId,
  (next, prev) => {
    if (next !== prev) {
      resetPlanState()
      resetPptArtifactState()
      resetStudyPackResultView()
      showStudyPackModal.value = false
      clearStudyPackFile()
      showPlanModal.value = false
      showPptEditModal.value = false
      webResourceSuggestion.value = null
      webResourceError.value = ''
      webResourceExpanded.value = false
      clearWebResourcePoll()
      if (next) {
        preloadLatestPlan(next)
        loadWebResourceSuggestion({ silent: true }).catch(() => {})
      }
    }
  },
)

watch(
  () => linkDocId.value,
  (value, oldValue) => {
    if (value !== oldValue) {
      linkChunkId.value = ''
    }
    const docId = Number(value)
    if (!Number.isInteger(docId) || docId <= 0) {
      kbOutline.value = []
      kbOutlineError.value = ''
      return
    }
    loadKbOutline(docId).catch(() => {})
  },
)

watch(
  () => studyPackSourceMode.value,
  (next, prev) => {
    if (next === prev) return
    studyPackError.value = ''
    if (next === 'file') {
      studyPackCustomText.value = ''
      return
    }
    clearStudyPackFile()
  },
)

onMounted(() => {
  window.addEventListener('mousemove', handleInputResize)
  window.addEventListener('mouseup', stopInputResize)
  window.addEventListener('keydown', handleDeveloperModeHotkey)
  initializeDeveloperMode()
  if (props.conversationId) {
    preloadLatestPlan(props.conversationId)
    loadWebResourceSuggestion({ silent: true }).catch(() => {})
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('mousemove', handleInputResize)
  window.removeEventListener('mouseup', stopInputResize)
  window.removeEventListener('keydown', handleDeveloperModeHotkey)
  stopVoicePreview()
  clearWebResourcePoll()
  if (chatProgressTimer) {
    clearInterval(chatProgressTimer)
    chatProgressTimer = null
  }
  if (imageProgressTimer) {
    clearInterval(imageProgressTimer)
    imageProgressTimer = null
  }
  if (pptProgressTimer) {
    clearInterval(pptProgressTimer)
    pptProgressTimer = null
  }
  if (videoProgressTimer) {
    clearInterval(videoProgressTimer)
    videoProgressTimer = null
  }
  if (pptStageTimer) {
    clearTimeout(pptStageTimer)
    pptStageTimer = null
  }
  revokePptPreviewUrls()
})

function getProgressEstimate(key) {
  const stored = Number(localStorage.getItem(`progress_estimate_${key}`))
  if (Number.isFinite(stored) && stored > 1000) {
    return stored
  }
  return PROGRESS_DEFAULTS[key] || 12000
}

function saveProgressEstimate(key, durationMs) {
  const fallback = PROGRESS_DEFAULTS[key] || 12000
  const previous = getProgressEstimate(key)
  const next = Math.round(previous * 0.65 + durationMs * 0.35)
  const clamped = Math.min(120000, Math.max(4000, next || fallback))
  localStorage.setItem(`progress_estimate_${key}`, String(clamped))
}

function startTimedProgress(key, progressRef) {
  const expected = getProgressEstimate(key)
  const startedAt = Date.now()
  progressRef.value = 4
  const timer = window.setInterval(() => {
    const elapsed = Date.now() - startedAt
    const ratio = Math.min(elapsed / expected, 1)
    const next = 4 + ratio * 94
    progressRef.value = Math.min(98, Math.max(progressRef.value, next))
  }, 200)
  return { startedAt, timer }
}

function getVoiceSampleText() {
  if (videoDraftPayload.value?.script_text) {
    return String(videoDraftPayload.value.script_text)
  }
  if (videoDraftPayload.value?.scriptText) {
    return String(videoDraftPayload.value.scriptText)
  }
  return selectedAvatar.value?.voice || '同学们好，我是教策云枢老师，很高兴和大家一起学习。'
}

async function previewVoice() {
  if (videoPreviewLoading.value) return
  voicePreviewError.value = ''
  const draftJson = videoDraftPayload.value?.microcourse_json || videoDraftPayload.value?.microcourseJson || null
  const fallbackText = getVoiceSampleText().trim()
  if (!draftJson && !fallbackText) {
    voicePreviewError.value = '请先生成讲稿分镜后再试听'
    return
  }

  videoPreviewLoading.value = true
  try {
    const response = await fetch(VIDEO_PREVIEW_AUDIO_API_URL, {
      method: 'POST',
      headers: getTeacherAuthHeaders(true),
      body: JSON.stringify({
        text: fallbackText,
        voice_id: selectedAvatar.value?.voiceId || null,
        microcourse_json: draftJson || undefined,
      }),
    })
    const data = await response.json().catch(() => ({}))
    if (!response.ok) {
      throw new Error(data.detail || `HTTP ${response.status}`)
    }

    const resolvedAudioUrl = resolveApiMediaUrl(data.audio_url)
    if (!resolvedAudioUrl) {
      throw new Error('未返回可播放音频地址')
    }

    stopVoicePreview()
    videoPreviewAudioUrl.value = resolvedAudioUrl
    const audio = new Audio(resolvedAudioUrl)
    voicePreviewAudio = audio
    isVoicePreviewing.value = true
    audio.onended = () => {
      isVoicePreviewing.value = false
    }
    audio.onerror = () => {
      isVoicePreviewing.value = false
      voicePreviewError.value = '试听音频播放失败'
    }
    await audio.play()
  } catch (err) {
    voicePreviewError.value = `试听失败：${err.message}`
    isVoicePreviewing.value = false
  } finally {
    videoPreviewLoading.value = false
  }
}

function stopVoicePreview() {
  if (voicePreviewAudio) {
    try {
      voicePreviewAudio.pause()
      voicePreviewAudio.currentTime = 0
    } catch {
      // ignore
    }
    voicePreviewAudio = null
  }
  isVoicePreviewing.value = false
}

function scrollToBottom() {
  nextTick(() => {
    if (chatContainer.value) {
      chatContainer.value.scrollTop = chatContainer.value.scrollHeight
    }
  })
}

function startInputResize(event) {
  isResizingInput.value = true
  inputStartY = event.clientY
  inputStartHeight = inputHeight.value
  document.body.style.cursor = 'row-resize'
  event.preventDefault()
}

function handleInputResize(event) {
  if (!isResizingInput.value) return
  const delta = inputStartY - event.clientY
  const nextHeight = Math.min(240, Math.max(110, inputStartHeight + delta))
  inputHeight.value = nextHeight
}

function stopInputResize() {
  if (!isResizingInput.value) return
  isResizingInput.value = false
  document.body.style.cursor = ''
}

function startChatProgress() {
  if (chatProgressTimer) {
    clearInterval(chatProgressTimer)
  }
  const expected = getProgressEstimate('chat')
  chatProgressStartedAt = Date.now()
  chatProgressHasOutput = false
  chatProgress.value = 4
  chatProgressTimer = window.setInterval(() => {
    const elapsed = Date.now() - chatProgressStartedAt
    const ratio = Math.min(elapsed / expected, 1)
    const ceiling = chatProgressHasOutput ? 94 : 10
    const target = chatProgressHasOutput
      ? 22 + ratio * 70
      : 4 + ratio * 6
    chatProgress.value = Math.min(ceiling, Math.max(chatProgress.value, target))
  }, 160)
}

function updateChatProgressForOutput(charCount = 1, displayedChars = 0, totalChars = 0) {
  chatProgressHasOutput = true
  if (chatProgress.value < 20) {
    chatProgress.value = 20
  }

  const total = Number(totalChars)
  const displayed = Number(displayedChars)
  if (Number.isFinite(total) && total > 0 && Number.isFinite(displayed) && displayed >= 0) {
    const ratio = Math.min(displayed / total, 1)
    chatProgress.value = Math.min(96, Math.max(chatProgress.value, 20 + ratio * 76))
    return
  }

  const count = Math.max(1, Number(charCount) || 1)
  const step = Math.min(3.8, Math.max(0.7, count * 0.14))
  chatProgress.value = Math.min(96, Math.max(chatProgress.value, chatProgress.value + step))
}

function stopChatProgress() {
  if (chatProgressTimer) {
    clearInterval(chatProgressTimer)
    chatProgressTimer = null
  }
  if (chatProgressStartedAt) {
    saveProgressEstimate('chat', Date.now() - chatProgressStartedAt)
    chatProgressStartedAt = 0
  }
  chatProgressHasOutput = false
  if (chatProgress.value > 0) {
    chatProgress.value = 100
    window.setTimeout(() => {
      chatProgress.value = 0
    }, 600)
  }
}

function startImageProgress() {
  if (imageProgressTimer) {
    clearInterval(imageProgressTimer)
  }
  const { startedAt, timer } = startTimedProgress('image', imageProgress)
  imageProgressStartedAt = startedAt
  imageProgressTimer = timer
}

function stopImageProgress() {
  if (imageProgressTimer) {
    clearInterval(imageProgressTimer)
    imageProgressTimer = null
  }
  if (imageProgressStartedAt) {
    saveProgressEstimate('image', Date.now() - imageProgressStartedAt)
    imageProgressStartedAt = 0
  }
  if (imageProgress.value > 0) {
    imageProgress.value = 100
    window.setTimeout(() => {
      imageProgress.value = 0
    }, 600)
  }
}

function startPptProgress() {
  if (pptProgressTimer) {
    clearInterval(pptProgressTimer)
  }
  const { startedAt, timer } = startTimedProgress('ppt', pptProgress)
  pptProgressStartedAt = startedAt
  pptProgressTimer = timer
}

function stopPptProgress() {
  if (pptProgressTimer) {
    clearInterval(pptProgressTimer)
    pptProgressTimer = null
  }
  if (pptProgressStartedAt) {
    saveProgressEstimate('ppt', Date.now() - pptProgressStartedAt)
    pptProgressStartedAt = 0
  }
  if (pptProgress.value > 0) {
    pptProgress.value = 100
    window.setTimeout(() => {
      pptProgress.value = 0
    }, 600)
  }
}

function startVideoProgress() {
  if (videoProgressTimer) {
    clearInterval(videoProgressTimer)
  }
  const { startedAt, timer } = startTimedProgress('video', videoProgress)
  videoProgressStartedAt = startedAt
  videoProgressTimer = timer
}

function stopVideoProgress() {
  if (videoProgressTimer) {
    clearInterval(videoProgressTimer)
    videoProgressTimer = null
  }
  if (videoProgressStartedAt) {
    saveProgressEstimate('video', Date.now() - videoProgressStartedAt)
    videoProgressStartedAt = 0
  }
  if (videoProgress.value > 0) {
    videoProgress.value = 100
    window.setTimeout(() => {
      videoProgress.value = 0
    }, 600)
  }
}

async function handleGeneratePpt() {
  if (pptLoading.value) return
  const topicText = pptTopic.value.trim()
  const token = getTeacherToken()
  const canUsePlanPipeline = usePlanForPpt.value && !!token && !!props.conversationId
  const enablePlan = canUsePlanPipeline && !!planId.value
  if (!topicText && !canUsePlanPipeline) {
    pptError.value = '请先输入课件主题'
    return
  }

  pptLoading.value = true
  pptError.value = ''
  pptStatus.value = canUsePlanPipeline ? '正在准备教学计划...' : '正在规划大纲...'
  startPptProgress()
  if (pptStageTimer) {
    clearTimeout(pptStageTimer)
  }
  pptStageTimer = window.setTimeout(() => {
    if (pptLoading.value) {
      pptStatus.value = '正在排版设计...'
    }
  }, 1200)

  try {
    let finalPlanId = enablePlan ? Number(planId.value) : null
    if (canUsePlanPipeline) {
      finalPlanId = await ensurePlanForGeneration({
        autoLink: true,
        topK: 2,
        silentAutoLink: true,
        failOnMissingCancel: true,
      })
      pptStatus.value = '正在规划大纲...'
    }

    const requestBody = {
      topic: topicText || (canUsePlanPipeline ? (planTitle.value || '教学课件') : ''),
      conversation_key: props.conversationId ? String(props.conversationId) : undefined,
      plan_id: finalPlanId ? Number(finalPlanId) : undefined,
      theme_name: getPptThemeName(),
    }

    const task = await createGenerationTask('generate_ppt', topicText || planTitle.value || 'PPT 课件生成', requestBody)
    appendTaskSubmittedMessage(task, 'PPT 课件')
    showPptModal.value = false
  } catch (err) {
    if (String(err?.message || '').includes('已取消补齐缺失信息')) {
      pptError.value = '已取消生成：请先补齐关键信息后再试'
    } else {
      pptError.value = `生成失败：${err.message}`
    }
  } finally {
    pptLoading.value = false
    pptStatus.value = ''
    stopPptProgress()
    if (pptStageTimer) {
      clearTimeout(pptStageTimer)
      pptStageTimer = null
    }
  }
}

function renderMarkdown(text) {
  return md.render(normalizeMathText(text || ''))
}

function getMicrocourseScenes(message) {
  if (Array.isArray(message?.scenePreviews) && message.scenePreviews.length) {
    return message.scenePreviews
  }
  if (Array.isArray(message?.microcourseJson?.scenes) && message.microcourseJson.scenes.length) {
    return message.microcourseJson.scenes
  }
  return []
}

function getMicrocourseLessonTitle(message) {
  const lessonTitle = String(message?.microcourseJson?.meta?.lesson_title || '').trim()
  if (lessonTitle) {
    return lessonTitle
  }
  const rows = getBoardContent(message)
  return rows[0] || '数字人微课'
}

function getBoardContent(message) {
  const scenes = getMicrocourseScenes(message)
  if (scenes.length) {
    const firstScene = scenes[0] || {}
    const rows = []
    if (firstScene.board_title) {
      rows.push(firstScene.board_title)
    }
    if (Array.isArray(firstScene.board_bullets)) {
      rows.push(...firstScene.board_bullets)
    }
    const cleaned = rows.map((item) => String(item || '').trim()).filter(Boolean)
    if (cleaned.length) {
      return cleaned.slice(0, 6)
    }
  }
  if (Array.isArray(message?.boardContent) && message.boardContent.length) {
    return message.boardContent
  }
  const raw = message?.text?.trim()
  if (!raw) {
    return ['今日课题：未提供']
  }
  const first = raw.split(/[。！？\n]/).find((segment) => segment && segment.trim()) || raw
  return [`今日课题：${first.slice(0, 16)}`]
}

function escapeHtml(value) {
  return String(value || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function escapeInlineJson(value) {
  return String(value || '')
    .replace(/</g, '\\u003c')
    .replace(/>/g, '\\u003e')
    .replace(/&/g, '\\u0026')
}

function buildMicroLessonHtml(message) {
  const scenes = getMicrocourseScenes(message)
  const boardItems = getBoardContent(message)
  const safeTitle = escapeHtml(getMicrocourseLessonTitle(message))
  const videoUrl = escapeHtml(message?.videoUrl)
  const scriptText = escapeHtml(message?.text || message?.scriptText || '')
  const safeScriptText = scriptText || '（暂无字幕内容）'

  const normalizedScenes = scenes
    .map((scene, index) => {
      const title = String(scene?.board_title || scene?.title || `场景 ${index + 1}`).trim()
      const subtitle = String(scene?.subtitle || '').trim()
      const bullets = Array.isArray(scene?.board_bullets)
        ? scene.board_bullets
            .map((item) => String(item || '').trim())
            .filter(Boolean)
            .slice(0, 8)
        : []
      const rawDuration = Number(scene?.duration_sec)
      const durationSec = Number.isFinite(rawDuration) && rawDuration > 0 ? Math.round(rawDuration * 100) / 100 : 0
      return {
        title: title || `场景 ${index + 1}`,
        subtitle,
        bullets,
        duration_sec: durationSec,
      }
    })
    .filter((scene) => scene.title || scene.subtitle || scene.bullets.length)

  if (!normalizedScenes.length) {
    normalizedScenes.push({
      title: boardItems[0] || '今日重点',
      subtitle: '',
      bullets: boardItems.slice(1),
      duration_sec: 0,
    })
  }

  if (!normalizedScenes[0].bullets.length && boardItems.length > 1) {
    normalizedScenes[0].bullets = boardItems.slice(1).map((item) => String(item || '').trim()).filter(Boolean)
  }

  const firstScene = normalizedScenes[0] || { title: '今日重点', subtitle: '', bullets: [] }
  const firstSceneTitle = escapeHtml(firstScene.title || '今日重点')
  const firstSceneSubtitle = escapeHtml(firstScene.subtitle || '')
  const firstBulletsHtml = (Array.isArray(firstScene.bullets) ? firstScene.bullets : [])
    .map((item) => `<li>${escapeHtml(item)}</li>`)
    .join('')
  const sceneRailHtml = normalizedScenes
    .map((scene, index) => {
      const previewBullets = (Array.isArray(scene.bullets) ? scene.bullets : [])
        .slice(0, 2)
        .map((item) => `<li>${escapeHtml(item)}</li>`)
        .join('')
      const durationText = scene.duration_sec > 0 ? `${scene.duration_sec}s` : '--'
      return `<div class="scene-chip ${index === 0 ? 'is-active' : ''}" data-scene-card-index="${index}">
        <div class="scene-chip__head">
          <span>${escapeHtml(scene.title || `场景 ${index + 1}`)}</span>
          <span>${escapeHtml(durationText)}</span>
        </div>
        ${scene.subtitle ? `<p class="scene-chip__subtitle">${escapeHtml(scene.subtitle)}</p>` : ''}
        ${previewBullets ? `<ul class="scene-chip__bullets">${previewBullets}</ul>` : ''}
      </div>`
    })
    .join('')
  const scenePayload = escapeInlineJson(JSON.stringify(normalizedScenes))

  return `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>${safeTitle}</title>
  <style>
    :root {
      --page-bg: radial-gradient(circle at 15% 8%, #103057 0%, #071a37 42%, #020917 100%);
      --shell-bg: rgba(5, 18, 40, 0.72);
      --board-bg: linear-gradient(160deg, #0a2b52 0%, #041834 58%, #020f24 100%);
      --text-strong: #f4f8ff;
      --text-normal: #dbeafe;
      --text-soft: #8fb1d9;
      --primary: #53d3ff;
      --primary-strong: #17b7f4;
      --line-soft: rgba(147, 197, 253, 0.24);
    }

    * { box-sizing: border-box; }
    body {
      margin: 0;
      min-height: 100vh;
      font-family: "HarmonyOS Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif;
      background: var(--page-bg);
      color: var(--text-strong);
    }

    .page {
      max-width: 1220px;
      margin: 0 auto;
      padding: 22px 18px 40px;
    }

    .hero {
      margin-bottom: 14px;
    }

    .hero h1 {
      margin: 0;
      font-size: clamp(24px, 3vw, 32px);
      line-height: 1.2;
      letter-spacing: 0.02em;
    }

    .hero p {
      margin: 8px 0 0;
      color: var(--text-soft);
      font-size: 14px;
    }

    .stage-shell {
      border-radius: 20px;
      background: var(--shell-bg);
      border: 1px solid var(--line-soft);
      box-shadow: 0 20px 48px rgba(1, 9, 24, 0.45);
      padding: 14px;
      backdrop-filter: blur(8px);
    }

    .lesson-stage {
      position: relative;
      border-radius: 16px;
      overflow: hidden;
      aspect-ratio: 16 / 9;
      min-height: 460px;
      background: var(--board-bg);
    }

    .lesson-stage::before {
      content: "";
      position: absolute;
      inset: 0;
      background:
        linear-gradient(rgba(148, 197, 255, 0.06) 1px, transparent 1px),
        linear-gradient(90deg, rgba(148, 197, 255, 0.05) 1px, transparent 1px);
      background-size: 28px 28px;
      opacity: 0.35;
      pointer-events: none;
      z-index: 0;
    }

    .board-layer {
      position: absolute;
      inset: 0;
      z-index: 1;
      padding: clamp(18px, 2.6vw, 28px) clamp(180px, 22vw, 300px) 22px clamp(18px, 2.8vw, 34px);
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    .board-layer.is-entering {
      animation: boardFade 220ms ease;
    }

    @keyframes boardFade {
      from {
        opacity: 0.65;
        transform: translateY(4px);
      }
      to {
        opacity: 1;
        transform: translateY(0);
      }
    }

    .board-badge {
      display: inline-flex;
      align-items: center;
      width: fit-content;
      padding: 5px 11px;
      border-radius: 999px;
      border: 1px solid rgba(83, 211, 255, 0.4);
      background: rgba(10, 21, 40, 0.5);
      color: #bae6fd;
      font-size: 12px;
      letter-spacing: 0.08em;
    }

    #sceneTitle {
      margin: 0;
      font-size: clamp(24px, 3vw, 36px);
      line-height: 1.25;
      max-width: 18em;
      text-shadow: 0 8px 18px rgba(2, 6, 23, 0.35);
    }

    #sceneSubtitle {
      margin: 0;
      max-width: 40em;
      color: #bfdbfe;
      font-size: 16px;
      line-height: 1.55;
    }

    #sceneSubtitle.is-hidden {
      display: none;
    }

    .board-list {
      margin: 0;
      padding: 0;
      list-style: none;
      display: grid;
      gap: 10px;
      max-width: 38em;
      color: var(--text-normal);
      font-size: clamp(16px, 2.1vw, 27px);
      line-height: 1.45;
    }

    .board-list li {
      display: flex;
      align-items: flex-start;
      gap: 10px;
    }

    .board-list li::before {
      content: "▸";
      color: var(--primary);
      flex-shrink: 0;
      font-size: 0.92em;
      margin-top: 0.12em;
    }

    .scene-index {
      margin-top: auto;
      width: fit-content;
      padding: 5px 12px;
      border-radius: 999px;
      border: 1px solid rgba(147, 197, 253, 0.28);
      background: rgba(2, 13, 30, 0.58);
      color: #bfdbfe;
      font-size: 12px;
      letter-spacing: 0.06em;
    }

    .avatar-pip {
      position: absolute;
      right: clamp(12px, 2.2vw, 24px);
      bottom: clamp(12px, 2.2vw, 24px);
      width: clamp(160px, 18vw, 238px);
      aspect-ratio: 10 / 14;
      border-radius: 20px;
      overflow: hidden;
      border: 1px solid rgba(186, 230, 253, 0.55);
      background: #020617;
      box-shadow: 0 22px 38px rgba(2, 6, 23, 0.6), 0 0 0 1px rgba(103, 232, 249, 0.18);
      z-index: 3;
    }

    .avatar-pip::after {
      content: "";
      position: absolute;
      inset: 0;
      background: linear-gradient(to top, rgba(2, 6, 23, 0.48), transparent 45%);
      pointer-events: none;
      z-index: 2;
    }

    .avatar-pip__halo {
      position: absolute;
      inset: -34%;
      border-radius: 50%;
      background: radial-gradient(circle, rgba(56, 189, 248, 0.4) 0%, rgba(56, 189, 248, 0) 68%);
      filter: blur(18px);
      z-index: 0;
      pointer-events: none;
    }

    .avatar-pip video {
      position: relative;
      z-index: 1;
      width: 100%;
      height: 100%;
      object-fit: cover;
      object-position: center top;
      transform: scale(1.02);
      display: block;
      background: #020617;
    }

    .transport {
      margin-top: 12px;
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      align-items: center;
    }

    .btn {
      border: 0;
      border-radius: 10px;
      padding: 9px 14px;
      font-size: 14px;
      font-weight: 600;
      cursor: pointer;
      text-decoration: none;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      transition: transform 120ms ease, box-shadow 160ms ease, background 160ms ease;
      min-height: 38px;
      white-space: nowrap;
    }

    .btn:hover {
      transform: translateY(-1px);
    }

    .btn.primary {
      color: #03233a;
      background: linear-gradient(135deg, #7dd3fc, #38bdf8);
      box-shadow: 0 10px 20px rgba(56, 189, 248, 0.3);
    }

    .btn.primary.is-playing {
      background: linear-gradient(135deg, #fca5a5, #fb7185);
      color: #2f0a18;
      box-shadow: 0 10px 20px rgba(251, 113, 133, 0.35);
    }

    .btn.secondary {
      color: #e2e8f0;
      background: rgba(15, 23, 42, 0.75);
      border: 1px solid rgba(148, 163, 184, 0.32);
    }

    .btn.secondary.is-expanded {
      border-color: rgba(125, 211, 252, 0.58);
      color: #d6f2ff;
      background: rgba(8, 35, 66, 0.78);
    }

    .btn.ghost {
      color: #bfdbfe;
      background: rgba(15, 23, 42, 0.35);
      border: 1px solid rgba(147, 197, 253, 0.24);
    }

    .progress-row {
      margin-top: 10px;
      display: flex;
      align-items: center;
      gap: 10px;
      color: var(--text-soft);
      font-size: 13px;
    }

    .timeline {
      width: 100%;
      accent-color: var(--primary-strong);
    }

    .scene-rail {
      margin-top: 12px;
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
      gap: 8px;
    }

    .scene-chip {
      border-radius: 12px;
      border: 1px solid rgba(148, 163, 184, 0.24);
      background: rgba(10, 19, 41, 0.75);
      color: #dbeafe;
      padding: 10px;
      text-align: left;
      cursor: pointer;
      transition: border-color 140ms ease, transform 140ms ease, box-shadow 140ms ease;
      min-height: 86px;
    }

    .scene-chip:hover {
      transform: translateY(-1px);
      border-color: rgba(125, 211, 252, 0.62);
    }

    .scene-chip.is-active {
      border-color: rgba(83, 211, 255, 0.8);
      box-shadow: 0 10px 18px rgba(2, 132, 199, 0.25);
      background: rgba(8, 30, 58, 0.86);
    }

    .scene-chip__head {
      display: flex;
      justify-content: space-between;
      gap: 8px;
      font-size: 12px;
      font-weight: 700;
      color: #e2e8f0;
    }

    .scene-chip__subtitle {
      margin: 6px 0 0;
      color: #c7d2fe;
      font-size: 12px;
      line-height: 1.45;
    }

    .scene-chip__bullets {
      margin: 6px 0 0;
      padding-left: 1rem;
      display: grid;
      gap: 3px;
      color: #bfdbfe;
      font-size: 12px;
      line-height: 1.35;
    }

    .script {
      margin-top: 14px;
      border-radius: 14px;
      border: 1px solid rgba(147, 197, 253, 0.24);
      background: rgba(2, 11, 27, 0.6);
      color: #d1e5ff;
      padding: 12px 14px;
      font-size: 13px;
      line-height: 1.75;
    }

    .caption-panel {
      display: none;
      margin-top: 12px;
      border: 1px solid rgba(147, 197, 253, 0.24);
      border-radius: 14px;
      background: rgba(2, 11, 27, 0.45);
      padding: 10px;
    }

    .caption-panel.is-open {
      display: block;
    }

    .caption-panel .scene-rail {
      margin-top: 0;
    }

    .caption-panel .script {
      margin-top: 10px;
      margin-bottom: 0;
    }

    @media (max-width: 860px) {
      .lesson-stage {
        min-height: 420px;
      }

      .board-layer {
        padding-right: 170px;
      }

      .board-list {
        font-size: 15px;
      }
    }

    @media (max-width: 720px) {
      .page {
        padding: 16px 12px 30px;
      }

      .lesson-stage {
        min-height: 400px;
      }

      .board-layer {
        padding: 14px 14px 166px 14px;
      }

      .avatar-pip {
        width: 126px;
        right: 12px;
        bottom: 12px;
        border-radius: 14px;
      }

      .transport {
        gap: 6px;
      }

      .btn {
        flex: 1 1 calc(50% - 6px);
      }
    }

    @media (max-width: 1024px) and (orientation: landscape) {
      .page {
        max-width: none;
        width: 100%;
        padding: 10px 10px 14px;
      }

      .hero {
        margin-bottom: 8px;
      }

      .hero h1 {
        font-size: clamp(18px, 2.4vw, 24px);
      }

      .stage-shell {
        display: grid;
        grid-template-columns: minmax(0, 1fr) minmax(210px, 28vw);
        grid-template-areas:
          "stage transport"
          "stage progress"
          "caption caption";
        align-items: start;
        gap: 10px;
        padding: 10px;
      }

      .lesson-stage {
        grid-area: stage;
        min-height: clamp(260px, 64vh, 520px);
      }

      .board-layer {
        padding: 12px clamp(120px, 18vw, 176px) 14px 12px;
        gap: 8px;
      }

      .board-list {
        gap: 6px;
        font-size: clamp(14px, 1.8vw, 20px);
      }

      .avatar-pip {
        width: clamp(96px, 15vw, 128px);
        right: 10px;
        bottom: 10px;
        border-radius: 12px;
      }

      .transport {
        grid-area: transport;
        margin-top: 0;
        display: grid;
        grid-template-columns: 1fr;
        align-content: start;
        gap: 8px;
      }

      .btn {
        width: 100%;
        min-height: 36px;
        padding: 8px 10px;
        font-size: 13px;
      }

      .progress-row {
        grid-area: progress;
        margin-top: 0;
        flex-direction: column;
        align-items: stretch;
        gap: 6px;
      }

      #timeText {
        text-align: right;
        font-size: 12px;
      }

      .caption-panel {
        grid-area: caption;
        margin-top: 0;
        max-height: 34vh;
        overflow-y: auto;
      }

      .scene-rail {
        grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
      }
    }
  </style>
</head>
<body>
  <div class="page">
    <div class="hero">
      <h1>${safeTitle}</h1>
    </div>
    <div class="stage-shell">
      <div class="lesson-stage">
        <div class="board-layer">
          <div class="board-badge">课堂黑板</div>
          <h2 id="sceneTitle">${firstSceneTitle}</h2>
          <p id="sceneSubtitle" class="${firstSceneSubtitle ? '' : 'is-hidden'}">${firstSceneSubtitle}</p>
          <ul id="sceneBullets" class="board-list">${firstBulletsHtml || '<li>本页暂无板书要点，可按左右方向键切换章节。</li>'}</ul>
          <div id="sceneIndexText" class="scene-index">1 / ${normalizedScenes.length}</div>
        </div>
        <div class="avatar-pip">
          <div class="avatar-pip__halo"></div>
          <video id="avatarVideo" src="${videoUrl}" playsinline preload="metadata"></video>
        </div>
      </div>

      <div class="transport">
        <button id="playToggle" class="btn primary" type="button">开始播放</button>
        <button id="captionToggle" class="btn secondary" type="button">展开字幕</button>
        <a class="btn ghost" href="${videoUrl}" download>下载视频</a>
      </div>

      <div class="progress-row">
        <input id="timeline" class="timeline" type="range" value="0" min="0" max="100" step="0.01" />
        <span id="timeText">00:00 / 00:00</span>
      </div>

      <div id="captionPanel" class="caption-panel">
        ${sceneRailHtml ? `<div class="scene-rail">${sceneRailHtml}</div>` : ''}
        <div class="script">台词：${safeScriptText}</div>
      </div>
    </div>
  </div>

  <script id="micro-scene-data" type="application/json">${scenePayload}<\/script>
  <script>
    (function () {
      var sceneDataNode = document.getElementById('micro-scene-data')
      var scenes = []
      try {
        scenes = JSON.parse((sceneDataNode && sceneDataNode.textContent) || '[]')
      } catch (err) {
        scenes = []
      }

      if (!Array.isArray(scenes) || !scenes.length) {
        scenes = [{ title: '今日重点', subtitle: '', bullets: [], duration_sec: 0 }]
      }

      var video = document.getElementById('avatarVideo')
      var playToggle = document.getElementById('playToggle')
      var captionToggleBtn = document.getElementById('captionToggle')
      var captionPanel = document.getElementById('captionPanel')
      var timeline = document.getElementById('timeline')
      var timeText = document.getElementById('timeText')
      var sceneTitle = document.getElementById('sceneTitle')
      var sceneSubtitle = document.getElementById('sceneSubtitle')
      var sceneBullets = document.getElementById('sceneBullets')
      var sceneIndexText = document.getElementById('sceneIndexText')
      var boardLayer = document.querySelector('.board-layer')
      var sceneCards = Array.prototype.slice.call(document.querySelectorAll('[data-scene-card-index]'))

      var currentSceneIndex = 0

      function escapeText(value) {
        return String(value || '')
          .replace(/&/g, '&amp;')
          .replace(/</g, '&lt;')
          .replace(/>/g, '&gt;')
          .replace(/"/g, '&quot;')
          .replace(/'/g, '&#39;')
      }

      function clampSceneIndex(index) {
        if (index < 0) return 0
        if (index >= scenes.length) return scenes.length - 1
        return index
      }

      function formatTime(seconds) {
        var sec = Number(seconds)
        if (!Number.isFinite(sec) || sec < 0) {
          sec = 0
        }
        var minutes = Math.floor(sec / 60)
        var remain = Math.floor(sec % 60)
        return String(minutes).padStart(2, '0') + ':' + String(remain).padStart(2, '0')
      }

      function refreshPlayButton() {
        if (!video || !playToggle) return
        var playing = !video.paused && !video.ended
        playToggle.textContent = playing ? '暂停播放' : '开始播放'
        playToggle.classList.toggle('is-playing', playing)
      }

      function refreshCaptionToggle() {
        if (!captionToggleBtn || !captionPanel) return
        var expanded = captionPanel.classList.contains('is-open')
        captionToggleBtn.textContent = expanded ? '收起字幕' : '展开字幕'
        captionToggleBtn.classList.toggle('is-expanded', expanded)
      }

      function refreshProgress() {
        if (!video || !timeline || !timeText) return
        var total = Number(video.duration)
        var current = Number(video.currentTime)
        timeline.max = Number.isFinite(total) && total > 0 ? String(total) : '100'
        timeline.value = Number.isFinite(current) && current >= 0 ? String(current) : '0'
        timeText.textContent = formatTime(current) + ' / ' + formatTime(total)
      }

      function renderScene(index) {
        currentSceneIndex = clampSceneIndex(index)
        var scene = scenes[currentSceneIndex] || {}
        var title = String(scene.title || ('场景 ' + (currentSceneIndex + 1))).trim()
        var subtitle = String(scene.subtitle || '').trim()
        var bullets = Array.isArray(scene.bullets) ? scene.bullets.filter(Boolean) : []

        if (sceneTitle) {
          sceneTitle.textContent = title || '今日重点'
        }
        if (sceneSubtitle) {
          sceneSubtitle.textContent = subtitle
          sceneSubtitle.classList.toggle('is-hidden', !subtitle)
        }
        if (sceneBullets) {
          sceneBullets.innerHTML = bullets.length
            ? bullets.map(function (item) { return '<li>' + escapeText(item) + '</li>' }).join('')
            : '<li>本页暂无板书要点，可按左右方向键切换章节。</li>'
        }
        if (sceneIndexText) {
          sceneIndexText.textContent = (currentSceneIndex + 1) + ' / ' + scenes.length
        }

        sceneCards.forEach(function (card, cardIndex) {
          card.classList.toggle('is-active', cardIndex === currentSceneIndex)
        })

        if (boardLayer) {
          boardLayer.classList.remove('is-entering')
          void boardLayer.offsetWidth
          boardLayer.classList.add('is-entering')
        }
      }

      function togglePlayState() {
        if (!video) return
        if (video.paused) {
          video.play().catch(function () {})
        } else {
          video.pause()
        }
      }

      renderScene(0)
      refreshProgress()
      refreshPlayButton()
      refreshCaptionToggle()

      if (video) {
        video.addEventListener('loadedmetadata', refreshProgress)
        video.addEventListener('timeupdate', function () {
          refreshProgress()
        })
        video.addEventListener('play', refreshPlayButton)
        video.addEventListener('pause', refreshPlayButton)
        video.addEventListener('ended', refreshPlayButton)
      }

      if (playToggle) {
        playToggle.addEventListener('click', function () {
          togglePlayState()
        })
      }

      if (captionToggleBtn && captionPanel) {
        captionToggleBtn.addEventListener('click', function () {
          captionPanel.classList.toggle('is-open')
          refreshCaptionToggle()
        })
      }

      if (timeline && video) {
        timeline.addEventListener('input', function () {
          var point = Number(timeline.value)
          if (Number.isFinite(point) && point >= 0) {
            video.currentTime = point
            refreshProgress()
          }
        })
      }

      window.addEventListener('keydown', function (event) {
        var tagName = String(event.target && event.target.tagName || '').toLowerCase()
        if (tagName === 'input' || tagName === 'textarea') {
          return
        }
        if (event.key === 'ArrowLeft') {
          event.preventDefault()
          renderScene(currentSceneIndex - 1)
          return
        }
        if (event.key === 'ArrowRight') {
          event.preventDefault()
          renderScene(currentSceneIndex + 1)
          return
        }
        if (event.code === 'Space') {
          event.preventDefault()
          togglePlayState()
        }
      })
    })()
  <\/script>
</body>
</html>`
}

function buildMicroLessonFilename(message) {
  const title = getMicrocourseLessonTitle(message)
  const safe = title.replace(/[\\/:*?"<>|]+/g, '').slice(0, 20) || '微课'
  return `${safe}-${Date.now()}.html`
}

function openMicroLesson(message) {
  openMicroLessonWindow(message)
}

function downloadMicroLesson(message) {
  downloadMicroLessonHtml(message)
}

function openImageModal() {
  imageError.value = ''
  if (!imagePrompt.value) {
    imagePrompt.value = '生动有趣的教学插图，符合课堂主题'
  }
  showImageModal.value = true
}

function closeImageModal() {
  if (imageLoading.value) return
  showImageModal.value = false
}

function openImagePreview(url) {
  if (!url) return
  window.open(url, '_blank')
}

function getDefaultPptTopic() {
  const lastUser = [...messages.value].reverse().find((msg) => msg.role === 'user' && msg.text)
  return lastUser?.text || '小学三年级古诗《静夜思》'
}

function openPptModal() {
  pptError.value = ''
  pptStatus.value = ''
  planStatus.value = getPlanPreparationStateText()
  if (!pptTopic.value.trim() && !(usePlanForPpt.value && planId.value)) {
    pptTopic.value = getDefaultPptTopic()
  }
  showPptModal.value = true
  if (props.conversationId && getTeacherToken() && !planId.value) {
    preloadLatestPlan(props.conversationId)
  }
}

function closePptModal() {
  if (pptLoading.value) return
  showPptModal.value = false
}

function openVideoModal() {
  videoError.value = ''
  voicePreviewError.value = ''
  videoStatus.value = ''
  videoPreviewAudioUrl.value = ''
  stopVoicePreview()
  videoDraftPayload.value = null
  videoStep.value = 0
  if (!videoDurationSec.value || Number(videoDurationSec.value) < 5) {
    videoDurationSec.value = 12
  }
  if (!selectedAvatar.value && !videoImageFilename.value) {
    videoUseDefault.value = true
  }
  showVideoModal.value = true
}

function closeVideoModal() {
  if (videoLoading.value || videoUploading.value) return
  stopVoicePreview()
  showVideoModal.value = false
}

function triggerVideoFileInput() {
  videoFileInput.value?.click()
}

function removeVideoImage() {
  videoImagePreview.value = ''
  videoImageFilename.value = ''
  selectedAvatar.value = null
  if (videoFileInput.value) {
    videoFileInput.value.value = ''
  }
}

function useDefaultAvatar() {
  videoUseDefault.value = true
  removeVideoImage()
}

function selectAvatar(avatar) {
  selectedAvatar.value = avatar
  videoUseDefault.value = false
  videoImagePreview.value = ''
  videoImageFilename.value = avatar.img
  if (videoFileInput.value) {
    videoFileInput.value.value = ''
  }
}

async function handleVideoFileChange(event) {
  const file = event.target.files?.[0]
  if (!file) return

  videoUploading.value = true
  videoError.value = ''
  videoUseDefault.value = false
  selectedAvatar.value = null

  const reader = new FileReader()
  reader.onload = (e) => {
    videoImagePreview.value = e.target.result
  }
  reader.readAsDataURL(file)

  try {
    const formData = new FormData()
    formData.append('file', file)
    const response = await fetch(IMAGE_UPLOAD_URL, {
      method: 'POST',
      headers: getTeacherAuthHeaders(),
      body: formData,
    })
    const data = await response.json().catch(() => ({}))
    if (!response.ok) {
      throw new Error(data.detail || `HTTP ${response.status}`)
    }
    videoImageFilename.value = data.filename
  } catch (err) {
    videoError.value = `上传失败：${err.message}`
  } finally {
    videoUploading.value = false
  }
}

async function handleGenerateVideo() {
  if (videoLoading.value || videoUploading.value) return
  if (!videoAutoScript.value && !videoText.value.trim()) {
    videoError.value = '请先输入台词，或开启自动讲稿'
    return
  }
  if (!videoUseDefault.value && !videoImageFilename.value) {
    videoError.value = '请先上传人物照片'
    return
  }

  videoLoading.value = true
  videoError.value = ''
  videoStep.value = 0
  videoStatus.value = videoEditInstruction.value.trim()
    ? '正在根据修改意见重排脚本分镜...'
    : '正在整理对话与教学计划...'
  startVideoProgress()
  const stageTimer = window.setTimeout(() => {
    if (videoLoading.value && videoStep.value < 1) {
      videoStep.value = 1
      videoStatus.value = '正在生成微课脚本与分镜...'
    }
  }, 900)

  try {
    const lastMicrocourseMessage = [...messages.value]
      .reverse()
      .find((msg) => msg.type === 'video' && msg.microcourseJson)
    const shouldEdit = !!videoEditInstruction.value.trim()
    const draftBaseJson = videoDraftPayload.value?.microcourse_json
      || videoDraftPayload.value?.microcourseJson
      || lastMicrocourseMessage?.microcourseJson
      || undefined
    const response = await fetch(shouldEdit ? VIDEO_EDIT_API_URL : VIDEO_API_URL, {
      method: 'POST',
      headers: getTeacherAuthHeaders(true),
      body: JSON.stringify({
        text: videoAutoScript.value ? '' : videoText.value,
        image_filename: videoUseDefault.value ? null : videoImageFilename.value,
        voice_id: selectedAvatar.value?.voiceId || null,
        conversation_key: props.conversationId ? String(props.conversationId) : undefined,
        plan_id: planId.value ? Number(planId.value) : undefined,
        auto_script: !!videoAutoScript.value,
        duration_sec: Number(videoDurationSec.value) || 12,
        scene_style: 'blackboard',
        resolution: videoResolution.value || '720P',
        draft_only: true,
        theme_name: getMicrocourseThemeName(),
        instruction: shouldEdit ? videoEditInstruction.value.trim() : undefined,
        microcourse_json: shouldEdit ? draftBaseJson : undefined,
      }),
    })
    const data = await response.json().catch(() => ({}))
    if (!response.ok) {
      throw new Error(data.detail || `HTTP ${response.status}`)
    }

    videoDraftPayload.value = data && typeof data === 'object' ? data : null
    if (data.script_text && !videoText.value.trim()) {
      videoText.value = data.script_text
    }
    videoStep.value = 1
    videoStatus.value = '讲稿分镜已生成，可直接导出数字人微课'
    videoEditInstruction.value = ''
  } catch (err) {
    videoError.value = `分镜生成失败：${err.message}`
  } finally {
    clearTimeout(stageTimer)
    videoLoading.value = false
    videoStatus.value = ''
    stopVideoProgress()
  }
}

async function handleExportVideo() {
  if (videoLoading.value || videoUploading.value) return
  const draftJson = videoDraftPayload.value?.microcourse_json || videoDraftPayload.value?.microcourseJson
  if (!draftJson || typeof draftJson !== 'object') {
    videoError.value = '请先生成讲稿分镜，再导出数字人微课'
    return
  }

  videoLoading.value = true
  videoError.value = ''
  videoStep.value = 2
  videoStatus.value = '正在提交视频导出任务...'
  startVideoProgress()
  try {
    const task = await createGenerationTask('export_video', '数字人微课导出', {
      text: videoAutoScript.value ? '' : videoText.value,
      microcourse_json: draftJson,
      image_filename: videoUseDefault.value ? null : videoImageFilename.value,
      voice_id: selectedAvatar.value?.voiceId || null,
      conversation_key: props.conversationId ? String(props.conversationId) : undefined,
      plan_id: planId.value ? Number(planId.value) : undefined,
      duration_sec: Number(videoDurationSec.value) || 12,
      scene_style: 'blackboard',
      resolution: videoResolution.value || '720P',
      theme_name: getMicrocourseThemeName(),
    })
    appendTaskSubmittedMessage(task, '数字人微课')
    videoStatus.value = '已提交后台导出任务'
    showVideoModal.value = false
    videoEditInstruction.value = ''
    scrollToBottom()
  } catch (err) {
    videoError.value = `导出失败：${err.message}`
  } finally {
    videoLoading.value = false
    videoStatus.value = ''
    stopVideoProgress()
  }
}

async function handleGenerateImage() {
  if (imageLoading.value) return
  if (!imagePrompt.value.trim()) {
    imageError.value = '请先输入画面描述'
    return
  }

  imageLoading.value = true
  imageError.value = ''
  startImageProgress()

  try {
    const response = await fetch(IMAGE_API_URL, {
      method: 'POST',
      headers: getTeacherAuthHeaders(true),
      body: JSON.stringify({ prompt: imagePrompt.value }),
    })

    const data = await response.json().catch(() => ({}))
    if (!response.ok) {
      throw new Error(data.detail || `HTTP ${response.status}`)
    }

    messages.value.push({
      role: 'assistant',
      type: 'image',
      imageUrl: resolveApiMediaUrl(data.image_url),
      prompt: imagePrompt.value,
    })
    showImageModal.value = false
    scrollToBottom()
  } catch (err) {
    imageError.value = `生成失败：${err.message}`
  } finally {
    imageLoading.value = false
    stopImageProgress()
  }
}

function isConfirmCommand(text = '') {
  return /^(确认|确认执行|继续执行|开始执行|同意|好的执行)$/.test(String(text || '').trim())
}

function shouldUseAgentChat(text = '') {
  const normalized = String(text || '').trim()
  if (!normalized) return false
  if (isConfirmCommand(normalized)) return true

  const hasToolVerb = /(生成|导出|制作|做一份|做一个|整理成|提取|修改|优化|下载|打印|转成|生成对应)/.test(normalized)
  const hasToolTarget = /(PPT|ppt|课件|Word|word|DOCX|docx|教案|教学计划|教学方案|图片|配图|插图|视频|微课|数字人|小游戏|翻牌|复习提纲|闪卡|作业|练习卷|练习单|练习题|PDF|pdf|学习包|巩固包)/.test(normalized)
  return hasToolVerb && hasToolTarget
}

async function requestAgentChat(messageText) {
  const body = {
    message: messageText,
    conversation_key: props.conversationId ? String(props.conversationId) : undefined,
    confirm: isConfirmCommand(messageText),
  }
  const response = await fetch(AGENT_CHAT_API_URL, {
    method: 'POST',
    headers: getTeacherAuthHeaders(true),
    body: JSON.stringify(body),
  })
  const data = await response.json().catch(() => ({}))
  if (!response.ok) {
    throw new Error(data.detail || `HTTP ${response.status}`)
  }
  return data || {}
}

async function streamLegacyChatResponse({ text, imageFile, aiMsg }) {
  const formData = new FormData()
  formData.append('prompt', text)
  if (imageFile) {
    formData.append('image', imageFile)
  }

  if (props.conversationId) {
    formData.append('conversation_key', String(props.conversationId))
  }

  const headers = {}
  const token = getTeacherToken()
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  const response = await fetch(API_URL, {
    method: 'POST',
    headers,
    body: formData,
  })

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  const streamState = { buffer: '', done: false }
  let streamTimer = null
  let receivedChars = 0
  let displayedChars = 0

  const flushBuffer = () => {
    if (!streamState.buffer.length) {
      if (streamState.done && streamTimer) {
        clearInterval(streamTimer)
        streamTimer = null
      }
      return
    }
    const slice = streamState.buffer.slice(0, 8)
    streamState.buffer = streamState.buffer.slice(8)
    aiMsg.text += slice
    displayedChars += slice.length
    updateChatProgressForOutput(slice.length, displayedChars, receivedChars)
    scrollToBottom()
  }

  const enqueueChunk = (chunk) => {
    if (!chunk) return
    streamState.buffer += chunk
    receivedChars += chunk.length
    if (!aiMsg.streaming) {
      aiMsg.streaming = true
    }
    if (!streamTimer) {
      streamTimer = window.setInterval(flushBuffer, 24)
    }
  }

  const waitForDrain = () => new Promise((resolve) => {
    const check = () => {
      if (!streamState.buffer.length && streamState.done) {
        resolve()
      } else {
        setTimeout(check, 30)
      }
    }
    check()
  })

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    const chunk = decoder.decode(value, { stream: true })
    enqueueChunk(chunk)
  }

  streamState.done = true
  flushBuffer()
  await waitForDrain()
}

function waitForTypingFrame(delay = 24) {
  return new Promise((resolve) => {
    window.setTimeout(resolve, delay)
  })
}

async function streamTextIntoMessage(aiMsg, fullText, options = {}) {
  const text = String(fullText || '')
  if (!text) return

  const chunkSize = Math.max(1, Number(options.chunkSize) || 8)
  const delay = Math.max(0, Number(options.delay) || 24)
  aiMsg.text = ''
  aiMsg.streaming = true

  for (let index = 0; index < text.length; index += chunkSize) {
    const slice = text.slice(index, index + chunkSize)
    aiMsg.text += slice
    updateChatProgressForOutput(slice.length, Math.min(index + slice.length, text.length), text.length)
    scrollToBottom()
    if (delay > 0) {
      await waitForTypingFrame(delay)
    }
  }

  aiMsg.streaming = false
}

function appendAgentExecutionMessages(executionRows, fallbackPrompt = '') {
  const rows = Array.isArray(executionRows) ? executionRows : []
  let hasExtraMessage = false

  const pushFileArtifactMessage = ({
    badge,
    title,
    description,
    downloadUrl,
    downloadLabel = '下载文件',
    previewUrl = '',
    previewLabel = '在线预览',
  }) => {
    const resolvedDownloadUrl = resolveApiMediaUrl(downloadUrl)
    const resolvedPreviewUrl = resolveApiMediaUrl(previewUrl)
    if (!resolvedDownloadUrl && !resolvedPreviewUrl) {
      return false
    }
    messages.value.push({
      role: 'assistant',
      type: 'file_artifact',
      badge: badge || '📎 文件产物',
      title: title || '文件已生成',
      description: String(description || '').trim(),
      downloadUrl: resolvedDownloadUrl,
      downloadLabel,
      previewUrl: resolvedPreviewUrl,
      previewLabel,
    })
    return true
  }

  rows.forEach((item) => {
    if (!item?.ok || !item?.result || typeof item.result !== 'object') return
    const result = item.result
    const resultType = String(result.type || '').trim()
    const toolName = String(item.tool_name || '').trim()

    if (resultType === 'plan') {
      const payload = {
        plan_id: result.plan_id,
        title: result.title || '教学计划',
        plan: result.plan || {},
        missing_questions: Array.isArray(result.missing_questions) ? result.missing_questions : [],
      }
      applyPlanPayload(payload)
      pushPlanHintCard(payload)
      hasExtraMessage = true
      return
    }

    if (resultType === 'image' && result.image_url) {
      messages.value.push({
        role: 'assistant',
        type: 'image',
        imageUrl: resolveApiMediaUrl(result.image_url),
        prompt: result.prompt || fallbackPrompt,
      })
      hasExtraMessage = true
      return
    }

    if (resultType === 'video' && result.video_url) {
      messages.value.push({
        role: 'assistant',
        type: 'video',
        videoUrl: resolveApiMediaUrl(result.video_url),
        text: result.script_text || fallbackPrompt || '数字人微课',
        scriptText: result.script_text || fallbackPrompt || '',
        boardContent: Array.isArray(result.board_content) ? result.board_content : [],
        scenePreviews: Array.isArray(result.scene_previews) ? result.scene_previews : [],
        microcourseJson: result.microcourse_json && typeof result.microcourse_json === 'object'
          ? result.microcourse_json
          : null,
        patchJson: result.patch_json && typeof result.patch_json === 'object' ? result.patch_json : null,
      })
      hasExtraMessage = true
      return
    }

    if (resultType === 'game' && result.html) {
      messages.value.push({
        role: 'assistant',
        type: 'game',
        html: result.html,
        prompt: result.prompt || fallbackPrompt || '基于当前对话自动生成记忆翻牌游戏',
      })
      hasExtraMessage = true
      return
    }

    if (resultType === 'study_pack' && result.study_pack && typeof result.study_pack === 'object') {
      const normalizedPack = normalizeStudyPackPayload(result.study_pack)
      messages.value.push({
        role: 'assistant',
        type: 'study_pack',
        studyPack: normalizedPack,
        sourceMode: 'conversation',
        sourceLabel: '当前会话教学计划',
      })
      hasExtraMessage = true

      if (pushFileArtifactMessage({
        badge: '📄 学习巩固包 · 复习提纲',
        title: '复习提纲文档已生成',
        description: result.summary || '',
        downloadUrl: result.outline_download_url,
        downloadLabel: '下载提纲',
      })) {
        hasExtraMessage = true
      }

      if (pushFileArtifactMessage({
        badge: '🧪 学习巩固包 · 练习题',
        title: '练习题文档已生成',
        description: result.summary || '',
        downloadUrl: result.quiz_download_url,
        downloadLabel: '下载练习题',
      })) {
        hasExtraMessage = true
      }
      return
    }

    if (resultType === 'ppt' && result.download_url) {
      if (pushFileArtifactMessage({
        badge: '📊 PPT 课件',
        title: `PPT 已生成（v${result.version || 1}）`,
        description: result.summary || '',
        downloadUrl: result.download_url,
        downloadLabel: '下载课件',
      })) {
        hasExtraMessage = true
      }
      return
    }

    if (resultType === 'doc' && result.download_url) {
      if (pushFileArtifactMessage({
        badge: '📄 Word 教案',
        title: 'Word 教案已生成',
        description: result.summary || '',
        downloadUrl: result.download_url,
        downloadLabel: '下载教案',
      })) {
        hasExtraMessage = true
      }
      return
    }

    if (resultType === 'review_outline' && result.download_url) {
      if (pushFileArtifactMessage({
        badge: '🧭 复习提纲',
        title: '复习提纲已生成',
        description: result.summary || '',
        downloadUrl: result.download_url,
        downloadLabel: '下载提纲',
      })) {
        hasExtraMessage = true
      }
      return
    }

    if (resultType === 'flashcards' && result.download_url) {
      const cardCount = Array.isArray(result.flashcards) ? result.flashcards.length : 0
      if (pushFileArtifactMessage({
        badge: '🗂️ 课堂闪卡',
        title: cardCount ? `课堂闪卡已生成（${cardCount} 条）` : '课堂闪卡已生成',
        description: result.summary || '',
        downloadUrl: result.download_url,
        downloadLabel: '下载闪卡',
      })) {
        hasExtraMessage = true
      }
      return
    }

    if (resultType === 'homework' && result.download_url) {
      if (pushFileArtifactMessage({
        badge: '📝 课后作业',
        title: '课后作业已生成',
        description: result.summary || '',
        downloadUrl: result.download_url,
        downloadLabel: '下载作业',
      })) {
        hasExtraMessage = true
      }
      return
    }

    if (resultType === 'exercise_sheet' && result.download_url) {
      const isPdfIntent = toolName === 'generate_exercise_pdf'
      if (pushFileArtifactMessage({
        badge: isPdfIntent ? '🧾 练习题 PDF（DOCX 输出）' : '🧾 课堂练习单',
        title: isPdfIntent ? '练习题 PDF 任务已完成（当前自动执行输出 DOCX）' : '课堂练习单已生成',
        description: result.summary || '',
        downloadUrl: result.download_url,
        downloadLabel: isPdfIntent ? '下载练习题文档' : '下载练习单',
      })) {
        hasExtraMessage = true
      }
    }
  })

  if (hasExtraMessage) {
    scrollToBottom()
  }
}

async function handleSend({ text, imageFile, imagePreview }) {
  if (!text.trim() && !imageFile) return
  if (isLoading.value) return

  // 添加用户消息
  const userMsg = {
    role: 'user',
    text: text,
    image: imagePreview || null,
  }
  messages.value.push(userMsg)

  // 添加 AI 占位消息
  const aiMsg = {
    role: 'assistant',
    text: '',
    loading: true,
    streaming: false,
  }
  messages.value.push(aiMsg)
  scrollToBottom()

  isLoading.value = true
  startChatProgress()
  let shouldRefreshConversation = false
  let shouldAutoSyncPlan = false

  try {
    if (imageFile) {
      await streamLegacyChatResponse({
        text,
        imageFile,
        aiMsg,
      })
      shouldRefreshConversation = true
      const finalAssistantText = String(aiMsg.text || '').trim()
      if (finalAssistantText && !finalAssistantText.includes('**[请求出错]**')) {
        shouldAutoSyncPlan = true
      }
    } else {
      if (!shouldUseAgentChat(text)) {
        await streamLegacyChatResponse({
          text,
          imageFile: null,
          aiMsg,
        })
        shouldRefreshConversation = true
        const finalAssistantText = String(aiMsg.text || '').trim()
        if (finalAssistantText && !finalAssistantText.includes('**[请求出错]**')) {
          shouldAutoSyncPlan = true
        }
        return
      }

      const agentData = await requestAgentChat(text)
      const mode = String(agentData.mode || '').trim()

      if (mode === 'chat_fallback') {
        await streamLegacyChatResponse({
          text,
          imageFile: null,
          aiMsg,
        })
        shouldRefreshConversation = true
        const finalAssistantText = String(aiMsg.text || '').trim()
        if (finalAssistantText && !finalAssistantText.includes('**[请求出错]**')) {
          shouldAutoSyncPlan = true
        }
      } else {
        aiMsg.text = String(agentData.reply_text || '').trim() || '我已处理你的请求。'
        await streamTextIntoMessage(aiMsg, aiMsg.text)
        if (mode === 'tool_result') {
          appendAgentExecutionMessages(agentData.execution, text)
        }
        shouldRefreshConversation = !!props.conversationId
      }
    }
  } catch (err) {
    aiMsg.text += `\n\n**[请求出错]** ${err.message}`
  } finally {
    aiMsg.loading = false
    isLoading.value = false
    stopChatProgress()
    scrollToBottom()
    if (shouldRefreshConversation && shouldAutoSyncPlan) {
      queueAutoSyncPlanFromConversation({
        conversationKey: props.conversationId,
        userText: text,
        assistantText: aiMsg.text,
      }).catch(() => {})
    }
    if (shouldRefreshConversation && props.conversationId) {
      emit('conversation-updated', String(props.conversationId))
      scheduleWebResourcePoll(2200)
    }
  }
}
</script>

<template>
  <div
    class="chat-shell flex-1 flex flex-col min-w-0 h-full"
    :class="[
      props.uiTheme === 'elder' ? 'chat-shell--elder' : '',
      collapsed ? 'chat-shell--sidebar-collapsed' : '',
    ]"
  >
    <div class="chat-bg"></div>
    <div class="cosmic-mist"></div>
    <div class="chat-layout relative z-10 min-h-0 flex-1">
      <!-- 顶部导航栏 -->
      <header class="chat-header flex items-center gap-3 px-4 py-3">
        <button
          v-if="collapsed"
          @click="emit('toggle-sidebar')"
          class="p-1.5 rounded-lg hover:bg-white/10 transition"
          title="展开侧边栏"
        >
          <svg class="w-5 h-5 text-slate-200" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
              d="M4 6h16M4 12h16M4 18h16" />
          </svg>
        </button>
        <div class="chat-brand-cluster flex items-center gap-2">
          <div class="ai-avatar w-8 h-8 rounded-lg flex items-center justify-center text-white text-sm font-bold">
            教
          </div>
          <div>
            <h1 class="text-sm font-semibold text-slate-100">教策云枢 · AI 备课助手</h1>
            <p class="text-xs text-slate-300">输入需求 · 生成教案 · 导出课件</p>
          </div>
        </div>
      </header>

      <div v-if="chatProgress" class="chat-progress">
        <div class="chat-progress-bar" :style="{ width: `${chatProgress}%` }"></div>
        <span>AI 正在组织答案...</span>
      </div>

      <!-- 消息列表区域 -->
      <section v-if="shouldShowWebResources" class="web-resource-radar">
        <div class="web-resource-radar__head">
          <div>
            <span class="web-resource-radar__eyebrow">备课资源雷达</span>
            <strong v-if="webResourceSuggestion?.status === 'done'">
              已找到 {{ webResourceItems.length }} 条可参考资源
            </strong>
            <strong v-else-if="webResourceSuggestion?.status === 'searching' || webResourceLoading">
              正在联网搜索相关备课资源
            </strong>
            <strong v-else-if="webResourceSuggestion?.status === 'failed' || webResourceError">
              暂未获取到可展示资源
            </strong>
            <strong v-else>自动根据当前备课主题准备参考链接</strong>
            <p v-if="webResourceSuggestion?.query">搜索词：{{ webResourceSuggestion.query }}</p>
            <p v-else-if="webResourceError">{{ webResourceError }}</p>
          </div>
          <div class="web-resource-radar__actions">
            <button type="button" @click="refreshWebResourceSuggestion" :disabled="webResourceLoading">
              {{ webResourceLoading ? '搜索中' : '重新搜索' }}
            </button>
            <button
              v-if="webResourceItems.length"
              type="button"
              @click="webResourceExpanded = !webResourceExpanded"
            >
              {{ webResourceExpanded ? '收起' : '查看链接' }}
            </button>
          </div>
        </div>

        <div v-if="webResourceExpanded && webResourceItems.length" class="web-resource-radar__list">
          <article v-for="(item, index) in webResourceItems" :key="item.url || index" class="web-resource-item">
            <div class="web-resource-item__index">{{ index + 1 }}</div>
            <div class="web-resource-item__body">
              <button type="button" class="web-resource-item__title" @click="openWebResource(item.url)">
                {{ item.title || '备课参考资源' }}
              </button>
              <p v-if="item.summary">{{ item.summary }}</p>
              <small v-if="item.use_hint">{{ item.use_hint }}</small>
              <span>{{ item.source || item.url }}</span>
            </div>
          </article>
        </div>
      </section>

      <div ref="chatContainer" class="chat-message-scroll flex-1 min-h-0 overflow-y-auto px-4 py-6 space-y-6">
        <!-- 欢迎界面 -->
        <div v-if="messages.length === 0" class="welcome-state flex flex-col items-center justify-center h-full text-center py-10">
          <div class="welcome-orbit" aria-hidden="true">
            <div class="welcome-ring"></div>
            <div class="welcome-ring welcome-ring--wide"></div>
            <div class="welcome-tile welcome-tile--left"></div>
            <div class="welcome-tile welcome-tile--middle"></div>
            <div class="welcome-tile welcome-tile--right"></div>
          </div>
          <h2 class="welcome-title text-xl font-semibold text-slate-100 mb-2">
            {{ props.uiTheme === 'elder' ? '开始新备课' : '教策云枢 · AI 备课助手' }}
          </h2>
          <p v-if="props.uiTheme !== 'elder'" class="welcome-subtitle">
            您的智能备课伙伴，让教学设计更轻松、更高效
          </p>
          <p class="welcome-copy text-slate-300 text-sm max-w-md mb-6">
            <template v-if="props.uiTheme === 'elder'">
              按“输入需求 → 生成/导出”即可完成备课。<br />
              支持生成教案、PPT、微课和学习资料。
            </template>
            <template v-else>
              我是您的 AI 备课助手，可以根据新课标要求帮您生成教案。<br />
              您可以输入教学需求，也可以上传教材图片让我分析。
            </template>
          </p>
          <div class="welcome-grid grid grid-cols-1 sm:grid-cols-2 gap-3 max-w-lg w-full">
            <div class="welcome-card floating-card rounded-xl border border-white/40 p-3 text-left transition cursor-pointer"
                 @click="handleSend({ text: '请帮我设计一节40分钟公开课，给出教学目标、重难点和课堂活动安排', imageFile: null, imagePreview: null })">
              <span class="welcome-card-icon welcome-card-icon--blue">▤</span>
              <span class="welcome-card-body">
                <p class="text-sm font-medium text-slate-100">设计一节公开课</p>
                <p class="text-xs text-slate-300 mt-1">含目标、流程、互动与课堂评价</p>
              </span>
              <span class="welcome-card-arrow">→</span>
            </div>
            <div class="welcome-card floating-card rounded-xl border border-white/40 p-3 text-left transition cursor-pointer"
                 @click="handleSend({ text: '请根据我接下来上传的教材，先生成教案，再导出课堂PPT', imageFile: null, imagePreview: null })">
              <span class="welcome-card-icon welcome-card-icon--purple">P</span>
              <span class="welcome-card-body">
                <p class="text-sm font-medium text-slate-100">生成课件与教案</p>
                <p class="text-xs text-slate-300 mt-1">教案 + PPT 一体生成</p>
              </span>
              <span class="welcome-card-arrow">→</span>
            </div>
            <div class="welcome-card floating-card rounded-xl border border-white/40 p-3 text-left transition cursor-pointer"
                 @click="handleSend({ text: '帮我把当前教学内容整理成可直接导出的Word教案，语言简洁可上课直接使用', imageFile: null, imagePreview: null })">
              <span class="welcome-card-icon welcome-card-icon--green">W</span>
              <span class="welcome-card-body">
                <p class="text-sm font-medium text-slate-100">导出 Word 教案</p>
                <p class="text-xs text-slate-300 mt-1">适合直接打印或二次编辑</p>
              </span>
              <span class="welcome-card-arrow">→</span>
            </div>
            <div class="welcome-card floating-card rounded-xl border border-white/40 p-3 text-left transition cursor-pointer"
                 @click="handleSend({ text: '请基于本节课生成学习巩固资料，包含复习提纲、闪卡和课堂练习题', imageFile: null, imagePreview: null })">
              <span class="welcome-card-icon welcome-card-icon--orange">⌄</span>
              <span class="welcome-card-body">
                <p class="text-sm font-medium text-slate-100">生成学习资料包</p>
                <p class="text-xs text-slate-300 mt-1">提纲 + 闪卡 + 练习题</p>
              </span>
              <span class="welcome-card-arrow">→</span>
            </div>
          </div>
        </div>

        <!-- 消息气泡 -->
        <template v-for="(msg, idx) in messages" :key="idx">
          <!-- 用户消息 -->
          <div v-if="msg.role === 'user'" class="flex justify-end">
            <div class="max-w-[75%]">
              <div class="user-bubble text-white rounded-2xl rounded-tr-md px-4 py-3 text-sm leading-relaxed">
                {{ msg.text }}
              </div>
              <img
                v-if="msg.image"
                :src="msg.image"
                alt="上传图片"
                class="mt-2 max-w-[200px] max-h-[200px] rounded-xl border border-slate-700 object-cover"
              />
            </div>
          </div>

          <!-- AI 消息 -->
          <div v-else class="flex justify-start gap-3">
            <div class="ai-avatar w-8 h-8 rounded-lg flex-shrink-0 flex items-center justify-center text-white text-xs font-bold mt-1">
              教
            </div>
            <div class="assistant-card max-w-[80%] rounded-2xl rounded-tl-md px-4 py-3 bg-slate-900/60 border border-white/10">
              <div v-if="msg.type === 'image'" class="space-y-2">
                <div class="text-xs text-sky-300 font-medium">🎨 AI 配图</div>
                <button
                  class="block rounded-2xl overflow-hidden border border-white/10 bg-slate-900/60 shadow-sm hover:shadow-md transition"
                  @click="openImagePreview(msg.imageUrl)"
                  title="点击放大查看"
                >
                  <img
                    :src="msg.imageUrl"
                    alt="AI 生成配图"
                    class="max-w-[320px] w-full object-cover"
                  />
                </button>
                <p class="text-[11px] text-slate-300">提示词：{{ msg.prompt }}</p>
              </div>
              <div v-else-if="msg.type === 'video'" class="space-y-3">
                <div class="text-xs text-rose-300 font-medium">🎥 数字人微课</div>
                <div class="micro-lesson">
                  <div class="micro-lesson__board">
                    <div class="micro-lesson__title">📌 今日重点</div>
                    <ul class="micro-lesson__list">
                      <li v-for="(item, index) in getBoardContent(msg)" :key="index">
                        {{ item }}
                      </li>
                    </ul>
                    <div v-if="getMicrocourseScenes(msg).length" class="micro-lesson__scenes">
                      <div
                        v-for="(scene, sceneIndex) in getMicrocourseScenes(msg).slice(0, 4)"
                        :key="scene.scene_id || sceneIndex"
                        class="micro-lesson__scene"
                      >
                        <div class="micro-lesson__scene-head">
                          <span>{{ scene.board_title || `场景 ${sceneIndex + 1}` }}</span>
                          <span>{{ scene.duration_sec || '' }}s</span>
                        </div>
                        <p v-if="scene.subtitle" class="micro-lesson__scene-subtitle">{{ scene.subtitle }}</p>
                      </div>
                    </div>
                  </div>
                  <div class="micro-lesson__video">
                    <video
                      :src="msg.videoUrl"
                      controls
                      playsinline
                      class="micro-lesson__video-media"
                    ></video>
                  </div>
                </div>
                <div class="micro-lesson__actions">
                  <button class="micro-lesson__action" @click="openMicroLesson(msg)">打开微课</button>
                  <button class="micro-lesson__action is-secondary" @click="downloadMicroLesson(msg)">下载微课</button>
                  <a class="micro-lesson__action is-ghost" :href="msg.videoUrl" target="_blank" rel="noopener">打开视频</a>
                </div>
                <p class="text-[11px] text-slate-300">台词：{{ msg.scriptText || msg.text }}</p>
              </div>
              <div v-else-if="msg.type === 'game'" class="space-y-3">
                <div class="text-xs text-emerald-300 font-medium">🎮 互动小游戏</div>
                <div class="game-card">
                  <iframe
                    class="game-frame"
                    :srcdoc="msg.html"
                    title="互动小游戏"
                  ></iframe>
                </div>
                <div class="game-actions">
                  <button class="game-action" @click="openGame(msg)">打开游戏</button>
                  <button class="game-action is-secondary" @click="downloadGame(msg)">下载 HTML</button>
                </div>
                <p class="text-[11px] text-slate-300">需求：{{ msg.prompt }}</p>
              </div>
              <div v-else-if="msg.type === 'plan'" class="space-y-3">
                <div class="text-xs text-cyan-300 font-medium">🧠 教学计划卡片</div>
                <div class="rounded-xl border border-cyan-300/30 bg-cyan-500/5 p-3 space-y-2">
                  <div class="text-sm font-semibold text-cyan-100">{{ msg.title || '教学计划' }}</div>
                  <p class="text-[11px] text-cyan-100/80">系统已生成教学计划，可按需调整。</p>
                  <p v-if="isDeveloperMode" class="text-[11px] text-cyan-100/70">内部编号：#{{ msg.planId }}</p>
                  <div v-if="Array.isArray(msg.keyConcepts) && msg.keyConcepts.length" class="text-[11px] text-slate-200">
                    <p class="mb-1 text-cyan-200/90">关键知识点：</p>
                    <ul class="list-disc pl-4 space-y-1">
                      <li v-for="(item, index) in msg.keyConcepts.slice(0, 4)" :key="index">{{ item }}</li>
                    </ul>
                  </div>
                  <div v-if="Array.isArray(msg.missingQuestions) && msg.missingQuestions.length" class="text-[11px] text-amber-200">
                    <p class="mb-1">待补充信息：</p>
                    <ul class="list-disc pl-4 space-y-1">
                      <li v-for="(item, index) in msg.missingQuestions.slice(0, 3)" :key="index">{{ item }}</li>
                    </ul>
                  </div>
                </div>
                <button
                  class="px-3 py-1.5 rounded-lg border border-cyan-300/40 text-cyan-100 text-xs hover:bg-cyan-300/10 transition"
                  @click="openPlanModal"
                >
                  打开教学计划
                </button>
              </div>
              <div v-else-if="msg.type === 'study_pack'" class="space-y-3">
                <div class="text-xs text-emerald-300 font-medium">🧠 学习巩固包</div>
                <div class="rounded-xl border border-emerald-300/30 bg-emerald-500/5 p-3 space-y-2">
                  <p class="text-xs text-emerald-100">来源：{{ msg.sourceLabel || '当前对话上下文' }}</p>
                  <p class="text-[11px] text-slate-200">
                    复习大纲 1 份 · 闪卡 {{ Array.isArray(msg.studyPack?.flashcards) ? msg.studyPack.flashcards.length : 0 }} 条 ·
                    题目 {{ Array.isArray(msg.studyPack?.quiz) ? msg.studyPack.quiz.length : 0 }} 道
                  </p>
                  <div class="rounded-lg bg-slate-950/35 border border-white/10 p-2 text-[11px] text-slate-200 line-clamp-3" v-html="getStudyPackOutlineHtml(msg.studyPack)"></div>
                </div>
                <button
                  class="px-3 py-1.5 rounded-lg border border-emerald-300/40 text-emerald-100 text-xs hover:bg-emerald-300/10 transition"
                  @click="openStudyPackFromMessage(msg.studyPack)"
                >
                  查看完整巩固包
                </button>
              </div>
              <div v-else-if="msg.type === 'file_artifact'" class="space-y-2">
                <div class="text-xs text-amber-300 font-medium">{{ msg.badge || '📎 文件产物' }}</div>
                <div class="rounded-xl border border-amber-300/30 bg-amber-500/5 p-3 space-y-1">
                  <p class="text-sm font-semibold text-amber-100">{{ msg.title || '文件已生成' }}</p>
                  <p v-if="msg.description" class="text-[11px] text-slate-200">{{ msg.description }}</p>
                </div>
                <div class="flex flex-wrap gap-2">
                  <button
                    v-if="msg.downloadUrl"
                    class="px-3 py-1.5 rounded-lg border border-amber-300/40 text-amber-100 text-xs hover:bg-amber-300/10 transition"
                    type="button"
                    @click="handleFileArtifactDownload(msg)"
                  >
                    {{ msg.downloadLabel || '下载文件' }}
                  </button>
                  <button
                    v-if="msg.previewUrl"
                    class="px-3 py-1.5 rounded-lg border border-slate-300/30 text-slate-200 text-xs hover:bg-slate-300/10 transition"
                    type="button"
                    @click="handleFileArtifactPreview(msg)"
                  >
                    {{ msg.previewLabel || '在线预览' }}
                  </button>
                </div>
              </div>
              <template v-else>
                <div
                  v-if="msg.text"
                  class="markdown-body text-sm text-slate-100"
                  :class="{ 'typing-cursor': msg.loading }"
                  v-html="renderMarkdown(msg.text)"
                ></div>
                <div v-else-if="msg.loading" class="flex items-center gap-2 py-1">
                  <div class="flex gap-1">
                    <span class="w-2 h-2 bg-indigo-400 rounded-full animate-bounce" style="animation-delay: 0ms"></span>
                    <span class="w-2 h-2 bg-indigo-400 rounded-full animate-bounce" style="animation-delay: 150ms"></span>
                    <span class="w-2 h-2 bg-indigo-400 rounded-full animate-bounce" style="animation-delay: 300ms"></span>
                  </div>
                  <span class="text-xs text-slate-400">正在思考...</span>
                </div>
              </template>
            </div>
          </div>
        </template>
      </div>

      <div class="chat-input-zone">
        <div class="input-resizer" @mousedown="startInputResize" title="拖动调整输入区高度">
          <span class="resizer-handle"></span>
        </div>

        <!-- 底部输入栏 -->
        <InputBar
          :disabled="isLoading || imageLoading || pptLoading || videoLoading || videoUploading"
          :rag-active="ragActive"
          :ui-theme="props.uiTheme"
          :plan-status-text="getPlanPreparationStateText()"
          :plan-status-tone="getPlanPreparationTone()"
          :height="inputHeight"
          @send="handleSend"
          @extract-plan="openPlanModal"
          @generate-image="openImageModal"
          @generate-ppt="openPptModal"
          @generate-video="openVideoModal"
          @generate-game="openGameModal"
          @export-doc="openDocModal"
          @edit-ppt="openPptEditModal"
          @open-study-tools="openStudyPackModal"
        />
      </div>
    </div>
  </div>

  <!-- 修改 PPT Modal -->
  <div
    v-if="showPptEditModal"
    class="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/80 px-4"
    @click.self="closePptEditModal"
  >
    <div class="modal-card w-full max-w-md rounded-2xl p-5 bg-slate-900/60 border border-white/10">
      <div class="flex items-center justify-between mb-3">
        <h3 class="text-base font-semibold text-slate-100">🛠️ 迭代修改 PPT</h3>
        <button
          class="text-slate-400 hover:text-slate-200"
          @click="closePptEditModal"
          :disabled="pptEditLoading"
        >
          ✕
        </button>
      </div>
      <p class="text-xs text-slate-400 mb-3">
        先预览当前版本，再输入修改意见（如“第3页和第4页对调，并在练习页加一个例题”）。
        系统会基于当前课件结构打补丁并生成新版本（版本号 +1）。
      </p>

      <template v-if="!pptArtifact?.id">
        <div class="rounded-xl border border-white/15 bg-slate-900/45 p-4">
          <p class="text-sm font-semibold text-slate-100">当前还没有可修改的 PPT 版本</p>
          <p class="mt-1 text-xs text-slate-300">请先导出 PPT 课件，再进行迭代修改。</p>
          <div class="mt-3 flex flex-wrap gap-2">
            <button
              type="button"
              class="rounded-lg bg-sky-400 px-3 py-1.5 text-xs font-semibold text-slate-900 hover:bg-sky-300"
              @click="openPptModalFromEdit"
            >
              去导出 PPT
            </button>
            <button
              type="button"
              class="rounded-lg border border-white/20 px-3 py-1.5 text-xs text-slate-200 hover:bg-white/10"
              @click="closePptEditModal"
            >
              关闭
            </button>
          </div>
        </div>
      </template>

      <template v-else>
        <div class="rounded-xl border border-amber-300/30 bg-amber-500/5 p-3 mb-3">
          <div class="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-amber-100">
            <span>当前版本：V{{ pptArtifact?.version || '--' }}</span>
            <span>Artifact：#{{ pptArtifact?.id || '--' }}</span>
            <span>页数：{{ pptArtifact?.page_count || 0 }}</span>
          </div>
          <p v-if="pptArtifact?.created_at" class="mt-1 text-[11px] text-amber-100/80">
            生成时间：{{ new Date(pptArtifact.created_at).toLocaleString() }}
          </p>
        </div>

        <div v-if="pptVersionHistory.length" class="mb-3">
          <p class="text-[11px] text-slate-300 mb-1">版本记录（新→旧）</p>
          <div class="flex flex-wrap gap-1.5">
            <span
              v-for="item in pptVersionHistory"
              :key="item.id"
              class="inline-flex items-center rounded-full border border-white/15 bg-slate-900/60 px-2 py-1 text-[11px] text-slate-200"
            >
              V{{ item.version }} · #{{ item.id }}
            </span>
          </div>
        </div>

        <div class="mb-3 rounded-xl border border-white/10 bg-slate-900/40 p-2">
          <p class="mb-2 text-[11px] text-slate-300">PPT 预览（每页 PNG）</p>
          <p v-if="pptPreviewLoading" class="text-[11px] text-slate-300">正在加载预览...</p>
          <p v-else-if="pptPreviewError" class="text-[11px] text-rose-300">{{ pptPreviewError }}</p>
          <div v-else-if="pptPreviewUrls.length" class="grid grid-cols-2 gap-2 max-h-52 overflow-y-auto pr-1">
            <div
              v-for="(url, index) in pptPreviewUrls"
              :key="`${url}-${index}`"
              class="rounded-lg border border-white/10 bg-slate-950/50 p-1"
            >
              <img :src="url" :alt="`PPT 预览第 ${index + 1} 页`" class="w-full rounded-md" />
              <p class="mt-1 text-center text-[11px] text-slate-300">第 {{ index + 1 }} 页</p>
              <button
                v-if="getSourceLinkForPreviewPage(index)"
                type="button"
                class="mt-1 w-full text-[10px] text-cyan-200/90 text-center hover:text-cyan-100 underline-offset-2 hover:underline"
                @click="openCitationEvidence(index)"
              >
                引用：{{ getReadableSourceLabel(getSourceLinkForPreviewPage(index)) }}（查看依据）
              </button>
              <p v-else class="mt-1 text-[10px] text-slate-400 text-center">引用：暂无匹配教材依据</p>
            </div>
          </div>
          <p v-else class="text-[11px] text-slate-400">暂无预览，请先生成 PPT。</p>
        </div>

        <textarea
          v-model="pptEditInstruction"
          rows="3"
          class="modal-textarea w-full rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-amber-300"
          placeholder="例如：把第二页内容精简为3条要点"
        ></textarea>
        <p v-if="pptEditError" class="mt-2 text-xs text-rose-300">{{ pptEditError }}</p>
        <div class="mt-4 flex items-center justify-end gap-2">
          <button
            class="px-3 py-2 text-sm rounded-lg text-slate-400 hover:text-slate-200"
            @click="closePptEditModal"
            :disabled="pptEditLoading"
          >
            取消
          </button>
          <button
            class="px-4 py-2 rounded-lg bg-amber-400 text-slate-900 text-sm hover:bg-amber-300 transition disabled:opacity-60"
            @click="handleEditPpt"
            :disabled="pptEditLoading"
          >
            <span v-if="pptEditLoading" class="inline-flex items-center gap-2">
              <span class="w-4 h-4 rounded-full border-2 border-white/40 border-t-white animate-spin"></span>
              正在修改...
            </span>
            <span v-else>开始修改</span>
          </button>
        </div>
      </template>
    </div>
  </div>

  <!-- 引用依据 Modal -->
  <div
    v-if="showCitationEvidenceModal"
    class="fixed inset-0 z-[60] flex items-center justify-center bg-slate-950/85 px-4"
    @click.self="closeCitationEvidenceModal"
  >
    <div class="modal-card w-full max-w-lg rounded-2xl p-5 bg-slate-900/80 border border-cyan-200/30">
      <div class="mb-3 flex items-center justify-between">
        <h3 class="text-base font-semibold text-slate-100">📎 引用依据</h3>
        <button class="text-slate-400 hover:text-slate-200" @click="closeCitationEvidenceModal">✕</button>
      </div>
      <p class="text-xs text-cyan-100/90">{{ getReadableSourceLabel(activeCitationEvidence) }}</p>
      <div class="mt-3 space-y-2 rounded-xl border border-white/10 bg-slate-950/40 p-3 text-xs text-slate-300">
        <p>
          <span class="text-slate-400">材料：</span>
          {{ getReadableDocFilename(activeCitationEvidence) }}
        </p>
        <p>
          <span class="text-slate-400">定位：</span>
          {{ getReadableLocator(getSourceLocatorFromItem(activeCitationEvidence)) }}
        </p>
        <p>
          <span class="text-slate-400">片段：</span>
          {{ activeCitationEvidence?.chunk_preview || activeCitationEvidence?.note || '暂无摘要' }}
        </p>
      </div>
      <div class="mt-4 flex justify-end">
        <button
          type="button"
          class="rounded-lg border border-cyan-200/40 px-3 py-1.5 text-xs text-cyan-100 hover:bg-cyan-300/10"
          @click="closeCitationEvidenceModal"
        >
          关闭
        </button>
      </div>
    </div>
  </div>

  <!-- 导出教案 Modal -->
  <div
    v-if="showDocModal"
    class="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/80 px-4"
    @click.self="closeDocModal"
  >
    <div class="modal-card w-full max-w-lg rounded-2xl p-5 bg-slate-900/60 border border-white/10">
      <div class="flex items-center justify-between mb-3">
        <h3 class="text-base font-semibold text-slate-100">📄 导出 Word 教案</h3>
        <button
          class="text-slate-400 hover:text-slate-200"
          @click="closeDocModal"
          :disabled="docLoading"
        >
          ✕
        </button>
      </div>
      <p class="text-xs text-slate-400 mb-3">
        可填写教案标题与内容需求，系统将生成标准 Word 教案并下载。
      </p>
      <p class="mb-3 text-xs" :class="getPlanPreparationTone()">
        {{ getPlanPreparationStateText() }}
      </p>
      <label class="mb-3 inline-flex items-center gap-2 text-xs text-cyan-100/90">
        <input
          v-model="usePlanForDoc"
          type="checkbox"
          class="h-4 w-4 rounded border-white/20 bg-slate-900/60"
          :disabled="docLoading"
        />
        <span>
          自动使用教学 Plan（推荐）
          <span v-if="planId">#{{ planId }}</span>
          <span v-else class="text-slate-400">（生成时自动准备）</span>
        </span>
      </label>
      <input
        v-model="docTitle"
        class="w-full rounded-xl px-3 py-2 text-sm mb-3 bg-slate-900/60 border border-white/10 focus:outline-none focus:ring-2 focus:ring-emerald-300"
        placeholder="教案标题（可选）"
      />
      <textarea
        v-model="docPrompt"
        rows="4"
        class="modal-textarea w-full rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-300"
        placeholder="教案内容或生成需求"
      ></textarea>
      <p v-if="docError" class="mt-2 text-xs text-rose-300">{{ docError }}</p>
      <div class="mt-4 flex items-center justify-end gap-2">
        <button
          class="px-3 py-2 text-sm rounded-lg text-slate-400 hover:text-slate-200"
          @click="closeDocModal"
          :disabled="docLoading"
        >
          取消
        </button>
        <button
          class="px-4 py-2 rounded-lg bg-emerald-400 text-slate-900 text-sm hover:bg-emerald-300 transition disabled:opacity-60"
          @click="handleExportDoc"
          :disabled="docLoading"
        >
          <span v-if="docLoading" class="inline-flex items-center gap-2">
            <span class="w-4 h-4 rounded-full border-2 border-white/40 border-t-white animate-spin"></span>
            正在生成...
          </span>
          <span v-else>导出 Word</span>
        </button>
      </div>
    </div>
  </div>

  <!-- 学习巩固包 Modal（原学生端能力并入教师端） -->
  <div
    v-if="showStudyPackModal"
    class="fixed inset-0 z-50 flex items-stretch justify-center bg-slate-950/85 p-2 sm:p-4"
    @click.self="closeStudyPackModal"
  >
    <div class="modal-card study-pack-modal w-full max-w-[1450px] h-full rounded-2xl border border-white/10 bg-slate-900/75 p-4 sm:p-5">
      <div class="mb-3 flex items-center justify-between">
        <h3 class="text-base font-semibold text-slate-100">🧠 学习巩固包（提纲 / 闪卡 / 题目）</h3>
        <button
          class="text-slate-400 hover:text-slate-200"
          @click="closeStudyPackModal"
          :disabled="studyPackLoading"
        >
          ✕
        </button>
      </div>

      <p class="mb-3 text-xs text-slate-300">
        {{ props.uiTheme === 'elder'
          ? '三步即可完成：1) 选来源 2) 点生成 3) 查看提纲/闪卡/练习题。'
          : '你可以直接基于当前对话生成，也可以上传教材文件生成。适合课堂后快速产出复习资料。' }}
      </p>

      <div class="grid h-[calc(100%-68px)] min-h-0 grid-cols-1 gap-3 xl:grid-cols-[340px_minmax(0,1fr)]">
        <div class="study-pack-side rounded-2xl border border-cyan-300/25 bg-cyan-500/5 p-3 sm:p-4 space-y-3">
          <ol v-if="props.uiTheme === 'elder'" class="teacher-step-list text-xs text-slate-200">
            <li>1. 选择生成来源</li>
            <li>2. 点击“生成学习资料”</li>
            <li>3. 在右侧查看结果</li>
          </ol>
          <p class="text-xs font-semibold text-cyan-100">生成来源</p>
          <div class="space-y-2 text-xs text-slate-200">
            <label class="flex items-center gap-2">
              <input v-model="studyPackSourceMode" value="conversation" type="radio" class="h-3.5 w-3.5" />
              <span>基于当前对话上下文</span>
            </label>
            <label class="flex items-center gap-2">
              <input v-model="studyPackSourceMode" value="file" type="radio" class="h-3.5 w-3.5" />
              <span>基于上传资料文件</span>
            </label>
          </div>

          <template v-if="studyPackSourceMode === 'conversation'">
            <label class="text-xs text-slate-200 block">
              对话范围
              <select
                v-model="studyPackConversationScope"
                class="mt-1 w-full rounded-lg px-2 py-1.5 text-xs bg-slate-900/70 border border-white/10 focus:outline-none focus:ring-1 focus:ring-cyan-300"
              >
                <option v-for="option in STUDY_PACK_SCOPE_OPTIONS" :key="option.value" :value="option.value">
                  {{ option.label }}
                </option>
              </select>
            </label>
            <textarea
              v-model="studyPackCustomText"
              rows="6"
              class="modal-textarea w-full rounded-xl px-3 py-2 text-xs focus:outline-none focus:ring-2 focus:ring-cyan-300"
              :placeholder="props.uiTheme === 'elder'
                ? '可选：补充一句要求，例如“侧重易错点”“适合课后复习”。'
                : '可选：补充要求（如重点记忆哪类语法/题型）'"
            ></textarea>
          </template>

          <template v-else>
            <div class="rounded-lg border border-white/10 bg-slate-900/50 p-2">
              <button
                type="button"
                class="w-full rounded-lg border border-cyan-300/40 px-3 py-2 text-xs text-cyan-100 hover:bg-cyan-300/10"
                @click="triggerStudyPackFileInput"
              >
                上传资料（PDF/PPT/Word/TXT）
              </button>
              <input
                ref="studyPackFileInput"
                type="file"
                accept=".pdf,.ppt,.pptx,.doc,.docx,.txt"
                class="hidden"
                @change="handleStudyPackFileChange"
              />
              <p v-if="studyPackFileName" class="mt-2 text-[11px] text-slate-200">已选：{{ studyPackFileName }}</p>
              <button
                v-if="studyPackFileName"
                type="button"
                class="mt-2 text-[11px] text-rose-300 hover:text-rose-200"
                @click="clearStudyPackFile"
              >
                清除文件
              </button>
            </div>
          </template>

          <button
            class="w-full rounded-xl bg-gradient-to-r from-sky-400 via-indigo-400 to-fuchsia-400 px-3 py-2.5 text-xs font-semibold text-slate-900 hover:opacity-90 disabled:opacity-60"
            @click="handleGenerateStudyPack"
            :disabled="studyPackLoading"
          >
            <span v-if="studyPackLoading">生成中...</span>
            <span v-else>{{ props.uiTheme === 'elder' ? '生成学习资料' : '生成学习巩固包' }}</span>
          </button>

          <p v-if="studyPackError" class="text-xs text-rose-300">{{ studyPackError }}</p>
        </div>

        <div class="study-pack-main rounded-2xl border border-white/10 bg-slate-900/40 p-3 sm:p-4 min-h-0 flex flex-col">
          <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
            <div>
              <p class="text-xs text-slate-300">生成结果预览</p>
              <p v-if="studyPackResult" class="text-[11px] text-slate-400 mt-0.5">
                复习大纲 1 份 · 闪卡 {{ studyPackResult.flashcards.length }} 张 · 题目 {{ studyPackQuizRows.length }} 道
              </p>
            </div>
            <button
              v-if="studyPackResult"
              type="button"
              class="text-[11px] text-slate-400 hover:text-slate-200"
              @click="resetStudyPackResultView"
            >
              清空结果
            </button>
          </div>

          <template v-if="studyPackResult">
            <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
              <div class="flex flex-wrap gap-2">
                <button
                  type="button"
                  class="study-pack-tab-btn"
                  :class="studyPackTab === 'outline' ? 'study-pack-tab-btn--active' : ''"
                  @click="studyPackTab = 'outline'"
                >
                  复习大纲
                </button>
                <button
                  type="button"
                  class="study-pack-tab-btn"
                  :class="studyPackTab === 'flashcards' ? 'study-pack-tab-btn--active' : ''"
                  @click="studyPackTab = 'flashcards'"
                >
                  闪卡
                </button>
                <button
                  type="button"
                  class="study-pack-tab-btn"
                  :class="studyPackTab === 'quiz' ? 'study-pack-tab-btn--active' : ''"
                  @click="studyPackTab = 'quiz'"
                >
                  题目
                </button>
              </div>

              <button
                v-if="studyPackTab === 'outline' || studyPackTab === 'quiz'"
                type="button"
                class="rounded-full border border-emerald-300/35 bg-emerald-400/10 px-3 py-1.5 text-[11px] text-emerald-100 transition hover:bg-emerald-300/20 disabled:opacity-60"
                :disabled="!!studyPackExporting || (studyPackTab === 'outline' ? !String(studyPackResult.outline || '').trim() : !studyPackQuizRows.length)"
                @click="handleExportStudyPackDoc(studyPackTab === 'outline' ? 'outline' : 'quiz')"
              >
                <span v-if="studyPackExporting === (studyPackTab === 'outline' ? 'outline' : 'quiz')">导出中...</span>
                <span v-else>{{ studyPackTab === 'outline' ? '导出提纲 Word' : '导出题目 Word' }}</span>
              </button>
            </div>

            <div class="study-pack-content flex-1 min-h-0 overflow-y-auto pr-1">
              <div v-if="studyPackTab === 'outline'" class="study-pack-panel markdown-body text-sm text-slate-100" v-html="getStudyPackOutlineHtml(studyPackResult)"></div>

              <div v-else-if="studyPackTab === 'flashcards'" class="study-pack-flash-grid">
                <button
                  v-for="(card, index) in studyPackResult.flashcards"
                  :key="`pack-card-${index}`"
                  type="button"
                  class="study-pack-flip-card"
                  @click="toggleStudyPackCard(index)"
                >
                  <div :class="['study-pack-flip-inner', studyPackCardStates[index] ? 'is-flipped' : '']">
                    <div :class="['study-pack-flip-face study-pack-flip-front bg-gradient-to-br', getStudyPackCardTheme(index)]">
                      <p class="study-pack-flip-index">Card {{ index + 1 }}</p>
                      <p class="study-pack-flip-text" v-html="renderInlineMarkdown(card.front || '请补充正面问题')"></p>
                      <span class="study-pack-flip-tip">点击翻转</span>
                    </div>
                    <div class="study-pack-flip-face study-pack-flip-back">
                      <p class="study-pack-flip-index">解析</p>
                      <p class="study-pack-flip-text" v-html="renderInlineMarkdown(card.back || '请补充背面答案')"></p>
                    </div>
                  </div>
                </button>
              </div>

              <div v-else class="space-y-3">
                <div class="study-pack-panel">
                  <h4 class="study-pack-section-title">第一部分：题目</h4>
                  <div class="space-y-2">
                    <div
                      v-for="(item, index) in studyPackQuizRows"
                      :key="`pack-quiz-question-${index}`"
                      class="study-pack-quiz-card"
                    >
                      <div class="study-pack-quiz-head">
                        <span class="study-pack-badge">Q{{ index + 1 }}</span>
                        <p class="study-pack-quiz-text" v-html="renderInlineMarkdown(item.question || '（无题干）')"></p>
                      </div>
                      <ul class="study-pack-quiz-options">
                        <li
                          v-for="(option, idx) in item.options || []"
                          :key="`opt-${index}-${idx}`"
                          v-html="renderInlineMarkdown(option)"
                        ></li>
                      </ul>
                    </div>
                  </div>
                </div>

                <div class="study-pack-panel">
                  <h4 class="study-pack-section-title">第二部分：答案与解析</h4>
                  <div class="space-y-2">
                    <div
                      v-for="(item, index) in studyPackQuizRows"
                      :key="`pack-quiz-analysis-${index}`"
                      class="study-pack-analysis-card"
                    >
                      <p class="study-pack-analysis-head">第 {{ index + 1 }} 题</p>
                      <p class="study-pack-analysis-row">
                        <span>答案：</span>
                        <span v-html="renderInlineMarkdown(item.answer || '未提供')"></span>
                      </p>
                      <p class="study-pack-analysis-row">
                        <span>解析：</span>
                        <span v-html="renderInlineMarkdown(item.analysis || '请补充解析')"></span>
                      </p>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </template>

          <p v-else class="text-xs text-slate-400 mt-2">
            {{ props.uiTheme === 'elder' ? '还没有生成结果，点击左侧“生成学习资料”。' : '暂未生成，左侧配置来源后点击“生成学习巩固包”。' }}
          </p>
        </div>
      </div>
    </div>
  </div>

  <!-- 教学 Plan Modal -->
  <div
    v-if="showPlanModal"
    class="plan-modal-overlay fixed inset-0 z-50 flex items-center justify-center bg-slate-950/80 px-4"
    @click.self="closePlanModal"
  >
    <div class="modal-card plan-workbench" @click="closePlanMoreMenu">
      <header class="plan-workbench__header">
        <div class="min-w-0">
          <p class="plan-workbench__eyebrow">LESSON PLAN HUB</p>
          <h3 class="plan-workbench__title">教学计划中枢</h3>
          <p class="plan-workbench__sub">
            汇总课程要求、教学要素、教材依据和风险提示；教师可在这里集中查看、编辑并决定后续生成内容的依据。
          </p>
        </div>
        <div class="plan-workbench__headerActions">
          <span
            v-if="isDeveloperMode"
            class="plan-dev-badge"
          >
            开发者模式
          </span>
          <button
            class="plan-workbench__close"
            @click="closePlanModal"
            :disabled="planLoading || planSaving || planLinkLoading"
            aria-label="关闭教学计划"
          >
            ×
          </button>
        </div>
      </header>

      <section class="plan-overview-grid">
        <div class="plan-overview-card plan-overview-card--wide">
          <span class="plan-overview-card__label">当前计划</span>
          <strong :class="getPlanPreparationTone()">{{ getPlanPreparationStateText() }}</strong>
          <p>{{ planTitle || '尚未生成教学计划' }}</p>
        </div>
        <div class="plan-overview-card">
          <span class="plan-overview-card__label">完整度</span>
          <strong>{{ getPlanCompletenessPercent() }}%</strong>
          <div class="plan-mini-bar">
            <i :class="getPlanCompletenessBarClass()" :style="{ width: `${getPlanCompletenessPercent()}%` }"></i>
          </div>
        </div>
        <div class="plan-overview-card">
          <span class="plan-overview-card__label">教材依据</span>
          <strong>{{ planSourceLinks.length }} / {{ getPlanConceptOptions().length || 0 }}</strong>
          <p>待补齐 {{ getPlanMissingEvidenceCount() }} 条</p>
        </div>
        <div class="plan-overview-card">
          <span class="plan-overview-card__label">待补信息</span>
          <strong>{{ planMissingQuestions.length }}</strong>
          <p>{{ planMissingQuestions.length ? '建议先补齐' : '当前可用' }}</p>
        </div>
      </section>

      <div class="plan-workbench__body">
        <aside class="plan-workbench__side">
          <nav class="plan-side-nav" aria-label="教学计划模块">
            <button
              v-for="stage in PLAN_MODAL_STAGE_OPTIONS"
              :key="stage.value"
              type="button"
              class="plan-side-nav__item"
              :class="{ 'is-active': planModalStage === stage.value }"
              @click="setPlanModalStage(stage.value)"
            >
              <span>{{ stage.label }}</span>
              <small>{{ stage.hint }}</small>
            </button>
          </nav>

          <section class="plan-panel plan-summary-card">
            <div class="plan-panel__head">
              <p>计划摘要</p>
              <button
                type="button"
                @click="handleRefreshPlanDetailFromMenu"
                :disabled="!planId"
              >
                刷新
              </button>
            </div>
            <dl class="plan-summary-list">
              <div>
                <dt>课型结构</dt>
                <dd>{{ getPlanStructureSummaryText() }}</dd>
              </div>
              <div>
                <dt>核心重点</dt>
                <dd>{{ getPlanKeyPointSummaryText() }}</dd>
              </div>
              <div>
                <dt>风险提示</dt>
                <dd>{{ getPlanRiskSummaryText() }}</dd>
              </div>
            </dl>
          </section>

          <section class="plan-panel plan-health-card">
            <div class="plan-panel__head">
              <p>质量检查</p>
              <span>{{ getPlanCompletenessPercent() }}%</span>
            </div>
            <div class="plan-health-meter">
              <i :class="getPlanCompletenessBarClass()" :style="{ width: `${getPlanCompletenessPercent()}%` }"></i>
            </div>
            <ul>
              <li v-for="(hint, index) in getPlanRiskHints()" :key="`plan-risk-${index}`">{{ hint }}</li>
            </ul>
          </section>

          <section class="plan-panel plan-meta-card">
            <p class="plan-panel__title">同步信息</p>
            <div class="plan-meta-grid">
              <span>用户消息</span>
              <strong>{{ planMessageCount || 0 }} 条</strong>
              <span>计划编号</span>
              <strong>{{ planId || '--' }}</strong>
              <span>上次对齐</span>
              <strong>
                <template v-if="planLastMessageAt">{{ new Date(planLastMessageAt).toLocaleString() }}</template>
                <template v-else>--</template>
              </strong>
            </div>
          </section>
        </aside>

        <main class="plan-workbench__main">
          <div class="plan-main-scroll">
            <section v-if="planModalStage === 'preferences'" class="plan-editor-section plan-editor-section--hero">
              <div class="plan-section-head">
                <div>
                  <p class="plan-section-kicker">QUICK REQUIREMENTS</p>
                  <h4>偏好与约束</h4>
                </div>
                <span>适合快速告诉系统“这节课要怎么上”</span>
              </div>

              <label class="plan-field plan-field--full">
                <span>教师补充要求</span>
                <textarea
                  v-model="planConstraint"
                  rows="6"
                  class="modal-textarea"
                  placeholder="例如：40分钟；互动多一点；练习难度中等；最后5分钟随堂测；PPT简洁蓝白风格"
                ></textarea>
              </label>

              <div class="plan-choice-grid">
                <div class="plan-choice-group">
                  <p>课堂风格</p>
                  <div>
                    <button
                      v-for="option in TEACHING_STYLE_OPTIONS"
                      :key="`style-${option}`"
                      type="button"
                      class="plan-chip"
                      :class="{ 'is-selected': planPreferenceQuick.teachingStyle === option }"
                      @click="togglePlanPreferenceOption('teachingStyle', option)"
                    >
                      {{ option }}
                    </button>
                  </div>
                </div>
                <div class="plan-choice-group">
                  <p>练习难度</p>
                  <div>
                    <button
                      v-for="option in PRACTICE_DIFFICULTY_OPTIONS"
                      :key="`difficulty-${option}`"
                      type="button"
                      class="plan-chip"
                      :class="{ 'is-selected': planPreferenceQuick.practiceDifficulty === option }"
                      @click="togglePlanPreferenceOption('practiceDifficulty', option)"
                    >
                      {{ option }}
                    </button>
                  </div>
                </div>
                <div class="plan-choice-group">
                  <p>课件结构</p>
                  <div>
                    <button
                      v-for="option in COURSE_STRUCTURE_OPTIONS"
                      :key="`structure-${option}`"
                      type="button"
                      class="plan-chip"
                      :class="{ 'is-selected': planPreferenceQuick.courseStructure === option }"
                      @click="togglePlanPreferenceOption('courseStructure', option)"
                    >
                      {{ option }}
                    </button>
                  </div>
                </div>
                <div class="plan-choice-group">
                  <p>生成要求</p>
                  <div>
                    <button
                      type="button"
                      class="plan-chip"
                      :class="{ 'is-selected': planPreferenceQuick.needQuiz }"
                      @click="togglePlanPreferenceNeed('needQuiz')"
                    >
                      随堂测
                    </button>
                    <button
                      type="button"
                      class="plan-chip"
                      :class="{ 'is-selected': planPreferenceQuick.needHomework }"
                      @click="togglePlanPreferenceNeed('needHomework')"
                    >
                      作业
                    </button>
                    <button
                      type="button"
                      class="plan-chip"
                      :class="{ 'is-selected': planPreferenceQuick.needBoardDesign }"
                      @click="togglePlanPreferenceNeed('needBoardDesign')"
                    >
                      板书设计
                    </button>
                  </div>
                </div>
              </div>
            </section>

            <section v-else-if="planModalStage === 'evidence'" class="plan-editor-section">
              <div class="plan-section-head">
                <div>
                  <p class="plan-section-kicker">EVIDENCE & MATERIALS</p>
                  <h4>教材依据与资料匹配</h4>
                </div>
                <button
                  type="button"
                  class="plan-secondary-btn"
                  @click="handleAutoLinkSources"
                  :disabled="planLinkLoading || !planId"
                >
                  <span v-if="planLinkLoading">匹配中...</span>
                  <span v-else>重新自动匹配</span>
                </button>
              </div>

              <div class="plan-evidence-layout">
                <section class="plan-evidence-list">
                  <div class="plan-panel plan-missing-card">
                    <div class="plan-panel__head">
                      <p>缺失信息任务（{{ planMissingQuestions.length }}）</p>
                      <button
                        type="button"
                        @click="handleCompleteMissingTasks"
                        :disabled="!planMissingQuestions.length"
                      >
                        去补齐
                      </button>
                    </div>
                    <ul v-if="planMissingQuestions.length">
                      <li v-for="(item, index) in planMissingQuestions" :key="index">{{ item }}</li>
                    </ul>
                    <p v-else>缺失项已补齐，可直接生成内容。</p>
                  </div>

                  <div v-if="planSourceLinks.length" class="plan-source-list">
                    <article
                      v-for="item in planSourceLinks"
                      :key="`evidence-${item.id}`"
                      class="plan-source-card"
                    >
                      <div>
                        <h5>{{ getPlanConceptLabelById(item.key_concept_id) }}</h5>
                        <span
                          :class="getSourceLinkStatusText(item) === '已定位' ? 'is-ok' : 'is-warn'"
                        >
                          {{ getSourceLinkStatusText(item) }}
                        </span>
                      </div>
                      <p>{{ getReadableSourceLabel(item) }}</p>
                      <small>定位：{{ getReadableLocator(getSourceLocatorFromItem(item)) }}</small>
                      <blockquote v-if="item.chunk_preview || item.note">{{ item.chunk_preview || item.note }}</blockquote>
                      <button type="button" @click="prepareSourceLinkForEdit(item)">重新选择来源</button>
                    </article>
                  </div>

                  <div v-else class="plan-empty-state">
                    <strong>当前还没有来源匹配结果</strong>
                    <p>可在右侧手动绑定资料，或点击“重新自动匹配”。</p>
                  </div>
                </section>

                <section class="plan-panel plan-link-editor">
                  <p class="plan-panel__title">手动修正引用来源</p>
                  <p v-if="kbDocsLoading" class="plan-help-text">正在加载资料列表...</p>
                  <p v-if="kbDocError" class="plan-error-text">{{ kbDocError }}</p>

                  <label class="plan-field">
                    <span>资料文档</span>
                    <select v-model="linkDocId">
                      <option value="">选择资料文档</option>
                      <option v-for="doc in kbDocs" :key="doc.doc_id" :value="String(doc.doc_id)">
                        {{ doc.filename }}（{{ doc.chunk_count }} 段）
                      </option>
                    </select>
                  </label>

                  <label class="plan-field">
                    <span>绑定知识点</span>
                    <select v-model="linkConceptId">
                      <option value="">未指定知识点</option>
                      <option v-for="concept in getPlanConceptOptions()" :key="concept.value" :value="concept.value">
                        {{ concept.label }}
                      </option>
                    </select>
                  </label>

                  <label class="plan-field">
                    <span>资料片段</span>
                    <select v-model="linkChunkId" :disabled="!linkDocId || kbOutlineLoading">
                      <option value="">自动定位（可选）</option>
                      <option v-for="chunk in kbOutline" :key="chunk.chunk_id" :value="String(chunk.chunk_id)">
                        {{ getReadableLocator(chunk.locator || '') }} · {{ chunk.preview }}
                      </option>
                    </select>
                  </label>

                  <p v-if="kbOutlineLoading" class="plan-help-text">正在加载片段列表...</p>
                  <p v-if="kbOutlineError" class="plan-error-text">{{ kbOutlineError }}</p>

                  <label class="plan-field">
                    <span>定位说明</span>
                    <input v-model="linkLocator" placeholder="可选定位（如 p3 / t30-60）" />
                  </label>
                  <label class="plan-field">
                    <span>绑定备注</span>
                    <textarea v-model="linkNote" rows="3" class="modal-textarea" placeholder="绑定说明（可选）"></textarea>
                  </label>
                  <button
                    class="plan-primary-btn"
                    type="button"
                    @click="handleLinkSource"
                    :disabled="planLinkLoading || !planId"
                  >
                    <span v-if="planLinkLoading">提交中...</span>
                    <span v-else>绑定到知识点</span>
                  </button>
                </section>
              </div>
            </section>

            <section v-else class="plan-editor-section">
              <div class="plan-section-head">
                <div>
                  <p class="plan-section-kicker">TEACHING ELEMENTS</p>
                  <h4>{{ isDeveloperMode && showPlanJsonAdvanced ? '开发者计划数据' : '教学要素编辑' }}</h4>
                </div>
                <span>{{ isDeveloperMode ? '可在更多菜单切换开发者视图' : '按字段编辑后保存即可' }}</span>
              </div>

              <div v-if="!isDeveloperMode || !showPlanJsonAdvanced" class="plan-form-stack">
                <div class="plan-form-card">
                  <div class="plan-form-card__head">
                    <p>基础信息</p>
                    <small>用于锁定教学对象、课题和资料范围</small>
                  </div>
                  <div class="plan-field-grid">
                    <label class="plan-field">
                      <span>学段 / 年级</span>
                      <input v-model="planForm.gradeStage" placeholder="如：高二" />
                    </label>
                    <label class="plan-field">
                      <span>学科</span>
                      <input v-model="planForm.subject" placeholder="如：语文" />
                    </label>
                    <label class="plan-field">
                      <span>课时（分钟）</span>
                      <input v-model="planForm.lessonDurationMin" type="number" min="1" placeholder="40" />
                    </label>
                    <label class="plan-field plan-field--wide">
                      <span>课文 / 单元</span>
                      <input v-model="planForm.unitText" placeholder="如：蜀道难" />
                    </label>
                    <label class="plan-field">
                      <span>主题标签</span>
                      <input v-model="planForm.topicTagsText" placeholder="用、分隔" />
                    </label>
                    <label class="plan-field">
                      <span>资料范围</span>
                      <select v-model="planForm.scopePolicy">
                        <option value="session_first">优先当前会话资料</option>
                        <option value="session_only">仅当前会话资料</option>
                        <option value="user_only">仅教师资料</option>
                        <option value="base_only">仅系统资料</option>
                      </select>
                    </label>
                  </div>
                </div>

                <div class="plan-form-card">
                  <div class="plan-form-card__head">
                    <p>教学目标</p>
                    <button type="button" @click="addPlanListItem('teachingGoals')">添加目标</button>
                  </div>
                  <div class="plan-repeat-list">
                    <div v-for="(_, index) in planForm.teachingGoals" :key="`goal-${index}`" class="plan-repeat-row">
                      <span>{{ index + 1 }}</span>
                      <input v-model="planForm.teachingGoals[index]" :placeholder="`目标 ${index + 1}`" />
                      <button type="button" @click="removePlanListItem('teachingGoals', index)">删除</button>
                    </div>
                  </div>
                </div>

                <div class="plan-form-card">
                  <div class="plan-form-card__head">
                    <p>关键知识点</p>
                    <button type="button" @click="addPlanListItem('keyConcepts')">添加知识点</button>
                  </div>
                  <div class="plan-repeat-list">
                    <div v-for="(_, index) in planForm.keyConcepts" :key="`concept-${index}`" class="plan-repeat-row">
                      <span>{{ index + 1 }}</span>
                      <input v-model="planForm.keyConcepts[index]" :placeholder="`知识点 ${index + 1}`" />
                      <button type="button" @click="removePlanListItem('keyConcepts', index)">删除</button>
                    </div>
                  </div>
                </div>

                <div class="plan-form-card">
                  <div class="plan-form-card__head">
                    <p>重点难点</p>
                    <button type="button" @click="addPlanListItem('focusDifficulties')">添加重难点</button>
                  </div>
                  <div class="plan-repeat-list">
                    <div v-for="(_, index) in planForm.focusDifficulties" :key="`focus-${index}`" class="plan-repeat-row">
                      <span>{{ index + 1 }}</span>
                      <input v-model="planForm.focusDifficulties[index]" :placeholder="`重点/难点 ${index + 1}`" />
                      <button type="button" @click="removePlanListItem('focusDifficulties', index)">删除</button>
                    </div>
                  </div>
                </div>

                <div class="plan-form-card">
                  <div class="plan-form-card__head">
                    <p>活动流程</p>
                    <button type="button" @click="addPlanActivityRow">添加活动</button>
                  </div>
                  <div class="plan-repeat-list">
                    <div v-for="(_, index) in planForm.activityDesign" :key="`activity-${index}`" class="plan-repeat-row plan-repeat-row--activity">
                      <span>{{ index + 1 }}</span>
                      <input v-model="planForm.activityDesign[index].name" :placeholder="`活动 ${index + 1}`" />
                      <input v-model="planForm.activityDesign[index].duration" type="number" min="1" placeholder="分钟" />
                      <button type="button" @click="removePlanActivityRow(index)">删除</button>
                    </div>
                  </div>
                </div>
              </div>

              <textarea
                v-else
                v-model="planDraft"
                class="modal-textarea plan-json-editor"
                placeholder="开发者模式：可查看或编辑计划数据（内部）"
              ></textarea>
            </section>
          </div>

          <footer class="plan-action-bar" @click.stop>
            <div class="plan-action-status">
              <p v-if="planStatus">{{ planStatus }}</p>
              <p v-if="planError" class="is-error">{{ planError }}</p>
              <p v-if="planLinkError" class="is-error">{{ planLinkError }}</p>
            </div>
            <div class="plan-action-buttons">
              <button
                class="plan-primary-btn"
                type="button"
                @click="handleQuickUpdatePlan"
                :disabled="planLoading"
              >
                <span v-if="planLoading">应用中...</span>
                <span v-else>更新并应用</span>
              </button>
              <button
                class="plan-secondary-btn"
                type="button"
                @click="handleSavePlan"
                :disabled="planSaving || !planId"
              >
                <span v-if="planSaving">保存中...</span>
                <span v-else>保存修改</span>
              </button>
              <div class="relative" @click.stop>
                <button
                  class="plan-ghost-btn"
                  type="button"
                  @click="togglePlanMoreMenu"
                  :disabled="planLoading || planSaving || planLinkLoading"
                >
                  更多
                </button>
                <div v-if="showPlanMoreMenu" class="plan-more-menu">
                  <button type="button" @click="handleRefreshPlanDetailFromMenu" :disabled="!planId">刷新详情</button>
                  <button v-if="isDeveloperMode" type="button" @click="handleTogglePlanEditorFromMenu">
                    {{ showPlanJsonAdvanced ? '返回教学要素编辑' : '查看计划数据（开发者）' }}
                  </button>
                  <button type="button" @click="handleAutoLinkSourcesFromMenu" :disabled="!planId || planLinkLoading">
                    <span v-if="planLinkLoading">匹配中...</span>
                    <span v-else>自动匹配引用</span>
                  </button>
                  <button v-if="isDeveloperMode" type="button" @click="handleCopyPlanDiagnosticsFromMenu">复制诊断信息</button>
                  <button v-if="isDeveloperMode" type="button" @click="handleExportPlanDataFromMenu">导出计划数据</button>
                </div>
              </div>
            </div>
          </footer>
        </main>
      </div>
    </div>
  </div>

  <!-- 缺失信息任务 Modal -->
  <div
    v-if="showMissingQuestionsModal"
    class="fixed inset-0 z-[60] flex items-center justify-center bg-slate-950/85 px-4"
    @click.self="cancelMissingQuestionsTaskModal"
  >
    <div class="modal-card w-full max-w-2xl rounded-2xl p-5 bg-slate-900/80 border border-white/10 max-h-[86vh] overflow-y-auto">
      <div class="mb-3 flex items-center justify-between">
        <h3 class="text-base font-semibold text-slate-100">🧩 补齐教学计划关键信息</h3>
        <button
          class="text-slate-400 hover:text-slate-200"
          @click="cancelMissingQuestionsTaskModal"
          :disabled="missingQuestionsModalLoading"
        >
          ✕
        </button>
      </div>

      <p class="mb-3 text-xs text-slate-300">请完成以下任务，系统将自动回填教学计划并继续后续生成。</p>
      <ul class="mb-4 list-disc pl-5 text-xs text-amber-100 space-y-1">
        <li v-for="(question, index) in missingQuestionsForModal" :key="`mq-${index}`">{{ question }}</li>
      </ul>

      <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
        <label class="text-xs text-slate-200">
          班级基础
          <select
            v-model="missingTaskAnswers.class_level"
            class="mt-1 w-full rounded-lg px-2 py-1.5 text-xs bg-slate-900/70 border border-white/10 focus:outline-none focus:ring-1 focus:ring-amber-300"
          >
            <option value="">请选择</option>
            <option v-for="option in CLASS_LEVEL_OPTIONS" :key="option" :value="option">{{ option }}</option>
          </select>
        </label>

        <label class="text-xs text-slate-200">
          常见错误（可多选）
          <p v-if="activePlanSubjectForMissing" class="mt-1 text-[11px] text-slate-400">当前学科：{{ activePlanSubjectForMissing }}</p>
          <div class="mt-1 grid grid-cols-2 gap-1 rounded-lg border border-white/10 bg-slate-900/50 p-2">
            <label
              v-for="option in commonMistakeOptionsForModal"
              :key="`mistake-${option}`"
              class="inline-flex items-center gap-1 text-[11px] text-slate-200"
            >
              <input
                type="checkbox"
                class="h-3.5 w-3.5 rounded border-white/20 bg-slate-900/80"
                :checked="missingTaskAnswers.common_mistakes.includes(option)"
                @change="toggleCommonMistake(option)"
              />
              <span>{{ option }}</span>
            </label>
          </div>
        </label>
      </div>

      <div class="mt-4 grid grid-cols-1 md:grid-cols-3 gap-3">
        <div>
          <p class="text-xs text-slate-200 mb-1">答题卡格式</p>
          <div class="space-y-1 text-[11px] text-slate-200">
            <label class="inline-flex items-center gap-1"><input v-model="missingTaskAnswers.unified_answer_sheet" :value="true" type="radio" class="h-3.5 w-3.5" />需要</label>
            <label class="inline-flex items-center gap-1"><input v-model="missingTaskAnswers.unified_answer_sheet" :value="false" type="radio" class="h-3.5 w-3.5" />不需要</label>
          </div>
        </div>
        <div>
          <p class="text-xs text-slate-200 mb-1">动画仿真</p>
          <div class="space-y-1 text-[11px] text-slate-200">
            <label class="inline-flex items-center gap-1"><input v-model="missingTaskAnswers.include_animation" :value="true" type="radio" class="h-3.5 w-3.5" />需要</label>
            <label class="inline-flex items-center gap-1"><input v-model="missingTaskAnswers.include_animation" :value="false" type="radio" class="h-3.5 w-3.5" />不需要</label>
          </div>
        </div>
        <div>
          <p class="text-xs text-slate-200 mb-1">小组合作角色</p>
          <div class="space-y-1 text-[11px] text-slate-200">
            <label class="inline-flex items-center gap-1"><input v-model="missingTaskAnswers.group_work" :value="true" type="radio" class="h-3.5 w-3.5" />需要</label>
            <label class="inline-flex items-center gap-1"><input v-model="missingTaskAnswers.group_work" :value="false" type="radio" class="h-3.5 w-3.5" />不需要</label>
          </div>
        </div>
      </div>

      <textarea
        v-model="missingTaskAnswers.extra_notes"
        rows="3"
        class="modal-textarea mt-4 w-full rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-amber-300"
        placeholder="补充说明（如课堂纪律、学校要求、展示风格等）"
      ></textarea>

      <p v-if="missingQuestionsModalError" class="mt-2 text-xs text-rose-300">{{ missingQuestionsModalError }}</p>
      <div class="mt-4 flex justify-end gap-2">
        <button
          class="px-3 py-2 text-sm rounded-lg text-slate-400 hover:text-slate-200"
          @click="cancelMissingQuestionsTaskModal"
          :disabled="missingQuestionsModalLoading"
        >
          取消
        </button>
        <button
          class="px-4 py-2 rounded-lg bg-amber-400 text-slate-900 text-sm hover:bg-amber-300 transition disabled:opacity-60"
          @click="submitMissingQuestionsTaskModal"
          :disabled="missingQuestionsModalLoading"
        >
          <span v-if="missingQuestionsModalLoading" class="inline-flex items-center gap-2">
            <span class="w-4 h-4 rounded-full border-2 border-white/40 border-t-white animate-spin"></span>
            正在提交...
          </span>
          <span v-else>完成并继续</span>
        </button>
      </div>
    </div>
  </div>

  <!-- 互动小游戏 Modal -->
  <div
    v-if="showGameModal"
    class="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/80 px-4"
    @click.self="closeGameModal"
  >
    <div class="modal-card w-full max-w-lg rounded-2xl p-5 bg-slate-900/60 border border-white/10">
      <div class="flex items-center justify-between mb-3">
        <h3 class="text-base font-semibold text-slate-100">🎮 生成互动小游戏</h3>
        <button
          class="text-slate-400 hover:text-slate-200"
          @click="closeGameModal"
          :disabled="gameLoading"
        >
          ✕
        </button>
      </div>
      <p class="text-xs text-slate-400 mb-3">
        系统只会抽取可配对的学科知识点；课时、年级、教材版本、标题、教学流程等信息不会作为卡片。
      </p>
      <textarea
        v-model="gamePrompt"
        rows="4"
        class="modal-textarea w-full rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-sky-400"
        placeholder="例如：围绕定语从句生成概念-解释翻牌配对，突出关系代词、关系副词和易错点"
      ></textarea>
      <p v-if="gameError" class="mt-2 text-xs text-rose-300">{{ gameError }}</p>
      <div class="mt-4 flex items-center justify-end gap-2">
        <button
          class="px-3 py-2 text-sm rounded-lg text-slate-400 hover:text-slate-200"
          @click="closeGameModal"
          :disabled="gameLoading"
        >
          取消
        </button>
        <button
          class="px-4 py-2 rounded-lg bg-sky-400 text-slate-900 text-sm hover:bg-sky-300 transition disabled:opacity-60"
          @click="handleGenerateGame"
          :disabled="gameLoading"
        >
          <span v-if="gameLoading" class="inline-flex items-center gap-2">
            <span class="w-4 h-4 rounded-full border-2 border-white/40 border-t-white animate-spin"></span>
            正在生成...
          </span>
          <span v-else>生成游戏</span>
        </button>
      </div>
    </div>
  </div>

  <!-- 生成配图 Modal -->
  <div
    v-if="showImageModal"
    class="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/80 px-4"
    @click.self="closeImageModal"
  >
    <div class="modal-card w-full max-w-md rounded-2xl p-5 bg-slate-900/60 border border-white/10">
      <div class="flex items-center justify-between mb-3">
        <h3 class="text-base font-semibold text-slate-100">🎨 一键配图</h3>
        <button
          class="text-slate-400 hover:text-slate-200"
          @click="closeImageModal"
          :disabled="imageLoading"
        >
          ✕
        </button>
      </div>
      <p class="text-xs text-slate-400 mb-3">
        输入画面描述，我们将生成教学插图。建议描述场景、人物、风格等。
      </p>
      <textarea
        v-model="imagePrompt"
        rows="3"
        class="modal-textarea w-full rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-sky-400"
        placeholder="生动有趣的教学插图..."
      ></textarea>
      <p v-if="imageError" class="mt-2 text-xs text-rose-300">{{ imageError }}</p>
      <div v-if="imageProgress" class="modal-progress">
        <div class="modal-progress-bar" :style="{ width: `${imageProgress}%` }"></div>
        <span>AI 正在绘制插图...</span>
      </div>
      <div class="mt-4 flex items-center justify-end gap-2">
        <button
          class="px-3 py-2 text-sm rounded-lg text-slate-400 hover:text-slate-200"
          @click="closeImageModal"
          :disabled="imageLoading"
        >
          取消
        </button>
        <button
          class="px-4 py-2 rounded-lg bg-sky-500 text-slate-900 text-sm hover:bg-sky-400 transition disabled:opacity-60"
          @click="handleGenerateImage"
          :disabled="imageLoading"
        >
          <span v-if="imageLoading" class="inline-flex items-center gap-2">
            <span class="w-4 h-4 rounded-full border-2 border-white/40 border-t-white animate-spin"></span>
            正在绘制中...
          </span>
          <span v-else>开始生成</span>
        </button>
      </div>
    </div>
  </div>

  <!-- 导出 PPT Modal -->
  <div
    v-if="showPptModal"
    class="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/80 px-4"
    @click.self="closePptModal"
  >
    <div class="modal-card w-full max-w-md rounded-2xl p-5 bg-slate-900/60 border border-white/10">
      <div class="flex items-center justify-between mb-3">
        <h3 class="text-base font-semibold text-slate-100">📊 导出 PPT 课件</h3>
        <button
          class="text-slate-400 hover:text-slate-200"
          @click="closePptModal"
          :disabled="pptLoading"
        >
          ✕
        </button>
      </div>
      <p class="text-xs text-slate-400 mb-3">
        输入课件主题，我们将自动规划大纲并生成可下载的 PPT。
      </p>
      <p class="mb-3 text-xs" :class="getPlanPreparationTone()">
        {{ getPlanPreparationStateText() }}
      </p>
      <label class="mb-3 inline-flex items-center gap-2 text-xs text-cyan-100/90">
        <input
          v-model="usePlanForPpt"
          type="checkbox"
          class="h-4 w-4 rounded border-white/20 bg-slate-900/60"
          :disabled="pptLoading"
        />
        <span>
          一键自动准备教学 Plan
          <span v-if="planId">#{{ planId }}</span>
          <span v-else class="text-slate-400">（生成时自动提取）</span>
        </span>
      </label>
      <textarea
        v-model="pptTopic"
        rows="3"
        class="modal-textarea w-full rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-amber-300"
        placeholder="例如：小学三年级古诗《静夜思》"
      ></textarea>
      <p v-if="pptError" class="mt-2 text-xs text-rose-300">{{ pptError }}</p>
      <div v-if="pptLoading" class="mt-3 flex items-center gap-2 text-xs text-amber-300">
        <span class="w-4 h-4 rounded-full border-2 border-amber-200/40 border-t-amber-300 animate-spin"></span>
        <span>{{ pptStatus }}</span>
      </div>
      <div v-if="pptProgress" class="modal-progress">
        <div class="modal-progress-bar modal-progress-amber" :style="{ width: `${pptProgress}%` }"></div>
        <span>AI 正在生成课件...</span>
      </div>
      <div class="mt-4 flex items-center justify-end gap-2">
        <button
          class="px-3 py-2 text-sm rounded-lg text-slate-400 hover:text-slate-200"
          @click="closePptModal"
          :disabled="pptLoading"
        >
          取消
        </button>
        <button
          class="px-4 py-2 rounded-lg bg-amber-400 text-slate-900 text-sm hover:bg-amber-300 transition disabled:opacity-60"
          @click="handleGeneratePpt"
          :disabled="pptLoading"
        >
          <span v-if="pptLoading" class="inline-flex items-center gap-2">
            <span class="w-4 h-4 rounded-full border-2 border-white/40 border-t-white animate-spin"></span>
            正在生成...
          </span>
          <span v-else>一键生成 PPT</span>
        </button>
      </div>
    </div>
  </div>

  <!-- 数字人微课 Modal -->
  <div
    v-if="showVideoModal"
    class="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/80 px-4"
    @click.self="closeVideoModal"
  >
    <div class="modal-card w-full max-w-lg rounded-2xl p-5 bg-slate-900/60 border border-white/10">
      <div class="flex items-center justify-between mb-3">
        <h3 class="text-base font-semibold text-slate-100">🎥 生成数字人微课</h3>
        <button
          class="text-slate-400 hover:text-slate-200"
          @click="closeVideoModal"
          :disabled="videoLoading || videoUploading"
        >
          ✕
        </button>
      </div>
      <p class="text-xs text-slate-400 mb-4">
        建议先生成“讲稿 + 分镜 + 板书要点”，再导出数字人微课，避免每次改词都重新渲染视频。
      </p>

      <div class="mb-4">
        <div class="flex items-center justify-between mb-3">
          <p class="text-sm font-medium text-slate-200">👤 数字人教师配置</p>
          <button
            type="button"
            class="px-3 py-2 rounded-lg border border-white/10 text-sm text-slate-300 hover:bg-slate-900/60 transition"
            @click="useDefaultAvatar"
          >
            使用系统默认
          </button>
        </div>
        <div class="grid grid-cols-2 gap-3">
          <button
            v-for="char in avatarList"
            :key="char.id"
            type="button"
            class="group relative overflow-hidden rounded-xl border-2 transition-all"
            :class="selectedAvatar?.id === char.id ? 'border-sky-400 ring-2 ring-sky-400/30' : 'border-transparent hover:border-sky-300/60'"
            @click="selectAvatar(char)"
          >
            <img
              :src="char.img"
              :alt="char.name"
              class="w-full h-24 object-cover group-hover:scale-105 transition-transform"
            />
            <div class="p-2 text-center text-xs font-medium bg-slate-950/70 text-slate-200">{{ char.name }}</div>
          </button>
        </div>

        <div class="mt-4 border-t border-white/10 pt-4 space-y-2">
          <p class="text-sm font-medium text-slate-200">自定义人物照片</p>
          <div v-if="videoImagePreview" class="flex items-center gap-3">
            <img
              :src="videoImagePreview"
              alt="人物预览"
              class="w-16 h-16 rounded-xl object-cover border border-white/10"
            />
            <button
              class="text-xs text-slate-400 hover:text-slate-200"
              @click="removeVideoImage"
              type="button"
            >
              移除照片
            </button>
          </div>
          <div v-else class="flex items-center gap-2">
            <button
              type="button"
              class="px-3 py-2 rounded-lg bg-slate-900/60 text-slate-200 text-xs hover:bg-slate-800/60"
              @click="triggerVideoFileInput"
              :disabled="videoUploading"
            >
              上传人物照片
            </button>
            <span class="text-xs text-slate-400">建议正面清晰头像</span>
          </div>
          <input
            ref="videoFileInput"
            type="file"
            accept="image/*"
            class="hidden"
            @change="handleVideoFileChange"
          />
        </div>
      </div>

      <div class="mb-4">
        <div class="flex items-center justify-between gap-3 mb-2">
          <p class="text-sm font-medium text-slate-200">讲稿来源</p>
          <label class="inline-flex items-center gap-2 text-xs text-slate-300">
            <input
              v-model="videoAutoScript"
              type="checkbox"
              class="rounded border-white/20 bg-slate-900/60"
            />
            自动基于当前对话与教学计划生成
          </label>
        </div>
        <div class="grid grid-cols-3 gap-2 mb-2">
          <label class="text-xs text-slate-400">
            目标时长（秒）
            <input
              v-model.number="videoDurationSec"
              type="number"
              min="5"
              max="20"
              class="modal-textarea mt-1 w-full rounded-lg px-2 py-1 text-xs focus:outline-none"
            />
          </label>
          <label class="text-xs text-slate-400">
            分辨率
            <select
              v-model="videoResolution"
              class="modal-textarea mt-1 w-full rounded-lg px-2 py-1 text-xs focus:outline-none"
            >
              <option value="720P">720P（高清）</option>
              <option value="480P">480P（标准）</option>
            </select>
          </label>
          <label class="text-xs text-slate-400">
            修改要求（可选）
            <input
              v-model="videoEditInstruction"
              type="text"
              class="modal-textarea mt-1 w-full rounded-lg px-2 py-1 text-xs focus:outline-none"
              placeholder="例如：第2段改成例题讲解"
            />
          </label>
        </div>
        <p class="text-xs text-slate-400 mb-1">自定义台词（关闭自动讲稿时生效）</p>
        <textarea
          v-model="videoText"
          rows="3"
          class="modal-textarea w-full rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-300"
          :placeholder="videoAutoScript ? '可留空，系统将自动提取脚本' : '同学们好，今天我们来学习...'"
          :disabled="videoAutoScript"
        ></textarea>
      </div>

      <p v-if="videoDraftPayload?.microcourse_json?.scenes?.length" class="mt-1 text-xs text-emerald-300">
        已生成 {{ videoDraftPayload.microcourse_json.scenes.length }} 段分镜，可直接导出数字人微课。
      </p>

      <p v-if="videoError" class="mt-2 text-xs text-rose-300">{{ videoError }}</p>
      <div v-if="videoLoading" class="mt-3">
        <GenerationSteps :steps="videoSteps" :active-step="videoStep" />
      </div>
      <div v-if="videoUploading" class="mt-3 flex items-center gap-2 text-xs text-rose-300">
        <span class="w-4 h-4 rounded-full border-2 border-rose-200/40 border-t-rose-300 animate-spin"></span>
        <span>正在上传图片...</span>
      </div>
      <div v-if="videoLoading" class="mt-3 flex items-center gap-2 text-xs text-rose-300">
        <span class="w-4 h-4 rounded-full border-2 border-rose-200/40 border-t-rose-300 animate-spin"></span>
        <span>{{ videoStatus }}</span>
      </div>
      <div v-if="videoProgress" class="modal-progress">
        <div class="modal-progress-bar modal-progress-rose" :style="{ width: `${videoProgress}%` }"></div>
        <span>AI 正在处理数字人流程...</span>
      </div>

      <div class="mt-4 flex items-center justify-end gap-2">
        <button
          class="px-3 py-2 text-sm rounded-lg text-slate-400 hover:text-slate-200"
          @click="closeVideoModal"
          :disabled="videoLoading || videoUploading"
        >
          取消
        </button>
        <button
          class="px-4 py-2 rounded-lg bg-slate-700 text-slate-100 text-sm hover:bg-slate-600 transition disabled:opacity-60"
          @click="handleGenerateVideo"
          :disabled="videoLoading || videoUploading"
        >
          <span v-if="videoLoading" class="inline-flex items-center gap-2">
            <span class="w-4 h-4 rounded-full border-2 border-white/40 border-t-white animate-spin"></span>
            正在处理中...
          </span>
          <span v-else>生成讲稿分镜</span>
        </button>
        <button
          class="px-4 py-2 rounded-lg bg-rose-400 text-slate-900 text-sm hover:bg-rose-300 transition disabled:opacity-60"
          @click="handleExportVideo"
          :disabled="videoLoading || videoUploading || !videoDraftPayload"
        >
          <span v-if="videoLoading" class="inline-flex items-center gap-2">
            <span class="w-4 h-4 rounded-full border-2 border-white/40 border-t-white animate-spin"></span>
            正在导出...
          </span>
          <span v-else>导出数字人微课</span>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-shell {
  position: relative;
  color: #e2e8f0;
  background: transparent;
  overflow: hidden;
}

.chat-shell--elder {
  color: var(--teacher-text-primary, #1f2937);
  background: var(--teacher-main-bg, #f7f8fa);
  font-size: 15px;
  line-height: 1.65;
}

.chat-shell--elder::before,
.chat-shell--elder::after,
.chat-shell--elder .chat-bg,
.chat-shell--elder .cosmic-mist {
  opacity: 0.03;
}

.chat-shell::before,
.chat-shell::after {
  content: '';
  position: absolute;
  inset: auto -12vw 18vh auto;
  width: 74vw;
  height: 24vw;
  border: 1px solid rgba(96, 165, 250, 0.13);
  border-radius: 50%;
  background: transparent;
  opacity: 1;
  transform: rotate(-14deg);
  pointer-events: none;
}

.chat-shell::after {
  inset: auto -7vw 22vh auto;
  width: 66vw;
  height: 21vw;
  border-color: rgba(168, 85, 247, 0.14);
}

.chat-bg {
  position: absolute;
  inset: 0;
  background-image:
    radial-gradient(1px 1px at 8% 20%, rgba(191, 219, 254, 0.45), transparent 60%),
    radial-gradient(1px 1px at 37% 14%, rgba(255, 255, 255, 0.38), transparent 60%),
    radial-gradient(1px 1px at 72% 20%, rgba(147, 197, 253, 0.42), transparent 60%),
    radial-gradient(2px 2px at 87% 31%, rgba(167, 139, 250, 0.55), transparent 60%);
  opacity: 0.7;
  animation: starDrift 36s linear infinite;
  pointer-events: none;
}

.chat-bg::before,
.chat-bg::after {
  content: '';
  position: absolute;
  pointer-events: none;
}

.chat-bg::before {
  left: -8%;
  right: -8%;
  top: 24%;
  height: 44%;
  opacity: 0.38;
  background:
    radial-gradient(ellipse at 18% 56%, rgba(29, 123, 255, 0.32), transparent 24%),
    radial-gradient(ellipse at 84% 45%, rgba(176, 72, 255, 0.35), transparent 28%),
    repeating-linear-gradient(166deg, rgba(64, 141, 255, 0.18) 0 1px, transparent 1px 18px),
    repeating-linear-gradient(14deg, rgba(117, 81, 255, 0.14) 0 1px, transparent 1px 22px);
  clip-path: polygon(0 45%, 16% 29%, 33% 40%, 48% 58%, 62% 47%, 77% 33%, 100% 21%, 100% 65%, 78% 78%, 58% 72%, 42% 62%, 25% 70%, 0 82%);
  filter: drop-shadow(0 0 28px rgba(56, 189, 248, 0.18));
  animation: waveDrift 14s ease-in-out infinite;
}

.chat-bg::after {
  right: -12%;
  bottom: 8%;
  width: 68%;
  height: 34%;
  border-radius: 50%;
  border-top: 1px solid rgba(125, 211, 252, 0.14);
  border-left: 1px solid rgba(167, 139, 250, 0.1);
  transform: rotate(-11deg);
}

.chat-header {
  min-height: 64px;
  padding-right: min(360px, 34vw);
  background: transparent;
  backdrop-filter: none;
  border-bottom: 1px solid rgba(96, 165, 250, 0.04);
}

.chat-header h1 {
  font-size: 18px;
  line-height: 1.2;
}

.chat-header p {
  color: rgba(203, 213, 225, 0.82);
}

.chat-brand-cluster {
  transition: opacity 0.2s ease;
}

.chat-shell:not(.chat-shell--sidebar-collapsed):not(.chat-shell--elder) .chat-brand-cluster {
  opacity: 0;
  pointer-events: none;
}

.chat-mode-pill {
  border-radius: 999px;
  border: 1px solid rgba(148, 163, 184, 0.3);
  background: rgba(2, 6, 23, 0.76);
  color: #dbeafe;
  font-size: 11px;
  font-weight: 600;
  padding: 5px 11px;
}

.chat-shell--elder .chat-header {
  background: var(--teacher-muted-card-bg, #fafbfc);
  border-bottom-color: var(--teacher-border-color, #d8dee8);
}

.chat-shell--elder .chat-mode-pill {
  background: rgba(255, 255, 255, 0.95);
  border-color: rgba(148, 163, 184, 0.45);
  color: #334155;
  font-size: 13px;
}

.chat-shell--elder .chat-header h1 {
  font-size: 16px;
  font-weight: 700;
  color: var(--teacher-text-primary, #1f2937);
}

.chat-shell--elder .chat-header p {
  font-size: 13px;
  color: var(--teacher-text-secondary, #4b5563);
}

.chat-shell--elder .chat-layout,
.chat-shell--elder .chat-message-scroll {
  background: var(--teacher-main-bg, #f7f8fa);
}

.ai-avatar {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  background: linear-gradient(135deg, #49c5ff, #6f55ff);
  box-shadow: 0 12px 28px -16px rgba(56, 189, 248, 0.9);
}

.user-bubble {
  background: linear-gradient(135deg, #38bdf8, #22d3ee, #a78bfa);
  box-shadow: 0 16px 32px -22px rgba(56, 189, 248, 0.6);
}

.assistant-card {
  background: rgba(15, 23, 42, 0.72);
  border: 1px solid rgba(148, 163, 184, 0.2);
  box-shadow: 0 20px 34px -28px rgba(15, 23, 42, 0.6);
}

.chat-shell--elder .assistant-card {
  background: var(--teacher-card-bg, #ffffff);
  border-color: var(--teacher-border-color, #d8dee8);
  box-shadow: 0 8px 20px -18px rgba(148, 163, 184, 0.45);
  font-size: 15px;
  line-height: 1.75;
  color: var(--teacher-text-primary, #1f2937);
}

.chat-shell--elder .user-bubble {
  box-shadow: 0 10px 18px -14px rgba(37, 99, 235, 0.38);
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
  font-size: 15px;
  line-height: 1.7;
}

.micro-lesson {
  position: relative;
  width: 100%;
  max-width: 560px;
  aspect-ratio: 16 / 9;
  border-radius: 18px;
  background: linear-gradient(135deg, rgba(15, 23, 42, 0.95), rgba(2, 6, 23, 0.9));
  border: 1px solid rgba(148, 163, 184, 0.25);
  overflow: hidden;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.04), 0 18px 40px -28px rgba(15, 23, 42, 0.8);
}

.micro-lesson__board {
  position: relative;
  height: 100%;
  padding: 20px 18px 190px;
  background:
    linear-gradient(135deg, rgba(15, 23, 42, 0.85), rgba(2, 6, 23, 0.95)),
    repeating-linear-gradient(
      0deg,
      rgba(148, 163, 184, 0.05),
      rgba(148, 163, 184, 0.05) 24px,
      transparent 24px,
      transparent 48px
    );
}

.micro-lesson__title {
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(226, 232, 240, 0.85);
  margin-bottom: 12px;
}

.micro-lesson__list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 8px;
  color: #e2e8f0;
  font-size: 15px;
  font-weight: 500;
}

.micro-lesson__list li {
  display: flex;
  align-items: center;
  gap: 8px;
}

.micro-lesson__list li::before {
  content: '▸';
  color: rgba(56, 189, 248, 0.9);
  font-size: 14px;
}

.micro-lesson__scenes {
  margin-top: 10px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.micro-lesson__scene {
  border-radius: 10px;
  border: 1px solid rgba(148, 163, 184, 0.28);
  background: rgba(15, 23, 42, 0.55);
  padding: 6px 8px;
}

.micro-lesson__scene-head {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 11px;
  color: rgba(226, 232, 240, 0.88);
}

.micro-lesson__scene-subtitle {
  margin: 4px 0 0;
  font-size: 11px;
  color: rgba(191, 219, 254, 0.9);
  line-height: 1.45;
}

.micro-lesson__video {
  position: absolute;
  right: 16px;
  bottom: 16px;
  width: 124px;
  height: 168px;
  border-radius: 14px;
  border: 2px solid rgba(248, 250, 252, 0.9);
  box-shadow: 0 18px 30px -20px rgba(15, 23, 42, 0.9);
  overflow: hidden;
  background: #020617;
}

.micro-lesson__video-media {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 0;
}

.micro-lesson__prompt {
  font-size: 11px;
  color: rgba(148, 163, 184, 0.9);
}

.micro-lesson__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.micro-lesson__action {
  padding: 6px 12px;
  border-radius: 999px;
  border: none;
  background: rgba(56, 189, 248, 0.95);
  color: #0f172a;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
  text-decoration: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.micro-lesson__action:hover {
  transform: translateY(-1px);
  box-shadow: 0 10px 20px -16px rgba(56, 189, 248, 0.8);
}

.micro-lesson__action.is-secondary {
  background: rgba(148, 163, 184, 0.2);
  color: rgba(226, 232, 240, 0.9);
}

.micro-lesson__action.is-ghost {
  background: rgba(15, 23, 42, 0.6);
  color: rgba(226, 232, 240, 0.85);
  border: 1px solid rgba(148, 163, 184, 0.3);
}

.chat-shell--elder .micro-lesson {
  background: var(--teacher-card-bg, #ffffff);
  border-color: var(--teacher-border-color, #d8dee8);
}

.chat-shell--elder .micro-lesson__board {
  background: linear-gradient(135deg, #f8fafc, #eef2f7);
}

.chat-shell--elder .micro-lesson__title,
.chat-shell--elder .micro-lesson__list,
.chat-shell--elder .micro-lesson__scene-head,
.chat-shell--elder .micro-lesson__scene-subtitle {
  color: var(--teacher-text-primary, #1f2937);
}

.game-card {
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid rgba(148, 163, 184, 0.2);
  background: rgba(15, 23, 42, 0.8);
  box-shadow: 0 18px 32px -26px rgba(15, 23, 42, 0.8);
}

.game-frame {
  width: 100%;
  height: 280px;
  border: none;
  background: #0f172a;
}

.game-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.game-action {
  padding: 6px 12px;
  border-radius: 999px;
  border: none;
  background: rgba(16, 185, 129, 0.9);
  color: #0f172a;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.game-action:hover {
  transform: translateY(-1px);
  box-shadow: 0 10px 20px -16px rgba(16, 185, 129, 0.7);
}

.game-action.is-secondary {
  background: rgba(148, 163, 184, 0.2);
  color: rgba(226, 232, 240, 0.9);
}

.welcome-state {
  position: relative;
  max-width: 1140px;
  margin: 0 auto;
  padding-top: 0;
  isolation: isolate;
}

.welcome-orbit {
  position: relative;
  width: 132px;
  height: 132px;
  margin-bottom: 24px;
  border-radius: 30px;
  border: 1px solid rgba(168, 180, 255, 0.5);
  background: linear-gradient(145deg, rgba(80, 89, 200, 0.34), rgba(20, 27, 80, 0.45));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.14), 0 24px 56px -32px rgba(83, 110, 255, 0.85);
  animation: welcomeFloat 5.8s ease-in-out infinite;
}

.welcome-ring {
  position: absolute;
  inset: 22px;
  border: 1px solid rgba(125, 211, 252, 0.14);
  border-radius: 24px;
  transform: none;
  box-shadow: inset 0 0 32px rgba(56, 189, 248, 0.08);
}

.welcome-ring--wide {
  inset: -1px;
  border-color: rgba(168, 85, 247, 0.22);
}

.welcome-tile {
  position: absolute;
  top: 40px;
  width: 34px;
  height: 48px;
  border-radius: 7px;
  transform: none;
  border: 1px solid rgba(255, 255, 255, 0.34);
  box-shadow: 0 18px 32px -18px rgba(56, 189, 248, 0.9), inset 0 1px 12px rgba(255, 255, 255, 0.24);
}

.welcome-tile--left {
  left: 44px;
  top: 36px;
  background: linear-gradient(145deg, #68f2a2, #4c7cff);
}

.welcome-tile--middle {
  left: 58px;
  top: 46px;
  background: linear-gradient(145deg, #ff5fa8, #6f65ff);
}

.welcome-tile--right {
  left: 72px;
  top: 56px;
  background: linear-gradient(145deg, #62d8ff, #6674ff);
}

.welcome-title {
  font-size: clamp(34px, 4vw, 50px) !important;
  font-weight: 800 !important;
  letter-spacing: 0;
  line-height: 1.1;
}

.welcome-title::first-letter {
  color: #f8fafc;
}

.welcome-title {
  background: linear-gradient(100deg, #ffffff 0%, #ffffff 34%, #35d5ff 44%, #a9b8ff 72%, #e267f3 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent !important;
}

.welcome-copy {
  max-width: 700px !important;
  color: rgba(226, 232, 240, 0.86) !important;
  font-size: 16px !important;
  line-height: 1.7;
  margin-bottom: 38px !important;
}

.welcome-subtitle {
  margin: 8px 0 18px;
  color: rgba(226, 232, 240, 0.9);
  font-size: 20px;
  font-weight: 600;
}

.welcome-grid {
  max-width: 1070px !important;
  grid-template-columns: repeat(4, minmax(0, 1fr)) !important;
  gap: 16px !important;
}

.welcome-card {
  position: relative;
  min-height: 150px;
  text-align: left !important;
  border-radius: 14px !important;
  padding: 24px 26px !important;
  display: grid;
  grid-template-columns: 54px minmax(0, 1fr);
  align-items: start;
  column-gap: 18px;
  background: linear-gradient(145deg, rgba(46, 61, 143, 0.36), rgba(12, 24, 65, 0.54));
  border: 1px solid rgba(148, 163, 184, 0.26) !important;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.08), 0 22px 44px -34px rgba(30, 64, 175, 0.82);
  backdrop-filter: blur(18px);
  overflow: hidden;
}

.welcome-card:nth-child(3) {
  background: linear-gradient(145deg, rgba(20, 143, 145, 0.26), rgba(12, 24, 65, 0.54));
}

.welcome-card:nth-child(4) {
  background: linear-gradient(145deg, rgba(143, 51, 150, 0.28), rgba(29, 19, 70, 0.54));
}

.welcome-card-icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  border-radius: 14px;
  color: white;
  font-size: 22px;
  font-weight: 800;
  margin-bottom: 0;
  box-shadow: 0 12px 30px -18px currentColor, inset 0 1px 0 rgba(255, 255, 255, 0.2);
}

.welcome-card-icon--blue {
  background: linear-gradient(145deg, #61caff, #3266ff);
}

.welcome-card-icon--purple {
  background: linear-gradient(145deg, #c084fc, #6d5dfc);
}

.welcome-card-icon--green {
  background: linear-gradient(145deg, #86efac, #22c55e);
}

.welcome-card-icon--orange {
  background: linear-gradient(145deg, #fbbf24, #fb7185);
}

.welcome-card .text-sm {
  font-size: 17px;
  font-weight: 800;
  line-height: 1.35;
}

.welcome-card .text-xs {
  font-size: 14px;
  line-height: 1.6;
  color: rgba(203, 213, 225, 0.84) !important;
}

.welcome-card-arrow {
  position: absolute;
  left: 94px;
  bottom: 18px;
  display: inline-grid;
  place-items: center;
  width: 38px;
  height: 28px;
  border-radius: 999px;
  color: rgba(226, 232, 240, 0.92);
  background: rgba(255, 255, 255, 0.09);
  font-size: 20px;
  transition: transform 0.2s ease, background 0.2s ease;
}

.welcome-card:hover {
  transform: translateY(-4px);
  border-color: rgba(125, 211, 252, 0.6);
  box-shadow: 0 20px 40px -28px rgba(56, 189, 248, 0.45);
}

.welcome-card:hover .welcome-card-arrow {
  transform: translateX(4px);
  background: rgba(125, 211, 252, 0.16);
}

@media (max-width: 1180px) {
  .welcome-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr)) !important;
    max-width: 680px !important;
  }
}

@media (max-width: 640px) {
  .chat-header {
    min-height: 82px;
    padding-right: 14px;
  }

  .chat-message-scroll {
    padding: 20px 16px 24px !important;
  }

  .welcome-state {
    height: auto !important;
    min-height: 0 !important;
    justify-content: flex-start !important;
    padding-top: 6px !important;
    padding-bottom: 16px !important;
  }

  .welcome-orbit {
    width: 142px;
    height: 104px;
    margin-bottom: 10px;
  }

  .welcome-ring {
    left: 18px;
    bottom: 15px;
    width: 104px;
    height: 40px;
  }

  .welcome-ring--wide {
    left: 2px;
    bottom: 8px;
    width: 138px;
    height: 56px;
  }

  .welcome-tile {
    top: 10px;
    width: 36px;
    height: 58px;
    border-radius: 11px;
  }

  .welcome-tile--left {
    left: 48px;
  }

  .welcome-tile--right {
    left: 80px;
    top: 27px;
    height: 48px;
  }

  .welcome-title {
    font-size: 26px !important;
  }

  .welcome-copy {
    font-size: 14px !important;
    margin-bottom: 22px !important;
  }

  .welcome-grid {
    grid-template-columns: 1fr !important;
  }

  .chat-input-zone {
    width: calc(100% - 32px);
    margin-bottom: 16px;
  }
}

.input-resizer {
  height: 12px;
  cursor: row-resize;
  background: linear-gradient(90deg, rgba(56, 189, 248, 0.35), rgba(236, 72, 153, 0.35));
  display: flex;
  align-items: center;
  justify-content: center;
}

.resizer-handle {
  width: 64px;
  height: 4px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.25);
  box-shadow: 0 0 12px rgba(125, 211, 252, 0.6);
}

.chat-progress {
  position: relative;
  height: 26px;
  margin: 0 16px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.7);
  border: 1px solid rgba(148, 163, 184, 0.2);
  overflow: hidden;
  font-size: 11px;
  display: flex;
  align-items: center;
  padding-left: 12px;
  color: #e2e8f0;
  box-shadow: 0 12px 24px -20px rgba(15, 23, 42, 0.7);
}

.chat-shell--elder .chat-progress {
  background: var(--teacher-card-bg, #ffffff);
  border-color: var(--teacher-border-color, #d8dee8);
  color: var(--teacher-text-secondary, #4b5563);
  box-shadow: 0 10px 20px -18px rgba(148, 163, 184, 0.45);
  height: 30px;
  font-size: 13px;
}

.chat-progress-bar {
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, rgba(56, 189, 248, 0.45), rgba(236, 72, 153, 0.45));
  transition: width 0.4s ease;
  z-index: 0;
}

.chat-progress span {
  position: relative;
  z-index: 1;
}

.web-resource-radar {
  flex-shrink: 0;
  width: min(1280px, calc(100% - 64px));
  margin: 10px auto 0;
  border: 1px solid rgba(91, 210, 255, 0.26);
  border-radius: 20px;
  background:
    radial-gradient(circle at 8% 0, rgba(56, 189, 248, 0.2), transparent 38%),
    linear-gradient(145deg, rgba(12, 28, 73, 0.82), rgba(14, 12, 52, 0.72));
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.09),
    0 24px 58px -44px rgba(56, 189, 248, 0.9);
  backdrop-filter: blur(20px);
  overflow: hidden;
}

.web-resource-radar__head {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding: 14px 16px;
  align-items: center;
}

.web-resource-radar__head > div:first-child {
  min-width: 0;
}

.web-resource-radar__eyebrow {
  display: block;
  color: #5deaff;
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0.22em;
  margin-bottom: 4px;
}

.web-resource-radar strong {
  display: block;
  color: #f8fbff;
  font-size: 15px;
  line-height: 1.35;
}

.web-resource-radar p {
  margin: 4px 0 0;
  color: rgba(219, 234, 254, 0.66);
  font-size: 12px;
  line-height: 1.55;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.web-resource-radar__actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.web-resource-radar__actions button {
  height: 34px;
  padding: 0 12px;
  border-radius: 12px;
  border: 1px solid rgba(125, 211, 252, 0.28);
  background: rgba(7, 13, 42, 0.62);
  color: rgba(239, 246, 255, 0.9);
  font-size: 12px;
  font-weight: 800;
  transition: transform 0.18s ease, border-color 0.18s ease, background 0.18s ease;
}

.web-resource-radar__actions button:hover:not(:disabled) {
  transform: translateY(-1px);
  border-color: rgba(94, 234, 212, 0.62);
  background: rgba(14, 116, 144, 0.22);
}

.web-resource-radar__actions button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.web-resource-radar__list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 10px;
  padding: 0 14px 14px;
}

.web-resource-item {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  gap: 10px;
  min-width: 0;
  border-radius: 16px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  background: rgba(5, 10, 34, 0.56);
  padding: 12px;
}

.web-resource-item__index {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  color: #fff;
  font-size: 12px;
  font-weight: 900;
  background: linear-gradient(145deg, #22d3ee, #7c3aed);
}

.web-resource-item__body {
  min-width: 0;
}

.web-resource-item__title {
  display: block;
  width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  color: #eef6ff;
  text-align: left;
  font-size: 13px;
  font-weight: 850;
  line-height: 1.45;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.web-resource-item__title:hover {
  color: #67e8f9;
}

.web-resource-item p,
.web-resource-item small,
.web-resource-item span {
  display: block;
  white-space: normal;
}

.web-resource-item p {
  color: rgba(219, 234, 254, 0.74);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.web-resource-item small {
  margin-top: 5px;
  color: rgba(125, 245, 205, 0.78);
  font-size: 11px;
  line-height: 1.55;
}

.web-resource-item span {
  margin-top: 6px;
  color: rgba(148, 163, 184, 0.7);
  font-size: 11px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-layout {
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
}

.chat-input-zone {
  flex-shrink: 0;
  margin-top: auto;
  width: min(1280px, calc(100% - 64px));
  margin-left: auto;
  margin-right: auto;
  margin-bottom: 28px;
  border-radius: 20px;
  border: 1px solid rgba(148, 163, 184, 0.28);
  background: linear-gradient(145deg, rgba(30, 41, 91, 0.68), rgba(24, 19, 62, 0.62));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.08), 0 26px 54px -38px rgba(30, 64, 175, 0.9);
  backdrop-filter: blur(20px);
  overflow: visible;
}

.modal-card {
  background: rgba(15, 23, 42, 0.75);
  border: 1px solid rgba(148, 163, 184, 0.2);
  box-shadow: 0 24px 50px -32px rgba(15, 23, 42, 0.8);
  backdrop-filter: blur(18px);
  color: #e2e8f0;
}

.chat-shell--elder .modal-card {
  background: var(--teacher-card-bg, #ffffff);
  border-color: var(--teacher-border-color, #d8dee8);
  color: var(--teacher-text-primary, #1f2937);
  font-size: 15px;
  line-height: 1.7;
  box-shadow: 0 20px 40px -26px rgba(148, 163, 184, 0.55);
  backdrop-filter: none;
}

.modal-textarea {
  border: 1px solid rgba(148, 163, 184, 0.25);
  background: rgba(2, 6, 23, 0.6);
  color: #e2e8f0;
}

.chat-shell--elder .modal-textarea {
  border-color: var(--teacher-border-color, #d8dee8);
  background: var(--teacher-card-bg, #ffffff);
  color: var(--teacher-text-primary, #1f2937);
}

.chat-shell--elder .text-slate-100,
.chat-shell--elder .text-slate-200,
.chat-shell--elder .text-slate-300,
.chat-shell--elder .text-slate-400,
.chat-shell--elder .text-slate-500,
.chat-shell--elder .text-cyan-100,
.chat-shell--elder .text-cyan-200,
.chat-shell--elder .text-emerald-100,
.chat-shell--elder .text-amber-100,
.chat-shell--elder .text-indigo-100 {
  color: var(--teacher-text-secondary, #4b5563) !important;
}

.chat-shell--elder .text-cyan-100\/90,
.chat-shell--elder .text-cyan-100\/80,
.chat-shell--elder .text-cyan-100\/70,
.chat-shell--elder .text-cyan-200\/90,
.chat-shell--elder .text-slate-400\/90 {
  color: var(--teacher-text-secondary, #4b5563) !important;
}

.chat-shell--elder .text-slate-100.font-semibold,
.chat-shell--elder .text-slate-100.font-medium,
.chat-shell--elder .text-slate-100 {
  color: var(--teacher-text-primary, #1f2937) !important;
}

.chat-shell--elder .bg-slate-900\/60,
.chat-shell--elder .bg-slate-900\/70,
.chat-shell--elder .bg-slate-900\/40,
.chat-shell--elder .bg-slate-950\/35,
.chat-shell--elder .bg-slate-950\/40,
.chat-shell--elder .bg-slate-950\/50 {
  background: var(--teacher-muted-card-bg, #fafbfc) !important;
}

.chat-shell--elder .border-white\/10,
.chat-shell--elder .border-white\/15,
.chat-shell--elder .border-white\/20 {
  border-color: var(--teacher-border-color, #d8dee8) !important;
}

.chat-shell--elder .bg-slate-950\/80,
.chat-shell--elder .bg-slate-950\/85 {
  background: rgba(31, 41, 55, 0.32) !important;
}

.chat-shell--elder .markdown-body {
  font-size: 16px;
  line-height: 1.8;
  color: var(--teacher-text-primary, #1f2937);
}

.chat-shell--elder .markdown-body h1,
.chat-shell--elder .markdown-body h2,
.chat-shell--elder .markdown-body h3,
.chat-shell--elder .markdown-body h4 {
  color: var(--teacher-text-primary, #1f2937);
}

.chat-shell--elder .markdown-body a {
  color: #2563eb;
}

.chat-shell--elder .welcome-card {
  border-color: var(--teacher-border-color, #d8dee8);
  background: var(--teacher-card-bg, #ffffff);
}

.chat-shell--elder .welcome-card .text-sm {
  font-size: 15px;
  font-weight: 700;
}

.chat-shell--elder .welcome-card .text-xs {
  font-size: 13px;
}

.chat-shell--elder .modal-card .bg-sky-400,
.chat-shell--elder .modal-card .bg-sky-500,
.chat-shell--elder .modal-card .bg-cyan-400,
.chat-shell--elder .modal-card .bg-amber-400,
.chat-shell--elder .modal-card .bg-rose-400 {
  background: #2563eb !important;
  color: #ffffff !important;
}

.chat-shell--elder .modal-card .bg-sky-400:hover,
.chat-shell--elder .modal-card .bg-sky-500:hover,
.chat-shell--elder .modal-card .bg-cyan-400:hover,
.chat-shell--elder .modal-card .bg-amber-400:hover,
.chat-shell--elder .modal-card .bg-rose-400:hover {
  background: #1d4ed8 !important;
}

.teacher-step-list {
  margin: 0;
  padding-left: 1rem;
  display: grid;
  gap: 3px;
  line-height: 1.5;
}

.study-pack-modal {
  overflow: hidden;
}

.study-pack-side,
.study-pack-main {
  backdrop-filter: blur(12px);
}

.study-pack-tab-btn {
  border-radius: 999px;
  border: 1px solid rgba(148, 163, 184, 0.28);
  padding: 6px 12px;
  font-size: 11px;
  color: #cbd5e1;
  transition: all 0.2s ease;
}

.study-pack-tab-btn:hover {
  background: rgba(255, 255, 255, 0.08);
}

.study-pack-tab-btn--active {
  border-color: rgba(94, 234, 212, 0.7);
  background: rgba(20, 184, 166, 0.24);
  color: #d1fae5;
}

.study-pack-panel {
  border-radius: 16px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  background: rgba(2, 6, 23, 0.42);
  padding: 14px;
}

.study-pack-flash-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.study-pack-flip-card {
  perspective: 1200px;
  min-height: 184px;
  border: none;
  background: transparent;
  text-align: left;
}

.study-pack-flip-inner {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 184px;
  transform-style: preserve-3d;
  transition: transform 0.65s ease;
}

.study-pack-flip-inner.is-flipped {
  transform: rotateY(180deg);
}

.study-pack-flip-face {
  position: absolute;
  inset: 0;
  border-radius: 18px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  backface-visibility: hidden;
}

.study-pack-flip-front {
  color: #e2e8f0;
}

.study-pack-flip-back {
  background: rgba(15, 23, 42, 0.88);
  border: 1px solid rgba(148, 163, 184, 0.32);
  transform: rotateY(180deg);
  color: #e2e8f0;
}

.study-pack-flip-index {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  opacity: 0.86;
}

.study-pack-flip-text {
  margin-top: 10px;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.55;
}

.study-pack-flip-tip {
  margin-top: 10px;
  font-size: 11px;
  opacity: 0.8;
}

.study-pack-section-title {
  font-size: 13px;
  font-weight: 700;
  color: #cbd5e1;
  margin-bottom: 10px;
}

.study-pack-quiz-card,
.study-pack-analysis-card {
  border-radius: 14px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  background: rgba(15, 23, 42, 0.58);
  padding: 12px;
}

.study-pack-quiz-head {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.study-pack-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 11px;
  font-weight: 700;
  color: #0f172a;
  background: rgba(125, 211, 252, 0.9);
}

.study-pack-quiz-text {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: #e2e8f0;
  line-height: 1.55;
}

.study-pack-quiz-options {
  margin-top: 9px;
  padding-left: 1.2rem;
  font-size: 12px;
  color: #cbd5e1;
  line-height: 1.55;
}

.study-pack-quiz-options li + li {
  margin-top: 4px;
}

.study-pack-analysis-head {
  margin: 0;
  font-size: 12px;
  font-weight: 700;
  color: #fef3c7;
}

.study-pack-analysis-row {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: #e2e8f0;
}

.study-pack-analysis-row span:first-child {
  font-weight: 700;
  color: #86efac;
}

.chat-shell--elder .study-pack-tab-btn {
  border-color: var(--teacher-border-color, #d8dee8);
  color: var(--teacher-text-secondary, #4b5563);
}

.chat-shell--elder .study-pack-tab-btn--active {
  border-color: #93c5fd;
  background: rgba(59, 130, 246, 0.14);
  color: #1d4ed8;
}

.chat-shell--elder .study-pack-panel,
.chat-shell--elder .study-pack-quiz-card,
.chat-shell--elder .study-pack-analysis-card,
.chat-shell--elder .study-pack-flip-back {
  background: #ffffff;
  border-color: var(--teacher-border-color, #d8dee8);
}

.chat-shell--elder .study-pack-section-title,
.chat-shell--elder .study-pack-quiz-text,
.chat-shell--elder .study-pack-analysis-row,
.chat-shell--elder .study-pack-flip-text {
  color: var(--teacher-text-primary, #1f2937);
}

.chat-shell--elder .study-pack-badge {
  color: #ffffff;
  background: #2563eb;
}

.chat-shell--elder .study-pack-analysis-head {
  color: #374151;
}

.chat-shell--elder .study-pack-analysis-row span:first-child {
  color: #2563eb;
}

@media (max-width: 1024px) {
  .study-pack-modal {
    max-height: 100vh;
  }
}

.modal-textarea::placeholder {
  color: rgba(148, 163, 184, 0.7);
}

.modal-progress {
  position: relative;
  margin-top: 14px;
  height: 24px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.7);
  border: 1px solid rgba(148, 163, 184, 0.2);
  overflow: hidden;
  display: flex;
  align-items: center;
  padding-left: 12px;
  font-size: 11px;
  color: #e2e8f0;
}

.modal-progress-bar {
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, rgba(56, 189, 248, 0.45), rgba(129, 140, 248, 0.45));
  transition: width 0.4s ease;
  z-index: 0;
}

.modal-progress-amber {
  background: linear-gradient(90deg, rgba(251, 191, 36, 0.5), rgba(251, 146, 60, 0.45));
}

.modal-progress-rose {
  background: linear-gradient(90deg, rgba(244, 114, 182, 0.5), rgba(248, 113, 113, 0.45));
}

.modal-progress span {
  position: relative;
  z-index: 1;
}

/* Premium lesson-plan workbench */
.plan-modal-overlay {
  padding: 28px !important;
  background:
    radial-gradient(circle at 22% 18%, rgba(28, 158, 255, 0.18), transparent 34%),
    radial-gradient(circle at 78% 12%, rgba(124, 58, 237, 0.18), transparent 38%),
    rgba(1, 6, 24, 0.76) !important;
  backdrop-filter: blur(18px) saturate(118%);
}

.plan-workbench {
  position: relative;
  display: flex;
  flex-direction: column;
  width: min(1500px, calc(100vw - 64px)) !important;
  max-width: none !important;
  height: min(880px, calc(100vh - 56px));
  max-height: none !important;
  padding: 0 !important;
  overflow: hidden !important;
  border-radius: 26px !important;
  border: 1px solid rgba(126, 164, 255, 0.28) !important;
  background:
    radial-gradient(circle at 22% 0%, rgba(46, 131, 255, 0.16), transparent 34%),
    radial-gradient(circle at 76% 8%, rgba(187, 83, 255, 0.15), transparent 36%),
    linear-gradient(145deg, rgba(9, 18, 51, 0.96), rgba(4, 9, 31, 0.96) 58%, rgba(12, 8, 36, 0.96)) !important;
  box-shadow:
    0 34px 110px -52px rgba(0, 0, 0, 0.92),
    0 0 0 1px rgba(255, 255, 255, 0.04) inset,
    0 1px 0 rgba(255, 255, 255, 0.1) inset;
}

.plan-workbench,
.plan-workbench * {
  box-sizing: border-box;
}

.plan-workbench::before,
.plan-workbench::after {
  content: '';
  position: absolute;
  pointer-events: none;
}

.plan-workbench::before {
  inset: 0;
  background-image:
    radial-gradient(circle, rgba(255, 255, 255, 0.4) 0 1px, transparent 1.8px),
    radial-gradient(circle, rgba(56, 189, 248, 0.5) 0 1px, transparent 1.8px);
  background-size: 170px 170px, 240px 240px;
  background-position: 20px 30px, 90px 110px;
  opacity: 0.2;
}

.plan-workbench::after {
  left: 0;
  right: 0;
  top: 0;
  height: 168px;
  background:
    linear-gradient(90deg, rgba(38, 195, 255, 0.34), rgba(124, 58, 237, 0.18), transparent 76%),
    radial-gradient(ellipse at 18% 0%, rgba(56, 189, 248, 0.28), transparent 64%);
  opacity: 0.64;
  mask-image: linear-gradient(to bottom, #000, transparent);
}

.plan-workbench__header,
.plan-workbench__sub,
.plan-workbench__tabs,
.plan-status-ribbon,
.plan-workbench__grid {
  position: relative;
  z-index: 1;
}

.plan-workbench__header {
  margin: 0 !important;
  padding: 26px 30px 10px;
}

.plan-workbench__title {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-size: 24px !important;
  font-weight: 800 !important;
  letter-spacing: 0;
  color: #f8fbff !important;
  text-shadow: 0 18px 46px rgba(56, 189, 248, 0.32);
}

.plan-workbench__title::first-letter {
  color: #ff6bd7;
}

.plan-workbench__close {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 14px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  background: rgba(6, 13, 43, 0.48);
  color: rgba(226, 232, 240, 0.76) !important;
  font-size: 20px;
}

.plan-workbench__close:hover {
  border-color: rgba(125, 211, 252, 0.42);
  color: #fff !important;
  background: rgba(30, 64, 175, 0.28);
}

.plan-workbench__sub {
  max-width: 1140px;
  margin: 0 !important;
  padding: 0 30px 16px;
  color: rgba(203, 213, 225, 0.82) !important;
  font-size: 13px !important;
  line-height: 1.75;
}

.plan-workbench__tabs {
  margin: 0 30px 14px !important;
  padding: 6px;
  width: fit-content;
  border-radius: 999px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  background: rgba(3, 8, 28, 0.44);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.06);
  backdrop-filter: blur(18px);
}

.plan-stage-btn {
  min-height: 34px;
  padding: 0 16px !important;
  border-radius: 999px !important;
  font-size: 13px !important;
  font-weight: 700;
  border-color: transparent !important;
  color: rgba(203, 213, 225, 0.76) !important;
}

.plan-stage-btn.bg-cyan-300\/20 {
  color: #fff !important;
  background: linear-gradient(100deg, rgba(39, 211, 255, 0.92), rgba(62, 126, 255, 0.9) 52%, rgba(124, 58, 237, 0.94)) !important;
  box-shadow: 0 18px 34px -22px rgba(56, 189, 248, 0.9);
}

.plan-status-ribbon {
  display: grid;
  grid-template-columns: 150px minmax(0, 1fr);
  align-items: center;
  gap: 14px;
  min-height: 74px;
  margin: 0 30px 18px !important;
  padding: 15px 18px !important;
  border-radius: 20px !important;
  border-color: rgba(96, 165, 250, 0.18) !important;
  background:
    linear-gradient(90deg, rgba(15, 43, 99, 0.62), rgba(7, 14, 45, 0.42)) !important;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.08);
}

.plan-status-ribbon p {
  margin: 0 !important;
}

.plan-status-ribbon p:first-child {
  color: rgba(148, 203, 255, 0.84) !important;
  font-size: 12px !important;
  font-weight: 800;
  letter-spacing: 0.14em;
}

.plan-status-ribbon p:nth-child(2) {
  font-size: 18px !important;
  color: #eef6ff !important;
}

.plan-status-ribbon p:nth-child(3) {
  grid-column: 2;
  color: rgba(203, 213, 225, 0.68) !important;
}

.plan-workbench__grid {
  grid-template-columns: minmax(360px, 420px) minmax(0, 1fr) !important;
  gap: 18px !important;
  height: calc(100% - 196px);
  padding: 0 30px 30px;
  min-height: 0;
}

.plan-workbench__side,
.plan-workbench__main,
.plan-workbench .overflow-y-auto {
  scrollbar-width: thin;
  scrollbar-color: rgba(56, 189, 248, 0.78) rgba(4, 10, 34, 0.32);
}

.plan-workbench__side {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
  overflow-y: auto;
  padding: 0 10px 20px 0;
  overscroll-behavior: contain;
}

.plan-workbench__main {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 0 !important;
  height: 100%;
  padding: 20px 22px 18px !important;
  overflow: hidden;
  border-radius: 22px !important;
  border-color: rgba(148, 163, 184, 0.18) !important;
  background:
    radial-gradient(circle at 92% 4%, rgba(124, 58, 237, 0.12), transparent 38%),
    linear-gradient(145deg, rgba(10, 22, 61, 0.74), rgba(4, 9, 31, 0.58)) !important;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.07);
}

.plan-workbench__main::before,
.plan-workbench__main::after {
  content: '';
  position: absolute;
  pointer-events: none;
}

.plan-workbench__main::before {
  inset: 0;
  background:
    radial-gradient(circle at 72% 22%, rgba(56, 189, 248, 0.08), transparent 34%),
    repeating-linear-gradient(0deg, rgba(96, 165, 250, 0.035) 0 1px, transparent 1px 48px),
    repeating-linear-gradient(90deg, rgba(124, 58, 237, 0.032) 0 1px, transparent 1px 56px);
  opacity: 0.65;
}

.plan-workbench__main::after {
  right: -18%;
  bottom: -28%;
  width: 72%;
  height: 52%;
  border-radius: 50%;
  border: 1px solid rgba(56, 189, 248, 0.1);
  box-shadow:
    0 0 0 36px rgba(56, 189, 248, 0.018),
    0 0 0 78px rgba(124, 58, 237, 0.014);
  transform: rotate(-8deg);
}

.plan-workbench__main > * {
  position: relative;
  z-index: 1;
}

.plan-panel,
.plan-workbench .rounded-xl.border,
.plan-workbench .rounded-lg.border {
  border-color: rgba(148, 163, 184, 0.18) !important;
  background:
    linear-gradient(145deg, rgba(16, 35, 78, 0.62), rgba(5, 11, 35, 0.52)) !important;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.055);
}

.plan-panel,
.plan-workbench .rounded-xl.border {
  border-radius: 20px !important;
}

.plan-panel {
  flex-shrink: 0;
  padding: 14px !important;
}

.plan-summary-card,
.plan-health-card,
.plan-meta-card {
  width: 100%;
}

.plan-panel--primary {
  border-color: rgba(56, 189, 248, 0.28) !important;
  background:
    radial-gradient(circle at 4% 6%, rgba(56, 189, 248, 0.16), transparent 44%),
    linear-gradient(145deg, rgba(9, 42, 75, 0.72), rgba(5, 11, 35, 0.58)) !important;
}

.plan-workbench textarea,
.plan-workbench input,
.plan-workbench select,
.plan-workbench .modal-textarea {
  border: 1px solid rgba(148, 163, 184, 0.22) !important;
  background: rgba(3, 8, 28, 0.62) !important;
  color: #e8f0ff !important;
  line-height: 1.45 !important;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.045);
}

.plan-workbench textarea:focus,
.plan-workbench input:focus,
.plan-workbench select:focus,
.plan-workbench .modal-textarea:focus {
  border-color: rgba(56, 189, 248, 0.72) !important;
  box-shadow:
    0 0 0 3px rgba(56, 189, 248, 0.12),
    inset 0 1px 0 rgba(255, 255, 255, 0.075) !important;
}

.plan-workbench button.bg-cyan-400,
.plan-workbench button.bg-sky-400,
.plan-workbench .bg-cyan-300.text-slate-900 {
  color: #fff !important;
  background: linear-gradient(100deg, #2bd4ff, #347cff 48%, #7b2cff) !important;
  box-shadow: 0 18px 38px -24px rgba(56, 189, 248, 0.88);
}

.plan-workbench button.border {
  border-color: rgba(148, 163, 184, 0.22) !important;
}

.plan-workbench .text-cyan-100,
.plan-workbench .text-cyan-200,
.plan-workbench .text-sky-100,
.plan-workbench .text-indigo-100 {
  color: #c9efff !important;
}

.plan-workbench .text-slate-100,
.plan-workbench .text-slate-200 {
  color: #edf5ff !important;
}

.plan-workbench .text-slate-300,
.plan-workbench .text-slate-400,
.plan-workbench .text-slate-500 {
  color: rgba(203, 213, 225, 0.68) !important;
}

.plan-workbench .list-disc li::marker {
  color: rgba(56, 189, 248, 0.84);
}

.plan-workbench .line-clamp-2,
.plan-workbench .line-clamp-3 {
  overflow: hidden;
}

@media (max-width: 1180px) {
  .plan-modal-overlay {
    padding: 16px !important;
  }

  .plan-workbench {
    width: calc(100vw - 32px) !important;
    height: calc(100vh - 32px);
  }

  .plan-workbench__grid {
    grid-template-columns: 1fr !important;
    overflow-y: auto;
  }

  .plan-workbench__main {
    min-height: 580px !important;
  }
}

.plan-workbench__eyebrow {
  margin: 0 0 8px;
  color: rgba(103, 232, 249, 0.82);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.28em;
}

.plan-workbench__header {
  display: flex !important;
  align-items: flex-start;
  justify-content: space-between;
  gap: 22px;
}

.plan-workbench__header .plan-workbench__sub {
  max-width: 980px;
  margin: 10px 0 0 !important;
  padding: 0 !important;
}

.plan-workbench__headerActions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.plan-dev-badge {
  border: 1px solid rgba(103, 232, 249, 0.28);
  border-radius: 999px;
  background: rgba(8, 145, 178, 0.12);
  color: #cffafe;
  padding: 6px 10px;
  font-size: 11px;
  font-weight: 700;
}

.plan-overview-grid {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(240px, 1.35fr) repeat(3, minmax(150px, 0.65fr));
  gap: 12px;
  padding: 0 30px 18px;
}

.plan-overview-card {
  min-height: 92px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 18px;
  background:
    radial-gradient(circle at 18% 0%, rgba(56, 189, 248, 0.14), transparent 48%),
    linear-gradient(145deg, rgba(13, 31, 72, 0.76), rgba(4, 9, 31, 0.56));
  padding: 16px;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.07);
}

.plan-overview-card__label {
  display: block;
  margin-bottom: 8px;
  color: rgba(203, 213, 225, 0.62);
  font-size: 11px;
  font-weight: 700;
}

.plan-overview-card strong {
  display: block;
  color: #f8fbff;
  font-size: 22px;
  line-height: 1.15;
}

.plan-overview-card p {
  margin: 8px 0 0;
  color: rgba(203, 213, 225, 0.7);
  font-size: 12px;
}

.plan-mini-bar,
.plan-health-meter {
  height: 7px;
  overflow: hidden;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.76);
}

.plan-mini-bar {
  margin-top: 12px;
}

.plan-mini-bar i,
.plan-health-meter i {
  display: block;
  height: 100%;
  border-radius: inherit;
}

.plan-workbench__body {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: 330px minmax(0, 1fr);
  gap: 18px;
  flex: 1;
  height: auto !important;
  min-height: 0;
  overflow: hidden;
  padding: 0 30px 30px;
}

.plan-side-nav {
  display: grid;
  gap: 10px;
}

.plan-side-nav__item {
  width: 100%;
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 18px;
  background: rgba(4, 10, 34, 0.48);
  padding: 13px 14px;
  text-align: left;
  transition: transform 0.2s ease, border-color 0.2s ease, background 0.2s ease;
}

.plan-side-nav__item:hover {
  border-color: rgba(103, 232, 249, 0.35);
  background: rgba(14, 43, 84, 0.5);
  transform: translateY(-1px);
}

.plan-side-nav__item.is-active {
  border-color: rgba(103, 232, 249, 0.62);
  background:
    linear-gradient(100deg, rgba(34, 211, 238, 0.24), rgba(79, 70, 229, 0.3)),
    rgba(4, 10, 34, 0.7);
  box-shadow: 0 18px 36px -26px rgba(56, 189, 248, 0.75);
}

.plan-side-nav__item span {
  display: block;
  color: #f8fbff;
  font-size: 14px;
  font-weight: 800;
}

.plan-side-nav__item small {
  display: block;
  margin-top: 5px;
  color: rgba(203, 213, 225, 0.62);
  font-size: 11px;
  line-height: 1.45;
}

.plan-panel__head,
.plan-form-card__head,
.plan-section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.plan-panel__head p,
.plan-panel__title,
.plan-form-card__head p {
  margin: 0;
  color: #edf5ff;
  font-size: 13px;
  font-weight: 800;
}

.plan-panel__head span,
.plan-form-card__head small,
.plan-section-head span {
  color: rgba(203, 213, 225, 0.62);
  font-size: 11px;
}

.plan-panel__head button,
.plan-form-card__head button,
.plan-source-card button {
  border: 1px solid rgba(103, 232, 249, 0.24);
  border-radius: 10px;
  color: #cffafe;
  background: rgba(8, 145, 178, 0.08);
  padding: 6px 10px;
  font-size: 11px;
}

.plan-panel__head button:disabled,
.plan-form-card__head button:disabled {
  opacity: 0.45;
}

.plan-summary-list {
  display: grid;
  gap: 12px;
  margin: 14px 0 0;
}

.plan-summary-list div {
  border-top: 1px solid rgba(148, 163, 184, 0.11);
  padding-top: 10px;
}

.plan-summary-list dt {
  color: rgba(103, 232, 249, 0.82);
  font-size: 11px;
  font-weight: 800;
}

.plan-summary-list dd {
  margin: 5px 0 0;
  color: rgba(226, 232, 240, 0.82);
  font-size: 12px;
  line-height: 1.55;
}

.plan-health-card ul,
.plan-missing-card ul {
  margin: 12px 0 0;
  padding-left: 17px;
  color: rgba(253, 224, 171, 0.92);
  font-size: 12px;
  line-height: 1.65;
}

.plan-health-meter {
  margin-top: 12px;
}

.plan-meta-grid {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 9px 12px;
  margin-top: 12px;
  font-size: 12px;
}

.plan-meta-grid span {
  color: rgba(203, 213, 225, 0.56);
}

.plan-meta-grid strong {
  min-width: 0;
  color: rgba(226, 232, 240, 0.86);
  font-weight: 700;
  overflow-wrap: anywhere;
}

.plan-main-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 8px 4px 24px 0;
  overscroll-behavior: contain;
}

.plan-editor-section {
  display: grid;
  gap: 16px;
}

.plan-editor-section--hero {
  max-width: 980px;
}

.plan-section-head {
  margin-bottom: 2px;
}

.plan-section-kicker {
  margin: 0 0 7px;
  color: rgba(103, 232, 249, 0.78);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.18em;
}

.plan-section-head h4 {
  margin: 0;
  color: #f8fbff;
  font-size: 22px;
  font-weight: 850;
  letter-spacing: 0;
}

.plan-field-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.plan-field {
  display: grid;
  gap: 7px;
}

.plan-field--wide {
  grid-column: span 2;
}

.plan-field--full {
  width: 100%;
}

.plan-field span,
.plan-choice-group p {
  color: rgba(203, 213, 225, 0.72);
  font-size: 12px;
  font-weight: 700;
}

.plan-field input,
.plan-field select,
.plan-field textarea,
.plan-json-editor {
  width: 100%;
  min-height: 40px;
  border-radius: 13px !important;
  padding: 10px 12px !important;
  font-size: 13px !important;
}

.plan-field textarea {
  min-height: 118px;
  resize: vertical;
}

.plan-choice-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.plan-choice-group {
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 18px;
  background: rgba(4, 10, 34, 0.38);
  padding: 14px;
}

.plan-choice-group p {
  margin: 0 0 10px;
}

.plan-choice-group div {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.plan-chip {
  border: 1px solid rgba(103, 232, 249, 0.28);
  border-radius: 999px;
  color: #cffafe;
  background: rgba(6, 13, 43, 0.58);
  padding: 7px 12px;
  font-size: 12px;
  transition: transform 0.18s ease, background 0.18s ease;
}

.plan-chip:hover {
  transform: translateY(-1px);
  background: rgba(8, 145, 178, 0.16);
}

.plan-chip.is-selected {
  color: #fff;
  border-color: rgba(165, 243, 252, 0.75);
  background: linear-gradient(100deg, #22d3ee, #3b82f6 52%, #7c3aed);
  box-shadow: 0 16px 34px -24px rgba(56, 189, 248, 0.92);
}

.plan-form-stack {
  display: grid;
  gap: 14px;
}

.plan-form-card {
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 20px;
  background:
    radial-gradient(circle at 0 0, rgba(56, 189, 248, 0.08), transparent 38%),
    rgba(4, 10, 34, 0.46);
  padding: 16px;
}

.plan-repeat-list {
  display: grid;
  gap: 9px;
  margin-top: 12px;
}

.plan-repeat-row {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) 54px;
  gap: 10px;
  align-items: center;
}

.plan-repeat-row--activity {
  grid-template-columns: 34px minmax(0, 1fr) 112px 54px;
}

.plan-repeat-row > span {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 10px;
  color: #cffafe;
  background: rgba(14, 116, 144, 0.22);
  font-size: 12px;
  font-weight: 800;
}

.plan-repeat-row input {
  min-width: 0;
  min-height: 38px;
  border-radius: 12px !important;
  padding: 9px 11px !important;
  font-size: 13px !important;
}

.plan-repeat-row button {
  color: rgba(203, 213, 225, 0.72);
  font-size: 12px;
}

.plan-repeat-row button:hover {
  color: #fda4af;
}

.plan-evidence-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 16px;
  align-items: start;
}

.plan-evidence-list {
  display: grid;
  gap: 12px;
}

.plan-missing-card p {
  margin: 12px 0 0;
  color: rgba(167, 243, 208, 0.9);
  font-size: 12px;
}

.plan-source-list {
  display: grid;
  gap: 12px;
}

.plan-source-card {
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 18px;
  background: rgba(4, 10, 34, 0.48);
  padding: 14px;
}

.plan-source-card > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.plan-source-card h5 {
  margin: 0;
  color: #edf5ff;
  font-size: 14px;
  font-weight: 800;
}

.plan-source-card span {
  border-radius: 999px;
  padding: 4px 8px;
  font-size: 11px;
  font-weight: 700;
}

.plan-source-card span.is-ok {
  color: #bbf7d0;
  background: rgba(34, 197, 94, 0.16);
}

.plan-source-card span.is-warn {
  color: #fde68a;
  background: rgba(245, 158, 11, 0.16);
}

.plan-source-card p,
.plan-source-card small,
.plan-source-card blockquote {
  display: block;
  margin: 8px 0 0;
  color: rgba(203, 213, 225, 0.72);
  font-size: 12px;
  line-height: 1.55;
}

.plan-source-card blockquote {
  border-left: 2px solid rgba(103, 232, 249, 0.34);
  padding-left: 10px;
  color: rgba(226, 232, 240, 0.84);
}

.plan-source-card button {
  margin-top: 12px;
}

.plan-empty-state {
  border: 1px solid rgba(251, 191, 36, 0.22);
  border-radius: 18px;
  background: rgba(251, 191, 36, 0.06);
  padding: 18px;
}

.plan-empty-state strong {
  color: #fde68a;
  font-size: 14px;
}

.plan-empty-state p,
.plan-help-text {
  color: rgba(203, 213, 225, 0.72);
  font-size: 12px;
}

.plan-error-text {
  color: #fda4af;
  font-size: 12px;
}

.plan-link-editor {
  position: sticky;
  top: 0;
  display: grid;
  gap: 12px;
}

.plan-json-editor {
  min-height: 520px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  line-height: 1.6;
}

.plan-action-bar {
  position: relative;
  z-index: 2;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 10px;
  border-top: 1px solid rgba(148, 163, 184, 0.14);
  padding-top: 12px;
  background: linear-gradient(180deg, rgba(5, 11, 35, 0), rgba(5, 11, 35, 0.78) 24%, rgba(5, 11, 35, 0.94));
}

.plan-action-status {
  min-width: 0;
  color: #86efac;
  font-size: 12px;
}

.plan-action-status p {
  margin: 0;
}

.plan-action-status .is-error {
  color: #fda4af;
}

.plan-action-buttons {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.plan-primary-btn,
.plan-secondary-btn,
.plan-ghost-btn {
  min-height: 38px;
  border-radius: 13px;
  padding: 0 16px;
  font-size: 13px;
  font-weight: 800;
  transition: transform 0.18s ease, opacity 0.18s ease;
}

.plan-primary-btn {
  border: 0;
  color: #fff;
  background: linear-gradient(100deg, #22d3ee, #347cff 48%, #7c3aed);
  box-shadow: 0 18px 36px -24px rgba(56, 189, 248, 0.9);
}

.plan-secondary-btn,
.plan-ghost-btn {
  border: 1px solid rgba(148, 163, 184, 0.24);
  color: #dbeafe;
  background: rgba(6, 13, 43, 0.5);
}

.plan-primary-btn:hover,
.plan-secondary-btn:hover,
.plan-ghost-btn:hover {
  transform: translateY(-1px);
}

.plan-primary-btn:disabled,
.plan-secondary-btn:disabled,
.plan-ghost-btn:disabled {
  opacity: 0.48;
  transform: none;
}

.plan-more-menu {
  position: absolute;
  right: 0;
  bottom: calc(100% + 10px);
  z-index: 20;
  width: 230px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 14px;
  background: rgba(3, 8, 28, 0.96);
  padding: 6px;
  box-shadow: 0 24px 70px -34px rgba(0, 0, 0, 0.9);
}

.plan-more-menu button {
  display: block;
  width: 100%;
  border-radius: 10px;
  color: rgba(226, 232, 240, 0.9);
  padding: 8px 10px;
  text-align: left;
  font-size: 12px;
}

.plan-more-menu button:hover {
  background: rgba(56, 189, 248, 0.1);
}

@media (max-width: 1180px) {
  .plan-overview-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .plan-workbench__body {
    grid-template-columns: 1fr;
    overflow-y: auto;
    height: calc(100% - 220px);
  }

  .plan-workbench__side {
    overflow: visible;
  }

  .plan-evidence-layout {
    grid-template-columns: 1fr;
  }

  .plan-link-editor {
    position: static;
  }
}

@media (max-width: 760px) {
  .plan-workbench__header,
  .plan-overview-grid,
  .plan-workbench__body {
    padding-left: 16px;
    padding-right: 16px;
  }

  .plan-overview-grid,
  .plan-field-grid,
  .plan-choice-grid {
    grid-template-columns: 1fr;
  }

  .plan-field--wide {
    grid-column: auto;
  }

  .plan-repeat-row,
  .plan-repeat-row--activity {
    grid-template-columns: 28px minmax(0, 1fr);
  }

  .plan-repeat-row button,
  .plan-repeat-row--activity button {
    grid-column: 2;
    justify-self: start;
  }

  .plan-action-bar {
    align-items: stretch;
    flex-direction: column;
  }

  .plan-action-buttons {
    flex-wrap: wrap;
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

@keyframes waveDrift {
  0%,
  100% {
    transform: translate3d(0, 0, 0) scale(1);
    opacity: 0.32;
  }
  50% {
    transform: translate3d(2.5%, -4%, 0) scale(1.03);
    opacity: 0.5;
  }
}

@keyframes welcomeFloat {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-8px);
  }
}

@keyframes nebulaPulse {
  0%,
  100% {
    transform: translateY(0);
    opacity: 0.7;
  }
  50% {
    transform: translateY(-18px) scale(1.02);
    opacity: 1;
  }
}

/* Reference-grade empty dashboard surface */
.chat-shell::before,
.chat-shell::after {
  opacity: 0.5;
}

.chat-bg {
  background-image:
    radial-gradient(1px 1px at 7% 13%, rgba(255, 255, 255, 0.52), transparent 60%),
    radial-gradient(1px 1px at 28% 8%, rgba(96, 165, 250, 0.62), transparent 60%),
    radial-gradient(1px 1px at 56% 21%, rgba(255, 255, 255, 0.48), transparent 60%),
    radial-gradient(2px 2px at 77% 16%, rgba(139, 92, 246, 0.78), transparent 60%),
    radial-gradient(1px 1px at 91% 37%, rgba(56, 189, 248, 0.58), transparent 60%);
}

.chat-bg::before {
  top: 26%;
  height: 43%;
  opacity: 0.5;
  background:
    radial-gradient(ellipse at 17% 58%, rgba(37, 99, 235, 0.34), transparent 25%),
    radial-gradient(ellipse at 88% 46%, rgba(147, 51, 234, 0.36), transparent 28%),
    repeating-linear-gradient(164deg, rgba(65, 137, 255, 0.22) 0 1px, transparent 1px 18px),
    repeating-linear-gradient(13deg, rgba(139, 92, 246, 0.18) 0 1px, transparent 1px 21px);
  clip-path: polygon(0 58%, 15% 36%, 31% 45%, 47% 62%, 61% 46%, 78% 31%, 100% 18%, 100% 64%, 77% 82%, 58% 72%, 39% 61%, 22% 72%, 0 87%);
}

.chat-message-scroll {
  padding-top: 30px !important;
  padding-bottom: 24px !important;
}

.welcome-state {
  max-width: 1110px;
  transform: translateY(-8px);
}

.welcome-orbit {
  width: 136px;
  height: 136px;
  margin-bottom: 28px;
  border-radius: 31px;
  background: linear-gradient(145deg, rgba(55, 66, 160, 0.42), rgba(14, 23, 66, 0.64));
  border-color: rgba(142, 157, 255, 0.58);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.16),
    inset 0 0 38px rgba(56, 189, 248, 0.08),
    0 22px 60px -28px rgba(99, 102, 241, 0.9);
}

.welcome-title {
  font-size: clamp(42px, 4.2vw, 58px) !important;
  text-shadow: 0 20px 52px rgba(59, 130, 246, 0.26);
}

.welcome-subtitle {
  margin-top: 12px;
  font-size: 20px;
  font-weight: 700;
  color: rgba(226, 232, 240, 0.94);
}

.welcome-copy {
  margin-top: 18px !important;
  margin-bottom: 36px !important;
  font-size: 16px !important;
  color: rgba(203, 213, 225, 0.86) !important;
}

.welcome-grid {
  max-width: 1080px !important;
  gap: 16px !important;
}

.welcome-card {
  min-height: 176px;
  border-radius: 14px !important;
  padding: 24px 26px !important;
  grid-template-columns: 58px minmax(0, 1fr);
  background:
    radial-gradient(circle at 16% 24%, rgba(124, 77, 255, 0.26), transparent 28%),
    linear-gradient(145deg, rgba(36, 43, 112, 0.58), rgba(11, 21, 61, 0.58));
  border-color: rgba(129, 140, 248, 0.34) !important;
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.09),
    0 24px 55px -42px rgba(59, 130, 246, 0.9);
}

.welcome-card:nth-child(2) {
  background:
    radial-gradient(circle at 16% 24%, rgba(59, 130, 246, 0.28), transparent 29%),
    linear-gradient(145deg, rgba(21, 56, 121, 0.58), rgba(10, 23, 65, 0.58));
}

.welcome-card:nth-child(3) {
  background:
    radial-gradient(circle at 16% 24%, rgba(20, 184, 166, 0.25), transparent 29%),
    linear-gradient(145deg, rgba(15, 83, 92, 0.54), rgba(9, 24, 61, 0.58));
}

.welcome-card:nth-child(4) {
  background:
    radial-gradient(circle at 16% 24%, rgba(217, 70, 239, 0.25), transparent 29%),
    linear-gradient(145deg, rgba(80, 35, 107, 0.56), rgba(21, 18, 62, 0.58));
}

.welcome-card-icon {
  width: 52px;
  height: 52px;
  border-radius: 15px;
  font-size: 20px;
}

.welcome-card .text-sm {
  font-size: 17px;
  white-space: nowrap;
}

.welcome-card .text-xs {
  margin-top: 8px !important;
  font-size: 14px;
}

.welcome-card-arrow {
  left: 96px;
  bottom: 22px;
  width: 40px;
  height: 30px;
  background: rgba(255, 255, 255, 0.08);
}

.chat-input-zone {
  width: min(1320px, calc(100% - 72px));
  margin-bottom: 26px;
  border-radius: 18px;
  border-color: rgba(148, 163, 184, 0.3);
  background: linear-gradient(145deg, rgba(19, 32, 78, 0.72), rgba(14, 14, 54, 0.7));
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.08),
    0 26px 64px -46px rgba(30, 64, 175, 0.95);
}

.input-resizer {
  height: 12px;
  border-radius: 18px 18px 0 0;
  background: linear-gradient(90deg, rgba(56, 189, 248, 0.34), rgba(236, 72, 153, 0.28));
}

.resizer-handle {
  width: 66px;
  height: 4px;
  background: rgba(255, 255, 255, 0.22);
}

/* Premium tool dialogs and generated artifacts */
.chat-shell:not(.chat-shell--elder) .modal-card:not(.plan-workbench) {
  position: relative;
  border-radius: 24px !important;
  border-color: rgba(126, 164, 255, 0.28) !important;
  background:
    radial-gradient(circle at 14% 0%, rgba(42, 213, 255, 0.16), transparent 34%),
    radial-gradient(circle at 86% 0%, rgba(196, 89, 255, 0.14), transparent 35%),
    linear-gradient(145deg, rgba(10, 22, 61, 0.95), rgba(4, 9, 31, 0.94) 58%, rgba(13, 9, 39, 0.95)) !important;
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.1),
    0 34px 110px -54px rgba(0, 0, 0, 0.92),
    0 20px 60px -48px rgba(56, 189, 248, 0.7) !important;
  backdrop-filter: blur(26px) saturate(118%);
}

.chat-shell:not(.chat-shell--elder) .modal-card:not(.plan-workbench)::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  border-radius: inherit;
  background:
    linear-gradient(90deg, rgba(56, 189, 248, 0.28), rgba(124, 58, 237, 0.12), transparent 78%),
    radial-gradient(circle at 14% 12%, rgba(255, 255, 255, 0.14) 0 1px, transparent 2px),
    radial-gradient(circle at 72% 18%, rgba(125, 211, 252, 0.22) 0 1px, transparent 2px);
  opacity: 0.72;
  mask-image: linear-gradient(to bottom, #000, transparent 42%);
}

.chat-shell:not(.chat-shell--elder) .modal-card:not(.plan-workbench) > * {
  position: relative;
  z-index: 1;
}

.chat-shell:not(.chat-shell--elder) .modal-card:not(.plan-workbench) h3 {
  color: #f8fbff !important;
  font-size: 20px !important;
  font-weight: 800 !important;
  letter-spacing: 0;
  text-shadow: 0 16px 42px rgba(56, 189, 248, 0.24);
}

.chat-shell:not(.chat-shell--elder) .modal-card:not(.plan-workbench) p,
.chat-shell:not(.chat-shell--elder) .modal-card:not(.plan-workbench) label,
.chat-shell:not(.chat-shell--elder) .modal-card:not(.plan-workbench) small {
  line-height: 1.72;
}

.chat-shell:not(.chat-shell--elder) .modal-card:not(.plan-workbench) input,
.chat-shell:not(.chat-shell--elder) .modal-card:not(.plan-workbench) textarea,
.chat-shell:not(.chat-shell--elder) .modal-card:not(.plan-workbench) select,
.chat-shell:not(.chat-shell--elder) .modal-card:not(.plan-workbench) .modal-textarea {
  border-radius: 15px !important;
  border-color: rgba(148, 163, 184, 0.24) !important;
  background: rgba(3, 8, 28, 0.62) !important;
  color: #edf5ff !important;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.055);
}

.chat-shell:not(.chat-shell--elder) .modal-card:not(.plan-workbench) input:focus,
.chat-shell:not(.chat-shell--elder) .modal-card:not(.plan-workbench) textarea:focus,
.chat-shell:not(.chat-shell--elder) .modal-card:not(.plan-workbench) select:focus,
.chat-shell:not(.chat-shell--elder) .modal-card:not(.plan-workbench) .modal-textarea:focus {
  border-color: rgba(56, 189, 248, 0.74) !important;
  box-shadow:
    0 0 0 3px rgba(56, 189, 248, 0.13),
    inset 0 1px 0 rgba(255, 255, 255, 0.08) !important;
}

.chat-shell:not(.chat-shell--elder) .modal-card:not(.plan-workbench) button.bg-sky-400,
.chat-shell:not(.chat-shell--elder) .modal-card:not(.plan-workbench) button.bg-sky-500,
.chat-shell:not(.chat-shell--elder) .modal-card:not(.plan-workbench) button.bg-cyan-400,
.chat-shell:not(.chat-shell--elder) .modal-card:not(.plan-workbench) button.bg-emerald-400,
.chat-shell:not(.chat-shell--elder) .modal-card:not(.plan-workbench) button.bg-amber-400,
.chat-shell:not(.chat-shell--elder) .modal-card:not(.plan-workbench) button.bg-rose-400,
.chat-shell:not(.chat-shell--elder) .modal-card:not(.plan-workbench) button.bg-gradient-to-r {
  color: #fff !important;
  border: 1px solid rgba(125, 211, 252, 0.34) !important;
  background: linear-gradient(100deg, #2bd4ff, #347cff 48%, #7b2cff) !important;
  box-shadow: 0 18px 42px -24px rgba(56, 189, 248, 0.88);
}

.chat-shell:not(.chat-shell--elder) .modal-card:not(.plan-workbench) button.border,
.chat-shell:not(.chat-shell--elder) .modal-card:not(.plan-workbench) .rounded-xl.border,
.chat-shell:not(.chat-shell--elder) .modal-card:not(.plan-workbench) .rounded-lg.border {
  border-color: rgba(148, 163, 184, 0.2) !important;
}

.chat-shell:not(.chat-shell--elder) .modal-card:not(.plan-workbench) .rounded-xl.border,
.chat-shell:not(.chat-shell--elder) .modal-card:not(.plan-workbench) .rounded-lg.border {
  background: linear-gradient(145deg, rgba(16, 35, 78, 0.62), rgba(5, 11, 35, 0.52)) !important;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.055);
}

.chat-shell:not(.chat-shell--elder) .study-pack-modal {
  width: min(1500px, calc(100vw - 56px)) !important;
  max-width: none !important;
  height: min(900px, calc(100vh - 48px)) !important;
  padding: 24px !important;
  border-radius: 26px !important;
}

.chat-shell:not(.chat-shell--elder) .study-pack-side,
.chat-shell:not(.chat-shell--elder) .study-pack-main {
  border-radius: 22px !important;
  border-color: rgba(148, 163, 184, 0.2) !important;
  background:
    radial-gradient(circle at 14% 6%, rgba(56, 189, 248, 0.13), transparent 40%),
    linear-gradient(145deg, rgba(13, 31, 77, 0.72), rgba(5, 11, 35, 0.58)) !important;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.065);
}

.chat-shell:not(.chat-shell--elder) .study-pack-main {
  position: relative;
  overflow: hidden;
}

.chat-shell:not(.chat-shell--elder) .study-pack-main::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    repeating-linear-gradient(0deg, rgba(96, 165, 250, 0.035) 0 1px, transparent 1px 48px),
    repeating-linear-gradient(90deg, rgba(124, 58, 237, 0.03) 0 1px, transparent 1px 56px);
  opacity: 0.75;
}

.chat-shell:not(.chat-shell--elder) .study-pack-main > * {
  position: relative;
  z-index: 1;
}

.chat-shell:not(.chat-shell--elder) .study-pack-tab-btn {
  min-height: 34px;
  border-radius: 999px;
  font-weight: 800;
  background: rgba(3, 8, 28, 0.44);
}

.chat-shell:not(.chat-shell--elder) .study-pack-tab-btn--active {
  color: #fff;
  background: linear-gradient(100deg, #2bd4ff, #347cff 48%, #7b2cff);
  box-shadow: 0 16px 34px -24px rgba(56, 189, 248, 0.88);
}

.chat-shell:not(.chat-shell--elder) .study-pack-flip-face {
  border-radius: 20px;
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.12),
    0 22px 54px -42px rgba(56, 189, 248, 0.85);
}

.chat-shell:not(.chat-shell--elder) .study-pack-flip-back,
.chat-shell:not(.chat-shell--elder) .study-pack-panel,
.chat-shell:not(.chat-shell--elder) .study-pack-quiz-card,
.chat-shell:not(.chat-shell--elder) .study-pack-analysis-card {
  background: linear-gradient(145deg, rgba(12, 28, 75, 0.9), rgba(7, 14, 45, 0.82));
}

.chat-shell:not(.chat-shell--elder) .micro-lesson,
.chat-shell:not(.chat-shell--elder) .game-card {
  border-radius: 22px;
  border-color: rgba(126, 164, 255, 0.3);
  background:
    radial-gradient(circle at 14% 8%, rgba(56, 189, 248, 0.18), transparent 36%),
    linear-gradient(145deg, rgba(11, 26, 70, 0.95), rgba(4, 9, 31, 0.92));
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.09),
    0 28px 74px -46px rgba(37, 99, 235, 0.82);
}

.chat-shell:not(.chat-shell--elder) .micro-lesson__board {
  background:
    radial-gradient(circle at 72% 22%, rgba(124, 58, 237, 0.18), transparent 32%),
    repeating-linear-gradient(0deg, rgba(96, 165, 250, 0.045) 0 1px, transparent 1px 34px),
    linear-gradient(145deg, rgba(10, 22, 61, 0.78), rgba(3, 8, 28, 0.92));
}

.chat-shell:not(.chat-shell--elder) .game-frame {
  border-radius: 16px;
  border-color: rgba(148, 163, 184, 0.24);
  background: rgba(3, 8, 28, 0.62);
}

/* 2026 hero depth pass: layered motion, luminous stage, dimensional cards */
.chat-shell:not(.chat-shell--elder) .chat-bg {
  background-image:
    radial-gradient(1px 1px at 9% 12%, rgba(255, 255, 255, 0.56), transparent 62%),
    radial-gradient(1px 1px at 22% 7%, rgba(96, 165, 250, 0.72), transparent 62%),
    radial-gradient(2px 2px at 48% 13%, rgba(125, 211, 252, 0.58), transparent 64%),
    radial-gradient(1px 1px at 72% 18%, rgba(196, 181, 253, 0.66), transparent 62%),
    radial-gradient(2px 2px at 91% 32%, rgba(56, 189, 248, 0.62), transparent 62%);
}

.chat-shell:not(.chat-shell--elder) .chat-bg::before {
  top: 24%;
  height: 50%;
  opacity: 0.64;
  animation: dashboardWaveDrift 16s ease-in-out infinite alternate;
  background:
    radial-gradient(ellipse at 12% 58%, rgba(37, 99, 235, 0.4), transparent 28%),
    radial-gradient(ellipse at 86% 44%, rgba(168, 85, 247, 0.42), transparent 30%),
    repeating-linear-gradient(164deg, rgba(65, 137, 255, 0.26) 0 1px, transparent 1px 18px),
    repeating-linear-gradient(13deg, rgba(139, 92, 246, 0.2) 0 1px, transparent 1px 22px);
  clip-path: polygon(0 58%, 14% 35%, 31% 44%, 47% 63%, 62% 46%, 78% 29%, 100% 17%, 100% 66%, 78% 82%, 58% 72%, 39% 61%, 22% 73%, 0 88%);
}

.chat-shell:not(.chat-shell--elder) .welcome-state {
  position: relative;
  isolation: isolate;
  width: min(1240px, calc(100% - 72px));
  max-width: 1240px;
  min-height: min(650px, 70vh);
  padding-top: 22px !important;
  transform: translateY(-10px);
  perspective: 1100px;
}

.chat-shell:not(.chat-shell--elder) .welcome-state::before {
  content: '';
  position: absolute;
  left: 50%;
  top: 108px;
  z-index: -2;
  width: min(1260px, 102vw);
  height: 380px;
  pointer-events: none;
  transform: translateX(-50%) rotateX(63deg);
  transform-origin: center top;
  opacity: 0.72;
  background:
    radial-gradient(ellipse at 50% 20%, rgba(124, 58, 237, 0.24), transparent 32%),
    radial-gradient(ellipse at 50% 44%, rgba(56, 189, 248, 0.2), transparent 38%),
    repeating-radial-gradient(ellipse at 50% 20%, rgba(96, 165, 250, 0.22) 0 1px, transparent 1px 28px);
  filter: drop-shadow(0 0 38px rgba(56, 189, 248, 0.2));
  animation: heroStageBreathe 7s ease-in-out infinite;
}

.chat-shell:not(.chat-shell--elder) .welcome-state::after {
  content: '';
  position: absolute;
  inset: 40px 0 auto;
  z-index: -1;
  height: 420px;
  pointer-events: none;
  opacity: 0.78;
  background:
    linear-gradient(180deg, rgba(56, 189, 248, 0.58), transparent 48%) 16% 44% / 1px 170px no-repeat,
    linear-gradient(180deg, rgba(168, 85, 247, 0.58), transparent 54%) 85% 32% / 1px 210px no-repeat,
    radial-gradient(circle at 16% 44%, rgba(125, 211, 252, 0.86) 0 2px, transparent 3px),
    radial-gradient(circle at 85% 32%, rgba(196, 181, 253, 0.9) 0 2px, transparent 3px),
    radial-gradient(circle at 26% 24%, rgba(255, 255, 255, 0.62) 0 1px, transparent 2px),
    radial-gradient(circle at 72% 18%, rgba(125, 211, 252, 0.52) 0 1px, transparent 2px);
  animation: heroParticles 9s ease-in-out infinite alternate;
}

.chat-shell:not(.chat-shell--elder) .welcome-orbit {
  width: 178px;
  height: 132px;
  margin-bottom: 24px;
  border: 0;
  border-radius: 36px;
  overflow: visible;
  background: transparent;
  box-shadow: none;
  transform-style: preserve-3d;
  animation: heroFloatRig 5.8s ease-in-out infinite;
}

.chat-shell:not(.chat-shell--elder) .welcome-orbit::before {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 13px;
  width: 188px;
  height: 58px;
  border-radius: 50%;
  transform: translateX(-50%) rotateX(66deg);
  background:
    radial-gradient(ellipse at center, rgba(167, 139, 250, 0.46), transparent 44%),
    repeating-radial-gradient(ellipse at center, rgba(96, 165, 250, 0.4) 0 1px, transparent 1px 18px);
  box-shadow:
    0 0 36px rgba(56, 189, 248, 0.34),
    0 0 58px rgba(124, 58, 237, 0.22);
}

.chat-shell:not(.chat-shell--elder) .welcome-orbit::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 26px;
  width: 98px;
  height: 18px;
  border-radius: 999px;
  transform: translateX(-50%);
  background: radial-gradient(ellipse at center, rgba(216, 180, 254, 0.95), rgba(56, 189, 248, 0.18) 45%, transparent 72%);
  filter: blur(3px);
  animation: heroCorePulse 2.6s ease-in-out infinite;
}

.chat-shell:not(.chat-shell--elder) .welcome-ring {
  position: absolute;
  left: 50%;
  top: 42px;
  width: 160px;
  height: 54px;
  border: 1px solid rgba(96, 165, 250, 0.58);
  border-radius: 50%;
  transform: translateX(-50%) rotateX(64deg);
  background: transparent;
  box-shadow:
    0 0 22px rgba(56, 189, 248, 0.24),
    inset 0 0 14px rgba(124, 58, 237, 0.22);
  animation: heroOrbitSpin 9s linear infinite;
}

.chat-shell:not(.chat-shell--elder) .welcome-ring--wide {
  top: 31px;
  width: 240px;
  height: 80px;
  border-color: rgba(168, 85, 247, 0.45);
  animation-duration: 13s;
  animation-direction: reverse;
}

.chat-shell:not(.chat-shell--elder) .welcome-tile {
  width: 44px;
  height: 78px;
  border-radius: 11px;
  transform-style: preserve-3d;
  background:
    linear-gradient(150deg, rgba(255, 255, 255, 0.5), rgba(96, 165, 250, 0.68) 45%, rgba(124, 58, 237, 0.72));
  border: 1px solid rgba(255, 255, 255, 0.34);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.52),
    inset -10px -14px 24px rgba(21, 28, 102, 0.28),
    0 18px 42px -20px rgba(56, 189, 248, 0.9);
  backdrop-filter: blur(10px);
}

.chat-shell:not(.chat-shell--elder) .welcome-tile::before {
  content: '';
  position: absolute;
  inset: 8px 8px auto;
  height: 18px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.26);
}

.chat-shell:not(.chat-shell--elder) .welcome-tile--left {
  left: 54px;
  top: 18px;
  transform: rotateY(-28deg) rotateZ(-4deg);
  animation: heroTileFloat 4.2s ease-in-out infinite;
}

.chat-shell:not(.chat-shell--elder) .welcome-tile--middle {
  left: 78px;
  top: 28px;
  height: 82px;
  transform: translateZ(22px) rotateY(-16deg);
  background:
    linear-gradient(150deg, rgba(128, 255, 187, 0.9), rgba(87, 143, 255, 0.72) 54%, rgba(236, 72, 153, 0.78));
  animation: heroTileFloat 4.8s ease-in-out 0.2s infinite;
}

.chat-shell:not(.chat-shell--elder) .welcome-tile--right {
  left: 102px;
  top: 40px;
  transform: translateZ(42px) rotateY(-10deg);
  background:
    linear-gradient(150deg, rgba(186, 230, 253, 0.98), rgba(96, 165, 250, 0.82) 45%, rgba(167, 139, 250, 0.86));
  animation: heroTileFloat 4.4s ease-in-out 0.45s infinite;
}

.chat-shell:not(.chat-shell--elder) .welcome-title {
  margin-top: 0 !important;
  font-size: clamp(52px, 5.2vw, 76px) !important;
  line-height: 1.05 !important;
  font-weight: 900 !important;
  letter-spacing: 0 !important;
  text-shadow:
    0 0 26px rgba(96, 165, 250, 0.25),
    0 26px 64px rgba(2, 8, 23, 0.62);
}

.chat-shell:not(.chat-shell--elder) .welcome-subtitle {
  position: relative;
  margin-top: 16px;
  font-size: 22px;
  font-weight: 800;
  color: rgba(241, 245, 249, 0.94);
  text-shadow: 0 12px 34px rgba(56, 189, 248, 0.18);
}

.chat-shell:not(.chat-shell--elder) .welcome-subtitle::before,
.chat-shell:not(.chat-shell--elder) .welcome-subtitle::after {
  content: '';
  display: inline-block;
  width: 9px;
  height: 9px;
  margin: 0 18px 2px;
  transform: rotate(45deg);
  border-radius: 2px;
  background: linear-gradient(135deg, #2bd4ff, #8b5cf6);
  box-shadow: 0 0 16px rgba(96, 165, 250, 0.75);
}

.chat-shell:not(.chat-shell--elder) .welcome-copy {
  margin-top: 20px !important;
  margin-bottom: 32px !important;
  font-size: 16px !important;
  line-height: 1.7 !important;
  color: rgba(226, 232, 240, 0.88) !important;
}

.chat-shell:not(.chat-shell--elder) .welcome-grid {
  width: min(1160px, 100%) !important;
  max-width: 1160px !important;
  gap: 20px !important;
  perspective: 1200px;
}

.chat-shell:not(.chat-shell--elder) .welcome-card {
  position: relative;
  min-height: 168px;
  padding: 26px 28px !important;
  border-radius: 16px !important;
  overflow: hidden;
  transform-style: preserve-3d;
  transition:
    transform 0.35s ease,
    border-color 0.35s ease,
    box-shadow 0.35s ease,
    background 0.35s ease;
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.13),
    inset 0 -1px 0 rgba(255, 255, 255, 0.04),
    0 30px 72px -48px rgba(59, 130, 246, 0.9),
    0 18px 38px -32px rgba(2, 8, 23, 0.9);
}

.chat-shell:not(.chat-shell--elder) .welcome-card::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  border-radius: inherit;
  background:
    linear-gradient(118deg, rgba(255, 255, 255, 0.18), transparent 24%, transparent 74%, rgba(255, 255, 255, 0.08)),
    radial-gradient(circle at 24% 22%, rgba(255, 255, 255, 0.17), transparent 20%);
  opacity: 0.72;
}

.chat-shell:not(.chat-shell--elder) .welcome-card::after {
  content: '';
  position: absolute;
  left: 14%;
  right: 14%;
  bottom: -18px;
  height: 46px;
  border-radius: 50%;
  background: rgba(56, 189, 248, 0.18);
  filter: blur(18px);
  opacity: 0.7;
  transition: opacity 0.35s ease, transform 0.35s ease;
}

.chat-shell:not(.chat-shell--elder) .welcome-card:nth-child(1) {
  border-color: rgba(76, 132, 255, 0.54) !important;
  background:
    radial-gradient(circle at 25% 22%, rgba(45, 212, 255, 0.2), transparent 28%),
    linear-gradient(145deg, rgba(33, 55, 132, 0.64), rgba(10, 22, 61, 0.66));
}

.chat-shell:not(.chat-shell--elder) .welcome-card:nth-child(2) {
  border-color: rgba(168, 85, 247, 0.58) !important;
  background:
    radial-gradient(circle at 25% 22%, rgba(168, 85, 247, 0.28), transparent 29%),
    linear-gradient(145deg, rgba(51, 32, 116, 0.66), rgba(10, 20, 60, 0.66));
}

.chat-shell:not(.chat-shell--elder) .welcome-card:nth-child(3) {
  border-color: rgba(45, 212, 191, 0.48) !important;
  background:
    radial-gradient(circle at 25% 22%, rgba(52, 211, 153, 0.23), transparent 30%),
    linear-gradient(145deg, rgba(15, 86, 96, 0.62), rgba(8, 24, 61, 0.68));
}

.chat-shell:not(.chat-shell--elder) .welcome-card:nth-child(4) {
  border-color: rgba(236, 72, 153, 0.52) !important;
  background:
    radial-gradient(circle at 25% 22%, rgba(251, 146, 60, 0.26), transparent 28%),
    linear-gradient(145deg, rgba(88, 34, 96, 0.64), rgba(21, 18, 62, 0.68));
}

.chat-shell:not(.chat-shell--elder) .welcome-card:hover {
  transform: translateY(-9px) rotateX(2deg) rotateY(-1deg);
  border-color: rgba(125, 211, 252, 0.78) !important;
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.17),
    0 36px 84px -46px rgba(56, 189, 248, 0.85),
    0 22px 42px -32px rgba(2, 8, 23, 0.95);
}

.chat-shell:not(.chat-shell--elder) .welcome-card:hover::after {
  opacity: 1;
  transform: scaleX(1.1);
}

.chat-shell:not(.chat-shell--elder) .welcome-card-icon {
  position: relative;
  z-index: 1;
  width: 58px;
  height: 58px;
  border-radius: 18px;
  transform: translateZ(28px);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.28),
    0 18px 38px -24px currentColor;
}

.chat-shell:not(.chat-shell--elder) .welcome-card-icon::before {
  content: '';
  position: absolute;
  inset: -11px;
  z-index: -1;
  border: 1px solid currentColor;
  opacity: 0.34;
  clip-path: polygon(50% 0, 92% 25%, 92% 75%, 50% 100%, 8% 75%, 8% 25%);
  background: radial-gradient(circle at 50% 50%, currentColor, transparent 62%);
  filter: saturate(120%);
}

.chat-shell:not(.chat-shell--elder) .welcome-card-icon::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: -20px;
  width: 72px;
  height: 22px;
  z-index: -2;
  border-radius: 50%;
  transform: translateX(-50%) rotateX(64deg);
  background: currentColor;
  opacity: 0.16;
  filter: blur(7px);
}

.chat-shell:not(.chat-shell--elder) .welcome-card-body {
  position: relative;
  z-index: 1;
  transform: translateZ(18px);
}

.chat-shell:not(.chat-shell--elder) .welcome-card .text-sm {
  font-size: 18px;
  font-weight: 850;
  line-height: 1.32;
}

.chat-shell:not(.chat-shell--elder) .welcome-card .text-xs {
  margin-top: 9px !important;
  font-size: 14px;
  line-height: 1.58;
  color: rgba(203, 213, 225, 0.82) !important;
}

.chat-shell:not(.chat-shell--elder) .welcome-card-arrow {
  left: 108px;
  bottom: 22px;
  z-index: 1;
  width: 42px;
  height: 34px;
  border-radius: 999px;
  background:
    radial-gradient(circle at 35% 22%, rgba(255, 255, 255, 0.18), transparent 38%),
    rgba(255, 255, 255, 0.09);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.12),
    0 12px 28px -20px rgba(56, 189, 248, 0.84);
}

.chat-shell:not(.chat-shell--elder) .chat-input-zone {
  border-radius: 20px;
  border-color: rgba(125, 211, 252, 0.24);
  background:
    radial-gradient(circle at 8% 0%, rgba(56, 189, 248, 0.14), transparent 36%),
    radial-gradient(circle at 96% 8%, rgba(236, 72, 153, 0.1), transparent 32%),
    linear-gradient(145deg, rgba(17, 30, 76, 0.78), rgba(12, 13, 51, 0.76));
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.1),
    0 30px 70px -48px rgba(56, 189, 248, 0.82);
}

.chat-shell:not(.chat-shell--elder) .input-resizer {
  background:
    linear-gradient(90deg, rgba(56, 189, 248, 0.48), rgba(99, 102, 241, 0.34), rgba(236, 72, 153, 0.36));
}

@keyframes dashboardWaveDrift {
  0% {
    transform: translate3d(-1.4%, 0, 0) scale(1);
  }
  100% {
    transform: translate3d(1.4%, -10px, 0) scale(1.03);
  }
}

@keyframes heroStageBreathe {
  0%,
  100% {
    opacity: 0.58;
    filter: drop-shadow(0 0 30px rgba(56, 189, 248, 0.18));
  }
  50% {
    opacity: 0.82;
    filter: drop-shadow(0 0 48px rgba(124, 58, 237, 0.24));
  }
}

@keyframes heroParticles {
  0% {
    transform: translateY(0);
    opacity: 0.62;
  }
  100% {
    transform: translateY(-12px);
    opacity: 0.92;
  }
}

@keyframes heroFloatRig {
  0%,
  100% {
    transform: translateY(0) rotateX(0deg);
  }
  50% {
    transform: translateY(-10px) rotateX(2deg);
  }
}

@keyframes heroCorePulse {
  0%,
  100% {
    opacity: 0.62;
    transform: translateX(-50%) scaleX(0.92);
  }
  50% {
    opacity: 1;
    transform: translateX(-50%) scaleX(1.08);
  }
}

@keyframes heroOrbitSpin {
  from {
    transform: translateX(-50%) rotateX(64deg) rotateZ(0deg);
  }
  to {
    transform: translateX(-50%) rotateX(64deg) rotateZ(360deg);
  }
}

@keyframes heroTileFloat {
  0%,
  100% {
    margin-top: 0;
  }
  50% {
    margin-top: -8px;
  }
}

@media (max-width: 1280px) {
  .chat-shell:not(.chat-shell--elder) .welcome-state {
    width: min(1080px, calc(100% - 44px));
  }

  .chat-shell:not(.chat-shell--elder) .welcome-title {
    font-size: clamp(42px, 5vw, 60px) !important;
  }

  .chat-shell:not(.chat-shell--elder) .welcome-card {
    min-height: 150px;
    padding: 22px 20px !important;
  }

  .chat-shell:not(.chat-shell--elder) .welcome-card .text-sm {
    white-space: normal;
  }
}

@media (prefers-reduced-motion: reduce) {
  .chat-shell:not(.chat-shell--elder) .chat-bg::before,
  .chat-shell:not(.chat-shell--elder) .welcome-state::before,
  .chat-shell:not(.chat-shell--elder) .welcome-state::after,
  .chat-shell:not(.chat-shell--elder) .welcome-orbit,
  .chat-shell:not(.chat-shell--elder) .welcome-orbit::after,
  .chat-shell:not(.chat-shell--elder) .welcome-ring,
  .chat-shell:not(.chat-shell--elder) .welcome-tile {
    animation: none !important;
  }
}

/* Spacing refinement: keep feature cards floating above the composer */
.chat-shell:not(.chat-shell--elder) .chat-message-scroll {
  padding-top: 22px !important;
  padding-bottom: 28px !important;
}

.chat-shell:not(.chat-shell--elder) .welcome-state {
  justify-content: flex-start !important;
  min-height: min(610px, 64vh);
  padding-top: 10px !important;
}

.chat-shell:not(.chat-shell--elder) .welcome-orbit {
  margin-bottom: 18px;
  transform: scale(0.92);
}

.chat-shell:not(.chat-shell--elder) .welcome-title {
  font-size: clamp(48px, 4.9vw, 70px) !important;
}

.chat-shell:not(.chat-shell--elder) .welcome-subtitle {
  margin-top: 13px;
  font-size: 21px;
}

.chat-shell:not(.chat-shell--elder) .welcome-copy {
  margin-top: 16px !important;
  margin-bottom: 28px !important;
}

.chat-shell:not(.chat-shell--elder) .welcome-grid {
  transform: translateY(-4px);
}

.chat-shell:not(.chat-shell--elder) .welcome-card {
  min-height: 154px;
  padding: 22px 24px !important;
}

.chat-shell:not(.chat-shell--elder) .welcome-card-icon {
  width: 54px;
  height: 54px;
}

.chat-shell:not(.chat-shell--elder) .welcome-card .text-sm {
  font-size: 17px;
}

.chat-shell:not(.chat-shell--elder) .welcome-card .text-xs {
  margin-top: 7px !important;
  line-height: 1.5;
}

.chat-shell:not(.chat-shell--elder) .welcome-card-arrow {
  bottom: 18px;
}

.chat-shell:not(.chat-shell--elder) .chat-input-zone {
  position: relative;
  width: min(1360px, calc(100% - 86px));
  margin-bottom: 22px;
  border-radius: 22px;
  border-color: rgba(116, 161, 255, 0.34);
  background:
    radial-gradient(circle at 8% 0%, rgba(56, 189, 248, 0.18), transparent 30%),
    radial-gradient(circle at 92% 0%, rgba(236, 72, 153, 0.14), transparent 32%),
    linear-gradient(145deg, rgba(17, 31, 79, 0.82), rgba(13, 13, 54, 0.8));
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.12),
    inset 0 -1px 0 rgba(255, 255, 255, 0.04),
    0 34px 78px -52px rgba(56, 189, 248, 0.9),
    0 -28px 56px -52px rgba(125, 211, 252, 0.72);
}

.chat-shell:not(.chat-shell--elder) .chat-input-zone::before {
  content: '';
  position: absolute;
  left: 1px;
  right: 1px;
  top: 0;
  height: 17px;
  pointer-events: none;
  border-radius: 22px 22px 0 0;
  background:
    linear-gradient(90deg, rgba(56, 189, 248, 0.58), rgba(99, 102, 241, 0.38), rgba(236, 72, 153, 0.42));
  opacity: 0.72;
  filter: saturate(120%);
}

.chat-shell:not(.chat-shell--elder) .chat-input-zone::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 7px;
  width: 76px;
  height: 5px;
  pointer-events: none;
  border-radius: 999px;
  transform: translateX(-50%);
  background: rgba(255, 255, 255, 0.26);
  box-shadow: 0 0 16px rgba(125, 211, 252, 0.36);
}

.chat-shell:not(.chat-shell--elder) .input-resizer {
  height: 18px;
  border-radius: 22px 22px 0 0;
  background: transparent;
}

.chat-shell:not(.chat-shell--elder) .resizer-handle {
  display: none;
}

@media (max-height: 850px) {
  .chat-shell:not(.chat-shell--elder) .welcome-orbit {
    transform: scale(0.82);
    margin-bottom: 8px;
  }

  .chat-shell:not(.chat-shell--elder) .welcome-title {
    font-size: clamp(42px, 4.6vw, 60px) !important;
  }

  .chat-shell:not(.chat-shell--elder) .welcome-copy {
    margin-bottom: 22px !important;
  }

  .chat-shell:not(.chat-shell--elder) .welcome-card {
    min-height: 140px;
    padding: 19px 22px !important;
  }
}

/* Final composer shell proportions */
.chat-shell:not(.chat-shell--elder) .chat-input-zone {
  width: min(1320px, calc(100% - 92px));
  margin-bottom: 18px;
  border-radius: 20px;
}

.chat-shell:not(.chat-shell--elder) .chat-input-zone::before {
  height: 14px;
  border-radius: 20px 20px 0 0;
}

.chat-shell:not(.chat-shell--elder) .chat-input-zone::after {
  top: 5px;
  width: 72px;
  height: 4px;
}

.chat-shell:not(.chat-shell--elder) .input-resizer {
  height: 14px;
  border-radius: 20px 20px 0 0;
}
</style>
