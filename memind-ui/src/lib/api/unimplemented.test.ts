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
