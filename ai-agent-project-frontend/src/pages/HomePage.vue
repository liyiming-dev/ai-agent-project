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

// Warm gradient palette pool for card covers (picked by id hash)
const WARM_PALETTES = [
  'linear-gradient(135deg, #F3B178 0%, #C2410C 100%)',
  'linear-gradient(135deg, #EBD5A2 0%, #8A6A2B 100%)',
  'linear-gradient(135deg, #A7C4E4 0%, #2B5CE6 100%)',
  'linear-gradient(135deg, #C8B5A1 0%, #6B4F36 100%)',
  'linear-gradient(135deg, #D4A5C3 0%, #7A3F66 100%)',
  'linear-gradient(135deg, #2E2A24 0%, #1C1A17 100%)',
]

const coverBg = (app: API.AppVO) => {
  const seed = String(app.id ?? app.appName ?? 'x')
  let h = 0
  for (let i = 0; i < seed.length; i++) h = (h * 31 + seed.charCodeAt(i)) >>> 0
  return WARM_PALETTES[h % WARM_PALETTES.length]
}

const coverMark = (app: API.AppVO) => {
  const name = (app.appName || 'AI').trim()
  // Prefer first two latin chars, else first CJK char
  const latin = name.match(/[A-Za-z]{1,2}/)
  if (latin) return latin[0].charAt(0).toUpperCase() + (latin[0].charAt(1) ?? '').toLowerCase()
  return name.charAt(0)
}

const relativeTime = (t?: string | number) => {
  if (!t) return ''
  const d = new Date(t).getTime()
  if (Number.isNaN(d)) return ''
  const diff = (Date.now() - d) / 1000
  if (diff < 60) return '刚刚'
  if (diff < 3600) return `${Math.floor(diff / 60)}m`
  if (diff < 86400) return `${Math.floor(diff / 3600)}h`
  if (diff < 86400 * 30) return `${Math.floor(diff / 86400)}d`
  return `${Math.floor(diff / (86400 * 30))}mo`
}

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

watch(
  () => loginUserStore.loginUser.id,
  (id) => {
    if (id) void fetchMyApps()
  },
)

onMounted(() => {
  if (isLogin.value) void fetchMyApps()
  void fetchGoodApps()
})
</script>

