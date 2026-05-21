import {
  BarChart3,
  Brain,
  GitBranch,
  KeyRound,
  LayoutDashboard,
  Settings,
} from "lucide-react"

import {
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarSeparator,
} from "@/components/ui/sidebar"
import { SidebarShell } from "@/features/shell/SidebarShell"
import { cn } from "@/lib/utils"

export type AppPage =
  | "dashboard"
  | "memories"
  | "analytics"
  | "api-keys"
  | "settings"

type AppShellProps = {
  activePage: AppPage
  onPageChange: (page: AppPage) => void
  children: React.ReactNode
}

const navItems = [
  { id: "dashboard" as const, label: "Dashboard", icon: LayoutDashboard },
  { id: "memories" as const, label: "Memories", icon: Brain },
  { id: "analytics" as const, label: "Analytics", icon: BarChart3 },
  { id: "api-keys" as const, label: "API Keys", icon: KeyRound },
  { id: "settings" as const, label: "Settings", icon: Settings },
]

function BrandMark() {
  return (
    <div
      aria-label="Memind console"
      className="flex items-center gap-3 rounded-lg px-2 py-1.5"
      role="img"
    >
      <div className="flex size-10 items-center justify-center rounded-lg bg-sidebar-primary text-sidebar-primary-foreground ring-1 ring-sidebar-primary/20">
        <Brain />
      </div>
      <div className="min-w-0">
        <div className="text-sm font-semibold tracking-tight">Memind</div>
        <div className="text-xs text-sidebar-foreground/65">Memory runtime</div>
      </div>
    </div>
  )
}

export function AppShell({
  activePage,
  onPageChange,
  children,
}: AppShellProps) {
  return (
    <SidebarShell
      contentKey={activePage}
      sidebar={
        <>
          <SidebarHeader className="gap-5 px-4 pt-4 pb-3">
            <BrandMark />
          </SidebarHeader>

          <SidebarContent>
            <SidebarGroup className="px-3">
              <SidebarGroupLabel className="px-2 font-medium tracking-[0.08em] text-sidebar-foreground/55 uppercase">
                Workspace
              </SidebarGroupLabel>
              <SidebarGroupContent>
                <SidebarMenu className="gap-1">
                  {navItems.map((item) => {
                    const isActive = item.id === activePage
                    const isSwitchable =
                      item.id === "dashboard" ||
                      item.id === "memories" ||
                      item.id === "analytics" ||
                      item.id === "api-keys" ||
                      item.id === "settings"

                    return (
                      <SidebarMenuItem key={item.id}>
                        <SidebarMenuButton
                          aria-current={isActive ? "page" : undefined}
                          aria-disabled={!isSwitchable}
                          disabled={!isSwitchable}
                          isActive={isActive}
                          onClick={() => {
                            if (isSwitchable) {
                              onPageChange(item.id)
                            }
                          }}
                          type="button"
                          className={cn(
                            "h-9 cursor-pointer rounded-lg text-[13px] transition-colors",
                            isActive &&
                              "bg-sidebar-accent text-sidebar-accent-foreground",
                            !isSwitchable && "cursor-not-allowed opacity-55"
                          )}
                        >
                          <item.icon />
                          <span>{item.label}</span>
                        </SidebarMenuButton>
                      </SidebarMenuItem>
                    )
                  })}
                </SidebarMenu>
              </SidebarGroupContent>
            </SidebarGroup>
          </SidebarContent>

          <SidebarFooter className="p-4">
            <SidebarSeparator className="mb-2" />
            <SidebarMenu>
              <SidebarMenuItem>
                <SidebarMenuButton
                  aria-label="GitHub"
                  className="h-9 cursor-pointer rounded-lg text-[13px]"
                  type="button"
                >
                  <GitBranch />
                </SidebarMenuButton>
              </SidebarMenuItem>
            </SidebarMenu>
          </SidebarFooter>
        </>
      }
    >
      {children}
    </SidebarShell>
  )
}
