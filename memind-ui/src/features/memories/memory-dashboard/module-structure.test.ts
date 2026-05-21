import { existsSync, readFileSync } from "node:fs"
import { resolve } from "node:path"
import { describe, expect, it } from "vitest"

const dashboardDir = resolve(
  process.cwd(),
  "src/features/memories/memory-dashboard"
)

describe("memory dashboard module structure", () => {
  it("keeps second-level console pages under the memories feature", () => {
    const expectedPages = [
      "OverviewPage.tsx",
      "RawDataPage.tsx",
      "ItemsPage.tsx",
      "GraphPage.tsx",
      "ThreadsPage.tsx",
      "InsightsPage.tsx",
    ]

    expectedPages.forEach((pageFile) => {
      expect(existsSync(resolve(dashboardDir, "pages", pageFile))).toBe(true)
    })

    expect(
      existsSync(resolve(process.cwd(), "src/features/memory-dashboard"))
    ).toBe(false)
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
