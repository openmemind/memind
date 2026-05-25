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

const insightsPagePath = resolve(
  process.cwd(),
  "src/features/memories/pages/InsightsPage/InsightsPage.tsx"
)
const insightLayoutPath = resolve(
  process.cwd(),
  "src/features/memories/pages/InsightsPage/insight-layout.ts"
)

describe("InsightsPage structure", () => {
  it("uses a compact insight tree layout", () => {
    const source = readFileSync(insightLayoutPath, "utf8")

    expect(source).toContain("const branchGap = 86")
    expect(source).toContain("const branchY = 64")
    expect(source).toContain("const leafGap = 54")
    expect(source).toContain("const rootWidth = 96")
    expect(source).toContain("const rootHeight = 36")
    expect(source).toContain("const branchWidth = 58")
    expect(source).toContain("const branchHeight = 27")
    expect(source).toContain("const leafWidth = 42")
    expect(source).toContain("const leafHeight = 23")
    expect(source).toContain("const subtreeGap = branchGap - branchWidth")
    expect(source).toContain("const branchSlots = root.branches.map")
    expect(source).toContain("const canvasWidth = Math.max(196")
    expect(source).toContain("height: 152")
  })

  it("keeps insight node content compact enough for the reduced node sizes", () => {
    const source = readFileSync(insightsPagePath, "utf8")
    const nodeSource = source.slice(
      source.indexOf("function InsightFlowNode"),
      source.indexOf("const insightNodeTypes")
    )

    expect(nodeSource).toContain('data-testid="memory-insight-node-shell"')
    expect(nodeSource).toContain('data-testid="memory-insight-node-title"')
    expect(nodeSource).toContain("overflow-hidden")
    expect(nodeSource).toContain("truncate")
    expect(nodeSource).toContain("text-[7px]")
    expect(nodeSource).toContain("text-[6px]")
    expect(nodeSource).toContain("text-[5px]")

    expect(nodeSource).not.toContain("<Badge")
    expect(nodeSource).not.toContain("data.description")
    expect(nodeSource).not.toContain("px-6")
    expect(nodeSource).not.toContain("px-5")
    expect(nodeSource).not.toContain("px-4")
    expect(nodeSource).not.toContain("py-5")
    expect(nodeSource).not.toContain("line-clamp")
    expect(nodeSource).not.toContain("text-xl")
    expect(nodeSource).not.toContain("mt-6")
    expect(nodeSource).not.toContain("mt-4")
    expect(nodeSource).not.toContain("mt-3")
  })

  it("uses naturally curved bezier edges between insight nodes", () => {
    const source = readFileSync(insightsPagePath, "utf8")
    const edgeSource = source.slice(
      source.indexOf("function buildInsightFlowElements"),
      source.indexOf("function InsightsCanvas")
    )

    expect(edgeSource).toContain('type: "default"')
    expect(edgeSource).not.toContain('type: "smoothstep"')
  })

  it("allows insight canvas nodes to be dragged", () => {
    const source = readFileSync(insightsPagePath, "utf8")

    expect(source).toContain("draggable: true")
    expect(source).toContain("nodesDraggable")
    expect(source).not.toContain("draggable: false")
    expect(source).not.toContain("nodesDraggable={false}")
  })
})
