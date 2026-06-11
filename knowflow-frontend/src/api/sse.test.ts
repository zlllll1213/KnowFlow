import { beforeEach, describe, expect, it, vi } from 'vitest'
import { parseSseEvent, streamRequest } from './sse'
import type { AgentResponse, ChatAskRequest, RagSourceChunk } from '@/types/chat'

const encoder = new TextEncoder()

function mockFetchWithChunks(chunks: string[], status = 200) {
  const stream = new ReadableStream<Uint8Array>({
    start(controller) {
      for (const chunk of chunks) {
        controller.enqueue(encoder.encode(chunk))
      }
      controller.close()
    },
  })
  const fetchMock = vi.fn(async () => new Response(stream, { status }))
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function event(name: string, payload: unknown, newline = '\n') {
  return `event: ${name}${newline}data: ${JSON.stringify(payload)}${newline}${newline}`
}

describe('parseSseEvent', () => {
  it('parses event and data while ignoring comments', () => {
    const parsed = parseSseEvent(': keep-alive\nevent: token\ndata: {"content":"hello"}')

    expect(parsed).toEqual({
      event: 'token',
      payload: { content: 'hello' },
    })
  })

  it('returns null for events without valid JSON data', () => {
    expect(parseSseEvent('event: token\n')).toBeNull()
    expect(parseSseEvent('event: token\ndata: {bad json}')).toBeNull()
  })
})

describe('streamRequest', () => {
  const request: ChatAskRequest = {
    kbId: 1,
    sessionId: 2,
    question: 'How does Agent mode work?',
  }

  beforeEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
    vi.stubGlobal('document', { cookie: 'XSRF-TOKEN=csrf-token' })
  })

  it('streams token, meta, sources and done events across chunk boundaries', async () => {
    const source: RagSourceChunk = {
      chunkId: 10,
      documentId: 20,
      fileName: 'agent.md',
      chunkIndex: 0,
      content: 'Agent trace content',
      score: 0.91,
    }
    const done: AgentResponse = {
      intent: 'qa',
      answer: 'Hello world',
      sources: [source],
      confidence: 0.82,
      trace: [{ step: 'router', detail: 'qa' }],
      latencyMs: 123,
    }
    const fetchMock = mockFetchWithChunks([
      event('meta', { intent: 'qa', confidence: 0.82, trace: done.trace }).slice(0, 24),
      event('meta', { intent: 'qa', confidence: 0.82, trace: done.trace }).slice(24),
      event('token', { content: 'Hello ' }),
      event('token', { content: 'world' }, '\r\n'),
      event('sources', { sources: [source] }),
      event('done', done),
    ])
    const callbacks = {
      onToken: vi.fn(),
      onMeta: vi.fn(),
      onSources: vi.fn(),
      onDone: vi.fn(),
    }

    const result = await streamRequest<AgentResponse>('/api/agent/ask/stream', request, callbacks)

    expect(result).toEqual(done)
    expect(callbacks.onMeta).toHaveBeenCalledWith({ intent: 'qa', confidence: 0.82, trace: done.trace })
    expect(callbacks.onToken).toHaveBeenNthCalledWith(1, 'Hello ')
    expect(callbacks.onToken).toHaveBeenNthCalledWith(2, 'world')
    expect(callbacks.onSources).toHaveBeenCalledWith([source])
    expect(callbacks.onDone).toHaveBeenCalledWith(done)
    expect(fetchMock).toHaveBeenCalledWith('http://localhost:8081/api/agent/ask/stream', expect.objectContaining({
      method: 'POST',
      headers: expect.objectContaining({
        'Content-Type': 'application/json',
        'X-XSRF-TOKEN': 'csrf-token',
      }),
      body: JSON.stringify(request),
      credentials: 'include',
    }))
  })

  it('accepts sources payloads sent as a bare array', async () => {
    const source: RagSourceChunk = {
      chunkId: 11,
      documentId: 21,
      fileName: 'rag.md',
      chunkIndex: 1,
      content: 'RAG source content',
      score: 0.74,
    }
    const callbacks = { onSources: vi.fn() }
    mockFetchWithChunks([
      event('sources', [source]),
      event('done', { id: 1, role: 'assistant', content: 'done', sources: [source], createdAt: '2026-06-01T00:00:00' }),
    ])

    await streamRequest('/api/chat/ask/stream', request, callbacks)

    expect(callbacks.onSources).toHaveBeenCalledWith([source])
  })

  it('passes the abort signal to fetch', async () => {
    const controller = new AbortController()
    const fetchMock = mockFetchWithChunks([
      event('done', { id: 1, role: 'assistant', content: 'done', createdAt: '2026-06-01T00:00:00' }),
    ])

    await streamRequest('/api/chat/ask/stream', request, {}, { signal: controller.signal })

    expect(fetchMock).toHaveBeenCalledWith('http://localhost:8081/api/chat/ask/stream', expect.objectContaining({
      signal: controller.signal,
    }))
  })

  it('rejects and invokes onError when the stream emits an error event', async () => {
    const callbacks = { onError: vi.fn() }
    mockFetchWithChunks([
      event('token', { content: 'partial' }),
      event('error', { message: 'Go RAG Service unavailable, fallback disabled.' }),
    ])

    await expect(streamRequest('/api/chat/ask/stream', request, callbacks)).rejects.toThrow(
      'Go RAG Service unavailable, fallback disabled.',
    )
    expect(callbacks.onError).toHaveBeenCalledWith('Go RAG Service unavailable, fallback disabled.')
  })

  it('preserves the stream error when the onError callback throws', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined)
    const callbacks = {
      onError: vi.fn(() => {
        throw new Error('toast failed')
      }),
    }
    mockFetchWithChunks([
      event('error', { message: 'Go RAG Service unavailable, fallback disabled.' }),
    ])

    await expect(streamRequest('/api/chat/ask/stream', request, callbacks)).rejects.toThrow(
      'Go RAG Service unavailable, fallback disabled.',
    )
    expect(console.error).toHaveBeenCalledWith('流式错误回调执行失败', expect.any(Error))
  })

  it('rejects if the stream closes without a done event', async () => {
    mockFetchWithChunks([event('token', { content: 'partial' })])

    await expect(streamRequest('/api/chat/ask/stream', request)).rejects.toThrow('流式问答未返回完成消息')
  })
})
