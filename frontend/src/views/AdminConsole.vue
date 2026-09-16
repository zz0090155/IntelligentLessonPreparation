<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

const DEFAULT_ADMIN_API_BASE =
  String(import.meta.env.VITE_ADMIN_API_BASE || import.meta.env.VITE_API_BASE || '/api').trim() || '/api'

const apiBase = ref(localStorage.getItem('admin_api_base') || DEFAULT_ADMIN_API_BASE)
const adminToken = ref('')
const adminUser = ref(null)
const loginForm = ref({
  username: '',
  password: '',
})

const loginLoading = ref(false)
const panelLoading = ref(false)
const dashboardLoading = ref(false)
const usersLoading = ref(false)
const userFeaturesLoading = ref(false)
const userFeaturesSaving = ref(false)
const auditLoading = ref(false)

const loginError = ref('')
const panelError = ref('')
const panelNotice = ref('')

const roleFilter = ref('teacher')
const statusFilter = ref('all')
const keyword = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

const users = ref([])
const selectedUserIds = ref([])
const selectedUserId = ref(null)

const bulkStatusDraft = ref({
  disabled_reason: '',
  disabled_until: '',
})

const featureCatalog = ref([])
const userFeatures = ref([])

const dashboardHours = ref(24)
const dashboardData = ref(null)
const auditLogs = ref([])

const isAuthed = computed(() => !!adminToken.value)
const normalizedApiBase = computed(() => normalizeBase(apiBase.value))
const selectedCount = computed(() => selectedUserIds.value.length)
const allOnPageSelected = computed(() => {
  if (!users.value.length) return false
  return users.value.every((item) => selectedUserIds.value.includes(item.id))
})
const selectedUserName = computed(() => {
  if (!selectedUserId.value) return ''
  const found = users.value.find((item) => item.id === selectedUserId.value)
  return found?.name || ''
})

function normalizeBase(raw) {
  const value = String(raw || '').trim()
  if (!value) return '/api'
  if (value === '/') return ''
  return value.endsWith('/') ? value.slice(0, -1) : value
}

function getApiErrorMessage(payload, status) {
  const detail = String(payload?.detail || payload?.message || '').trim()
  if (detail) return detail
  const code = Number(status || 0)
  if (code === 400) return '请求参数不正确，请检查后重试'
  if (code === 401) return '管理员登录已失效，请重新登录'
  if (code === 403) return '当前账号没有管理员权限'
  if (code === 404) return '目标数据不存在或已被删除'
  if (code === 409 || code === 422) return '请求无法处理，请调整后重试'
  if (code >= 500) return '服务器暂时不可用，请稍后再试'
  return '请求失败，请稍后再试'
}

function buildApiUrl(path) {
  const prefix = normalizedApiBase.value
  const normalizedPath = String(path || '').startsWith('/') ? path : `/${path}`
  return `${prefix}${normalizedPath}`
}

async function requestApi(path, options = {}) {
  const { method = 'GET', body, auth = true } = options
  const headers = {}
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (auth && adminToken.value) {
    headers.Authorization = `Bearer ${adminToken.value}`
  }

  const response = await fetch(buildApiUrl(path), {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })

  const data = await response.json().catch(() => ({}))
  if (!response.ok) {
    if (response.status === 401 && auth) {
      clearAdminSession()
    }
    throw new Error(getApiErrorMessage(data, response.status))
  }
  return data
}

function clearPanelMessage() {
  panelError.value = ''
  panelNotice.value = ''
}

function clearAdminSession() {
  adminToken.value = ''
  adminUser.value = null
  loginForm.value.username = ''
  loginForm.value.password = ''
  localStorage.removeItem('admin_token')
  localStorage.removeItem('admin_user')
  localStorage.removeItem('admin_last_username')
  selectedUserIds.value = []
  selectedUserId.value = null
  users.value = []
  userFeatures.value = []
  dashboardData.value = null
  auditLogs.value = []
}

function persistApiBase() {
  const value = normalizeBase(apiBase.value)
  apiBase.value = value
  localStorage.setItem('admin_api_base', value)
  clearPanelMessage()
  panelNotice.value = `已保存管理接口地址：${value}`
}

async function handleLogin() {
  if (loginLoading.value) return
  loginError.value = ''
  clearPanelMessage()

  const username = String(loginForm.value.username || '').trim()
  const password = String(loginForm.value.password || '')
  if (!username || !password) {
    loginError.value = '请输入管理员账号和密码'
    return
  }

  loginLoading.value = true
  try {
    const data = await requestApi('/admin/login', {
      method: 'POST',
      auth: false,
      body: { username, password },
    })

    adminToken.value = String(data?.token || '')
    adminUser.value = data?.user || { name: username, role: 'admin' }
    apiBase.value = normalizeBase(apiBase.value)
    localStorage.setItem('admin_api_base', apiBase.value)
    loginForm.value.password = ''

    await bootstrapAdminPanel()
  } catch (err) {
    loginError.value = err instanceof Error ? err.message : '管理员登录失败'
  } finally {
    loginLoading.value = false
  }
}

