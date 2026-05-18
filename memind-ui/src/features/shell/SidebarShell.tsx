import type * as React from "react"

import {
  Sidebar,
  SidebarFooter,
  SidebarInset,
  SidebarProvider,
  SidebarRail,
} from "@/components/ui/sidebar"
import { AnimatedContent, AnimatedSidebar } from "@/features/shared/route-motion"

type SidebarShellProps = {
  sidebar: React.ReactNode
  children: React.ReactNode
  contentKey?: string
}

export function SidebarShell({
  sidebar,
  children,
  contentKey,
}: SidebarShellProps) {
  return (
    <SidebarProvider className="h-svh min-h-0 overflow-hidden">
      <Sidebar className="h-svh min-h-0" collapsible="none">
        <AnimatedSidebar className="flex h-full min-h-0 flex-col">
          {sidebar}
        </AnimatedSidebar>
        <SidebarRail />
      </Sidebar>

      <SidebarInset className="h-svh min-h-0 overflow-y-auto">
        <AnimatedContent motionKey={contentKey}>{children}</AnimatedContent>
      </SidebarInset>
    </SidebarProvider>
  )
}

export { SidebarFooter }
