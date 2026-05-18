import type * as React from "react"
import { type HTMLMotionProps, motion } from "motion/react"

const sidebarMotion: Pick<
  HTMLMotionProps<"div">,
  "animate" | "exit" | "initial" | "transition"
> = {
  initial: { opacity: 0, x: -16 },
  animate: { opacity: 1, x: 0 },
  exit: { opacity: 0, x: -12 },
  transition: { duration: 0.22, ease: [0, 0, 0.2, 1] },
}

const contentMotion: Pick<
  HTMLMotionProps<"div">,
  "animate" | "exit" | "initial" | "transition"
> = {
  initial: { opacity: 0, y: 8 },
  animate: { opacity: 1, y: 0 },
  exit: { opacity: 0, y: -4 },
  transition: { duration: 0.22, ease: [0, 0, 0.2, 1] },
}

export function AnimatedSidebar({
  className,
  children,
}: {
  className?: string
  children: React.ReactNode
}) {
  return (
    <motion.div className={className} {...sidebarMotion}>
      {children}
    </motion.div>
  )
}

export function AnimatedContent({
  className,
  children,
  motionKey,
}: {
  className?: string
  children: React.ReactNode
  motionKey?: string
}) {
  return (
    <motion.div
      key={motionKey}
      className={className}
      data-testid={motionKey ? `animated-content-${motionKey}` : undefined}
      {...contentMotion}
    >
      {children}
    </motion.div>
  )
}
