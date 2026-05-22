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

import { toast } from "sonner"

export class UnimplementedApiError extends Error {
  readonly apiName: string
  readonly endpoint: string

  constructor(apiName: string, endpoint: string) {
    super(`${apiName} is not implemented: ${endpoint}`)
    this.name = "UnimplementedApiError"
    this.apiName = apiName
    this.endpoint = endpoint
  }
}

export async function notImplementedApi<T = never>(
  apiName: string,
  endpoint: string
): Promise<T> {
  toast.error(`${apiName} 接口暂未实现`, {
    description: `${endpoint} 待后端完成后接入。`,
  })

  throw new UnimplementedApiError(apiName, endpoint)
}