function handleLogout() {
  clearAdminSession()
  clearPanelMessage()
  panelNotice.value = '已退出管理员控制台'
}

async function bootstrapAdminPanel() {
  panelLoading.value = true
  clearPanelMessage()
  try {
    await Promise.all([loadFeatureCatalog(), loadDashboard(), loadUsers(), loadAuditLogs()])
  } catch (err) {
    panelError.value = err instanceof Error ? err.message : '管理台数据加载失败'
  } finally {
    panelLoading.value = false
  }
}

async function loadFeatureCatalog() {
  const data = await requestApi('/admin/features')
  featureCatalog.value = Array.isArray(data?.items) ? data.items : []
}

async function loadDashboard() {
  dashboardLoading.value = true
  try {
    const data = await requestApi(`/admin/dashboard/overview?hours=${Number(dashboardHours.value || 24)}`)
    dashboardData.value = data
  } catch (err) {
    panelError.value = err instanceof Error ? err.message : '运维看板加载失败'
  } finally {
    dashboardLoading.value = false
  }
}

async function loadAuditLogs() {
  auditLoading.value = true
  try {
    const data = await requestApi('/admin/audit_logs?limit=120')
    auditLogs.value = Array.isArray(data?.items) ? data.items : []
  } catch (err) {
    panelError.value = err instanceof Error ? err.message : '审计日志加载失败'
  } finally {
    auditLoading.value = false
  }
}

async function loadUsers(resetPage = false) {
  if (resetPage) {
    page.value = 1
  }
  usersLoading.value = true
  clearPanelMessage()
  try {
    const params = new URLSearchParams({
      role: roleFilter.value,
      status: statusFilter.value,
      page: String(page.value),
      page_size: String(pageSize.value),
    })
    if (String(keyword.value || '').trim()) {
      params.set('keyword', String(keyword.value || '').trim())
    }

    const data = await requestApi(`/admin/users?${params.toString()}`)
    users.value = Array.isArray(data?.items) ? data.items : []
    total.value = Number(data?.total || 0)

    const availableIds = new Set(users.value.map((item) => item.id))
    selectedUserIds.value = selectedUserIds.value.filter((id) => availableIds.has(id))
    if (selectedUserId.value && !availableIds.has(selectedUserId.value)) {
      selectedUserId.value = null
      userFeatures.value = []
    }
  } catch (err) {
    panelError.value = err instanceof Error ? err.message : '用户列表加载失败'
  } finally {
    usersLoading.value = false
  }
}

function toggleSelectAll(event) {
  const checked = !!event?.target?.checked
  if (!checked) {
    selectedUserIds.value = []
    return
  }
  selectedUserIds.value = users.value.map((item) => item.id)
}

function toggleUserSelection(id, checked) {
  const next = new Set(selectedUserIds.value)
  if (checked) {
    next.add(id)
  } else {
    next.delete(id)
  }
  selectedUserIds.value = Array.from(next)
}

async function applyBulkStatus(enabled) {
  if (!selectedUserIds.value.length) {
    panelError.value = '请至少选择一个用户'
    return
  }

  const body = {
    role: roleFilter.value,
    user_ids: selectedUserIds.value,
    enabled: !!enabled,
    disabled_reason: enabled ? null : String(bulkStatusDraft.value.disabled_reason || '').trim() || null,
    disabled_until: enabled ? null : String(bulkStatusDraft.value.disabled_until || '').trim() || null,
  }

  usersLoading.value = true
  clearPanelMessage()
  try {
    await requestApi('/admin/users/status/bulk', { method: 'POST', body })
    panelNotice.value = enabled
      ? `已批量恢复 ${selectedUserIds.value.length} 个账号`
      : `已批量停用 ${selectedUserIds.value.length} 个账号`
    await Promise.all([loadUsers(), loadDashboard(), loadAuditLogs()])
  } catch (err) {
    panelError.value = err instanceof Error ? err.message : '批量更新账号状态失败'
  } finally {
    usersLoading.value = false
  }
}

async function setSingleUserStatus(item, enabled) {
  const body = {
    enabled: !!enabled,
    disabled_reason: enabled ? null : String(item.disabled_reason || '').trim() || null,
    disabled_until: enabled ? null : String(item.disabled_until || '').trim() || null,
  }

  usersLoading.value = true
  clearPanelMessage()
  try {
    await requestApi(`/admin/users/${roleFilter.value}/${item.id}/status`, {
      method: 'PATCH',
      body,
    })
    panelNotice.value = enabled ? `已恢复 ${item.name}` : `已停用 ${item.name}`
    await Promise.all([loadUsers(), loadDashboard(), loadAuditLogs()])
  } catch (err) {
    panelError.value = err instanceof Error ? err.message : '账号状态更新失败'
  } finally {
    usersLoading.value = false
  }
}

