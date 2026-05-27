<template>
  <div class="chat-layout">
    <!-- Left: KB + Sessions -->
    <div class="chat-sidebar">
      <div class="sidebar-section">
        <div class="section-label">知识库</div>
        <el-select v-model="selectedKbId" placeholder="选择知识库" size="small" style="width:100%" @change="onKbChange">
          <el-option v-for="kb in kbs" :key="kb.id" :label="kb.name" :value="kb.id" />
        </el-select>
      </div>
      <div class="sidebar-section sessions-section">
        <div class="sessions-header">
          <span class="section-label">会话列表</span>
          <button class="new-session-btn" :disabled="!selectedKbId" @click="createNewSession">
            <el-icon><Plus /></el-icon>
          </button>
        </div>
        <div v-if="!selectedKbId" class="no-kb-tip">请先选择知识库</div>
        <div v-else class="session-list">
          <div
            v-for="s in sessions"
            :key="s.id"
            class="session-item"
            :class="{ active: currentSession?.id === s.id }"
            @click="selectSession(s)"
          >
            <el-icon class="session-icon"><ChatDotRound /></el-icon>
            <div class="session-info">
              <div class="session-title">{{ s.title }}</div>
              <div class="session-time">{{ formatDate(s.updatedAt) }}</div>
            </div>
          </div>
          <div v-if="sessions.length === 0" class="no-sessions">暂无会话，点击 + 新建</div>
        </div>
      </div>
    </div>

    <!-- Center: Chat Window -->
    <div class="chat-main">
      <div v-if="!currentSession" class="chat-welcome">
        <div class="welcome-icon"><el-icon><ChatDotRound /></el-icon></div>
        <h3>智能问答</h3>
        <p>选择知识库并新建会话，开始提问</p>
      </div>
      <template v-else>
        <div class="chat-header">
          <div>
            <div class="chat-title">{{ currentSession.title }}</div>
            <div class="chat-subtitle">基于知识库 · {{ currentKbName }}</div>
          </div>
          <el-switch v-model="agentMode" size="small" active-text="Agent" inactive-text="RAG" />
        </div>
        <div class="messages-wrap" ref="messagesRef">
          <div v-if="messages.length === 0" class="messages-empty">
            <p>发送您的第一个问题</p>
          </div>
          <ChatMessage v-for="m in messages" :key="m.id" :msg="m" />
          <div v-if="answering" class="thinking-indicator">
            <div class="thinking-dot" /><div class="thinking-dot" /><div class="thinking-dot" />
          </div>
        </div>
        <div class="input-area">
          <el-input
            v-model="inputText"
            type="textarea"
            :rows="3"
            placeholder="输入问题，按 Ctrl+Enter 发送…"
            resize="none"
            @keydown.ctrl.enter="sendMessage"
          />
          <el-button
            type="primary" class="send-btn"
            :loading="answering" :disabled="!inputText.trim()"
            @click="sendMessage"
          >
            <el-icon><Promotion /></el-icon>
          </el-button>
        </div>
      </template>
    </div>

    <!-- Right: Source Panel -->
    <div class="source-column">
      <div class="source-stack">
        <SourcePanel :sources="sources" />
        <AgentTracePanel
          v-if="agentMode || agentTrace.length"
          :trace="agentTrace"
          :confidence="agentConfidence"
          :intent="agentIntent"
          :latency-ms="agentLatencyMs"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import ChatMessage from '@/components/ChatMessage.vue'
import SourcePanel from '@/components/SourcePanel.vue'
import AgentTracePanel from '@/components/AgentTracePanel.vue'
import { getKbList } from '@/api/kb'
import { createSession, listSessions, askQuestionStream, askAgentStream, getChatHistory } from '@/api/chat'
import type { KbVO } from '@/types/kb'
import type { AgentResponse, AgentTraceStep, ChatSessionVO, ChatMessageVO, RagSourceChunk } from '@/types/chat'

const route = useRoute()
const kbs = ref<KbVO[]>([])
const selectedKbId = ref<number | null>(null)
const sessions = ref<ChatSessionVO[]>([])
const currentSession = ref<ChatSessionVO | null>(null)
const messages = ref<ChatMessageVO[]>([])
const inputText = ref('')
const answering = ref(false)
const agentMode = ref(false)
const sources = ref<RagSourceChunk[]>([])
const agentTrace = ref<AgentTraceStep[]>([])
const agentConfidence = ref<number | null>(null)
const agentIntent = ref<AgentResponse['intent'] | string | null>(null)
const agentLatencyMs = ref<number | null>(null)
const messagesRef = ref<HTMLElement>()

const currentKbName = computed(() => kbs.value.find(k => k.id === selectedKbId.value)?.name ?? '')

onMounted(async () => {
  kbs.value = (await getKbList().catch((e: unknown) => { console.error('加载知识库列表失败', e); return { records: [], total: 0 } })).records
  const kbId = route.query.kbId ? Number(route.query.kbId) : null
  if (kbId && kbs.value.find(k => k.id === kbId)) {
    selectedKbId.value = kbId
    await loadSessions()
  }
})

