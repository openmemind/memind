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

import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Header } from '@/components/layout/header'
import { Main } from '@/components/layout/main'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { getDashboard } from '@/features/api/dashboard'
import {
  EmptyState,
  PageError,
  PageLoading,
} from '@/features/components/data-state'
import { readMemoryScopeFromLocation } from '@/features/components/memory-scope-location'
import { formatNumber } from '@/lib/format'
import type { AdminDashboardView, DailyCount, NamedCount } from '@/features/types'

export function DashboardPage() {
  const [days, setDays] = useState(() => readDaysFromLocation())
  const memoryId = readMemoryScopeFromLocation()
  const query = useQuery({
    queryKey: ['dashboard', memoryId, days],
    queryFn: () => getDashboard({ memoryId, days }),
  })

  return (
    <>
      <Header>
        <div className='min-w-0'>
          <h1 className='truncate text-lg font-semibold'>Memind UI</h1>
          <p className='truncate text-sm text-muted-foreground'>
            Local Memory Admin
          </p>
        </div>
      </Header>
      <Main>
        <div className='flex flex-col gap-6'>
          <div className='flex flex-wrap items-center justify-between gap-3'>
            <div className='flex flex-col gap-1'>
              <h2 className='text-2xl font-semibold'>Dashboard</h2>
              <p className='text-sm text-muted-foreground'>
                Local memory activity and maintenance backlog.
              </p>
            </div>
            <div className='flex items-center gap-2'>
              <Label htmlFor='dashboard-days'>Activity days</Label>
              <select
                id='dashboard-days'
                aria-label='Activity days'
                value={days}
                className='h-9 rounded-md border bg-background px-3 text-sm'
                onChange={(event) => {
                  const next = Number(event.target.value)
                  setDays(next)
                  writeDaysToLocation(next)
                }}
              >
                <option value={7}>7</option>
                <option value={14}>14</option>
                <option value={30}>30</option>
              </select>
            </div>
          </div>

          {query.isLoading ? <PageLoading /> : null}
          {query.isError ? (
            <PageError message='Unable to load dashboard.' onRetry={query.refetch} />
          ) : null}
          {query.data ? <DashboardContent data={query.data} memoryId={memoryId} /> : null}
        </div>
      </Main>
    </>
  )
}

function DashboardContent({
  data,
  memoryId,
}: {
  data: AdminDashboardView
  memoryId: string
}) {
  const isZero = useMemo(() => isZeroDashboard(data), [data])

  if (isZero) {
    return <EmptyState title='No memory activity for this scope yet.' />
  }

  return (
    <div className='flex flex-col gap-6'>
      <MetricGrid data={data} />
      <BacklogGrid backlog={data.backlog} memoryId={memoryId} />
      <ActivityPanel activity={data.activity} />
      <BreakdownPanel breakdown={data.breakdown} />
      <HealthPanel healthSignals={data.healthSignals} />
    </div>
  )
}

function MetricGrid({ data }: { data: AdminDashboardView }) {
  const metrics = [
    ['Raw Data', data.totals.rawData],
    ['Memory Items', data.totals.items],
    ['Insights', data.totals.insights],
    ['Memory Threads', data.totals.memoryThreads],
    ['Graph Entities', data.totals.graphEntities],
    ['Item Links', data.totals.itemLinks],
  ] as const

  return (
    <div className='grid gap-4 md:grid-cols-2 xl:grid-cols-3'>
      {metrics.map(([label, value]) => (
        <Card key={label} aria-label={`${label} ${value}`}>
          <CardHeader>
            <CardDescription>{label}</CardDescription>
            <CardTitle className='text-3xl'>{formatNumber(value)}</CardTitle>
          </CardHeader>
        </Card>
      ))}
    </div>
  )
}

function BacklogGrid({
  backlog,
  memoryId,
}: {
  backlog: AdminDashboardView['backlog']
  memoryId: string
}) {
  const links = [
    {
      label: 'pending conversations',
      value: backlog.conversationPending,
      href: backlogHref('/buffers', [
        ['tab', 'conversations'],
        ['state', 'pending'],
        ['memoryId', memoryId],
      ]),
    },
    {
      label: 'unbuilt insights',
      value: backlog.insightUnbuilt,
      href: backlogHref('/buffers', [
        ['tab', 'insights'],
        ['state', 'unbuilt'],
        ['memoryId', memoryId],
      ]),
    },
    {
      label: 'ungrouped insights',
      value: backlog.insightUngrouped,
      href: backlogHref('/buffers', [
        ['tab', 'insights'],
        ['state', 'ungrouped'],
        ['memoryId', memoryId],
      ]),
    },
    {
      label: 'thread outbox pending',
      value: backlog.threadOutboxPending,
      href: backlogHref('/memory-threads', [
        ['focus', 'status'],
        ['memoryId', memoryId],
      ]),
    },
    {
      label: 'thread outbox failed',
      value: backlog.threadOutboxFailed,
      href: backlogHref('/memory-threads', [
        ['focus', 'status'],
        ['memoryId', memoryId],
      ]),
    },
    {
      label: 'graph batches needing repair',
      value: backlog.graphBatchRepairRequired,
      href: backlogHref('/item-graph', [
        ['tab', 'batches'],
        ['state', 'REPAIR_REQUIRED'],
        ['memoryId', memoryId],
      ]),
    },
  ]

  return (
    <Card>
      <CardHeader>
        <CardTitle>Backlog</CardTitle>
        <CardDescription>Maintenance queues with direct drill-down links.</CardDescription>
      </CardHeader>
      <CardContent>
        <div className='grid gap-2 md:grid-cols-2 xl:grid-cols-3'>
          {links.map((link) => (
            <a
              key={link.label}
              href={link.href}
              className='rounded-md border p-3 text-sm hover:bg-accent'
            >
              <span>{link.label}</span> <span>{formatNumber(link.value)}</span>
            </a>
          ))}
        </div>
      </CardContent>
    </Card>
  )
}

