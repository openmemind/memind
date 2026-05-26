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

const buffersPagePath = resolve(
  process.cwd(),
  "src/features/memories/pages/BuffersPage/BuffersPage.tsx"
)

describe("BuffersPage structure", () => {
  it("uses the shared paginated table and Threads-style tabs for buffer switching", () => {
    const source = readFileSync(buffersPagePath, "utf8")

    expect(source).toContain("PaginatedTable")
    expect(source).toContain("Tabs")
    expect(source).toContain("TabsList")
    expect(source).toContain("TabsTrigger")
    expect(source).toContain('value="conversation"')
    expect(source).toContain('value="insight"')
    expect(source).not.toContain('value="all"')
    expect(source).not.toContain("All")
    expect(source).toContain("Conversation")
    expect(source).toContain("Insight")
  })
})
