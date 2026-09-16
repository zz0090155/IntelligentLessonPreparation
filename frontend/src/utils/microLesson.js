function asText(value, fallback = '') {
  const text = String(value ?? '').trim()
  return text || fallback
}

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function escapeInlineJson(value) {
  return String(value ?? '')
    .replace(/</g, '\\u003c')
    .replace(/>/g, '\\u003e')
    .replace(/&/g, '\\u0026')
}

export function getMicrocourseScenes(message) {
  if (Array.isArray(message?.scenePreviews) && message.scenePreviews.length) {
    return message.scenePreviews
  }
  if (Array.isArray(message?.scene_previews) && message.scene_previews.length) {
    return message.scene_previews
  }
  if (Array.isArray(message?.microcourseJson?.scenes) && message.microcourseJson.scenes.length) {
    return message.microcourseJson.scenes
  }
  if (Array.isArray(message?.microcourse_json?.scenes) && message.microcourse_json.scenes.length) {
    return message.microcourse_json.scenes
  }
  return []
}

export function getBoardContent(message) {
  const scenes = getMicrocourseScenes(message)
  if (scenes.length) {
    const firstScene = scenes[0] || {}
    const rows = []
    if (firstScene.board_title || firstScene.title) {
      rows.push(firstScene.board_title || firstScene.title)
    }
    if (Array.isArray(firstScene.board_bullets)) rows.push(...firstScene.board_bullets)
    if (Array.isArray(firstScene.bullets)) rows.push(...firstScene.bullets)
    const cleaned = rows.map((item) => asText(item)).filter(Boolean)
    if (cleaned.length) return cleaned.slice(0, 8)
  }
  const board = message?.boardContent || message?.board_content
  if (Array.isArray(board) && board.length) {
    return board.map((item) => asText(item)).filter(Boolean).slice(0, 8)
  }
  const raw = asText(message?.text || message?.scriptText || message?.script_text)
  if (!raw) return ['今日重点：待补充']
  const first = raw.split(/[。！？\n]/).find((segment) => segment && segment.trim()) || raw
  return [`今日重点：${first.slice(0, 20)}`]
}

export function getMicrocourseLessonTitle(message) {
  const microcourseJson = message?.microcourseJson || message?.microcourse_json || {}
  const lessonTitle = asText(microcourseJson?.meta?.lesson_title || microcourseJson?.title)
  if (lessonTitle) return lessonTitle
  const rows = getBoardContent(message)
  return rows[0] || '数字人微课'
}

function normalizeScenes(message) {
  const boardItems = getBoardContent(message)
  const scenes = getMicrocourseScenes(message)
    .map((scene, index) => {
      const title = asText(scene?.board_title || scene?.title, `场景 ${index + 1}`)
      const subtitle = asText(scene?.subtitle || scene?.summary || scene?.narration)
      const bullets = [
        ...(Array.isArray(scene?.board_bullets) ? scene.board_bullets : []),
        ...(Array.isArray(scene?.bullets) ? scene.bullets : []),
      ]
        .map((item) => asText(item))
        .filter(Boolean)
        .slice(0, 8)
      const rawDuration = Number(scene?.duration_sec || scene?.duration || 0)
      return {
        title,
        subtitle,
        bullets,
        duration_sec: Number.isFinite(rawDuration) && rawDuration > 0 ? Math.round(rawDuration * 10) / 10 : 0,
      }
    })
    .filter((scene) => scene.title || scene.subtitle || scene.bullets.length)

  if (!scenes.length) {
    scenes.push({
      title: boardItems[0] || '今日重点',
      subtitle: '',
      bullets: boardItems.slice(1),
      duration_sec: 0,
    })
  }

  if (!scenes[0].bullets.length && boardItems.length > 1) {
    scenes[0].bullets = boardItems.slice(1)
  }
  return scenes
}

