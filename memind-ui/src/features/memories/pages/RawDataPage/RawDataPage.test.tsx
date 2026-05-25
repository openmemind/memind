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

import { RawDataPage } from "./RawDataPage"

const noopRefreshAction: RefreshAction = {
  isRefreshing: false,
  onRefresh: () => {},
}

describe("RawDataPage", () => {
  it("opens raw data details in an expandable right-side panel", async () => {
    const user = userEvent.setup()

    render(
      <RawDataPage
        data={rawDataPageData}
        refreshAction={noopRefreshAction}
      />
    )

    expect(screen.queryByTestId("record-details-panel")).not.toBeInTheDocument()

    await user.click(screen.getByRole("button", { name: "View" }))

    const details = screen.getByTestId("record-details-panel")

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument()
    expect(details).toHaveAttribute("data-animation", "slide-scale")
    expect(details).toHaveAttribute("data-anchor", "right")
    expect(details).toHaveAttribute("data-expanded", "false")
    expect(details).toHaveClass("ml-auto")
    expect(details).toHaveClass("w-full")
    expect(details).toHaveClass("max-w-120")
    await waitFor(() => {
      expect(details).toHaveAttribute("data-state", "open")
    })
    expect(within(details).getByText("Record Details")).toBeInTheDocument()
    expect(within(details).getByText("Raw Caption")).toBeInTheDocument()
    expect(within(details).getByText('"speaker_id"')).toHaveClass("hljs-attr")
    expect(within(details).getByText('"user_491"')).toHaveClass("hljs-string")

    await user.click(
      within(details).getByRole("button", { name: "Expand record details" })
    )

    expect(screen.getByTestId("record-details-panel")).toHaveAttribute(
      "data-expanded",
      "true"
    )
    expect(screen.getByTestId("record-details-panel")).toHaveClass(
      "max-w-[calc(100vw-2rem)]"
    )

    await user.click(
      within(screen.getByTestId("record-details-panel")).getByRole("button", {
        name: "Shrink record details",
      })
    )

    expect(screen.getByTestId("record-details-panel")).toHaveAttribute(
      "data-expanded",
      "false"
    )
    expect(screen.getByTestId("record-details-panel")).toHaveClass("max-w-120")

    await user.click(
      within(screen.getByTestId("record-details-panel")).getByRole("button", {
        name: "Close record details",
      })
    )

    expect(screen.getByTestId("record-details-panel")).toHaveAttribute(
      "data-state",
      "closed"
    )
    await waitFor(() => {
      expect(
        screen.queryByTestId("record-details-panel")
      ).not.toBeInTheDocument()
    })
  })

  it("keeps raw data details open until the close button is clicked", async () => {
    const user = userEvent.setup()

    render(
      <RawDataPage
        data={rawDataPageData}
        refreshAction={noopRefreshAction}
      />
    )

    await user.click(screen.getByRole("button", { name: "View" }))

    const details = screen.getByTestId("record-details-panel")

    await waitFor(() => {
      expect(details).toHaveAttribute("data-state", "open")
    })

    fireEvent.pointerDown(document.body)
    fireEvent.keyDown(document, { key: "Escape" })
    screen.getByRole("button", { name: "Refresh" }).focus()

    await waitFor(() => {
      expect(screen.getByTestId("record-details-panel")).toHaveAttribute(
        "data-state",
        "open"
      )
    })

    await user.click(
      within(screen.getByTestId("record-details-panel")).getByRole("button", {
        name: "Close record details",
      })
    )

    expect(screen.getByTestId("record-details-panel")).toHaveAttribute(
      "data-state",
      "closed"
    )
  })
})

const rawDataPageData = {
  rawData: {
    page: {
      hasNext: false,
      hasPrevious: false,
      page: 1,
      pageSize: 20,
      totalItems: 1,
      totalPages: 1,
    },
    paginationLabel: "Showing 1 of 1 records",
    records: [
      {
        associatedItems: [
          {
            kind: "article",
            label: "API Documentation Snippet",
          },
        ],
        caption:
          "User inquired about API rate limits for the Nexus endpoint...",
        createdAt: "2m ago",
        id: "rd_7a2b9c1d-84e1-4f02-9844-01938ae2",
        metadataJson: `{
  "source": "slack_integration",
  "tags": ["critical", "api-errors"]
}`,
        segmentJson: `{
  "start": 1709214000,
  "speaker_id": "user_491"
}`,
        shortId: "rd_7a2...",
        source: "Slack (Prod)",
        type: "conversation",
        typeLabel: "CONV",
        vectorStatus: "Live",
      },
    ],
  },
} as MemoryDashboardData
