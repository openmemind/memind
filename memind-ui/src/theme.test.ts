import { readFileSync } from "node:fs"

describe("theme", () => {
  it("uses a neutral monochrome palette instead of green or teal", () => {
    const css = readFileSync("src/index.css", "utf8")

    expect(css).toContain("--background: oklch(1 0 0);")
    expect(css).toContain("--primary: oklch(0.205 0 0);")
    expect(css).toContain("--sidebar-primary: oklch(0.205 0 0);")
    expect(css).toContain("--chart-1: oklch(0.205 0 0);")
    expect(css).not.toMatch(/oklch\([^)]*\s18[0-9](?:\s|\))/)
  })

  it("keeps the main app background flat white", () => {
    const sidebarShell = readFileSync(
      "src/features/shell/SidebarShell.tsx",
      "utf8"
    )

    expect(sidebarShell).toContain("overflow-y-auto bg-background")
    expect(sidebarShell).not.toContain("bg-[radial-gradient")
    expect(sidebarShell).not.toContain("linear-gradient")
  })

  it("keeps selected navigation simple without inset edge bars", () => {
    const appShell = readFileSync("src/features/shell/AppShell.tsx", "utf8")
    const settings = readFileSync("src/features/settings/Settings.tsx", "utf8")

    expect(appShell).not.toContain("shadow-[inset_3px_0_0")
    expect(settings).not.toContain("shadow-[inset_3px_0_0")
  })
})
