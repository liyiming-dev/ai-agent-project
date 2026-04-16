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
.home-page {
  display: flex;
  flex-direction: column;
  gap: 72px;
  padding-bottom: 64px;
}

/* ---------- Hero ---------- */

.hero {
  position: relative;
  padding: 56px 0 16px;
  text-align: center;
  animation: fadeUp 0.8s var(--ease-out-expo) both;
}

.hero-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 6px 16px 6px 14px;
  margin-bottom: 28px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.55);
  border: 1px solid rgba(15, 123, 138, 0.16);
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.24em;
  text-transform: uppercase;
  color: var(--ink-600);
  font-family: var(--font-sans);
}

.hero-eyebrow-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--gradient-accent);
  box-shadow: 0 0 0 4px rgba(61, 214, 208, 0.2);
  animation: pulseDot 2.4s ease-in-out infinite;
}

@keyframes pulseDot {

  0%,
  100% {
    box-shadow: 0 0 0 4px rgba(61, 214, 208, 0.2);
  }

  50% {
    box-shadow: 0 0 0 7px rgba(61, 214, 208, 0.05);
  }
}

.hero-title {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 18px;
  margin: 0 0 20px;
  font-family: var(--font-display);
  font-size: clamp(42px, 6vw, 72px);
  font-weight: 400;
  line-height: 1.08;
  letter-spacing: -0.02em;
  color: var(--ink-900);
}

.hero-word {
  display: inline-block;
}

.hero-word--accent em {
  display: inline-block;
  font-style: normal;
  font-weight: 500;
  padding-right: 0.12em;
  background: var(--gradient-text);
  background-clip: text;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.hero-logo-wrap {
  position: relative;
  width: clamp(48px, 6vw, 72px);
  height: clamp(48px, 6vw, 72px);
  border-radius: 50%;
  padding: 6px;
  background: var(--gradient-primary);
  box-shadow: 0 14px 30px rgba(15, 123, 138, 0.25);
}

.hero-logo {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: #fff;
  padding: 6px;
  display: block;
}

.hero-subtitle {
  max-width: 560px;
  margin: 0 auto 44px;
  color: var(--ink-500);
  font-size: 16.5px;
  line-height: 1.6;
  letter-spacing: 0.01em;
}

/* Prompt input — intentionally NOT a card: borderline-transparent,
   no box-shadow, single 1px subtle gradient border. */
.prompt-shell {
  position: relative;
  width: min(100%, 840px);
  margin: 0 auto;
  padding: 20px 20px 16px;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.42);
  border: 1px solid rgba(15, 123, 138, 0.14);
  backdrop-filter: blur(6px);
  transition: border-color 0.3s var(--ease-out-quart), background 0.3s var(--ease-out-quart);
}

.prompt-shell:focus-within {
  border-color: rgba(47, 179, 184, 0.55);
  background: rgba(255, 255, 255, 0.62);
}

.prompt-shell::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 22px;
  padding: 1px;
  background: linear-gradient(135deg, rgba(61, 214, 208, 0.55), rgba(15, 123, 138, 0.05) 40%, rgba(132, 228, 209, 0.4));
  -webkit-mask: linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0);
  -webkit-mask-composite: xor;
  mask-composite: exclude;
  pointer-events: none;
  opacity: 0.9;
}

.prompt-glow {
  position: absolute;
  inset: -22px;
  border-radius: 30px;
  background: radial-gradient(ellipse at 50% 100%, rgba(61, 214, 208, 0.28), transparent 70%);
  filter: blur(18px);
  pointer-events: none;
  z-index: -1;
  opacity: 0.7;
}

.prompt-input {
  font-family: var(--font-sans);
  font-size: 16px;
  line-height: 1.6;
  resize: none;
  background: transparent;
  color: var(--ink-800);
  padding: 4px 2px;
}

.prompt-input :deep(textarea) {
  background: transparent !important;
  padding: 4px 6px !important;
  font-size: 16px !important;
  color: var(--ink-800) !important;
}

.prompt-input :deep(textarea::placeholder) {
  color: var(--ink-400) !important;
}

.prompt-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed rgba(15, 123, 138, 0.12);
}

.prompt-hint {
  font-size: 12px;
  letter-spacing: 0.06em;
  color: var(--ink-400);
  font-family: var(--font-mono);
}

.prompt-send {
  width: 44px !important;
  height: 44px !important;
  display: inline-flex !important;
  align-items: center;
  justify-content: center;
}

.prompt-send :deep(.anticon),
.prompt-send svg {
  color: #fff;
}

/* Suggestions */
.suggestions {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 28px;
}

.suggestions-label {
  font-size: 12px;
  letter-spacing: 0.24em;
  text-transform: uppercase;
  color: var(--ink-400);
  margin-right: 4px;
  font-weight: 500;
}

.suggestion-btn {
  background: rgba(255, 255, 255, 0.5) !important;
  border: 1px solid rgba(15, 123, 138, 0.14) !important;
  color: var(--ink-700) !important;
  transition: all 0.25s var(--ease-out-quart) !important;
  font-size: 13.5px;
}

