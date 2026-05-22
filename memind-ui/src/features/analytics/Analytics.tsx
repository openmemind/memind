import {
  Activity,
  AlertTriangle,
  CheckCircle2,
  Filter,
  Search,
  Timer,
} from "lucide-react"
import {
  Bar,
  BarChart,
  CartesianGrid,
  Line,
  LineChart,
  XAxis,
  YAxis,
} from "recharts"

import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { PaginatedTable } from "@/components/PaginatedTable"
import {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from "@/components/ui/chart"
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
import {
  TableCell,
  TableHead,
  TableRow,
} from "@/components/ui/table"
import {
  MetricCard,
  PageHeader,
  PageSurface,
  Panel,
  StatusBadge,
  type Tone,
} from "@/features/shared/ui"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"

import {
  type AnalyticsMetric,
  type LatencyPoint,
  type RecentTrace,
  type RequestHealthPoint,
  useAnalyticsData,
} from "./analytics-data"

const requestHealthChartConfig = {
  requests: {
    label: "Requests",
    color: "var(--chart-2)",
  },
  errors: {
    label: "Errors",
    color: "var(--destructive)",
  },
} satisfies ChartConfig

const latencyChartConfig = {
  p50: {
    label: "P50",
    color: "var(--chart-3)",
  },
  p95: {
    label: "P95",
    color: "var(--chart-2)",
  },
  p99: {
    label: "P99",
    color: "var(--destructive)",
  },
} satisfies ChartConfig

function metricIcon(index: number) {
  const icons = [Activity, AlertTriangle, Timer]
  return icons[index] ?? Activity
}

function TimeRangeToggle() {
  return (
    <ToggleGroup defaultValue={["15m"]} spacing={0} variant="outline">
      <ToggleGroupItem value="15m">15m</ToggleGroupItem>
      <ToggleGroupItem value="1h">1h</ToggleGroupItem>
      <ToggleGroupItem value="24h">24h</ToggleGroupItem>
      <ToggleGroupItem value="7d">7d</ToggleGroupItem>
      <ToggleGroupItem value="30d">30d</ToggleGroupItem>
    </ToggleGroup>
  )
}

function metricTone(tone: AnalyticsMetric["tone"]): Tone {
  return tone
}

function traceStatusTone(status: RecentTrace["status"]): Tone {
  if (status.startsWith("2")) {
    return "success"
  }

  if (status === "429") {
    return "warning"
  }

  return "danger"
}

function RequestHealthChart({ points }: { points: RequestHealthPoint[] }) {
  return (
    <Card className="h-full">
      <CardHeader>
        <CardTitle>Request Health</CardTitle>
        <CardDescription>
          Volume and error correlation over the last 60 minutes.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <ChartContainer
          className="aspect-auto h-72 w-full"
          config={requestHealthChartConfig}
        >
          <BarChart accessibilityLayer data={points}>
            <CartesianGrid vertical={false} />
            <XAxis
              axisLine={false}
              dataKey="label"
              tickLine={false}
              tickMargin={8}
            />
            <YAxis hide />
            <ChartTooltip
              content={<ChartTooltipContent indicator="dashed" />}
              cursor={false}
            />
            <Bar dataKey="requests" fill="var(--color-requests)" radius={4} />
            <Line
              dataKey="errors"
              dot={false}
              stroke="var(--color-errors)"
              strokeWidth={2}
              type="monotone"
            />
          </BarChart>
        </ChartContainer>
      </CardContent>
    </Card>
  )
}

function LatencyChart({ points }: { points: LatencyPoint[] }) {
  const maxLatency = Math.max(...points.map((point) => point.p99))

  return (
    <Panel
      description="Distribution across response percentiles."
      title="Latency (ms)"
    >
      <div className="flex flex-col gap-5">
        <ChartContainer
          className="aspect-auto h-44 w-full"
          config={latencyChartConfig}
        >
          <LineChart accessibilityLayer data={points}>
            <CartesianGrid vertical={false} />
            <XAxis
              axisLine={false}
              dataKey="label"
              tickLine={false}
              tickMargin={8}
            />
            <YAxis hide />
            <ChartTooltip content={<ChartTooltipContent />} cursor={false} />
            <Line
              dataKey="p50"
              dot={false}
              stroke="var(--color-p50)"
              strokeWidth={2}
              type="monotone"
            />
            <Line
              dataKey="p95"
              dot={false}
              stroke="var(--color-p95)"
              strokeWidth={2}
              type="monotone"
            />
            <Line
              dataKey="p99"
              dot={false}
              stroke="var(--color-p99)"
              strokeWidth={2}
              type="monotone"
            />
          </LineChart>
        </ChartContainer>

        <div className="flex flex-col gap-3">
          {points.slice(-4).map((point) => (
            <Progress key={point.label} value={(point.p99 / maxLatency) * 100}>
              <ProgressLabel>{point.label}</ProgressLabel>
              <ProgressValue>{() => `P99 ${point.p99}ms`}</ProgressValue>
            </Progress>
          ))}
        </div>
      </div>
    </Panel>
  )
}

function RecentTraces({ traces }: { traces: RecentTrace[] }) {
  return (
    <section className="flex flex-col gap-3">
      <div className="flex flex-col gap-3">
        <div>
          <h2 className="text-base font-semibold">Recent Traces</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Latest API calls across open and admin endpoints.
          </p>
        </div>
        <div className="flex flex-col gap-2 rounded-lg bg-muted/35 p-2 sm:flex-row sm:items-center">
          <InputGroup className="h-8 sm:w-72">
            <InputGroupAddon>
              <Search />
            </InputGroupAddon>
            <InputGroupInput placeholder="Filter traces..." type="search" />
          </InputGroup>
          <Button variant="outline">
            <Filter data-icon="inline-start" />
            Filter
          </Button>
        </div>
      </div>
      <PaginatedTable
        columnCount={8}
        columns={
          <TableRow>
            <TableHead>Trace ID</TableHead>
            <TableHead>Method</TableHead>
            <TableHead className="min-w-[240px]">Endpoint</TableHead>
            <TableHead>Status</TableHead>
            <TableHead>Duration</TableHead>
            <TableHead>User</TableHead>
            <TableHead>Timestamp</TableHead>
            <TableHead className="text-right">Action</TableHead>
          </TableRow>
        }
        emptyState={{
          description: "Recent API calls will appear after traffic is recorded.",
          title: "No recent traces",
        }}
        pagination={{ summary: `Showing ${traces.length} recent traces` }}
        tableClassName="min-w-[1120px]"
        tableViewportClassName="max-h-[420px] overflow-auto"
      >
        {traces.map((trace) => (
          <TableRow key={trace.traceId}>
            <TableCell>
              <code>{trace.traceId}</code>
            </TableCell>
            <TableCell>{trace.method}</TableCell>
            <TableCell>
              <code>{trace.endpoint}</code>
            </TableCell>
            <TableCell>
              <StatusBadge
                label={trace.status}
                tone={traceStatusTone(trace.status)}
              />
            </TableCell>
            <TableCell>{trace.duration}</TableCell>
            <TableCell>{trace.user}</TableCell>
            <TableCell className="text-muted-foreground">
              {trace.timestamp}
            </TableCell>
            <TableCell className="text-right">
              <Button variant="outline">View</Button>
            </TableCell>
          </TableRow>
        ))}
      </PaginatedTable>
    </section>
  )
}

export function Analytics() {
  const analyticsQuery = useAnalyticsData()

  const data = analyticsQuery.data

  return (
    <PageSurface className="h-full overflow-hidden">
      {analyticsQuery.isLoading ? null : analyticsQuery.isError ||
        !data ? null : (
        <div>
          <PageHeader
            action={<TimeRangeToggle />}
            description="Observe runtime health, latency, failures, and memory activity."
            title="Analytics"
          />

          <section className="mb-5 grid grid-cols-1 gap-4 md:grid-cols-3">
            {data.metrics.map((metric, index) => {
              const Icon =
                metric.tone === "success"
                  ? CheckCircle2
                  : metric.tone === "danger"
                    ? AlertTriangle
                    : metricIcon(index)

              return (
                <MetricCard
                  key={metric.label}
                  detail={metric.detail}
                  icon={Icon}
                  label={metric.label}
                  tone={metricTone(metric.tone)}
                  trend={metric.trend}
                  value={metric.value}
                />
              )
            })}
          </section>

          <section className="mb-5 grid grid-cols-1 gap-5 xl:grid-cols-[minmax(0,1fr)_360px]">
            <RequestHealthChart points={data.requestHealth} />
            <LatencyChart points={data.latency} />
          </section>

          <RecentTraces traces={data.traces} />
        </div>
      )}
    </PageSurface>
  )
}
