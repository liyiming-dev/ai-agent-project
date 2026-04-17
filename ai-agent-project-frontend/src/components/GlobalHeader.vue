<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Avatar, Button, Menu, Space, message } from 'ant-design-vue'
import type { MenuProps } from 'ant-design-vue'
import { logout } from '@/api/api/userController'

import { useLoginUserStore } from '@/stores/loginUser.ts'
const loginUserStore = useLoginUserStore()

import { globalMenuItems } from '@/config/menu'

const route = useRoute()
const router = useRouter()
const isLoggingOut = ref(false)

const menuItems = computed<MenuProps['items']>(() => {
  return globalMenuItems
    .filter((item) => {
      if (item.key.startsWith('/admin')) {
        return loginUserStore.loginUser.userRole === 'admin'
      }
      return true
    })
    .map((item) => ({
      key: item.key,
      label: item.label,
    }))
})

const selectedKeys = computed(() => {
  const matchedItem = globalMenuItems.find(
    (item) => route.path.startsWith(item.key) && item.key !== '/',
  )

  if (matchedItem) {
    return [matchedItem.key]
  }

  return ['/']
})

const handleMenuClick: MenuProps['onClick'] = ({ key }) => {
  router.push(key as string)
}

const loginPageUrl = computed(() => `/user/login?redirect=${encodeURIComponent(route.fullPath)}`)

const goToLoginPage = () => {
  router.push(loginPageUrl.value)
}

const handleLogout = async () => {
  isLoggingOut.value = true
  try {
    const res = await logout()
    if (res.data.code === 0) {
      loginUserStore.resetLoginUser()
      message.success('退出登录成功')
      await router.push('/')
      return
    }
    message.error(res.data.message ?? '退出登录失败')
  } catch {
    message.error('退出登录失败，请稍后重试')
  } finally {
    isLoggingOut.value = false
  }
}
</script>

<template>
  <header class="global-header">
    <div class="header-content">
      <RouterLink class="brand" to="/">
        <span class="brand-logo-wrap">
          <img alt="网站 Logo" class="brand-logo" src="/logo.png" />
        </span>
        <span class="brand-text">
          <span class="brand-title">AI <em>Agent</em></span>
          <span class="brand-kicker">一句话 · 呈所想</span>
        </span>
      </RouterLink>

      <Menu
        class="header-menu"
        mode="horizontal"
        :items="menuItems"
        :selected-keys="selectedKeys"
        @click="handleMenuClick"
      />

      <div class="user-login-status">
        <div v-if="loginUserStore.loginUser.id" class="login-user">
          <Space :size="12">
            <Avatar :src="loginUserStore.loginUser.userAvatar">
              {{ loginUserStore.loginUser.userName?.[0] ?? 'U' }}
            </Avatar>
            <span class="user-name">{{ loginUserStore.loginUser.userName ?? '无名' }}</span>
            <Button type="link" :loading="isLoggingOut" @click="handleLogout">退出</Button>
          </Space>
        </div>
        <div v-else>
          <Button type="primary" size="large" @click="goToLoginPage">登录</Button>
        </div>
      </div>
    </div>
  </header>
</template>

<style scoped>
.global-header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: rgba(252, 250, 245, 0.88);
  backdrop-filter: saturate(160%) blur(16px);
  -webkit-backdrop-filter: saturate(160%) blur(16px);
  border-bottom: 1px solid var(--ink-200);
}

.header-content {
  width: min(calc(100% - 40px), 1240px);
  min-height: 76px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  gap: 28px;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 14px;
  flex-shrink: 0;
  position: relative;
  padding: 6px 4px;
  transition: transform 0.4s var(--ease-out-expo);
}

.brand:hover {
  transform: translateY(-1px);
}

.brand-logo-wrap {
  position: relative;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  overflow: hidden;
  background: var(--hot-600);
  box-shadow: inset 0 0 0 2px var(--ink-900);
}

.brand-logo-wrap::before {
  content: '';
  position: absolute;
  inset: 0;
  background: var(--hot-600);
  z-index: 1;
  pointer-events: none;
  mix-blend-mode: multiply;
}

.brand-logo {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1;
}

.brand-title {
  font-family: var(--font-display);
  font-weight: 600;
  font-size: 19px;
  letter-spacing: 0;
  color: var(--ink-900);
  white-space: nowrap;
}

.brand-title em {
  font-style: normal;
  font-weight: 700;
  color: var(--hot-600);
}

.brand-kicker {
  margin-top: 4px;
  font-family: var(--font-mono);
  font-size: 10.5px;
  letter-spacing: 0.08em;
  color: var(--ink-500);
  font-weight: 500;
}

.header-menu {
  min-width: 0;
  flex: 1;
}

.user-login-status {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.login-user {
  display: flex;
  align-items: center;
  padding: 6px 14px 6px 6px;
  border-radius: 999px;
  background: #FFFFFF;
  border: 1px solid var(--ink-200);
  transition: border-color 0.2s var(--ease-out-quart);
}

.login-user:hover {
  border-color: var(--ink-900);
}

.user-name {
  max-width: 160px;
  overflow: hidden;
  color: var(--ink-800);
  font-weight: 600;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:deep(.header-menu .ant-menu-overflow) {
  justify-content: center;
}

@media (max-width: 768px) {
  .header-content {
    width: min(calc(100% - 24px), 1240px);
    padding: 12px 0;
    gap: 12px;
    flex-wrap: wrap;
    min-height: unset;
  }

  .brand-kicker {
    display: none;
  }

  .brand-title {
    font-size: 18px;
  }

  .header-menu {
    order: 3;
    flex-basis: 100%;
  }

  .user-login-status {
    margin-left: auto;
  }

  :deep(.header-menu .ant-menu-overflow) {
    justify-content: flex-start;
  }
}
</style>
