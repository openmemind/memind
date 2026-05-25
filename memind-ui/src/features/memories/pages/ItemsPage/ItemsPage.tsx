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

import {
  Database,
  ExternalLink,
  Maximize2,
  MessageSquare,
  Minimize2,
  RefreshCcw,
  X,
} from "lucide-react"
import type * as React from "react"
import { useEffect, useRef, useState } from "react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { PaginatedTable } from "@/components/PaginatedTable"
import {
  TableCell,
  TableHead,
  TableRow,
} from "@/components/ui/table"
import { PageHeader } from "@/features/shared/ui"
import { cn } from "@/lib/utils"

import { DetailRow } from "../../components/DetailRow"
import { JsonBlock } from "../../components/JsonBlock"
import { VectorStatus } from "../../components/VectorStatus"
import type {
  MemoryDashboardData,
  MemoryItemRecord,
} from "../../dashboard/memory-dashboard-data"
import type { RefreshAction } from "../../dashboard/refresh-action"

export function ItemsHeader({
  refreshAction,
}: {
  refreshAction: RefreshAction
}) {
  return (
    <PageHeader
      action={
        <div className="flex flex-wrap gap-2">
          <Button
            disabled={refreshAction.isRefreshing}
            onClick={refreshAction.onRefresh}
            type="button"
            variant="outline"
          >
            <RefreshCcw data-icon="inline-start" />
            Refresh
          </Button>
          <Button disabled type="button" variant="outline">
            Delete selected
          </Button>
        </div>
      }
      description="Inspect extracted memory facts, source raw data, metadata, vectors, and related threads."
      title="Memory Items"
    />
  )
}

function MemoryItemId({ item }: { item: MemoryItemRecord }) {
  return (
    <div className="flex flex-col gap-1">
      <code className="font-mono text-[11px] font-medium">{item.shortId}</code>
      <span className="line-clamp-2 max-w-[18rem] text-muted-foreground">
        {item.content}
      </span>
    </div>
  )
}

function ItemsTable({
  data,
  onPageChange,
  onViewItem,
}: {
  data: MemoryDashboardData
  onPageChange?: (page: number) => void
  onViewItem: (item: MemoryItemRecord) => void
}) {
  return (
    <PaginatedTable
      className="mb-6"
      columnCount={11}
      columns={
        <TableRow>
          <TableHead className="w-12">
            <Checkbox aria-label="Select all memory items" />
          </TableHead>
          <TableHead>Item</TableHead>
          <TableHead>Scope</TableHead>
          <TableHead>Category</TableHead>
          <TableHead>Type</TableHead>
          <TableHead>Source</TableHead>
          <TableHead>Vector</TableHead>
          <TableHead>Observed</TableHead>
          <TableHead>Occurred</TableHead>
          <TableHead className="text-center">Threads</TableHead>
          <TableHead className="text-right">Action</TableHead>
        </TableRow>
      }
      emptyState={{
        description: "Extracted memory items matching the filters show here.",
        title: "No memory items",
      }}
      pagination={{
        currentPage: data.items.page.page,
        hasNext: data.items.page.hasNext,
        hasPrevious: data.items.page.hasPrevious,
        onPageChange,
        summary: data.items.paginationLabel,
        totalPages: data.items.page.totalPages,
      }}
      tableClassName="min-w-[1220px]"
    >
      {data.items.records.map((item) => (
        <TableRow key={item.id}>
          <TableCell>
            <Checkbox aria-label={`Select ${item.id}`} />
          </TableCell>
          <TableCell>
            <MemoryItemId item={item} />
          </TableCell>
          <TableCell>
            <Badge variant="secondary">{item.scope}</Badge>
          </TableCell>
          <TableCell>
            <Badge variant="outline">{item.category}</Badge>
          </TableCell>
          <TableCell>
            <Badge variant="outline">{item.type}</Badge>
          </TableCell>
          <TableCell>
            <code className="font-mono text-[11px]">
              {item.sourceRawDataShortId}
            </code>
          </TableCell>
          <TableCell>
            <VectorStatus status={item.vectorStatus} />
          </TableCell>
          <TableCell className="font-mono text-[11px] text-muted-foreground">
            {item.observedAt}
          </TableCell>
          <TableCell className="font-mono text-[11px] text-muted-foreground">
            {item.occurredAt}
          </TableCell>
          <TableCell className="text-center">{item.threadCount}</TableCell>
          <TableCell className="text-right">
            <Button
              onClick={() => onViewItem(item)}
              size="sm"
              type="button"
              variant="outline"
            >
              View
            </Button>
          </TableCell>
        </TableRow>
      ))}
    </PaginatedTable>
  )
}

