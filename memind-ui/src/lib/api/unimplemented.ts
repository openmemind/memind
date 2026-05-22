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
