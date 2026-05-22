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

import { useNavigate } from "@tanstack/react-router"
import { AlertTriangle, CheckCircle2, Circle, Info, Search } from "lucide-react"
import { Area, AreaChart, CartesianGrid, Line, XAxis, YAxis } from "recharts"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
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
  TableCell,
  TableHead,
  TableRow,
} from "@/components/ui/table"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { cn } from "@/lib/utils"

import {
  type ActivityPoint,
  type AlertSummaryItem,
  type DashboardMetric,
  type MemoryActivity,
  useDashboardData,
} from "./dashboard-data"

const requestActivityChartConfig = {
  requests: {
    label: "req count",
    color: "var(--primary)",
  },
  extractRequests: {
    label: "req count extract",
    color: "var(--muted-foreground)",
  },
  insights: {
    label: "insights",
    color: "var(--chart-3)",
  },
} satisfies ChartConfig

function TimeRangeToggle() {
  return (
    <ToggleGroup
      className="rounded-lg border border-border/60 bg-card p-1"
      defaultValue={["24h"]}
      spacing={1}
    >
      <ToggleGroupItem className="h-7 px-3" value="24h">
        24h
      </ToggleGroupItem>
      <ToggleGroupItem className="h-7 px-3" value="7d">
        7d
      </ToggleGroupItem>
      <ToggleGroupItem className="h-7 px-3" value="30d">
        30d
      </ToggleGroupItem>
    </ToggleGroup>
  )
}

function DashboardSearch() {
  return (
    <div className="mb-6">
      <InputGroup className="h-14 rounded-lg border-border/60 bg-card">
        <InputGroupAddon className="pl-4">
          <Search />
        </InputGroupAddon>
        <InputGroupInput
          className="text-sm"
          placeholder="Search MemoryId / UserId / AgentId..."
          type="search"
        />
      </InputGroup>
    </div>
  )
}

function MetricTrend({ metric }: { metric: DashboardMetric }) {
  if (metric.tone === "success" && metric.label === "memories") {
    return (
      <Badge className="rounded px-1.5 py-0.5 text-[11px]" variant="outline">
        {metric.trend}
      </Badge>
    )
  }

  return (
    <span
      className={cn(
        "text-xs font-medium text-muted-foreground",
        metric.tone === "danger" && "text-destructive",
        metric.label === "req count" && "text-foreground"
      )}
    >
      {metric.detail}
    </span>
  )
}

function DashboardMetricCard({ metric }: { metric: DashboardMetric }) {
  return (
    <Card
      data-testid="dashboard-metric-card"
      className={cn(
        "min-h-24 justify-between p-0 py-0",
        metric.tone === "danger" && "border-l-2 border-l-destructive/80"
      )}
    >
      <CardHeader className="px-4 pt-4">
        <CardTitle className="text-sm font-normal text-muted-foreground">
          {metric.label}
        </CardTitle>
      </CardHeader>
      <CardContent className="px-4 pb-4">
        <div className="flex flex-wrap items-baseline gap-2">
          <span
            className={cn(
              "text-3xl leading-none font-bold tracking-tight text-foreground tabular-nums",
              metric.tone === "danger" && "text-destructive"
            )}
          >
            {metric.value}
          </span>
          <MetricTrend metric={metric} />
        </div>
      </CardContent>
    </Card>
  )
}

function ChartLegendItem({
  label,
  tone,
}: {
  label: string
  tone: "primary" | "secondary" | "destructive"
}) {
  return (
    <div className="flex items-center gap-1.5 text-[11px] font-medium">
      <Circle
        className={cn(
          "size-2 fill-current",
          tone === "primary" && "text-primary",
          tone === "secondary" && "text-muted-foreground",
          tone === "destructive" && "text-destructive"
        )}
      />
      <span>{label}</span>
    </div>
  )
}


