import axios from 'axios'
import router from '@/router'
import { removeToken } from '@/utils/token'
import { ensureCsrfToken } from '@/utils/csrf'

export interface Result<T = unknown> {
  code: number
  message: string
  data: T
}

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081',
  timeout: 15000,
  withCredentials: true,
})

function redirectToLogin() {
  removeToken()
  const current = router.currentRoute.value
  if (!['/login', '/register'].includes(current.path)) {
    router.replace({ path: '/login', query: { redirect: current.fullPath } }).catch(() => {
      // fallback if router is not ready
      window.location.href = '/login'
    })
  }
}

request.interceptors.request.use((config) => {
  const method = (config.method || 'get').toUpperCase()
  if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) {
    return ensureCsrfToken(request.defaults.baseURL || '').then((token) => {
      if (token) {
        config.headers['X-XSRF-TOKEN'] = token
      }
      return config
    })
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const res = response.data as Result
    if (res.code === 0 || res.code === 200) {
      // axios interceptor unwrapping — the caller's declared return type provides type safety
      return res.data as any
    }
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  (error) => {
    if (error.response?.status === 401) {
      redirectToLogin()
    }
    const msg = error.response?.data?.message || error.message || '请求失败'
    return Promise.reject(new Error(msg))
  }
)

export default request
