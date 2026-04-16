<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { deleteUser, listUserVoByPage } from '@/api/api/userController'

type TablePagination = {
  current?: number
  pageSize?: number
}

type UserTableColumn = {
  title: string
  dataIndex?: keyof API.UserVO
  key?: string
}

const columns: UserTableColumn[] = [
  {
    title: 'ID',
    dataIndex: 'id',
  },
  {
    title: '账号',
    dataIndex: 'userAccount',
  },
  {
    title: '用户名',
    dataIndex: 'userName',
  },
  {
    title: '头像',
    dataIndex: 'userAvatar',
  },
  {
    title: '简介',
    dataIndex: 'userProfile',
  },
  {
    title: '用户角色',
    dataIndex: 'userRole',
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
  },
  {
    title: '操作',
    key: 'action',
  },
]

const data = ref<API.UserVO[]>([])
const total = ref(0)
const loading = ref(false)

const searchParams = reactive<API.UserQueryDto>({
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
  if (!value) {
    return '-'
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  const pad = (num: number) => `${num}`.padStart(2, '0')

  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await listUserVoByPage({
      ...searchParams,
    })

    if (res.data.code === 0 && res.data.data) {
      data.value = res.data.data.records ?? []
      total.value = res.data.data.totalRow ?? 0
      return
    }

    message.error(res.data.message ?? '获取用户数据失败')
  } catch {
    message.error('获取用户数据失败，请稍后重试')
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
  fetchData()
}

const doDelete = async (id: string) => {
  if (!id) {
    return
  }
  const res = await deleteUser({ id })
  if (res.data.code === 0) {
    message.success('删除成功')
    fetchData()
  } else {
    message.error('删除失败')
  }
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
        <span>Admin&nbsp;·&nbsp;Users</span>
      </span>
      <h1 class="admin-title">
        用户<em>管理</em>
      </h1>
      <p class="admin-description">查看系统中的用户列表，并按分页加载基础信息。</p>
      <span class="admin-rule" aria-hidden="true"></span>
    </header>

    <div class="admin-toolbar">
      <a-form layout="inline" :model="searchParams" @finish="doSearch">
        <a-form-item label="账号">
          <a-input v-model:value="searchParams.userAccount" placeholder="输入账号" allow-clear />
        </a-form-item>
        <a-form-item label="用户名">
          <a-input v-model:value="searchParams.userName" placeholder="输入用户名" allow-clear />
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
        <template v-if="column.dataIndex === 'userAvatar'">
          <a-image v-if="record.userAvatar" :src="record.userAvatar" :width="72" />
          <span v-else>-</span>
        </template>

        <template v-else-if="column.dataIndex === 'userRole'">
          <a-tag :color="record.userRole === 'admin' ? 'green' : 'blue'">
            {{ record.userRole === 'admin' ? '管理员' : '普通用户' }}
          </a-tag>
        </template>

        <template v-else-if="column.dataIndex === 'createTime'">
          {{ formatDateTime(record.createTime) }}
        </template>

        <template v-else-if="column.key === 'action'">
          <a-button type="link" danger @click="doDelete(record.id)">删除</a-button>
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

@media (max-width: 768px) {
  .admin-header {
    padding-left: 20px;
  }
}
</style>
