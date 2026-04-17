<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { addApp, listGoodAppVoByPage, listMyAppVoByPage } from '@/api/api/appController'
import { useLoginUserStore } from '@/stores/loginUser'

const router = useRouter()
const loginUserStore = useLoginUserStore()

const initPrompt = ref('')
const creating = ref(false)

const PAGE_SIZE = 6

const myApps = ref<API.AppVO[]>([])
const myAppsTotal = ref(0)
const myAppsLoading = ref(false)
const myAppsParams = reactive<API.AppQueryDto>({
  pageNum: 1,
  pageSize: PAGE_SIZE,
  sortField: 'createTime',
  sortOrder: 'descend',
})

const goodApps = ref<API.AppVO[]>([])
const goodAppsTotal = ref(0)
const goodAppsLoading = ref(false)
const goodAppsParams = reactive<API.AppQueryDto>({
  pageNum: 1,
  pageSize: PAGE_SIZE,
  sortField: 'createTime',
  sortOrder: 'descend',
})

const isLogin = computed(() => !!loginUserStore.loginUser.id)

const suggestions = ['波普风电商页面', '企业网站', '电商运营后台', '暗黑话题社区']

const fetchMyApps = async () => {
  if (!isLogin.value) {
    myApps.value = []
    myAppsTotal.value = 0
    return
  }
  myAppsLoading.value = true
  try {
    const res = await listMyAppVoByPage({ ...myAppsParams })
    if (res.data.code === 0 && res.data.data) {
      myApps.value = res.data.data.records ?? []
      myAppsTotal.value = res.data.data.totalRow ?? 0
      return
    }
    message.error(res.data.message ?? '获取我的作品失败')
  } catch {
    message.error('获取我的作品失败')
  } finally {
    myAppsLoading.value = false
  }
}

const fetchGoodApps = async () => {
  goodAppsLoading.value = true
  try {
    const res = await listGoodAppVoByPage({ ...goodAppsParams })
    if (res.data.code === 0 && res.data.data) {
      goodApps.value = res.data.data.records ?? []
      goodAppsTotal.value = res.data.data.totalRow ?? 0
      return
    }
    message.error(res.data.message ?? '获取精选案例失败')
  } catch {
    message.error('获取精选案例失败')
  } finally {
    goodAppsLoading.value = false
  }
}

const onMyAppsPageChange = (page: number) => {
  myAppsParams.pageNum = page
  void fetchMyApps()
}

const onGoodAppsPageChange = (page: number) => {
  goodAppsParams.pageNum = page
  void fetchGoodApps()
}

const handleSuggestion = (text: string) => {
  initPrompt.value = text
}

const handlePromptKeydown = (e: KeyboardEvent) => {
  if (e.isComposing || (e as KeyboardEvent & { keyCode: number }).keyCode === 229) return
  if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
    e.preventDefault()
    void handleCreate()
  }
}

const handleCreate = async () => {
  const prompt = initPrompt.value.trim()
  if (!prompt) {
    message.warning('请输入提示词')
    return
  }
  if (!isLogin.value) {
    message.warning('请先登录')
    router.push('/user/login?redirect=/')
    return
  }
  creating.value = true
  try {
    const res = await addApp({ initPrompt: prompt })
    if (res.data.code === 0 && res.data.data) {
      router.push({ path: `/app/chat/${res.data.data}` })
      return
    }
    message.error(res.data.message ?? '创建应用失败')
  } catch {
    message.error('创建应用失败')
  } finally {
    creating.value = false
  }
}

const goToApp = (app: API.AppVO) => {
  if (!app.id) return
  router.push({ path: `/app/chat/${app.id}`, query: { view: '1' } })
}

const goToDeployed = (app: API.AppVO) => {
  if (!app.deployKey) return
  window.open(`http://localhost:8123/api/static/${app.deployKey}`, '_blank', 'noopener')
}

// 登录信息异步加载：id 到达后再拉取「我的作品」
watch(
  () => loginUserStore.loginUser.id,
  (id) => {
    if (id) void fetchMyApps()
  },
)

onMounted(() => {
  // 已登录（命中缓存时 id 已存在）直接拉取
  if (isLogin.value) void fetchMyApps()
  void fetchGoodApps()
})
</script>