async function openUserFeaturePanel(item) {
  selectedUserId.value = item.id
  userFeaturesLoading.value = true
  clearPanelMessage()
  try {
    const data = await requestApi(`/admin/users/${roleFilter.value}/${item.id}/features`)
    const items = Array.isArray(data?.items) ? data.items : []
    userFeatures.value = items.map((feature) => ({
      ...feature,
      daily_limit: feature.daily_limit == null ? '' : String(feature.daily_limit),
    }))
  } catch (err) {
    panelError.value = err instanceof Error ? err.message : '用户功能配置加载失败'
  } finally {
    userFeaturesLoading.value = false
  }
}

function buildFeatureGrantPayload() {
  const grants = []
  userFeatures.value.forEach((item) => {
    const enabled = !!item.enabled
    const limitText = String(item.daily_limit ?? '').trim()
    const parsedLimit = Number(limitText)
    const hasLimit = limitText !== '' && Number.isFinite(parsedLimit) && parsedLimit > 0

    if (!enabled || hasLimit || item.is_override) {
      grants.push({
        feature_key: item.feature_key,
        enabled,
        daily_limit: hasLimit ? Math.floor(parsedLimit) : null,
      })
    }
  })
  return grants
}

async function saveSelectedUserFeatures() {
  if (!selectedUserId.value) {
    panelError.value = '请先选择一个用户再保存功能权限'
    return
  }

  userFeaturesSaving.value = true
  clearPanelMessage()
  try {
    await requestApi(`/admin/users/${roleFilter.value}/${selectedUserId.value}/features`, {
      method: 'PUT',
      body: { grants: buildFeatureGrantPayload() },
    })
    panelNotice.value = '用户功能权限已保存'
    await Promise.all([
      loadUsers(),
      loadDashboard(),
      loadAuditLogs(),
      openUserFeaturePanel({ id: selectedUserId.value }),
    ])
  } catch (err) {
    panelError.value = err instanceof Error ? err.message : '保存用户功能权限失败'
  } finally {
    userFeaturesSaving.value = false
  }
}

function formatTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return `${date.toLocaleDateString()} ${date.toLocaleTimeString()}`
}

function goLanding() {
  router.push('/')
}

watch(
  () => roleFilter.value,
  async () => {
    selectedUserIds.value = []
    selectedUserId.value = null
    userFeatures.value = []
    if (isAuthed.value) {
      await loadUsers(true)
    }
  }
)

onMounted(() => {
  localStorage.removeItem('admin_token')
  localStorage.removeItem('admin_user')
  localStorage.removeItem('admin_last_username')
})
</script>

