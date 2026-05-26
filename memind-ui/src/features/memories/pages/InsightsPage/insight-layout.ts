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

import type { MemoryInsightNode } from "../../dashboard/memory-dashboard-data"

export type InsightBranch = MemoryInsightNode & {
  selected?: boolean
  leaves: MemoryInsightNode[]
}

export type InsightRoot = MemoryInsightNode & {
  branches: InsightBranch[]
}

export type InsightTreeNode = MemoryInsightNode & {
  height: number
  selected?: boolean
  width: number
  x: number
  y: number
}

export type InsightTreeEdge = {
  from: string
  to: string
}

export function createInsightTreeLayout(
  root: InsightRoot,
  selectedNodeId: string | null
): {
  edges: InsightTreeEdge[]
  height: number
  nodes: InsightTreeNode[]
  width: number
} {
  const branchGap = 86
  const branchY = 64
  const leafGap = 54
  const rootWidth = 96
  const rootHeight = 36
  const branchWidth = 58
  const branchHeight = 27
  const leafWidth = 42
  const leafHeight = 23
  const subtreeGap = branchGap - branchWidth
  const branchSlots = root.branches.map((branch) => {
    const leafSlotWidth =
      branch.leaves.length > 0
        ? branch.leaves.length * leafWidth +
          (branch.leaves.length - 1) * (leafGap - leafWidth)
        : branchWidth

    return Math.max(branchWidth, leafSlotWidth)
  })
  const branchSlotsWidth =
    branchSlots.reduce((total, slotWidth) => total + slotWidth, 0) +
    Math.max(0, root.branches.length - 1) * subtreeGap
  const canvasWidth = Math.max(196, branchSlotsWidth + 84)
  const rootX = canvasWidth / 2 - rootWidth / 2
  let branchSlotX = canvasWidth / 2 - branchSlotsWidth / 2
  const nodes: InsightTreeNode[] = [
    {
      ...root,
      height: rootHeight,
      selected: selectedNodeId === root.id,
      width: rootWidth,
      x: rootX,
      y: 8,
    },
  ]
  const edges: InsightTreeEdge[] = []

  root.branches.forEach((branch, branchIndex) => {
    const branchSlotWidth = branchSlots[branchIndex] ?? branchWidth
    const branchX = branchSlotX + branchSlotWidth / 2 - branchWidth / 2

    nodes.push({
      ...branch,
      height: branchHeight,
      selected: selectedNodeId === branch.id || branch.selected,
      width: branchWidth,
      x: branchX,
      y: branchY,
    })
    edges.push({ from: root.id, to: branch.id })

    const leafTotalWidth =
      branch.leaves.length > 0
        ? branch.leaves.length * leafWidth +
          (branch.leaves.length - 1) * (leafGap - leafWidth)
        : leafWidth
    const leafStartX = branchX + branchWidth / 2 - leafTotalWidth / 2

    branch.leaves.forEach((leaf, leafIndex) => {
      nodes.push({
        ...leaf,
        height: leafHeight,
        selected: selectedNodeId === leaf.id,
        width: leafWidth,
        x: leafStartX + leafIndex * leafGap,
        y: 112,
      })
      edges.push({ from: branch.id, to: leaf.id })
    })

    branchSlotX += branchSlotWidth + subtreeGap
  })

  return {
    edges,
    height: 152,
    nodes,
    width: canvasWidth,
  }
}
