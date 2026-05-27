import request from './request'
import type { KbVO, KbCreateRequest, KbUpdateRequest } from '@/types/kb'
import type { PageResult } from '@/types/common'

export function getKbList(page = 1, size = 100): Promise<PageResult<KbVO>> {
  return request.get('/api/kb/list', { params: { page, size } })
}

export function getKbDetail(id: number): Promise<KbVO> {
  return request.get(`/api/kb/${id}`)
}

export function createKb(data: KbCreateRequest): Promise<KbVO> {
  return request.post('/api/kb', data)
}

export function updateKb(id: number, data: KbUpdateRequest): Promise<KbVO> {
  return request.put(`/api/kb/${id}`, data)
}

export function deleteKb(id: number): Promise<string> {
  return request.delete(`/api/kb/${id}`)
}
