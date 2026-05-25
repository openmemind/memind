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

import { SidebarShell } from "@/features/shell/SidebarShell"
import { PageSurface } from "@/features/shared/ui"
import { cn } from "@/lib/utils"

import { WorkspaceSidebar } from "../components/WorkspaceSidebar"
import {
  useMemoryBuffers,
  useMemoryDashboard,
  useMemoryGraph,
  useMemoryInsights,
  useMemoryItems,
  useMemoryRawData,
  useMemoryThreads,
} from "./memory-dashboard-data"
import { BuffersPage } from "../pages/BuffersPage/BuffersPage"
import { GraphPage } from "../pages/GraphPage/GraphPage"
import { InsightsPage } from "../pages/InsightsPage/InsightsPage"
import { ItemsPage } from "../pages/ItemsPage/ItemsPage"
import { OverviewPage } from "../pages/OverviewPage/OverviewPage"
import { RawDataPage } from "../pages/RawDataPage/RawDataPage"
import { ThreadsPage } from "../pages/ThreadsPage/ThreadsPage"
import type { MemoryWorkspacePage } from "./types"

type MemoryDashboardProps = {
  memoryId: string
  onBack: () => void
  activePage?: MemoryWorkspacePage
  onPageChange?: (page: MemoryWorkspacePage) => void
}

export function MemoryDashboard({
  activePage = "overview",
  memoryId,
  onBack,
  onPageChange,
}: MemoryDashboardProps) {
  const dashboardQuery = useMemoryDashboard(memoryId)
  const rawDataQuery = useMemoryRawData(memoryId, activePage === "raw-data")
  const itemsQuery = useMemoryItems(memoryId, activePage === "items")
  const graphQuery = useMemoryGraph(memoryId, activePage === "graph")
  const threadsQuery = useMemoryThreads(memoryId, activePage === "threads")
  const insightsQuery = useMemoryInsights(memoryId, activePage === "insights")
  const buffersQuery = useMemoryBuffers(memoryId, activePage === "buffers")

  const activePageQuery =
    activePage === "raw-data"
      ? rawDataQuery
      : activePage === "items"
        ? itemsQuery
        : activePage === "graph"
          ? graphQuery
          : activePage === "threads"
            ? threadsQuery
            : activePage === "insights"
              ? insightsQuery
              : activePage === "buffers"
                ? buffersQuery
                : dashboardQuery

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

  const data = {
    ...dashboardQuery.data,
    buffers: buffersQuery.data ?? dashboardQuery.data.buffers,
    graph: graphQuery.data ?? dashboardQuery.data.graph,
    insights: insightsQuery.data ?? dashboardQuery.data.insights,
    items: itemsQuery.data ?? dashboardQuery.data.items,
    rawData: rawDataQuery.data ?? dashboardQuery.data.rawData,
    threads: threadsQuery.data ?? dashboardQuery.data.threads,
  }
  const refreshAction = {
    isRefreshing: activePageQuery.isFetching,
    onRefresh: () => {
      void activePageQuery.refetch()
    },
  }
  const isPageLoading =
    activePage !== "overview" &&
    activePageQuery.isLoading &&
    !activePageQuery.data
  const isPageError = activePage !== "overview" && activePageQuery.isError
  const isFullWorkspacePage =
    activePage === "graph" ||
    activePage === "threads" ||
    activePage === "insights"

  return (
    <SidebarShell
      contentClassName={isFullWorkspacePage ? "overflow-hidden" : undefined}
      contentKey={`memory-${activePage}`}
      contentSurfaceClassName={
        isFullWorkspacePage
          ? "flex h-full min-h-0 w-full min-w-0 max-w-none"
          : undefined
      }
      sidebar={
        <WorkspaceSidebar
          activePage={activePage}
          data={data}
          onBack={onBack}
          onPageChange={onPageChange}
        />
      }
    >
      <PageSurface
        className={cn(
          "px-4 py-6 sm:px-6 lg:px-10 lg:py-8",
          isFullWorkspacePage &&
            "h-full min-h-0 w-full min-w-0 flex-1 p-0 sm:px-0 lg:px-0 lg:py-0"
        )}
        data-testid="memory-workspace-surface"
      >
        {isPageLoading ? (
          <main className="flex h-full min-h-80 items-center justify-center text-sm text-muted-foreground">
            Loading memory dashboard...
          </main>
        ) : isPageError ? (
          <main className="flex h-full min-h-80 items-center justify-center text-sm text-destructive">
            Failed to load memory dashboard.
          </main>
        ) : activePage === "raw-data" ? (
          <RawDataPage data={data} refreshAction={refreshAction} />
        ) : activePage === "items" ? (
          <ItemsPage data={data} refreshAction={refreshAction} />
        ) : activePage === "graph" ? (
          <GraphPage data={data} refreshAction={refreshAction} />
        ) : activePage === "threads" ? (
          <ThreadsPage data={data} refreshAction={refreshAction} />
        ) : activePage === "insights" ? (
          <InsightsPage data={data} refreshAction={refreshAction} />
        ) : activePage === "buffers" ? (
          <BuffersPage data={data} refreshAction={refreshAction} />
        ) : (
          <OverviewPage
            data={data}
            onBack={onBack}
            onPageChange={onPageChange}
            refreshAction={refreshAction}
          />
        )}
      </PageSurface>
    </SidebarShell>
  )
}
