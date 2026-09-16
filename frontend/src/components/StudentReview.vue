<script setup>
import { ref, computed, watch } from 'vue'
import MarkdownIt from 'markdown-it'

const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
})

const fileInput = ref(null)
const selectedFile = ref(null)
const isDragging = ref(false)
const isAnalyzing = ref(false)
const uploadError = ref('')
const analysisStep = ref('')
const studyData = ref(null)
const cardStates = ref({})
const quizStates = ref([])

const sampleData = {
  outline: `# 高中数学 · 勾股定理复习大纲

## 核心概念
- 勾股定理：直角三角形两直角边平方和等于斜边平方。
- 勾股逆定理：若满足 a² + b² = c²，则为直角三角形。

## 关键公式
- \(a^2 + b^2 = c^2\)
- 斜边公式：\(c = \sqrt{a^2 + b^2}\)

## 高频考点
- 已知两边求斜边或直角边
- 判断三角形是否为直角三角形
- 勾股定理在实际问题中的应用
`,
  flashcards: [
    { front: '勾股定理是什么？', back: '直角三角形两直角边平方和等于斜边平方。' },
    { front: '勾股逆定理', back: '若 a² + b² = c²，则为直角三角形。' },
    { front: '斜边公式', back: 'c = √(a² + b²)。' },
    { front: '应用场景', back: '测量距离、建筑斜坡、坐标几何等。' },
  ],
  quiz: [
    {
      question: '直角三角形两直角边分别为 3 和 4，斜边为多少？',
      options: ['A. 5', 'B. 6', 'C. 7', 'D. 8'],
      answer: 'A',
      analysis: '3² + 4² = 9 + 16 = 25，所以斜边为 5。',
    },
    {
      question: '若三角形三边为 5、12、13，则该三角形是？',
      options: ['A. 等腰', 'B. 直角', 'C. 锐角', 'D. 钝角'],
      answer: 'B',
      analysis: '5² + 12² = 25 + 144 = 169，等于 13²。',
    },
    {
      question: '勾股定理适用于哪类三角形？',
      options: ['A. 任意三角形', 'B. 直角三角形', 'C. 等边三角形', 'D. 等腰三角形'],
      answer: 'B',
      analysis: '仅在直角三角形内成立。',
    },
  ],
  shortQuestions: [
    {
      question: '请描述勾股定理在现实生活中的一个应用场景。',
      answer: '例如用楼梯长度和高度计算斜坡长度，或测量旗杆高度。',
      analysis: '把实际问题转化为直角三角形，利用 a² + b² = c² 求未知边。',
    },
    {
      question: '如何判断一个三角形是否为直角三角形？',
      answer: '检验三边是否满足 a² + b² = c²。',
      analysis: '满足勾股逆定理即可判断为直角三角形。',
    },
  ],
}

const parsedOutline = computed(() => {
  if (!studyData.value?.outline) return ''
  return md.render(studyData.value.outline)
})

const flashcardCount = computed(() => studyData.value?.flashcards?.length || 0)
const quizCount = computed(() => studyData.value?.quiz?.length || 0)

const cardThemes = [
  'from-cyan-500 via-blue-500 to-indigo-600',
  'from-emerald-500 via-teal-500 to-sky-600',
  'from-amber-400 via-orange-500 to-rose-500',
  'from-violet-500 via-fuchsia-500 to-pink-500',
]

let analysisTimers = []

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
  handleFiles(event.dataTransfer.files)
}

function handleFileInput(event) {
  handleFiles(event.target.files)
}

function handleFiles(files) {
  if (!files || !files.length) return
  const file = files[0]
  const ext = file.name.split('.').pop()?.toLowerCase()
  const allowed = ['pdf', 'ppt', 'pptx', 'doc', 'docx']
  if (!allowed.includes(ext)) {
    uploadError.value = '仅支持 PDF / PPT / Word 文件'
    return
  }
  uploadError.value = ''
  selectedFile.value = file
}

function clearAnalysisTimers() {
  analysisTimers.forEach(timer => clearTimeout(timer))
  analysisTimers = []
}

function startAnalyze() {
  if (isAnalyzing.value) return
  if (!selectedFile.value) {
    uploadError.value = '请先上传复习资料'
    return
  }
  clearAnalysisTimers()
  isAnalyzing.value = true
  analysisStep.value = '正在解析文件内容...'

  analysisTimers.push(setTimeout(() => {
    if (isAnalyzing.value) analysisStep.value = '正在提炼复习大纲...'
  }, 900))
  analysisTimers.push(setTimeout(() => {
    if (isAnalyzing.value) analysisStep.value = '正在生成闪卡与押题卷...'
  }, 1700))
  analysisTimers.push(setTimeout(() => {
    studyData.value = { ...sampleData }
    isAnalyzing.value = false
    analysisStep.value = ''
  }, 2600))
}

