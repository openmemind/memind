import {
  ChevronRight,
  Clock3,
  ExternalLink,
  FileClock,
  ListTree,
  MoreHorizontal,
  RefreshCcw,
  Search,
  Sparkles,
  Tags,
} from "lucide-react"
import { useMemo, useState } from "react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardAction,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import {
  Empty,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from "@/components/ui/empty"
import {
  InputGroup,
  InputGroupAddon,
  InputGroupInput,
} from "@/components/ui/input-group"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { cn } from "@/lib/utils"

import type {
  MemoryDashboardData,
  MemoryThreadStoryline,
  MemoryThreadTimelineItem,
} from "../memory-dashboard-data"

type ThreadFilter = "all" | "active" | "dormant"

function threadStatusVariant(status: MemoryThreadStoryline["status"]) {
  return status === "ACTIVE"
    ? "outline"
    : status === "CLOSED"
      ? "secondary"
      : "ghost"
}

function statusDotClassName(status: MemoryThreadStoryline["status"]) {
  return status === "ACTIVE" ? "bg-primary" : "bg-muted-foreground/45"
}

function ThreadsHeader({
  projection,
}: {
  projection: MemoryDashboardData["threads"]["projection"]
}) {
  return (
    <header className="flex shrink-0 flex-col gap-4 border-b bg-background px-4 py-4 md:flex-row md:items-end md:justify-between lg:px-8">
      <div className="min-w-0">
        <h1 className="text-2xl font-semibold tracking-tight">
          Memory Threads
        </h1>
        <p className="mt-1 max-w-2xl text-sm text-muted-foreground">
          Manage storyline continuity and item memberships across your memory
          graph.
        </p>
      </div>
      <div className="flex flex-wrap items-center gap-2">
        <Badge className="h-7 gap-1.5 px-2.5" variant="outline">
          <FileClock data-icon="inline-start" />
          {projection.status}
        </Badge>
        <Button type="button" variant="outline">
          <RefreshCcw data-icon="inline-start" />
          Refresh
        </Button>
        <Button type="button">
          <Sparkles data-icon="inline-start" />
          Rebuild Projections
        </Button>
      </div>
    </header>
  )
}

function ThreadStorylineButton({
  isSelected,
  onSelect,
  storyline,
}: {
  isSelected: boolean
  onSelect: () => void
  storyline: MemoryThreadStoryline
}) {
  return (
    <button
      aria-pressed={isSelected}
      className={cn(
        "relative w-full cursor-pointer border-b px-4 py-4 text-left transition-colors hover:bg-muted/45 focus-visible:ring-2 focus-visible:ring-ring/30 focus-visible:outline-none",
        isSelected && "bg-muted/50"
      )}
      onClick={onSelect}
      type="button"
    >
      {isSelected ? (
        <span className="absolute inset-y-0 left-0 w-1 bg-primary" />
      ) : null}
      <div className="flex items-start justify-between gap-3">
        <h3 className="text-sm leading-snug font-semibold">
          {storyline.title}
        </h3>
        <span
          className={cn(
            "mt-1 size-1.5 shrink-0 rounded-full",
            statusDotClassName(storyline.status)
          )}
        />
      </div>
      <p className="mt-1.5 line-clamp-2 text-xs leading-relaxed text-muted-foreground">
        {storyline.description}
      </p>
      <div className="mt-3 flex flex-wrap items-center gap-2 text-[0.625rem] font-medium text-muted-foreground">
        <span className="flex items-center gap-1">
          <Tags />
          {storyline.category}
        </span>
        <span className="size-1 rounded-full bg-border" />
        <span>{storyline.memberCount} mem</span>
        <span className="size-1 rounded-full bg-border" />
        <span>{storyline.lastEvent}</span>
      </div>
    </button>
  )
}

