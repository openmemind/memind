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

import { existsSync, readFileSync } from "node:fs"
import { resolve } from "node:path"
import { describe, expect, it } from "vitest"

const memoriesDir = resolve(process.cwd(), "src/features/memories")
const dashboardDir = resolve(memoriesDir, "dashboard")
const pagesDir = resolve(memoriesDir, "pages")
const componentsDir = resolve(memoriesDir, "components")

describe("memory dashboard module structure", () => {
  it("keeps the dashboard shell, shared components, and page modules separated under memories", () => {
    const expectedPages = {
      GraphPage: ["GraphPage.tsx", "GraphPage.structure.test.ts", "graph-api.ts"],
      InsightsPage: [
        "InsightsPage.tsx",
        "InsightsPage.test.tsx",
        "insights-api.ts",
      ],
      ItemsPage: ["ItemsPage.tsx", "ItemsPage.test.tsx", "items-api.ts"],
      OverviewPage: ["OverviewPage.tsx", "overview-api.ts"],
      RawDataPage: ["RawDataPage.tsx", "RawDataPage.test.tsx", "raw-data-api.ts"],
      ThreadsPage: [
        "ThreadsPage.tsx",
        "ThreadsPage.structure.test.ts",
        "threads-api.ts",
      ],
    }
    const expectedComponents = [
      "DetailRow.tsx",
      "JsonBlock.tsx",
      "MobileBackBar.tsx",
      "SheetPanel.tsx",
      "VectorStatus.tsx",
      "WorkspaceSidebar.tsx",
    ]

    expect(existsSync(resolve(dashboardDir, "MemoryDashboard.tsx"))).toBe(true)
    expect(existsSync(resolve(dashboardDir, "memory-dashboard-data.ts"))).toBe(
      true
    )
    expect(existsSync(resolve(dashboardDir, "types.ts"))).toBe(true)

    Object.entries(expectedPages).forEach(([pageDir, files]) => {
      files.forEach((file) => {
        expect(existsSync(resolve(pagesDir, pageDir, file))).toBe(true)
      })
    })

    expectedComponents.forEach((file) => {
      expect(existsSync(resolve(componentsDir, file))).toBe(true)
    })

    expect(existsSync(resolve(memoriesDir, "memory-dashboard"))).toBe(false)
  })

  it("leaves MemoryDashboard responsible only for the workspace shell", () => {
    const shellSource = readFileSync(
      resolve(dashboardDir, "MemoryDashboard.tsx"),
      "utf8"
    )

    expect(shellSource).not.toMatch(/function OverviewPage/)
    expect(shellSource).not.toMatch(/function RawDataPage/)
    expect(shellSource).not.toMatch(/function ItemsPage/)
    expect(shellSource).not.toMatch(/function GraphPage/)
    expect(shellSource).not.toMatch(/function ThreadsPage/)
    expect(shellSource).not.toMatch(/function InsightsPage/)
  })
})
