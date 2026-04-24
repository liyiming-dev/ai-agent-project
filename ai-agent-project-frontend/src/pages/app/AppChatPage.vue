<script setup lang="ts">
import { computed, h, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import 'highlight.js/styles/atom-one-dark.css'
import { deleteApp, deployApp, getAppVoById } from '@/api/api/appController'
import { listAppChatHistory } from '@/api/api/chatHistoryController'
import { useLoginUserStore } from '@/stores/loginUser'

type ChatMessage = {
  role: 'user' | 'ai'
  content: string
  loading?: boolean
  createTime?: string
}

const escapeHtml = (text: string): string =>
  text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')

const md: MarkdownIt = new MarkdownIt({
  linkify: true,
  breaks: true,
  highlight(code: string, lang: string): string {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return `<pre class="hljs"><code>${
          hljs.highlight(code, { language: lang, ignoreIllegals: true }).value
        }</code></pre>`
      } catch {
        // fallthrough
      }
    }
    return `<pre class="hljs"><code>${escapeHtml(code)}</code></pre>`
  },
})

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

const appId = computed(() => route.params.id as string)

const app = ref<API.AppVO | null>(null)
const messages = ref<ChatMessage[]>([])
const inputMessage = ref('')
const sending = ref(false)
const deploying = ref(false)
const previewReady = ref(false)
const previewVersion = ref(0)
const initialSent = ref(false)
const detailVisible = ref(false)
const deleting = ref(false)

// 历史消息游标分页相关
const historyLoading = ref(false)
const hasMoreHistory = ref(false)
const oldestCreateTime = ref<string | undefined>(undefined)
const totalHistoryCount = ref(0)

const isOwner = computed(() => {
  const myId = loginUserStore.loginUser.id
  return !!(myId && app.value?.userId && app.value.userId === myId)
})

const messagesRef = ref<HTMLElement | null>(null)
let eventSource: EventSource | null = null
let doneReceived = false
let stoppedByUser = false

const previewUrl = computed(() => {
  if (!app.value?.codeGenType || !app.value?.id) return ''
  // Vue 项目经过 npm run build 后，产物在 dist/ 子目录中，需要在路径中追加 /dist
  const isVueProject = app.value.codeGenType === 'vue_project'
  const base = `http://localhost:8123/api/static/${app.value.codeGenType}_${app.value.id}${isVueProject ? '/dist' : ''}/`
  return previewVersion.value ? `${base}?t=${previewVersion.value}` : base
})

