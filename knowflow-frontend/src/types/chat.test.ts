import { describe, expect, it } from 'vitest'
import { isAgentResponse } from './chat'
import type { AgentResponse, ChatMessageVO } from './chat'

describe('isAgentResponse', () => {
  it('accepts complete Agent responses', () => {
    const response: AgentResponse = {
      intent: 'qa',
      answer: '基于知识库的回答',
      sources: [],
      confidence: 0.8,
      trace: [{ step: 'router', detail: '识别为问答' }],
      latencyMs: 120,
    }

    expect(isAgentResponse(response)).toBe(true)
  })

  it('rejects regular chat messages and partial objects', () => {
    const message: ChatMessageVO = {
      id: 1,
      role: 'assistant',
      content: '普通回答',
      createdAt: '2026-06-01T00:00:00',
    }

    expect(isAgentResponse(message)).toBe(false)
    expect(isAgentResponse({ ...message, answer: 'partial' } as any)).toBe(false)
  })
})
