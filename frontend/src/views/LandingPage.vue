<script setup></script>

<template>
  <main class="landing-page">
    <div class="landing-stars" aria-hidden="true"></div>
    <div class="landing-mesh" aria-hidden="true"></div>

    <header class="landing-nav">
      <router-link to="/" class="brand-lockup" aria-label="教策云枢首页">
        <span class="brand-mark" aria-hidden="true"><i></i><i></i><i></i></span>
        <strong>教策云枢</strong>
      </router-link>
      <nav class="landing-links" aria-label="页面入口">
        <router-link to="/auth/teacher">教师登录</router-link>
        <router-link to="/auth/student">学生登录</router-link>
        <router-link to="/admin">管理台</router-link>
      </nav>
    </header>

    <section class="landing-hero">
      <div class="hero-copy">
        <p class="hero-kicker">AI Lesson Cloud</p>
        <h1>教策云枢 · <span>AI 备课助手</span></h1>
        <h2>让备课更智能，让教学更高效</h2>
        <p class="hero-desc">
          面向教师的智能备课与教学资源生成平台，将教学设计、课件生成、教材解析、学习资料包与学情分析整合到统一工作台。
        </p>

        <div class="hero-actions">
          <router-link to="/auth/teacher" class="primary-action">
            <span class="action-plane" aria-hidden="true"></span>
            教师工作台
          </router-link>
          <router-link to="/auth/student" class="secondary-action">学生学习中心</router-link>
        </div>
      </div>

      <div class="ai-portal" aria-hidden="true">
        <div class="portal-orbit portal-orbit--outer"></div>
        <div class="portal-orbit portal-orbit--inner"></div>
        <div class="portal-console">
          <div class="console-sidebar">
            <span></span><span></span><span></span><span></span>
          </div>
          <div class="console-core">AI</div>
          <div class="console-lines">
            <span></span><span></span><span></span>
          </div>
        </div>
        <div class="portal-card portal-card--doc"><i></i><strong>智能生成</strong></div>
        <div class="portal-card portal-card--chart"><i></i><strong>学情分析</strong></div>
        <div class="portal-card portal-card--media"><i></i><strong>多模课件</strong></div>
        <div class="portal-glow"></div>
      </div>
    </section>

    <section class="entry-panel" aria-label="功能入口">
      <router-link to="/auth/teacher" class="entry-card entry-card--teacher">
        <span class="entry-icon entry-icon--teacher" aria-hidden="true"></span>
        <strong>教师端</strong>
        <small>备课对话、教案课件、Word 导出与学习资料生成</small>
      </router-link>
      <router-link to="/auth/student" class="entry-card entry-card--student">
        <span class="entry-icon entry-icon--student" aria-hidden="true"></span>
        <strong>学生端</strong>
        <small>上传资料，生成复习大纲、闪卡与训练题</small>
      </router-link>
      <router-link to="/admin" class="entry-card entry-card--admin">
        <span class="entry-icon entry-icon--admin" aria-hidden="true"></span>
        <strong>管理台</strong>
        <small>账号控制、功能权限、审计日志与运维看板</small>
      </router-link>
    </section>

    <footer class="landing-footer">
      教策云枢 · AI 备课助手 © 2024 教策科技 版权所有
    </footer>
  </main>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Noto+Sans+SC:wght@400;500;600;700;800&display=swap');

