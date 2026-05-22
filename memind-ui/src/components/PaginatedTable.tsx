//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

import * as React from "react"

import {
  Empty,
  EmptyDescription,
  EmptyHeader,
  EmptyTitle,
} from "@/components/ui/empty"
import {
  Pagination,
  PaginationContent,
  PaginationEllipsis,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from "@/components/ui/pagination"
import {
  Table,
  TableBody,
  TableCell,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { cn } from "@/lib/utils"

type PaginatedTableEmptyState = {
  title: React.ReactNode
  description?: React.ReactNode
}

type PaginatedTablePagination = {
  summary: React.ReactNode
  currentPage?: number
  totalPages?: number
  hasPrevious?: boolean
  hasNext?: boolean
  onPageChange?: (page: number) => void
}

type PaginatedTableProps = React.ComponentProps<"div"> & {
  columns: React.ReactNode
  columnCount: number
  children?: React.ReactNode
  emptyState?: PaginatedTableEmptyState
  isEmpty?: boolean
  pagination?: PaginatedTablePagination
  tableClassName?: string
  tableViewportClassName?: string
}

function getPageItems(currentPage: number, totalPages: number) {
  if (totalPages <= 5) {
    return Array.from({ length: totalPages }, (_, index) => index + 1)
  }

  const pages = new Set([1, totalPages, currentPage])

  if (currentPage > 1) {
    pages.add(currentPage - 1)
  }

  if (currentPage < totalPages) {
    pages.add(currentPage + 1)
  }

  const sortedPages = Array.from(pages).sort((first, second) => first - second)
  const items: Array<number | "ellipsis"> = []

  sortedPages.forEach((page, index) => {
    const previousPage = sortedPages[index - 1]

    if (previousPage && page - previousPage > 1) {
      items.push("ellipsis")
    }

    items.push(page)
  })

  return items
}

function PaginatedTableEmptyRow({
  columnCount,
  emptyState,
}: {
  columnCount: number
  emptyState?: PaginatedTableEmptyState
}) {
  return (
    <TableRow className="hover:bg-transparent">
      <TableCell className="py-0" colSpan={columnCount}>
        <Empty className="min-h-44 rounded-none border-0">
          <EmptyHeader>
            <EmptyTitle>{emptyState?.title ?? "No results found"}</EmptyTitle>
            {emptyState?.description ? (
              <EmptyDescription>{emptyState.description}</EmptyDescription>
            ) : null}
          </EmptyHeader>
        </Empty>
      </TableCell>
    </TableRow>
  )
}

function PaginatedTableFooter({
  pagination,
}: {
  pagination: PaginatedTablePagination
}) {
  const currentPage = pagination.currentPage ?? 1
  const totalPages = pagination.totalPages ?? 1
  const hasPrevious = pagination.hasPrevious ?? currentPage > 1
  const hasNext = pagination.hasNext ?? currentPage < totalPages
  const canRenderPages = pagination.currentPage && pagination.totalPages
  const pageItems = canRenderPages ? getPageItems(currentPage, totalPages) : []

  function handlePageClick(
    event: React.MouseEvent<HTMLAnchorElement>,
    page: number,
    disabled = false
  ) {
    event.preventDefault()

    if (disabled || page === currentPage) {
      return
    }

    pagination.onPageChange?.(page)
  }

  return (
    <div className="flex flex-col gap-3 border-t border-border/70 bg-muted/25 px-2 py-1 text-xs text-muted-foreground md:flex-row md:items-center md:justify-between">
      <span>{pagination.summary}</span>
      <Pagination className="mx-0 w-auto justify-start md:justify-end">
        <PaginationContent>
          <PaginationItem>
            <PaginationPrevious
              aria-disabled={!hasPrevious}
              className={cn(!hasPrevious && "pointer-events-none opacity-50")}
              href="#"
              onClick={(event) =>
                handlePageClick(event, currentPage - 1, !hasPrevious)
              }
            />
          </PaginationItem>
          {pageItems.map((item, index) => (
            <PaginationItem key={`${item}-${index}`}>
              {item === "ellipsis" ? (
                <PaginationEllipsis />
              ) : (
                <PaginationLink
                  href="#"
                  isActive={item === currentPage}
                  onClick={(event) => handlePageClick(event, item)}
                >
                  {item}
                </PaginationLink>
              )}
            </PaginationItem>
          ))}
          <PaginationItem>
            <PaginationNext
              aria-disabled={!hasNext}
              className={cn(!hasNext && "pointer-events-none opacity-50")}
              href="#"
              onClick={(event) =>
                handlePageClick(event, currentPage + 1, !hasNext)
              }
            />
          </PaginationItem>
        </PaginationContent>
      </Pagination>
    </div>
  )
}

function PaginatedTable({
  children,
  className,
  columns,
  columnCount,
  emptyState,
  isEmpty,
  pagination,
  tableClassName,
  tableViewportClassName,
  ...props
}: PaginatedTableProps) {
  const hasRows = React.Children.count(children) > 0
  const shouldRenderEmpty = isEmpty ?? !hasRows

  return (
    <div
      className={cn("overflow-hidden rounded-md border bg-card/95", className)}
      {...props}
    >
      <div className={tableViewportClassName}>
        <Table className={tableClassName}>
          <TableHeader>{columns}</TableHeader>
          <TableBody>
            {shouldRenderEmpty ? (
              <PaginatedTableEmptyRow
                columnCount={columnCount}
                emptyState={emptyState}
              />
            ) : (
              children
            )}
          </TableBody>
        </Table>
      </div>
      {pagination ? <PaginatedTableFooter pagination={pagination} /> : null}
    </div>
  )
}

export { PaginatedTable }
export type { PaginatedTableEmptyState, PaginatedTablePagination }