const formatDateTime = (value?: string) => {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (n: number) => `${n}`.padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

const renderMarkdown = (text: string) => md.render(text || '')

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

const closeEventSource = () => {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
}

/**
 * 将后端 ChatHistory 记录转换为前端 ChatMessage
 */
const chatHistoryToMessage = (record: API.ChatHistory): ChatMessage => {
  return {
    role: record.messageType === 'user' ? 'user' : 'ai',
    content: record.message || '',
    createTime: record.createTime,
  }
}

/**
 * 加载对话历史（游标分页）
 * 首次加载传空 lastCreateTime，之后传最早一条记录的 createTime
 */
const loadChatHistory = async (isLoadMore = false) => {
  if (!appId.value) return
  historyLoading.value = true
  try {
    const params: API.listAppChatHistoryParams = {
      appId: appId.value as unknown as number,
      pageSize: 10,
    }
    if (isLoadMore && oldestCreateTime.value) {
      params.lastCreateTime = oldestCreateTime.value
    }
    const res = await listAppChatHistory(params)
    if (res.data.code === 0 && res.data.data) {
      const page = res.data.data
      const records = page.records ?? []
      // 后端返回的是按时间降序的最新 N 条，需要反转为升序
      const newMessages = records.map(chatHistoryToMessage).reverse()

      if (isLoadMore) {
        // 加载更多：将旧消息插入到列表前面
        // 保存当前滚动位置以防止跳动
        const container = messagesRef.value
        const prevScrollHeight = container?.scrollHeight ?? 0
        messages.value = [...newMessages, ...messages.value]
        // 恢复滚动位置
        nextTick(() => {
          if (container) {
            const newScrollHeight = container.scrollHeight
            container.scrollTop = newScrollHeight - prevScrollHeight
          }
        })
      } else {
        // 首次加载
        messages.value = newMessages
        totalHistoryCount.value = page.totalRow ?? 0
        scrollToBottom()
      }

      // 更新游标：取当前列表中最早那条消息的 createTime
      if (messages.value.length > 0) {
        oldestCreateTime.value = messages.value[0].createTime
      }

      // 判断是否还有更多
      if (!isLoadMore) {
        // 首次：如果 totalRow > 已加载的数量，就还有更多
        hasMoreHistory.value = (page.totalRow ?? 0) > records.length
      } else {
        // 加载更多：如果返回的条数 < pageSize，说明没有更多了
        hasMoreHistory.value = records.length >= 10
      }
    }
  } catch {
    message.error('加载对话历史失败')
  } finally {
    historyLoading.value = false
  }
}

const handleLoadMore = () => {
  void loadChatHistory(true)
}

const sendMessage = (text: string) => {
  const content = text.trim()
  if (!content) return
  if (sending.value) {
    message.warning('AI 正在回复，请稍候')
    return
  }
  if (!appId.value) return

  messages.value.push({ role: 'user', content })
  messages.value.push({ role: 'ai', content: '', loading: true })
  const aiMsg = messages.value[messages.value.length - 1]!
  sending.value = true
  previewReady.value = false
  doneReceived = false
  stoppedByUser = false
  scrollToBottom()

  const url = `http://localhost:8123/api/app/chat/gen/code?appId=${appId.value}&message=${encodeURIComponent(content)}`
  closeEventSource()
  eventSource = new EventSource(url, { withCredentials: true })

  const finishSuccess = async () => {
    closeEventSource()
    sending.value = false
    aiMsg.loading = false
    const ok = await fetchApp()
    if (!ok) return
    previewVersion.value = Date.now()
    previewReady.value = true
  }

  eventSource.onmessage = (event) => {
    try {
      const parsed = JSON.parse(event.data)
      const chunk = typeof parsed === 'string' ? parsed : (parsed.d ?? parsed.data ?? '')
      aiMsg.content += chunk
    } catch {
      aiMsg.content += event.data
    }
    aiMsg.loading = false
    scrollToBottom()
  }

  eventSource.onerror = () => {
    closeEventSource()
    sending.value = false
    aiMsg.loading = false
    if (doneReceived) {
      void finishSuccess()
    } else if (stoppedByUser) {
      aiMsg.content = aiMsg.content
        ? `${aiMsg.content}\n\n（已手动中断生成）`
        : '（已手动中断生成）'
    } else {
      aiMsg.content = aiMsg.content
        ? `${aiMsg.content}\n\n（回复中断，请稍后再试）`
        : '（回复中断，请稍后再试）'
      message.error('AI 回复中断，请稍后再试')
    }
  }

  eventSource.addEventListener('done', () => {
    doneReceived = true
    void finishSuccess()
  })
}

const handleSend = () => {
  const text = inputMessage.value.trim()
  if (!text) return
  inputMessage.value = ''
  sendMessage(text)
}

const handleStop = () => {
  if (!sending.value) return
  stoppedByUser = true
  closeEventSource()
  sending.value = false
  const last = messages.value[messages.value.length - 1]
  if (last && last.role === 'ai') {
    last.loading = false
    last.content = last.content
      ? `${last.content}\n\n（已手动中断生成）`
      : '（已手动中断生成）'
  }
  message.info('已中断生成')
}

const handleInputKeydown = (e: KeyboardEvent) => {
  if (e.isComposing || (e as KeyboardEvent & { keyCode: number }).keyCode === 229) return
  if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
    e.preventDefault()
    handleSend()
  }
}

