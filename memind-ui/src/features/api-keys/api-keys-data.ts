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

import { useQuery } from "@tanstack/react-query"

export type ApiKeyStatus = "active" | "disabled" | "expiring"

export type ApiKeyRecord = {
  id: string
  name: string
  prefix: string
  owner: string
  ownerInitials: string
  scopes: string[]
  status: ApiKeyStatus
  lastUsed: string
  requests: string
  expires: string
}

export type ApiKeysData = {
  keys: ApiKeyRecord[]
}

const apiKeysData: ApiKeysData = {
  keys: [
    {
      id: "key-prod-1",
      name: "Production-Server-1",
      prefix: "mem_sk_abc123••••",
      owner: "System",
      ownerInitials: "SY",
      scopes: ["read", "write"],
      status: "active",
      lastUsed: "2m ago",
      requests: "842",
      expires: "Never",
    },
    {
      id: "key-dev-local",
      name: "Dev-Agent-Local",
      prefix: "mem_sk_xyz789••••",
      owner: "Jane Doe",
      ownerInitials: "JD",
      scopes: ["read"],
      status: "expiring",
      lastUsed: "1h ago",
      requests: "12",
      expires: "7d",
    },
    {
      id: "key-legacy",
      name: "Legacy-Integration",
      prefix: "mem_sk_vbn456••••",
      owner: "Admin Legacy",
      ownerInitials: "AL",
      scopes: ["admin"],
      status: "disabled",
      lastUsed: "Never",
      requests: "0",
      expires: "Dec 1, 2026",
    },
  ],
}

async function fetchApiKeysData() {
  return apiKeysData
}

export function useApiKeysData() {
  return useQuery({
    queryKey: ["api-keys", "list"],
    queryFn: fetchApiKeysData,
  })
}