.suggestion-btn:hover {
  background: rgba(255, 255, 255, 0.88) !important;
  border-color: var(--teal-500) !important;
  color: var(--teal-700) !important;
  transform: translateY(-1px);
}

/* ---------- Sections ---------- */

.app-section {
  width: min(100%, 1200px);
  margin: 0 auto;
  animation: fadeUp 0.8s var(--ease-out-expo) both;
  animation-delay: 0.1s;
}

.section-head {
  display: flex;
  align-items: flex-end;
  gap: 22px;
  margin-bottom: 28px;
}

.section-head-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.section-kicker {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.28em;
  text-transform: uppercase;
  color: var(--teal-600);
}

.section-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(26px, 2.6vw, 34px);
  font-weight: 500;
  letter-spacing: -0.01em;
  color: var(--ink-900);
}

.section-rule {
  flex: 1;
  height: 1px;
  margin-bottom: 10px;
  background: linear-gradient(90deg,
      rgba(15, 123, 138, 0.28) 0%,
      rgba(15, 123, 138, 0.05) 65%,
      transparent 100%);
}

/* ---------- Cards (allowed — user work & featured) ---------- */

.app-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 22px;
}

.app-card {
  --i: 0;
  position: relative;
  padding: 14px;
  border-radius: 20px;
  background: linear-gradient(180deg,
      rgba(255, 255, 255, 0.96) 0%,
      rgba(247, 252, 251, 0.92) 100%);
  border: 1px solid rgba(15, 123, 138, 0.08);
  box-shadow:
    0 1px 2px rgba(15, 123, 138, 0.04),
    0 18px 38px -24px rgba(15, 123, 138, 0.28);
  transition:
    transform 0.4s var(--ease-out-expo),
    box-shadow 0.4s var(--ease-out-expo),
    border-color 0.3s var(--ease-out-quart);
  overflow: hidden;
  animation: fadeUp 0.65s var(--ease-out-expo) both;
  animation-delay: calc(var(--i) * 60ms);
}

.app-card::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  padding: 1px;
  background: linear-gradient(135deg, rgba(61, 214, 208, 0.0), rgba(61, 214, 208, 0.0));
  -webkit-mask: linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0);
  -webkit-mask-composite: xor;
  mask-composite: exclude;
  pointer-events: none;
  transition: background 0.4s var(--ease-out-expo);
}

.app-card:hover {
  transform: translateY(-6px);
  box-shadow:
    0 2px 4px rgba(15, 123, 138, 0.05),
    0 28px 56px -24px rgba(15, 123, 138, 0.38);
  border-color: rgba(61, 214, 208, 0.28);
}

.app-card:hover::before {
  background: linear-gradient(135deg, rgba(61, 214, 208, 0.55), rgba(132, 228, 209, 0.1) 60%, rgba(15, 123, 138, 0.3));
}

.app-card--featured {
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.96) 0%, rgba(239, 250, 247, 0.94) 100%);
}

.app-cover {
  position: relative;
  height: 190px;
  overflow: hidden;
  border-radius: 14px;
  background: linear-gradient(135deg, var(--teal-100), var(--teal-50));
  cursor: pointer;
  isolation: isolate;
}

.app-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.6s var(--ease-out-expo);
}

.app-card:hover .app-cover img {
  transform: scale(1.04);
}

.app-cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    radial-gradient(circle at 30% 30%, rgba(132, 228, 209, 0.35), transparent 60%),
    radial-gradient(circle at 70% 80%, rgba(61, 214, 208, 0.22), transparent 65%);
}

.app-cover-placeholder img {
  width: 68px;
  height: 68px;
  opacity: 0.55;
  filter: saturate(0.8);
}

.app-cover-sheen {
  position: absolute;
  inset: 0;
  background: linear-gradient(130deg,
      transparent 30%,
      rgba(255, 255, 255, 0.35) 50%,
      transparent 70%);
  transform: translateX(-100%);
  transition: transform 0.9s var(--ease-out-expo);
  pointer-events: none;
}

.app-card:hover .app-cover-sheen {
  transform: translateX(100%);
}

.featured-badge {
  position: absolute;
  top: 12px;
  left: 12px;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.9);
  color: var(--teal-700);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.14em;
  box-shadow: 0 6px 16px rgba(15, 123, 138, 0.18);
  backdrop-filter: blur(6px);
}

.app-card-body {
  padding: 14px 6px 4px;
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
  font-family: var(--font-display);
  font-size: 17px;
  font-weight: 500;
  color: var(--ink-900);
  letter-spacing: -0.01em;
  margin-bottom: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.app-meta {
  font-size: 12.5px;
  color: var(--ink-400);
  letter-spacing: 0.01em;
}

.app-card-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 14px;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 32px;
}

@media (max-width: 680px) {
  .section-head {
    flex-wrap: wrap;
  }

  .section-rule {
    display: none;
  }

  .hero {
    padding: 32px 0 8px;
  }
}
</style>
