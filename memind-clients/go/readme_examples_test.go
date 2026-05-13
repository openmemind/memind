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

package memind_test

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"

	memind "github.com/openmemind/memind/memind-clients/go"
)

func Example_readmeQuickStart() {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		switch r.URL.Path {
		case "/open/v1/health":
			_, _ = w.Write([]byte(`{"data":{"status":"UP","service":"memind-server"}}`))
		case "/open/v1/memory/sync/extract":
			_, _ = w.Write([]byte(`{"data":{"status":"SUCCESS","rawDataIds":[],"itemIds":[],"insightIds":[],"insightPending":false}}`))
		case "/open/v1/memory/retrieve":
			_, _ = w.Write([]byte(`{"data":{"items":[],"insights":[],"rawData":[],"evidences":[]}}`))
		default:
			w.WriteHeader(http.StatusNotFound)
			_, _ = w.Write([]byte(`{"error":{"code":"not_found","message":"not found"}}`))
		}
	}))
	defer server.Close()

	ctx := context.Background()

	client, err := memind.NewClient(
		memind.WithBaseURL(server.URL),
		memind.WithAPIToken(os.Getenv("MEMIND_API_TOKEN")),
	)
	if err != nil {
		panic(err)
	}

	health, err := client.Health(ctx)
	if err != nil {
		panic(err)
	}
	fmt.Println(health.Status)

	extract, err := client.Memory.Extract(ctx, memind.ExtractMemoryRequest{
		UserID:     "user-1",
		AgentID:    "agent-1",
		RawContent: memind.Conversation(memind.UserMessage("Remember that I prefer concise answers.")),
	})
	if err != nil {
		var apiErr *memind.APIError
		if errors.As(err, &apiErr) {
			fmt.Println(apiErr.StatusCode, apiErr.ErrorCode, apiErr.RequestID)
		}
		panic(err)
	}
	fmt.Println(extract.Status)

	result, err := client.Memory.Retrieve(ctx, memind.RetrieveMemoryRequest{
		UserID:   "user-1",
		AgentID:  "agent-1",
		Query:    "What does the user like?",
		Strategy: memind.StrategySimple,
	})
	if err != nil {
		panic(err)
	}
	fmt.Println(len(result.Items))

	// Output:
	// UP
	// SUCCESS
	// 0
}

func Example_rawContent() {
	raw := memind.Conversation(
		memind.UserMessage("My preferred timezone is Asia/Shanghai."),
		memind.AssistantMessage("I will remember that."),
	)
	fmt.Printf("%T\n", raw)

	mapped, err := memind.RawMap("document", map[string]any{
		"title": "Project note",
		"body":  "The release checklist is ready.",
	})
	if err != nil {
		panic(err)
	}
	fmt.Printf("%T\n", mapped)

	// Output:
	// memind.ConversationContent
	// memind.MapRawContent
}
