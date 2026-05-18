import {
  AlertTriangle,
  ArrowLeft,
  ArrowUpDown,
  Bell,
  Bolt,
  Bot,
  CalendarDays,
  Check,
  ChevronRight,
  CircleEllipsis,
  Clipboard,
  Copy,
  Database,
  ExternalLink,
  FileText,
  GitFork,
  LayoutDashboard,
  Lightbulb,
  List,
  MessageSquare,
  RefreshCcw,
  Search,
  Settings,
} from "lucide-react"
import hljs from "highlight.js/lib/core"
import jsonLanguage from "highlight.js/lib/languages/json"
import { type MouseEvent, useState } from "react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Checkbox } from "@/components/ui/checkbox"
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import {
  InputGroup,
  InputGroupAddon,
  InputGroupInput,
} from "@/components/ui/input-group"
import {
  Progress,
  ProgressLabel,
  ProgressValue,
} from "@/components/ui/progress"
import { Separator } from "@/components/ui/separator"
import { Dialog as SheetPrimitive } from "@base-ui/react/dialog"
import {
  Sheet,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet"
import {
  SidebarContent,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from "@/components/ui/sidebar"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { SidebarShell } from "@/features/shell/SidebarShell"
import { FilterSelect, PagePagination } from "@/features/shared/ui"
import { cn } from "@/lib/utils"

import {
  type MemoryActivityItem,
  type MemoryDashboardData,
  type MemoryPipelineStage,
  type MemoryRawDataRecord,
  useMemoryDashboard,
} from "./memory-dashboard-data"

hljs.registerLanguage("json", jsonLanguage)

type MemoryWorkspacePage = "overview" | "raw-data"

type MemoryDashboardProps = {
  memoryId: string
  onBack: () => void
  activePage?: MemoryWorkspacePage
  onPageChange?: (page: MemoryWorkspacePage) => void
}

const navItems = [
  { id: "overview", label: "Overview", icon: LayoutDashboard, enabled: true },
  { id: "raw-data", label: "Raw Data", icon: Database, enabled: true },
  { id: "items", label: "Items", icon: List, enabled: false },
  { id: "graph", label: "Graph", icon: GitFork, enabled: false },
  { id: "threads", label: "Threads", icon: MessageSquare, enabled: false },
  { id: "insights", label: "Insights", icon: Lightbulb, enabled: false },
  { id: "retrieve", label: "Retrieve", icon: Search, enabled: false },
  { id: "alerts", label: "Alerts", icon: Bell, enabled: false },
  { id: "settings", label: "Settings", icon: Settings, enabled: false },
] satisfies Array<{
  id: string
  label: string
  icon: typeof LayoutDashboard
  enabled: boolean
}>

const rawDataTypeOptions = [
  { value: "all", label: "All types" },
  { value: "conversation", label: "Conversation" },
  { value: "document", label: "Document" },
  { value: "image", label: "Image" },
  { value: "audio", label: "Audio" },
]

const rawDataSourceOptions = [
  { value: "all", label: "Source" },
  { value: "slack", label: "Slack" },
  { value: "discord", label: "Discord" },
  { value: "email", label: "Email" },
]

function IdentityValue({ value }: { value: string }) {
  function handleCopy() {
    if (navigator.clipboard) {
      void navigator.clipboard.writeText(value)
    }
  }

  return (
    <div className="group/identity-value flex min-w-0 items-center gap-1">
      <span className="min-w-0 flex-1 truncate font-mono text-[11px] text-muted-foreground">
        {value}
      </span>
      <Button
        aria-label={`Copy ${value}`}
        className="opacity-0 transition-opacity group-hover/identity-value:opacity-100 focus-visible:opacity-100"
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

function MemoryBrandMark({ data }: { data: MemoryDashboardData }) {
  return (
    <div className="flex items-center gap-3 px-2 py-1">
      <div className="flex size-9 shrink-0 items-center justify-center rounded-md bg-sidebar-primary text-sidebar-primary-foreground">
        <Bot />
      </div>
      <div className="flex min-w-0 flex-1 flex-col justify-center">
        <IdentityValue value={data.identity.runtimeId} />
        <IdentityValue value={data.identity.agent} />
      </div>
    </div>
  )
}

function WorkspaceSidebar({
  activePage,
  data,
  onBack,
  onPageChange,
}: {
  activePage: MemoryWorkspacePage
  data: MemoryDashboardData
  onBack: () => void
  onPageChange?: (page: MemoryWorkspacePage) => void
}) {
  return (
    <>
      <SidebarHeader className="gap-6 p-4">
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton onClick={onBack} type="button">
              <ArrowLeft />
              <span>Back to Console</span>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
        <MemoryBrandMark data={data} />
      </SidebarHeader>

      <SidebarContent>
        <SidebarGroup>
          <SidebarGroupLabel>Memory Workspace</SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu>
              {navItems.map((item) => (
                <SidebarMenuItem key={item.label}>
                  <SidebarMenuButton
                    aria-current={item.id === activePage ? "page" : undefined}
                    disabled={!item.enabled}
                    isActive={item.id === activePage}
                    onClick={() => {
                      if (item.enabled) {
                        onPageChange?.(item.id as MemoryWorkspacePage)
                      }
                    }}
                    type="button"
                  >
                    <item.icon />
                    <span>{item.label}</span>
                  </SidebarMenuButton>
                </SidebarMenuItem>
              ))}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>
    </>
  )
}

function MobileBackBar({ onBack }: { onBack: () => void }) {
  return (
    <div className="mb-6 lg:hidden">
      <Button onClick={onBack} type="button" variant="ghost">
        <ArrowLeft data-icon="inline-start" />
        Back to Console
      </Button>
    </div>
  )
}

function OverviewHeader({ data }: { data: MemoryDashboardData }) {
  return (
    <div className="mb-8 flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
      <div>
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="text-3xl font-semibold tracking-tight">
            Memory Overview
          </h1>
          <Badge variant="outline">
            <code>{data.id}</code>
            <Clipboard data-icon="inline-end" />
          </Badge>
        </div>
        <p className="mt-2 text-muted-foreground">
          User:{" "}
          <span className="font-medium text-foreground">{data.userId}</span>
          {" | "}
          Agent:{" "}
          <span className="font-medium text-foreground">{data.agentId}</span>
          {" | "}
          Last Activity:{" "}
          <span className="font-medium text-foreground">
            {data.lastActivity}
          </span>
        </p>
      </div>
      <div className="flex flex-wrap gap-2">
        <Button type="button" variant="outline">
          <RefreshCcw data-icon="inline-start" />
          Refresh
        </Button>
        <Button type="button">Force Snapshot</Button>
      </div>
    </div>
  )
}

function MetricGrid({ data }: { data: MemoryDashboardData }) {
  return (
    <section className="mb-6 grid grid-cols-1 gap-6 sm:grid-cols-2 xl:grid-cols-5">
      {data.metrics.map((metric) => (
        <Card
          key={metric.label}
          className={cn(
            metric.tone === "danger" &&
              "border-destructive/30 ring-destructive/20"
          )}
        >
          <CardHeader>
            <CardDescription>{metric.label}</CardDescription>
            <CardTitle
              className={cn(
                "mt-3 text-3xl font-semibold",
                metric.tone === "danger" && "text-destructive"
              )}
            >
              {metric.value}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <span className="text-muted-foreground">{metric.detail}</span>
          </CardContent>
        </Card>
      ))}
    </section>
  )
}

function PipelineStage({
  stage,
  isLast,
}: {
  stage: MemoryPipelineStage
  isLast: boolean
}) {
  return (
    <div className="relative flex min-w-32 flex-1 flex-col items-center gap-3 text-center">
      {!isLast ? (
        <div className="absolute top-5 left-1/2 hidden h-px w-full bg-border md:block" />
      ) : null}
      <div
        className={cn(
          "relative flex size-10 items-center justify-center rounded-full border bg-card",
          stage.status === "complete" && "bg-primary text-primary-foreground",
          stage.status === "warning" &&
            "border-destructive/40 bg-destructive/10 text-destructive"
        )}
      >
        {stage.status === "complete" ? (
          <Check className="size-4" />
        ) : stage.status === "warning" ? (
          <AlertTriangle className="size-4" />
        ) : (
          <CircleEllipsis className="size-4" />
        )}
      </div>
      <div>
        <div className="font-medium">{stage.label}</div>
        <div
          className={cn(
            "mt-1 text-xs text-muted-foreground",
            stage.status === "warning" && "text-destructive"
          )}
        >
          {stage.detail}
        </div>
      </div>
    </div>
  )
}

function MemoryPipeline({ data }: { data: MemoryDashboardData }) {
  return (
    <Card className="lg:col-span-2">
      <CardHeader className="border-b">
        <div>
          <CardTitle>Memory Formation Pipeline</CardTitle>
        </div>
        <CardAction>
          <Badge variant="outline">Operational</Badge>
        </CardAction>
      </CardHeader>
      <CardContent>
        <div className="flex flex-col gap-6 py-4 md:flex-row md:items-start md:justify-between">
          {data.pipeline.map((stage, index) => (
            <PipelineStage
              key={stage.label}
              isLast={index === data.pipeline.length - 1}
              stage={stage}
            />
          ))}
        </div>
      </CardContent>
    </Card>
  )
}

function AttentionPanel({ data }: { data: MemoryDashboardData }) {
  return (
    <Card>
      <CardHeader className="border-b">
        <CardTitle>Needs Attention</CardTitle>
      </CardHeader>
      <CardContent className="px-0">
        <div className="flex flex-col">
          {data.attention.map((item, index) => {
            return (
              <button
                key={item.label}
                className={cn(
                  "flex items-center justify-between gap-3 px-4 py-4 text-left transition-colors hover:bg-muted/50",
                  index > 0 && "border-t"
                )}
                type="button"
              >
                <span className="flex min-w-0 items-center gap-3">
                  {item.label.includes("conversation") ? (
                    <MessageSquare
                      className={cn(
                        "size-4 shrink-0 text-muted-foreground",
                        item.tone === "danger" && "text-destructive"
                      )}
                    />
                  ) : item.label.includes("insight") ? (
                    <Lightbulb
                      className={cn(
                        "size-4 shrink-0 text-muted-foreground",
                        item.tone === "danger" && "text-destructive"
                      )}
                    />
                  ) : (
                    <GitFork
                      className={cn(
                        "size-4 shrink-0 text-muted-foreground",
                        item.tone === "danger" && "text-destructive"
                      )}
                    />
                  )}
                  <span
                    className={cn(
                      "truncate font-medium",
                      item.tone === "danger" && "text-destructive"
                    )}
                  >
                    {item.label}
                  </span>
                </span>
                <span className="flex items-center gap-2">
                  <Badge
                    variant={item.tone === "danger" ? "destructive" : "outline"}
                  >
                    {item.count}
                  </Badge>
                  <ChevronRight className="size-4 text-muted-foreground" />
                </span>
              </button>
            )
          })}
        </div>
      </CardContent>
    </Card>
  )
}

function DistributionPanel({ data }: { data: MemoryDashboardData }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Raw Data by Type</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="flex flex-col gap-4">
          {data.rawDataByType.map((item) => (
            <Progress key={item.label} value={item.value}>
              <ProgressLabel>{item.label}</ProgressLabel>
              <ProgressValue />
            </Progress>
          ))}
        </div>
      </CardContent>
    </Card>
  )
}

function CategoryPanel({ data }: { data: MemoryDashboardData }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Items by Category</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
          {data.itemsByCategory.map((item) => (
            <div
              key={item.label}
              className="flex min-h-20 flex-col justify-between rounded-md border bg-muted/30 p-3"
            >
              <span className="text-xs text-muted-foreground">
                {item.label}
              </span>
              <span className="text-xl font-semibold">{item.value}</span>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  )
}

function ActivityItem({ item }: { item: MemoryActivityItem }) {
  return (
    <div className={cn("relative pl-8", item.tone === "muted" && "opacity-65")}>
      <div className="absolute top-1.5 left-0 flex size-5 items-center justify-center rounded-full border bg-card">
        <div className="size-1.5 rounded-full bg-primary" />
      </div>
      <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
        <div className="font-medium">{item.title}</div>
        <div className="text-xs text-muted-foreground">{item.time}</div>
      </div>
      <p className="mt-1 text-muted-foreground">{item.detail}</p>
    </div>
  )
}

function RecentActivity({ data }: { data: MemoryDashboardData }) {
  return (
    <Card className="lg:col-span-2">
      <CardHeader>
        <CardTitle>Recent Activity</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="relative flex flex-col gap-6 before:absolute before:top-3 before:bottom-3 before:left-2.5 before:w-px before:bg-border">
          {data.activity.map((item) => (
            <ActivityItem key={`${item.title}-${item.time}`} item={item} />
          ))}
        </div>
      </CardContent>
    </Card>
  )
}

function QuickRetrieve() {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Search className="size-4" />
          Quick Retrieve
        </CardTitle>
        <CardDescription>
          Test the memory retrieval engine against the current workspace state.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <FieldGroup>
          <Field>
            <FieldLabel htmlFor="memory-query">Query Text</FieldLabel>
            <Input
              id="memory-query"
              placeholder="e.g. What was mentioned about project delta?"
              type="text"
            />
          </Field>
          <Field>
            <FieldLabel>Retrieval Strategy</FieldLabel>
            <ToggleGroup defaultValue={["simple"]} spacing={1}>
              <ToggleGroupItem value="simple">Simple</ToggleGroupItem>
              <ToggleGroupItem value="deep">Deep</ToggleGroupItem>
            </ToggleGroup>
          </Field>
          <Separator />
          <Button className="w-full" type="button">
            <Bolt data-icon="inline-start" />
            Run Query
          </Button>
        </FieldGroup>
      </CardContent>
    </Card>
  )
}

function RawDataHeader() {
  return (
    <div className="mb-8 flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
      <div>
        <h1 className="text-3xl font-semibold tracking-tight">Raw Data</h1>
        <p className="mt-2 text-muted-foreground">
          Inspect source records, captions, segments, metadata, and cleanup
          impact for this Memory.
        </p>
      </div>
      <div className="flex flex-wrap gap-2">
        <Button type="button" variant="outline">
          <RefreshCcw data-icon="inline-start" />
          Refresh
        </Button>
        <Button disabled type="button" variant="outline">
          Delete selected
        </Button>
      </div>
    </div>
  )
}

function RawDataSummary({ data }: { data: MemoryDashboardData }) {
  return (
    <section className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-5">
      {data.rawData.summary.map((item) => (
        <Card key={item.label}>
          <CardHeader>
            <CardDescription>{item.label}</CardDescription>
            <CardTitle className="mt-3 text-3xl font-semibold">
              {item.value}
            </CardTitle>
          </CardHeader>
          <CardContent>
            {item.trend ? (
              <Badge variant="outline">{item.trend}</Badge>
            ) : (
              <span className="text-muted-foreground">
                {item.detail ?? "Current workspace"}
              </span>
            )}
          </CardContent>
        </Card>
      ))}
    </section>
  )
}

function RawDataToolbar() {
  return (
    <Card className="mb-4">
      <CardContent>
        <div className="flex flex-col gap-3 xl:flex-row xl:items-center">
          <InputGroup className="h-9 min-w-0 flex-1">
            <InputGroupAddon>
              <Search />
            </InputGroupAddon>
            <InputGroupInput placeholder="Search records..." type="search" />
          </InputGroup>

          <div className="flex flex-wrap items-center gap-2">
            <FilterSelect
              aria-label="Raw data type"
              className="min-w-32"
              defaultValue="all"
              items={rawDataTypeOptions}
            />
            <FilterSelect
              aria-label="Raw data source"
              className="min-w-32"
              defaultValue="all"
              items={rawDataSourceOptions}
            />
            <Button type="button" variant="outline">
              <CalendarDays data-icon="inline-start" />
              Date range
            </Button>
            <Button type="button" variant="outline">
              <ArrowUpDown data-icon="inline-start" />
              Sort
            </Button>
          </div>
        </div>
      </CardContent>
    </Card>
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

function VectorStatus({ status }: { status: string }) {
  return (
    <Badge variant="outline">
      <span className="size-1.5 rounded-full bg-primary" />
      {status}
    </Badge>
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
    <Card className="mb-6">
      <CardContent className="px-0">
        <Table>
          <TableHeader>
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
          </TableHeader>
          <TableBody>
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
                <TableCell className="max-w-[28rem] truncate font-medium">
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
          </TableBody>
        </Table>
        <PagePagination label={data.rawData.paginationLabel} />
      </CardContent>
    </Card>
  )
}

function JsonBlock({ label, value }: { label: string; value: string }) {
  let formattedValue = value

  try {
    formattedValue = JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    formattedValue = value
  }

  const highlightedJson = hljs.highlight(formattedValue, {
    language: "json",
    ignoreIllegals: true,
  }).value

  return (
    <div>
      <div className="mb-2 text-xs font-medium text-muted-foreground">
        {label}
      </div>
      <pre className="overflow-x-auto rounded-md bg-muted p-4 font-mono text-[11px] leading-relaxed">
        <code
          className="language-json"
          dangerouslySetInnerHTML={{ __html: highlightedJson }}
        />
      </pre>
    </div>
  )
}

function SheetPanel({
  children,
  className,
}: {
  children: React.ReactNode
  className?: string
}) {
  return (
    <SheetPrimitive.Portal>
      <SheetPrimitive.Popup
        data-testid="record-details-sheet"
        className={cn(
          "fixed inset-y-0 right-0 z-50 flex h-full w-3/4 flex-col border-l bg-popover bg-clip-padding text-xs/relaxed text-popover-foreground shadow-lg transition duration-200 ease-in-out data-ending-style:translate-x-full data-ending-style:opacity-0 data-starting-style:translate-x-full data-starting-style:opacity-0 sm:max-w-md",
          className
        )}
      >
        {children}
      </SheetPrimitive.Popup>
    </SheetPrimitive.Portal>
  )
}

function RecordDetailsSheet({
  onCloseComplete,
  onOpenChange,
  open,
  record,
}: {
  onCloseComplete: () => void
  onOpenChange: (open: boolean) => void
  open: boolean
  record: MemoryRawDataRecord | null
}) {
  if (!record) {
    return null
  }

  return (
    <Sheet
      onOpenChange={onOpenChange}
      onOpenChangeComplete={(isOpen) => {
        if (!isOpen) {
          onCloseComplete()
        }
      }}
      open={open}
    >
      <SheetPanel>
        <SheetHeader className="border-b">
          <SheetTitle>Record Details</SheetTitle>
          <SheetDescription className="font-mono">{record.id}</SheetDescription>
          <SheetPrimitive.Close
            className="absolute top-4 right-4 rounded-md p-1 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
            aria-label="Close"
          >
            <span className="sr-only">Close</span>
          </SheetPrimitive.Close>
        </SheetHeader>
        <div className="flex min-h-0 flex-1 flex-col gap-6 overflow-y-auto px-6 py-4">
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant="secondary">{record.type}</Badge>
            <span className="font-mono text-[11px] text-muted-foreground">
              Created {record.createdAt}
            </span>
          </div>

          <div className="rounded-md border bg-muted/30 p-4">
            <div className="mb-2 text-xs font-medium text-muted-foreground">
              Raw Caption
            </div>
            <p className="leading-relaxed">
              User inquired about API rate limits for the Nexus endpoint. They
              mentioned encountering 429 errors intermittently over the last 15
              minutes while attempting to sync vector batches from the
              development staging environment.
            </p>
          </div>

          <JsonBlock label="Segment Data" value={record.segmentJson} />
          <JsonBlock label="Metadata Objects" value={record.metadataJson} />

          <div>
            <div className="mb-2 text-xs font-medium text-muted-foreground">
              Associated Memory Items
            </div>
            <div className="flex flex-col gap-2">
              {record.associatedItems.map((item) => (
                <button
                  key={item.label}
                  className="flex items-center justify-between gap-3 rounded-md border bg-card px-3 py-3 text-left transition-colors hover:bg-muted/50"
                  type="button"
                >
                  <span className="flex min-w-0 items-center gap-3">
                    <FileText className="shrink-0 text-muted-foreground" />
                    <span className="truncate font-medium">{item.label}</span>
                  </span>
                  <ExternalLink className="shrink-0 text-muted-foreground" />
                </button>
              ))}
            </div>
          </div>
        </div>
        <SheetFooter className="border-t">
          <div className="flex flex-col gap-2 sm:flex-row">
            <Button className="flex-1" type="button">
              Reprocess
            </Button>
            <Button type="button" variant="destructive">
              Delete
            </Button>
          </div>
        </SheetFooter>
      </SheetPanel>
    </Sheet>
  )
}

function RawDataPage({ data }: { data: MemoryDashboardData }) {
  const [selectedRecord, setSelectedRecord] =
    useState<MemoryRawDataRecord | null>(null)
  const [isDetailsOpen, setIsDetailsOpen] = useState(false)

  function handleViewRecord(record: MemoryRawDataRecord) {
    setSelectedRecord(record)
    setIsDetailsOpen(true)
  }

  return (
    <>
      <RawDataHeader />
      <RawDataSummary data={data} />
      <RawDataToolbar />
      <RawDataTable data={data} onViewRecord={handleViewRecord} />
      <RecordDetailsSheet
        onCloseComplete={() => setSelectedRecord(null)}
        onOpenChange={(open) => {
          if (!open) {
            setIsDetailsOpen(false)
          }
        }}
        open={isDetailsOpen}
        record={selectedRecord}
      />
    </>
  )
}

function OverviewPage({
  data,
  onBack,
}: {
  data: MemoryDashboardData
  onBack: () => void
}) {
  return (
    <>
      <MobileBackBar onBack={onBack} />
      <OverviewHeader data={data} />
      <MetricGrid data={data} />

      <section className="mb-6 grid grid-cols-1 gap-6 xl:grid-cols-3">
        <MemoryPipeline data={data} />
        <AttentionPanel data={data} />
      </section>

      <section className="mb-6 grid grid-cols-1 gap-6 xl:grid-cols-2">
        <DistributionPanel data={data} />
        <CategoryPanel data={data} />
      </section>

      <section className="grid grid-cols-1 gap-6 xl:grid-cols-3">
        <RecentActivity data={data} />
        <QuickRetrieve />
      </section>
    </>
  )
}

export function MemoryDashboard({
  activePage = "overview",
  memoryId,
  onBack,
  onPageChange,
}: MemoryDashboardProps) {
  const dashboardQuery = useMemoryDashboard(memoryId)

  if (dashboardQuery.isLoading) {
    return (
      <main className="flex h-svh items-center justify-center text-sm text-muted-foreground">
        Loading memory dashboard...
      </main>
    )
  }

  if (dashboardQuery.isError || !dashboardQuery.data) {
    return (
      <main className="flex h-svh items-center justify-center text-sm text-destructive">
        Failed to load memory dashboard.
      </main>
    )
  }

  const data = dashboardQuery.data

  return (
    <SidebarShell
      contentKey={`memory-${activePage}`}
      sidebar={
        <WorkspaceSidebar
          activePage={activePage}
          data={data}
          onBack={onBack}
          onPageChange={onPageChange}
        />
      }
    >
      <div className="px-4 py-10 lg:px-10 lg:py-12">
        {activePage === "raw-data" ? (
          <RawDataPage data={data} />
        ) : (
          <OverviewPage data={data} onBack={onBack} />
        )}
      </div>
    </SidebarShell>
  )
}