function useSample() {
  selectedFile.value = null
  uploadError.value = ''
  studyData.value = { ...sampleData }
}

function resetUpload() {
  selectedFile.value = null
  uploadError.value = ''
  if (fileInput.value) fileInput.value.value = ''
}

function toggleCard(index) {
  cardStates.value[index] = !cardStates.value[index]
}

function getCardTheme(index) {
  return cardThemes[index % cardThemes.length]
}

function checkAnswer(questionIndex, option) {
  const current = quizStates.value[questionIndex]
  if (!current || current.selected) return
  const correct = option.trim().startsWith(studyData.value.quiz[questionIndex].answer)
  quizStates.value[questionIndex] = { selected: option, correct }
}
</script>

<template>
  <div class="student-review min-h-screen">
    <div class="max-w-6xl mx-auto px-6 pt-12 pb-10">
      <div class="hero-card">
        <div class="hero-badge">Student Portal · 复习中心</div>
        <h1 class="hero-title">万能复习转化器</h1>
        <p class="hero-subtitle">
          上传课堂资料，AI 自动提炼复习大纲、智能闪卡与押题卷，让学生专注在真正重要的知识点。
        </p>
        <div class="hero-tags">
          <span>📝 智能笔记 Outline</span>
          <span>📇 AI Flashcards</span>
          <span>🎯 Exam Predictor</span>
        </div>
      </div>

      <div class="upload-card" @dragover.prevent="handleDragOver" @dragleave="handleDragLeave" @drop.prevent="handleDrop">
        <div :class="['upload-zone', { dragging: isDragging }]" @click="triggerFileInput">
          <div class="upload-icon">📂</div>
          <div class="upload-text">
            <h3>拖拽上传复习资料</h3>
            <p>支持 PDF / PPT / Word</p>
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

        <div class="upload-info" v-if="selectedFile">
          <div>
            <p class="file-name">{{ selectedFile.name }}</p>
            <p class="file-tip">准备生成：大纲 · 闪卡 · 押题卷</p>
          </div>
          <div class="upload-actions">
            <button type="button" class="ghost-btn" @click="resetUpload" :disabled="isAnalyzing">重新选择</button>
            <button type="button" class="primary-btn" @click="startAnalyze" :disabled="isAnalyzing">
              <span v-if="isAnalyzing">{{ analysisStep }}</span>
              <span v-else>开始分析</span>
            </button>
          </div>
        </div>

        <div class="upload-info" v-else>
          <div>
            <p class="file-name">还没有上传资料</p>
            <p class="file-tip">可先体验示例内容，感受学习中心效果。</p>
          </div>
          <div class="upload-actions">
            <button type="button" class="ghost-btn" @click="useSample">使用示例内容</button>
          </div>
        </div>

        <p v-if="uploadError" class="upload-error">{{ uploadError }}</p>
      </div>

      <div class="results-header">
        <div>
          <h2>学习结果展示区</h2>
          <p>三栏并行展示，让不同学习场景互不干扰。</p>
        </div>
        <div class="results-meta" v-if="studyData">
          <span>{{ flashcardCount }} 张闪卡</span>
          <span>{{ quizCount }} 道选择题</span>
        </div>
      </div>

      <div class="results-grid">
        <section class="result-panel">
          <header>
            <h3>📝 复习大纲</h3>
            <p>Markdown 渲染的结构化笔记</p>
          </header>
          <div v-if="studyData" class="outline-body" v-html="parsedOutline"></div>
          <div v-else class="placeholder">上传资料后自动生成复习大纲。</div>
        </section>

        <section class="result-panel">
          <header>
            <h3>📇 智能闪卡</h3>
            <p>点击翻转卡片，快速记忆核心概念</p>
          </header>
          <div v-if="studyData" class="flashcard-grid">
            <div
              v-for="(card, index) in studyData.flashcards"
              :key="index"
              class="flip-card"
              @click="toggleCard(index)"
            >
              <div :class="['flip-card-inner', cardStates[index] ? 'is-flipped' : '']">
                <div :class="['flip-card-face', 'flip-card-front', 'bg-gradient-to-br', getCardTheme(index)]">
                  <h4>{{ card.front }}</h4>
                  <span>点击查看答案</span>
                </div>
                <div class="flip-card-face flip-card-back">
                  <p>{{ card.back }}</p>
                </div>
              </div>
            </div>
          </div>
          <div v-else class="placeholder">生成的重点知识将自动变成闪卡。</div>
        </section>

        <section class="result-panel">
          <header>
            <h3>🎯 模拟测试</h3>
            <p>即时判题 + 解析，强化理解与押题训练</p>
          </header>
          <div v-if="studyData" class="quiz-list">
            <div v-for="(q, idx) in studyData.quiz" :key="idx" class="quiz-card">
              <div class="quiz-title">
                <span>Q{{ idx + 1 }}</span>
                <h4>{{ q.question }}</h4>
              </div>
              <div class="quiz-options">
                <button
                  v-for="(opt, oIdx) in q.options"
                  :key="oIdx"
                  @click="checkAnswer(idx, opt)"
                  :class="[
                    'quiz-option',
                    quizStates[idx]?.selected === opt && quizStates[idx]?.correct ? 'option-correct' : '',
                    quizStates[idx]?.selected === opt && quizStates[idx]?.correct === false ? 'option-wrong' : '',
                  ]"
                >
                  {{ opt }}
                </button>
              </div>
              <div v-if="quizStates[idx]?.selected" class="quiz-analysis">
                <strong>解析：</strong> {{ q.analysis }}
              </div>
            </div>

            <div v-if="studyData.shortQuestions?.length" class="short-section">
              <h4>✍️ 简答题</h4>
              <div v-for="(q, idx) in studyData.shortQuestions" :key="idx" class="short-card">
                <p class="short-question">{{ idx + 1 }}. {{ q.question }}</p>
                <details class="short-detail">
                  <summary>查看答案与解析</summary>
                  <p class="short-answer"><strong>答案：</strong> {{ q.answer }}</p>
                  <p class="short-answer"><strong>解析：</strong> {{ q.analysis }}</p>
                </details>
              </div>
            </div>
          </div>
          <div v-else class="placeholder">AI 押题卷会在这里展示。</div>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Noto+Sans+SC:wght@400;600;700&family=ZCOOL+XiaoWei&display=swap');

