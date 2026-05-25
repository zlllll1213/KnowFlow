import request from './request'
import { getToken } from '@/utils/token'
import type { ChatSessionVO, ChatMessageVO, ChatAskRequest, RagSourceChunk } from '@/types/chat'

export function createSession(kbId: number, title?: string): Promise<ChatSessionVO> {
  return request.post('/api/chat/session', { kbId, title })
}

export function listSessions(kbId: number): Promise<ChatSessionVO[]> {
  return request.get('/api/chat/session/list', { params: { kbId } })
}

export function askQuestion(data: ChatAskRequest): Promise<ChatMessageVO> {
  return request.post('/api/chat/ask', data)
}

export interface AskStreamCallbacks {
  onToken?: (token: string) => void
  onSources?: (sources: RagSourceChunk[]) => void
  onDone?: (message: ChatMessageVO) => void
}

export async function askQuestionStream(data: ChatAskRequest, callbacks: AskStreamCallbacks = {}): Promise<ChatMessageVO> {
  const baseURL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081'
  const token = getToken()
  const response = await fetch(`${baseURL}/api/chat/ask/stream`, {
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
  let finalMessage: ChatMessageVO | null = null

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
    const payload = rawData ? JSON.parse(rawData) : null

    if (event === 'token') callbacks.onToken?.(payload.content ?? '')
    if (event === 'sources') callbacks.onSources?.(payload ?? [])
    if (event === 'error') throw new Error(payload.message || '流式问答失败')
    if (event === 'done') {
      finalMessage = payload
      callbacks.onDone?.(payload)
    }
  }

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const events = buffer.split(/\n\n/)
    buffer = events.pop() ?? ''
    for (const event of events) {
      if (event.trim()) handleEvent(event)
    }
  }

  if (buffer.trim()) handleEvent(buffer)
  if (!finalMessage) throw new Error('流式问答未返回完成消息')
  return finalMessage
}

export function getChatHistory(sessionId: number): Promise<ChatMessageVO[]> {
  return request.get('/api/chat/history', { params: { sessionId } })
}
