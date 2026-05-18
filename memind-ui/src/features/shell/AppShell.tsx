import {
  BarChart3,
  Brain,
  CircleDot,
  KeyRound,
  LayoutDashboard,
  Settings,
} from "lucide-react"

import { Badge } from "@/components/ui/badge"
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
    <div className="flex items-center gap-3 px-2 py-1">
      <div className="flex size-9 items-center justify-center rounded-md bg-sidebar-primary text-sidebar-primary-foreground">
        <Brain />
      </div>
      <div className="min-w-0">
        <div className="text-sm font-semibold">Memind</div>
        <div className="text-xs text-muted-foreground">Memory runtime</div>
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
          <SidebarHeader className="gap-6 p-4">
            <BrandMark />
          </SidebarHeader>

          <SidebarContent>
            <SidebarGroup>
              <SidebarGroupLabel>Workspace</SidebarGroupLabel>
              <SidebarGroupContent>
                <SidebarMenu>
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
            <SidebarSeparator />
            <div className="flex flex-col gap-2 rounded-lg bg-background/70 p-3 ring-1 ring-sidebar-border">
              <div className="flex items-center justify-between gap-2">
                <div className="flex items-center gap-2 text-xs font-medium">
                  <CircleDot className="text-primary" />
                  Local runtime
                </div>
                <Badge variant="outline">Live</Badge>
              </div>
              <div className="font-mono text-[11px] text-muted-foreground">
                admin/v1 connected
              </div>
            </div>
          </SidebarFooter>
        </>
      }
    >
      {children}
    </SidebarShell>
  )
}