function ActivityPanel({
  activity,
}: {
  activity: AdminDashboardView['activity']
}) {
  const rows = mergeActivityRows(
    activity.rawDataCreated,
    activity.itemsCreated,
    activity.insightsCreated
  )

  return (
    <Card>
      <CardHeader>
        <CardTitle>Activity</CardTitle>
        <CardDescription>{activity.days} day creation trend.</CardDescription>
      </CardHeader>
      <CardContent>
        <div className='overflow-auto'>
          <table className='w-full text-sm'>
            <thead>
              <tr className='border-b text-left'>
                <th className='py-2 font-medium'>Date</th>
                <th className='py-2 font-medium'>Raw data</th>
                <th className='py-2 font-medium'>Items</th>
                <th className='py-2 font-medium'>Insights</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.date} className='border-b'>
                  <td className='py-2'>{row.date}</td>
                  <td className='py-2'>{formatNumber(row.rawData)}</td>
                  <td className='py-2'>{formatNumber(row.items)}</td>
                  <td className='py-2'>{formatNumber(row.insights)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </CardContent>
    </Card>
  )
}

function BreakdownPanel({
  breakdown,
}: {
  breakdown: AdminDashboardView['breakdown']
}) {
  return (
    <div className='grid gap-4 lg:grid-cols-2'>
      <BreakdownCard title='Source Clients' values={breakdown.sourceClients} />
      <BreakdownCard title='Raw Data Types' values={breakdown.rawDataTypes} />
      <BreakdownCard title='Item Types' values={breakdown.itemTypes} />
      <BreakdownCard title='Insight Types' values={breakdown.insightTypes} />
      <BreakdownCard title='Graph Link Types' values={breakdown.graphLinkTypes} />
    </div>
  )
}

function BreakdownCard({ title, values }: { title: string; values: NamedCount[] }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
      </CardHeader>
      <CardContent>
        {values.length === 0 ? (
          <p className='text-sm text-muted-foreground'>No data.</p>
        ) : (
          <ul className='flex flex-col gap-2 text-sm'>
            {values.map((value) => (
              <li key={value.name} className='flex items-center justify-between gap-4'>
                <span className='truncate'>{value.name}</span>
                <span>{formatNumber(value.count)}</span>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}

function HealthPanel({
  healthSignals,
}: {
  healthSignals: AdminDashboardView['healthSignals']
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Health Signals</CardTitle>
      </CardHeader>
      <CardContent>
        <div className='grid gap-3 text-sm md:grid-cols-2'>
          <p>Graph enabled: {healthSignals.graphEnabled ? 'yes' : 'no'}</p>
          <p>
            Retrieval graph assist:{' '}
            {healthSignals.retrievalGraphAssistEnabled ? 'yes' : 'no'}
          </p>
        </div>
        {healthSignals.threadProjectionStates.length > 0 ? (
          <ul className='mt-4 flex flex-col gap-2 text-sm'>
            {healthSignals.threadProjectionStates.map((state) => (
              <li key={state.state}>
                {state.state}: {formatNumber(state.count)}
              </li>
            ))}
          </ul>
        ) : null}
      </CardContent>
    </Card>
  )
}

function readDaysFromLocation() {
  if (typeof window === 'undefined') return 7
  const raw = new URLSearchParams(window.location.search).get('days')
  const parsed = raw ? Number(raw) : 7
  return [7, 14, 30].includes(parsed) ? parsed : 7
}

function writeDaysToLocation(days: number) {
  if (typeof window === 'undefined') return
  const url = new URL(window.location.href)
  if (days === 7) {
    url.searchParams.delete('days')
  } else {
    url.searchParams.set('days', String(days))
  }
  window.history.replaceState(window.history.state, '', url)
}

function backlogHref(path: string, pairs: Array<[string, string | undefined]>) {
  const params = new URLSearchParams()
  for (const [key, value] of pairs) {
    if (value) params.append(key, value)
  }
  const query = params.toString()
  return query ? `${path}?${query}` : path
}

function isZeroDashboard(data: AdminDashboardView) {
  return (
    Object.values(data.totals).every((value) => value === 0) &&
    Object.values(data.backlog).every((value) => value === 0)
  )
}

function mergeActivityRows(
  rawDataCreated: DailyCount[],
  itemsCreated: DailyCount[],
  insightsCreated: DailyCount[]
) {
  const rows = new Map<
    string,
    { date: string; rawData: number; items: number; insights: number }
  >()

  for (const item of rawDataCreated) {
    rows.set(item.date, {
      ...(rows.get(item.date) ?? emptyActivityRow(item.date)),
      rawData: item.count,
    })
  }
  for (const item of itemsCreated) {
    rows.set(item.date, {
      ...(rows.get(item.date) ?? emptyActivityRow(item.date)),
      items: item.count,
    })
  }
  for (const item of insightsCreated) {
    rows.set(item.date, {
      ...(rows.get(item.date) ?? emptyActivityRow(item.date)),
      insights: item.count,
    })
  }

  return [...rows.values()].sort((a, b) => a.date.localeCompare(b.date))
}

function emptyActivityRow(date: string) {
  return { date, rawData: 0, items: 0, insights: 0 }
}