const fetchApp = async () => {
  if (!appId.value) return
  try {
    const res = await getAppVoById({ id: appId.value as unknown as number })
    if (res.data.code === 0 && res.data.data) {
      app.value = res.data.data
      return true
    }
    message.error(res.data.message ?? '获取应用信息失败')
  } catch {
    message.error('获取应用信息失败')
  }
  return false
}

const refreshPreview = () => {
  previewVersion.value = Date.now()
}

const handleDeploy = async () => {
  if (!appId.value) return
  deploying.value = true
  try {
    const res = await deployApp({ appId: appId.value as unknown as number })
    if (res.data.code === 0 && res.data.data) {
      const url = res.data.data
      Modal.success({
        title: '部署成功',
        content: () =>
          h('a', { href: url, target: '_blank', rel: 'noopener' }, url),
      })
      return
    }
    message.error(res.data.message ?? '部署失败')
  } catch {
    message.error('部署失败')
  } finally {
    deploying.value = false
  }
}

const openDetail = () => {
  detailVisible.value = true
}

const handleEditApp = () => {
  if (!app.value?.id) return
  detailVisible.value = false
  router.push(`/app/edit/${app.value.id}`)
}

const handleDeleteApp = () => {
  if (!app.value?.id) return
  Modal.confirm({
    title: '确认删除',
    content: '删除后不可恢复，确定要删除该应用吗？',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      deleting.value = true
      try {
        const res = await deleteApp({ id: app.value!.id as unknown as number })
        if (res.data.code === 0 && res.data.data) {
          message.success('删除成功')
          detailVisible.value = false
          router.push('/')
          return
        }
        message.error(res.data.message ?? '删除失败')
      } catch {
        message.error('删除失败')
      } finally {
        deleting.value = false
      }
    },
  })
}

/**
 * 自动发送初始消息逻辑：
 * - 移除之前的 view 参数判断
 * - 如果是自己的 app，并且没有对话历史，才自动将 initPrompt 作为第一条消息触发对话
 *
 * 网站展示逻辑：
 * - 如果 app 有至少 2 条对话记录，也展示对应的网站
 */
watch(
  () => app.value,
  (val) => {
    if (!val || initialSent.value) return
    initialSent.value = true

    // 如果是 owner，没有历史消息，且有 initPrompt → 自动发送
    const shouldAutoRun = isOwner.value && totalHistoryCount.value === 0 && !!val.initPrompt
    if (shouldAutoRun) {
      sendMessage(val.initPrompt as string)
    } else if (totalHistoryCount.value >= 2) {
      // 有至少 2 条对话记录，展示网站预览
      previewVersion.value = Date.now()
      previewReady.value = true
    }
  },
)

onMounted(async () => {
  // 先加载对话历史
  await loadChatHistory(false)
  // 再获取应用信息（会触发 watch 来决定是否自动发送初始消息）
  await fetchApp()
})

onBeforeUnmount(() => {
  closeEventSource()
})
</script>