.landing-page {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  padding: 34px 74px 28px;
  color: #e5edff;
  font-family: 'Noto Sans SC', system-ui, sans-serif;
  background:
    radial-gradient(circle at 18% 16%, rgba(0, 112, 255, 0.38), transparent 34%),
    radial-gradient(circle at 84% 22%, rgba(124, 58, 237, 0.34), transparent 36%),
    radial-gradient(circle at 70% 86%, rgba(15, 23, 42, 0.88), transparent 50%),
    linear-gradient(125deg, #061b47 0%, #07123a 45%, #17072d 100%);
}

.landing-page::before {
  content: '';
  position: absolute;
  inset: auto -10% -24% -12%;
  height: 58%;
  background:
    linear-gradient(165deg, transparent 8%, rgba(49, 116, 255, 0.24) 38%, transparent 42%),
    repeating-linear-gradient(172deg, rgba(78, 147, 255, 0.18) 0 1px, transparent 1px 16px);
  transform: skewY(-10deg);
  opacity: 0.58;
  mask-image: linear-gradient(to top, #000, transparent 88%);
  pointer-events: none;
}

.landing-stars {
  position: absolute;
  inset: 0;
  background-image:
    radial-gradient(circle, rgba(255, 255, 255, 0.85) 0 1px, transparent 1.8px),
    radial-gradient(circle, rgba(74, 144, 255, 0.76) 0 1px, transparent 2px);
  background-position: 28px 42px, 90px 104px;
  background-size: 178px 178px, 236px 236px;
  opacity: 0.36;
  animation: starDrift 34s linear infinite;
  pointer-events: none;
}

.landing-mesh {
  position: absolute;
  left: -8%;
  right: -8%;
  bottom: 18%;
  height: 230px;
  background:
    radial-gradient(ellipse at center, rgba(28, 158, 255, 0.22), transparent 62%),
    repeating-linear-gradient(12deg, rgba(88, 166, 255, 0.12) 0 1px, transparent 1px 20px),
    repeating-linear-gradient(102deg, rgba(155, 92, 255, 0.11) 0 1px, transparent 1px 24px);
  transform: perspective(700px) rotateX(58deg);
  opacity: 0.72;
  pointer-events: none;
}

.landing-nav,
.landing-hero,
.entry-panel,
.landing-footer {
  position: relative;
  z-index: 1;
}

.landing-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}

.brand-lockup {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  color: #f8fbff;
  text-decoration: none;
}

.brand-lockup strong {
  font-size: 23px;
  font-weight: 800;
}

.brand-mark {
  position: relative;
  width: 42px;
  height: 42px;
}

.brand-mark i {
  position: absolute;
  width: 18px;
  height: 32px;
  border-radius: 5px;
  transform: skewY(-8deg);
  box-shadow: 0 14px 34px -14px rgba(37, 211, 255, 0.8);
}

.brand-mark i:nth-child(1) {
  left: 0;
  top: 5px;
  background: linear-gradient(180deg, #5cebd8, #3b82f6);
}

.brand-mark i:nth-child(2) {
  left: 12px;
  top: 0;
  background: linear-gradient(180deg, #69f299, #53a4ff);
}

.brand-mark i:nth-child(3) {
  right: 2px;
  bottom: 4px;
  background: linear-gradient(180deg, #64d5ff, #d455ff);
}

.landing-links {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 999px;
  background: rgba(4, 10, 34, 0.34);
  backdrop-filter: blur(18px);
}

.landing-links a {
  padding: 8px 16px;
  border-radius: 999px;
  color: rgba(226, 232, 240, 0.82);
  font-size: 14px;
  text-decoration: none;
  transition: background 0.2s ease, color 0.2s ease;
}

.landing-links a:hover {
  color: #fff;
  background: rgba(80, 124, 255, 0.22);
}

.landing-hero {
  display: grid;
  grid-template-columns: minmax(580px, 0.96fr) minmax(520px, 1fr);
  align-items: center;
  gap: 42px;
  min-height: 610px;
  padding-top: 36px;
}

.hero-kicker {
  color: #55e4ff;
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0.36em;
  text-transform: uppercase;
}

.hero-copy h1 {
  margin-top: 24px;
  font-size: clamp(54px, 5vw, 78px);
  line-height: 1.08;
  font-weight: 800;
  letter-spacing: 0;
  white-space: nowrap;
  color: #fff;
  text-shadow: 0 28px 80px rgba(59, 130, 246, 0.42);
}

.hero-copy h1 span {
  color: transparent;
  background: linear-gradient(96deg, #3bdcff, #687dff 48%, #d35dff);
  -webkit-background-clip: text;
  background-clip: text;
}

.hero-copy h2 {
  margin-top: 18px;
  color: rgba(226, 232, 240, 0.95);
  font-size: clamp(28px, 2.7vw, 42px);
  font-weight: 800;
}

.hero-desc {
  max-width: 730px;
  margin-top: 24px;
  color: rgba(203, 213, 225, 0.88);
  font-size: 18px;
  line-height: 1.9;
}

.hero-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 34px;
}

.primary-action,
.secondary-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 54px;
  padding: 0 24px;
  border-radius: 14px;
  font-size: 16px;
  font-weight: 800;
  text-decoration: none;
}

.primary-action {
  color: #f8fbff;
  background: linear-gradient(100deg, #2bd4ff, #347cff 48%, #7b2cff);
  box-shadow: 0 24px 58px -24px rgba(56, 189, 248, 0.9);
}

.secondary-action {
  color: rgba(226, 232, 240, 0.9);
  border: 1px solid rgba(148, 163, 184, 0.3);
  background: rgba(4, 10, 34, 0.42);
}

.action-plane {
  width: 18px;
  height: 18px;
  background: currentColor;
  clip-path: polygon(0 8%, 100% 50%, 0 92%, 18% 54%);
}

.ai-portal {
  position: relative;
  height: 500px;
}

.portal-orbit {
  position: absolute;
  left: 6%;
  right: 4%;
  bottom: 68px;
  height: 172px;
  border: 1px solid rgba(51, 197, 255, 0.5);
  border-radius: 50%;
  transform: rotate(-6deg);
  box-shadow: 0 0 40px rgba(56, 189, 248, 0.16);
}

.portal-orbit--outer {
  left: -4%;
  right: -6%;
  bottom: 34px;
  height: 230px;
  border-color: rgba(99, 102, 241, 0.4);
}

.portal-console {
  position: absolute;
  left: 16%;
  right: 16%;
  bottom: 108px;
  height: 214px;
  border-radius: 22px;
  border: 1px solid rgba(76, 145, 255, 0.45);
  background:
    linear-gradient(145deg, rgba(15, 34, 91, 0.9), rgba(7, 15, 48, 0.78)),
    repeating-linear-gradient(90deg, rgba(125, 211, 252, 0.06) 0 1px, transparent 1px 72px);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.12), 0 34px 78px -42px rgba(37, 99, 235, 0.94);
}

.console-sidebar,
.console-lines {
  position: absolute;
  top: 48px;
  display: grid;
  gap: 15px;
}

.console-sidebar {
  left: 30px;
  width: 78px;
}

.console-lines {
  right: 32px;
  width: 94px;
}

.console-sidebar span,
.console-lines span {
  height: 14px;
  border-radius: 999px;
  background: linear-gradient(90deg, rgba(56, 189, 248, 0.82), rgba(129, 140, 248, 0.26));
}

.console-core {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 136px;
  height: 136px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  transform: translate(-50%, -50%);
  color: #e0f2fe;
  font-size: 48px;
  font-weight: 800;
  background: radial-gradient(circle, rgba(18, 72, 176, 0.92), rgba(8, 20, 62, 0.88));
  border: 8px solid rgba(30, 144, 255, 0.33);
  box-shadow: 0 0 30px rgba(56, 189, 248, 0.6), inset 0 0 24px rgba(56, 189, 248, 0.24);
}

.portal-card {
  position: absolute;
  width: 126px;
  height: 112px;
  display: grid;
  place-items: center;
  border-radius: 18px;
  border: 1px solid rgba(99, 164, 255, 0.5);
  background: linear-gradient(145deg, rgba(25, 62, 147, 0.9), rgba(43, 28, 115, 0.74));
  color: #dbeafe;
  font-size: 14px;
  font-weight: 800;
  box-shadow: 0 24px 54px -26px rgba(37, 99, 235, 0.95);
  animation: floatCard 8s ease-in-out infinite;
}

.portal-card i {
  width: 46px;
  height: 46px;
  border-radius: 14px;
  background: linear-gradient(145deg, #dbeafe, #4b80ff);
}

.portal-card--doc {
  left: 4%;
  bottom: 150px;
}

.portal-card--chart {
  right: 8%;
  top: 74px;
  animation-delay: 1.4s;
}

.portal-card--media {
  right: 2%;
  bottom: 130px;
  animation-delay: 2.3s;
}

.portal-card--chart i {
  background: linear-gradient(145deg, #32d7ff, #7c3aed);
}

.portal-card--media i {
  border-radius: 50%;
  background: linear-gradient(145deg, #f8fafc, #7c3aed);
}

.portal-glow {
  position: absolute;
  left: 22%;
  right: 22%;
  bottom: 78px;
  height: 62px;
  border-radius: 50%;
  background: radial-gradient(ellipse, rgba(22, 211, 255, 0.64), rgba(37, 99, 235, 0.24) 48%, transparent 72%);
  filter: blur(2px);
  box-shadow: 0 0 42px rgba(0, 183, 255, 0.62);
}

.entry-panel {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
  margin-top: -18px;
}

.entry-card {
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr);
  align-items: center;
  gap: 18px;
  min-height: 128px;
  padding: 22px 24px;
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.26);
  background: linear-gradient(145deg, rgba(20, 42, 96, 0.56), rgba(7, 14, 45, 0.48));
  color: #e8f0ff;
  text-decoration: none;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(20px);
  transition: transform 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
}

.entry-card:hover {
  transform: translateY(-4px);
  border-color: rgba(89, 199, 255, 0.56);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.12), 0 26px 70px -42px rgba(56, 189, 248, 0.78);
}

.entry-card strong {
  display: block;
  grid-column: 2;
  font-size: 20px;
  font-weight: 800;
}

.entry-card small {
  display: block;
  grid-column: 2;
  margin-top: 8px;
  color: rgba(203, 213, 225, 0.82);
  line-height: 1.55;
}

.entry-icon {
  width: 70px;
  height: 70px;
  border-radius: 16px;
  background: linear-gradient(145deg, #4b8dff, #244fc6);
  box-shadow: 0 18px 42px -22px rgba(56, 189, 248, 0.9);
}

.entry-icon--student {
  background: linear-gradient(145deg, #35d0ff, #6d5cff);
}

.entry-icon--admin {
  background: linear-gradient(145deg, #5eead4, #2563eb);
}

.landing-footer {
  margin-top: 22px;
  text-align: center;
  color: rgba(148, 163, 184, 0.72);
  font-size: 14px;
}

@keyframes starDrift {
  to {
    background-position: -178px 248px, -146px 186px;
  }
}

@keyframes floatCard {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-12px);
  }
}

/* Spatial depth refinement */
.landing-page {
  background:
    radial-gradient(circle at 12% 18%, rgba(0, 109, 255, 0.48), transparent 30%),
    radial-gradient(circle at 84% 16%, rgba(118, 44, 255, 0.38), transparent 35%),
    radial-gradient(circle at 50% 90%, rgba(0, 174, 255, 0.1), transparent 34%),
    linear-gradient(128deg, #020918 0%, #061944 42%, #160629 100%);
}

.landing-page::after {
  content: '';
  position: absolute;
  left: -10%;
  right: -10%;
  bottom: 13%;
  height: 320px;
  background:
    radial-gradient(ellipse at 20% 54%, rgba(0, 174, 255, 0.18), transparent 32%),
    radial-gradient(ellipse at 78% 48%, rgba(151, 71, 255, 0.17), transparent 34%),
    repeating-linear-gradient(11deg, rgba(65, 137, 255, 0.13) 0 1px, transparent 1px 20px),
    repeating-linear-gradient(102deg, rgba(151, 71, 255, 0.1) 0 1px, transparent 1px 24px);
  transform: perspective(840px) rotateX(62deg);
  opacity: 0.72;
  pointer-events: none;
  mask-image: linear-gradient(to bottom, transparent, #000 18%, #000 76%, transparent);
}

.ai-portal {
  filter: drop-shadow(0 34px 90px rgba(0, 0, 0, 0.4));
}

.portal-console {
  border-color: rgba(95, 164, 255, 0.54);
  background:
    linear-gradient(145deg, rgba(13, 44, 122, 0.92), rgba(5, 12, 42, 0.84)),
    linear-gradient(90deg, rgba(56, 189, 248, 0.08), transparent 32%, rgba(124, 58, 237, 0.08)),
    repeating-linear-gradient(90deg, rgba(125, 211, 252, 0.055) 0 1px, transparent 1px 72px);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.16),
    inset 0 -24px 60px rgba(0, 0, 0, 0.22),
    0 34px 90px -42px rgba(37, 99, 235, 0.98);
}

.portal-console::before,
.portal-console::after {
  content: '';
  position: absolute;
  pointer-events: none;
}

.portal-console::before {
  left: 22px;
  right: 22px;
  top: 16px;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(105, 211, 255, 0.72), transparent);
}

.portal-console::after {
  left: 50%;
  bottom: -50px;
  width: 360px;
  height: 86px;
  border-radius: 50%;
  transform: translateX(-50%);
  background: radial-gradient(ellipse, rgba(0, 184, 255, 0.45), transparent 68%);
  filter: blur(9px);
}

.console-core {
  box-shadow:
    0 0 36px rgba(56, 189, 248, 0.72),
    0 0 0 16px rgba(37, 99, 235, 0.12),
    inset 0 0 26px rgba(56, 189, 248, 0.28);
}

.portal-card,
.entry-card {
  background:
    radial-gradient(circle at 24% 16%, rgba(255, 255, 255, 0.16), transparent 34%),
    linear-gradient(145deg, rgba(26, 58, 132, 0.74), rgba(7, 14, 45, 0.56));
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.12),
    0 28px 74px -48px rgba(37, 99, 235, 0.78);
}

@media (max-width: 1200px) {
  .landing-page {
    padding: 28px;
  }

  .landing-hero {
    grid-template-columns: 1fr;
    min-height: 0;
  }

  .ai-portal {
    height: 420px;
  }

  .entry-panel {
    grid-template-columns: 1fr;
    margin-top: 20px;
  }
}

@media (max-width: 720px) {
  .landing-nav,
  .hero-actions {
    align-items: flex-start;
    flex-direction: column;
  }

  .landing-links {
    width: 100%;
    justify-content: space-between;
  }

  .landing-links a {
    padding: 8px 10px;
    font-size: 13px;
  }

  .hero-copy h1 {
    white-space: normal;
    font-size: 42px;
  }

  .ai-portal {
    display: none;
  }

  .entry-card {
    grid-template-columns: 56px minmax(0, 1fr);
  }

  .entry-icon {
    width: 54px;
    height: 54px;
  }
}
</style>
