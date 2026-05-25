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
  Bolt,
  Check,
  ChevronRight,
  CircleEllipsis,
  Clipboard,
  GitFork,
  Lightbulb,
  MessageSquare,
  RefreshCcw,
  Search,
} from "lucide-react"

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
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import {
  Progress,
  ProgressLabel,
  ProgressValue,
} from "@/components/ui/progress"
import { Separator } from "@/components/ui/separator"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { PageHeader, type Tone } from "@/features/shared/ui"
import { cn } from "@/lib/utils"

import { MobileBackBar } from "../../components/MobileBackBar"
import type {
  MemoryActivityItem,
  MemoryDashboardData,
  MemoryPipelineStage,
} from "../../dashboard/memory-dashboard-data"
import type { RefreshAction } from "../../dashboard/refresh-action"
import type { MemoryWorkspacePage } from "../../dashboard/types"

function OverviewHeader({
  data,
  refreshAction,
}: {
  data: MemoryDashboardData
  refreshAction: RefreshAction
}) {
  return (
    <div data-testid="memory-overview-header">
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
          </div>
        }
        description={`User: ${data.userId} | Agent: ${data.agentId} | Last Activity: ${data.lastActivity}`}
        eyebrow={data.status}
        title="Memory Overview"
      />
      <Badge className="-mt-3 mb-5 w-fit gap-1.5" variant="outline">
        <code>{data.id}</code>
        <Clipboard data-icon="inline-end" />
      </Badge>
    </div>
  )
}

function metricTone(
  tone: MemoryDashboardData["metrics"][number]["tone"]
): Tone {
  return tone === "danger" ? "danger" : "default"
}

function CompactMetricCard({
  detail,
  label,
  tone = "default",
  value,
}: {
  detail: string
  label: string
  tone?: Tone
  value: string
}) {
  return (
    <Card
      className={cn(
        "gap-2 transition-colors hover:ring-primary/25",
        tone === "danger" && "ring-destructive/20"
      )}
      size="sm"
    >
      <CardHeader className="gap-0.5">
        <CardDescription className="font-medium tracking-[0.05em] uppercase">
          {label}
        </CardDescription>
        <CardTitle
          className={cn(
            "text-xl font-semibold tabular-nums md:text-2xl",
            tone === "danger" && "text-destructive"
          )}
        >
          {value}
        </CardTitle>
      </CardHeader>
      <CardContent>
        <div className="text-xs text-muted-foreground">{detail}</div>
      </CardContent>
    </Card>
  )
}

function MetricGrid({ data }: { data: MemoryDashboardData }) {
  return (
    <section className="mb-4 grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-5">
      {data.metrics.map((metric) => (
        <CompactMetricCard
          key={metric.label}
          detail={metric.detail}
          label={metric.label}
          tone={metricTone(metric.tone)}
          value={metric.value}
        />
      ))}
    </section>
  )
}

