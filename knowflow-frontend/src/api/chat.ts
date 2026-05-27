import request from './request'
import { getToken } from '@/utils/token'
import type { ChatSessionVO, ChatMessageVO, ChatAskRequest, RagSourceChunk, AgentResponse } from '@/types/chat'
import type { PageResult } from '@/types/common'

export function createSession(kbId: number, title?: string): Promise<ChatSessionVO> {
  return request.post('/api/chat/session', { kbId, title })
}

export function listSessions(kbId: number, page = 1, size = 100): Promise<PageResult<ChatSessionVO>> {
  return request.get('/api/chat/session/list', { params: { kbId, page, size } })
}

export function askQuestion(data: ChatAskRequest): Promise<ChatMessageVO> {
  return request.post('/api/chat/ask', data)
}

export interface AskStreamCallbacks {
  onToken?: (token: string) => void
  onSources?: (sources: RagSourceChunk[]) => void
  onDone?: (message: ChatMessageVO | AgentResponse) => void
  onMeta?: (meta: Partial<AgentResponse>) => void
  onError?: (message: string) => void
}

async function streamRequest<TDone>(path: string, data: ChatAskRequest, callbacks: AskStreamCallbacks = {}): Promise<TDone> {
  const baseURL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081'
  const token = getToken()
  const response = await fetch(`${baseURL}${path}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(data),
  })

  if (!response.ok || !response.body) {
    throw new Error(`流式问答失败: ${response.status}`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let finalMessage: TDone | null = null

  function handleEvent(raw: string) {
    const lines = raw.split(/\r?\n/)
    let event = 'message'
    const dataLines: string[] = []

    for (const line of lines) {
      if (line.startsWith('event:')) event = line.slice(6).trim()
      if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
    }

    if (dataLines.length === 0) return
    const rawData = dataLines.join('\n')
    let payload: any = null
    try {
      payload = rawData ? JSON.parse(rawData) : null
    } catch {
      return
    }

    if (event === 'token') callbacks.onToken?.(payload.content ?? '')
    if (event === 'sources') callbacks.onSources?.(Array.isArray(payload) ? payload : (payload?.sources ?? []))
    if (event === 'meta') callbacks.onMeta?.(payload)
    if (event === 'error') {
      callbacks.onError?.(payload.message || '流式问答失败')
      throw new Error(payload.message || '流式问答失败')
    }
    if (event === 'done') {
      finalMessage = payload
      callbacks.onDone?.(payload)
    }
  }

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const events = buffer.split(/\r?\n\r?\n/)
      buffer = events.pop() ?? ''
      for (const event of events) {
        if (event.trim()) handleEvent(event)
      }
    }

    if (buffer.trim()) handleEvent(buffer)
  } catch (error) {
    await reader.cancel().catch(() => undefined)
    throw error
  } finally {
    reader.releaseLock()
  }

  if (!finalMessage) throw new Error('流式问答未返回完成消息')
  return finalMessage
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