function DetailsSection({
  children,
  title,
}: {
  children: React.ReactNode
  title: string
}) {
  return (
    <section>
      <h3 className="mb-3 text-xs font-medium tracking-[0.08em] text-muted-foreground uppercase">
        {title}
      </h3>
      {children}
    </section>
  )
}

function MemoryItemDetailsPanel({
  isExpanded,
  isVisible,
  item,
  onCloseAnimationEnd,
  onClose,
  onToggleExpanded,
}: {
  isExpanded: boolean
  isVisible: boolean
  item: MemoryItemRecord
  onCloseAnimationEnd: () => void
  onClose: () => void
  onToggleExpanded: () => void
}) {
  const panelRef = useRef<HTMLElement | null>(null)

  useEffect(() => {
    panelRef.current?.focus()
  }, [item.id])

  function handleTransitionEnd(event: React.TransitionEvent<HTMLDivElement>) {
    if (event.target !== event.currentTarget || isVisible) {
      return
    }

    onCloseAnimationEnd()
  }

  return (
    <div className="pointer-events-none fixed inset-4 z-50">
      <aside
        ref={panelRef}
        aria-label="Itemdetails"
        className={cn(
          "pointer-events-auto ml-auto flex h-full w-full flex-col overflow-hidden rounded-lg border bg-popover text-xs/relaxed text-popover-foreground shadow-2xl transition-[max-width,transform,opacity] duration-300 ease-in-out focus-visible:outline-none",
          isExpanded ? "max-w-[calc(100vw-2rem)]" : "max-w-120",
          isVisible
            ? "translate-x-0 scale-100 opacity-100"
            : "translate-x-8 scale-[0.98] opacity-0"
        )}
        data-anchor="right"
        data-animation="slide-scale"
        data-expanded={isExpanded ? "true" : "false"}
        data-state={isVisible ? "open" : "closed"}
        data-testid="item-details-panel"
        tabIndex={-1}
        onTransitionEnd={handleTransitionEnd}
      >
        <div className="flex shrink-0 items-start justify-between gap-4 border-b bg-popover px-6 py-5">
          <div className="min-w-0">
            <h2 className="text-sm font-semibold text-foreground">
              Itemdetails
            </h2>
            <code className="mt-1 inline-flex rounded bg-muted px-2 py-0.5 font-mono text-[11px] text-muted-foreground">
              {item.id}
            </code>
            <div className="mt-3 flex flex-wrap gap-2">
              <Badge variant="outline">{item.category}</Badge>
              <Badge variant="secondary">{item.scope}</Badge>
              <Badge variant="outline">{item.type}</Badge>
            </div>
          </div>
          <div className="flex shrink-0 items-center gap-1">
            <Button
              aria-label={
                isExpanded ? "Shrink item details" : "Expand item details"
              }
              size="icon-sm"
              type="button"
              variant="ghost"
              onClick={onToggleExpanded}
            >
              {isExpanded ? <Minimize2 /> : <Maximize2 />}
            </Button>
            <Button
              aria-label="Close item details"
              size="icon-sm"
              type="button"
              variant="ghost"
              onClick={onClose}
            >
              <X />
            </Button>
          </div>
        </div>

        <div
          className={cn(
            "min-h-0 flex-1 overflow-y-auto",
            isExpanded ? "px-8 py-7 lg:px-10" : "px-6 py-5"
          )}
        >
          <div
            className={cn(
              "mx-auto grid gap-7",
              isExpanded ? "max-w-6xl lg:grid-cols-[minmax(0,1fr)_24rem]" : ""
            )}
          >
            <div className="flex min-w-0 flex-col gap-7">
              <DetailsSection title="Content">
                <div
                  className={cn(
                    "rounded-md border bg-muted/30 p-4 leading-relaxed",
                    isExpanded ? "text-base" : "text-sm"
                  )}
                >
                  {item.content}
                </div>
              </DetailsSection>

              <DetailsSection title="Temporal Data">
                <div className="flex flex-col gap-3">
                  <DetailRow
                    label="Observed"
                    value={<span>{item.observedAtFull}</span>}
                  />
                  <DetailRow
                    label="Occurred"
                    value={<span>{item.occurredAt}</span>}
                  />
                </div>
              </DetailsSection>

              <DetailsSection title="Vector & Deduplication">
                <div className="flex flex-col gap-3 rounded-md border bg-muted/30 p-4">
                  <div>
                    <p className="mb-1 text-[10px] font-medium tracking-[0.08em] text-muted-foreground uppercase">
                      vectorId
                    </p>
                    <p className="font-mono text-[11px] break-all">
                      {item.vectorId}
                    </p>
                  </div>
                  <div>
                    <p className="mb-1 text-[10px] font-medium tracking-[0.08em] text-muted-foreground uppercase">
                      contentHash
                    </p>
                    <p className="font-mono text-[11px]">{item.contentHash}</p>
                  </div>
                </div>
              </DetailsSection>
            </div>

            <div className="flex min-w-0 flex-col gap-7">
              <DetailsSection title="Source">
                <div className="flex flex-col gap-3 rounded-md border p-4">
                  <DetailRow
                    label="rawDataId"
                    value={<span>{item.sourceRawDataShortId}</span>}
                  />
                  <DetailRow
                    label="Type"
                    value={<span>{item.sourceType}</span>}
                  />
                  <DetailRow
                    label="Integration"
                    value={<span>{item.sourceIntegration}</span>}
                  />
                  <Button className="mt-1 w-full" type="button">
                    <Database data-icon="inline-start" />
                    View Raw Data
                  </Button>
                </div>
              </DetailsSection>

              <JsonBlock label="Metadata" value={item.metadataJson} />

              <DetailsSection
                title={`Related Threads (${item.relatedThreads.length})`}
              >
                <div className="flex flex-col gap-2">
                  {item.relatedThreads.map((thread) => (
                    <button
                      key={thread.id}
                      className="flex items-center justify-between gap-3 rounded-md border bg-card px-3 py-3 text-left transition-colors hover:bg-muted/50"
                      type="button"
                    >
                      <span className="flex min-w-0 items-center gap-3">
                        <MessageSquare className="shrink-0 text-muted-foreground" />
                        <span>
                          <span className="block font-mono text-[11px]">
                            {thread.id}
                          </span>
                          <span className="text-xs text-muted-foreground">
                            {thread.status} - {thread.updatedAt}
                          </span>
                        </span>
                      </span>
                      <ExternalLink className="shrink-0 text-muted-foreground" />
                    </button>
                  ))}
                </div>
              </DetailsSection>
            </div>
          </div>
        </div>
      </aside>
    </div>
  )
}