<template>
  <div class="chat-page">
    <header class="chat-header">
      <div class="chat-header-left">
        <span class="logo-wrap">
          <img class="logo" src="/logo.png" alt="logo" />
        </span>
        <div class="app-title-group">
          <span class="app-kicker">Workspace</span>
          <span class="app-name">{{ app?.appName || '加载中...' }}</span>
        </div>
      </div>
      <a-space :size="10">
        <a-button :disabled="!app" @click="openDetail">应用详情</a-button>
        <a-button
          type="primary"
          :loading="deploying"
          :disabled="!isOwner"
          @click="handleDeploy"
        >
          部署
        </a-button>
      </a-space>
    </header>

    <div class="chat-body">
      <aside class="chat-panel">
        <div class="panel-header">
          <span class="panel-kicker">Conversation</span>
          <span class="panel-title">与 AI 协作</span>
        </div>
        <div ref="messagesRef" class="messages">
          <!-- 加载更多按钮 -->
          <div v-if="hasMoreHistory" class="load-more-wrap">
            <a-button
              type="link"
              size="small"
              :loading="historyLoading"
              class="load-more-btn"
              @click="handleLoadMore"
            >
              {{ historyLoading ? '加载中...' : '↑ 加载更多历史消息' }}
            </a-button>
          </div>
          <div
            v-for="(msg, idx) in messages"
            :key="idx"
            class="message"
            :class="msg.role === 'user' ? 'message-user' : 'message-ai'"
          >
            <div class="message-content">
              <a-spin v-if="msg.loading && !msg.content" size="small" />
              <template v-else>
                <div
                  v-if="msg.role === 'ai'"
                  class="markdown-body"
                  v-html="renderMarkdown(msg.content)"
                ></div>
                <template v-else>{{ msg.content }}</template>
              </template>
            </div>
          </div>
        </div>
        <div class="input-area">
          <a-tooltip
            :title="app && !isOwner ? '无法在别人的作品下对话哦~' : ''"
            placement="top"
          >
            <div class="input-wrap">
              <a-textarea
                v-model:value="inputMessage"
                :placeholder="isOwner ? '描述越详细，页面越具体，可以一步一步完善生成效果' : '仅作者可继续对话'"
                :auto-size="{ minRows: 3, maxRows: 6 }"
                :disabled="sending || !isOwner"
                @keydown="handleInputKeydown"
              />
            </div>
          </a-tooltip>
          <div class="input-actions">
            <span class="input-hint">Ctrl / ⌘ + Enter 发送</span>
            <a-space :size="10">
              <a-button v-if="sending" danger @click="handleStop">停止生成</a-button>
              <a-button
                type="primary"
                :loading="sending"
                :disabled="!isOwner"
                @click="handleSend"
              >
                发送
              </a-button>
            </a-space>
          </div>
        </div>
      </aside>

      <main class="preview-panel">
        <div v-if="previewReady && previewUrl" class="preview-wrapper">
          <div class="preview-toolbar">
            <span class="preview-dot-group" aria-hidden="true">
              <span class="dot dot-r"></span>
              <span class="dot dot-y"></span>
              <span class="dot dot-g"></span>
            </span>
            <span class="preview-url">{{ previewUrl }}</span>
            <a-space :size="8">
              <a-button size="small" @click="refreshPreview">刷新</a-button>
              <a-button size="small" type="link" :href="previewUrl" target="_blank">
                新窗口
              </a-button>
            </a-space>
          </div>
          <div class="preview-frame">
            <iframe :key="previewVersion" :src="previewUrl" frameborder="0" />
          </div>
        </div>
        <div v-else class="preview-placeholder">
          <div class="placeholder-inner">
            <span class="placeholder-eyebrow">Preview</span>
            <a-empty :description="sending ? 'AI 正在生成网站，请稍候...' : '等待生成网站'" />
          </div>
        </div>
      </main>
    </div>

    <a-modal
      v-model:open="detailVisible"
      title="应用详情"
      :footer="null"
      :width="480"
    >
      <div v-if="app" class="detail-body">
        <div class="detail-item">
          <span class="detail-label">应用名称</span>
          <span class="detail-value">{{ app.appName || '未命名应用' }}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">创建者</span>
          <div class="detail-value detail-user">
            <a-avatar :src="app.user?.userAvatar" size="small">
              {{ app.user?.userName?.[0] ?? 'U' }}
            </a-avatar>
            <span>{{ app.user?.userName ?? '匿名用户' }}</span>
          </div>
        </div>
        <div class="detail-item">
          <span class="detail-label">创建时间</span>
          <span class="detail-value">{{ formatDateTime(app.createTime) }}</span>
        </div>
        <div v-if="app.editTime" class="detail-item">
          <span class="detail-label">最近编辑</span>
          <span class="detail-value">{{ formatDateTime(app.editTime) }}</span>
        </div>
        <div class="detail-footer">
          <a-space :size="10">
            <a-button type="primary" :disabled="!isOwner" @click="handleEditApp">
              修改
            </a-button>
            <a-popconfirm
              title="确定删除？"
              ok-text="删除"
              ok-type="danger"
              cancel-text="取消"
              :disabled="!isOwner"
              @confirm="handleDeleteApp"
            >
              <a-button danger :disabled="!isOwner" :loading="deleting">
                删除
              </a-button>
            </a-popconfirm>
          </a-space>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 140px);
  min-height: 600px;
  animation: fadeUp 0.6s var(--ease-out-expo) both;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  margin-bottom: 16px;
  background: rgba(255, 255, 255, 0.55);
  border: 1px solid rgba(15, 123, 138, 0.1);
  border-radius: 16px;
  backdrop-filter: saturate(140%) blur(14px);
  -webkit-backdrop-filter: saturate(140%) blur(14px);
}