function ThreadList({
  filter,
  onFilterChange,
  onQueryChange,
  onSelectThread,
  query,
  selectedThreadKey,
  storylines,
}: {
  filter: ThreadFilter
  onFilterChange: (filter: ThreadFilter) => void
  onQueryChange: (query: string) => void
  onSelectThread: (threadKey: string) => void
  query: string
  selectedThreadKey: string
  storylines: MemoryThreadStoryline[]
}) {
  return (
    <aside
      className="thread-sidebar flex min-h-130 flex-col border-b bg-background lg:min-h-0 lg:w-80 lg:shrink-0 lg:border-r lg:border-b-0"
      data-testid="thread-sidebar"
    >
      <div className="flex flex-col gap-3 border-b p-4">
        <InputGroup className="h-9 bg-muted/40">
          <InputGroupAddon>
            <Search />
          </InputGroupAddon>
          <InputGroupInput
            aria-label="Filter threads"
            onChange={(event) => onQueryChange(event.target.value)}
            placeholder="Filter threads..."
            type="search"
            value={query}
          />
        </InputGroup>

        <Tabs
          className="w-full"
          onValueChange={(value) => onFilterChange(value as ThreadFilter)}
          value={filter}
        >
          <TabsList
            aria-label="Filter thread status"
            className="w-full rounded-md border bg-muted p-1"
          >
            <TabsTrigger className="flex-1 border-0 shadow-none" value="all">
              All
            </TabsTrigger>
            <TabsTrigger className="flex-1 border-0 shadow-none" value="active">
              Active
            </TabsTrigger>
            <TabsTrigger
              className="flex-1 border-0 shadow-none"
              value="dormant"
            >
              Dormant
            </TabsTrigger>
          </TabsList>
        </Tabs>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto">
        {storylines.length > 0 ? (
          storylines.map((storyline) => (
            <ThreadStorylineButton
              key={storyline.key}
              isSelected={storyline.key === selectedThreadKey}
              onSelect={() => onSelectThread(storyline.key)}
              storyline={storyline}
            />
          ))
        ) : (
          <Empty className="min-h-72 border-0">
            <EmptyHeader>
              <EmptyMedia variant="icon">
                <Search />
              </EmptyMedia>
              <EmptyTitle>No threads found</EmptyTitle>
              <EmptyDescription>
                Adjust the filter or search term to inspect another storyline.
              </EmptyDescription>
            </EmptyHeader>
          </Empty>
        )}
      </div>
    </aside>
  )
}

