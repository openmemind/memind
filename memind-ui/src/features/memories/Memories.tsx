import {
  AlertTriangle,
  ArrowUpDown,
  Bot,
  CheckCircle2,
  Clipboard,
  Clock3,
  Search,
} from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import {
  InputGroup,
  InputGroupAddon,
  InputGroupInput,
} from "@/components/ui/input-group"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import {
  FilterSelect,
  MetricCard,
  PageHeader,
  PagePagination,
  PageSurface,
  StatusBadge,
  TableSurface,
  type Tone,
} from "@/features/shared/ui"

import {
  type MemoryStatus,
  type MemorySummary,
  type MemoryWorkspace,
  useMemoriesData,
} from "./memories-data"

function summaryTone(tone: MemorySummary["tone"]): Tone {
  return tone
}

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
    <Card className="mb-5 bg-card/95">
      <CardContent className="py-3">
        <div className="flex flex-col gap-3 xl:flex-row xl:items-center">
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
            <FilterSelect
              aria-label="Memory status"
              className="min-w-32"
              defaultValue="all"
              items={[
                { value: "all", label: "Status: All" },
                { value: "active", label: "Active" },
                { value: "idle", label: "Idle" },
                { value: "warning", label: "Warning" },
              ]}
            />
            <FilterSelect
              aria-label="Memory alerts"
              className="min-w-32"
              defaultValue="all"
              items={[
                { value: "all", label: "Alerts: All" },
                { value: "critical", label: "Critical" },
                { value: "warning", label: "Warning" },
              ]}
            />
            <FilterSelect
              aria-label="Memory created range"
              className="min-w-40"
              defaultValue="30d"
              items={[
                { value: "30d", label: "Created: Last 30d" },
                { value: "7d", label: "Last 7d" },
                { value: "24h", label: "Last 24h" },
              ]}
            />
            <Button>
              <ArrowUpDown data-icon="inline-start" />
              Sort
            </Button>
          </div>
        </div>
      </CardContent>
    </Card>
  )
}

function AlertBadges({ row }: { row: MemoryWorkspace }) {
  const totalAlerts = row.alerts.critical + row.alerts.warning

  if (totalAlerts === 0) {
    return <Badge variant="secondary">0</Badge>
  }

  return (
    <div className="flex gap-1">
      {row.alerts.critical > 0 ? (
        <Badge variant="destructive">{row.alerts.critical}</Badge>
      ) : null}
      {row.alerts.warning > 0 ? (
        <Badge variant="outline">{row.alerts.warning}</Badge>
      ) : null}
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
      <TableCell>
        <div className="font-medium">{row.name}</div>
        <div className="mt-1 flex items-center gap-2">
          <code>{row.id}</code>
          <Clipboard className="size-3.5 text-muted-foreground" />
        </div>
      </TableCell>
      <TableCell>
        <div>{row.userId}</div>
        <div className="mt-1 flex items-center gap-1 text-muted-foreground">
          <Bot className="size-3.5" />
          {row.agentId}
        </div>
      </TableCell>
      <TableCell>
        <StatusBadge
          label={statusLabel(row.status)}
          tone={statusTone(row.status)}
        />
      </TableCell>
      <TableCell>
        <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-muted-foreground">
          <span>
            REQ: <b className="font-medium text-foreground">{row.requests}</b>
          </span>
          <span>
            ITEMS: <b className="font-medium text-foreground">{row.items}</b>
          </span>
          <span>
            INSIGHT:{" "}
            <b className="font-medium text-foreground">{row.insights}</b>
          </span>
        </div>
      </TableCell>
      <TableCell>
        <AlertBadges row={row} />
      </TableCell>
      <TableCell>
        <div className="flex items-center gap-2">
          {row.status === "error" ? (
            <AlertTriangle className="size-4 text-destructive" />
          ) : row.status === "active" ? (
            <CheckCircle2 className="size-4 text-primary" />
          ) : (
            <Clock3 className="size-4 text-muted-foreground" />
          )}
          <div>
            <div>{row.lastActivity}</div>
            <div className="text-muted-foreground">{row.createdAt}</div>
          </div>
        </div>
      </TableCell>
      <TableCell className="text-right">
        <Button onClick={() => onOpenMemory?.(row.id)} variant="outline">
          Open
        </Button>
      </TableCell>
    </TableRow>
  )
}

function MemoryTable({
  rows,
  onOpenMemory,
}: {
  rows: MemoryWorkspace[]
  onOpenMemory?: (memoryId: string) => void
}) {
  return (
    <TableSurface>
      <Table className="min-w-[940px]">
        <TableHeader>
          <TableRow>
            <TableHead>Memory</TableHead>
            <TableHead>User / Agent</TableHead>
            <TableHead>Status</TableHead>
            <TableHead>Stats</TableHead>
            <TableHead>Alerts</TableHead>
            <TableHead>Activity</TableHead>
            <TableHead className="text-right">Action</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((row) => (
            <MemoryRow key={row.id} onOpenMemory={onOpenMemory} row={row} />
          ))}
        </TableBody>
      </Table>
      <PagePagination label="Showing 1 to 25 of 2,840 results" />
    </TableSurface>
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

      <section className="mb-5 grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
        {data.summaries.map((summary) => (
          <MetricCard
            key={summary.label}
            detail={summary.detail}
            label={summary.label}
            tone={summaryTone(summary.tone)}
            value={summary.value}
          />
        ))}
      </section>

      <FilterToolbar />
      <MemoryTable onOpenMemory={onOpenMemory} rows={data.workspaces} />
    </PageSurface>
  )
}
