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
  AlertTriangle,
  ArrowUpDown,
  Bot,
  CheckCircle2,
  Clock3,
  Search,
} from "lucide-react"

import { Button } from "@/components/ui/button"
import { PaginatedTable } from "@/components/PaginatedTable"
import {
  InputGroup,
  InputGroupAddon,
  InputGroupInput,
} from "@/components/ui/input-group"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { TableCell, TableHead, TableRow } from "@/components/ui/table"
import {
  PageHeader,
  PageSurface,
  StatusBadge,
  type Tone,
} from "@/features/shared/ui"

import {
  type MemoryStatus,
  type MemoryWorkspace,
  useMemoriesData,
} from "./memories-data"

function statusTone(status: MemoryStatus): Tone {
  switch (status) {
    case "active":
      return "success"
    case "idle":
      return "default"
    case "warning":
      return "warning"
    case "error":
      return "danger"
  }
}

function statusLabel(status: MemoryStatus) {
  switch (status) {
    case "active":
      return "Active"
    case "idle":
      return "Idle"
    case "warning":
      return "Warning"
    case "error":
      return "Error"
  }
}

function FilterToolbar() {
  return (
    <div className="mb-5 flex flex-col gap-3 xl:flex-row xl:items-center">
      <InputGroup className="h-9 min-w-0 flex-1">
        <InputGroupAddon>
          <Search />
        </InputGroupAddon>
        <InputGroupInput
          placeholder="Search MemoryId / UserId / AgentId..."
          type="search"
        />
      </InputGroup>

      <div className="flex flex-wrap items-center gap-2">
        <Select defaultValue="all">
          <SelectTrigger aria-label="Memory status" className="min-w-32">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectGroup>
              <SelectItem value="all">Status: All</SelectItem>
              <SelectItem value="active">Active</SelectItem>
              <SelectItem value="idle">Idle</SelectItem>
              <SelectItem value="warning">Warning</SelectItem>
            </SelectGroup>
          </SelectContent>
        </Select>
        <Select defaultValue="all">
          <SelectTrigger aria-label="Memory alerts" className="min-w-32">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectGroup>
              <SelectItem value="all">Alerts: All</SelectItem>
              <SelectItem value="critical">Critical</SelectItem>
              <SelectItem value="warning">Warning</SelectItem>
            </SelectGroup>
          </SelectContent>
        </Select>
        <Select defaultValue="30d">
          <SelectTrigger aria-label="Memory created range" className="min-w-40">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectGroup>
              <SelectItem value="30d">Created: Last 30d</SelectItem>
              <SelectItem value="7d">Last 7d</SelectItem>
              <SelectItem value="24h">Last 24h</SelectItem>
            </SelectGroup>
          </SelectContent>
        </Select>
        <Button>
          <ArrowUpDown data-icon="inline-start" />
          Sort
        </Button>
      </div>
    </div>
  )
}

function MemoryRow({
  row,
  onOpenMemory,
}: {
  row: MemoryWorkspace
  onOpenMemory?: (memoryId: string) => void
}) {
  return (
    <TableRow>
      <TableCell className="font-mono text-xs font-medium">
        <code>{row.id}</code>
      </TableCell>
      <TableCell>
        <div className="flex min-w-0 items-center gap-2">
          <span className="truncate">{row.userId}</span>
          <span className="text-muted-foreground">/</span>
          <Bot className="text-muted-foreground" />
          <span className="truncate text-muted-foreground">{row.agentId}</span>
        </div>
      </TableCell>
      <TableCell>
        <StatusBadge
          label={statusLabel(row.status)}
          tone={statusTone(row.status)}
        />
      </TableCell>
      <TableCell className="font-medium">{row.items}</TableCell>
      <TableCell className="font-medium">{row.insights}</TableCell>
      <TableCell className="font-medium">{row.rawData}</TableCell>
      <TableCell>
        <div className="flex items-center gap-2 text-sm">
          {row.status === "error" ? (
            <AlertTriangle className="text-destructive" />
          ) : row.status === "active" ? (
            <CheckCircle2 className="text-primary" />
          ) : (
            <Clock3 className="text-muted-foreground" />
          )}
          <span>{row.lastActivity}</span>
        </div>
      </TableCell>
      <TableCell className="text-right">
        <Button
          onClick={() => onOpenMemory?.(row.id)}
          size="sm"
          variant="outline"
        >
          Open
        </Button>
      </TableCell>
    </TableRow>
  )
}

function MemoryTable({
  pagination,
  rows,
  onOpenMemory,
}: {
  pagination: {
    currentPage: number
    hasNext: boolean
    hasPrevious: boolean
    summary: string
    totalPages: number
  }
  rows: MemoryWorkspace[]
  onOpenMemory?: (memoryId: string) => void
}) {
  return (
    <PaginatedTable
      columnCount={8}
      columns={
        <TableRow>
          <TableHead>ID</TableHead>
          <TableHead>User / Agent</TableHead>
          <TableHead>Status</TableHead>
          <TableHead>Items</TableHead>
          <TableHead>Insight</TableHead>
          <TableHead>Raw Data</TableHead>
          <TableHead>Activity</TableHead>
          <TableHead className="text-right">Action</TableHead>
        </TableRow>
      }
      emptyState={{
        description:
          "Memory workspaces matching the current filters show here.",
        title: "No memories found",
      }}
      pagination={pagination}
      tableClassName="min-w-220"
    >
      {rows.map((row) => (
        <MemoryRow key={row.id} onOpenMemory={onOpenMemory} row={row} />
      ))}
    </PaginatedTable>
  )
}

export function Memories({
  onOpenMemory,
}: {
  onOpenMemory?: (memoryId: string) => void
}) {
  const memoriesQuery = useMemoriesData()

  if (memoriesQuery.isLoading) {
    return (
      <div className="flex min-h-svh items-center justify-center text-sm text-muted-foreground">
        Loading memories...
      </div>
    )
  }

  if (memoriesQuery.isError || !memoriesQuery.data) {
    return (
      <div className="flex min-h-svh items-center justify-center text-sm text-destructive">
        Failed to load memories.
      </div>
    )
  }

  const data = memoriesQuery.data

  return (
    <PageSurface>
      <PageHeader
        description="Browse, filter, and open memory workspaces."
        title="Memories"
      />

      <FilterToolbar />
      <MemoryTable
        onOpenMemory={onOpenMemory}
        pagination={data.pagination}
        rows={data.workspaces}
      />
    </PageSurface>
  )
}