async function onKbChange() {
  sessions.value = []
  currentSession.value = null
  messages.value = []
  resetEvidence()
  await loadSessions()
}

async function loadSessions() {
  if (!selectedKbId.value) return
  sessions.value = (await listSessions(selectedKbId.value).catch((e: unknown) => { console.error('加载会话列表失败', e); return { records: [], total: 0 } })).records
  if (sessions.value.length > 0) await selectSession(sessions.value[0])
}

async function selectSession(s: ChatSessionVO) {
  currentSession.value = s
  messages.value = (await getChatHistory(s.id).catch((e: unknown) => { console.error('加载聊天历史失败', e); return { records: [], total: 0 } })).records
  resetEvidence()
  scrollToBottom()
}

async function createNewSession() {
  if (!selectedKbId.value) return
  try {
    const s = await createSession(selectedKbId.value, `会话 ${new Date().toLocaleTimeString('zh-CN')}`)
    sessions.value.unshift(s)
    await selectSession(s)
  } catch (e: any) { ElMessage.error(e.message) }
}

async function sendMessage() {
  if (!inputText.value.trim() || !currentSession.value || !selectedKbId.value) return
  const question = inputText.value.trim()
  inputText.value = ''

  const userMsg: ChatMessageVO = {
    id: Date.now(),
    role: 'user',
    content: question,
    createdAt: new Date().toISOString(),
  }
  messages.value.push(userMsg)
  scrollToBottom()

  answering.value = true
  resetEvidence()
  const assistantMsg: ChatMessageVO = {
    id: -Date.now(),
    role: 'assistant',
    content: '',
    sources: [],
    agentMode: agentMode.value,
    createdAt: new Date().toISOString(),
  }
  messages.value.push(assistantMsg)
  scrollToBottom()

  try {
    const stream = agentMode.value ? askAgentStream : askQuestionStream
    const reply = await stream(
      {
        kbId: selectedKbId.value,
        sessionId: currentSession.value.id,
        question,
      },
      {
        onToken: (token) => {
          assistantMsg.content += token
          scrollToBottom()
        },
        onSources: (items) => {
          sources.value = items ?? []
          assistantMsg.sources = sources.value
        },
        onMeta: (meta) => {
          if (!agentMode.value) return
          agentIntent.value = meta.intent ?? agentIntent.value
          agentConfidence.value = typeof meta.confidence === 'number' ? meta.confidence : agentConfidence.value
          agentTrace.value = meta.trace ?? agentTrace.value
          agentLatencyMs.value = typeof meta.latencyMs === 'number' ? meta.latencyMs : agentLatencyMs.value
          assistantMsg.intent = agentIntent.value ?? undefined
          assistantMsg.confidence = agentConfidence.value ?? undefined
        },
        onDone: (message) => {
          const idx = messages.value.findIndex(m => m.id === assistantMsg.id)
          const normalized: ChatMessageVO = 'answer' in message
            ? {
                ...assistantMsg,
                content: message.answer,
                sources: message.sources,
                agentMode: true,
                intent: message.intent,
                confidence: message.confidence,
              }
            : message
          if (idx >= 0) messages.value[idx] = normalized
          sources.value = normalized.sources ?? sources.value
          if ('trace' in message) {
            agentTrace.value = message.trace ?? agentTrace.value
            agentConfidence.value = message.confidence
            agentIntent.value = message.intent
            agentLatencyMs.value = message.latencyMs ?? agentLatencyMs.value
          }
        },
      }
    )
    if (!assistantMsg.content) assistantMsg.content = 'answer' in reply ? reply.answer : reply.content
    scrollToBottom()
  } catch (e: any) {
    messages.value = messages.value.filter(m => m.id !== assistantMsg.id)
    ElMessage.error(e.message || '问答失败')
  } finally {
    answering.value = false
  }
}

async function scrollToBottom() {
  await nextTick()
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

function formatDate(d: string) {
  return d ? new Date(d).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) : ''
}

function resetEvidence() {
  sources.value = []
  agentTrace.value = []
  agentConfidence.value = null
  agentIntent.value = null
  agentLatencyMs.value = null
}
</script>

<style scoped>
.chat-layout { display: flex; height: calc(100vh - 0px); margin: -32px -36px; overflow: hidden; }

