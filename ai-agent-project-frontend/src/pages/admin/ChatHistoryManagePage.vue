<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  listAllChatHistoryByPageForAdmin,
  remove,
} from '@/api/api/chatHistoryController'

type TablePagination = {
  current?: number
  pageSize?: number
}

const columns = [
  { title: 'ID', dataIndex: 'id', width: 100 },
  { title: '消息内容', dataIndex: 'message', ellipsis: true },
  { title: '消息类型', dataIndex: 'messageType', width: 100 },
  { title: '应用ID', dataIndex: 'appId', width: 100 },
  { title: '用户ID', dataIndex: 'userId', width: 100 },
  { title: '创建时间', dataIndex: 'createTime', width: 180 },
  { title: '操作', key: 'action', width: 100 },
]

const data = ref<API.ChatHistory[]>([])
const total = ref(0)
const loading = ref(false)

const searchParams = reactive<API.ChatHistoryQueryRequest>({
  pageNum: 1,
  pageSize: 10,
})

const pagination = computed(() => ({
  current: searchParams.pageNum ?? 1,
  pageSize: searchParams.pageSize ?? 10,
  total: total.value,
  showSizeChanger: true,
  showTotal: (value: number) => `共 ${value} 条`,
}))

const formatDateTime = (value?: string) => {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (n: number) => `${n}`.padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await listAllChatHistoryByPageForAdmin({ ...searchParams })
    if (res.data.code === 0 && res.data.data) {
      data.value = res.data.data.records ?? []
      total.value = res.data.data.totalRow ?? 0
      return
    }
    message.error(res.data.message ?? '获取对话记录失败')
  } catch {
    message.error('获取对话记录失败')
  } finally {
    loading.value = false
  }
}

const handleTableChange = (page: TablePagination) => {
  searchParams.pageNum = page.current ?? 1
  searchParams.pageSize = page.pageSize ?? 10
  void fetchData()
}

const doSearch = () => {
  searchParams.pageNum = 1
  void fetchData()
}

const doDelete = (id?: number) => {
  if (!id) return
  Modal.confirm({
    title: '确定要删除该对话记录吗？',
    onOk: async () => {
      const res = await remove({ id })
      if (res.data) {
        message.success('删除成功')
        void fetchData()
      } else {
        message.error('删除失败')
      }
    },
  })
}

onMounted(() => {
  void fetchData()
})
</script>

<template>
  <section class="admin-page">
    <header class="admin-header">
      <span class="admin-eyebrow">
        <span class="admin-eyebrow-dot"></span>
        <span>Admin&nbsp;·&nbsp;Chat History</span>
      </span>
      <h1 class="admin-title">
        对话<em>管理</em>
      </h1>
      <p class="admin-description">管理系统中的所有对话记录，支持搜索、查看与删除。</p>
      <span class="admin-rule" aria-hidden="true"></span>
    </header>

    <div class="admin-toolbar">
      <a-form layout="inline" :model="searchParams" @finish="doSearch">
        <a-form-item label="消息内容">
          <a-input v-model:value="searchParams.message" placeholder="输入消息内容" allow-clear />
        </a-form-item>
        <a-form-item label="消息类型">
          <a-select
            v-model:value="searchParams.messageType"
            placeholder="选择消息类型"
            allow-clear
            style="width: 120px"
          >
            <a-select-option value="user">用户</a-select-option>
            <a-select-option value="ai">AI</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="应用ID">
          <a-input-number v-model:value="searchParams.appId" placeholder="应用ID" />
        </a-form-item>
        <a-form-item label="用户ID">
          <a-input-number v-model:value="searchParams.userId" placeholder="用户ID" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" html-type="submit">搜索</a-button>
        </a-form-item>
      </a-form>
    </div>

    <a-table
      class="admin-table"
      :columns="columns"
      :data-source="data"
      :loading="loading"
      :pagination="pagination"
      row-key="id"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'message'">
          <a-tooltip :title="record.message">
            <span class="message-cell">{{ record.message }}</span>
          </a-tooltip>
        </template>
        <template v-else-if="column.dataIndex === 'messageType'">
          <a-tag :color="record.messageType === 'user' ? 'blue' : 'green'">
            {{ record.messageType === 'user' ? '用户' : 'AI' }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'createTime'">
          {{ formatDateTime(record.createTime) }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="link" danger @click="doDelete(record.id)">删除</a-button>
          </a-space>
        </template>
      </template>
    </a-table>
  </section>
</template>

<style scoped>
.admin-page {
  width: min(100%, 1240px);
  margin: 0 auto;
  padding: 16px 0 40px;
  animation: fadeUp 0.7s var(--ease-out-expo) both;
}

.admin-header {
  position: relative;
  padding-left: 28px;
  margin-bottom: 36px;
}

.admin-header::before {
  content: '';
  position: absolute;
  left: 0;
  top: 6px;
  bottom: 6px;
  width: 2px;
  border-radius: 2px;
  background: linear-gradient(180deg, var(--teal-400) 0%, var(--teal-200) 60%, transparent 100%);
}

.admin-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--teal-700);
  margin-bottom: 18px;
}

.admin-eyebrow-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--gradient-accent);
  box-shadow: 0 0 0 4px rgba(61, 214, 208, 0.18);
}

.admin-title {
  margin: 0 0 14px;
  font-family: var(--font-display);
  font-size: clamp(32px, 4.2vw, 46px);
  font-weight: 400;
  line-height: 1.1;
  letter-spacing: -0.02em;
  color: var(--ink-900);
}

.admin-title em {
  font-style: italic;
  font-weight: 500;
  background: var(--gradient-text);
  background-clip: text;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.admin-description {
  margin: 0 0 22px;
  color: var(--ink-500);
  font-size: 15px;
  line-height: 1.7;
}

.admin-rule {
  display: block;
  width: 100%;
  height: 1px;
  background: linear-gradient(
    90deg,
    rgba(15, 123, 138, 0.22) 0%,
    rgba(61, 214, 208, 0.14) 40%,
    transparent 100%
  );
}

.admin-toolbar {
  margin-bottom: 20px;
}

.admin-table {
  margin-top: 4px;
}

.message-cell {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 300px;
}

@media (max-width: 768px) {
  .admin-header {
    padding-left: 20px;
  }
}
</style>
