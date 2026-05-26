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

import type * as React from "react"
import { motion } from "motion/react"

import { GithubButton } from "@/components/Github.tsx"
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
        <SidebarFooter>
          <motion.div
            animate={{ opacity: 1, scale: 1, y: 0 }}
            className="mx-auto mb-40"
            data-animation="fade-rise"
            data-testid="sidebar-github-button-animation"
            initial={{ opacity: 0, scale: 0.96, y: 10 }}
            transition={{ delay: 0.08, duration: 0.28, ease: [0, 0, 0.2, 1] }}
          >
            <GithubButton />
          </motion.div>
        </SidebarFooter>
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
              "mx-auto min-h-full w-full max-w-375",
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
