import axios from 'axios'
import router from '@/router'
import { getToken, removeToken } from '@/utils/token'

export interface Result<T = unknown> {
  code: number
  message: string
  data: T
}

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081',
  timeout: 15000,
})

function redirectToLogin() {
  removeToken()
  const current = router.currentRoute.value
  if (!['/login', '/register'].includes(current.path)) {
    router.replace({ path: '/login', query: { redirect: current.fullPath } })
  }
}

request.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const res: Result = response.data
    if (res.code === 0 || res.code === 200) {
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
