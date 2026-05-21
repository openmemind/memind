import {
  ArrowUpDown,
  Database,
  ExternalLink,
  Maximize2,
  MessageSquare,
  Minimize2,
  RefreshCcw,
  Search,
  X,
} from "lucide-react"
import type * as React from "react"
import { useEffect, useRef, useState } from "react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Checkbox } from "@/components/ui/checkbox"
import {
  InputGroup,
  InputGroupAddon,
  InputGroupInput,
} from "@/components/ui/input-group"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import {
  FilterSelect,
  PageHeader,
  PagePagination,
  TableSurface,
} from "@/features/shared/ui"
import { cn } from "@/lib/utils"

import { DetailRow } from "../components/DetailRow"
import { JsonBlock } from "../components/JsonBlock"
import { VectorStatus } from "../components/VectorStatus"
import type {
  MemoryDashboardData,
  MemoryItemRecord,
} from "../memory-dashboard-data"

const itemScopeOptions = [
  { value: "all", label: "Scope: All" },
  { value: "user", label: "USER" },
  { value: "agent", label: "AGENT" },
]

const itemCategoryOptions = [
  { value: "all", label: "Category: All" },
  { value: "profile", label: "profile" },
  { value: "behavior", label: "behavior" },
  { value: "event", label: "event" },
  { value: "tool", label: "tool" },
]

const itemTypeOptions = [
  { value: "all", label: "Type: All" },
  { value: "fact", label: "FACT" },
  { value: "foresight", label: "FORESIGHT" },
]

const itemSourceOptions = [
  { value: "all", label: "Source: All" },
  { value: "raw-data", label: "Raw Data" },
]

export function ItemsHeader() {
  return (
    <PageHeader
      action={
        <div className="flex flex-wrap gap-2">
          <Button type="button" variant="outline">
            <RefreshCcw data-icon="inline-start" />
            Refresh
          </Button>
          <Button disabled type="button" variant="outline">
            Delete selected
          </Button>
        </div>
      }
      description="Inspect extracted memory facts, source raw data, metadata, vectors, and related threads."
      title="Memory Items"
    />
  )
}

function ItemsSummary({ data }: { data: MemoryDashboardData }) {
  return (
    <section className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-6">
      {data.items.summary.map((item) => (
        <Card key={item.label}>
          <CardHeader>
            <CardDescription>{item.label}</CardDescription>
            <CardTitle className="mt-3 text-3xl font-semibold">
              {item.value}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <span className="text-muted-foreground">
              {item.detail ?? "Current workspace"}
            </span>
          </CardContent>
        </Card>
      ))}
    </section>
  )
}

function ItemsToolbar() {
  return (
    <Card className="mb-4 bg-card/95" data-testid="items-toolbar">
      <CardContent className="py-3">
        <div className="flex flex-col gap-3 xl:flex-row xl:items-center">
          <InputGroup className="h-9 min-w-0 flex-1">
            <InputGroupAddon>
              <Search />
            </InputGroupAddon>
            <InputGroupInput
              placeholder="Search memory items..."
              type="search"
            />
          </InputGroup>

          <div className="flex flex-wrap items-center gap-2">
            <FilterSelect
              aria-label="Memory item scope"
              className="min-w-32"
              defaultValue="all"
              items={itemScopeOptions}
            />
            <FilterSelect
              aria-label="Memory item category"
              className="min-w-36"
              defaultValue="all"
              items={itemCategoryOptions}
            />
            <FilterSelect
              aria-label="Memory item type"
              className="min-w-32"
              defaultValue="all"
              items={itemTypeOptions}
            />
            <FilterSelect
              aria-label="Memory item source"
              className="min-w-32"
              defaultValue="all"
              items={itemSourceOptions}
            />
            <Button type="button" variant="outline">
              <ArrowUpDown data-icon="inline-start" />
              Sort
            </Button>
          </div>
        </div>
      </CardContent>
    </Card>
  )
}

