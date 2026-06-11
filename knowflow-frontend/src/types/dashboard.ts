import type { DocumentVO } from './document'
import type { ChatSessionVO } from './chat'

export interface DashboardStatsVO {
  kbCount: number
  docCount: number
  doneDocCount: number
  failedDocCount: number
  chunkCount: number
  chatCount: number
  recentDocs: DocumentVO[]
  recentSessions: ChatSessionVO[]
  recentFailedTasks: RecentFailedTaskVO[]
}

export interface RecentFailedTaskVO {
  taskId: number
  documentId: number
  fileName: string
  errorMessage: string
  updatedAt: string
}