.student-review {
  font-family: 'Noto Sans SC', sans-serif;
  background: radial-gradient(circle at top left, rgba(59, 130, 246, 0.12), transparent 50%),
    radial-gradient(circle at 70% 20%, rgba(244, 114, 182, 0.12), transparent 45%),
    #f8fafc;
  position: relative;
  overflow: hidden;
  color: #0f172a;
}

.hero-card {
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.9), rgba(248, 250, 252, 0.9));
  border: 1px solid rgba(226, 232, 240, 0.8);
  border-radius: 28px;
  padding: 32px 36px;
  box-shadow: 0 20px 40px -28px rgba(15, 23, 42, 0.35);
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(14, 116, 144, 0.12);
  color: #0e7490;
  font-weight: 600;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.hero-title {
  font-family: 'ZCOOL XiaoWei', serif;
  font-size: 40px;
  margin-top: 16px;
  color: #0f172a;
}

.hero-subtitle {
  margin-top: 10px;
  max-width: 720px;
  font-size: 16px;
  color: #475569;
  line-height: 1.7;
}

.hero-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 18px;
}

.hero-tags span {
  background: rgba(99, 102, 241, 0.12);
  color: #4338ca;
  padding: 6px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.upload-card {
  margin-top: 28px;
  background: #ffffff;
  border-radius: 24px;
  padding: 24px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  box-shadow: 0 18px 34px -28px rgba(15, 23, 42, 0.4);
}

.upload-zone {
  border: 2px dashed rgba(148, 163, 184, 0.6);
  border-radius: 22px;
  padding: 28px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  cursor: pointer;
  transition: all 0.3s ease;
  background: linear-gradient(135deg, rgba(248, 250, 252, 0.9), rgba(226, 232, 240, 0.4));
}

.upload-zone.dragging {
  border-color: rgba(59, 130, 246, 0.8);
  background: linear-gradient(135deg, rgba(224, 231, 255, 0.8), rgba(191, 219, 254, 0.5));
  transform: translateY(-2px);
}

.upload-icon {
  font-size: 36px;
}

.upload-text h3 {
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
}

.upload-text p {
  font-size: 13px;
  color: #64748b;
  margin-top: 4px;
}

.upload-btn {
  background: #0f172a;
  color: #ffffff;
  padding: 10px 20px;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 600;
}

.upload-info {
  margin-top: 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}

.file-name {
  font-weight: 600;
  color: #0f172a;
}

.file-tip {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 4px;
}

.upload-actions {
  display: flex;
  gap: 10px;
}

.ghost-btn {
  border: 1px solid rgba(148, 163, 184, 0.5);
  padding: 8px 14px;
  border-radius: 10px;
  color: #475569;
  font-size: 12px;
}

.primary-btn {
  background: linear-gradient(135deg, #2563eb, #7c3aed);
  color: #ffffff;
  padding: 8px 18px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 600;
}

.upload-error {
  margin-top: 12px;
  color: #e11d48;
  font-size: 12px;
}

.results-header {
  margin-top: 36px;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}

.results-header h2 {
  font-size: 22px;
  font-weight: 700;
}

.results-header p {
  font-size: 13px;
  color: #64748b;
  margin-top: 6px;
}

.results-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #475569;
}

.results-meta span {
  background: rgba(15, 23, 42, 0.04);
  padding: 6px 10px;
  border-radius: 999px;
}

.results-grid {
  margin-top: 20px;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 18px;
}

.result-panel {
  background: #ffffff;
  border-radius: 22px;
  padding: 20px;
  border: 1px solid rgba(226, 232, 240, 0.8);
  box-shadow: 0 14px 30px -24px rgba(15, 23, 42, 0.35);
  min-height: 420px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.result-panel header h3 {
  font-size: 16px;
  font-weight: 700;
}

.result-panel header p {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 4px;
}

.outline-body {
  font-size: 14px;
  color: #1e293b;
  line-height: 1.7;
}

.placeholder {
  margin-top: 12px;
  font-size: 13px;
  color: #94a3b8;
  text-align: center;
  padding: 30px 10px;
  border: 1px dashed rgba(148, 163, 184, 0.4);
  border-radius: 16px;
}

.flashcard-grid {
  display: grid;
  gap: 14px;
}

.flip-card {
  perspective: 1200px;
  cursor: pointer;
  height: 150px;
}

.flip-card-inner {
  position: relative;
  width: 100%;
  height: 100%;
  transition: transform 0.7s ease;
  transform-style: preserve-3d;
}

.is-flipped {
  transform: rotateY(180deg);
}

.flip-card-face {
  position: absolute;
  inset: 0;
  border-radius: 18px;
  padding: 18px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  color: #ffffff;
  backface-visibility: hidden;
}

.flip-card-front h4 {
  font-size: 16px;
  font-weight: 700;
}

.flip-card-front span {
  font-size: 12px;
  opacity: 0.8;
  margin-top: 8px;
}

.flip-card-back {
  background: #ffffff;
  border: 2px solid rgba(129, 140, 248, 0.2);
  color: #0f172a;
  transform: rotateY(180deg);
}

.flip-card-back p {
  font-size: 14px;
  line-height: 1.6;
}

.quiz-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.quiz-card {
  background: rgba(248, 250, 252, 0.9);
  border-radius: 16px;
  padding: 16px;
  border: 1px solid rgba(226, 232, 240, 0.9);
}

.quiz-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.quiz-title span {
  background: rgba(59, 130, 246, 0.12);
  color: #1d4ed8;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.quiz-title h4 {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

.quiz-options {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.quiz-option {
  text-align: left;
  padding: 10px 12px;
  border-radius: 10px;
  background: #f1f5f9;
  border: 1px solid transparent;
  transition: all 0.2s ease;
  font-size: 13px;
  color: #1e293b;
}

.quiz-option:hover {
  background: #e2e8f0;
}

.option-correct {
  background: rgba(34, 197, 94, 0.15);
  border-color: rgba(34, 197, 94, 0.6);
  color: #15803d;
}

.option-wrong {
  background: rgba(244, 63, 94, 0.12);
  border-color: rgba(244, 63, 94, 0.5);
  color: #be123c;
}

.quiz-analysis {
  margin-top: 10px;
  font-size: 12px;
  background: rgba(254, 243, 199, 0.7);
  padding: 10px;
  border-radius: 10px;
  color: #92400e;
}

.short-section {
  margin-top: 18px;
}

.short-section h4 {
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 10px;
}

.short-card {
  background: #ffffff;
  border-radius: 14px;
  padding: 12px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  margin-bottom: 10px;
}

.short-question {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
}

.short-detail {
  margin-top: 8px;
  font-size: 12px;
  color: #475569;
}

.short-detail summary {
  cursor: pointer;
  color: #2563eb;
  font-weight: 600;
}

.short-answer {
  margin-top: 6px;
  line-height: 1.6;
}

@media (max-width: 768px) {
  .upload-zone {
    flex-direction: column;
    align-items: flex-start;
  }
  .upload-info {
    flex-direction: column;
    align-items: flex-start;
  }
  .upload-actions {
    width: 100%;
    flex-wrap: wrap;
  }
}

/* Unified premium review skin */
.student-review {
  color: #e5edff;
  background:
    radial-gradient(circle at 18% 12%, rgba(0, 132, 255, 0.32), transparent 32%),
    radial-gradient(circle at 84% 16%, rgba(124, 58, 237, 0.26), transparent 34%),
    linear-gradient(128deg, #061b47 0%, #08143b 44%, #17072e 100%);
  overflow: hidden;
}

.student-review::before {
  content: '';
  position: fixed;
  inset: 0;
  background-image:
    radial-gradient(circle, rgba(255, 255, 255, 0.72) 0 1px, transparent 1.8px),
    radial-gradient(circle, rgba(74, 144, 255, 0.64) 0 1px, transparent 2px);
  background-size: 182px 182px, 244px 244px;
  opacity: 0.22;
  pointer-events: none;
}

.hero-card,
.upload-card,
.result-panel {
  position: relative;
  border: 1px solid rgba(148, 163, 184, 0.24);
  background: linear-gradient(145deg, rgba(14, 30, 75, 0.82), rgba(6, 12, 38, 0.72));
  color: #e5edff;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.08), 0 28px 80px -52px rgba(37, 99, 235, 0.8);
  backdrop-filter: blur(22px);
}

.hero-title,
.results-header h2,
.result-panel header h3 {
  color: #fff;
  font-family: 'Noto Sans SC', system-ui, sans-serif;
  font-weight: 800;
  letter-spacing: 0;
}

.hero-badge {
  color: #55e4ff;
  border-color: rgba(56, 189, 248, 0.32);
  background: rgba(56, 189, 248, 0.1);
}

.hero-subtitle,
.results-header p,
.result-panel header p,
.file-tip,
.placeholder {
  color: rgba(203, 213, 225, 0.82);
}

.hero-tags span,
.results-meta span {
  border: 1px solid rgba(148, 163, 184, 0.22);
  background: rgba(6, 13, 43, 0.48);
  color: rgba(226, 232, 240, 0.9);
}

.upload-zone {
  border-color: rgba(96, 165, 250, 0.52);
  background:
    radial-gradient(circle at 18% 20%, rgba(56, 189, 248, 0.18), transparent 38%),
    rgba(4, 10, 34, 0.45);
}

.upload-zone.dragging {
  border-color: rgba(45, 212, 191, 0.78);
  background:
    radial-gradient(circle at 18% 20%, rgba(45, 212, 191, 0.2), transparent 42%),
    rgba(9, 20, 54, 0.68);
}

.upload-icon {
  color: transparent;
  background: linear-gradient(145deg, #38d5ff, #7b5cff);
  box-shadow: 0 20px 44px -24px rgba(56, 189, 248, 0.92);
}

.upload-icon::before {
  content: '';
  display: block;
  width: 30px;
  height: 24px;
  margin: auto;
  border: 2px solid rgba(255, 255, 255, 0.88);
  border-top-color: transparent;
  border-radius: 8px;
}

.primary-btn,
.upload-btn {
  color: #fff;
  background: linear-gradient(100deg, #2bd4ff, #347cff 48%, #7b2cff);
  box-shadow: 0 18px 42px -22px rgba(56, 189, 248, 0.86);
}

.ghost-btn {
  color: rgba(226, 232, 240, 0.9);
  border-color: rgba(148, 163, 184, 0.22);
  background: rgba(6, 13, 43, 0.48);
}

.flip-card-face,
.quiz-card,
.short-card {
  border-color: rgba(148, 163, 184, 0.22);
  background: linear-gradient(145deg, rgba(26, 48, 104, 0.54), rgba(7, 14, 45, 0.52));
}

.flip-card-front {
  color: #fff;
}

.flip-card-back {
  color: rgba(226, 232, 240, 0.9);
}

.quiz-option {
  border-color: rgba(148, 163, 184, 0.22);
  background: rgba(5, 11, 35, 0.54);
  color: rgba(226, 232, 240, 0.9);
}

.quiz-option:hover {
  border-color: rgba(56, 189, 248, 0.48);
  background: rgba(18, 38, 87, 0.72);
}

.quiz-analysis,
.short-detail {
  border-color: rgba(45, 212, 191, 0.24);
  background: rgba(8, 30, 55, 0.72);
  color: #dbeafe;
}
</style>
