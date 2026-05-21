import request from './request'
import type { ChatSessionVO, ChatMessageVO, ChatAskRequest } from '@/types/chat'

export function createSession(kbId: number, title?: string): Promise<ChatSessionVO> {
  return request.post('/api/chat/session', { kbId, title })
}

export function listSessions(kbId: number): Promise<ChatSessionVO[]> {
  return request.get('/api/chat/session/list', { params: { kbId } })
}

export function askQuestion(data: ChatAskRequest): Promise<ChatMessageVO> {
  return request.post('/api/chat/ask', data)
}

export function getChatHistory(sessionId: number): Promise<ChatMessageVO[]> {
  return request.get('/api/chat/history', { params: { sessionId } })
}
