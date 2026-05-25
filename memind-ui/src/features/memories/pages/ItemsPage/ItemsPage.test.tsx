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
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { describe, expect, it } from "vitest"

import type { MemoryDashboardData } from "../../dashboard/memory-dashboard-data"
import type { RefreshAction } from "../../dashboard/refresh-action"

import { ItemsPage } from "./ItemsPage"

const noopRefreshAction: RefreshAction = {
  isRefreshing: false,
  onRefresh: () => {},
}

describe("ItemsPage", () => {
  it("keeps item details open until the close button is clicked", async () => {
    const user = userEvent.setup()

    render(
      <ItemsPage data={itemsPageData} refreshAction={noopRefreshAction} />
    )

    expect(screen.queryByTestId("item-details-panel")).not.toBeInTheDocument()

    await user.click(screen.getByRole("button", { name: "View" }))

    const details = screen.getByTestId("item-details-panel")

    await waitFor(() => {
      expect(details).toHaveAttribute("data-state", "open")
    })
    expect(within(details).getByText("Itemdetails")).toBeInTheDocument()

    fireEvent.pointerDown(document.body)
    fireEvent.keyDown(document, { key: "Escape" })
    screen.getByRole("button", { name: "Refresh" }).focus()

    await waitFor(() => {
      expect(screen.getByTestId("item-details-panel")).toHaveAttribute(
        "data-state",
        "open"
      )
    })

    await user.click(
      within(screen.getByTestId("item-details-panel")).getByRole("button", {
        name: "Close item details",
      })
    )

    expect(screen.getByTestId("item-details-panel")).toHaveAttribute(
      "data-state",
      "closed"
    )
  })
})

const itemsPageData = {
  items: {
    paginationLabel: "Showing 1 of 1 items",
    records: [
      {
        category: "behavior",
        content: "User prefers dark mode for all dashboard interfaces.",
        contentHash: "h_9f2e...88a2",
        id: "mi_2b9c7a1d",
        metadataJson: `{
  "confidence": 0.98,
  "entities": ["user", "dark mode"]
}`,
        observedAt: "2m ago",
        observedAtFull: "2024-05-20 14:30:05",
        occurredAt: "-",
        relatedThreads: [
          {
            id: "thr_a92",
            status: "Active",
            updatedAt: "2m ago",
          },
        ],
        scope: "USER",
        shortId: "mi_2b9c7...",
        sourceIntegration: "slack_integration",
        sourceRawDataId: "rd_7a2b9c1d-84e1-4f02-9844-01938ae2",
        sourceRawDataShortId: "rd_7a2b...",
        sourceType: "conversation",
        threadCount: 1,
        type: "FACT",
        vectorId: "vec_8213_f9a1_223b_091c",
        vectorStatus: "Vectorized",
      },
    ],
    summary: [],
  },
} as MemoryDashboardData
