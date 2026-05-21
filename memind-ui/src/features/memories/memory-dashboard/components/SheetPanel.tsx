import { Dialog as SheetPrimitive } from "@base-ui/react/dialog"
import type * as React from "react"

import { cn } from "@/lib/utils"

export function SheetPanel({
  children,
  className,
}: {
  children: React.ReactNode
  className?: string
}) {
  return (
    <SheetPrimitive.Portal>
      <SheetPrimitive.Popup
        data-testid="record-details-sheet"
        className={cn(
          "fixed inset-y-0 right-0 z-50 flex h-full w-3/4 flex-col border-l bg-popover bg-clip-padding text-xs/relaxed text-popover-foreground shadow-lg transition duration-200 ease-in-out data-ending-style:translate-x-full data-ending-style:opacity-0 data-starting-style:translate-x-full data-starting-style:opacity-0 sm:max-w-md",
          className
        )}
      >
        {children}
      </SheetPrimitive.Popup>
    </SheetPrimitive.Portal>
  )
}
