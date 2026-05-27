export type DocumentStatus = 'UPLOADED' | 'PARSING' | 'EMBEDDING' | 'DONE' | 'FAILED'

export interface DocumentVO {
  id: number
  kbId: number
  fileName: string
  fileType: string
  fileSize: number
  status: DocumentStatus
  chunkCount: number
  parseTaskId?: number
  errorMessage?: string
  createdAt: string
  updatedAt: string
}
