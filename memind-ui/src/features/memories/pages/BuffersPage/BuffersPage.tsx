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

import { Lightbulb, MessageSquare, RefreshCcw } from "lucide-react"
import { useMemo, useState } from "react"

import { PaginatedTable } from "@/components/PaginatedTable"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { TableCell, TableHead, TableRow } from "@/components/ui/table"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { PageHeader } from "@/features/shared/ui"

import type {
  MemoryBufferRecord,
  MemoryDashboardData,
} from "../../dashboard/memory-dashboard-data"
import type { RefreshAction } from "../../dashboard/refresh-action"

type BufferFilter = "conversation" | "insight"

function bufferLabel(record: MemoryBufferRecord) {
  return record.buffer === "conversation_buffer" ? "Conversation" : "Insight"
}

function bufferStatus(record: MemoryBufferRecord) {
  if (record.buffer === "conversation_buffer") {
    return record.extracted ? "Extracted" : "Pending"
  }

  return record.built ? "Built" : "Unbuilt"
}

function bufferStatusVariant(record: MemoryBufferRecord) {
  if (record.buffer === "conversation_buffer") {
    return record.extracted ? "secondary" : "outline"
  }

  return record.built ? "secondary" : "outline"
}

function bufferPrimaryValue(record: MemoryBufferRecord) {
  if (record.buffer === "conversation_buffer") {
    return record.sessionId
  }

  return record.insightTypeName
}

function bufferSecondaryValue(record: MemoryBufferRecord) {
  if (record.buffer === "conversation_buffer") {
    return record.content
  }

  return record.itemId
}

function bufferContextValue(record: MemoryBufferRecord) {
  if (record.buffer === "conversation_buffer") {
    return record.sourceClient
  }

  return record.groupName
}

function bufferScopeValue(record: MemoryBufferRecord) {
  return [record.userId, record.agentId].filter(Boolean).join(" / ") || "-"
}

function matchesBufferFilter(record: MemoryBufferRecord, filter: BufferFilter) {
  return filter === "conversation"
    ? record.buffer === "conversation_buffer"
    : record.buffer === "insight_buffer"
}

function paginationLabel({
  conversationCount,
  filter,
  insightCount,
  visibleCount,
}: {
  conversationCount: number
  filter: BufferFilter
  insightCount: number
  visibleCount: number
}) {
  return filter === "conversation"
    ? `Showing ${visibleCount} of ${conversationCount} conversation buffers`
    : `Showing ${visibleCount} of ${insightCount} insight buffers`
}

function BuffersFilter({
  filter,
  onFilterChange,
}: {
  filter: BufferFilter
  onFilterChange: (filter: BufferFilter) => void
}) {
  return (
    <Tabs
      className="w-full sm:w-auto"
      data-testid="memory-buffers-filter"
      onValueChange={(value) => onFilterChange(value as BufferFilter)}
      value={filter}
    >
      <TabsList
        aria-label="Filter memory buffers"
        className="w-full rounded-md border bg-muted p-1 sm:w-auto"
      >
        <TabsTrigger
          className="min-w-24 border-0 px-3 shadow-none"
          data-state={filter === "conversation" ? "active" : "inactive"}
          value="conversation"
        >
          Conversation
        </TabsTrigger>
        <TabsTrigger
          className="min-w-24 border-0 px-3 shadow-none"
          data-state={filter === "insight" ? "active" : "inactive"}
          value="insight"
        >
          Insight
        </TabsTrigger>
      </TabsList>
    </Tabs>
  )
}

function BuffersTable({
  records,
  summary,
}: {
  records: MemoryBufferRecord[]
  summary: string
}) {
  return (
    <PaginatedTable
      className="mb-6"
      columnCount={8}
      columns={
        <TableRow>
          <TableHead>Buffer</TableHead>
          <TableHead>Record</TableHead>
          <TableHead>Status</TableHead>
          <TableHead>Scope</TableHead>
          <TableHead>Context</TableHead>
          <TableHead>Memory</TableHead>
          <TableHead>Updated</TableHead>
          <TableHead>Created</TableHead>
        </TableRow>
      }
      emptyState={{
        description: "Buffered conversations and insights show here.",
        title: "No buffer records",
      }}
      pagination={{ summary }}
      tableClassName="min-w-[1080px]"
    >
      {records.map((record) => {
        const Icon =
          record.buffer === "conversation_buffer" ? MessageSquare : Lightbulb

        return (
          <TableRow key={`${record.buffer}-${record.id}`}>
            <TableCell>
              <Badge className="gap-1.5" variant="secondary">
                <Icon data-icon="inline-start" />
                {bufferLabel(record)}
              </Badge>
            </TableCell>
            <TableCell>
              <div className="flex max-w-md flex-col gap-1">
                <span className="font-mono text-[11px] font-medium">
                  {bufferPrimaryValue(record)}
                </span>
                <span className="line-clamp-2 text-muted-foreground">
                  {bufferSecondaryValue(record)}
                </span>
              </div>
            </TableCell>
            <TableCell>
              <Badge variant={bufferStatusVariant(record)}>
                {bufferStatus(record)}
              </Badge>
            </TableCell>
            <TableCell className="font-mono text-[11px] text-muted-foreground">
              {bufferScopeValue(record)}
            </TableCell>
            <TableCell>
              <span className="font-mono text-[11px]">
                {bufferContextValue(record)}
              </span>
            </TableCell>
            <TableCell className="font-mono text-[11px] text-muted-foreground">
              {record.memoryId}
            </TableCell>
            <TableCell className="font-mono text-[11px] text-muted-foreground">
              {record.updatedAt}
            </TableCell>
            <TableCell className="font-mono text-[11px] text-muted-foreground">
              {record.createdAt}
            </TableCell>
          </TableRow>
        )
      })}
    </PaginatedTable>
  )
}

export function BuffersPage({
  data,
  refreshAction,
}: {
  data: MemoryDashboardData
  refreshAction: RefreshAction
}) {
  const [filter, setFilter] = useState<BufferFilter>("conversation")
  const visibleRecords = useMemo(
    () =>
      data.buffers.records.filter((record) =>
        matchesBufferFilter(record, filter)
      ),
    [data.buffers.records, filter]
  )
  const tableSummary = paginationLabel({
    conversationCount: data.buffers.conversationCount,
    filter,
    insightCount: data.buffers.insightCount,
    visibleCount: visibleRecords.length,
  })

  return (
    <div data-testid="memory-buffers-page">
      <PageHeader
        action={
          <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-end">
            <BuffersFilter filter={filter} onFilterChange={setFilter} />
            <Button
              disabled={refreshAction.isRefreshing}
              onClick={refreshAction.onRefresh}
              type="button"
              variant="outline"
            >
              <RefreshCcw data-icon="inline-start" />
              Refresh
            </Button>
          </div>
        }
        description="Inspect pending conversation and insight buffers before they are committed into memory."
        title="Memory Buffers"
      />
      <BuffersTable records={visibleRecords} summary={tableSummary} />
    </div>
  )
}