function RequestActivityChart({ points }: { points: ActivityPoint[] }) {

  return (
    <Card className="h-full p-0 py-0" data-testid="request-activity-panel">
      <CardHeader className="gap-4 px-4 pt-4">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <CardTitle className="text-base font-semibold">
              Request Activity
            </CardTitle>
            <p className="mt-1 text-xs text-muted-foreground">7-day flow</p>
          </div>
          <div className="mb-3 flex flex-wrap gap-3">
            <ChartLegendItem label="req count" tone="primary" />
            <ChartLegendItem label="req count extract" tone="secondary" />
            <ChartLegendItem label="insights" tone="destructive" />
          </div>
        </div>
      </CardHeader>
      <CardContent className="px-4 pb-4">
        <ChartContainer
          className="aspect-auto h-56 w-full"
          config={requestActivityChartConfig}
          data-testid="request-activity-chart"
        >
          <AreaChart
            accessibilityLayer
            data={points}
            margin={{ bottom: 6, left: 0, right: 8, top: 12 }}
          >
            <defs>
              <linearGradient
                id="requestActivityFill"
                x1="0"
                x2="0"
                y1="0"
                y2="1"
              >
                <stop
                  offset="5%"
                  stopColor="var(--color-requests)"
                  stopOpacity={0.22}
                />
                <stop
                  offset="95%"
                  stopColor="var(--color-requests)"
                  stopOpacity={0.02}
                />
              </linearGradient>
            </defs>
            <CartesianGrid
              strokeDasharray="3 6"
              stroke="var(--border)"
              strokeOpacity={0.55}
              vertical={false}
            />
            <XAxis
              axisLine={false}
              dataKey="label"
              interval={0}
              tickLine={false}
              tickMargin={10}
            />
            <YAxis
              axisLine={false}
              tickLine={false}
              tickMargin={8}
              width={32}
            />
            <Area
              dataKey="requests"
              fill="url(#requestActivityFill)"
              fillOpacity={1}
              stroke="var(--color-requests)"
              strokeWidth={2.5}
              type="monotone"
            />
            <Line
              dataKey="extractRequests"
              dot={false}
              stroke="var(--color-extractRequests)"
              strokeDasharray="4 4"
              strokeWidth={2}
              type="monotone"
            />
            <Line
              dataKey="insights"
              dot={false}
              stroke="var(--color-insights)"
              strokeWidth={2}
              type="monotone"
            />
            <ChartTooltip
              content={<ChartTooltipContent indicator="line" />}
              cursor={{
                stroke: "var(--border)",
                strokeDasharray: "3 3",
                strokeOpacity: 0.9,
              }}
            />
          </AreaChart>
        </ChartContainer>
      </CardContent>
    </Card>
  )
}