<template>
  <div class="home-page">

    <!-- ── HERO ── -->
    <section class="hero">
      <div class="hero-left">
        <span class="hero-kicker"><i></i>v0.5 · 一句话生成</span>
        <h1 class="hero-title">
          把想法<span class="hl">讲出来</span>,<br>
          网站就<span class="hl">做出来</span>。
        </h1>
        <p class="hero-lead">
          用你最自然的语言描述,AI 写出 HTML、CSS 与交互,并立刻为你部署一个可访问的站点
          —— 像把草图交给一位不知疲倦的手作工匠。
        </p>
      </div>

      <div class="hero-illus" aria-hidden="true">
        <svg viewBox="0 0 440 440" fill="none" xmlns="http://www.w3.org/2000/svg">
          <circle cx="220" cy="220" r="200" fill="#EFEADF" />
          <g transform="rotate(-6 220 220)">
            <rect x="90" y="110" width="260" height="220" rx="8" fill="#FFFFFF" stroke="#1C1A17" stroke-width="2" />
            <line x1="110" y1="150" x2="300" y2="150" stroke="#D9D1C2" stroke-width="2" stroke-dasharray="4 6" />
            <line x1="110" y1="175" x2="280" y2="175" stroke="#D9D1C2" stroke-width="2" stroke-dasharray="4 6" />
            <line x1="110" y1="200" x2="260" y2="200" stroke="#D9D1C2" stroke-width="2" stroke-dasharray="4 6" />
            <rect x="110" y="230" width="90" height="60" rx="4" fill="#F4D9C7" stroke="#C2410C" stroke-width="2" />
            <rect x="210" y="230" width="90" height="60" rx="4" fill="#E5ECFB" stroke="#2B5CE6" stroke-width="2" />
          </g>
          <g transform="rotate(28 340 110)">
            <rect x="290" y="100" width="120" height="18" rx="3" fill="#C2410C" stroke="#1C1A17" stroke-width="2" />
            <polygon points="410,100 430,109 410,118" fill="#1C1A17" />
            <rect x="290" y="100" width="22" height="18" fill="#F4D9C7" stroke="#1C1A17" stroke-width="2" />
          </g>
          <g transform="rotate(8 110 330)">
            <rect x="60" y="310" width="110" height="60" rx="6" fill="#1C1A17" />
            <text x="72" y="335" fill="#F4D9C7" font-family="monospace" font-size="11">&lt;site/&gt;</text>
            <text x="72" y="352" fill="#A7C4E4" font-family="monospace" font-size="11">build()</text>
          </g>
          <circle cx="70" cy="90" r="5" fill="#C2410C" />
          <circle cx="380" cy="360" r="6" fill="#2B5CE6" />
          <circle cx="395" cy="150" r="3" fill="#1C1A17" />
        </svg>
      </div>
    </section>

    <!-- ── PROMPT ── -->
    <section class="prompt-wrap">
      <div class="prompt-shell">
        <a-textarea v-model:value="initPrompt" class="prompt-input" :bordered="false"
          placeholder="例如:创建一个极简的待办清单,支持本地存储…(Ctrl / ⌘ + Enter 发送)"
          :auto-size="{ minRows: 3, maxRows: 6 }" @keydown="handlePromptKeydown" />
        <div class="prompt-actions">
          <span class="prompt-hint">Ctrl / ⌘ + Enter 发送</span>
          <button class="send-btn" :disabled="creating" @click="handleCreate">
            <span v-if="!creating">发送</span>
            <span v-else>生成中</span>
            <svg viewBox="0 0 24 24" width="14" height="14" aria-hidden="true">
              <path d="M5 12h14M13 6l6 6-6 6" fill="none" stroke="currentColor" stroke-width="2"
                stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </button>
        </div>
      </div>

      <div class="suggestions">
        <span class="suggestions-label">灵感起点</span>
        <button v-for="item in suggestions" :key="item" class="chip" type="button"
          @click="handleSuggestion(item)">
          {{ item }}
        </button>
      </div>
    </section>

    <!-- ── MY WORKS ── -->
    <section v-if="isLogin" class="app-section">
      <header class="section-head">
        <h2 class="section-title">我的作品</h2>
        <span class="section-badge">{{ myAppsTotal || myApps.length }} 件</span>
        <span class="spacer"></span>
      </header>

      <a-spin :spinning="myAppsLoading">
        <a-empty v-if="!myApps.length && !myAppsLoading" description="暂无作品,试试在上方创建一个" />
        <div v-else class="app-grid">
          <article v-for="(app, idx) in myApps" :key="app.id" class="app-card" :style="{ '--i': idx }"
            @click="goToApp(app)">
            <div class="app-cover" :style="{ background: coverBg(app) }">
              <img v-if="app.cover" :src="app.cover" :alt="app.appName" class="cover-img" />
              <span v-else class="cover-mark">{{ coverMark(app) }}</span>
              <span v-if="idx === 0 && myAppsParams.pageNum === 1" class="cover-pill hot">最新</span>
            </div>
            <div class="app-body">
              <div class="app-name">{{ app.appName || '未命名应用' }}</div>
              <div class="app-meta">
                <span>@{{ app.user?.userName || '匿名' }}<template v-if="relativeTime(app.createTime)"> · {{ relativeTime(app.createTime) }}</template></span>
                <a v-if="app.deployKey" class="deployed"
                  @click.stop="goToDeployed(app)">● 已部署 ↗</a>
                <span v-else class="draft">○ 草稿</span>
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

    <!-- ── CURATED ── -->
    <section class="app-section">
      <header class="section-head">
        <h2 class="section-title">精选案例</h2>
        <span class="section-badge">curated</span>
        <span class="spacer"></span>
      </header>

      <a-spin :spinning="goodAppsLoading">
        <a-empty v-if="!goodApps.length && !goodAppsLoading" description="暂无精选案例" />
        <div v-else class="app-grid">
          <article v-for="(app, idx) in goodApps" :key="app.id" class="app-card" :style="{ '--i': idx }"
            @click="goToApp(app)">
            <div class="app-cover" :style="{ background: coverBg(app) }">
              <img v-if="app.cover" :src="app.cover" :alt="app.appName" class="cover-img" />
              <span v-else class="cover-mark">{{ coverMark(app) }}</span>
              <span class="cover-pill hot">精选</span>
            </div>
            <div class="app-body">
              <div class="app-name">{{ app.appName || '未命名应用' }}</div>
              <div class="app-meta">
                <span>@{{ app.user?.userName || '匿名' }}</span>
                <a v-if="app.deployKey" class="deployed"
                  @click.stop="goToDeployed(app)">● 已部署 ↗</a>
                <span v-else class="draft">○ 草稿</span>
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
   HOME PAGE · Warm Tech (方向 B)
