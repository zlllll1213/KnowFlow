export interface KbVO {
  id: number
  name: string
  description: string
  createdAt: string
  updatedAt: string
  documentCount: number
  doneCount: number
}

export interface KbCreateRequest {
  name: string
  description?: string
}

export interface KbUpdateRequest {
  name?: string
  description?: string
}