<template>
  <div class="home-page">
    <section class="hero">

      <h1 class="hero-title">
        <span class="hero-word hero-word--accent"><em>AI 应用生成平台</em></span>
      </h1>
      <p class="hero-subtitle">一句话轻松创建网站应用</p>

      <div class="prompt-shell">
        <span class="prompt-glow" aria-hidden="true"></span>
        <a-textarea v-model:value="initPrompt" class="prompt-input" :bordered="false"
          placeholder="例如：创建一个极简的待办清单，支持本地存储…（Ctrl / ⌘ + Enter 发送）" :auto-size="{ minRows: 3, maxRows: 6 }"
          @keydown="handlePromptKeydown" />
        <div class="prompt-actions">
          <span class="prompt-hint">Ctrl / ⌘ + Enter 发送</span>
          <a-button type="primary" shape="circle" size="large" class="prompt-send" :loading="creating"
            @click="handleCreate">
            <template #icon>
              <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
                <path d="M12 19V5M5 12l7-7 7 7" fill="none" stroke="currentColor" stroke-width="2"
                  stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </template>
          </a-button>
        </div>
      </div>

      <div class="suggestions">
        <span class="suggestions-label">灵感起点</span>
        <a-button v-for="item in suggestions" :key="item" shape="round" class="suggestion-btn"
          @click="handleSuggestion(item)">
          {{ item }}
        </a-button>
      </div>
    </section>

    <section v-if="isLogin" class="app-section">
      <header class="section-head">
        <div class="section-head-text">
          <span class="section-kicker">Studio</span>
          <h2 class="section-title">我的作品</h2>
        </div>
        <span class="section-rule" aria-hidden="true"></span>
      </header>
      <a-spin :spinning="myAppsLoading">
        <a-empty v-if="!myApps.length && !myAppsLoading" description="暂无作品，试试在上方创建一个" />
        <div v-else class="app-grid">
          <article v-for="(app, idx) in myApps" :key="app.id" class="app-card" :style="{ '--i': idx }">
            <div class="app-cover" @click="goToApp(app)">
              <img v-if="app.cover" :src="app.cover" :alt="app.appName" />
              <div v-else class="app-cover-placeholder">
                <img src="/logo.png" alt="cover" />
              </div>
              <span class="app-cover-sheen" aria-hidden="true"></span>
            </div>
            <div class="app-card-body">
              <div class="app-card-footer">
                <a-avatar :src="app.user?.userAvatar" size="small">
                  {{ app.user?.userName?.[0] ?? 'U' }}
                </a-avatar>
                <div class="app-card-info">
                  <div class="app-name">{{ app.appName || '未命名应用' }}</div>
                  <div class="app-meta">{{ app.user?.userName || '匿名用户' }}</div>
                </div>
              </div>
              <div class="app-card-actions">
                <a-button size="small" @click="goToApp(app)">查看对话</a-button>
                <a-button v-if="app.deployKey" size="small" type="primary" @click="goToDeployed(app)">
                  查看作品
                </a-button>
              </div>
            </div>
          </article>
        </div>
      </a-spin>
      <div v-if="myAppsTotal > PAGE_SIZE" class="pagination">
        <a-pagination :current="myAppsParams.pageNum" :page-size="PAGE_SIZE" :total="myAppsTotal"
          :show-size-changer="false" @change="onMyAppsPageChange" />
      </div>
    </section>

    <section class="app-section">
      <header class="section-head">
        <div class="section-head-text">
          <span class="section-kicker">Curated</span>
          <h2 class="section-title">精选案例</h2>
        </div>
        <span class="section-rule" aria-hidden="true"></span>
      </header>
      <a-spin :spinning="goodAppsLoading">
        <a-empty v-if="!goodApps.length && !goodAppsLoading" description="暂无精选案例" />
        <div v-else class="app-grid">
          <article v-for="(app, idx) in goodApps" :key="app.id" class="app-card app-card--featured"
            :style="{ '--i': idx }">
            <div class="app-cover" @click="goToApp(app)">
              <img v-if="app.cover" :src="app.cover" :alt="app.appName" />
              <div v-else class="app-cover-placeholder">
                <img src="/logo.png" alt="cover" />
              </div>
              <span class="app-cover-sheen" aria-hidden="true"></span>
              <span class="featured-badge">精选</span>
            </div>
            <div class="app-card-body">
              <div class="app-card-footer">
                <a-avatar :src="app.user?.userAvatar" size="small">
                  {{ app.user?.userName?.[0] ?? 'U' }}
                </a-avatar>
                <div class="app-card-info">
                  <div class="app-name">{{ app.appName || '未命名应用' }}</div>
                  <div class="app-meta">{{ app.user?.userName || '匿名用户' }}</div>
                </div>
              </div>
              <div class="app-card-actions">
                <a-button size="small" @click="goToApp(app)">查看对话</a-button>
                <a-button v-if="app.deployKey" size="small" type="primary" @click="goToDeployed(app)">
                  查看作品
                </a-button>
              </div>
            </div>
          </article>
        </div>
      </a-spin>
      <div v-if="goodAppsTotal > PAGE_SIZE" class="pagination">
        <a-pagination :current="goodAppsParams.pageNum" :page-size="PAGE_SIZE" :total="goodAppsTotal"
          :show-size-changer="false" @change="onGoodAppsPageChange" />
      </div>
    </section>
  </div>
