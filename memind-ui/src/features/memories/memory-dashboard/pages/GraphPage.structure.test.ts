import { readFileSync } from "node:fs"
import { resolve } from "node:path"
import { describe, expect, it } from "vitest"

const graphPagePath = resolve(
  process.cwd(),
  "src/features/memories/memory-dashboard/pages/GraphPage.tsx"
)

describe("GraphPage structure", () => {
  it("uses React Flow for the entity graph instead of hand-drawn SVG edges", () => {
    const source = readFileSync(graphPagePath, "utf8")

    expect(source).toContain('from "@xyflow/react"')
    expect(source).toContain("<ReactFlow")
    expect(source).not.toContain("<svg")
    expect(source).not.toContain("<line")
  })
})