.chat-sidebar {
  width: 240px; flex-shrink: 0; background: var(--color-surface);
  border-right: 1px solid var(--color-border); display: flex; flex-direction: column; overflow: hidden;
}
.sidebar-section { padding: 16px 14px; border-bottom: 1px solid var(--color-border); }
.section-label { font-size: 11px; font-weight: 600; color: var(--color-text-muted); text-transform: uppercase; letter-spacing: .05em; margin-bottom: 8px; display: block; }
.sessions-section { flex: 1; display: flex; flex-direction: column; overflow: hidden; border-bottom: none; }
.sessions-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.new-session-btn {
  width: 22px; height: 22px; border-radius: 4px; border: 1px solid var(--color-border);
  background: none; cursor: pointer; color: var(--color-text-secondary);
  display: flex; align-items: center; justify-content: center; font-size: 12px;
}
.new-session-btn:hover:not(:disabled) { background: var(--color-accent-light); color: var(--color-accent); border-color: var(--color-accent); }
.new-session-btn:disabled { opacity: .4; cursor: not-allowed; }
.session-list { flex: 1; overflow-y: auto; display: flex; flex-direction: column; gap: 2px; }
.no-kb-tip, .no-sessions { font-size: 12px; color: var(--color-text-muted); padding: 20px 0; text-align: center; }
.session-item {
  display: flex; align-items: flex-start; gap: 9px; padding: 9px 10px;
  border-radius: var(--radius-sm); cursor: pointer; transition: background .15s;
}
.session-item:hover { background: var(--color-bg); }
.session-item.active { background: var(--color-accent-light); }
.session-icon { font-size: 14px; color: var(--color-text-muted); margin-top: 2px; flex-shrink: 0; }
.session-item.active .session-icon { color: var(--color-accent); }
.session-title { font-size: 13px; font-weight: 500; color: var(--color-text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.session-time { font-size: 11px; color: var(--color-text-muted); margin-top: 2px; }

.chat-main {
  flex: 1; display: flex; flex-direction: column; background: var(--color-bg); overflow: hidden;
}
.chat-welcome {
  flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 12px; color: var(--color-text-muted);
}
.welcome-icon { font-size: 52px; color: var(--color-border); }
.chat-welcome h3 { font-family: var(--font-heading); font-size: 20px; color: var(--color-text-secondary); }
.chat-welcome p { font-size: 14px; }
.chat-header { padding: 16px 24px 12px; border-bottom: 1px solid var(--color-border); background: var(--color-surface); display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.chat-title { font-family: var(--font-heading); font-size: 16px; font-weight: 600; }
.chat-subtitle { font-size: 12px; color: var(--color-text-muted); margin-top: 2px; }
.messages-wrap { flex: 1; overflow-y: auto; padding: 20px 24px; display: flex; flex-direction: column; gap: 16px; }
.messages-empty { text-align: center; color: var(--color-text-muted); font-size: 14px; padding: 40px 0; }
.thinking-indicator { display: flex; gap: 5px; padding: 4px 0; }
.thinking-dot {
  width: 7px; height: 7px; border-radius: 50%; background: var(--color-accent);
  animation: bounce 1.2s infinite ease-in-out;
}
.thinking-dot:nth-child(2) { animation-delay: .2s; }
.thinking-dot:nth-child(3) { animation-delay: .4s; }
@keyframes bounce { 0%, 80%, 100% { transform: scale(.6); opacity:.4; } 40% { transform: scale(1); opacity:1; } }
.input-area {
  padding: 16px 24px 20px; border-top: 1px solid var(--color-border);
  background: var(--color-surface); display: flex; gap: 10px; align-items: flex-end;
}
.input-area .el-textarea { flex: 1; }
.send-btn { height: 72px; width: 52px; font-size: 18px; }

.source-column {
  width: 260px; flex-shrink: 0; background: var(--color-surface);
  border-left: 1px solid var(--color-border); overflow: hidden;
}
.source-stack {
  height: 100%; display: grid; grid-template-rows: minmax(0, 1fr) minmax(220px, 42%);
}

@media (max-width: 1024px) {
  .source-column {
    display: none;
  }
}

@media (max-width: 768px) {
  .chat-layout {
    flex-direction: column;
    height: auto;
    min-height: calc(100svh - 110px);
    margin: -22px -16px -88px;
    overflow: visible;
  }

  .chat-sidebar {
    width: 100%;
    max-height: 250px;
    border-right: none;
    border-bottom: 1px solid var(--color-border);
    overflow: visible;
  }

  .sidebar-section {
    padding: 14px 16px;
  }

  .sessions-section {
    min-height: 0;
    max-height: 170px;
  }

  .session-list {
    max-height: 116px;
  }

  .session-item {
    padding: 8px 9px;
  }

  .chat-main {
    min-height: calc(100svh - 360px);
  }

  .chat-header {
    padding: 14px 16px 12px;
  }

  .messages-wrap {
    min-height: 220px;
    padding: 16px;
    gap: 12px;
  }

  .input-area {
    padding: 12px 16px calc(14px + env(safe-area-inset-bottom));
    gap: 8px;
  }

  .send-btn {
    width: 46px;
    height: 72px;
  }
}

@media (max-width: 480px) {
  .chat-sidebar {
    max-height: 232px;
  }

  .section-label {
    font-size: 10px;
  }

  .chat-welcome {
    min-height: 280px;
    padding: 20px;
    text-align: center;
  }
}
</style>