════════════════════════════════════════════ */
.home-page {
  display: flex;
  flex-direction: column;
  gap: 88px;
  padding-bottom: 96px;
}

/* ── HERO ── */
.hero {
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  gap: 60px;
  align-items: center;
  padding: 56px 0 20px;
  animation: fadeUp 0.7s var(--ease-out-expo) both;
}

.hero-kicker {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-family: var(--font-mono);
  font-size: 12px;
  letter-spacing: 0.06em;
  color: var(--hot-600);
  background: var(--hot-100);
  padding: 5px 12px;
  border-radius: 999px;
}

.hero-kicker i {
  width: 6px;
  height: 6px;
  background: var(--hot-600);
  border-radius: 50%;
  display: inline-block;
}

.hero-title {
  font-family: var(--font-display);
  font-weight: 600;
  font-size: clamp(40px, 5.6vw, 64px);
  line-height: 1.18;
  letter-spacing: -0.005em;
  color: var(--ink-900);
  margin: 22px 0 20px;
}

.hero-title .hl {
  background: linear-gradient(transparent 62%, var(--hot-200) 62%);
  padding: 0 4px;
}

.hero-lead {
  font-size: 16px;
  color: var(--ink-700);
  max-width: 540px;
  margin: 0;
  line-height: 1.75;
}

.hero-illus {
  justify-self: end;
  width: 100%;
  max-width: 420px;
  aspect-ratio: 1/1;
  animation: fadeUp 0.8s var(--ease-out-expo) both;
  animation-delay: 0.12s;
}

.hero-illus svg {
  width: 100%;
  height: 100%;
}

/* ── PROMPT ── */
.prompt-wrap {
  animation: fadeUp 0.7s var(--ease-out-expo) both;
  animation-delay: 0.08s;
}

.prompt-shell {
  max-width: 880px;
  background: #FFFFFF;
  border: 1px solid var(--ink-200);
  border-radius: 14px;
  padding: 18px 20px 14px;
  box-shadow: var(--shadow-input);
  transition: box-shadow 0.25s var(--ease-out-quart), transform 0.25s var(--ease-out-quart);
}

.prompt-shell:focus-within {
  box-shadow: var(--shadow-input-focus);
  transform: translateY(-2px);
}

.prompt-input {
  font-family: var(--font-sans);
  font-size: 15px;
  line-height: 1.7;
  resize: none;
  background: transparent;
  color: var(--ink-900);
  padding: 2px;
}

.prompt-input :deep(textarea) {
  background: transparent !important;
  padding: 2px 4px !important;
  font-size: 15px !important;
  color: var(--ink-900) !important;
  font-family: var(--font-sans) !important;
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
  border-top: 1px dashed var(--ink-200);
}

.prompt-hint {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--ink-500);
  letter-spacing: 0.02em;
}

.send-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: var(--hot-600);
  color: #fff;
  border: 0;
  padding: 8px 16px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  font-family: var(--font-sans);
  transition: background 0.2s ease, transform 0.2s ease;
}

.send-btn:hover:not(:disabled) {
  background: var(--ink-900);
  transform: translateY(-1px);
}

.send-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.send-btn svg {
  flex-shrink: 0;
}

