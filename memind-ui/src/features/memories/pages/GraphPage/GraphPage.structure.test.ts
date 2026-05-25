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

const graphPagePath = resolve(
  process.cwd(),
  "src/features/memories/pages/GraphPage/GraphPage.tsx"
)
const graphApiPath = resolve(
  process.cwd(),
  "src/features/memories/pages/GraphPage/graph-api.ts"
)

describe("GraphPage structure", () => {
  it("uses React Flow for the entity graph instead of hand-drawn SVG edges", () => {
    const source = readFileSync(graphPagePath, "utf8")

    expect(source).toContain('from "@xyflow/react"')
    expect(source).toContain("<ReactFlow")
    expect(source).not.toContain("<svg")
    expect(source).not.toContain("<line")
  })

  it("uses a compact graph viewport", () => {
    const source = readFileSync(graphPagePath, "utf8")

    expect(source).toContain("width: 196")
    expect(source).toContain("height: 124")
    expect(source).not.toContain("width: 392")
    expect(source).not.toContain("height: 248")
    expect(source).not.toContain("width: 490")
    expect(source).not.toContain("height: 310")
    expect(source).not.toContain("width: 980")
    expect(source).not.toContain("height: 620")
  })

  it("uses fixed graph entity types for compact legends and controls", () => {
    const source = readFileSync(graphPagePath, "utf8")
    const graphApiSource = readFileSync(graphApiPath, "utf8")

    expect(source).toContain("function getGraphEntityTypes")
    expect(source).toContain("node.entityType")
    expect(source).toContain("const GRAPH_ENTITY_LEGEND_TYPES")
    expect(source).toContain("GraphEntityType")
    expect(source).toContain("Object.values(GraphEntityType)")
    expect(graphApiSource).toContain('Person: "PERSON"')
    expect(graphApiSource).toContain('Organization: "ORGANIZATION"')
    expect(graphApiSource).toContain('Place: "PLACE"')
    expect(graphApiSource).toContain('Object: "OBJECT"')
    expect(graphApiSource).toContain('Concept: "CONCEPT"')
    expect(graphApiSource).toContain('Other: "OTHER"')
    expect(graphApiSource).toContain('Special: "SPECIAL"')
    expect(source).toContain('data-testid="memory-graph-legend"')
    expect(source).toContain('data-testid="memory-graph-legend-item"')
    expect(source).toContain('data-testid="memory-graph-toolbar"')
    expect(source).toContain("h-7 w-40")
    expect(source).toContain("h-7 items-center")
    expect(source).toContain("w-12")

    expect(source).not.toContain("Person\\n")
    expect(source).not.toContain("Org\\n")
    expect(source).not.toContain("h-9 w-48")
    expect(source).not.toContain("md:w-56")
    expect(source).not.toContain("w-16")
    expect(source).not.toContain("gap-4 rounded-lg")
    expect(source).not.toContain("px-4 py-2.5")
    expect(source).not.toContain("<GraphLegend entityTypes={entityTypes}")
    expect(source).not.toContain('["PERSON", "ORGANIZATION"]')
  })
})