</template>

<style scoped>
/* ════════════════════════════════════════════
   HOME PAGE  –  Bright Blue/White Tech Aesthetic
════════════════════════════════════════════ */
.home-page {
  display: flex;
  flex-direction: column;
  gap: 80px;
  padding-bottom: 80px;
}

/* ── Hero ── */
.hero {
  position: relative;
  padding: 64px 0 20px;
  text-align: center;
  animation: fadeUp 0.75s var(--ease-out-expo) both;
}

/* Subtle sky-blue glow behind hero */
.hero::before {
  content: '';
  position: absolute;
  top: -60px;
  left: 50%;
  transform: translateX(-50%);
  width: 700px;
  height: 340px;
  border-radius: 50%;
  background: radial-gradient(ellipse at center, rgba(0, 102, 255, 0.10) 0%, transparent 70%);
  filter: blur(20px);
  pointer-events: none;
  z-index: -1;
}

.hero-title {
  margin: 0 0 16px;
  font-family: var(--font-display);
  font-size: clamp(44px, 6.5vw, 76px);
  font-weight: 800;
  line-height: 1.06;
  letter-spacing: -0.03em;
  color: var(--ink-900);
}

.hero-word--accent em {
  display: inline-block;
  font-style: normal;
  font-weight: 800;
  background: var(--gradient-text);
  background-clip: text;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.hero-subtitle {
  max-width: 520px;
  margin: 0 auto 48px;
  color: var(--ink-500);
  font-size: 17px;
  font-weight: 400;
  line-height: 1.65;
  letter-spacing: 0.005em;
}

/* ── Prompt Card ── */
.prompt-shell {
  position: relative;
  width: min(100%, 820px);
  margin: 0 auto;
  padding: 22px 22px 16px;
  border-radius: 20px;
  background: #FFFFFF;
  border: 1px solid var(--ink-200);
  box-shadow: var(--shadow-input);
  transition:
    border-color 0.3s var(--ease-out-quart),
    box-shadow 0.3s var(--ease-out-quart);
}

.prompt-shell:focus-within {
  border-color: var(--blue-400);
  box-shadow: var(--shadow-input), 0 0 0 4px rgba(0, 102, 255, 0.08);
}

/* Drop the old pseudo-element gradient border */
.prompt-shell::before { display: none; }

.prompt-glow {
  position: absolute;
  inset: -28px;
  border-radius: 28px;
  background: radial-gradient(ellipse at 50% 110%, rgba(0, 102, 255, 0.10), transparent 65%);
  filter: blur(16px);
  pointer-events: none;
  z-index: -1;
}

.prompt-input {
  font-family: var(--font-sans);
  font-size: 16px;
  line-height: 1.65;
  resize: none;
  background: transparent;
  color: var(--ink-900);
  padding: 4px 2px;
}

.prompt-input :deep(textarea) {
  background: transparent !important;
  padding: 4px 6px !important;
  font-size: 16px !important;
  color: var(--ink-900) !important;
  font-family: var(--font-sans) !important;
}

.prompt-input :deep(textarea::placeholder) {
  color: var(--ink-400) !important;
  font-size: 15.5px !important;
}

.prompt-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--ink-100);
}

.prompt-hint {
  font-size: 12px;
  letter-spacing: 0.05em;
  color: var(--ink-400);
  font-family: var(--font-mono);
}

.prompt-send {
  width: 44px !important;
  height: 44px !important;
  display: inline-flex !important;
  align-items: center !important;
  justify-content: center !important;
  background: var(--gradient-primary) !important;
  border: none !important;
  border-radius: 12px !important;
  box-shadow: 0 4px 16px rgba(0, 102, 255, 0.35) !important;
  transition: box-shadow 0.25s var(--ease-out-quart), transform 0.25s var(--ease-out-quart) !important;
}

.prompt-send:hover {
  box-shadow: 0 6px 22px rgba(0, 102, 255, 0.45) !important;
}

.prompt-send :deep(.anticon),
.prompt-send svg {
  color: #fff;
}

/* ── Suggestion Chips ── */
.suggestions {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 24px;
}

.suggestions-label {
  font-size: 11.5px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--ink-400);
  margin-right: 2px;
  font-weight: 600;
}

.suggestion-btn {
  height: 34px !important;
  padding: 0 14px !important;
  background: rgba(255, 255, 255, 0.9) !important;
  border: 1px solid var(--ink-200) !important;
  color: var(--ink-600) !important;
  font-size: 13px !important;
  font-weight: 500 !important;
  border-radius: 999px !important;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05) !important;
  transition: all 0.22s var(--ease-out-quart) !important;
}

