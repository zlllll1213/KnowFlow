import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi, fetchMe } from '@/api/auth'
import { getToken, setToken, removeToken } from '@/utils/token'
import type { LoginRequest } from '@/types/auth'
import type { UserVO } from '@/types/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(getToken())
  const userInfo = ref<UserVO | null>(null)

  async function login(data: LoginRequest) {
    const res = await loginApi({ ...data, username: data.username.trim() })
    setToken(res.token)
    token.value = res.token
    userInfo.value = { id: res.userId, username: res.username, email: '', createdAt: '' }
    try {
      await fetchUserInfo()
    } catch (error) {
      logout()
      throw error
    }
  }

  function logout() {
    removeToken()
    token.value = null
    userInfo.value = null
  }

  async function fetchUserInfo() {
    const info = await fetchMe()
    userInfo.value = info
  }

  return { token, userInfo, login, logout, fetchUserInfo }
})
