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
  ArrowLeft,
  Bot,
  Boxes,
  Copy,
  Database,
  GitFork,
  LayoutDashboard,
  Lightbulb,
  List,
  MessageSquare,
} from "lucide-react"

import { Button } from "@/components/ui/button"
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
import { cn } from "@/lib/utils"

import type { MemoryDashboardData } from "../dashboard/memory-dashboard-data"
import type { MemoryWorkspacePage } from "../dashboard/types"

const navItems = [
  { id: "overview", label: "Overview", icon: LayoutDashboard, enabled: true },
  { id: "raw-data", label: "Raw Data", icon: Database, enabled: true },
  { id: "items", label: "Items", icon: List, enabled: true },
  { id: "graph", label: "Graph", icon: GitFork, enabled: true },
  { id: "threads", label: "Threads", icon: MessageSquare, enabled: true },
  { id: "insights", label: "Insights", icon: Lightbulb, enabled: true },
  { id: "buffers", label: "Buffers", icon: Boxes, enabled: true },
] satisfies Array<{
  id: string
  label: string
  icon: typeof LayoutDashboard
  enabled: boolean
}>

function IdentityValue({ value }: { value: string }) {
  function handleCopy() {
    if (navigator.clipboard) {
      void navigator.clipboard.writeText(value)
    }
  }

  return (
    <div className="group/identity-value flex min-w-0 items-center gap-1">
      <span className="min-w-0 flex-1 truncate font-mono text-[11px] text-muted-foreground">
        {value}
      </span>
      <Button
        aria-label={`Copy ${value}`}
        className="opacity-0 transition-opacity group-hover/identity-value:opacity-100 focus-visible:opacity-100"
        onClick={handleCopy}
        size="icon-xs"
        type="button"
        variant="ghost"
      >
        <Copy />
      </Button>
    </div>
  )
}

function MemoryBrandMark({ data }: { data: MemoryDashboardData }) {
  return (
    <div className="flex items-center gap-3 rounded-lg px-2 py-1.5">
      <div className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-sidebar-primary text-sidebar-primary-foreground">
        <Bot />
      </div>
      <div className="flex min-w-0 flex-1 flex-col justify-center">
        <IdentityValue value={data.identity.runtimeId} />
        <IdentityValue value={data.identity.agent} />
      </div>
    </div>
  )
}

export function WorkspaceSidebar({
  activePage,
  data,
  onBack,
  onPageChange,
}: {
  activePage: MemoryWorkspacePage
  data: MemoryDashboardData
  onBack: () => void
  onPageChange?: (page: MemoryWorkspacePage) => void
}) {
  return (
    <>
      <SidebarHeader className="gap-5 px-4 pt-4 pb-3">
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton
              className="h-9 cursor-pointer rounded-lg text-[13px]"
              onClick={onBack}
              type="button"
            >
              <ArrowLeft />
              <span>Back to Console</span>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
        <MemoryBrandMark data={data} />
      </SidebarHeader>

      <SidebarContent>
        <SidebarGroup className="px-3">
          <SidebarGroupLabel className="px-2 font-medium tracking-[0.08em] text-sidebar-foreground/55 uppercase">
            Memory Workspace
          </SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu className="gap-1">
              {navItems.map((item) => (
                <SidebarMenuItem key={item.label}>
                  <SidebarMenuButton
                    aria-current={item.id === activePage ? "page" : undefined}
                    className={cn(
                      "h-9 rounded-lg text-[13px] transition-colors",
                      item.enabled && "cursor-pointer",
                      item.id === activePage &&
                        "bg-sidebar-accent text-sidebar-accent-foreground",
                      !item.enabled && "cursor-not-allowed opacity-55"
                    )}
                    disabled={!item.enabled}
                    isActive={item.id === activePage}
                    onClick={() => {
                      if (item.enabled) {
                        onPageChange?.(item.id as MemoryWorkspacePage)
                      }
                    }}
                    type="button"
                  >
                    <item.icon />
                    <span>{item.label}</span>
                  </SidebarMenuButton>
                </SidebarMenuItem>
              ))}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>
    </>
  )
}
