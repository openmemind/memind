import { Dialog as SheetPrimitive } from "@base-ui/react/dialog"
import {
  ArrowUpDown,
  CalendarDays,
  Copy,
  ExternalLink,
  FileText,
  RefreshCcw,
  Search,
} from "lucide-react"
import { type MouseEvent, useState } from "react"

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
  Sheet,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet"
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

import { JsonBlock } from "../components/JsonBlock"
import { SheetPanel } from "../components/SheetPanel"
import { VectorStatus } from "../components/VectorStatus"
import type {
  MemoryDashboardData,
  MemoryRawDataRecord,
} from "../memory-dashboard-data"

const rawDataTypeOptions = [
  { value: "all", label: "All types" },
  { value: "conversation", label: "Conversation" },
  { value: "document", label: "Document" },
  { value: "image", label: "Image" },
  { value: "audio", label: "Audio" },
]

const rawDataSourceOptions = [
  { value: "all", label: "Source" },
  { value: "slack", label: "Slack" },
  { value: "discord", label: "Discord" },
  { value: "email", label: "Email" },
]

export function RawDataHeader() {
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
      description="Inspect source records, captions, segments, metadata, and cleanup impact for this Memory."
      title="Raw Data"
    />
  )
}

function RawDataSummary({ data }: { data: MemoryDashboardData }) {
  return (
    <section className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-5">
      {data.rawData.summary.map((item) => (
        <Card key={item.label}>
          <CardHeader>
            <CardDescription>{item.label}</CardDescription>
            <CardTitle className="mt-3 text-3xl font-semibold">
              {item.value}
            </CardTitle>
          </CardHeader>
          <CardContent>
            {item.trend ? (
              <Badge variant="outline">{item.trend}</Badge>
            ) : (
              <span className="text-muted-foreground">
                {item.detail ?? "Current workspace"}
              </span>
            )}
          </CardContent>
        </Card>
      ))}
    </section>
  )
}

