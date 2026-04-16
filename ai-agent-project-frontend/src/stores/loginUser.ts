import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getLoginUser } from '@/api/api/userController'

const DEFAULT_LOGIN_USER: API.LoginUserVO = {
  userName: '未登录',
}

export const useLoginUserStore = defineStore('loginUser', () => {
  const loginUser = ref<API.LoginUserVO>({ ...DEFAULT_LOGIN_USER })

  // 获取登录用户信息
  async function fetchLoginUser() {
    try {
      const res = await getLoginUser()
      if (res.data.code === 0 && res.data.data) {
        loginUser.value = res.data.data
        return
      }
      resetLoginUser()
    } catch {
      resetLoginUser()
    }
  }

  // 更新登录用户信息
  function setLoginUser(newLoginUser: API.LoginUserVO) {
    loginUser.value = newLoginUser
  }

  function resetLoginUser() {
    loginUser.value = { ...DEFAULT_LOGIN_USER }
  }

  return { loginUser, setLoginUser, resetLoginUser, fetchLoginUser }
})