.suggestion-btn:hover {
  background: var(--blue-50) !important;
  border-color: var(--blue-300) !important;
  color: var(--blue-600) !important;
  box-shadow: 0 2px 8px rgba(0, 102, 255, 0.12) !important;
  transform: translateY(-2px) !important;
}

/* ── Sections ── */
.app-section {
  width: min(100%, 1200px);
  margin: 0 auto;
  animation: fadeUp 0.75s var(--ease-out-expo) both;
  animation-delay: 0.08s;
}

.section-head {
  display: flex;
  align-items: flex-end;
  gap: 20px;
  margin-bottom: 28px;
}

.section-head-text {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.section-kicker {
  font-size: 10.5px;
  font-weight: 700;
  letter-spacing: 0.32em;
  text-transform: uppercase;
  color: var(--blue-500);
}

.section-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(24px, 2.4vw, 30px);
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--ink-900);
}

.section-rule {
  flex: 1;
  height: 1px;
  margin-bottom: 8px;
  background: linear-gradient(
    90deg,
    rgba(0, 102, 255, 0.20) 0%,
    rgba(0, 102, 255, 0.05) 60%,
    transparent 100%
  );
}

/* ── Card Grid ── */
.app-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

@media (max-width: 900px) {
  .app-grid { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 600px) {
  .app-grid { grid-template-columns: 1fr; }
}

/* ── App Card ── */
.app-card {
  --i: 0;
  position: relative;
  border-radius: 14px;
  background: #FFFFFF;
  border: 1px solid var(--ink-200);
  box-shadow: var(--shadow-card);
  overflow: hidden;
  transition:
    transform 0.36s var(--ease-out-expo),
    box-shadow 0.36s var(--ease-out-expo),
    border-color 0.25s ease;
  animation: fadeUp 0.6s var(--ease-out-expo) both;
  animation-delay: calc(var(--i) * 55ms);
  cursor: default;
}

.app-card::before { display: none; }

.app-card:hover {
  transform: translateY(-7px);
  box-shadow: var(--shadow-card-hover);
  border-color: var(--blue-200);
}

.app-card--featured {
  /* subtle blue tint for curated cards */
  background: linear-gradient(180deg, #FFFFFF 0%, #F8FAFF 100%);
}

/* ── Card Cover ── */
.app-cover {
  position: relative;
  height: 180px;
  overflow: hidden;
  border-radius: 0;
  background: linear-gradient(135deg, var(--blue-100) 0%, var(--purple-100) 100%);
  cursor: pointer;
}

.app-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.55s var(--ease-out-expo);
}

.app-card:hover .app-cover img {
  transform: scale(1.05);
}

.app-cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    radial-gradient(circle at 28% 28%, rgba(0, 102, 255, 0.12), transparent 55%),
    radial-gradient(circle at 72% 72%, rgba(124, 58, 237, 0.10), transparent 55%),
    linear-gradient(135deg, #EBF3FF 0%, #F5F3FF 100%);
}

.app-cover-placeholder img {
  width: 60px;
  height: 60px;
  opacity: 0.45;
  filter: saturate(0.6) brightness(1.1);
}

/* sheen sweep on hover */
.app-cover-sheen {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    120deg,
    transparent 25%,
    rgba(255, 255, 255, 0.45) 50%,
    transparent 75%
  );
  transform: translateX(-130%);
  transition: transform 0.85s var(--ease-out-expo);
  pointer-events: none;
}

.app-card:hover .app-cover-sheen {
  transform: translateX(130%);
}

/* Featured badge */
.featured-badge {
  position: absolute;
  top: 10px;
  left: 10px;
  padding: 3px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.92);
  color: var(--blue-600);
  font-size: 10.5px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  box-shadow: 0 4px 12px rgba(0, 102, 255, 0.16);
  backdrop-filter: blur(6px);
}

/* ── Card Body ── */
.app-card-body {
  padding: 14px 16px 12px;
}

.app-card-footer {
  display: flex;
  align-items: center;
  gap: 10px;
}

.app-card-info {
  flex: 1;
  min-width: 0;
}

.app-name {
  font-size: 15.5px;
  font-weight: 600;
  color: var(--ink-900);
  letter-spacing: -0.01em;
  margin-bottom: 3px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.app-meta {
  font-size: 12px;
  color: var(--ink-400);
  letter-spacing: 0.01em;
}

.app-card-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--ink-100);
}

/* ── Pagination ── */
.pagination {
  display: flex;
  justify-content: center;
  margin-top: 36px;
}

/* ── Responsive Hero ── */
@media (max-width: 680px) {
  .section-head { flex-wrap: wrap; }
  .section-rule { display: none; }
  .hero { padding: 36px 0 12px; }
}
</style>
