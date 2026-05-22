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

import { render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { describe, expect, it, vi } from "vitest"

import { PaginatedTable } from "./PaginatedTable"
import { TableCell, TableHead, TableRow } from "./ui/table"

describe("PaginatedTable", () => {
  it("renders table rows with a pagination summary", () => {
    render(
      <PaginatedTable
        columns={
          <TableRow>
            <TableHead>Name</TableHead>
            <TableHead>Status</TableHead>
          </TableRow>
        }
        columnCount={2}
        pagination={{ summary: "Showing 2 of 12 memories" }}
      >
        <TableRow>
          <TableCell>Memory A</TableCell>
          <TableCell>Active</TableCell>
        </TableRow>
        <TableRow>
          <TableCell>Memory B</TableCell>
          <TableCell>Idle</TableCell>
        </TableRow>
      </PaginatedTable>
    )

    expect(screen.getByRole("table")).toBeInTheDocument()
    expect(screen.getByText("Memory A")).toBeInTheDocument()
    expect(screen.getByText("Showing 2 of 12 memories")).toBeInTheDocument()
    expect(screen.getByRole("navigation", { name: "pagination" }))
      .toBeInTheDocument()
  })

  it("renders shadcn empty content inside the table body when empty", () => {
    render(
      <PaginatedTable
        columns={
          <TableRow>
            <TableHead>Name</TableHead>
            <TableHead>Status</TableHead>
          </TableRow>
        }
        columnCount={2}
        emptyState={{
          description: "Try changing filters or creating a new memory.",
          title: "No memories found",
        }}
        isEmpty
        pagination={{ summary: "Showing 0 of 0 memories" }}
      />
    )

    const emptyTitle = screen.getByText("No memories found")
    const emptyRoot = emptyTitle.closest("[data-slot='empty']")
    const emptyCell = emptyTitle.closest("td")

    expect(emptyRoot).toBeInTheDocument()
    expect(emptyTitle.closest("tbody")).toBeInTheDocument()
    expect(emptyCell).toHaveAttribute("colspan", "2")
    expect(
      screen.getByText("Try changing filters or creating a new memory.")
    ).toBeInTheDocument()
  })

  it("requests page changes from pagination controls", async () => {
    const user = userEvent.setup()
    const onPageChange = vi.fn()

    render(
      <PaginatedTable
        columns={
          <TableRow>
            <TableHead>Name</TableHead>
          </TableRow>
        }
        columnCount={1}
        pagination={{
          currentPage: 2,
          hasNext: true,
          hasPrevious: true,
          onPageChange,
          summary: "Showing 11-20 of 30 memories",
          totalPages: 3,
        }}
      >
        <TableRow>
          <TableCell>Memory C</TableCell>
        </TableRow>
      </PaginatedTable>
    )

    await user.click(screen.getByRole("button", { name: "Go to next page" }))
    await user.click(
      screen.getByRole("button", { name: "Go to previous page" })
    )

    expect(onPageChange).toHaveBeenNthCalledWith(1, 3)
    expect(onPageChange).toHaveBeenNthCalledWith(2, 1)
  })
})
