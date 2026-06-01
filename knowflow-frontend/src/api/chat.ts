import request from './request'
import { streamRequest } from './sse'
import type { AskStreamCallbacks } from './sse'
import type { ChatSessionVO, ChatMessageVO, ChatAskRequest, AgentResponse } from '@/types/chat'
import type { PageResult } from '@/types/common'

export type { AskStreamCallbacks } from './sse'

export function createSession(kbId: number, title?: string): Promise<ChatSessionVO> {
  return request.post('/api/chat/session', { kbId, title })
}

export function listSessions(kbId: number, page = 1, size = 100): Promise<PageResult<ChatSessionVO>> {
  return request.get('/api/chat/session/list', { params: { kbId, page, size } })
}

export function askQuestion(data: ChatAskRequest): Promise<ChatMessageVO> {
  return request.post('/api/chat/ask', data)
}

export function askQuestionStream(data: ChatAskRequest, callbacks: AskStreamCallbacks = {}): Promise<ChatMessageVO> {
  return streamRequest<ChatMessageVO>('/api/chat/ask/stream', data, callbacks)
}

export function askAgent(data: ChatAskRequest): Promise<AgentResponse> {
  return request.post('/api/agent/ask', data)
}

export function askAgentStream(data: ChatAskRequest, callbacks: AskStreamCallbacks = {}): Promise<AgentResponse> {
  return streamRequest<AgentResponse>('/api/agent/ask/stream', data, callbacks)
}

export function getChatHistory(sessionId: number, page = 1, size = 100): Promise<PageResult<ChatMessageVO>> {
  return request.get('/api/chat/history', { params: { sessionId, page, size } })
}