function MemoryItemId({ item }: { item: MemoryItemRecord }) {
  return (
    <div className="flex flex-col gap-1">
      <code className="font-mono text-[11px] font-medium">{item.shortId}</code>
      <span className="line-clamp-2 max-w-[18rem] text-muted-foreground">
        {item.content}
      </span>
    </div>
  )
}

function ItemsTable({
  data,
  onViewItem,
}: {
  data: MemoryDashboardData
  onViewItem: (item: MemoryItemRecord) => void
}) {
  return (
    <TableSurface className="mb-6">
      <Table className="min-w-[1220px]">
        <TableHeader>
          <TableRow>
            <TableHead className="w-12">
              <Checkbox aria-label="Select all memory items" />
            </TableHead>
            <TableHead>Item</TableHead>
            <TableHead>Scope</TableHead>
            <TableHead>Category</TableHead>
            <TableHead>Type</TableHead>
            <TableHead>Source</TableHead>
            <TableHead>Vector</TableHead>
            <TableHead>Observed</TableHead>
            <TableHead>Occurred</TableHead>
            <TableHead className="text-center">Threads</TableHead>
            <TableHead className="text-right">Action</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {data.items.records.map((item) => (
            <TableRow key={item.id}>
              <TableCell>
                <Checkbox aria-label={`Select ${item.id}`} />
              </TableCell>
              <TableCell>
                <MemoryItemId item={item} />
              </TableCell>
              <TableCell>
                <Badge variant="secondary">{item.scope}</Badge>
              </TableCell>
              <TableCell>
                <Badge variant="outline">{item.category}</Badge>
              </TableCell>
              <TableCell>
                <Badge variant="outline">{item.type}</Badge>
              </TableCell>
              <TableCell>
                <code className="font-mono text-[11px]">
                  {item.sourceRawDataShortId}
                </code>
              </TableCell>
              <TableCell>
                <VectorStatus status={item.vectorStatus} />
              </TableCell>
              <TableCell className="font-mono text-[11px] text-muted-foreground">
                {item.observedAt}
              </TableCell>
              <TableCell className="font-mono text-[11px] text-muted-foreground">
                {item.occurredAt}
              </TableCell>
              <TableCell className="text-center">{item.threadCount}</TableCell>
              <TableCell className="text-right">
                <Button
                  onClick={() => onViewItem(item)}
                  size="sm"
                  type="button"
                  variant="outline"
                >
                  View
                </Button>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      <PagePagination label={data.items.paginationLabel} />
    </TableSurface>
  )
}

function DetailsSection({
  children,
  title,
}: {
  children: React.ReactNode
  title: string
}) {
  return (
    <section>
      <h3 className="mb-3 text-xs font-medium tracking-[0.08em] text-muted-foreground uppercase">
        {title}
      </h3>
      {children}
    </section>
  )
}

function MemoryItemDetailsPanel({
  isExpanded,
  isVisible,
  item,
  onCloseAnimationEnd,
  onClose,
  onToggleExpanded,
}: {
  isExpanded: boolean
  isVisible: boolean
  item: MemoryItemRecord
  onCloseAnimationEnd: () => void
  onClose: () => void
  onToggleExpanded: () => void
}) {
  const panelRef = useRef<HTMLElement | null>(null)

  useEffect(() => {
    panelRef.current?.focus()
  }, [item.id])

  useEffect(() => {
    function handlePointerDown(event: PointerEvent) {
      if (!panelRef.current?.contains(event.target as Node)) {
        onClose()
      }
    }

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        onClose()
      }
    }

    document.addEventListener("pointerdown", handlePointerDown)
    document.addEventListener("keydown", handleKeyDown)

    return () => {
      document.removeEventListener("pointerdown", handlePointerDown)
      document.removeEventListener("keydown", handleKeyDown)
    }
  }, [onClose])

  function handleBlur(event: React.FocusEvent<HTMLElement>) {
    const nextTarget = event.relatedTarget

    if (
      nextTarget instanceof Node &&
      event.currentTarget.contains(nextTarget)
    ) {
      return
    }

    onClose()
  }

  function handleTransitionEnd(event: React.TransitionEvent<HTMLDivElement>) {
    if (event.target !== event.currentTarget || isVisible) {
      return
    }

    onCloseAnimationEnd()
  }

  return (
    <div className="pointer-events-none fixed inset-4 z-50">
      <aside
        ref={panelRef}
        aria-label="Itemdetails"
        className={cn(
          "pointer-events-auto ml-auto flex h-full w-full flex-col overflow-hidden rounded-lg border bg-popover text-xs/relaxed text-popover-foreground shadow-2xl transition-[max-width,transform,opacity] duration-300 ease-[cubic-bezier(0.4,0,0.2,1)] focus-visible:outline-none",
          isExpanded ? "max-w-[calc(100vw-2rem)]" : "max-w-[30rem]",
          isVisible
            ? "translate-x-0 scale-100 opacity-100"
            : "translate-x-8 scale-[0.98] opacity-0"
        )}
        data-anchor="right"
        data-animation="slide-scale"
        data-expanded={isExpanded ? "true" : "false"}
        data-state={isVisible ? "open" : "closed"}
        data-testid="item-details-panel"
        tabIndex={-1}
        onBlur={handleBlur}
        onTransitionEnd={handleTransitionEnd}
      >
      <div className="flex shrink-0 items-start justify-between gap-4 border-b bg-popover px-6 py-5">
        <div className="min-w-0">
          <h2 className="text-sm font-semibold text-foreground">Itemdetails</h2>
          <code className="mt-1 inline-flex rounded bg-muted px-2 py-0.5 font-mono text-[11px] text-muted-foreground">
            {item.id}
          </code>
          <div className="mt-3 flex flex-wrap gap-2">
            <Badge variant="outline">{item.category}</Badge>
            <Badge variant="secondary">{item.scope}</Badge>
            <Badge variant="outline">{item.type}</Badge>
          </div>
        </div>
        <div className="flex shrink-0 items-center gap-1">
          <Button
            aria-label={
              isExpanded ? "Shrink item details" : "Expand item details"
            }
            size="icon-sm"
            type="button"
            variant="ghost"
            onClick={onToggleExpanded}
          >
            {isExpanded ? <Minimize2 /> : <Maximize2 />}
          </Button>
          <Button
            aria-label="Close item details"
            size="icon-sm"
            type="button"
            variant="ghost"
            onClick={onClose}
          >
            <X />
          </Button>
        </div>
      </div>

      <div
        className={cn(
          "min-h-0 flex-1 overflow-y-auto",
          isExpanded ? "px-8 py-7 lg:px-10" : "px-6 py-5"
        )}
      >
        <div
          className={cn(
            "mx-auto grid gap-7",
            isExpanded ? "max-w-6xl lg:grid-cols-[minmax(0,1fr)_24rem]" : ""
          )}
        >
          <div className="flex min-w-0 flex-col gap-7">
            <DetailsSection title="Content">
              <div
                className={cn(
                  "rounded-md border bg-muted/30 p-4 leading-relaxed",
                  isExpanded ? "text-base" : "text-sm"
                )}
              >
                {item.content}
              </div>
            </DetailsSection>

            <DetailsSection title="Temporal Data">
              <div className="flex flex-col gap-3">
                <DetailRow
                  label="Observed"
                  value={<span>{item.observedAtFull}</span>}
                />
                <DetailRow
                  label="Occurred"
                  value={<span>{item.occurredAt}</span>}
                />
              </div>
            </DetailsSection>

            <DetailsSection title="Vector & Deduplication">
              <div className="flex flex-col gap-3 rounded-md border bg-muted/30 p-4">
                <div>
                  <p className="mb-1 text-[10px] font-medium tracking-[0.08em] text-muted-foreground uppercase">
                    vectorId
                  </p>
                  <p className="font-mono text-[11px] break-all">
                    {item.vectorId}
                  </p>
                </div>
                <div>
                  <p className="mb-1 text-[10px] font-medium tracking-[0.08em] text-muted-foreground uppercase">
                    contentHash
                  </p>
                  <p className="font-mono text-[11px]">{item.contentHash}</p>
                </div>
              </div>
            </DetailsSection>
          </div>

          <div className="flex min-w-0 flex-col gap-7">
            <DetailsSection title="Source">
              <div className="flex flex-col gap-3 rounded-md border p-4">
                <DetailRow
                  label="rawDataId"
                  value={<span>{item.sourceRawDataShortId}</span>}
                />
                <DetailRow
                  label="Type"
                  value={<span>{item.sourceType}</span>}
                />
                <DetailRow
                  label="Integration"
                  value={<span>{item.sourceIntegration}</span>}
                />
                <Button className="mt-1 w-full" type="button">
                  <Database data-icon="inline-start" />
                  View Raw Data
                </Button>
              </div>
            </DetailsSection>

            <JsonBlock label="Metadata" value={item.metadataJson} />

            <DetailsSection
              title={`Related Threads (${item.relatedThreads.length})`}
            >
              <div className="flex flex-col gap-2">
                {item.relatedThreads.map((thread) => (
                  <button
                    key={thread.id}
                    className="flex items-center justify-between gap-3 rounded-md border bg-card px-3 py-3 text-left transition-colors hover:bg-muted/50"
                    type="button"
                  >
                    <span className="flex min-w-0 items-center gap-3">
                      <MessageSquare className="shrink-0 text-muted-foreground" />
                      <span>
                        <span className="block font-mono text-[11px]">
                          {thread.id}
                        </span>
                        <span className="text-xs text-muted-foreground">
                          {thread.status} - {thread.updatedAt}
                        </span>
                      </span>
                    </span>
                    <ExternalLink className="shrink-0 text-muted-foreground" />
                  </button>
                ))}
              </div>
            </DetailsSection>
          </div>
        </div>
      </div>
      </aside>
    </div>
  )
}

export function ItemsPage({ data }: { data: MemoryDashboardData }) {
  const [selectedItem, setSelectedItem] = useState<MemoryItemRecord | null>(
    null
  )
  const [isDetailsExpanded, setIsDetailsExpanded] = useState(false)
  const [isDetailsVisible, setIsDetailsVisible] = useState(false)

  useEffect(() => {
    if (!selectedItem) {
      return
    }

    const animationFrame = window.requestAnimationFrame(() => {
      setIsDetailsVisible(true)
    })

    return () => window.cancelAnimationFrame(animationFrame)
  }, [selectedItem])

  useEffect(() => {
    if (!selectedItem || isDetailsVisible) {
      return
    }

    const closeTimer = window.setTimeout(() => {
      setSelectedItem(null)
    }, 300)

    return () => window.clearTimeout(closeTimer)
  }, [isDetailsVisible, selectedItem])

  function handleViewItem(item: MemoryItemRecord) {
    setSelectedItem(item)
    setIsDetailsExpanded(false)
    setIsDetailsVisible(false)
  }

  function handleCloseDetails() {
    setIsDetailsVisible(false)
    setIsDetailsExpanded(false)
  }

  return (
    <>
      <ItemsHeader />
      <ItemsSummary data={data} />
      <ItemsToolbar />
      <ItemsTable data={data} onViewItem={handleViewItem} />
      {selectedItem ? (
        <MemoryItemDetailsPanel
          isExpanded={isDetailsExpanded}
          isVisible={isDetailsVisible}
          item={selectedItem}
          onClose={handleCloseDetails}
          onCloseAnimationEnd={() => setSelectedItem(null)}
          onToggleExpanded={() => setIsDetailsExpanded((value) => !value)}
        />
      ) : null}
    </>
  )
}
