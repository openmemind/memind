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
  Copy,
  ExternalLink,
  FileText,
  Maximize2,
  Minimize2,
  RefreshCcw,
  X,
} from "lucide-react"
import type * as React from "react"
import { type MouseEvent, useEffect, useRef, useState } from "react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { PaginatedTable } from "@/components/PaginatedTable"
import { TableCell, TableHead, TableRow } from "@/components/ui/table"
import { PageHeader } from "@/features/shared/ui"
import { cn } from "@/lib/utils"

import { JsonBlock } from "../../components/JsonBlock"
import { VectorStatus } from "../../components/VectorStatus"
import type {
  MemoryDashboardData,
  MemoryRawDataRecord,
} from "../../dashboard/memory-dashboard-data"
import type { RefreshAction } from "../../dashboard/refresh-action"

export function RawDataHeader({
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
      description="Inspect source records, captions, segments, metadata, and cleanup impact for this Memory."
      title="Raw Data"
    />
  )
}
function RawDataId({ record }: { record: MemoryRawDataRecord }) {
  function handleCopy(event: MouseEvent<HTMLButtonElement>) {
    event.stopPropagation()

    if (navigator.clipboard) {
      void navigator.clipboard.writeText(record.id)
    }
  }

  return (
    <div className="flex items-center gap-1 font-mono text-[11px]">
      <span>{record.shortId}</span>
      <Button
        aria-label={`Copy ${record.id}`}
        onClick={handleCopy}
        size="icon-xs"
        type="button"
        variant="ghost"
      >
        <Copy />
      </Button>
    </div>
  )
}

