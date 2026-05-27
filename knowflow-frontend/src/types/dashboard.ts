import type { DocumentVO } from './document'

export interface DashboardStatsVO {
  knowledgeBaseCount: number
  documentCount: number
  parsedDocumentCount: number
  chunkCount: number
  sessionCount: number
  questionCount: number
  recentDocuments: DocumentVO[]
  recentFailedTasks: string[]
}
