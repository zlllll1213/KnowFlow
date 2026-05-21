export interface ChatSessionVO {
  id: number
  kbId: number
  userId: number
  title: string
  createdAt: string
  updatedAt: string
}

export interface ChatMessageVO {
  id: number
  role: 'user' | 'assistant'
  content: string
  createdAt: string
}

export interface ChatAskRequest {
  kbId: number
  sessionId: number
  question: string
}

export interface SourceChunk {
  id: number
  content: string
  fileName: string
  score?: number
}
