import type * as React from "react"
import type { LucideIcon } from "lucide-react"

import { Badge } from "@/components/ui/badge"
import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import {
  Pagination,
  PaginationContent,
  PaginationItem,
  PaginationNext,
  PaginationPrevious,
} from "@/components/ui/pagination"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { cn } from "@/lib/utils"

export type Tone = "default" | "success" | "warning" | "danger"

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
    <div className="mb-10 flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
      <div>
        {eyebrow ? (
          <p className="mb-2 text-muted-foreground">{eyebrow}</p>
        ) : null}
        <h1 className="text-3xl font-semibold tracking-tight">{title}</h1>
        <p className="mt-2 text-muted-foreground">{description}</p>
      </div>
      {action ? <div className="mt-1">{action}</div> : null}
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
    <Badge className="h-7 gap-1.5 px-2.5" variant="outline">
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
    <Card>
      <CardHeader>
        <div className="min-w-0">
          <CardDescription>{label}</CardDescription>
          <CardTitle className="mt-3 text-3xl font-semibold">
            {value}
          </CardTitle>
        </div>
        {Icon ? (
          <CardAction>
            <Icon className="size-4 text-muted-foreground" />
          </CardAction>
        ) : null}
      </CardHeader>
      <CardContent>
        <div className="flex items-center justify-between gap-3">
          <span className="text-muted-foreground">{detail}</span>
          {trend ? (
            <Badge variant={toneVariant(tone)}>{trend}</Badge>
          ) : null}
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
  return (
    <Badge className={cn("capitalize", className)} variant={toneVariant(tone)}>
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
    <Card className={className}>
      {title || description || action ? (
        <CardHeader>
          <div>
            {title ? <CardTitle>{title}</CardTitle> : null}
            {description ? (
              <CardDescription>{description}</CardDescription>
            ) : null}
          </div>
          {action ? <CardAction>{action}</CardAction> : null}
        </CardHeader>
      ) : null}
      <CardContent className={contentClassName}>{children}</CardContent>
    </Card>
  )
}

export function PagePagination({ label }: { label: string }) {
  return (
    <div className="flex flex-col gap-3 px-4 py-3 text-muted-foreground md:flex-row md:items-center md:justify-between">
      <span>{label}</span>
      <Pagination className="mx-0 w-auto justify-start md:justify-end">
        <PaginationContent>
          <PaginationItem>
            <PaginationPrevious href="#" />
          </PaginationItem>
          <PaginationItem>
            <PaginationNext href="#" />
          </PaginationItem>
        </PaginationContent>
      </Pagination>
    </div>
  )
}

export function FilterSelect({
  "aria-label": ariaLabel,
  defaultValue,
  items,
  className,
  size,
}: {
  "aria-label": string
  defaultValue: string
  items: Array<{ value: string; label: string }>
  className?: string
  size?: "sm" | "default"
}) {
  return (
    <Select defaultValue={defaultValue}>
      <SelectTrigger aria-label={ariaLabel} className={className} size={size}>
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        <SelectGroup>
          {items.map((item) => (
            <SelectItem key={item.value} value={item.value}>
              {item.label}
            </SelectItem>
          ))}
        </SelectGroup>
      </SelectContent>
    </Select>
  )
}
