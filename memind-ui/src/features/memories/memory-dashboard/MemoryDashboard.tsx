import { SidebarShell } from "@/features/shell/SidebarShell"
import { PageSurface } from "@/features/shared/ui"
import { cn } from "@/lib/utils"

import { WorkspaceSidebar } from "./components/WorkspaceSidebar"
import { useMemoryDashboard } from "./memory-dashboard-data"
import { GraphPage } from "./pages/GraphPage"
import { InsightsPage } from "./pages/InsightsPage"
import { ItemsPage } from "./pages/ItemsPage"
import { OverviewPage } from "./pages/OverviewPage"
import { RawDataPage } from "./pages/RawDataPage"
import { ThreadsPage } from "./pages/ThreadsPage"
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
        {activePage === "raw-data" ? (
          <RawDataPage data={data} />
        ) : activePage === "items" ? (
          <ItemsPage data={data} />
        ) : activePage === "graph" ? (
          <GraphPage data={data} />
        ) : activePage === "threads" ? (
          <ThreadsPage data={data} />
        ) : activePage === "insights" ? (
          <InsightsPage data={data} />
        ) : (
          <OverviewPage data={data} onBack={onBack} />
        )}
      </PageSurface>
    </SidebarShell>
  )
}
