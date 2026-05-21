import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ChatSessionVO, ChatMessageVO } from '@/types/chat'

export const useChatStore = defineStore('chat', () => {
  const sessions = ref<ChatSessionVO[]>([])
  const currentSession = ref<ChatSessionVO | null>(null)
  const messages = ref<ChatMessageVO[]>([])

  function setSession(session: ChatSessionVO) {
    currentSession.value = session
  }

  function setSessions(list: ChatSessionVO[]) {
    sessions.value = list
  }

  function setMessages(list: ChatMessageVO[]) {
    messages.value = list
  }

  function appendMessage(msg: ChatMessageVO) {
    messages.value.push(msg)
  }

  function clearMessages() {
    messages.value = []
  }

  return { sessions, currentSession, messages, setSession, setSessions, setMessages, appendMessage, clearMessages }
})
