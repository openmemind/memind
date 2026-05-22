import type * as React from "react"
import { ClipboardList, Clock3, type LucideIcon } from "lucide-react"

import { Badge } from "@/components/ui/badge"
import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { cn } from "@/lib/utils"

export type Tone = "default" | "success" | "warning" | "danger"

export function PageSurface({
  children,
  className,
  ...props
}: React.ComponentProps<"main">) {
  return (
    <main
      className={cn(
        "flex min-h-full flex-col px-4 py-6 sm:px-6 lg:px-10 lg:py-8",
        className
      )}
      {...props}
    >
      {children}
    </main>
  )
}

function toneVariant(tone: Tone) {
  if (tone === "danger") {
    return "destructive"
  }

  if (tone === "success" || tone === "warning") {
    return "outline"
  }

  return "secondary"
}

export function PageHeader({
  title,
  description,
  eyebrow,
  action,
}: {
  title: string
  description: string
  eyebrow?: string
  action?: React.ReactNode
}) {
  return (
    <div className="mb-6 flex flex-col gap-4 border-b border-border/70 pb-6 md:flex-row md:items-end md:justify-between">
      <div className="max-w-3xl min-w-0">
        {eyebrow ? (
          <p className="mb-2 text-xs font-medium tracking-[0.1em] text-primary uppercase">
            {eyebrow}
          </p>
        ) : null}
        <h1 className="text-2xl font-semibold tracking-tight md:text-3xl">
          {title}
        </h1>
        <p className="mt-2 max-w-2xl text-sm text-muted-foreground">
          {description}
        </p>
      </div>
      {action ? <div className="shrink-0">{action}</div> : null}
    </div>
  )
}

export function ActionBadge({
  icon: Icon,
  children,
}: {
  icon?: LucideIcon
  children: React.ReactNode
}) {
  return (
    <Badge className="h-7 gap-1.5 px-2.5 text-xs" variant="outline">
      {Icon ? <Icon /> : null}
      {children}
    </Badge>
  )
}

export function MetricCard({
  label,
  value,
  detail,
  trend,
  tone = "default",
  icon: Icon,
}: {
  label: string
  value: string
  detail: string
  trend?: string
  tone?: Tone
  icon?: LucideIcon
}) {
  return (
    <Card className="transition-colors hover:ring-primary/25">
      <CardHeader>
        <div className="min-w-0">
          <CardDescription className="font-medium tracking-[0.05em] uppercase">
            {label}
          </CardDescription>
          <CardTitle className="mt-3 text-2xl font-semibold tabular-nums md:text-3xl">
            {value}
          </CardTitle>
        </div>
        {Icon ? (
          <CardAction>
            <div className="flex size-8 items-center justify-center rounded-md bg-primary/10 text-primary">
              <Icon />
            </div>
          </CardAction>
        ) : null}
      </CardHeader>
      <CardContent>
        <div className="flex items-center justify-between gap-3 text-xs">
          <span className="text-muted-foreground">{detail}</span>
          {trend ? <Badge variant={toneVariant(tone)}>{trend}</Badge> : null}
        </div>
      </CardContent>
    </Card>
  )
}

export function StatusBadge({
  label,
  tone = "default",
  className,
}: {
  label: string
  tone?: Tone
  className?: string
}) {
  const toneClassName = {
    default: "bg-muted text-muted-foreground ring-border",
    success: "bg-muted text-foreground ring-border",
    warning: "bg-secondary text-secondary-foreground ring-border",
    danger: "",
  }[tone]

  return (
    <Badge
      className={cn("capitalize ring-1", toneClassName, className)}
      variant={toneVariant(tone)}
    >
      {label}
    </Badge>
  )
}

export function Panel({
  title,
  description,
  action,
  className,
  contentClassName,
  children,
}: {
  title?: string
  description?: string
  action?: React.ReactNode
  className?: string
  contentClassName?: string
  children: React.ReactNode
}) {
  return (
    <Card className={cn("bg-card/95", className)}>
      {title || description || action ? (
        <CardHeader className="border-b border-border/70 pb-4">
          <div>
            {title ? <CardTitle>{title}</CardTitle> : null}
            {description ? (
              <CardDescription>{description}</CardDescription>
            ) : null}
          </div>
          {action ? <CardAction>{action}</CardAction> : null}
        </CardHeader>
      ) : null}
      <CardContent className={cn("pt-0", contentClassName)}>
        {children}
      </CardContent>
    </Card>
  )
}

export function PlanningOverlay({
  "data-testid": dataTestId,
}: {
  "data-testid": string
}) {
  return (
    <div
      aria-label="Planning"
      className="absolute inset-0 z-20 flex items-center justify-center bg-background/70 p-4 backdrop-blur-md"
      data-testid={dataTestId}
    >
      <div className="flex w-full max-w-sm flex-col items-center gap-4 rounded-lg border bg-card/95 px-6 py-7 text-center shadow-sm">
        <div
          aria-hidden="true"
          className="relative flex size-12 items-center justify-center rounded-lg border bg-primary/10 text-primary"
        >
          <ClipboardList />
          <span className="absolute -right-1 -bottom-1 flex size-5 items-center justify-center rounded-md border bg-card text-primary shadow-sm">
            <Clock3 />
          </span>
        </div>
        <div className="flex flex-col gap-1.5">
          <p className="text-xs font-medium tracking-[0.18em] text-muted-foreground uppercase">
            Roadmap in progress
          </p>
          <p className="text-2xl font-semibold tracking-tight">Planning</p>
          <p className="text-sm text-muted-foreground">
            This workspace is being shaped and will be available soon.
          </p>
        </div>
      </div>
    </div>
  )
}
