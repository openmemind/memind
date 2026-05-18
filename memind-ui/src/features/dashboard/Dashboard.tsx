import { AlertTriangle, CheckCircle2, Filter, Info, Search } from "lucide-react"
import {
  Line,
  LineChart,
  ResponsiveContainer,
  XAxis,
  YAxis,
} from "recharts"

import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import {
  Card,
  CardAction,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
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
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import {
  MetricCard,
  PageHeader,
  PagePagination,
  type Tone,
} from "@/features/shared/ui"

import {
  type ActivityPoint,
  type AlertSummaryItem,
  type DashboardMetric,
  type MemoryActivity,
  type MetricTone,
  useDashboardData,
} from "./dashboard-data"

const metricTone = (tone: MetricTone): Tone => tone

function TimeRangeToggle() {
  return (
    <ToggleGroup
      defaultValue={["24h"]}
      spacing={0}
      variant="outline"
    >
      <ToggleGroupItem value="24h">24h</ToggleGroupItem>
      <ToggleGroupItem value="7d">7d</ToggleGroupItem>
      <ToggleGroupItem value="30d">30d</ToggleGroupItem>
    </ToggleGroup>
  )
}

function DashboardSearch() {
  return (
    <div className="mb-6">
      <InputGroup className="h-12">
        <InputGroupAddon>
          <Search />
        </InputGroupAddon>
        <InputGroupInput
          placeholder="Search MemoryId / UserId / AgentId..."
          type="search"
        />
      </InputGroup>
    </div>
  )
}

function DashboardMetricCard({ metric }: { metric: DashboardMetric }) {
  return (
    <MetricCard
      detail={metric.detail}
      label={metric.label}
      tone={metricTone(metric.tone)}
      trend={metric.trend}
      value={metric.value}
    />
  )
}

function RequestActivityChart({ points }: { points: ActivityPoint[] }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Request Activity</CardTitle>
        <CardAction>
          <div className="flex flex-wrap gap-2">
            <Badge variant="secondary">Total</Badge>
            <Badge variant="outline">Success</Badge>
            <Badge variant="destructive">Failed</Badge>
          </div>
        </CardAction>
      </CardHeader>
      <CardContent>
        <div className="h-72">
          <ResponsiveContainer height="100%" width="100%">
            <LineChart data={points} margin={{ left: 0, right: 0 }}>
              <XAxis
                axisLine={false}
                dataKey="label"
                tickLine={false}
                tickMargin={12}
              />
              <YAxis hide />
              <Line
                dataKey="total"
                dot={false}
                stroke="var(--primary)"
                strokeWidth={2}
                type="monotone"
              />
              <Line
                dataKey="success"
                dot={false}
                stroke="var(--chart-2)"
                strokeWidth={2}
                type="monotone"
              />
              <Line
                dataKey="failed"
                dot={false}
                stroke="var(--destructive)"
                strokeWidth={2}
                type="monotone"
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </CardContent>
    </Card>
  )
}

function AlertsSummary({ alerts }: { alerts: AlertSummaryItem[] }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Alerts Summary</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-6">
        <div className="grid grid-cols-2 gap-4">
          <div>
            <div className="text-3xl font-semibold">1</div>
            <Badge variant="destructive">Critical</Badge>
          </div>
          <div>
            <div className="text-3xl font-semibold">2</div>
            <Badge variant="outline">Warning</Badge>
          </div>
        </div>
        <div className="flex flex-col gap-4">
          {alerts.map((alert) => (
            <div
              className="flex items-center justify-between gap-4"
              key={alert.memoryId}
            >
              <code>{alert.memoryId}</code>
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
    return <AlertTriangle className="size-4 text-destructive" />
  }

  if (alert === "info") {
    return <Info className="size-4 text-muted-foreground" />
  }

  return <CheckCircle2 className="size-4 text-muted-foreground" />
}

function RecentlyAddedMemories({ rows }: { rows: MemoryActivity[] }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Recently Added Memories</CardTitle>
        <CardAction>
          <Button variant="outline">
            <Filter data-icon="inline-start" />
            Filter
          </Button>
        </CardAction>
      </CardHeader>
      <CardContent>
        <Table className="min-w-[960px]">
          <TableHeader>
            <TableRow>
              <TableHead>Memory</TableHead>
              <TableHead>User</TableHead>
              <TableHead>Agent</TableHead>
              <TableHead>Created</TableHead>
              <TableHead>Requests</TableHead>
              <TableHead>Alerts</TableHead>
              <TableHead>Last Activity</TableHead>
              <TableHead className="text-right">Action</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map((row) => (
              <TableRow key={row.memoryId}>
                <TableCell>
                  <code>{row.memoryId}</code>
                </TableCell>
                <TableCell>{row.userId}</TableCell>
                <TableCell>{row.agentId}</TableCell>
                <TableCell className="text-muted-foreground">
                  {row.createdAt}
                </TableCell>
                <TableCell>{row.requests}</TableCell>
                <TableCell>
                  <AlertIcon alert={row.alert} />
                </TableCell>
                <TableCell className="text-muted-foreground">
                  {row.updatedAt}
                </TableCell>
                <TableCell className="text-right">
                  <Button variant="outline">Open</Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        <PagePagination label="Showing 4 of 1,284 memories" />
      </CardContent>
    </Card>
  )
}

export function Dashboard() {
  const dashboardQuery = useDashboardData()

  if (dashboardQuery.isLoading) {
    return (
      <div className="flex min-h-svh items-center justify-center text-sm text-muted-foreground">
        Loading dashboard...
      </div>
    )
  }

  if (dashboardQuery.isError || !dashboardQuery.data) {
    return (
      <div className="flex min-h-svh items-center justify-center text-sm text-destructive">
        Failed to load dashboard data.
      </div>
    )
  }

  const data = dashboardQuery.data

  return (
    <main className="px-4 py-10 lg:px-10 lg:py-12">
      <PageHeader
        action={<TimeRangeToggle />}
        description="Track memory growth, requests, alerts, and recent activity."
        title="Dashboard"
      />

      <DashboardSearch />

      <section className="mb-6 grid grid-cols-1 gap-6 md:grid-cols-2 xl:grid-cols-5">
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

      <RecentlyAddedMemories rows={data.recentActivity} />
    </main>
  )
}
