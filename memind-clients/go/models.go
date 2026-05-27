/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package memind

import "time"

type Role string

const (
	RoleUser      Role = "USER"
	RoleAssistant Role = "ASSISTANT"
)

type Strategy string

const (
	StrategySimple Strategy = "SIMPLE"
	StrategyDeep   Strategy = "DEEP"
)

type Message struct {
	Role         Role           `json:"role"`
	Content      []ContentBlock `json:"content"`
	Timestamp    *time.Time     `json:"timestamp,omitempty"`
	UserName     string         `json:"userName,omitempty"`
	SourceClient string         `json:"sourceClient,omitempty"`
}

type ExtractMemoryRequest struct {
	UserID       string     `json:"userId"`
	AgentID      string     `json:"agentId"`
	RawContent   RawContent `json:"rawContent"`
	SourceClient string     `json:"sourceClient,omitempty"`
}

type AddMessageRequest struct {
	UserID       string  `json:"userId"`
	AgentID      string  `json:"agentId"`
	Message      Message `json:"message"`
	SourceClient string  `json:"sourceClient,omitempty"`
}

type CommitMemoryRequest struct {
	UserID       string `json:"userId"`
	AgentID      string `json:"agentId"`
	SourceClient string `json:"sourceClient,omitempty"`
}

type RetrieveMemoryRequest struct {
	UserID         string                  `json:"userId"`
	AgentID        string                  `json:"agentId"`
	Query          string                  `json:"query"`
	Strategy       Strategy                `json:"strategy"`
	Trace          *bool                   `json:"trace,omitempty"`
	Scope          string                  `json:"scope,omitempty"`
	Categories     []string                `json:"categories,omitempty"`
	TimeRange      *TimeRange              `json:"timeRange,omitempty"`
	MetadataFilter *MetadataFilter         `json:"metadataFilter,omitempty"`
	Include        *RetrieveIncludeOptions `json:"include,omitempty"`
}

type TimeRange struct {
	Field string     `json:"field,omitempty"`
	From  *time.Time `json:"from,omitempty"`
	To    *time.Time `json:"to,omitempty"`
}

type MetadataCondition struct {
	Path  string `json:"path"`
	Op    string `json:"op"`
	Value any    `json:"value,omitempty"`
}

type MetadataFilter struct {
	All []MetadataCondition `json:"all,omitempty"`
	Any []MetadataCondition `json:"any,omitempty"`
	Not []MetadataCondition `json:"not,omitempty"`
}

type RetrieveIncludeOptions struct {
	RawDataMetadata *bool `json:"rawDataMetadata,omitempty"`
	RawDataSegment  *bool `json:"rawDataSegment,omitempty"`
}

type RawDataQueryIncludeOptions struct {
	Segment  *bool `json:"segment,omitempty"`
	Metadata *bool `json:"metadata,omitempty"`
}

type QueryMemoryItemsRequest struct {
	UserID         string          `json:"userId"`
	AgentID        string          `json:"agentId"`
	Scope          string          `json:"scope,omitempty"`
	Categories     []string        `json:"categories,omitempty"`
	SourceClients  []string        `json:"sourceClients,omitempty"`
	RawDataTypes   []string        `json:"rawDataTypes,omitempty"`
	TimeRange      *TimeRange      `json:"timeRange,omitempty"`
	MetadataFilter *MetadataFilter `json:"metadataFilter,omitempty"`
	Limit          *int            `json:"limit,omitempty"`
	Cursor         string          `json:"cursor,omitempty"`
}

type QueryMemoryRawDataRequest struct {
	UserID         string                      `json:"userId"`
	AgentID        string                      `json:"agentId"`
	Types          []string                    `json:"types,omitempty"`
	SourceClients  []string                    `json:"sourceClients,omitempty"`
	TimeRange      *TimeRange                  `json:"timeRange,omitempty"`
	MetadataFilter *MetadataFilter             `json:"metadataFilter,omitempty"`
	Include        *RawDataQueryIncludeOptions `json:"include,omitempty"`
	Limit          *int                        `json:"limit,omitempty"`
	Cursor         string                      `json:"cursor,omitempty"`
}

type HealthResponse struct {
	Status  string `json:"status"`
	Service string `json:"service"`
}

type ExtractMemoryResponse struct {
	Status         string   `json:"status"`
	RawDataIDs     []string `json:"rawDataIds"`
	ItemIDs        []int64  `json:"itemIds"`
	InsightIDs     []int64  `json:"insightIds"`
	InsightPending bool     `json:"insightPending"`
	DurationMillis *int64   `json:"durationMillis,omitempty"`
	ErrorMessage   string   `json:"errorMessage,omitempty"`
}

type AddMessageResponse struct {
	Triggered bool                   `json:"triggered"`
	Result    *ExtractMemoryResponse `json:"result,omitempty"`
}

type OperationAccepted struct {
	OperationID string `json:"operationId"`
	Status      string `json:"status"`
	Mode        string `json:"mode"`
}

