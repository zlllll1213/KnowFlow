import request from './request'
import type { AxiosProgressEvent } from 'axios'
import type { DocumentVO, DocumentStatus } from '@/types/document'
import type { PageResult } from '@/types/common'

export type UploadProgressHandler = (percent: number, event: AxiosProgressEvent) => void

export function uploadDocument(
  kbId: number,
  file: File,
  onProgress?: UploadProgressHandler
): Promise<DocumentVO> {
  const form = new FormData()
  form.append('kbId', String(kbId))
  form.append('file', file)
  return request.post('/api/document/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: (event: AxiosProgressEvent) => {
      if (!onProgress || !event.total) return
      onProgress(Math.round((event.loaded / event.total) * 100), event)
    },
  })
}

export function getDocumentList(kbId: number, page = 1, size = 100): Promise<PageResult<DocumentVO>> {
  return request.get('/api/document/list', { params: { kbId, page, size } })
}

export function getDocumentDetail(id: number): Promise<DocumentVO> {
  return request.get(`/api/document/${id}`)
}

export function deleteDocument(id: number): Promise<void> {
  return request.delete(`/api/document/${id}`)
}

export function getDocumentStatus(id: number): Promise<DocumentStatus> {
  return request.get(`/api/document/${id}/status`)
}
