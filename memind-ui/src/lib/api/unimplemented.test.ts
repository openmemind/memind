import { beforeEach, describe, expect, it, vi } from "vitest"
import { toast } from "sonner"

import { UnimplementedApiError, notImplementedApi } from "./unimplemented"

vi.mock("sonner", () => ({
  toast: {
    error: vi.fn(),
  },
}))

describe("notImplementedApi", () => {
  beforeEach(() => {
    vi.mocked(toast.error).mockClear()
  })

  it("shows a toast and rejects with endpoint details", async () => {
    await expect(
      notImplementedApi("Memories list", "GET /admin/v1/memories")
    ).rejects.toMatchObject({
      apiName: "Memories list",
      endpoint: "GET /admin/v1/memories",
      name: "UnimplementedApiError",
    })

    expect(toast.error).toHaveBeenCalledWith("Memories list 接口暂未实现", {
      description: "GET /admin/v1/memories 待后端完成后接入。",
    })
  })

  it("uses a typed error so callers can distinguish missing backend work", async () => {
    await expect(
      notImplementedApi("Insight tree", "GET /admin/v1/insights/tree")
    ).rejects.toBeInstanceOf(UnimplementedApiError)
  })
})
