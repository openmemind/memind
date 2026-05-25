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

import { readFileSync } from "node:fs"
import { resolve } from "node:path"
import { describe, expect, it } from "vitest"

const threadsPagePath = resolve(
  process.cwd(),
  "src/features/memories/pages/ThreadsPage/ThreadsPage.tsx"
)
const dashboardPath = resolve(
  process.cwd(),
  "src/features/memories/dashboard/MemoryDashboard.tsx"
)

describe("ThreadsPage structure", () => {
  it("recreates the template as a shadcn split-pane workbench without editing shared shadcn components", () => {
    const source = readFileSync(threadsPagePath, "utf8")

    expect(source).toContain("thread-workbench")
    expect(source).toContain("thread-sidebar")
    expect(source).toContain("thread-detail")
    expect(source).toContain('from "@/components/ui/card"')
    expect(source).toContain('from "@/components/ui/input-group"')
    expect(source).toContain('from "@/components/ui/tabs"')
    expect(source).not.toContain('from "@/components/ToggleGroup"')
    expect(source).not.toContain('from "@/components/ui/toggle-group"')
    expect(source).not.toContain("MetricCard")
    expect(source).not.toContain("Panel")
  })

  it("lets the thread workbench occupy the full workspace content area", () => {
    const pageSource = readFileSync(threadsPagePath, "utf8")
    const dashboardSource = readFileSync(dashboardPath, "utf8")

    expect(pageSource).toContain("h-full w-full")
    expect(pageSource).not.toContain("min-h-[calc(100svh-3rem)]")
    expect(pageSource).not.toContain("rounded-lg border")
    expect(dashboardSource).toContain('activePage === "threads"')
    expect(dashboardSource).toContain("p-0 sm:px-0 lg:px-0 lg:py-0")
    expect(dashboardSource).toContain("overflow-hidden")
  })

  it("uses a single-border segmented filter with a visible active state", () => {
    const source = readFileSync(threadsPagePath, "utf8")

    expect(source).toContain("<Tabs")
    expect(source).toContain("<TabsList")
    expect(source).toContain("<TabsTrigger")
    expect(source).toContain(
      'className="w-full rounded-md border bg-muted p-1"'
    )
    expect(source).toContain("value={filter}")
    expect(source).not.toContain("threadFilterItemClassName")
    expect(source).not.toContain("<ToggleGroup")
  })
})
