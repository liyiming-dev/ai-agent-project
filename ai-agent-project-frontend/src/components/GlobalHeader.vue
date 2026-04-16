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
  background: rgba(247, 252, 251, 0.72);
  backdrop-filter: saturate(140%) blur(18px);
  -webkit-backdrop-filter: saturate(140%) blur(18px);
}

.global-header::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 1px;
  background: linear-gradient(
    90deg,
    transparent 0%,
    rgba(15, 123, 138, 0.18) 18%,
    rgba(61, 214, 208, 0.28) 50%,
    rgba(15, 123, 138, 0.18) 82%,
    transparent 100%
  );
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
  width: 42px;
  height: 42px;
  border-radius: 14px;
  overflow: hidden;
  box-shadow: 0 10px 22px rgba(15, 123, 138, 0.28),
              inset 0 0 0 1px rgba(255, 255, 255, 0.45);
}

.brand-logo-wrap::before {
  content: '';
  position: absolute;
  inset: 0;
  background: var(--gradient-primary);
  opacity: 0.12;
  z-index: 1;
  pointer-events: none;
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
  font-weight: 500;
  font-size: 22px;
  letter-spacing: -0.01em;
  color: var(--ink-800);
  white-space: nowrap;
}

.brand-title em {
  font-style: italic;
  font-weight: 400;
  background: var(--gradient-text);
  background-clip: text;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.brand-kicker {
  margin-top: 4px;
  font-size: 10.5px;
  letter-spacing: 0.26em;
  text-transform: uppercase;
  color: var(--ink-400);
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
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid rgba(15, 123, 138, 0.1);
  transition: border-color 0.25s var(--ease-out-quart), background 0.25s var(--ease-out-quart);
}

.login-user:hover {
  border-color: rgba(15, 123, 138, 0.25);
  background: rgba(255, 255, 255, 0.82);
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
