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
import { join } from "node:path"

import { describe, expect, it } from "vitest"

const featureFiles = [
  "src/features/memories/Memories.tsx",
  "src/features/analytics/Analytics.tsx",
  "src/features/settings/Settings.tsx",
  "src/features/shared/ui.tsx",
  "src/features/memories/memory-dashboard/pages/GraphPage.tsx",
  "src/features/memories/memory-dashboard/pages/ItemsPage.tsx",
  "src/features/memories/memory-dashboard/pages/RawDataPage.tsx",
]

describe("feature select usage", () => {
  it("uses the shadcn web Select component instead of NativeSelect", () => {
    const retiredSelectWrapper = "Filter" + "Select"

    for (const file of featureFiles) {
      const source = readFileSync(join(process.cwd(), file), "utf8")

      expect(source).not.toContain("@/components/ui/native-select")
      expect(source).not.toContain("NativeSelect")
      expect(source).not.toContain(retiredSelectWrapper)
    }

    const filesWithSelects = [
      "src/features/memories/Memories.tsx",
      "src/features/settings/Settings.tsx",
      "src/features/memories/memory-dashboard/pages/GraphPage.tsx",
    ]

    for (const file of filesWithSelects) {
      const source = readFileSync(join(process.cwd(), file), "utf8")

      expect(source).toContain("@/components/ui/select")
      expect(source).toContain("<Select")
      expect(source).toContain("<SelectTrigger")
      expect(source).toContain("<SelectContent")
      expect(source).toContain("<SelectGroup")
      expect(source).toContain("<SelectItem")
    }
  })
})