<template>
  <div class="admin-console min-h-screen">
    <div class="admin-console__bg"></div>
    <main class="admin-shell mx-auto px-4 py-6 md:px-8 md:py-10">
      <section v-if="!isAuthed" class="admin-login-card">
        <div class="login-head">
          <p class="eyebrow">Remote Admin Console</p>
          <h1>远程管理台</h1>
          <p>在本地浏览器直接管理部署环境账号、功能权限与运维统计。</p>
        </div>

        <label class="field">
          <span>管理接口地址</span>
          <input v-model="apiBase" placeholder="例如 /api 或 http://47.239.205.114/api" />
        </label>

        <label class="field">
          <span>管理员账号</span>
          <input v-model="loginForm.username" autocomplete="username" />
        </label>

        <label class="field">
          <span>管理员密码</span>
          <input
            v-model="loginForm.password"
            type="password"
            autocomplete="current-password"
            @keyup.enter="handleLogin"
          />
        </label>

        <p v-if="loginError" class="error-text">{{ loginError }}</p>

        <div class="actions">
          <button type="button" class="btn ghost" @click="goLanding">返回首页</button>
          <button type="button" class="btn primary" :disabled="loginLoading" @click="handleLogin">
            {{ loginLoading ? '登录中...' : '进入管理台' }}
          </button>
        </div>
      </section>

      <section v-else class="admin-panel">
        <header class="panel-header">
          <div>
            <p class="eyebrow">Admin</p>
            <h2>远程管理控制台</h2>
            <p class="desc">
              当前管理员：{{ adminUser?.name || 'admin' }} · 接口地址：{{ normalizedApiBase }}
            </p>
          </div>
          <div class="header-actions">
            <button type="button" class="btn ghost" @click="persistApiBase">保存接口地址</button>
            <button type="button" class="btn ghost" :disabled="panelLoading" @click="bootstrapAdminPanel">
              {{ panelLoading ? '刷新中...' : '刷新全部数据' }}
            </button>
            <button type="button" class="btn danger" @click="handleLogout">退出登录</button>
          </div>
        </header>

        <p v-if="panelError" class="error-text mt-3">{{ panelError }}</p>
        <p v-else-if="panelNotice" class="ok-text mt-3">{{ panelNotice }}</p>

        <section class="admin-card mt-4">
          <div class="section-head">
            <h3>运维看板</h3>
            <div class="inline-actions">
              <select v-model.number="dashboardHours" class="compact-select">
                <option :value="6">近 6 小时</option>
                <option :value="24">近 24 小时</option>
                <option :value="72">近 3 天</option>
                <option :value="168">近 7 天</option>
              </select>
              <button type="button" class="btn ghost" :disabled="dashboardLoading" @click="loadDashboard">
                {{ dashboardLoading ? '加载中...' : '刷新看板' }}
              </button>
            </div>
          </div>

          <div class="metrics-grid" v-if="dashboardData?.summary">
            <article class="metric-item">
              <label>请求总量</label>
              <strong>{{ dashboardData.summary.total_requests || 0 }}</strong>
            </article>
            <article class="metric-item">
              <label>活跃用户</label>
              <strong>{{ dashboardData.summary.active_users || 0 }}</strong>
            </article>
            <article class="metric-item">
              <label>平均耗时</label>
              <strong>{{ dashboardData.summary.avg_latency_ms || 0 }} ms</strong>
            </article>
            <article class="metric-item warn">
              <label>5xx 错误</label>
              <strong>{{ dashboardData.summary.error_requests || 0 }}</strong>
            </article>
            <article class="metric-item warn">
              <label>403 拒绝</label>
              <strong>{{ dashboardData.summary.forbidden_requests || 0 }}</strong>
            </article>
            <article class="metric-item warn">
              <label>429 限流</label>
              <strong>{{ dashboardData.summary.throttled_requests || 0 }}</strong>
            </article>
            <article class="metric-item">
              <label>教师账号</label>
              <strong>{{ dashboardData.accounts?.teacher_total || 0 }}</strong>
            </article>
            <article class="metric-item">
              <label>学生账号</label>
              <strong>{{ dashboardData.accounts?.student_total || 0 }}</strong>
            </article>
            <article class="metric-item warn">
              <label>已停用账号</label>
              <strong>{{ dashboardData.accounts?.disabled_total || 0 }}</strong>
            </article>
          </div>

          <div class="split-grid mt-4">
            <div>
              <h4>功能调用排行</h4>
              <ul class="compact-list">
                <li v-for="item in dashboardData?.feature_stats || []" :key="item.feature_key">
                  <span>{{ item.label || item.feature_key }}</span>
                  <small>{{ item.count }} 次 · 错误率 {{ Number(item.error_rate || 0) * 100 }}%</small>
                </li>
              </ul>
            </div>
            <div>
              <h4>热点接口</h4>
              <ul class="compact-list">
                <li v-for="item in dashboardData?.top_endpoints || []" :key="item.endpoint">
                  <span>{{ item.endpoint }}</span>
                  <small>{{ item.count }} 次</small>
                </li>
              </ul>
            </div>
          </div>
        </section>

        <section class="admin-card mt-4">
          <div class="section-head">
            <h3>账号控制</h3>
            <div class="inline-actions">
              <select v-model="roleFilter" class="compact-select">
                <option value="teacher">教师</option>
                <option value="student">学生</option>
              </select>
              <select v-model="statusFilter" class="compact-select">
                <option value="all">全部状态</option>
                <option value="enabled">仅启用</option>
                <option value="disabled">仅停用</option>
              </select>
              <input v-model="keyword" class="compact-input" placeholder="用户名/邮箱" @keyup.enter="loadUsers(true)" />
              <button type="button" class="btn ghost" :disabled="usersLoading" @click="loadUsers(true)">
                {{ usersLoading ? '查询中...' : '查询' }}
              </button>
            </div>
          </div>

          <div class="bulk-toolbar">
            <input v-model="bulkStatusDraft.disabled_reason" class="compact-input" placeholder="批量停用原因（可选）" />
            <input
              v-model="bulkStatusDraft.disabled_until"
              class="compact-input"
              placeholder="停用截止时间 ISO8601（可选）"
            />
            <button type="button" class="btn warn" :disabled="usersLoading" @click="applyBulkStatus(false)">
              批量停用 ({{ selectedCount }})
            </button>
            <button type="button" class="btn success" :disabled="usersLoading" @click="applyBulkStatus(true)">
              批量恢复
            </button>
          </div>

          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>
                    <input type="checkbox" :checked="allOnPageSelected" @change="toggleSelectAll" />
                  </th>
                  <th>ID</th>
                  <th>用户名</th>
                  <th>邮箱</th>
                  <th>状态</th>
                  <th>停用原因</th>
                  <th>功能覆写</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in users" :key="item.id">
                  <td>
                    <input
                      type="checkbox"
                      :checked="selectedUserIds.includes(item.id)"
                      @change="toggleUserSelection(item.id, $event.target.checked)"
                    />
                  </td>
                  <td>{{ item.id }}</td>
                  <td>{{ item.name }}</td>
                  <td>{{ item.email || '-' }}</td>
                  <td>
                    <span :class="item.status === 'enabled' ? 'tag ok' : 'tag warn'">
                      {{ item.status === 'enabled' ? '启用中' : '已停用' }}
                    </span>
                  </td>
                  <td>
                    <input v-model="item.disabled_reason" class="mini-input" placeholder="可编辑" />
                    <input v-model="item.disabled_until" class="mini-input mt-1" placeholder="ISO8601" />
                  </td>
                  <td>{{ item.feature_override_count || 0 }}</td>
                  <td>
                    <div class="row-actions">
                      <button type="button" class="btn tiny ghost" @click="openUserFeaturePanel(item)">功能配置</button>
                      <button
                        v-if="item.status === 'enabled'"
                        type="button"
                        class="btn tiny warn"
                        @click="setSingleUserStatus(item, false)"
                      >
                        停用
                      </button>
                      <button
                        v-else
                        type="button"
                        class="btn tiny success"
                        @click="setSingleUserStatus(item, true)"
                      >
                        恢复
                      </button>
                    </div>
                  </td>
                </tr>
                <tr v-if="!users.length">
                  <td colspan="8" class="empty-row">暂无用户数据</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="pager mt-3">
            <span>共 {{ total }} 条</span>
            <div class="inline-actions">
              <button type="button" class="btn tiny ghost" :disabled="page <= 1" @click="page -= 1; loadUsers()">上一页</button>
              <span>第 {{ page }} 页</span>
              <button
                type="button"
                class="btn tiny ghost"
                :disabled="page * pageSize >= total"
                @click="page += 1; loadUsers()"
              >
                下一页
              </button>
            </div>
          </div>
        </section>

        <section class="admin-card mt-4">
          <div class="section-head">
            <h3>功能权限</h3>
            <p class="muted">仅保留单用户精细配置。</p>
          </div>

          <div>
            <h4>
              单用户配置
              <span v-if="selectedUserId" class="muted">（{{ selectedUserName || `#${selectedUserId}` }}）</span>
            </h4>
            <p v-if="userFeaturesLoading" class="muted">正在加载功能配置...</p>
            <div v-else class="feature-list">
              <div v-for="item in userFeatures" :key="item.feature_key" class="feature-row">
                <div>
                  <strong>{{ item.label || item.feature_key }}</strong>
                  <small>{{ item.feature_key }}</small>
                </div>
                <label class="switch-wrap">
                  <input type="checkbox" v-model="item.enabled" />
                  <span>启用</span>
                </label>
                <input
                  v-model="item.daily_limit"
                  class="mini-input"
                  type="number"
                  min="1"
                  placeholder="日限额"
                />
              </div>
            </div>

            <div class="inline-actions mt-3">
              <button
                type="button"
                class="btn primary"
                :disabled="!selectedUserId || userFeaturesSaving"
                @click="saveSelectedUserFeatures"
              >
                {{ userFeaturesSaving ? '保存中...' : '保存当前用户配置' }}
              </button>
            </div>
          </div>

          <details class="catalog mt-4">
            <summary>可配置功能目录（{{ featureCatalog.length }}）</summary>
            <div class="catalog-grid mt-2">
              <div v-for="feature in featureCatalog" :key="feature.feature_key" class="catalog-item">
                <strong>{{ feature.label || feature.feature_key }}</strong>
                <small>{{ feature.feature_key }} · {{ (feature.roles || []).join(' / ') }}</small>
              </div>
            </div>
          </details>
        </section>

        <section class="admin-card mt-4">
          <div class="section-head">
            <h3>审计日志</h3>
            <button type="button" class="btn ghost" :disabled="auditLoading" @click="loadAuditLogs">
              {{ auditLoading ? '加载中...' : '刷新日志' }}
            </button>
          </div>
          <div class="log-list">
            <article v-for="log in auditLogs" :key="log.id" class="log-item">
              <header>
                <strong>{{ log.action }}</strong>
                <small>{{ formatTime(log.created_at) }}</small>
              </header>
              <p>
                管理员 {{ log.admin_name || '-' }} · {{ log.target_role || '-' }}
                <span v-if="log.target_user_id">#{{ log.target_user_id }}</span>
              </p>
              <pre>{{ JSON.stringify(log.detail || {}, null, 2) }}</pre>
            </article>
            <p v-if="!auditLogs.length" class="muted">暂无审计记录</p>
          </div>
        </section>
      </section>
    </main>
  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600;700&family=Noto+Sans+SC:wght@400;500;700&display=swap');

