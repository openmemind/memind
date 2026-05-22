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
  Navigate,
  Outlet,
  RouterProvider,
  createRootRoute,
  createRoute,
  createRouter,
  useNavigate,
  useRouterState,
} from "@tanstack/react-router"

import { Analytics } from "@/features/analytics/Analytics"
import { ApiKeys } from "@/features/api-keys/ApiKeys"
import { Dashboard } from "@/features/dashboard/Dashboard"
import { Memories } from "@/features/memories/Memories"
import { MemoryDashboard } from "@/features/memories/memory-dashboard/MemoryDashboard"
import { Settings } from "@/features/settings/Settings"
import { AppShell, type AppPage } from "@/features/shell/AppShell"

const pagePaths: Record<AppPage, string> = {
  dashboard: "/dashboard",
  memories: "/memories",
  analytics: "/analytics",
  "api-keys": "/api-keys",
  settings: "/settings",
}

function activePageFromPath(pathname: string): AppPage {
  if (pathname.startsWith("/memories")) {
    return "memories"
  }

  if (pathname.startsWith("/analytics")) {
    return "analytics"
  }

  if (pathname.startsWith("/api-keys")) {
    return "api-keys"
  }

  if (pathname.startsWith("/settings")) {
    return "settings"
  }

  return "dashboard"
}

function ConsoleLayout() {
  const navigate = useNavigate()
  const pathname = useRouterState({
    select: (state) => state.location.pathname,
  })

  return (
    <AppShell
      activePage={activePageFromPath(pathname)}
      onPageChange={(page) => {
        void navigate({ to: pagePaths[page] })
      }}
    >
      <Outlet />
    </AppShell>
  )
}

function MemoriesRouteComponent() {
  const navigate = useNavigate()

  return (
    <Memories
      onOpenMemory={(memoryId) => {
        void navigate({
          to: "/memories/$memoryId",
          params: { memoryId },
        })
      }}
    />
  )
}

function MemoryWorkspaceRouteComponent() {
  const navigate = useNavigate()
  const { memoryId } = memoryWorkspaceRoute.useParams()
  const pathname = useRouterState({
    select: (state) => state.location.pathname,
  })
  const activePage = pathname.endsWith("/raw-data")
    ? "raw-data"
    : pathname.endsWith("/items")
      ? "items"
      : pathname.endsWith("/graph")
        ? "graph"
        : pathname.endsWith("/threads")
          ? "threads"
          : pathname.endsWith("/insights")
            ? "insights"
            : "overview"

  return (
    <MemoryDashboard
      activePage={activePage}
      memoryId={memoryId}
      onBack={() => {
        void navigate({ to: "/memories" })
      }}
      onPageChange={(page) => {
        if (page === "overview") {
          void navigate({
            to: "/memories/$memoryId",
            params: { memoryId },
          })
        }

        if (page === "raw-data") {
          void navigate({
            to: "/memories/$memoryId/raw-data",
            params: { memoryId },
          })
        }

        if (page === "items") {
          void navigate({
            to: "/memories/$memoryId/items",
            params: { memoryId },
          })
        }

        if (page === "graph") {
          void navigate({
            to: "/memories/$memoryId/graph",
            params: { memoryId },
          })
        }

        if (page === "threads") {
          void navigate({
            to: "/memories/$memoryId/threads",
            params: { memoryId },
          })
        }

        if (page === "insights") {
          void navigate({
            to: "/memories/$memoryId/insights",
            params: { memoryId },
          })
        }
      }}
    />
  )
}

const rootRoute = createRootRoute()

const indexRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/",
  component: () => <Navigate replace to="/dashboard" />,
})

const consoleLayoutRoute = createRoute({
  getParentRoute: () => rootRoute,
  id: "console",
  component: ConsoleLayout,
})

const dashboardRoute = createRoute({
  getParentRoute: () => consoleLayoutRoute,
  path: "dashboard",
  component: Dashboard,
})

const memoriesRoute = createRoute({
  getParentRoute: () => consoleLayoutRoute,
  path: "memories",
  component: MemoriesRouteComponent,
})

const analyticsRoute = createRoute({
  getParentRoute: () => consoleLayoutRoute,
  path: "analytics",
  component: Analytics,
})

const apiKeysRoute = createRoute({
  getParentRoute: () => consoleLayoutRoute,
  path: "api-keys",
  component: ApiKeys,
})

const settingsRoute = createRoute({
  getParentRoute: () => consoleLayoutRoute,
  path: "settings",
  component: Settings,
})

const memoryWorkspaceRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "memories/$memoryId",
  component: MemoryWorkspaceRouteComponent,
})

const memoryWorkspaceIndexRoute = createRoute({
  getParentRoute: () => memoryWorkspaceRoute,
  path: "/",
})

const memoryRawDataRoute = createRoute({
  getParentRoute: () => memoryWorkspaceRoute,
  path: "raw-data",
})

const memoryItemsRoute = createRoute({
  getParentRoute: () => memoryWorkspaceRoute,
  path: "items",
})

const memoryGraphRoute = createRoute({
  getParentRoute: () => memoryWorkspaceRoute,
  path: "graph",
})

const memoryThreadsRoute = createRoute({
  getParentRoute: () => memoryWorkspaceRoute,
  path: "threads",
})

const memoryInsightsRoute = createRoute({
  getParentRoute: () => memoryWorkspaceRoute,
  path: "insights",
})

const routeTree = rootRoute.addChildren([
  indexRoute,
  consoleLayoutRoute.addChildren([
    dashboardRoute,
    memoriesRoute,
    analyticsRoute,
    apiKeysRoute,
    settingsRoute,
  ]),
  memoryWorkspaceRoute.addChildren([
    memoryWorkspaceIndexRoute,
    memoryRawDataRoute,
    memoryItemsRoute,
    memoryGraphRoute,
    memoryThreadsRoute,
    memoryInsightsRoute,
  ]),
])

function createAppRouter() {
  return createRouter({ routeTree })
}

declare module "@tanstack/react-router" {
  interface Register {
    router: ReturnType<typeof createAppRouter>
  }
}

export function App() {
  return <RouterProvider router={createAppRouter()} />
}

export default App
