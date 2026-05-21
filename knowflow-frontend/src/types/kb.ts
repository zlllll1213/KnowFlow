export interface KbVO {
  id: number
  name: string
  description: string
  createdAt: string
  updatedAt: string
  documentCount?: number
}

export interface KbCreateRequest {
  name: string
  description?: string
}

export interface KbUpdateRequest {
  name?: string
  description?: string
}