function AlertsSummary({ alerts }: { alerts: AlertSummaryItem[] }) {
  return (
    <Card className="h-full p-0 py-0">
      <CardHeader className="px-6 pt-6">
        <CardTitle className="text-xl font-semibold">Alerts Summary</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-8 px-6 pb-6">
        <div className="grid grid-cols-2 gap-4">
          <div className="rounded-lg bg-destructive/10 p-4 text-destructive">
            <div className="text-4xl leading-none font-bold">1</div>
            <p className="mt-2 text-xs font-medium">Critical</p>
          </div>
          <div className="rounded-lg bg-muted p-4">
            <div className="text-4xl leading-none font-bold">2</div>
            <p className="mt-2 text-xs font-medium text-muted-foreground">
              Warning
            </p>
          </div>
        </div>
        <div className="flex flex-col gap-4">
          {alerts.map((alert) => (
            <div
              className="flex items-center justify-between gap-4 rounded-lg border border-border/60 p-3 text-xs font-medium"
              key={alert.memoryId}
            >
              <code className="font-mono text-foreground">
                {alert.memoryId}
              </code>
              <span className="text-muted-foreground">{alert.time}</span>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  )
}

function AlertIcon({ alert }: { alert: MemoryActivity["alert"] }) {
  if (alert === "critical") {
    return (
      <span className="text-destructive" title="Critical alert">
        <AlertTriangle />
      </span>
    )
  }

  if (alert === "info") {
    return (
      <span className="text-muted-foreground" title="Informational alert">
        <Info />
      </span>
    )
  }

  return (
    <span className="text-muted-foreground" title="Healthy">
      <CheckCircle2 />
    </span>
  )
}

function RecentlyAddedMemories({
  onOpenMemory,
  rows,
}: {
  onOpenMemory: (memoryId: string) => void
  rows: MemoryActivity[]
}) {
  return (
    <section className="overflow-hidden rounded-lg ring-1 ring-foreground/10">
      <div className="border-b border-border/50 px-6 py-5">
        <h2 className="text-xl font-semibold">Recently Added Memories</h2>
      </div>
      <PaginatedTable
        className="rounded-none border-0 bg-transparent"
        columnCount={8}
        columns={
          <TableRow>
            <TableHead className="px-6">Memory</TableHead>
            <TableHead className="px-6">User</TableHead>
            <TableHead className="px-6">Agent</TableHead>
            <TableHead className="px-6">Created</TableHead>
            <TableHead className="px-6">Requests</TableHead>
            <TableHead className="px-6">Alerts</TableHead>
            <TableHead className="px-6">Last Activity</TableHead>
            <TableHead className="px-6 text-right">Action</TableHead>
          </TableRow>
        }
        emptyState={{
          description: "Recent memory activity will appear here.",
          title: "No recent memories",
        }}
        pagination={{ summary: `Showing ${rows.length} recent memories` }}
        tableClassName="min-w-240"
      >
        {rows.map((row) => (
          <TableRow key={row.memoryId}>
            <TableCell className="px-6 font-mono text-foreground">
              {row.memoryId}
            </TableCell>
            <TableCell className="px-6">{row.userId}</TableCell>
            <TableCell className="px-6 font-medium">{row.agentId}</TableCell>
            <TableCell className="px-6 text-muted-foreground">
              {row.createdAt}
            </TableCell>
            <TableCell className="px-6 font-mono">{row.requests}</TableCell>
            <TableCell className="px-6">
              <AlertIcon alert={row.alert} />
            </TableCell>
            <TableCell className="px-6 text-muted-foreground">
              {row.updatedAt}
            </TableCell>
            <TableCell className="px-6 text-right">
              <Button
                onClick={() => onOpenMemory(row.memoryId)}
                type="button"
                variant="outline"
              >
                Open
              </Button>
            </TableCell>
          </TableRow>
        ))}
      </PaginatedTable>
    </section>
  )
}

export function Dashboard() {
  const navigate = useNavigate()
  const dashboardQuery = useDashboardData()

  if (dashboardQuery.isLoading) {
    return (
      <main className="flex min-h-svh items-center justify-center text-sm text-muted-foreground">
        Loading dashboard...
      </main>
    )
  }

  if (dashboardQuery.isError || !dashboardQuery.data) {
    return (
      <main className="flex min-h-svh items-center justify-center text-sm text-destructive">
        Failed to load dashboard data.
      </main>
    )
  }

  const data = dashboardQuery.data

  return (
    <main className="min-h-full px-4 pt-10 pb-12 sm:px-8 lg:px-10 lg:pt-12">
      <header className="mb-10 flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <h1 className="text-4xl leading-tight font-bold tracking-tight text-foreground">
            Overview
          </h1>
          <p className="mt-1 text-base text-muted-foreground">
            Track memory growth, requests, alerts, and recent activity.
          </p>
        </div>
        <TimeRangeToggle />
      </header>

      <DashboardSearch />

      <section className="mb-5 grid grid-cols-1 gap-4 md:grid-cols-3">
        {data.metrics.map((metric) => (
          <DashboardMetricCard key={metric.label} metric={metric} />
        ))}
      </section>

      <section className="mb-6 grid grid-cols-1 gap-6 xl:grid-cols-3">
        <div className="xl:col-span-2">
          <RequestActivityChart points={data.activity} />
        </div>
        <AlertsSummary alerts={data.alerts} />
      </section>

      <RecentlyAddedMemories
        onOpenMemory={(memoryId) => {
          void navigate({
            to: "/memories/$memoryId",
            params: { memoryId },
          })
        }}
        rows={data.recentActivity}
      />
    </main>
  )
}
