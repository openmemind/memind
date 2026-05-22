export type PageMeta = {
  hasNext: boolean
  hasPrevious: boolean
  page: number
  pageSize: number
  totalItems: number
  totalPages: number
}

export type PageResult<T> = {
  items: T[]
  page: PageMeta
}