function RawDataTable({
  data,
  onViewRecord,
}: {
  data: MemoryDashboardData
  onViewRecord: (record: MemoryRawDataRecord) => void
}) {
  return (
    <PaginatedTable
      className="mb-6"
      columnCount={8}
      columns={
        <TableRow>
          <TableHead className="w-12">
            <Checkbox aria-label="Select all raw data records" />
          </TableHead>
          <TableHead>Raw Data ID</TableHead>
          <TableHead>Type</TableHead>
          <TableHead>Caption</TableHead>
          <TableHead>Source</TableHead>
          <TableHead>Vector</TableHead>
          <TableHead>Created</TableHead>
          <TableHead className="text-right">Action</TableHead>
        </TableRow>
      }
      emptyState={{
        description: "Source records matching the current filters show here.",
        title: "No raw data records",
      }}
      pagination={{ summary: data.rawData.paginationLabel }}
      tableClassName="min-w-[1080px]"
    >
      {data.rawData.records.map((record) => (
        <TableRow key={record.id}>
          <TableCell>
            <Checkbox
              aria-label={`Select ${record.id}`}
              onClick={(event) => event.stopPropagation()}
            />
          </TableCell>
          <TableCell>
            <RawDataId record={record} />
          </TableCell>
          <TableCell>
            <Badge variant="secondary">{record.typeLabel}</Badge>
          </TableCell>
          <TableCell className="max-w-md truncate font-medium">
            {record.caption}
          </TableCell>
          <TableCell className="font-mono text-[11px] text-muted-foreground">
            {record.source}
          </TableCell>
          <TableCell>
            <VectorStatus status={record.vectorStatus} />
          </TableCell>
          <TableCell className="font-mono text-[11px] text-muted-foreground">
            {record.createdAt}
          </TableCell>
          <TableCell className="text-right">
            <Button
              onClick={() => onViewRecord(record)}
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

function RecordDetailsPanel({
  isExpanded,
  isVisible,
  onClose,
  onCloseAnimationEnd,
  onToggleExpanded,
  record,
}: {
  isExpanded: boolean
  isVisible: boolean
  onClose: () => void
  onCloseAnimationEnd: () => void
  onToggleExpanded: () => void
  record: MemoryRawDataRecord
}) {
  const panelRef = useRef<HTMLElement | null>(null)

  useEffect(() => {
    panelRef.current?.focus()
  }, [record.id])

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
        aria-label="Record details"
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
        data-testid="record-details-panel"
        tabIndex={-1}
        onTransitionEnd={handleTransitionEnd}
      >
        <div className="flex shrink-0 items-start justify-between gap-4 border-b bg-popover px-6 py-5">
          <div className="min-w-0">
            <h2 className="text-sm font-semibold text-foreground">
              Record Details
            </h2>
            <code className="mt-1 inline-flex rounded bg-muted px-2 py-0.5 font-mono text-[11px] text-muted-foreground">
              {record.id}
            </code>
            <div className="mt-3 flex flex-wrap items-center gap-2">
              <Badge variant="secondary">{record.type}</Badge>
              <span className="font-mono text-[11px] text-muted-foreground">
                Created {record.createdAt}
              </span>
            </div>
          </div>
          <div className="flex shrink-0 items-center gap-1">
            <Button
              aria-label={
                isExpanded ? "Shrink record details" : "Expand record details"
              }
              size="icon-sm"
              type="button"
              variant="ghost"
              onClick={onToggleExpanded}
            >
              {isExpanded ? <Minimize2 /> : <Maximize2 />}
            </Button>
            <Button
              aria-label="Close record details"
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
              <DetailsSection title="Raw Caption">
                <div
                  className={cn(
                    "rounded-md border bg-muted/30 p-4 leading-relaxed",
                    isExpanded ? "text-base" : "text-sm"
                  )}
                >
                  {record.caption}
                </div>
              </DetailsSection>

              <JsonBlock label="Segment Data" value={record.segmentJson} />
            </div>

            <div className="flex min-w-0 flex-col gap-7">
              <JsonBlock label="Metadata Objects" value={record.metadataJson} />

              <DetailsSection title="Associated Memory Items">
                <div className="flex flex-col gap-2">
                  {record.associatedItems.map((item) => (
                    <button
                      key={item.label}
                      className="flex items-center justify-between gap-3 rounded-md border bg-card px-3 py-3 text-left transition-colors hover:bg-muted/50"
                      type="button"
                    >
                      <span className="flex min-w-0 items-center gap-3">
                        <FileText className="shrink-0 text-muted-foreground" />
                        <span className="truncate font-medium">
                          {item.label}
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

export function RawDataPage({
  data,
  refreshAction,
}: {
  data: MemoryDashboardData
  refreshAction: RefreshAction
}) {
  const [selectedRecord, setSelectedRecord] =
    useState<MemoryRawDataRecord | null>(null)
  const [isDetailsExpanded, setIsDetailsExpanded] = useState(false)
  const [isDetailsVisible, setIsDetailsVisible] = useState(false)

  useEffect(() => {
    if (!selectedRecord) {
      return
    }

    const animationFrame = window.requestAnimationFrame(() => {
      setIsDetailsVisible(true)
    })

    return () => window.cancelAnimationFrame(animationFrame)
  }, [selectedRecord])

  useEffect(() => {
    if (!selectedRecord || isDetailsVisible) {
      return
    }

    const closeTimer = window.setTimeout(() => {
      setSelectedRecord(null)
    }, 300)

    return () => window.clearTimeout(closeTimer)
  }, [isDetailsVisible, selectedRecord])

  function handleViewRecord(record: MemoryRawDataRecord) {
    setSelectedRecord(record)
    setIsDetailsExpanded(false)
    setIsDetailsVisible(false)
  }

  function handleCloseDetails() {
    setIsDetailsVisible(false)
    setIsDetailsExpanded(false)
  }

  return (
    <>
      <RawDataHeader refreshAction={refreshAction} />
      {/*<RawDataSummary data={data} />*/}
      {/*<RawDataToolbar />*/}
      <RawDataTable data={data} onViewRecord={handleViewRecord} />
      {selectedRecord ? (
        <RecordDetailsPanel
          isExpanded={isDetailsExpanded}
          isVisible={isDetailsVisible}
          onClose={handleCloseDetails}
          onCloseAnimationEnd={() => setSelectedRecord(null)}
          onToggleExpanded={() => setIsDetailsExpanded((value) => !value)}
          record={selectedRecord}
        />
      ) : null}
    </>
  )
}
