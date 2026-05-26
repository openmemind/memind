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

import {
  BarChart3,
  Brain,
  KeyRound,
  LayoutDashboard,
  Settings,
} from "lucide-react"

import {
  SidebarContent,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from "@/components/ui/sidebar"
import { SidebarShell } from "@/features/shell/SidebarShell"
import { PlanningOverlay } from "@/features/shared/ui"
import { cn } from "@/lib/utils"
import * as React from "react"

import logo from "@/assets/logo.png"

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
      <img src={logo} alt="logo" className="size-10" />
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
  const planningOverlay =
    activePage === "analytics" || activePage === "api-keys" ? (
      <PlanningOverlay data-testid={`${activePage}-planning-overlay`} />
    ) : null

  return (
    <SidebarShell
      contentOverlay={planningOverlay}
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
        </>
      }
    >
      {children}
    </SidebarShell>
  )
}
