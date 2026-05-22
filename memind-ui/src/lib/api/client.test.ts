import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import { ApiError, fetchJson } from "./client"

describe("fetchJson", () => {
  const originalFetch = globalThis.fetch

  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    globalThis.fetch = originalFetch
  })

  it("unwraps success result data and appends query parameters", async () => {
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(JSON.stringify({ data: { status: "ok" } }), {
        headers: { "Content-Type": "application/json" },
        status: 200,
      })
    )

    await expect(
      fetchJson<{ status: string }>("/admin/v1/dashboard", {
        query: {
          days: 7,
          empty: "",
          memoryId: "user:agent",
          skipped: undefined,
        },
      })
    ).resolves.toEqual({ status: "ok" })

    expect(fetch).toHaveBeenCalledWith(
      "/admin/v1/dashboard?days=7&memoryId=user%3Aagent",
      expect.objectContaining({ headers: expect.any(Headers) })
    )
    expect(
      (vi.mocked(fetch).mock.calls[0]?.[1]?.headers as Headers).get("Accept")
    ).toBe("application/json")
  })

  it("passes through paginated data envelopes", async () => {
    const page = {
      items: [{ id: "rd_1" }],
      page: {
        hasNext: false,
        hasPrevious: false,
        page: 1,
        pageSize: 20,
        totalItems: 1,
        totalPages: 1,
      },
    }
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(JSON.stringify({ data: page }), { status: 200 })
    )

    await expect(fetchJson("/admin/v1/raw-data")).resolves.toEqual(page)
  })

  it("throws ApiError with backend error details for failed responses", async () => {
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          error: {
            code: "validation_failed",
            message: "Request validation failed",
          },
        }),
        {
          headers: { "X-Request-Id": "req_123" },
          status: 400,
          statusText: "Bad Request",
        }
      )
    )

    await expect(fetchJson("/admin/v1/raw-data")).rejects.toMatchObject({
      code: "validation_failed",
      message: "Request validation failed",
      requestId: "req_123",
      status: 400,
    })
  })

  it("throws ApiError when the response is not a success envelope", async () => {
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(JSON.stringify({ value: "missing envelope" }), {
        status: 200,
      })
    )

    await expect(fetchJson("/admin/v1/dashboard")).rejects.toBeInstanceOf(
      ApiError
    )
  })
})
