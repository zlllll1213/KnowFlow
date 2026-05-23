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
  sources?: RagSourceChunk[]
  createdAt: string
}

export interface ChatAskRequest {
  kbId: number
  sessionId: number
  question: string
}

/** 引用来源片段（与后端 RagSourceChunk 对齐） */
export interface RagSourceChunk {
  chunkId: number
  documentId: number
  fileName: string
  chunkIndex: number
  content: string
  score: number
}

/** @deprecated 使用 RagSourceChunk */
export type SourceChunk = RagSourceChunk
