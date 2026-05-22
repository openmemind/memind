import {
  Background,
  BackgroundVariant,
  Handle,
  Position,
  ReactFlow,
  type Edge as FlowEdge,
  type Node as FlowNode,
  type NodeProps as FlowNodeProps,
  type NodeTypes,
} from "@xyflow/react"
import "@xyflow/react/dist/style.css"
import { Building2, GitFork, Search, UserRound, X } from "lucide-react"
import type * as React from "react"
import { useEffect, useMemo, useRef, useState } from "react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  InputGroup,
  InputGroupAddon,
  InputGroupInput,
} from "@/components/ui/input-group"
import { Progress } from "@/components/ui/progress"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Slider } from "@/components/ui/slider"
import { cn } from "@/lib/utils"

import { JsonBlock } from "../components/JsonBlock"
import type {
  MemoryDashboardData,
  MemoryGraphEntityDetail,
  MemoryGraphNode,
} from "../memory-dashboard-data"

const graphViewport = {
  width: 980,
  height: 620,
}

type GraphFlowNodeData = MemoryGraphNode & {
  selected?: boolean
}

type GraphFlowNodeType = FlowNode<GraphFlowNodeData, "entity">
type GraphFlowEdgeType = FlowEdge<Record<string, never>, "smoothstep">

function percentToCanvasPoint(value: string, max: number) {
  if (!value.endsWith("%")) {
    return Number(value)
  }

  return (Number(value.replace("%", "")) / 100) * max
}

function GraphFlowNode({ data, selected }: FlowNodeProps<GraphFlowNodeType>) {
  const Icon = data.icon === "organization" ? Building2 : UserRound
  const highlighted = Boolean(selected || data.selected)

  return (
    <button
      aria-label={`Open ${data.type} entity ${data.label}`}
      className="nodrag nopan group flex h-full w-full cursor-pointer items-center gap-3 rounded-xl border border-white/80 bg-white/70 p-3.5 text-left shadow-[0_8px_20px_-8px_rgba(0,0,0,0.18)] backdrop-blur-md transition-all hover:scale-[1.03] hover:bg-white focus-visible:ring-2 focus-visible:ring-ring/30 focus-visible:outline-none"
      onMouseDown={(event) => event.stopPropagation()}
      type="button"
    >
      <Handle
        className="opacity-0"
        isConnectable={false}
        position={Position.Left}
        type="target"
      />
      <span
        className={cn(
          "flex size-10 shrink-0 items-center justify-center rounded-lg border transition-colors",
          data.type === "Organization"
            ? "border-primary/30 bg-primary/10 text-primary"
            : "border-primary bg-primary text-primary-foreground",
          highlighted && "ring-2 ring-primary/25"
        )}
      >
        <Icon />
      </span>
      <span className="flex min-w-0 flex-col gap-1">
        <span className="truncate text-[10px] leading-none font-bold tracking-[0.08em] text-muted-foreground uppercase">
          {data.type}
        </span>
        <span className="truncate text-[13px] leading-none font-bold text-primary">
          {data.label}
        </span>
      </span>
      <Handle
        className="opacity-0"
        isConnectable={false}
        position={Position.Right}
        type="source"
      />
    </button>
  )
}

const graphNodeTypes: NodeTypes = {
  entity: GraphFlowNode,
}

