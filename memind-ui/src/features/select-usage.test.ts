import { readFileSync } from "node:fs"
import { join } from "node:path"

import { describe, expect, it } from "vitest"

const featureFiles = [
  "src/features/memories/Memories.tsx",
  "src/features/analytics/Analytics.tsx",
  "src/features/settings/Settings.tsx",
  "src/features/shared/ui.tsx",
]

describe("feature select usage", () => {
  it("uses the shadcn web Select component instead of NativeSelect", () => {
    for (const file of featureFiles) {
      const source = readFileSync(join(process.cwd(), file), "utf8")

      expect(source).not.toContain("@/components/ui/native-select")
      expect(source).not.toContain("NativeSelect")
    }

    const sharedUi = readFileSync(
      join(process.cwd(), "src/features/shared/ui.tsx"),
      "utf8"
    )

    expect(sharedUi).toContain("@/components/ui/select")
  })
})
