import type * as React from "react"

import {
  Sidebar,
  SidebarFooter,
  SidebarInset,
  SidebarProvider,
  SidebarRail,
} from "@/components/ui/sidebar"
import {
  AnimatedContent,
  AnimatedSidebar,
} from "@/features/shared/route-motion"
import { cn } from "@/lib/utils"

type SidebarShellProps = {
  sidebar: React.ReactNode
  children: React.ReactNode
  contentKey?: string
  contentClassName?: string
  contentOverlay?: React.ReactNode
  contentSurfaceClassName?: string
}

export function SidebarShell({
  sidebar,
  children,
  contentKey,
  contentClassName,
  contentOverlay,
  contentSurfaceClassName,
}: SidebarShellProps) {
  return (
    <SidebarProvider className="h-svh min-h-0 overflow-hidden bg-background">
      <Sidebar
        className="h-svh min-h-0 border-r border-sidebar-border/80"
        collapsible="none"
      >
        <AnimatedSidebar className="flex h-full min-h-0 flex-col">
          {sidebar}
        </AnimatedSidebar>
        <SidebarRail />
      </Sidebar>

      <SidebarInset
        className={cn(
          "relative h-svh min-h-0 overflow-y-auto bg-background",
          contentOverlay && "overflow-hidden",
          contentClassName
        )}
        data-testid={contentOverlay ? "planning-content-inset" : undefined}
      >
        <AnimatedContent
          className={cn("min-h-full", contentSurfaceClassName)}
          motionKey={contentKey}
        >
          <div
            className={cn(
              "mx-auto min-h-full w-full max-w-[1500px]",
              contentSurfaceClassName
            )}
            data-testid="console-content-surface"
          >
            {children}
          </div>
        </AnimatedContent>
        {contentOverlay}
      </SidebarInset>
    </SidebarProvider>
  )
}

export { SidebarFooter }
