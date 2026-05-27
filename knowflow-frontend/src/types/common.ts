export interface PageResult<T> {
  total: number
  page: number
  size: number
  records: T[]
}
