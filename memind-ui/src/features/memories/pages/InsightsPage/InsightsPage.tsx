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
import {
  ChevronDown,
  ChevronLeft,
  ChevronsRight,
  Circle,
  FileText,
  GitBranch,
  Lightbulb,
  RefreshCcw,
  X,
} from "lucide-react"
import { AnimatePresence, motion } from "motion/react"
import type * as React from "react"
import { useEffect, useMemo, useRef, useState } from "react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible"
import {
  Empty,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from "@/components/ui/empty"
import { Separator } from "@/components/ui/separator"
import { cn } from "@/lib/utils"

import type {
  MemoryDashboardData,
  MemoryInsightDetail,
  MemoryInsightNode,
} from "../../dashboard/memory-dashboard-data"
import type { RefreshAction } from "../../dashboard/refresh-action"
import {
  createInsightTreeLayout,
  type InsightBranch,
  type InsightRoot,
  type InsightTreeEdge,
  type InsightTreeNode,
} from "./insight-layout"
import { fetchInsightsList, type AdminInsightView } from "./insights-api"

function titleCase(value: string) {
  return value
    .split(/[_\s-]+/)
    .filter(Boolean)
    .map((part) => part[0]?.toUpperCase() + part.slice(1).toLowerCase())
    .join(" ")
}

export function InsightsToolbar({
  refreshAction,
}: {
  refreshAction: RefreshAction
}) {
  return (
    <header
      className="z-40 flex min-h-14 shrink-0 items-center justify-between gap-4 border-b bg-white px-4 lg:px-6"
      data-testid="memory-insights-toolbar"
    >
      <div className="flex min-w-0 items-center gap-4">
        <h1 className="text-sm font-semibold text-primary">Insight Tree</h1>
        <Separator className="h-4 self-center" orientation="vertical" />
      </div>
      <Button
        disabled={refreshAction.isRefreshing}
        onClick={refreshAction.onRefresh}
        type="button"
        variant="outline"
      >
        <RefreshCcw data-icon="inline-start" />
        Refresh
      </Button>
    </header>
  )
}

function getInsightRoots(data: MemoryDashboardData): InsightRoot[] {
  if (data.insights.roots?.length) {
    return data.insights.roots
  }

  const root = data.insights.root

  if (!root) {
    return []
  }

  const fallbackRoot: InsightRoot = {
    ...root,
    branches: data.insights.branches,
  }
  const hierarchyRoots = data.insights.hierarchy[0]?.items ?? []
  const extraRoots = hierarchyRoots
    .filter((title) => title !== root.title)
    .map((title, index): InsightRoot => {
      const normalizedTitle = title.replace(/\.\.\.$/, "")
      const branch =
        data.insights.branches[index % data.insights.branches.length]

      return {
        ...root,
        id: `${root.id}-${index + 2}`,
        title:
          normalizedTitle === title
            ? title
            : title.startsWith("Market Expansion")
              ? "Market Expansion Strategy"
              : title.startsWith("User Retention")
                ? "User Retention Flow"
                : normalizedTitle,
        description: `${normalizedTitle} insight projection synthesized from the current Memory branch set.`,
        category: index === 0 ? "Growth" : "Retention",
        children: branch ? "1 Branch" : "0 Branches",
        branches: branch
          ? [
              {
                ...branch,
                id: `${branch.id}-r${index + 2}`,
                selected: false,
                leaves: branch.leaves.map((leaf) => ({
                  ...leaf,
                  id: `${leaf.id}-r${index + 2}`,
                })),
              },
            ]
          : [],
      }
    })

  return [
    {
      ...fallbackRoot,
    },
    ...extraRoots,
  ]
}

function getRootLeaves(root: InsightRoot) {
  return root.branches.flatMap((branch) => branch.leaves)
}

function insightKindFromTier(tier?: string): MemoryInsightNode["kind"] {
  const normalizedTier = tier?.toUpperCase()

  if (normalizedTier === "ROOT") {
    return "root"
  }

  if (normalizedTier === "BRANCH") {
    return "branch"
  }

  return "leaf"
}

function insightNodeFromView(insight: AdminInsightView): MemoryInsightNode {
  const kind = insightKindFromTier(insight.tier)

  return {
    category: insight.type ?? "Insight",
    childInsightIds: insight.childInsightIds ?? [],
    children: `${insight.childInsightIds?.length ?? 0} Children`,
    description: insight.content ?? insight.name ?? "-",
    id: String(insight.insightId),
    kind,
    label: titleCase(insight.tier ?? kind),
    title: insight.name ?? insight.groupName ?? `Insight ${insight.insightId}`,
  }
}

function withLoadedDescendants(
  root: InsightRoot,
  loadedNodes: Map<string, MemoryInsightNode>
): InsightRoot {
  if (!(root.childInsightIds?.length) && root.branches.length) {
    return root
  }

  const branches = (root.childInsightIds ?? [])
    .map((childInsightId) => loadedNodes.get(String(childInsightId)))
    .filter((node): node is MemoryInsightNode => Boolean(node))
    .map((branchNode): InsightBranch => ({
      ...branchNode,
      kind: "branch",
      leaves: (branchNode.childInsightIds ?? [])
        .map((childInsightId) => loadedNodes.get(String(childInsightId)))
        .filter((node): node is MemoryInsightNode => Boolean(node))
        .map((leafNode) => ({
          ...leafNode,
          kind: "leaf",
        })),
    }))

  return {
    ...root,
    branches,
  }
}

function createPreloadedNodes(roots: InsightRoot[]) {
  return new Map(
    roots.flatMap((root) => [
      ...root.branches.map(
        (branch) => [branch.id, branch] as [string, MemoryInsightNode]
      ),
      ...root.branches.flatMap((branch) =>
        branch.leaves.map(
          (leaf) => [leaf.id, leaf] as [string, MemoryInsightNode]
        )
      ),
    ])
  )
}

function InsightExplorerSection({
  children,
  count,
  defaultOpen = true,
  label,
  testId,
}: {
  children: React.ReactNode
  count: number
  defaultOpen?: boolean
  label: string
  testId: string
}) {
  const [open, setOpen] = useState(defaultOpen)

  return (
    <Collapsible open={open} onOpenChange={setOpen}>
      <CollapsibleTrigger
        aria-label={`Toggle ${label} group`}
        className={cn(
          "flex w-full items-center gap-1.5 rounded px-2 py-1.5 text-left text-muted-foreground transition-colors hover:bg-muted/70 hover:text-primary",
          open && "text-primary"
        )}
      >
        <ChevronDown
          className={cn("transition-transform", !open && "-rotate-90")}
        />
        <span className="flex-1 truncate text-[11px] font-bold tracking-[0.08em] uppercase">
          {label}
        </span>
        <Badge className="font-mono text-[10px]" variant="secondary">
          {count}
        </Badge>
      </CollapsibleTrigger>
      <CollapsibleContent>
        {open ? (
          <div className="mt-0.5 flex flex-col gap-0.5" data-testid={testId}>
            {children}
          </div>
        ) : null}
      </CollapsibleContent>
    </Collapsible>
  )
}

function TreeButton({
  active,
  children,
  className,
  icon,
  onClick,
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement> & {
  active?: boolean
  icon: React.ReactNode
}) {
  return (
    <button
      className={cn(
        "flex w-full min-w-0 items-center gap-2 rounded px-2 py-1.5 text-left text-[13px] transition-colors hover:bg-muted/50 hover:text-primary focus-visible:ring-2 focus-visible:ring-ring/30 focus-visible:outline-none",
        active ? "bg-muted text-primary shadow-sm" : "text-muted-foreground",
        className
      )}
      type="button"
      onClick={onClick}
      {...props}
    >
      {icon}
      <span className={cn("truncate", active && "font-semibold")}>
        {children}
      </span>
    </button>
  )
}

function InsightsExplorer({
  activeRoot,
  activeRootId,
  onCollapsedChange,
  onRootSelect,
  roots,
}: {
  activeRoot: InsightRoot | null
  activeRootId: string | null
  onCollapsedChange: (collapsed: boolean) => void
  onRootSelect: (rootId: string) => void
  roots: InsightRoot[]
}) {
  const leaves = activeRoot ? getRootLeaves(activeRoot) : []

  return (
    <motion.aside
      className="group/sidebar relative z-20 flex h-full w-72 shrink-0 flex-col border-r bg-background"
      data-testid="memory-insights-explorer"
      exit={{ width: 0 }}
      transition={{ duration: 0.18, ease: [0, 0, 0.2, 1] }}
    >
      <div
        className="flex min-w-0 items-center justify-between border-b bg-muted/30 px-4 py-3"
        data-testid="memory-insights-explorer-header"
      >
        <span className="text-[10px] font-bold tracking-[0.08em] text-muted-foreground uppercase">
          Hierarchy Explorer
        </span>
      </div>
      <Button
        aria-label="Collapse hierarchy explorer"
        className="absolute top-1/2 -right-3 z-30 rounded-full bg-background opacity-0 shadow-sm transition-opacity group-hover/sidebar:opacity-100 hover:bg-muted"
        size="icon-sm"
        type="button"
        variant="outline"
        onClick={() => onCollapsedChange(true)}
      >
        <ChevronLeft />
      </Button>
      <div
        className="min-h-0 min-w-0 flex-1 overflow-y-auto px-2 py-2"
        data-testid="memory-insights-explorer-content"
      >
        <div className="flex flex-col gap-2">
          <InsightExplorerSection
            count={roots.length}
            label="Roots"
            testId="insight-explorer-root-items"
          >
            <div className="ml-[13px] flex flex-col gap-0.5 border-l pl-3">
              {roots.map((root) => (
                <TreeButton
                  key={root.id}
                  active={root.id === activeRootId}
                  aria-label={`Select root ${root.title}`}
                  icon={<FileText data-icon="inline-start" />}
                  onClick={() => onRootSelect(root.id)}
                >
                  {root.title}
                </TreeButton>
              ))}
            </div>
          </InsightExplorerSection>

          <InsightExplorerSection
            count={activeRoot?.branches.length ?? 0}
            label="Branches"
            testId="insight-explorer-branch-items"
          >
            <div className="ml-[13px] flex flex-col gap-0.5 border-l pl-3">
              {activeRoot?.branches.map((branch) => (
                <TreeButton
                  key={branch.id}
                  aria-label={`Select branch ${branch.title}`}
                  icon={<GitBranch data-icon="inline-start" />}
                >
                  {branch.title}
                </TreeButton>
              ))}
            </div>
          </InsightExplorerSection>

          <InsightExplorerSection
            count={leaves.length}
            label="Leaves"
            testId="insight-explorer-leaf-items"
          >
            <div className="ml-[13px] flex flex-col gap-0.5 border-l pl-3">
              {leaves.map((leaf) => (
                <TreeButton
                  key={leaf.id}
                  aria-label={`Select leaf ${leaf.title}`}
                  className="text-[12px]"
                  icon={<Circle data-icon="inline-start" />}
                >
                  {leaf.title}
                </TreeButton>
              ))}
            </div>
          </InsightExplorerSection>
        </div>
      </div>
    </motion.aside>
  )
}

function buildInsightDetail(
  node: MemoryInsightNode,
  data: MemoryDashboardData
): MemoryInsightDetail {
  if (node.id === data.insights.selectedDetail?.id) {
    return data.insights.selectedDetail
  }

  return {
    id: node.id,
    kind: node.kind[0].toUpperCase() + node.kind.slice(1),
    title: node.title,
    description: node.description,
    points: [
      `${node.title} contributes to the current insight projection.`,
      `${node.category} signals are grouped under this Memory workspace.`,
    ],
    metadata: [
      {
        label: "GROUP",
        value: node.category,
      },
      {
        label: "TIER",
        value: node.kind === "root" ? "Strategic (A)" : "Operational",
      },
    ],
    categories: [node.category, node.kind],
  }
}

type InsightFlowNodeData = MemoryInsightNode & {
  height: number
  selected?: boolean
  width: number
}

type InsightFlowNodeType = FlowNode<InsightFlowNodeData, "insight">
type InsightFlowEdgeType = FlowEdge<Record<string, never>, "default">

function InsightFlowNode({
  data,
  selected,
}: FlowNodeProps<InsightFlowNodeType>) {
  const isRoot = data.kind === "root"
  const isLeaf = data.kind === "leaf"
  const highlighted = Boolean(selected || data.selected)
  const kindLabel = isRoot ? "Root" : isLeaf ? "Leaf" : "Branch"
  const dotClassName =
    data.label === "Bug"
      ? "bg-destructive"
      : data.label === "Metric"
        ? "bg-primary"
        : "bg-muted-foreground"
  const shellClassName = cn(
    "flex h-full w-full min-w-0 overflow-hidden",
    isRoot && "flex-col justify-center gap-0.5 px-1.5 py-1",
    !isRoot && !isLeaf && "items-center gap-1 px-1 py-0.5",
    isLeaf && "items-center gap-0.5 px-0.5 py-0.5"
  )
  const labelClassName = cn(
    "shrink-0 rounded-sm font-bold leading-none uppercase",
    isRoot
      ? "max-w-[46px] bg-primary/10 px-1 py-0.5 text-[6px] text-primary"
      : "max-w-[21px] bg-muted px-0.5 py-px text-[5px] text-muted-foreground",
    highlighted && !isRoot && "bg-primary/10 text-primary"
  )
  const titleClassName = cn(
    "min-w-0 flex-1 truncate font-semibold leading-none text-foreground",
    isRoot && "text-[7px]",
    !isRoot && !isLeaf && "text-[6px]",
    isLeaf && "text-[5px]"
  )

  return (
    <button
      aria-label={`Open ${data.kind} insight ${data.title}`}
      className={cn(
        "group flex h-full w-full overflow-hidden rounded-md border bg-white text-left shadow-sm transition-all hover:border-primary/60 hover:shadow-md focus-visible:ring-2 focus-visible:ring-ring/30 focus-visible:outline-none",
        isRoot && "rounded-lg",
        highlighted && "border-primary shadow-lg ring-4 ring-muted"
      )}
      data-id={data.id}
      type="button"
    >
      <Handle
        className="opacity-0"
        isConnectable={false}
        position={Position.Top}
        type="target"
      />
      <div className={shellClassName} data-testid="memory-insight-node-shell">
        {isRoot ? (
          <div className="flex min-w-0 items-center gap-1">
            <span className={cn(labelClassName, "truncate")}>
              {data.label}
            </span>
            <span className="min-w-0 flex-1 truncate font-mono text-[5px] leading-none text-muted-foreground">
              {data.children}
            </span>
          </div>
        ) : (
          <span className={cn("size-1 shrink-0 rounded-full", dotClassName)} />
        )}
        <div className="flex min-w-0 flex-1 items-center gap-1 overflow-hidden">
          {!isRoot && !isLeaf ? (
            <span className={cn(labelClassName, "truncate")}>
              {data.label}
            </span>
          ) : null}
          <span
            className={titleClassName}
            data-testid="memory-insight-node-title"
          >
            {data.title}
          </span>
          {highlighted ? (
            <span
              aria-hidden="true"
              className="size-1 shrink-0 rounded-full bg-primary"
            />
          ) : null}
        </div>
        <span className="sr-only">{kindLabel}</span>
      </div>
      <Handle
        className="opacity-0"
        isConnectable={false}
        position={Position.Bottom}
        type="source"
      />
    </button>
  )
}

const insightNodeTypes: NodeTypes = {
  insight: InsightFlowNode,
}

function buildInsightFlowElements(layout: {
  edges: InsightTreeEdge[]
  nodes: InsightTreeNode[]
}): {
  edges: InsightFlowEdgeType[]
  nodes: InsightFlowNodeType[]
} {
  return {
    nodes: layout.nodes.map((node) => ({
      id: node.id,
      type: "insight",
      position: { x: node.x, y: node.y },
      data: {
        ...node,
        height: node.height,
        selected: node.selected,
        width: node.width,
      },
      draggable: true,
      selectable: true,
      style: {
        height: node.height,
        width: node.width,
      },
    })),
    edges: layout.edges.map((edge) => ({
      id: `${edge.from}-${edge.to}`,
      type: "default",
      source: edge.from,
      target: edge.to,
      selectable: false,
      style: {
        opacity: 1,
        stroke: "var(--muted-foreground)",
        strokeWidth: 1.75,
      },
    })),
  }
}

function InsightsCanvas({
  explorerCollapsed,
  onExpandExplorer,
  onSelectNode,
  root,
  selectedNodeId,
}: {
  explorerCollapsed: boolean
  onExpandExplorer: () => void
  onSelectNode: (node: MemoryInsightNode) => void
  root: InsightRoot
  selectedNodeId: string | null
}) {
  const layout = useMemo(
    () => createInsightTreeLayout(root, selectedNodeId),
    [root, selectedNodeId]
  )
  const flowElements = useMemo(() => buildInsightFlowElements(layout), [layout])

  return (
    <section
      className="relative h-full min-h-full w-full min-w-0 flex-1 overflow-hidden bg-muted/20"
      data-testid="memory-insights-canvas"
    >
      {explorerCollapsed ? (
        <div className="group/restore absolute inset-y-0 left-0 z-30 flex w-8 items-center justify-center">
          <Button
            aria-label="Expand hierarchy explorer"
            className="rounded-full bg-background opacity-0 shadow-sm transition-opacity group-hover/restore:opacity-100"
            size="icon-sm"
            type="button"
            variant="outline"
            onClick={onExpandExplorer}
          >
            <ChevronsRight />
          </Button>
        </div>
      ) : null}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 z-10"
        data-edge-count={flowElements.edges.length}
        data-edge-stroke="var(--muted-foreground)"
        data-testid="memory-insight-edge-layer"
      />
      <ReactFlow
        className="memory-insight-flow h-full min-h-full"
        colorMode="light"
        data-testid="memory-insight-flow"
        edges={flowElements.edges}
        fitView
        fitViewOptions={{ padding: 0.14 }}
        height={layout.height}
        key={root.id}
        maxZoom={1.35}
        minZoom={0.45}
        nodeTypes={insightNodeTypes}
        nodes={flowElements.nodes}
        nodesConnectable={false}
        nodesDraggable
        panOnScroll
        proOptions={{ hideAttribution: true }}
        width={layout.width}
        zoomOnDoubleClick={false}
        zoomOnScroll={false}
        onNodeClick={(_, node) => onSelectNode(node.data)}
      >
        <Background
          color="hsl(var(--border))"
          gap={20}
          size={1}
          variant={BackgroundVariant.Dots}
        />
      </ReactFlow>
    </section>
  )
}

function InsightDetails({
  detail,
  onClose,
}: {
  detail: MemoryInsightDetail
  onClose: () => void
}) {
  const panelRef = useRef<HTMLElement | null>(null)

  useEffect(() => {
    panelRef.current?.focus()
  }, [detail.id])

  useEffect(() => {
    function handlePointerDown(event: PointerEvent) {
      if (!panelRef.current?.contains(event.target as Node)) {
        onClose()
      }
    }

    document.addEventListener("pointerdown", handlePointerDown)

    return () => {
      document.removeEventListener("pointerdown", handlePointerDown)
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

  return (
    <motion.aside
      ref={panelRef}
      animate={{ opacity: 1, x: 0 }}
      aria-label="Node Details"
      className="absolute top-0 right-0 bottom-0 z-40 flex w-[400px] max-w-[min(400px,100%)] shrink-0 flex-col border-l bg-white shadow-xl focus-visible:outline-none"
      data-testid="memory-insight-details"
      exit={{ opacity: 0, x: 28 }}
      initial={{ opacity: 0, x: 28 }}
      tabIndex={-1}
      transition={{ duration: 0.2, ease: [0, 0, 0.2, 1] }}
      onBlur={handleBlur}
    >
      <div className="flex items-center justify-between border-b bg-white px-6 py-4">
        <h2 className="text-sm font-semibold text-primary">Node Details</h2>
        <Button
          aria-label="Close node details"
          size="icon-xs"
          type="button"
          variant="ghost"
          onClick={onClose}
        >
          <X />
        </Button>
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto">
        <div className="flex flex-col gap-8 p-8">
          <div className="flex items-center gap-3">
            <Badge className="rounded" variant="default">
              {detail.kind}
            </Badge>
            <span className="font-mono text-[11px] text-muted-foreground">
              ID: {detail.id}
            </span>
          </div>
          <div>
            <h2 className="mb-4 text-2xl leading-tight font-bold tracking-tight text-foreground">
              {detail.title}
            </h2>
            <p className="text-[14px] leading-relaxed text-muted-foreground">
              {detail.description}
            </p>
          </div>

          <section>
            <h3 className="mb-4 flex items-center gap-2 text-[10px] font-bold tracking-widest text-muted-foreground uppercase">
              <span className="h-px w-4 bg-border" />
              Insight Points
            </h3>
            <ul className="flex flex-col gap-4">
              {detail.points.map((point) => (
                <li key={point} className="flex gap-3">
                  <span className="mt-1.5 size-1.5 shrink-0 rounded-full bg-primary" />
                  <span className="text-[13px] leading-normal">{point}</span>
                </li>
              ))}
            </ul>
          </section>

          <section className="grid grid-cols-2 gap-8 border-y py-6">
            {detail.metadata.map((item) => (
              <div key={item.label}>
                <p className="mb-1 text-[10px] font-bold tracking-wider text-muted-foreground uppercase">
                  {item.label.toLowerCase()}
                </p>
                <p className="text-[13px] font-semibold text-foreground">
                  {item.value}
                </p>
              </div>
            ))}
          </section>

          <section>
            <h3 className="mb-4 flex items-center gap-2 text-[10px] font-bold tracking-widest text-muted-foreground uppercase">
              <span className="h-px w-4 bg-border" />
              Categories
            </h3>
            <div className="flex flex-wrap gap-2">
              {detail.categories.map((category) => (
                <Badge
                  key={category}
                  variant={category === "Critical" ? "destructive" : "outline"}
                >
                  {category}
                </Badge>
              ))}
            </div>
          </section>
        </div>
      </div>
      <div className="grid grid-cols-2 gap-3 border-t bg-white p-6">
        <Button type="button">Regenerate</Button>
        <Button type="button" variant="outline">
          Delete
        </Button>
      </div>
    </motion.aside>
  )
}

function InsightsEmptyState() {
  return (
    <section
      className="flex min-h-0 flex-1 bg-muted/20 p-6"
      data-testid="memory-insights-empty"
    >
      <Empty className="rounded-none border-0 bg-transparent">
        <EmptyHeader>
          <EmptyMedia variant="icon">
            <Lightbulb />
          </EmptyMedia>
          <EmptyTitle>No insights yet</EmptyTitle>
          <EmptyDescription>
            Insight Tree will appear after this memory has generated insight
            records.
          </EmptyDescription>
        </EmptyHeader>
      </Empty>
    </section>
  )
}

function InsightsPageBody({
  data,
  roots,
}: {
  data: MemoryDashboardData
  roots: InsightRoot[]
}) {
  const [requestedRootId, setRequestedRootId] = useState<string | null>(null)
  const [explorerCollapsed, setExplorerCollapsed] = useState(false)
  const [loadedNodes, setLoadedNodes] = useState<Map<string, MemoryInsightNode>>(
    () => createPreloadedNodes(roots)
  )
  const [selectedDetail, setSelectedDetail] =
    useState<MemoryInsightDetail | null>(null)
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null)
  const activeRootId = roots.some((root) => root.id === requestedRootId)
    ? requestedRootId
    : null
  const selectedRoot =
    roots.find((root) => root.id === activeRootId) ?? null
  const activeRoot = selectedRoot
    ? withLoadedDescendants(selectedRoot, loadedNodes)
    : null

  async function loadInsightDescendants(childInsightIds: number[]) {
    if (!childInsightIds.length) {
      return
    }

    const children = await fetchInsightsList(childInsightIds)
    const childNodes = children.map(insightNodeFromView)

    setLoadedNodes((currentNodes) => {
      const nextNodes = new Map(currentNodes)

      childNodes.forEach((node) => {
        nextNodes.set(node.id, node)
      })

      return nextNodes
    })

    for (const childNode of childNodes) {
      await loadInsightDescendants(childNode.childInsightIds ?? [])
    }
  }

  function handleRootSelect(rootId: string) {
    const root = roots.find((candidate) => candidate.id === rootId)

    setRequestedRootId(rootId)
    setSelectedDetail(null)
    setSelectedNodeId(null)

    if (root) {
      void loadInsightDescendants(root.childInsightIds ?? [])
    }
  }

  function handleSelectNode(node: MemoryInsightNode) {
    setSelectedNodeId(node.id)
    setSelectedDetail(buildInsightDetail(node, data))
  }

  return (
    <section
      className="flex min-h-0 flex-1 overflow-hidden"
      data-testid="memory-insights-body"
    >
      <AnimatePresence initial={false}>
        {!explorerCollapsed ? (
          <InsightsExplorer
            key="insights-explorer"
            activeRoot={activeRoot}
            activeRootId={activeRoot?.id ?? null}
            roots={roots}
            onCollapsedChange={setExplorerCollapsed}
            onRootSelect={handleRootSelect}
          />
        ) : null}
      </AnimatePresence>
      <div className="relative flex h-full min-h-0 w-full min-w-0 flex-1 overflow-hidden">
        {activeRoot ? (
          <InsightsCanvas
            explorerCollapsed={explorerCollapsed}
            root={activeRoot}
            selectedNodeId={selectedNodeId}
            onExpandExplorer={() => setExplorerCollapsed(false)}
            onSelectNode={handleSelectNode}
          />
        ) : null}
        <AnimatePresence initial={false}>
          {selectedDetail ? (
            <InsightDetails
              key={selectedDetail.id}
              detail={selectedDetail}
              onClose={() => setSelectedDetail(null)}
            />
          ) : null}
        </AnimatePresence>
      </div>
    </section>
  )
}

export function InsightsPage({
  data,
  refreshAction,
}: {
  data: MemoryDashboardData
  refreshAction: RefreshAction
}) {
  const roots = useMemo(() => getInsightRoots(data), [data])
  const rootsKey = useMemo(() => JSON.stringify(roots), [roots])
  const hasInsights = roots.length > 0

  return (
    <div
      className="flex h-full min-h-0 flex-1 flex-col overflow-hidden bg-white"
      data-testid="memory-insights-workspace"
    >
      <InsightsToolbar refreshAction={refreshAction} />
      {!hasInsights ? <InsightsEmptyState /> : null}
      {hasInsights ? (
        <InsightsPageBody key={rootsKey} data={data} roots={roots} />
      ) : null}
    </div>
  )
}
