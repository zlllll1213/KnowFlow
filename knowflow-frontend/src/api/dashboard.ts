import request from './request'
import type { DashboardStatsVO } from '@/types/dashboard'

export function getDashboardStats(): Promise<DashboardStatsVO> {
  return request.get('/api/dashboard/stats')
}
