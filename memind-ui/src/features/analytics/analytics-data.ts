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

export type AnalyticsMetric = {
  label: string
  value: string
  detail: string
  trend: string
  tone: "default" | "success" | "warning" | "danger"
}

export type RequestHealthPoint = {
  label: string
  requests: number
  errors: number
}

export type LatencyPoint = {
  label: string
  p50: number
  p95: number
  p99: number
}

export type TraceStatus = "200" | "201" | "204" | "429" | "500"

export type RecentTrace = {
  traceId: string
  method: "GET" | "POST" | "DELETE"
  endpoint: string
  status: TraceStatus
  duration: string
  user: string
  timestamp: string
}

export type AnalyticsData = {
  metrics: AnalyticsMetric[]
  requestHealth: RequestHealthPoint[]
  latency: LatencyPoint[]
  traces: RecentTrace[]
}

const analyticsData: AnalyticsData = {
  metrics: [
    {
      label: "RPM",
      value: "2.5k",
      detail: "Requests per minute",
      trend: "+5.2%",
      tone: "success",
    },
    {
      label: "Errors",
      value: "14",
      detail: "Runtime errors",
      trend: "critical",
      tone: "danger",
    },
    {
      label: "Latency (P95)",
      value: "242ms",
      detail: "Retrieval response",
      trend: "stable",
      tone: "success",
    },
  ],
  requestHealth: [
    { label: "10:00", requests: 420, errors: 4 },
    { label: "10:05", requests: 510, errors: 5 },
    { label: "10:10", requests: 690, errors: 8 },
    { label: "10:15", requests: 630, errors: 7 },
    { label: "10:20", requests: 580, errors: 6 },
    { label: "10:25", requests: 760, errors: 12 },
    { label: "10:30", requests: 880, errors: 10 },
    { label: "10:35", requests: 790, errors: 9 },
    { label: "10:40", requests: 690, errors: 6 },
    { label: "10:45", requests: 610, errors: 5 },
  ],
  latency: [
    { label: "10:00", p50: 84, p95: 210, p99: 480 },
    { label: "10:05", p50: 92, p95: 230, p99: 520 },
    { label: "10:10", p50: 88, p95: 218, p99: 498 },
    { label: "10:15", p50: 110, p95: 286, p99: 610 },
    { label: "10:20", p50: 96, p95: 242, p99: 536 },
    { label: "10:25", p50: 90, p95: 235, p99: 502 },
    { label: "10:30", p50: 82, p95: 224, p99: 490 },
  ],
  traces: [
    {
      traceId: "TRC_882x_01",
      method: "POST",
      endpoint: "/memories/extract",
      status: "200",
      duration: "450ms",
      user: "alex.r",
      timestamp: "10:45:12",
    },
    {
      traceId: "TRC_882x_02",
      method: "GET",
      endpoint: "/memories/search",
      status: "500",
      duration: "1.2s",
      user: "sarah.c",
      timestamp: "10:44:58",
    },
    {
      traceId: "TRC_882x_03",
      method: "POST",
      endpoint: "/memories/store",
      status: "201",
      duration: "182ms",
      user: "dev_user",
      timestamp: "10:44:30",
    },
    {
      traceId: "TRC_882x_04",
      method: "DELETE",
      endpoint: "/memories/purge",
      status: "204",
      duration: "45ms",
      user: "alex.r",
      timestamp: "10:44:12",
    },
    {
      traceId: "TRC_882x_05",
      method: "POST",
      endpoint: "/memories/extract",
      status: "200",
      duration: "318ms",
      user: "maya.l",
      timestamp: "10:43:58",
    },
    {
      traceId: "TRC_882x_06",
      method: "GET",
      endpoint: "/memories/search",
      status: "200",
      duration: "96ms",
      user: "dev_user",
      timestamp: "10:43:41",
    },
    {
      traceId: "TRC_882x_07",
      method: "POST",
      endpoint: "/memories/store",
      status: "429",
      duration: "780ms",
      user: "sarah.c",
      timestamp: "10:43:20",
    },
    {
      traceId: "TRC_882x_08",
      method: "GET",
      endpoint: "/memories/search",
      status: "200",
      duration: "112ms",
      user: "alex.r",
      timestamp: "10:42:55",
    },
    {
      traceId: "TRC_882x_09",
      method: "POST",
      endpoint: "/memories/extract",
      status: "201",
      duration: "256ms",
      user: "ops_bot",
      timestamp: "10:42:39",
    },
    {
      traceId: "TRC_882x_10",
      method: "DELETE",
      endpoint: "/memories/purge",
      status: "204",
      duration: "52ms",
      user: "admin",
      timestamp: "10:42:12",
    },
    {
      traceId: "TRC_882x_11",
      method: "POST",
      endpoint: "/memories/store",
      status: "500",
      duration: "1.8s",
      user: "qa_agent",
      timestamp: "10:41:58",
    },
    {
      traceId: "TRC_882x_12",
      method: "GET",
      endpoint: "/memories/search",
      status: "200",
      duration: "88ms",
      user: "maya.l",
      timestamp: "10:41:35",
    },
  ],
}

async function fetchAnalyticsData() {
  return analyticsData
}

export function useAnalyticsData() {
  return useQuery({
    queryKey: ["analytics", "overview"],
    queryFn: fetchAnalyticsData,
  })
}