.chat-header-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.logo-wrap {
  position: relative;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 8px 18px rgba(15, 123, 138, 0.22),
              inset 0 0 0 1px rgba(255, 255, 255, 0.4);
}

.logo-wrap::before {
  content: '';
  position: absolute;
  inset: 0;
  background: var(--gradient-primary);
  opacity: 0.12;
  pointer-events: none;
}

.logo {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.app-title-group {
  display: flex;
  flex-direction: column;
  line-height: 1.1;
}

.app-kicker {
  font-size: 10.5px;
  letter-spacing: 0.26em;
  text-transform: uppercase;
  color: var(--ink-400);
  font-weight: 500;
}

.app-name {
  margin-top: 4px;
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 500;
  color: var(--ink-900);
  letter-spacing: -0.01em;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 40vw;
}

.chat-body {
  display: grid;
  grid-template-columns: minmax(360px, 1fr) 2fr;
  gap: 16px;
  flex: 1;
  min-height: 0;
}

.chat-panel {
  display: flex;
  flex-direction: column;
  background: rgba(255, 255, 255, 0.62);
  border: 1px solid rgba(15, 123, 138, 0.12);
  border-radius: 18px;
  backdrop-filter: saturate(140%) blur(14px);
  -webkit-backdrop-filter: saturate(140%) blur(14px);
  overflow: hidden;
}

.panel-header {
  display: flex;
  flex-direction: column;
  padding: 16px 20px 14px;
  border-bottom: 1px solid rgba(15, 123, 138, 0.08);
}

.panel-kicker {
  font-size: 10.5px;
  letter-spacing: 0.28em;
  text-transform: uppercase;
  color: var(--teal-700);
  font-weight: 600;
}

.panel-title {
  margin-top: 4px;
  font-family: var(--font-display);
  font-size: 15px;
  color: var(--ink-800);
  letter-spacing: -0.005em;
}

.messages {
  flex: 1;
  padding: 18px 18px 4px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* 加载更多按钮区域 */
.load-more-wrap {
  display: flex;
  justify-content: center;
  padding: 4px 0 8px;
}

.load-more-btn {
  font-size: 12px;
  color: var(--teal-700);
  letter-spacing: 0.02em;
  transition: all 0.25s var(--ease-out-quart);
}

.load-more-btn:hover {
  color: var(--teal-900);
}

.message {
  display: flex;
}

.message-user {
  justify-content: flex-end;
}

.message-ai {
  justify-content: flex-start;
}

.message-content {
  max-width: 82%;
  padding: 12px 16px;
  border-radius: 14px;
  font-size: 14px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
  transition: transform 0.3s var(--ease-out-expo);
}

.message-user .message-content {
  color: #fff;
  background: var(--gradient-primary);
  border-bottom-right-radius: 4px;
  box-shadow: 0 10px 22px rgba(15, 123, 138, 0.22);
}

.message-ai .message-content {
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(15, 123, 138, 0.08);
  color: var(--ink-800);
  border-bottom-left-radius: 4px;
}

.input-area {
  padding: 14px 16px 16px;
  border-top: 1px solid rgba(15, 123, 138, 0.08);
  background: rgba(255, 255, 255, 0.4);
}

.input-wrap {
  width: 100%;
}

.input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 10px;
}

.input-hint {
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.04em;
  color: var(--ink-400);
}

.preview-panel {
  background: rgba(255, 255, 255, 0.62);
  border: 1px solid rgba(15, 123, 138, 0.12);
  border-radius: 18px;
  backdrop-filter: saturate(140%) blur(14px);
  -webkit-backdrop-filter: saturate(140%) blur(14px);
  overflow: hidden;
  display: flex;
}

.preview-wrapper {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
}

.preview-toolbar {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 14px;
  border-bottom: 1px solid rgba(15, 123, 138, 0.08);
  background: rgba(247, 252, 251, 0.6);
}

.preview-dot-group {
  display: inline-flex;
  gap: 6px;
  flex-shrink: 0;
}

.preview-dot-group .dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  opacity: 0.75;
}

