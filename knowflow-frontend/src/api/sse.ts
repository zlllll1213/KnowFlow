import { getToken } from '@/utils/token'
import type { AgentResponse, ChatAskRequest, ChatMessageVO, RagSourceChunk } from '@/types/chat'

export interface AskStreamCallbacks {
  onToken?: (token: string) => void
  onSources?: (sources: RagSourceChunk[]) => void
  onDone?: (message: ChatMessageVO | AgentResponse) => void
  onMeta?: (meta: Partial<AgentResponse>) => void
  onError?: (message: string) => void
}

export interface ParsedSseEvent {
  event: string
  payload: any
}

export function parseSseEvent(raw: string): ParsedSseEvent | null {
  const lines = raw.split(/\r?\n/)
  let event = 'message'
  const dataLines: string[] = []

  for (const line of lines) {
    if (line.startsWith(':')) continue
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
      continue
    }
    if (line.startsWith('data:')) {
      const value = line.slice(5)
      dataLines.push(value.startsWith(' ') ? value.slice(1) : value)
    }
  }

  if (dataLines.length === 0) return null
  const rawData = dataLines.join('\n').trim()
  if (!rawData) return null

  try {
    return { event, payload: JSON.parse(rawData) }
  } catch {
    return null
  }
}

function dispatchSseEvent(parsed: ParsedSseEvent, callbacks: AskStreamCallbacks) {
  const { event, payload } = parsed
  if (event === 'token') callbacks.onToken?.(payload?.content ?? '')
  if (event === 'sources') callbacks.onSources?.(Array.isArray(payload) ? payload : (payload?.sources ?? []))
  if (event === 'meta') callbacks.onMeta?.(payload)
  if (event === 'error') {
    const message = payload?.message || '流式问答失败'
    try {
      callbacks.onError?.(message)
    } catch (callbackError) {
      console.error('流式错误回调执行失败', callbackError)
    }
    throw new Error(message)
  }
  if (event === 'done') {
    callbacks.onDone?.(payload)
    return payload
  }
  return null
}

export async function streamRequest<TDone>(path: string, data: ChatAskRequest, callbacks: AskStreamCallbacks = {}): Promise<TDone> {
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
    const parsed = parseSseEvent(raw)
    if (!parsed) return
    const donePayload = dispatchSseEvent(parsed, callbacks)
    if (donePayload) finalMessage = donePayload
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
