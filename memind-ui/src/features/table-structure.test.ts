import { readFileSync } from "node:fs"
import { resolve } from "node:path"
import { describe, expect, it } from "vitest"

const featureFilesWithTables = [
  "src/features/dashboard/Dashboard.tsx",
  "src/features/analytics/Analytics.tsx",
  "src/features/api-keys/ApiKeys.tsx",
  "src/features/memories/Memories.tsx",
  "src/features/memories/memory-dashboard/pages/RawDataPage.tsx",
  "src/features/memories/memory-dashboard/pages/ItemsPage.tsx",
  "src/features/memories/memory-dashboard/pages/GraphPage.tsx",
]

function containerStartBeforeTable(source: string, tableIndex: number) {
  const cardStart = source.lastIndexOf("<Card", tableIndex)
  const cardEnd = source.lastIndexOf("</Card>", tableIndex)
  const panelStart = source.lastIndexOf("<Panel", tableIndex)
  const panelEnd = source.lastIndexOf("</Panel>", tableIndex)

  return {
    cardOpen: cardStart > cardEnd,
    panelOpen: panelStart > panelEnd,
  }
}

describe("feature tables", () => {
  it("uses shadcn table directly instead of Card or Panel wrappers", () => {
    featureFilesWithTables.forEach((file) => {
      const source = readFileSync(resolve(process.cwd(), file), "utf8")
      const tableMatches = source.matchAll(/<Table[\s>]/g)

      for (const match of tableMatches) {
        const index = match.index ?? 0
        const containerState = containerStartBeforeTable(source, index)

        expect(containerState, `${file} should not wrap Table in Card`).toEqual(
          expect.objectContaining({ cardOpen: false })
        )
        expect(
          containerState,
          `${file} should not wrap Table in Panel`
        ).toEqual(expect.objectContaining({ panelOpen: false }))
      }
    })
  })
})
