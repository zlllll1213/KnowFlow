import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi, fetchMe, logout as logoutApi } from '@/api/auth'
import { getToken, setToken, removeToken } from '@/utils/token'
import type { LoginRequest } from '@/types/auth'
import type { UserVO } from '@/types/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(getToken())
  const userInfo = ref<UserVO | null>(null)

  async function login(data: LoginRequest) {
    const res = await loginApi({ ...data, username: data.username.trim() })
    setToken()
    token.value = getToken()
    userInfo.value = { id: res.userId, username: res.username, email: '', createdAt: '' }
    try {
      await fetchUserInfo()
    } catch (error) {
      await logout()
      throw error
    }
  }

  async function logout() {
    await logoutApi().catch(() => undefined)
    removeToken()
    token.value = null
    userInfo.value = null
  }

  async function fetchUserInfo() {
    const info = await fetchMe()
    setToken()
    token.value = getToken()
    userInfo.value = info
  }

  return { token, userInfo, login, logout, fetchUserInfo }
})