.preview-dot-group .dot-r {
  background: #ff6b6b;
}

.preview-dot-group .dot-y {
  background: #ffd166;
}

.preview-dot-group .dot-g {
  background: #3dd6d0;
}

.preview-url {
  flex: 1;
  min-width: 0;
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--ink-500);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.preview-frame {
  flex: 1;
  width: 100%;
  background: #fff;
}

.preview-frame iframe {
  width: 100%;
  height: 100%;
  border: none;
  display: block;
}

.preview-placeholder {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
}

.placeholder-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.placeholder-eyebrow {
  font-size: 10.5px;
  letter-spacing: 0.28em;
  text-transform: uppercase;
  color: var(--teal-700);
  font-weight: 600;
}

.detail-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.detail-label {
  flex: 0 0 80px;
  color: var(--ink-400);
  font-size: 12px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  font-weight: 600;
}

.detail-value {
  flex: 1;
  color: var(--ink-800);
  font-size: 14px;
  word-break: break-all;
}

.detail-user {
  display: flex;
  align-items: center;
  gap: 8px;
}

.detail-footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
  border-top: 1px solid rgba(15, 123, 138, 0.08);
}

@media (max-width: 960px) {
  .chat-body {
    grid-template-columns: 1fr;
  }
  .app-name {
    max-width: 50vw;
  }
}
</style>

<style>
.markdown-body {
  font-size: 14px;
  line-height: 1.7;
  color: var(--ink-800);
}

.markdown-body p {
  margin: 0 0 8px;
}

.markdown-body p:last-child {
  margin-bottom: 0;
}

.markdown-body pre.hljs {
  margin: 10px 0;
  padding: 14px 16px;
  border-radius: 10px;
  background: #1f2a2f;
  color: #c9d3d5;
  overflow-x: auto;
  font-size: 13px;
  line-height: 1.55;
  box-shadow: 0 10px 24px rgba(31, 42, 47, 0.18);
}

.markdown-body pre.hljs code {
  background: transparent;
  padding: 0;
  font-family: var(--font-mono, Consolas, Monaco, 'Courier New', monospace);
  white-space: pre;
}

.markdown-body code {
  background: rgba(15, 123, 138, 0.1);
  color: var(--teal-700);
  padding: 2px 6px;
  border-radius: 6px;
  font-family: var(--font-mono, Consolas, Monaco, 'Courier New', monospace);
  font-size: 13px;
}

.markdown-body ul,
.markdown-body ol {
  padding-left: 20px;
  margin: 8px 0;
}

.markdown-body h1,
.markdown-body h2,
.markdown-body h3,
.markdown-body h4 {
  margin: 14px 0 8px;
  font-family: var(--font-display, inherit);
  font-weight: 500;
  color: var(--ink-900);
  letter-spacing: -0.01em;
}

.markdown-body a {
  color: var(--teal-700);
  font-weight: 500;
}

.message-user .markdown-body,
.message-user .markdown-body code {
  color: #fff;
}
</style>