function ThreadActions({
  thread,
}: {
  thread: MemoryThreadStoryline["detail"]
}) {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={
          <Button
            aria-label={`Actions for ${thread.title}`}
            size="icon"
            type="button"
            variant="ghost"
          />
        }
      >
        <MoreHorizontal />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-44">
        <DropdownMenuGroup>
          <DropdownMenuLabel>Thread actions</DropdownMenuLabel>
          <DropdownMenuItem>
            <ExternalLink />
            Open thread
          </DropdownMenuItem>
          <DropdownMenuItem>
            <Clock3 />
            Audit memberships
          </DropdownMenuItem>
        </DropdownMenuGroup>
        <DropdownMenuSeparator />
        <DropdownMenuItem>
          <RefreshCcw />
          Rebuild projection
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

function NarrativeSnapshot({
  thread,
}: {
  thread: MemoryThreadStoryline["detail"]
}) {
  return (
    <Card className="bg-muted/25" size="sm">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-xs font-semibold tracking-[0.08em] uppercase">
          <Sparkles />
          Narrative Snapshot
        </CardTitle>
      </CardHeader>
      <CardContent>
        <p className="text-sm leading-relaxed text-muted-foreground">
          {thread.narrative}
        </p>
      </CardContent>
    </Card>
  )
}

function TimelineItem({
  isPrimary,
  item,
}: {
  isPrimary: boolean
  item: MemoryThreadTimelineItem
}) {
  return (
    <div className="relative pb-8 pl-10 last:pb-2">
      <span
        className={cn(
          "absolute top-1 left-0 flex size-4 items-center justify-center rounded-full border bg-background ring-4 ring-background",
          isPrimary && "border-primary"
        )}
      >
        <span
          className={cn(
            "size-2 rounded-full bg-muted-foreground/45",
            isPrimary && "bg-primary"
          )}
        />
      </span>

      <Card className="transition-shadow hover:shadow-sm" size="sm">
        <CardHeader>
          <div className="flex min-w-0 flex-wrap items-center gap-2">
            <code className="font-mono text-[0.6875rem] font-semibold">
              {item.id}
            </code>
            <Badge variant={isPrimary ? "default" : "outline"}>
              {item.role}
            </Badge>
          </div>
          <CardAction>
            <time className="font-mono text-[0.6875rem] text-muted-foreground">
              {item.timestamp}
            </time>
          </CardAction>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <p
            className={cn(
              "text-sm leading-relaxed",
              isPrimary
                ? "font-medium text-foreground italic"
                : "text-muted-foreground"
            )}
          >
            {item.content}
          </p>
          <div className="flex flex-col gap-3 border-t pt-3 sm:flex-row sm:items-center sm:justify-between">
            <span className="text-[0.625rem] font-semibold tracking-[0.04em] text-muted-foreground uppercase">
              Source: {item.source}
            </span>
            <Button size="sm" type="button" variant="ghost">
              View Item
              <ChevronRight data-icon="inline-end" />
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}

function ThreadDetail({
  thread,
}: {
  thread: MemoryThreadStoryline["detail"] | undefined
}) {
  if (!thread) {
    return (
      <section
        className="thread-detail flex min-h-130 flex-1 bg-background"
        data-testid="thread-detail"
      >
        <Empty className="min-h-full border-0">
          <EmptyHeader>
            <EmptyMedia variant="icon">
              <ListTree />
            </EmptyMedia>
            <EmptyTitle>No thread selected</EmptyTitle>
            <EmptyDescription>
              Choose a storyline from the list to review its membership
              timeline.
            </EmptyDescription>
          </EmptyHeader>
        </Empty>
      </section>
    )
  }

  return (
    <section
      className="thread-detail min-h-0 flex-1 bg-background"
      data-testid="thread-detail"
    >
      <div className="h-full overflow-y-auto">
        <div className="mx-auto flex max-w-4xl flex-col gap-8 p-4 md:p-8">
          <div className="flex items-start justify-between gap-4">
            <div className="min-w-0">
              <div className="mb-4 flex flex-wrap items-center gap-2">
                <span className="font-mono text-[0.6875rem] tracking-[0.08em] text-muted-foreground uppercase">
                  {thread.key}
                </span>
                <span className="size-1 rounded-full bg-border" />
                <Badge variant="outline">{thread.category}</Badge>
              </div>
              <h2 className="text-2xl leading-tight font-semibold tracking-tight md:text-3xl">
                {thread.title}
              </h2>
            </div>

            <div className="flex shrink-0 items-center gap-2">
              <Badge
                className="h-7 gap-1.5 px-2.5"
                variant={threadStatusVariant(thread.status)}
              >
                <span
                  className={cn(
                    "size-1.5 rounded-full",
                    statusDotClassName(thread.status)
                  )}
                />
                {thread.status} THREAD
              </Badge>
              <ThreadActions thread={thread} />
            </div>
          </div>

          <NarrativeSnapshot thread={thread} />

          <div>
            <div className="mb-6 flex items-center gap-2 text-xs font-semibold tracking-[0.12em] uppercase">
              <ListTree />
              Item Membership Timeline
            </div>
            <div className="relative before:absolute before:top-2 before:bottom-6 before:left-2 before:w-px before:bg-border">
              {thread.timeline.map((item) => (
                <TimelineItem
                  key={item.id}
                  isPrimary={item.role === "PRIMARY"}
                  item={item}
                />
              ))}
            </div>
            <div className="mt-4 flex justify-center">
              <Button type="button" variant="outline">
                Load 15 earlier items
              </Button>
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}

function matchesThreadFilter(
  storyline: MemoryThreadStoryline,
  filter: ThreadFilter
) {
  if (filter === "active") {
    return storyline.status === "ACTIVE"
  }

  if (filter === "dormant") {
    return storyline.status === "DORMANT"
  }

  return true
}

function matchesThreadQuery(storyline: MemoryThreadStoryline, query: string) {
  const normalizedQuery = query.trim().toLowerCase()

  if (!normalizedQuery) {
    return true
  }

  return [
    storyline.title,
    storyline.description,
    storyline.category,
    storyline.key,
  ]
    .join(" ")
    .toLowerCase()
    .includes(normalizedQuery)
}

export function ThreadsPage({ data }: { data: MemoryDashboardData }) {
  const [filter, setFilter] = useState<ThreadFilter>("all")
  const [query, setQuery] = useState("")
  const [selectedThreadKey, setSelectedThreadKey] = useState(
    data.threads.storylines[0]?.key ?? ""
  )

  const visibleStorylines = useMemo(
    () =>
      data.threads.storylines.filter(
        (storyline) =>
          matchesThreadFilter(storyline, filter) &&
          matchesThreadQuery(storyline, query)
      ),
    [data.threads.storylines, filter, query]
  )

  const selectedStoryline =
    visibleStorylines.find(
      (storyline) => storyline.key === selectedThreadKey
    ) ??
    visibleStorylines[0] ??
    data.threads.storylines.find(
      (storyline) => storyline.key === selectedThreadKey
    )

  return (
    <div
      className="thread-workbench flex h-full w-full min-w-0 flex-1 flex-col overflow-hidden bg-background"
      data-testid="thread-workbench"
    >
      <ThreadsHeader projection={data.threads.projection} />
      {/*<ThreadsSummary data={data.threads} />*/}
      <div className="flex min-h-0 flex-1 flex-col lg:flex-row">
        <ThreadList
          filter={filter}
          onFilterChange={setFilter}
          onQueryChange={setQuery}
          onSelectThread={setSelectedThreadKey}
          query={query}
          selectedThreadKey={selectedStoryline?.key ?? ""}
          storylines={visibleStorylines}
        />
        <ThreadDetail thread={selectedStoryline?.detail} />
      </div>
    </div>
  )
}