.admin-console {
  position: relative;
  color: #e2e8f0;
  font-family: 'Outfit', 'Noto Sans SC', sans-serif;
  background: #020617;
  overflow: hidden;
}

.admin-console__bg {
  position: absolute;
  inset: -30% -10% auto -10%;
  height: 70%;
  background:
    radial-gradient(circle at 20% 30%, rgba(16, 185, 129, 0.24), transparent 58%),
    radial-gradient(circle at 72% 22%, rgba(59, 130, 246, 0.24), transparent 60%),
    radial-gradient(circle at 55% 75%, rgba(236, 72, 153, 0.2), transparent 65%);
  filter: blur(18px);
  pointer-events: none;
}

.admin-shell {
  position: relative;
  z-index: 1;
  max-width: 1240px;
}

.admin-login-card,
.admin-card,
.panel-header {
  background: rgba(15, 23, 42, 0.76);
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 18px;
  backdrop-filter: blur(10px);
}

.admin-login-card {
  max-width: 460px;
  margin: 8vh auto 0;
  padding: 28px;
}

.login-head h1,
.panel-header h2 {
  margin: 6px 0;
  font-size: 1.55rem;
  font-weight: 700;
  letter-spacing: 0.01em;
}

.login-head p,
.desc,
.muted {
  color: #94a3b8;
  font-size: 0.92rem;
}

