import axios from 'axios'
import { message } from 'ant-design-vue'

function getLoginRedirectUrl() {
  const redirect = `${window.location.pathname}${window.location.search}${window.location.hash}`
  return `/user/login?redirect=${encodeURIComponent(redirect)}`
}

// 处理 Java Long 超过 JS 安全整数的精度问题：把 JSON 文本里过大的数字字面量转成字符串
function safeJsonParse(text: string) {
  if (!text) return text
  const safe = text.replace(
    /:\s*(-?\d{16,})(?=\s*[,}\]])/g,
    (match, num: string) => {
      const n = Number(num)
      if (!Number.isSafeInteger(n)) {
        return `:"${num}"`
      }
      return match
    },
  )
  try {
    return JSON.parse(safe)
  } catch {
    return text
  }
}

// 创建 Axios 实例
const myAxios = axios.create({
  baseURL: 'http://localhost:8123/api',
  timeout: 60000,
  withCredentials: true,
  transformResponse: [
    (data) => {
      if (typeof data === 'string') {
        return safeJsonParse(data)
      }
      return data
    },
  ],
})

// 全局请求拦截器
myAxios.interceptors.request.use(
  function (config) {
    // Do something before request is sent
    return config
  },
  function (error) {
    // Do something with request error
    return Promise.reject(error)
  },
)

// 全局响应拦截器
myAxios.interceptors.response.use(
  function (response) {
    const { data } = response
    const responseUrl = response.request?.responseURL ?? ''
    // 未登录
    if (data.code === 40100) {
      // 不是获取用户信息的请求，并且用户目前不是已经在用户登录页面，则跳转到登录页面
      if (
        !responseUrl.includes('user/get/login') &&
        !window.location.pathname.includes('/user/login')
      ) {
        message.warning('请先登录')
        window.location.href = getLoginRedirectUrl()
      }
    }
    return response
  },
  function (error) {
    // Any status codes that falls outside the range of 2xx cause this function to trigger
    // Do something with response error
    return Promise.reject(error)
  },
)

export default myAxios
