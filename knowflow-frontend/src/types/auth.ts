export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  password: string
  email: string
}

export interface LoginResponse {
  userId: number
  username: string
}

export interface UserVO {
  id: number
  username: string
  email: string
  createdAt: string
}