.eyebrow {
  font-size: 0.74rem;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: #67e8f9;
}

.field {
  display: block;
  margin-top: 14px;
}

.field span {
  display: block;
  margin-bottom: 6px;
  font-size: 0.84rem;
  color: #cbd5e1;
}

input,
select,
textarea {
  width: 100%;
  border: 1px solid rgba(148, 163, 184, 0.35);
  background: rgba(15, 23, 42, 0.8);
  color: #f8fafc;
  border-radius: 10px;
  padding: 10px 12px;
  outline: none;
}

input:focus,
select:focus,
textarea:focus {
  border-color: rgba(45, 212, 191, 0.9);
  box-shadow: 0 0 0 1px rgba(45, 212, 191, 0.55);
}

.actions,
.inline-actions,
.header-actions,
.bulk-toolbar,
.row-actions,
.pager {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.actions {
  justify-content: flex-end;
  margin-top: 14px;
}

.btn {
  border: none;
  border-radius: 10px;
  padding: 9px 14px;
  font-size: 0.88rem;
  cursor: pointer;
  transition: transform 0.15s ease, opacity 0.15s ease;
}

.btn:hover {
  transform: translateY(-1px);
}

.btn:disabled {
  cursor: not-allowed;
  opacity: 0.55;
  transform: none;
}

.btn.primary {
  color: #082f49;
  background: linear-gradient(135deg, #67e8f9, #5eead4);
  font-weight: 600;
}

.btn.ghost {
  color: #cbd5e1;
  background: rgba(148, 163, 184, 0.18);
}

.btn.warn {
  color: #ffe4b5;
  background: rgba(217, 119, 6, 0.35);
}

.btn.success {
  color: #dcfce7;
  background: rgba(22, 163, 74, 0.35);
}

.btn.danger {
  color: #fee2e2;
  background: rgba(220, 38, 38, 0.42);
}

.btn.tiny {
  padding: 6px 10px;
  font-size: 0.78rem;
}

.panel-header {
  padding: 18px;
  display: flex;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
}

.admin-card {
  padding: 16px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}

.section-head h3 {
  margin: 0;
  font-size: 1.06rem;
  font-weight: 600;
}

h4 {
  margin: 0 0 8px;
  font-size: 0.95rem;
  color: #cbd5e1;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(145px, 1fr));
  gap: 10px;
}

.metric-item {
  border-radius: 12px;
  border: 1px solid rgba(148, 163, 184, 0.24);
  padding: 10px;
  background: rgba(15, 23, 42, 0.54);
}

.metric-item label {
  font-size: 0.76rem;
  color: #94a3b8;
}

.metric-item strong {
  display: block;
  margin-top: 6px;
  font-size: 1.18rem;
}

.metric-item.warn {
  border-color: rgba(251, 191, 36, 0.45);
}

.split-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 14px;
}

.compact-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 8px;
}