function buildGraphFlowElements(
  data: MemoryDashboardData,
  selectedNodeId: string | null
): {
  edges: GraphFlowEdgeType[]
  nodes: GraphFlowNodeType[]
} {
  return {
    nodes: data.graph.nodes.map((node) => ({
      id: node.id,
      type: "entity",
      position: {
        x: percentToCanvasPoint(node.x, graphViewport.width),
        y: percentToCanvasPoint(node.y, graphViewport.height),
      },
      data: {
        ...node,
        selected: node.id === selectedNodeId,
      },
      draggable: false,
      focusable: true,
      selectable: true,
      sourcePosition: Position.Right,
      targetPosition: Position.Left,
      style: {
        height: 68,
        width: node.size === "lg" ? 170 : 156,
      },
    })),
    edges: data.graph.edges.map((edge) => ({
      id: `${edge.from}-${edge.to}`,
      type: "smoothstep",
      source: edge.from,
      target: edge.to,
      selectable: false,
      style: {
        stroke: "hsl(var(--primary) / 0.18)",
        strokeDasharray: "5 5",
        strokeWidth: 1.5,
      },
    })),
  }
}

function createNodeDetail(
  data: MemoryDashboardData,
  node: MemoryGraphNode
): MemoryGraphEntityDetail {
  if (node.id === data.graph.selectedEntity.entityId) {
    return data.graph.selectedEntity
  }

  const connectedNodes = data.graph.edges
    .filter((edge) => edge.from === node.id || edge.to === node.id)
    .map((edge) => (edge.from === node.id ? edge.to : edge.from))
    .map((nodeId) =>
      data.graph.nodes.find((graphNode) => graphNode.id === nodeId)
    )
    .filter((graphNode): graphNode is MemoryGraphNode => Boolean(graphNode))

  return {
    label: node.label,
    entityId: node.id,
    type: node.type.toUpperCase(),
    aliases: [node.label],
    cooccurrences: connectedNodes.map((connectedNode) => ({
      label: connectedNode.label,
      strength: connectedNode.size === "lg" ? 82 : 61,
    })),
    metadataJson: JSON.stringify(
      {
        graph_node_id: node.id,
        entity_type: node.type,
        connected_entities: connectedNodes.map(
          (connectedNode) => connectedNode.label
        ),
      },
      null,
      2
    ),
    mentionCount: connectedNodes.length,
  }
}

function GraphToolbar({ onFocus }: { onFocus: () => void }) {
  return (
    <div className="pointer-events-none absolute top-4 right-4 left-4 z-20 flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
      <div className="pointer-events-auto flex flex-wrap items-center gap-2">
        <InputGroup className="h-9 w-48 border-border bg-background/90 shadow-sm backdrop-blur-md md:w-56">
          <InputGroupAddon>
            <Search />
          </InputGroupAddon>
          <InputGroupInput
            onFocus={onFocus}
            placeholder="Search entities..."
            type="search"
          />
        </InputGroup>
        <div className="flex h-9 items-center gap-2 rounded-md border border-border bg-background/90 px-3 shadow-sm backdrop-blur-md">
          <span className="text-[11px] font-bold tracking-[0.06em] text-muted-foreground uppercase">
            Type:
          </span>
          <Select defaultValue="all">
            <SelectTrigger
              aria-label="Graph entity type"
              className="border-0 bg-transparent px-0 shadow-none"
              size="sm"
            >
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectGroup>
                <SelectItem value="all">All</SelectItem>
                <SelectItem value="person">Person</SelectItem>
                <SelectItem value="organization">Org</SelectItem>
              </SelectGroup>
            </SelectContent>
          </Select>
        </div>
        <div className="flex h-9 items-center gap-3 rounded-md border border-border bg-background/90 px-3 shadow-sm backdrop-blur-md">
          <span className="shrink-0 text-[11px] font-bold tracking-[0.06em] text-muted-foreground uppercase">
            Strength
          </span>
          <Slider
            aria-label="Minimum graph strength"
            className="w-16"
            defaultValue={[42]}
            max={100}
            min={0}
          />
        </div>
      </div>
      <div className="pointer-events-auto flex items-center lg:justify-end">
        <div className="flex h-9 items-center rounded-md border border-border bg-background/90 px-3 shadow-sm backdrop-blur-md">
          <Select defaultValue="force">
            <SelectTrigger
              aria-label="Graph layout"
              className="border-0 bg-transparent px-0 text-[11px] font-bold tracking-[0.06em] uppercase shadow-none"
              size="sm"
            >
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectGroup>
                <SelectItem value="force">Force Directed</SelectItem>
                <SelectItem value="circular">Circular</SelectItem>
                <SelectItem value="grid">Grid</SelectItem>
              </SelectGroup>
            </SelectContent>
          </Select>
        </div>
      </div>
    </div>
  )
}

