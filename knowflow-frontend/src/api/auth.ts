import request from './request'
import type { LoginRequest, RegisterRequest, LoginResponse, UserVO } from '@/types/auth'

export function login(data: LoginRequest): Promise<LoginResponse> {
  return request.post('/api/auth/login', data)
}

export function register(data: RegisterRequest): Promise<UserVO> {
  return request.post('/api/auth/register', data)
}

export function fetchMe(): Promise<UserVO> {
  return request.get('/api/auth/me')
}

export function logout(): Promise<void> {
  return request.post('/api/auth/logout')
}