.compact-list li {
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 10px;
  padding: 8px 10px;
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

.compact-list small {
  color: #94a3b8;
  white-space: nowrap;
}

.bulk-toolbar {
  margin: 12px 0;
}

.compact-select,
.compact-input,
.mini-input {
  width: auto;
  min-width: 130px;
}

.compact-input {
  min-width: 180px;
}

.mini-input {
  width: 180px;
  font-size: 0.78rem;
  padding: 6px 8px;
}

.table-wrap {
  overflow-x: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.84rem;
}

th,
td {
  border-bottom: 1px solid rgba(148, 163, 184, 0.2);
  padding: 8px 6px;
  text-align: left;
  vertical-align: top;
}

th {
  color: #cbd5e1;
  font-weight: 600;
}

.empty-row {
  text-align: center;
  color: #94a3b8;
  padding: 18px 6px;
}

.tag {
  display: inline-block;
  border-radius: 999px;
  padding: 2px 8px;
  font-size: 0.75rem;
}

.tag.ok {
  background: rgba(22, 163, 74, 0.28);
  color: #bbf7d0;
}

.tag.warn {
  background: rgba(217, 119, 6, 0.25);
  color: #fde68a;
}

.feature-list {
  display: grid;
  gap: 8px;
}

.feature-row {
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: 12px;
  padding: 10px;
  display: grid;
  grid-template-columns: minmax(130px, 1fr) auto 110px;
  align-items: center;
  gap: 10px;
}

.feature-row small {
  display: block;
  color: #94a3b8;
  margin-top: 4px;
}

.switch-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #cbd5e1;
  font-size: 0.84rem;
}

.catalog {
  border: 1px dashed rgba(148, 163, 184, 0.35);
  border-radius: 12px;
  padding: 10px;
}

.catalog summary {
  cursor: pointer;
  color: #cbd5e1;
}

.catalog-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 8px;
}

.catalog-item {
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 10px;
  padding: 8px 10px;
}

.catalog-item small {
  display: block;
  margin-top: 4px;
  color: #94a3b8;
}

.log-list {
  display: grid;
  gap: 10px;
  max-height: 480px;
  overflow-y: auto;
}

.log-item {
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 12px;
  padding: 10px;
}

.log-item header {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

.log-item small,
.log-item p {
  color: #94a3b8;
  font-size: 0.78rem;
}

.log-item pre {
  margin: 8px 0 0;
  background: rgba(15, 23, 42, 0.9);
  border-radius: 8px;
  padding: 8px;
  overflow-x: auto;
  color: #e2e8f0;
  font-size: 0.74rem;
}

.error-text,
.ok-text {
  font-size: 0.84rem;
  margin-top: 10px;
}

.error-text {
  color: #fca5a5;
}

.ok-text {
  color: #86efac;
}

@media (max-width: 860px) {
  .feature-row {
    grid-template-columns: 1fr;
  }

  .mini-input,
  .compact-input,
  .compact-select {
    width: 100%;
    min-width: 0;
  }

  .row-actions {
    flex-direction: column;
    align-items: stretch;
  }
}

/* Unified premium admin skin */
.admin-console {
  min-height: 100vh;
  background:
    radial-gradient(circle at 14% 12%, rgba(0, 132, 255, 0.28), transparent 34%),
    radial-gradient(circle at 86% 12%, rgba(124, 58, 237, 0.24), transparent 36%),
    linear-gradient(128deg, #061b47 0%, #08143b 44%, #17072e 100%);
}

.admin-console::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image:
    radial-gradient(circle, rgba(255, 255, 255, 0.72) 0 1px, transparent 1.8px),
    radial-gradient(circle, rgba(74, 144, 255, 0.64) 0 1px, transparent 2px);
  background-size: 182px 182px, 244px 244px;
  opacity: 0.22;
  pointer-events: none;
}

.admin-console::after {
  content: '';
  position: absolute;
  left: -10%;
  right: -10%;
  bottom: 12%;
  height: 260px;
  background:
    radial-gradient(ellipse at center, rgba(28, 158, 255, 0.18), transparent 62%),
    repeating-linear-gradient(12deg, rgba(88, 166, 255, 0.08) 0 1px, transparent 1px 22px),
    repeating-linear-gradient(102deg, rgba(155, 92, 255, 0.08) 0 1px, transparent 1px 24px);
  transform: perspective(700px) rotateX(58deg);
  opacity: 0.62;
  pointer-events: none;
}

.admin-console__bg {
  inset: -22% -16% auto -16%;
  height: 74%;
  background:
    radial-gradient(circle at 18% 25%, rgba(45, 212, 191, 0.18), transparent 54%),
    radial-gradient(circle at 72% 20%, rgba(59, 130, 246, 0.24), transparent 58%),
    radial-gradient(circle at 88% 66%, rgba(217, 70, 239, 0.18), transparent 58%);
  filter: blur(20px);
}

.admin-shell {
  max-width: 1440px;
}

.admin-login-card,
.admin-card,
.panel-header {
  border-color: rgba(148, 163, 184, 0.24);
  background: linear-gradient(145deg, rgba(14, 30, 75, 0.82), rgba(6, 12, 38, 0.72));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.08), 0 28px 80px -52px rgba(37, 99, 235, 0.8);
  backdrop-filter: blur(22px);
}

