import request from './request'
import type { DocumentVO, DocumentStatus } from '@/types/document'

export function uploadDocument(kbId: number, file: File): Promise<DocumentVO> {
  const form = new FormData()
  form.append('kbId', String(kbId))
  form.append('file', file)
  return request.post('/api/document/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function getDocumentList(kbId: number): Promise<DocumentVO[]> {
  return request.get('/api/document/list', { params: { kbId } })
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