type RetrieveMemoryResponse struct {
	Status    string              `json:"status,omitempty"`
	Items     []RetrievedItem     `json:"items"`
	Insights  []RetrievedInsight  `json:"insights"`
	RawData   []RetrievedRawData  `json:"rawData"`
	Evidences []string            `json:"evidences"`
	Strategy  string              `json:"strategy,omitempty"`
	Query     string              `json:"query,omitempty"`
	Trace     *RetrievalTraceView `json:"trace,omitempty"`
}

type RetrievedItem struct {
	ID          string         `json:"id"`
	Text        string         `json:"text"`
	VectorScore float32        `json:"vectorScore"`
	FinalScore  float64        `json:"finalScore"`
	OccurredAt  *time.Time     `json:"occurredAt,omitempty"`
	Category    string         `json:"category,omitempty"`
	Metadata    map[string]any `json:"metadata,omitempty"`
}

type RetrievedInsight struct {
	ID   string `json:"id"`
	Text string `json:"text"`
	Tier string `json:"tier,omitempty"`
}

type RetrievedRawData struct {
	RawDataID    string         `json:"rawDataId"`
	Caption      string         `json:"caption,omitempty"`
	MaxScore     float64        `json:"maxScore"`
	ItemIDs      []string       `json:"itemIds,omitempty"`
	Type         string         `json:"type,omitempty"`
	SourceClient string         `json:"sourceClient,omitempty"`
	Metadata     map[string]any `json:"metadata,omitempty"`
	StartTime    *time.Time     `json:"startTime,omitempty"`
	EndTime      *time.Time     `json:"endTime,omitempty"`
	CreatedAt    *time.Time     `json:"createdAt,omitempty"`
}

type QueryMemoryItemsResponse struct {
	Items      []MemoryItem `json:"items"`
	NextCursor string       `json:"nextCursor,omitempty"`
}

type MemoryItem struct {
	ID           string         `json:"id"`
	Text         string         `json:"text"`
	Scope        string         `json:"scope,omitempty"`
	Category     string         `json:"category,omitempty"`
	Type         string         `json:"type,omitempty"`
	RawDataID    string         `json:"rawDataId,omitempty"`
	RawDataType  string         `json:"rawDataType,omitempty"`
	SourceClient string         `json:"sourceClient,omitempty"`
	OccurredAt   *time.Time     `json:"occurredAt,omitempty"`
	ObservedAt   *time.Time     `json:"observedAt,omitempty"`
	CreatedAt    *time.Time     `json:"createdAt,omitempty"`
	Metadata     map[string]any `json:"metadata,omitempty"`
}

type QueryMemoryRawDataResponse struct {
	RawData    []MemoryRawData `json:"rawData"`
	NextCursor string          `json:"nextCursor,omitempty"`
}

type MemoryRawData struct {
	ID           string         `json:"id"`
	Type         string         `json:"type,omitempty"`
	SourceClient string         `json:"sourceClient,omitempty"`
	Caption      string         `json:"caption,omitempty"`
	Metadata     map[string]any `json:"metadata,omitempty"`
	Segment      map[string]any `json:"segment,omitempty"`
	StartTime    *time.Time     `json:"startTime,omitempty"`
	EndTime      *time.Time     `json:"endTime,omitempty"`
	CreatedAt    *time.Time     `json:"createdAt,omitempty"`
}

type RetrievalTraceView struct {
	TraceID      string      `json:"traceId,omitempty"`
	StartedAt    *time.Time  `json:"startedAt,omitempty"`
	CompletedAt  *time.Time  `json:"completedAt,omitempty"`
	Truncated    *bool       `json:"truncated,omitempty"`
	Stages       []StageView `json:"stages"`
	Merge        *MergeView  `json:"merge,omitempty"`
	FinalResults *FinalView  `json:"finalResults,omitempty"`
}

type StageView struct {
	Stage          string           `json:"stage,omitempty"`
	Tier           string           `json:"tier,omitempty"`
	Method         string           `json:"method,omitempty"`
	Status         string           `json:"status,omitempty"`
	InputCount     *int             `json:"inputCount,omitempty"`
	CandidateCount *int             `json:"candidateCount,omitempty"`
	ResultCount    *int             `json:"resultCount,omitempty"`
	Degraded       bool             `json:"degraded"`
	Skipped        bool             `json:"skipped"`
	StartedAt      *time.Time       `json:"startedAt,omitempty"`
	DurationMillis *int64           `json:"durationMillis,omitempty"`
	Attributes     map[string]any   `json:"attributes,omitempty"`
	Candidates     []map[string]any `json:"candidates,omitempty"`
}

type MergeView struct {
	InputCount        int    `json:"inputCount"`
	OutputCount       int    `json:"outputCount"`
	DeduplicatedCount int    `json:"deduplicatedCount"`
	SourceCount       int    `json:"sourceCount"`
	Status            string `json:"status,omitempty"`
}

type FinalView struct {
	Strategy      string `json:"strategy,omitempty"`
	Status        string `json:"status,omitempty"`
	ItemCount     int    `json:"itemCount"`
	InsightCount  int    `json:"insightCount"`
	RawDataCount  int    `json:"rawDataCount"`
	EvidenceCount int    `json:"evidenceCount"`
}
