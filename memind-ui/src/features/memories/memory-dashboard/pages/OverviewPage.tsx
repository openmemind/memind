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
import { MetricCard, PageHeader, Panel, type Tone } from "@/features/shared/ui"
import { cn } from "@/lib/utils"

import { MobileBackBar } from "../components/MobileBackBar"
import type {
  MemoryActivityItem,
  MemoryDashboardData,
  MemoryPipelineStage,
} from "../memory-dashboard-data"

function OverviewHeader({ data }: { data: MemoryDashboardData }) {
  return (
    <div data-testid="memory-overview-header">
      <PageHeader
        action={
          <div className="flex flex-wrap gap-2">
            <Button type="button" variant="outline">
              <RefreshCcw data-icon="inline-start" />
              Refresh
            </Button>
            <Button type="button">Force Snapshot</Button>
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

function MetricGrid({ data }: { data: MemoryDashboardData }) {
  return (
    <section className="mb-5 grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-5">
      {data.metrics.map((metric) => (
        <MetricCard
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
    <Panel
      action={<Badge variant="outline">Operational</Badge>}
      className="lg:col-span-2"
      title="Memory Formation Pipeline"
    >
      <div className="flex flex-col gap-6 py-4 md:flex-row md:items-start md:justify-between">
        {data.pipeline.map((stage, index) => (
          <PipelineStage
            key={stage.label}
            isLast={index === data.pipeline.length - 1}
            stage={stage}
          />
        ))}
      </div>
    </Panel>
  )
}

function AttentionPanel({ data }: { data: MemoryDashboardData }) {
  return (
    <Panel contentClassName="px-0" title="Needs Attention">
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
    </Panel>
  )
}

function DistributionPanel({ data }: { data: MemoryDashboardData }) {
  return (
    <Panel title="Raw Data by Type">
      <div className="flex flex-col gap-4">
        {data.rawDataByType.map((item) => (
          <Progress key={item.label} value={item.value}>
            <ProgressLabel>{item.label}</ProgressLabel>
            <ProgressValue />
          </Progress>
        ))}
      </div>
    </Panel>
  )
}

function CategoryPanel({ data }: { data: MemoryDashboardData }) {
  return (
    <Panel title="Items by Category">
      <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
        {data.itemsByCategory.map((item) => (
          <div
            key={item.label}
            className="flex min-h-20 flex-col justify-between rounded-md border bg-muted/30 p-3"
          >
            <span className="text-xs text-muted-foreground">{item.label}</span>
            <span className="text-xl font-semibold">{item.value}</span>
          </div>
        ))}
      </div>
    </Panel>
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
    <Panel className="lg:col-span-2" title="Recent Activity">
      <div className="relative flex flex-col gap-6 before:absolute before:top-3 before:bottom-3 before:left-2.5 before:w-px before:bg-border">
        {data.activity.map((item) => (
          <ActivityItem key={`${item.title}-${item.time}`} item={item} />
        ))}
      </div>
    </Panel>
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

export function OverviewPage({
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

      <section className="mb-5 grid grid-cols-1 gap-5 xl:grid-cols-3">
        <MemoryPipeline data={data} />
        <AttentionPanel data={data} />
      </section>

      <section className="mb-5 grid grid-cols-1 gap-5 xl:grid-cols-2">
        <DistributionPanel data={data} />
        <CategoryPanel data={data} />
      </section>

      <section className="grid grid-cols-1 gap-5 xl:grid-cols-3">
        <RecentActivity data={data} />
        <QuickRetrieve />
      </section>
    </>
  )
}
