<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { register as userRegister } from '@/api/api/userController'

const router = useRouter()
const submitting = ref(false)

const formState = reactive<API.UserRegisterDto>({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
})

const validateCheckPassword = async (_rule: unknown, value: string | undefined) => {
  if (!value) {
    return Promise.reject('请再次输入密码')
  }
  if (value !== formState.userPassword) {
    return Promise.reject('两次输入的密码不一致')
  }
  return Promise.resolve()
}

const handleFinish = async () => {
  submitting.value = true
  try {
    const res = await userRegister(formState)
    if (res.data.code === 0) {
      message.success('注册成功，请登录')
      await router.push('/user/login')
      return
    }
    message.error(res.data.message ?? '注册失败')
  } catch {
    message.error('注册失败，请检查后端服务是否正常')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="auth-page">
    <div class="auth-inner">
      <aside class="auth-intro">
        <span class="auth-eyebrow">
          <span class="auth-eyebrow-dot"></span>
          <span>Create&nbsp;Account</span>
        </span>
        <h1 class="auth-title">
          开始你的<em>创作</em>
        </h1>
        <p class="auth-tagline">三步完成注册 · 即刻开始体验</p>
        <p class="auth-description">
          设置你的账号与密码，<br />
          立即进入 AI 驱动的生成式创作空间。
        </p>
        <ul class="auth-list">
          <li><span class="step">01</span>输入账号与密码</li>
          <li><span class="step">02</span>两次密码确认一致</li>
          <li><span class="step">03</span>跳转登录，开始使用</li>
        </ul>
      </aside>

      <div class="auth-form-area">
        <div class="auth-form-header">
          <h2 class="auth-form-title">注册账号</h2>
          <p class="auth-form-sub">注册成功后将自动引导至登录页面</p>
        </div>

        <a-form class="auth-form" layout="vertical" :model="formState" @finish="handleFinish">
          <a-form-item label="账号" name="userAccount" :rules="[{ required: true, message: '请输入账号' }]">
            <a-input v-model:value="formState.userAccount" size="large" placeholder="请输入账号" />
          </a-form-item>

          <a-form-item label="密码" name="userPassword" :rules="[{ required: true, message: '请输入密码' }]">
            <a-input-password
              v-model:value="formState.userPassword"
              size="large"
              placeholder="请输入密码"
            />
          </a-form-item>

          <a-form-item
            label="确认密码"
            name="checkPassword"
            :rules="[{ validator: validateCheckPassword }]"
          >
            <a-input-password
              v-model:value="formState.checkPassword"
              size="large"
              placeholder="请再次输入密码"
            />
          </a-form-item>

          <a-form-item class="auth-actions">
            <a-button type="primary" html-type="submit" size="large" block :loading="submitting">
              注册
            </a-button>
            <div class="auth-footnote">
              已有账号？
              <RouterLink to="/user/login">前往登录</RouterLink>
            </div>
          </a-form-item>
        </a-form>
      </div>
    </div>
  </section>
</template>

<style scoped>
.auth-page {
  min-height: calc(100vh - 220px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 0;
  animation: fadeUp 0.7s var(--ease-out-expo) both;
}

.auth-inner {
  width: min(100%, 960px);
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr);
  gap: clamp(32px, 6vw, 80px);
  align-items: center;
}

.auth-intro {
  position: relative;
  padding-left: 28px;
}

.auth-intro::before {
  content: '';
  position: absolute;
  left: 0;
  top: 8px;
  bottom: 8px;
  width: 2px;
  border-radius: 2px;
  background: linear-gradient(180deg, var(--teal-400) 0%, var(--teal-200) 60%, transparent 100%);
}

.auth-eyebrow {
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

.auth-eyebrow-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--gradient-accent);
  box-shadow: 0 0 0 4px rgba(61, 214, 208, 0.18);
}

.auth-title {
  margin: 0 0 16px;
  font-family: var(--font-display);
  font-size: clamp(40px, 5.2vw, 60px);
  font-weight: 400;
  line-height: 1.08;
  letter-spacing: -0.02em;
  color: var(--ink-900);
}

.auth-title em {
  font-style: italic;
  font-weight: 500;
  background: var(--gradient-text);
  background-clip: text;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.auth-tagline {
  margin: 0 0 20px;
  font-family: var(--font-display);
  font-style: italic;
  font-size: 17px;
  color: var(--teal-700);
  font-weight: 400;
}

.auth-description {
  margin: 0 0 28px;
  color: var(--ink-500);
  font-size: 15px;
  line-height: 1.7;
}

.auth-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 10px;
}

.auth-list li {
  display: flex;
  align-items: center;
  gap: 14px;
  color: var(--ink-600);
  font-size: 14px;
}

.auth-list li .step {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 500;
  color: var(--teal-700);
  letter-spacing: 0.08em;
  min-width: 22px;
}

.auth-form-area {
  position: relative;
  padding: 4px 0;
}

.auth-form-header {
  margin-bottom: 28px;
}

.auth-form-title {
  margin: 0 0 6px;
  font-family: var(--font-display);
  font-size: 26px;
  font-weight: 500;
  color: var(--ink-900);
  letter-spacing: -0.01em;
}

.auth-form-sub {
  margin: 0;
  color: var(--ink-400);
  font-size: 13.5px;
}

.auth-form :deep(.ant-form-item) {
  margin-bottom: 20px;
}

.auth-actions {
  margin-bottom: 0 !important;
}

.auth-actions :deep(.ant-form-item-control-input-content) {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.auth-footnote {
  text-align: center;
  font-size: 13.5px;
  color: var(--ink-500);
}

.auth-footnote a {
  color: var(--teal-700);
  font-weight: 600;
  position: relative;
}

.auth-footnote a::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -2px;
  height: 1px;
  background: currentColor;
  transform: scaleX(0);
  transform-origin: right;
  transition: transform 0.3s var(--ease-out-expo);
}

.auth-footnote a:hover::after {
  transform: scaleX(1);
  transform-origin: left;
}

@media (max-width: 820px) {
  .auth-inner {
    grid-template-columns: 1fr;
    gap: 32px;
  }
  .auth-intro {
    padding-left: 22px;
  }
}
</style>
