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