.admin-login-card {
  max-width: 560px;
  margin-top: 9vh;
  padding: 44px;
  border-radius: 24px;
}

.login-head h1,
.panel-header h2 {
  color: transparent;
  background: linear-gradient(100deg, #45dfff, #7aa2ff 52%, #d65cff);
  -webkit-background-clip: text;
  background-clip: text;
  font-size: clamp(30px, 3vw, 42px);
  font-weight: 800;
}

.panel-header {
  padding: 26px;
  border-radius: 22px;
}

.panel-header h2 {
  font-size: 30px;
}

.desc,
.muted,
.login-head p {
  color: rgba(203, 213, 225, 0.78);
}

.eyebrow {
  color: #55e4ff;
  font-weight: 800;
  letter-spacing: 0.28em;
}

.field span {
  color: rgba(226, 232, 240, 0.82);
}

input,
select,
textarea,
.compact-input,
.compact-select,
.mini-input {
  border-color: rgba(148, 163, 184, 0.24);
  background: rgba(4, 10, 34, 0.55);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.05);
}

input:focus,
select:focus,
textarea:focus {
  border-color: rgba(56, 189, 248, 0.78);
  box-shadow: 0 0 0 3px rgba(56, 189, 248, 0.14), inset 0 1px 0 rgba(255, 255, 255, 0.08);
}

.btn {
  min-height: 38px;
  border: 1px solid transparent;
  border-radius: 12px;
  font-weight: 700;
}

.btn.primary {
  color: #fff;
  background: linear-gradient(100deg, #2bd4ff, #347cff 48%, #7b2cff);
  box-shadow: 0 18px 42px -22px rgba(56, 189, 248, 0.86);
}

.btn.ghost {
  color: rgba(226, 232, 240, 0.88);
  border-color: rgba(148, 163, 184, 0.2);
  background: rgba(6, 13, 43, 0.48);
}

.btn.warn {
  color: #fde68a;
  border-color: rgba(245, 158, 11, 0.28);
  background: rgba(120, 53, 15, 0.34);
}

.btn.success {
  color: #bbf7d0;
  border-color: rgba(34, 197, 94, 0.26);
  background: rgba(20, 83, 45, 0.36);
}

.btn.danger {
  color: #fecdd3;
  border-color: rgba(244, 63, 94, 0.28);
  background: rgba(127, 29, 29, 0.38);
}

.admin-card {
  padding: 22px;
  border-radius: 20px;
}

.section-head {
  margin-bottom: 14px;
}

.section-head h3 {
  color: #f8fbff;
  font-size: 20px;
  font-weight: 800;
}

.metrics-grid {
  gap: 12px;
}

.metric-item,
.compact-list li,
.feature-row,
.catalog,
.catalog-item,
.log-item {
  border-color: rgba(148, 163, 184, 0.2);
  background: linear-gradient(145deg, rgba(26, 48, 104, 0.54), rgba(7, 14, 45, 0.52));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.06);
}

.metric-item {
  padding: 15px;
  border-radius: 16px;
}

.metric-item strong {
  color: #fff;
  font-size: 26px;
}

.metric-item.warn {
  border-color: rgba(245, 158, 11, 0.32);
  background: linear-gradient(145deg, rgba(120, 53, 15, 0.28), rgba(7, 14, 45, 0.52));
}

.table-wrap {
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 16px;
  background: rgba(4, 10, 34, 0.34);
}

table {
  min-width: 960px;
}

th {
  position: sticky;
  top: 0;
  z-index: 1;
  color: rgba(226, 232, 240, 0.86);
  background: rgba(8, 18, 54, 0.92);
}

th,
td {
  padding: 11px 10px;
  border-bottom-color: rgba(148, 163, 184, 0.14);
}

tr:hover td {
  background: rgba(59, 130, 246, 0.08);
}

.tag {
  border: 1px solid currentColor;
}

.tag.ok {
  background: rgba(34, 197, 94, 0.13);
}

.tag.warn {
  background: rgba(245, 158, 11, 0.14);
}

.switch-wrap input {
  width: auto;
  accent-color: #38bdf8;
}

.log-list {
  padding-right: 4px;
}

.log-item pre {
  border: 1px solid rgba(148, 163, 184, 0.14);
  background: rgba(2, 8, 28, 0.72);
}
</style>