function GraphLegend() {
  return (
    <div className="absolute bottom-4 left-4 z-10 flex flex-wrap gap-4 rounded-lg border border-border bg-background/80 px-4 py-2.5 text-[10px] font-bold tracking-[0.08em] text-muted-foreground uppercase shadow-sm backdrop-blur-md">
      <span className="flex items-center gap-2">
        <span className="size-2.5 rounded bg-primary" />
        Person
      </span>
      <span className="flex items-center gap-2">
        <span className="size-2.5 rounded border border-primary bg-primary/20" />
        Org
      </span>
    </div>
  )
}

function SelectionDetailPanel({
  detail,
  onBlur,
  onClose,
}: {
  detail: MemoryGraphEntityDetail
  onBlur: React.FocusEventHandler<HTMLElement>
  onClose: () => void
}) {
  const panelRef = useRef<HTMLElement>(null)
  const isOrganization = detail.type.toLowerCase().includes("org")
  const Icon = isOrganization ? Building2 : UserRound

  useEffect(() => {
    panelRef.current?.focus()
  }, [detail.entityId])

  return (
    <aside
      aria-label="Selection details"
      className="absolute top-4 right-4 bottom-4 z-30 flex w-[min(20rem,calc(100%-2rem))] flex-col rounded-xl border border-border bg-background shadow-2xl outline-none"
      onBlur={onBlur}
      ref={panelRef}
      tabIndex={-1}
    >
      <div className="flex items-center justify-between gap-3 border-b border-border px-5 py-4">
        <span className="text-[11px] font-bold tracking-[0.14em] text-muted-foreground uppercase">
          Selection Detail
        </span>
        <Button
          aria-label="Close selection detail"
          onClick={onClose}
          size="icon-sm"
          type="button"
          variant="ghost"
        >
          <X />
        </Button>
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto">
        <div className="flex flex-col gap-6 p-5">
          <div className="flex items-start gap-3">
            <div
              className={cn(
                "flex size-10 shrink-0 items-center justify-center rounded-lg border",
                isOrganization
                  ? "border-primary/30 bg-primary/10 text-primary"
                  : "border-primary bg-primary text-primary-foreground"
              )}
            >
              <Icon />
            </div>
            <div className="min-w-0">
              <h2 className="truncate text-lg leading-tight font-bold text-primary">
                {detail.label}
              </h2>
              <p className="mt-0.5 truncate font-mono text-[10px] text-muted-foreground">
                ID: {detail.entityId}
              </p>
              <Badge className="mt-2 w-fit uppercase" variant="secondary">
                {detail.type}
              </Badge>
            </div>
          </div>

          <section>
            <h3 className="mb-2.5 text-[10px] font-bold tracking-[0.14em] text-muted-foreground uppercase">
              Aliases
            </h3>
            <div className="flex flex-wrap gap-1.5">
              {detail.aliases.map((alias) => (
                <Badge key={alias} variant="outline">
                  {alias}
                </Badge>
              ))}
            </div>
          </section>

          <section>
            <h3 className="mb-2.5 text-[10px] font-bold tracking-[0.14em] text-muted-foreground uppercase">
              Associations
            </h3>
            <div className="flex flex-col gap-3">
              {detail.cooccurrences.length ? (
                detail.cooccurrences.map((item) => (
                  <Progress key={item.label} value={item.strength}>
                    <div className="flex w-full items-end justify-between gap-3">
                      <span className="text-xs font-semibold text-primary">
                        {item.label}
                      </span>
                      <span className="font-mono text-[10px] font-bold text-muted-foreground">
                        {item.strength}%
                      </span>
                    </div>
                  </Progress>
                ))
              ) : (
                <p className="text-xs text-muted-foreground">
                  No connected entities in the current graph view.
                </p>
              )}
            </div>
          </section>

          <JsonBlock label="Metadata Info" value={detail.metadataJson} />
        </div>
      </div>
      <div className="border-t border-border p-4">
        <Button className="h-9 w-full" type="button">
          View All Mentions ({detail.mentionCount})
        </Button>
      </div>
    </aside>
  )
}