/* ── Suggestions ── */
.suggestions {
  max-width: 880px;
  margin-top: 18px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.suggestions-label {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--ink-500);
  margin-right: 4px;
  letter-spacing: 0.04em;
}

.chip {
  border: 1px dashed var(--ink-300);
  background: transparent;
  color: var(--ink-700);
  font-size: 13px;
  padding: 6px 12px;
  border-radius: 999px;
  cursor: pointer;
  font-family: var(--font-sans);
  transition: all 0.2s var(--ease-out-quart);
}

.chip:hover {
  border-style: solid;
  border-color: var(--ink-900);
  background: var(--cream-200);
  color: var(--ink-900);
}

/* ── SECTION ── */
.app-section {
  animation: fadeUp 0.7s var(--ease-out-expo) both;
  animation-delay: 0.12s;
}

.section-head {
  display: flex;
  align-items: baseline;
  gap: 14px;
  margin-bottom: 28px;
}

.section-title {
  margin: 0;
  font-family: var(--font-display);
  font-weight: 600;
  font-size: clamp(22px, 2.2vw, 28px);
  letter-spacing: 0.01em;
  color: var(--ink-900);
}

.section-badge {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--hot-600);
  background: var(--hot-100);
  padding: 2px 8px;
  border-radius: 4px;
  letter-spacing: 0.02em;
}

.spacer {
  flex: 1;
}

/* ── GRID ── */
.app-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
}

@media (max-width: 900px) {
  .app-grid { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 600px) {
  .app-grid { grid-template-columns: 1fr; }
}

/* ── CARD ── */
.app-card {
  --i: 0;
  background: #FFFFFF;
  border: 1px solid var(--ink-200);
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.25s var(--ease-out-quart), box-shadow 0.25s var(--ease-out-quart), border-color 0.2s ease;
  animation: fadeUp 0.55s var(--ease-out-expo) both;
  animation-delay: calc(var(--i) * 55ms);
}

.app-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-card-hover);
  border-color: var(--ink-900);
}

/* ── COVER ── */
.app-cover {
  position: relative;
  aspect-ratio: 4 / 3;
  display: grid;
  place-items: center;
  overflow: hidden;
}

.cover-img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.5s var(--ease-out-expo);
}

.app-card:hover .cover-img {
  transform: scale(1.04);
}

.cover-mark {
  font-family: var(--font-display);
  font-weight: 600;
  font-size: clamp(52px, 6vw, 80px);
  color: #fff;
  letter-spacing: -0.02em;
  transition: transform 0.4s var(--ease-out-expo);
  user-select: none;
}

.app-card:hover .cover-mark {
  transform: translateY(-4px);
}

.cover-pill {
  position: absolute;
  top: 12px;
  left: 12px;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.08em;
  background: rgba(255, 255, 255, 0.92);
  color: var(--ink-900);
  padding: 3px 9px;
  border-radius: 999px;
  backdrop-filter: blur(4px);
}

.cover-pill.hot {
  background: var(--hot-600);
  color: #fff;
}

/* ── CARD BODY ── */
.app-body {
  padding: 14px 16px 16px;
}

.app-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--ink-900);
  letter-spacing: -0.005em;
  margin-bottom: 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.app-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--ink-500);
}

.app-meta .deployed {
  color: var(--hot-600);
  cursor: pointer;
}

.app-meta .deployed:hover {
  color: var(--ink-900);
  text-decoration: underline;
}

.app-meta .draft {
  color: var(--ink-400);
}

/* ── Pagination ── */
.pagination {
  display: flex;
  justify-content: center;
  margin-top: 36px;
}

/* ── Responsive ── */
@media (max-width: 900px) {
  .hero {
    grid-template-columns: 1fr;
    gap: 32px;
    padding-top: 32px;
  }

  .hero-illus {
    justify-self: start;
    max-width: 320px;
  }
}

@media (max-width: 680px) {
  .home-page { gap: 64px; }
  .hero { padding: 24px 0 0; }
  .section-head { flex-wrap: wrap; gap: 10px; }
}
</style>