function RawDataToolbar() {
  return (
    <Card className="mb-4 bg-card/95" data-testid="raw-data-toolbar">
      <CardContent className="py-3">
        <div className="flex flex-col gap-3 xl:flex-row xl:items-center">
          <InputGroup className="h-9 min-w-0 flex-1">
            <InputGroupAddon>
              <Search />
            </InputGroupAddon>
            <InputGroupInput placeholder="Search records..." type="search" />
          </InputGroup>

          <div className="flex flex-wrap items-center gap-2">
            <FilterSelect
              aria-label="Raw data type"
              className="min-w-32"
              defaultValue="all"
              items={rawDataTypeOptions}
            />
            <FilterSelect
              aria-label="Raw data source"
              className="min-w-32"
              defaultValue="all"
              items={rawDataSourceOptions}
            />
            <Button type="button" variant="outline">
              <CalendarDays data-icon="inline-start" />
              Date range
            </Button>
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

function RawDataId({ record }: { record: MemoryRawDataRecord }) {
  function handleCopy(event: MouseEvent<HTMLButtonElement>) {
    event.stopPropagation()

    if (navigator.clipboard) {
      void navigator.clipboard.writeText(record.id)
    }
  }

  return (
    <div className="flex items-center gap-1 font-mono text-[11px]">
      <span>{record.shortId}</span>
      <Button
        aria-label={`Copy ${record.id}`}
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

function RawDataTable({
  data,
  onViewRecord,
}: {
  data: MemoryDashboardData
  onViewRecord: (record: MemoryRawDataRecord) => void
}) {
  return (
    <TableSurface className="mb-6">
      <Table className="min-w-[1080px]">
        <TableHeader>
          <TableRow>
            <TableHead className="w-12">
              <Checkbox aria-label="Select all raw data records" />
            </TableHead>
            <TableHead>Raw Data ID</TableHead>
            <TableHead>Type</TableHead>
            <TableHead>Caption</TableHead>
            <TableHead>Source</TableHead>
            <TableHead>Vector</TableHead>
            <TableHead>Created</TableHead>
            <TableHead className="text-right">Action</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {data.rawData.records.map((record) => (
            <TableRow key={record.id}>
              <TableCell>
                <Checkbox
                  aria-label={`Select ${record.id}`}
                  onClick={(event) => event.stopPropagation()}
                />
              </TableCell>
              <TableCell>
                <RawDataId record={record} />
              </TableCell>
              <TableCell>
                <Badge variant="secondary">{record.typeLabel}</Badge>
              </TableCell>
              <TableCell className="max-w-[28rem] truncate font-medium">
                {record.caption}
              </TableCell>
              <TableCell className="font-mono text-[11px] text-muted-foreground">
                {record.source}
              </TableCell>
              <TableCell>
                <VectorStatus status={record.vectorStatus} />
              </TableCell>
              <TableCell className="font-mono text-[11px] text-muted-foreground">
                {record.createdAt}
              </TableCell>
              <TableCell className="text-right">
                <Button
                  onClick={() => onViewRecord(record)}
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
      <PagePagination label={data.rawData.paginationLabel} />
    </TableSurface>
  )
}

function RecordDetailsSheet({
  onCloseComplete,
  onOpenChange,
  open,
  record,
}: {
  onCloseComplete: () => void
  onOpenChange: (open: boolean) => void
  open: boolean
  record: MemoryRawDataRecord | null
}) {
  if (!record) {
    return null
  }

  return (
    <Sheet
      onOpenChange={onOpenChange}
      onOpenChangeComplete={(isOpen) => {
        if (!isOpen) {
          onCloseComplete()
        }
      }}
      open={open}
    >
      <SheetPanel>
        <SheetHeader className="border-b">
          <SheetTitle>Record Details</SheetTitle>
          <SheetDescription className="font-mono">{record.id}</SheetDescription>
          <SheetPrimitive.Close
            className="absolute top-4 right-4 rounded-md p-1 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
            aria-label="Close"
          >
            <span className="sr-only">Close</span>
          </SheetPrimitive.Close>
        </SheetHeader>
        <div className="flex min-h-0 flex-1 flex-col gap-6 overflow-y-auto px-6 py-4">
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant="secondary">{record.type}</Badge>
            <span className="font-mono text-[11px] text-muted-foreground">
              Created {record.createdAt}
            </span>
          </div>

          <div className="rounded-md border bg-muted/30 p-4">
            <div className="mb-2 text-xs font-medium text-muted-foreground">
              Raw Caption
            </div>
            <p className="leading-relaxed">
              User inquired about API rate limits for the Nexus endpoint. They
              mentioned encountering 429 errors intermittently over the last 15
              minutes while attempting to sync vector batches from the
              development staging environment.
            </p>
          </div>

          <JsonBlock label="Segment Data" value={record.segmentJson} />
          <JsonBlock label="Metadata Objects" value={record.metadataJson} />

          <div>
            <div className="mb-2 text-xs font-medium text-muted-foreground">
              Associated Memory Items
            </div>
            <div className="flex flex-col gap-2">
              {record.associatedItems.map((item) => (
                <button
                  key={item.label}
                  className="flex items-center justify-between gap-3 rounded-md border bg-card px-3 py-3 text-left transition-colors hover:bg-muted/50"
                  type="button"
                >
                  <span className="flex min-w-0 items-center gap-3">
                    <FileText className="shrink-0 text-muted-foreground" />
                    <span className="truncate font-medium">{item.label}</span>
                  </span>
                  <ExternalLink className="shrink-0 text-muted-foreground" />
                </button>
              ))}
            </div>
          </div>
        </div>
        <SheetFooter className="border-t">
          <div className="flex flex-col gap-2 sm:flex-row">
            <Button className="flex-1" type="button">
              Reprocess
            </Button>
            <Button type="button" variant="destructive">
              Delete
            </Button>
          </div>
        </SheetFooter>
      </SheetPanel>
    </Sheet>
  )
}

export function RawDataPage({ data }: { data: MemoryDashboardData }) {
  const [selectedRecord, setSelectedRecord] =
    useState<MemoryRawDataRecord | null>(null)
  const [isDetailsOpen, setIsDetailsOpen] = useState(false)

  function handleViewRecord(record: MemoryRawDataRecord) {
    setSelectedRecord(record)
    setIsDetailsOpen(true)
  }

  return (
    <>
      <RawDataHeader />
      <RawDataSummary data={data} />
      <RawDataToolbar />
      <RawDataTable data={data} onViewRecord={handleViewRecord} />
      <RecordDetailsSheet
        onCloseComplete={() => setSelectedRecord(null)}
        onOpenChange={(open) => {
          if (!open) {
            setIsDetailsOpen(false)
          }
        }}
        open={isDetailsOpen}
        record={selectedRecord}
      />
    </>
  )
}