export function ItemsPage({
  data,
  onPageChange,
  refreshAction,
}: {
  data: MemoryDashboardData
  onPageChange?: (page: number) => void
  refreshAction: RefreshAction
}) {
  const [selectedItem, setSelectedItem] = useState<MemoryItemRecord | null>(
    null
  )
  const [isDetailsExpanded, setIsDetailsExpanded] = useState(false)
  const [isDetailsVisible, setIsDetailsVisible] = useState(false)

  useEffect(() => {
    if (!selectedItem) {
      return
    }

    const animationFrame = window.requestAnimationFrame(() => {
      setIsDetailsVisible(true)
    })

    return () => window.cancelAnimationFrame(animationFrame)
  }, [selectedItem])

  useEffect(() => {
    if (!selectedItem || isDetailsVisible) {
      return
    }

    const closeTimer = window.setTimeout(() => {
      setSelectedItem(null)
    }, 300)

    return () => window.clearTimeout(closeTimer)
  }, [isDetailsVisible, selectedItem])

  function handleViewItem(item: MemoryItemRecord) {
    setSelectedItem(item)
    setIsDetailsExpanded(false)
    setIsDetailsVisible(false)
  }

  function handleCloseDetails() {
    setIsDetailsVisible(false)
    setIsDetailsExpanded(false)
  }

  return (
    <>
      <ItemsHeader refreshAction={refreshAction} />
      <ItemsTable
        data={data}
        onPageChange={onPageChange}
        onViewItem={handleViewItem}
      />
      {selectedItem ? (
        <MemoryItemDetailsPanel
          isExpanded={isDetailsExpanded}
          isVisible={isDetailsVisible}
          item={selectedItem}
          onClose={handleCloseDetails}
          onCloseAnimationEnd={() => setSelectedItem(null)}
          onToggleExpanded={() => setIsDetailsExpanded((value) => !value)}
        />
      ) : null}
    </>
  )
}