function CompactPanel({
  title,
  description,
  action,
  className,
  contentClassName,
  children,
}: {
  title?: string
  description?: string
  action?: React.ReactNode
  className?: string
  contentClassName?: string
  children: React.ReactNode
}) {
  return (
    <Card className={cn("bg-card/95", className)} size="sm">
      {title || description || action ? (
        <CardHeader className="border-b border-border/70 pb-3">
          <div>
            {title ? <CardTitle>{title}</CardTitle> : null}
            {description ? (
              <CardDescription>{description}</CardDescription>
            ) : null}
          </div>
          {action ? <CardAction>{action}</CardAction> : null}
        </CardHeader>
      ) : null}
      <CardContent className={cn("pt-0", contentClassName)}>
        {children}
      </CardContent>
    </Card>
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
    <div className="relative flex min-w-32 flex-1 flex-col items-center gap-2 text-center">
      {!isLast ? (
        <div className="absolute top-4 left-1/2 hidden h-px w-full bg-border md:block" />
      ) : null}
      <div
        className={cn(
          "relative flex size-8 items-center justify-center rounded-full border bg-card",
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
        <div className="text-sm font-medium">{stage.label}</div>
        <div
          className={cn(
            "mt-0.5 text-xs text-muted-foreground",
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
    <CompactPanel
      action={<Badge variant="outline">Operational</Badge>}
      className="lg:col-span-2"
      title="Memory Formation Pipeline"
    >
      <div className="flex flex-col gap-4 py-2 md:flex-row md:items-start md:justify-between">
        {data.pipeline.map((stage, index) => (
          <PipelineStage
            key={stage.label}
            isLast={index === data.pipeline.length - 1}
            stage={stage}
          />
        ))}
      </div>
    </CompactPanel>
  )
}

function attentionTargetPage(label: string): MemoryWorkspacePage {
  if (label.includes("conversation")) {
    return "threads"
  }

  if (label.includes("insight")) {
    return "insights"
  }

  return "graph"
}

function AttentionPanel({
  data,
  onPageChange,
}: {
  data: MemoryDashboardData
  onPageChange?: (page: MemoryWorkspacePage) => void
}) {
  return (
    <CompactPanel contentClassName="px-0" title="Needs Attention">
      <div className="flex flex-col">
        {data.attention.map((item, index) => {
          const targetPage = attentionTargetPage(item.label)

          return (
            <button
              key={item.label}
              className={cn(
                "flex items-center justify-between gap-3 px-3 py-3 text-left transition-colors hover:bg-muted/50",
                index > 0 && "border-t"
              )}
              onClick={() => onPageChange?.(targetPage)}
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
    </CompactPanel>
  )
}

function DistributionPanel({ data }: { data: MemoryDashboardData }) {
  return (
    <CompactPanel title="Raw Data by Type">
      <div className="flex flex-col gap-3">
        {data.rawDataByType.map((item) => (
          <Progress key={item.label} value={item.value}>
            <ProgressLabel>{item.label}</ProgressLabel>
            <ProgressValue />
          </Progress>
        ))}
      </div>
    </CompactPanel>
  )
}

function CategoryPanel({ data }: { data: MemoryDashboardData }) {
  return (
    <CompactPanel title="Items by Category">
      <div className="grid grid-cols-2 gap-2 md:grid-cols-4">
        {data.itemsByCategory.map((item) => (
          <div
            key={item.label}
            className="flex min-h-16 flex-col justify-between rounded-md border bg-muted/30 p-2.5"
          >
            <span className="text-xs text-muted-foreground">{item.label}</span>
            <span className="text-lg font-semibold">{item.value}</span>
          </div>
        ))}
      </div>
    </CompactPanel>
  )
}

function ActivityItem({ item }: { item: MemoryActivityItem }) {
  return (
    <div className={cn("relative pl-7", item.tone === "muted" && "opacity-65")}>
      <div className="absolute top-1.5 left-0 flex size-4 items-center justify-center rounded-full border bg-card">
        <div className="size-1.5 rounded-full bg-primary" />
      </div>
      <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
        <div className="text-sm font-medium">{item.title}</div>
        <div className="text-xs text-muted-foreground">{item.time}</div>
      </div>
      <p className="mt-0.5 text-muted-foreground">{item.detail}</p>
    </div>
  )
}

function RecentActivity({ data }: { data: MemoryDashboardData }) {
  return (
    <CompactPanel className="lg:col-span-2" title="Recent Activity">
      <div className="relative flex flex-col gap-4 before:absolute before:top-3 before:bottom-3 before:left-2 before:w-px before:bg-border">
        {data.activity.map((item) => (
          <ActivityItem key={`${item.title}-${item.time}`} item={item} />
        ))}
      </div>
    </CompactPanel>
  )
}

function QuickRetrieve() {
  return (
    <Card className="gap-3" size="sm">
      <CardHeader className="gap-1 border-b border-border/70 pb-3">
        <CardTitle className="flex items-center gap-2">
          <Search className="size-4" />
          Quick Retrieve
        </CardTitle>
        <CardDescription>
          Test the memory retrieval engine against the current workspace state.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <FieldGroup className="gap-3">
          <Field className="gap-1.5">
            <FieldLabel htmlFor="memory-query">Query Text</FieldLabel>
            <Input
              className="h-8"
              id="memory-query"
              placeholder="e.g. What was mentioned about project delta?"
              type="text"
            />
          </Field>
          <Field className="gap-1.5">
            <FieldLabel>Retrieval Strategy</FieldLabel>
            <ToggleGroup defaultValue={["simple"]} spacing={1}>
              <ToggleGroupItem value="simple">Simple</ToggleGroupItem>
              <ToggleGroupItem value="deep">Deep</ToggleGroupItem>
            </ToggleGroup>
          </Field>
          <Separator />
          <Button className="w-full" size="sm" type="button">
            <Bolt data-icon="inline-start" />
            Run Query
          </Button>
        </FieldGroup>
      </CardContent>
    </Card>
  )
}

export function OverviewPage({
  data,
  onBack,
  onPageChange,
  refreshAction,
}: {
  data: MemoryDashboardData
  onBack: () => void
  onPageChange?: (page: MemoryWorkspacePage) => void
  refreshAction: RefreshAction
}) {
  return (
    <>
      <MobileBackBar onBack={onBack} />
      <OverviewHeader data={data} refreshAction={refreshAction} />
      <MetricGrid data={data} />

      <section className="mb-4 grid grid-cols-1 gap-4 xl:grid-cols-3">
        <MemoryPipeline data={data} />
        <AttentionPanel data={data} onPageChange={onPageChange} />
      </section>

      <section className="mb-4 grid grid-cols-1 gap-4 xl:grid-cols-2">
        <DistributionPanel data={data} />
        <CategoryPanel data={data} />
      </section>

      <section className="grid grid-cols-1 gap-4 xl:grid-cols-3">
        <RecentActivity data={data} />
        <QuickRetrieve />
      </section>
    </>
  )
}