export function buildMicroLessonHtml(message) {
  const scenes = normalizeScenes(message)
  const title = escapeHtml(getMicrocourseLessonTitle(message))
  const videoUrl = escapeHtml(message?.videoUrl || message?.video_url || '')
  const scriptText = escapeHtml(message?.text || message?.scriptText || message?.script_text || '')
  const firstScene = scenes[0] || { title: '今日重点', subtitle: '', bullets: [] }
  const scenePayload = escapeInlineJson(JSON.stringify(scenes))
  const sceneRailHtml = scenes
    .map((scene, index) => {
      const bullets = scene.bullets.slice(0, 2).map((item) => `<li>${escapeHtml(item)}</li>`).join('')
      const duration = scene.duration_sec ? `${scene.duration_sec}s` : '--'
      return `<button class="scene-chip ${index === 0 ? 'is-active' : ''}" type="button" data-scene="${index}">
        <span><b>${escapeHtml(scene.title)}</b><em>${escapeHtml(duration)}</em></span>
        ${scene.subtitle ? `<p>${escapeHtml(scene.subtitle)}</p>` : ''}
        ${bullets ? `<ul>${bullets}</ul>` : ''}
      </button>`
    })
    .join('')

  return `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>${title}</title>
  <style>
    :root {
      color-scheme: dark;
      --cyan: #33d6ff;
      --blue: #4d7cff;
      --violet: #8057ff;
      --pink: #ff6bd6;
      --line: rgba(152, 196, 255, .24);
      --text: #eef6ff;
      --muted: #9fb7d6;
    }

    * { box-sizing: border-box; }
    body {
      margin: 0;
      min-height: 100vh;
      font-family: Inter, "PingFang SC", "Microsoft YaHei", system-ui, sans-serif;
      color: var(--text);
      background:
        radial-gradient(circle at 18% 16%, rgba(39, 143, 255, .32), transparent 34%),
        radial-gradient(circle at 86% 24%, rgba(127, 86, 255, .26), transparent 34%),
        linear-gradient(125deg, #061a39 0%, #06122c 48%, #020713 100%);
      overflow-x: hidden;
    }

    .page {
      width: min(1320px, calc(100% - 56px));
      margin: 0 auto;
      padding: 34px 0 42px;
    }

    h1 {
      margin: 0 0 18px;
      font-size: clamp(26px, 3.4vw, 42px);
      line-height: 1.12;
      letter-spacing: .02em;
      text-shadow: 0 18px 46px rgba(21, 126, 255, .32);
    }

    .stage-shell {
      position: relative;
      border: 1px solid var(--line);
      border-radius: 24px;
      padding: 18px;
      background:
        linear-gradient(145deg, rgba(16, 45, 87, .72), rgba(7, 12, 35, .82)),
        linear-gradient(90deg, rgba(48, 199, 255, .12), rgba(151, 71, 255, .12));
      box-shadow: 0 28px 90px rgba(0, 8, 32, .56), inset 0 1px 0 rgba(255, 255, 255, .08);
      overflow: hidden;
    }

    .stage-shell::before {
      content: "";
      position: absolute;
      left: 14px;
      right: 14px;
      top: 0;
      height: 7px;
      border-radius: 999px;
      background: linear-gradient(90deg, rgba(51, 214, 255, .72), rgba(128, 87, 255, .54), rgba(255, 107, 214, .52));
      opacity: .65;
    }

    .lesson-stage {
      position: relative;
      min-height: min(64vh, 720px);
      aspect-ratio: 16 / 9;
      border-radius: 18px;
      overflow: hidden;
      background:
        radial-gradient(circle at 22% 18%, rgba(41, 151, 255, .18), transparent 38%),
        linear-gradient(160deg, rgba(9, 44, 81, .96), rgba(3, 14, 34, .98) 60%, rgba(1, 8, 22, .98));
      box-shadow: inset 0 0 0 1px rgba(154, 204, 255, .12);
    }

    .lesson-stage::before,
    .lesson-stage::after {
      content: "";
      position: absolute;
      inset: 0;
      pointer-events: none;
    }

    .lesson-stage::before {
      background:
        linear-gradient(rgba(137, 197, 255, .06) 1px, transparent 1px),
        linear-gradient(90deg, rgba(137, 197, 255, .05) 1px, transparent 1px);
      background-size: 32px 32px;
      mask-image: linear-gradient(to bottom, #000, transparent 96%);
    }

    .lesson-stage::after {
      background:
        radial-gradient(circle at 76% 75%, rgba(33, 214, 255, .12), transparent 22%),
        linear-gradient(120deg, transparent 0 64%, rgba(123, 92, 255, .08) 65%, transparent 78%);
    }

    .board-layer {
      position: absolute;
      inset: 0;
      z-index: 1;
      padding: clamp(28px, 4vw, 58px) clamp(220px, 24vw, 360px) 30px clamp(28px, 4vw, 58px);
      display: flex;
      flex-direction: column;
      gap: 18px;
    }

    .board-layer.is-entering { animation: boardEnter .28s ease; }

    @keyframes boardEnter {
      from { opacity: .55; transform: translateY(8px); }
      to { opacity: 1; transform: translateY(0); }
    }

    .board-badge {
      width: fit-content;
      border-radius: 999px;
      padding: 7px 16px;
      color: #bdf0ff;
      border: 1px solid rgba(51, 214, 255, .4);
      background: rgba(2, 13, 31, .5);
      letter-spacing: .08em;
      font-size: 14px;
    }

    #sceneTitle {
      margin: 0;
      max-width: 18em;
      font-size: clamp(30px, 4vw, 54px);
      line-height: 1.12;
      text-shadow: 0 12px 30px rgba(2, 6, 23, .55);
    }

    #sceneSubtitle {
      margin: 0;
      max-width: 42em;
      color: #c7d8ff;
      font-size: clamp(16px, 1.4vw, 22px);
      line-height: 1.7;
    }

    #sceneSubtitle:empty { display: none; }

    .board-list {
      list-style: none;
      margin: 2px 0 0;
      padding: 0;
      display: grid;
      gap: 14px;
      max-width: 34em;
      color: #dcecff;
      font-size: clamp(20px, 2.35vw, 36px);
      line-height: 1.42;
      font-weight: 700;
    }

    .board-list li {
      display: flex;
      gap: 14px;
      align-items: flex-start;
    }

    .board-list li::before {
      content: "▸";
      color: var(--cyan);
      flex: 0 0 auto;
      filter: drop-shadow(0 0 8px rgba(51, 214, 255, .65));
    }

    .scene-index {
      margin-top: auto;
      width: fit-content;
      border: 1px solid rgba(157, 203, 255, .24);
      border-radius: 999px;
      padding: 7px 16px;
      background: rgba(2, 9, 24, .58);
      color: #d8e8ff;
      font-weight: 700;
    }

    .avatar-pip {
      position: absolute;
      z-index: 4;
      right: clamp(22px, 3.2vw, 56px);
      bottom: clamp(22px, 3.2vw, 52px);
      width: clamp(190px, 18vw, 300px);
      aspect-ratio: 10 / 14;
      border-radius: 24px;
      overflow: hidden;
      background: #030819;
      border: 1px solid rgba(197, 229, 255, .56);
      box-shadow: 0 26px 60px rgba(0, 5, 20, .68), 0 0 40px rgba(51, 214, 255, .12);
    }

    .avatar-pip::before {
      content: "";
      position: absolute;
      inset: -22%;
      background: radial-gradient(circle, rgba(74, 222, 255, .38), transparent 62%);
      filter: blur(24px);
    }

    .avatar-pip video {
      position: relative;
      z-index: 1;
      width: 100%;
      height: 100%;
      object-fit: cover;
      object-position: center top;
      display: block;
    }

    .controls {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 10px;
      margin-top: 14px;
    }

    .btn {
      min-height: 42px;
      border: 1px solid rgba(154, 204, 255, .24);
      border-radius: 12px;
      padding: 0 18px;
      color: #edf6ff;
      background: rgba(5, 14, 36, .66);
      font-weight: 800;
      cursor: pointer;
      text-decoration: none;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      transition: transform .15s ease, border-color .15s ease, box-shadow .15s ease;
    }

    .btn:hover { transform: translateY(-1px); border-color: rgba(70, 206, 255, .58); }
    .btn.primary {
      color: white;
      border: 0;
      background: linear-gradient(135deg, #34cfff, #6b49ff);
      box-shadow: 0 14px 32px rgba(57, 134, 255, .35);
    }
    .btn.primary.is-playing { background: linear-gradient(135deg, #ff8794, #ff5e79); }

    .progress-row {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      gap: 12px;
      align-items: center;
      margin-top: 12px;
      color: var(--muted);
      font-size: 14px;
    }

    .timeline {
      width: 100%;
      accent-color: var(--cyan);
    }

    .caption-panel {
      display: none;
      margin-top: 14px;
      border-radius: 18px;
      border: 1px solid rgba(154, 204, 255, .2);
      background: rgba(3, 10, 28, .55);
      padding: 14px;
    }

    .caption-panel.is-open { display: block; }

    .scene-rail {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
      gap: 10px;
    }

    .scene-chip {
      text-align: left;
      border: 1px solid rgba(154, 204, 255, .18);
      border-radius: 14px;
      padding: 12px;
      color: #dcecff;
      background: rgba(7, 16, 42, .75);
      cursor: pointer;
    }

    .scene-chip.is-active {
      border-color: rgba(51, 214, 255, .72);
      box-shadow: 0 12px 28px rgba(34, 167, 255, .2);
      background: linear-gradient(145deg, rgba(16, 54, 100, .78), rgba(15, 18, 58, .72));
    }

    .scene-chip span {
      display: flex;
      justify-content: space-between;
      gap: 10px;
      align-items: center;
    }

    .scene-chip b { font-size: 14px; }
    .scene-chip em { color: #a8c6ee; font-style: normal; font-size: 12px; }
    .scene-chip p, .scene-chip ul { margin: 8px 0 0; color: #adc4e8; font-size: 12px; line-height: 1.55; }
    .scene-chip ul { padding-left: 18px; }

    .script {
      margin: 12px 0 0;
      color: #d6e6ff;
      line-height: 1.8;
      font-size: 14px;
      white-space: pre-wrap;
    }

    @media (max-width: 820px) {
      .page { width: min(100% - 24px, 1320px); padding-top: 18px; }
      .lesson-stage { min-height: 560px; aspect-ratio: auto; }
      .board-layer { padding: 22px 22px 210px; }
      .avatar-pip { width: 150px; right: 18px; bottom: 18px; border-radius: 18px; }
      .board-list { font-size: 18px; }
    }
  </style>
</head>
<body>
  <main class="page">
    <h1>${title}</h1>
    <section class="stage-shell">
      <div class="lesson-stage">
        <div class="board-layer">
          <div class="board-badge">课堂黑板</div>
          <h2 id="sceneTitle">${escapeHtml(firstScene.title)}</h2>
          <p id="sceneSubtitle">${escapeHtml(firstScene.subtitle)}</p>
          <ul id="sceneBullets" class="board-list">
            ${(firstScene.bullets || []).map((item) => `<li>${escapeHtml(item)}</li>`).join('') || '<li>本页暂无板书要点</li>'}
          </ul>
          <div id="sceneIndexText" class="scene-index">1 / ${scenes.length}</div>
        </div>
        <div class="avatar-pip">
          <video id="avatarVideo" src="${videoUrl}" playsinline preload="metadata"></video>
        </div>
      </div>

      <div class="controls">
        <button id="playToggle" class="btn primary" type="button">开始播放</button>
        <button id="captionToggle" class="btn" type="button">展开字幕</button>
        <a class="btn" href="${videoUrl}" download>下载视频</a>
      </div>
      <div class="progress-row">
        <input id="timeline" class="timeline" type="range" min="0" max="100" value="0" step="0.01" />
        <span id="timeText">00:00 / 00:00</span>
      </div>
      <div id="captionPanel" class="caption-panel">
        <div class="scene-rail">${sceneRailHtml}</div>
        <p class="script">台词：${scriptText || '暂无字幕内容'}</p>
      </div>
    </section>
  </main>

  <script id="sceneData" type="application/json">${scenePayload}<\/script>
  <script>
    (function () {
      var scenes = []
      try {
        scenes = JSON.parse(document.getElementById('sceneData').textContent || '[]')
      } catch (err) {
        scenes = []
      }
      if (!scenes.length) scenes = [{ title: '今日重点', subtitle: '', bullets: [] }]

      var video = document.getElementById('avatarVideo')
      var playToggle = document.getElementById('playToggle')
      var captionToggle = document.getElementById('captionToggle')
      var captionPanel = document.getElementById('captionPanel')
      var timeline = document.getElementById('timeline')
      var timeText = document.getElementById('timeText')
      var titleNode = document.getElementById('sceneTitle')
      var subtitleNode = document.getElementById('sceneSubtitle')
      var bulletsNode = document.getElementById('sceneBullets')
      var indexNode = document.getElementById('sceneIndexText')
      var boardLayer = document.querySelector('.board-layer')
      var chips = Array.prototype.slice.call(document.querySelectorAll('[data-scene]'))
      var activeIndex = 0

      function escapeText(value) {
        return String(value || '')
          .replace(/&/g, '&amp;')
          .replace(/</g, '&lt;')
          .replace(/>/g, '&gt;')
          .replace(/"/g, '&quot;')
          .replace(/'/g, '&#39;')
      }

      function formatTime(seconds) {
        var value = Number(seconds)
        if (!Number.isFinite(value) || value < 0) value = 0
        var minutes = Math.floor(value / 60)
        var remain = Math.floor(value % 60)
        return String(minutes).padStart(2, '0') + ':' + String(remain).padStart(2, '0')
      }

      function renderScene(index) {
        activeIndex = Math.max(0, Math.min(scenes.length - 1, index))
        var scene = scenes[activeIndex] || {}
        var bullets = Array.isArray(scene.bullets) ? scene.bullets.filter(Boolean) : []
        titleNode.textContent = scene.title || ('场景 ' + (activeIndex + 1))
        subtitleNode.textContent = scene.subtitle || ''
        bulletsNode.innerHTML = bullets.length
          ? bullets.map(function (item) { return '<li>' + escapeText(item) + '</li>' }).join('')
          : '<li>本页暂无板书要点</li>'
        indexNode.textContent = (activeIndex + 1) + ' / ' + scenes.length
        chips.forEach(function (chip, chipIndex) {
          chip.classList.toggle('is-active', chipIndex === activeIndex)
        })
        boardLayer.classList.remove('is-entering')
        void boardLayer.offsetWidth
        boardLayer.classList.add('is-entering')
      }

      function updatePlayButton() {
        var playing = video && !video.paused && !video.ended
        playToggle.textContent = playing ? '暂停播放' : '开始播放'
        playToggle.classList.toggle('is-playing', playing)
      }

      function updateProgress() {
        var duration = Number(video.duration)
        var current = Number(video.currentTime)
        timeline.max = Number.isFinite(duration) && duration > 0 ? String(duration) : '100'
        timeline.value = Number.isFinite(current) ? String(current) : '0'
        timeText.textContent = formatTime(current) + ' / ' + formatTime(duration)
      }

      playToggle.addEventListener('click', function () {
        if (!video) return
        if (video.paused) video.play().catch(function () {})
        else video.pause()
      })

      captionToggle.addEventListener('click', function () {
        captionPanel.classList.toggle('is-open')
        captionToggle.textContent = captionPanel.classList.contains('is-open') ? '收起字幕' : '展开字幕'
      })

      timeline.addEventListener('input', function () {
        var value = Number(timeline.value)
        if (video && Number.isFinite(value)) video.currentTime = value
      })

      chips.forEach(function (chip) {
        chip.addEventListener('click', function () {
          renderScene(Number(chip.getAttribute('data-scene')))
        })
      })

      if (video) {
        video.addEventListener('loadedmetadata', updateProgress)
        video.addEventListener('timeupdate', updateProgress)
        video.addEventListener('play', updatePlayButton)
        video.addEventListener('pause', updatePlayButton)
        video.addEventListener('ended', updatePlayButton)
      }

      window.addEventListener('keydown', function (event) {
        if (event.key === 'ArrowLeft') renderScene(activeIndex - 1)
        if (event.key === 'ArrowRight') renderScene(activeIndex + 1)
        if (event.code === 'Space') {
          event.preventDefault()
          if (video.paused) video.play().catch(function () {})
          else video.pause()
        }
      })

      renderScene(0)
      updatePlayButton()
      updateProgress()
    })()
  <\/script>
</body>
</html>`
}

export function buildMicroLessonFilename(message) {
  const safeTitle = getMicrocourseLessonTitle(message)
    .replace(/[\\/:*?"<>|]+/g, '')
    .replace(/\s+/g, '-')
    .slice(0, 28)
  return `${safeTitle || '数字人微课'}-${Date.now()}.html`
}

export function openMicroLessonWindow(message) {
  const lessonWindow = window.open('', '_blank')
  if (!lessonWindow) return
  lessonWindow.document.open()
  lessonWindow.document.write(buildMicroLessonHtml(message))
  lessonWindow.document.close()
}

export function downloadMicroLessonHtml(message) {
  const blob = new Blob([buildMicroLessonHtml(message)], { type: 'text/html;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = buildMicroLessonFilename(message)
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}