function GraphCanvas({ data }: { data: MemoryDashboardData }) {
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null)
  const selectedNode = useMemo(
    () => data.graph.nodes.find((node) => node.id === selectedNodeId) ?? null,
    [data.graph.nodes, selectedNodeId]
  )
  const selectedDetail = useMemo(
    () => (selectedNode ? createNodeDetail(data, selectedNode) : null),
    [data, selectedNode]
  )
  const flowElements = useMemo(
    () => buildGraphFlowElements(data, selectedNodeId),
    [data, selectedNodeId]
  )

  function closeSelectionOnBlur(event: React.FocusEvent<HTMLElement>) {
    if (!event.currentTarget.contains(event.relatedTarget)) {
      setSelectedNodeId(null)
    }
  }

  return (
    <div
      className="relative min-h-[560px] flex-1 overflow-hidden bg-background"
      data-testid="memory-graph-canvas"
    >
      <GraphToolbar onFocus={() => setSelectedNodeId(null)} />
      <ReactFlow
        className="memory-entity-flow"
        colorMode="light"
        data-testid="memory-graph-flow"
        edges={flowElements.edges}
        fitView
        fitViewOptions={{ padding: 0.34 }}
        height={graphViewport.height}
        maxZoom={1.35}
        minZoom={0.5}
        nodeTypes={graphNodeTypes}
        nodes={flowElements.nodes}
        nodesConnectable={false}
        nodesDraggable={false}
        onNodeClick={(_event, node) => setSelectedNodeId(node.id)}
        onPaneClick={() => setSelectedNodeId(null)}
        panOnScroll
        proOptions={{ hideAttribution: true }}
        width={graphViewport.width}
        zoomOnDoubleClick={false}
        zoomOnScroll={false}
      >
        <Background
          color="hsl(var(--muted-foreground) / 0.45)"
          gap={32}
          size={0.8}
          variant={BackgroundVariant.Dots}
        />
      </ReactFlow>
      <GraphLegend />
      {selectedDetail ? (
        <SelectionDetailPanel
          detail={selectedDetail}
          onBlur={closeSelectionOnBlur}
          onClose={() => setSelectedNodeId(null)}
        />
      ) : null}
    </div>
  )
}

export function GraphHeader() {
  return (
    <header className="flex shrink-0 flex-col gap-4 border-b border-border bg-background px-8 pt-6 pb-6 md:flex-row md:items-center md:justify-between">
      <div className="min-w-0">
        <h1 className="text-[28px] leading-tight font-bold tracking-tight text-primary">
          Graph Explorer
        </h1>
        <p className="mt-1 max-w-2xl text-sm leading-relaxed text-muted-foreground">
          Map connections between entities, mentions, and item relationships to
          discover hidden insights.
        </p>
      </div>
      <Button
        aria-label="More graph actions"
        className="hidden shadow-sm md:inline-flex"
        size="icon-lg"
        type="button"
        variant="outline"
      >
        <GitFork />
      </Button>
    </header>
  )
}

export function GraphPage({ data }: { data: MemoryDashboardData }) {
  return (
    <div className="flex h-full min-h-0 w-full flex-col overflow-hidden bg-background">
      <GraphHeader />
      <GraphCanvas data={data} />
    </div>
  )
}
