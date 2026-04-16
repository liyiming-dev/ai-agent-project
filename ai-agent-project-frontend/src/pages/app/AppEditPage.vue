<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  getAppVoById,
  getAppVoByIdByAdmin,
  updateApp,
  updateAppByAdmin,
} from '@/api/api/appController'
import { useLoginUserStore } from '@/stores/loginUser'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

const appId = computed(() => route.params.id as string)
const isAdmin = computed(() => loginUserStore.loginUser.userRole === 'admin')

const loading = ref(false)
const submitting = ref(false)

const form = reactive<API.AppAdminUpdateDto>({
  id: undefined,
  appName: '',
  cover: '',
  priority: 0,
})

const original = reactive<API.AppAdminUpdateDto>({
  appName: '',
  cover: '',
  priority: 0,
})

const fetchApp = async () => {
  if (!appId.value) return
  loading.value = true
  try {
    const id = appId.value as unknown as number
    const res = isAdmin.value
      ? await getAppVoByIdByAdmin({ id })
      : await getAppVoById({ id })
    if (res.data.code === 0 && res.data.data) {
      const app = res.data.data
      form.id = app.id
      form.appName = app.appName ?? ''
      form.cover = app.cover ?? ''
      form.priority = app.priority ?? 0
      original.appName = form.appName
      original.cover = form.cover
      original.priority = form.priority
      return
    }
    message.error(res.data.message ?? '获取应用信息失败')
  } catch {
    message.error('获取应用信息失败')
  } finally {
    loading.value = false
  }
}

const handleSubmit = async () => {
  if (!form.id) return
  submitting.value = true
  try {
    let res
    if (isAdmin.value) {
      const payload: API.AppAdminUpdateDto = { id: form.id }
      if (form.appName !== original.appName) payload.appName = form.appName
      if (form.cover !== original.cover) payload.cover = form.cover
      if (form.priority !== original.priority) payload.priority = form.priority
      res = await updateAppByAdmin(payload)
    } else {
      res = await updateApp({ id: form.id, appName: form.appName })
    }
    if (res.data.code === 0) {
      message.success('更新成功')
      router.back()
      return
    }
    message.error(res.data.message ?? '更新失败')
  } catch {
    message.error('更新失败')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  void fetchApp()
})
</script>

<template>
  <section class="edit-page">
    <div class="edit-inner">
      <aside class="edit-intro">
        <span class="edit-eyebrow">
          <span class="edit-eyebrow-dot"></span>
          <span>Edit&nbsp;Application</span>
        </span>
        <h1 class="edit-title">
          精修你的<em>作品</em>
        </h1>
        <p class="edit-tagline">{{ isAdmin ? '管理员视角' : '作者视角' }} · 轻量编辑</p>
        <p class="edit-description">
          {{ isAdmin
            ? '可以更新应用名称、封面与优先级，仅变更的字段会被写入。'
            : '可以修改应用的名称，让它更符合当前的主题与风格。' }}
        </p>
        <ul class="edit-list">
          <li><span class="dot"></span>保存后立即生效</li>
          <li><span class="dot"></span>取消将返回上一级</li>
        </ul>
      </aside>

      <div class="edit-form-area">
        <div class="edit-form-header">
          <h2 class="edit-form-title">编辑信息</h2>
          <p class="edit-form-sub">根据需要修改下列字段后保存</p>
        </div>

        <a-spin :spinning="loading">
          <a-form class="edit-form" layout="vertical" :model="form" @finish="handleSubmit">
            <a-form-item label="应用名称">
              <a-input v-model:value="form.appName" size="large" placeholder="请输入应用名称" />
            </a-form-item>
            <template v-if="isAdmin">
              <a-form-item label="应用封面">
                <a-input v-model:value="form.cover" size="large" placeholder="请输入封面图 URL" />
              </a-form-item>
              <a-form-item label="优先级">
                <a-input-number v-model:value="form.priority" :min="0" :max="99" size="large" style="width: 100%" />
              </a-form-item>
            </template>
            <a-form-item class="edit-actions">
              <a-space :size="12">
                <a-button type="primary" html-type="submit" size="large" :loading="submitting">保存</a-button>
                <a-button size="large" @click="router.back()">取消</a-button>
              </a-space>
            </a-form-item>
          </a-form>
        </a-spin>
      </div>
    </div>
  </section>
</template>

<style scoped>
.edit-page {
  min-height: calc(100vh - 220px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 0;
  animation: fadeUp 0.7s var(--ease-out-expo) both;
}

.edit-inner {
  width: min(100%, 960px);
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.1fr);
  gap: clamp(32px, 6vw, 80px);
  align-items: center;
}

.edit-intro {
  position: relative;
  padding-left: 28px;
}

.edit-intro::before {
  content: '';
  position: absolute;
  left: 0;
  top: 8px;
  bottom: 8px;
  width: 2px;
  border-radius: 2px;
  background: linear-gradient(180deg, var(--teal-400) 0%, var(--teal-200) 60%, transparent 100%);
}

.edit-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--teal-700);
  margin-bottom: 22px;
}

.edit-eyebrow-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--gradient-accent);
  box-shadow: 0 0 0 4px rgba(61, 214, 208, 0.18);
}

.edit-title {
  margin: 0 0 16px;
  font-family: var(--font-display);
  font-size: clamp(36px, 5vw, 54px);
  font-weight: 400;
  line-height: 1.08;
  letter-spacing: -0.02em;
  color: var(--ink-900);
}

.edit-title em {
  font-style: italic;
  font-weight: 500;
  background: var(--gradient-text);
  background-clip: text;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.edit-tagline {
  margin: 0 0 20px;
  font-family: var(--font-display);
  font-style: italic;
  font-size: 17px;
  color: var(--teal-700);
  font-weight: 400;
}

.edit-description {
  margin: 0 0 28px;
  color: var(--ink-500);
  font-size: 15px;
  line-height: 1.7;
}

.edit-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 10px;
}

.edit-list li {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--ink-600);
  font-size: 14px;
}

.edit-list li .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--gradient-accent);
  flex-shrink: 0;
}

.edit-form-area {
  position: relative;
  padding: 4px 0;
}

.edit-form-header {
  margin-bottom: 28px;
}

.edit-form-title {
  margin: 0 0 6px;
  font-family: var(--font-display);
  font-size: 26px;
  font-weight: 500;
  color: var(--ink-900);
  letter-spacing: -0.01em;
}

.edit-form-sub {
  margin: 0;
  color: var(--ink-400);
  font-size: 13.5px;
}

.edit-form :deep(.ant-form-item) {
  margin-bottom: 20px;
}

.edit-actions {
  margin-bottom: 0 !important;
}

@media (max-width: 820px) {
  .edit-inner {
    grid-template-columns: 1fr;
    gap: 32px;
  }
  .edit-intro {
    padding-left: 22px;
  }
}
</style>
