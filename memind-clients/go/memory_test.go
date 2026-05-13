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

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

func TestHealthCallsEndpoint(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet || r.URL.Path != "/open/v1/health" {
			t.Fatalf("request = %s %s", r.Method, r.URL.Path)
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"data":{"status":"UP","service":"memind-server"}}`))
	}))
	defer server.Close()

	client, err := NewClient(WithBaseURL(server.URL))
	if err != nil {
		t.Fatalf("NewClient() error = %v", err)
	}
	resp, err := client.Health(context.Background())
	if err != nil {
		t.Fatalf("Health error = %v", err)
	}
	if resp.Status != "UP" {
		t.Fatalf("status = %q", resp.Status)
	}
}

func TestMemoryMethodsCallEndpoints(t *testing.T) {
	expected := []string{
		"POST /open/v1/memory/sync/extract",
		"POST /open/v1/memory/sync/add-message",
		"POST /open/v1/memory/sync/commit",
		"POST /open/v1/memory/retrieve",
		"POST /open/v1/memory/async/extract",
		"POST /open/v1/memory/async/add-message",
		"POST /open/v1/memory/async/commit",
	}
	var seen []string
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		seen = append(seen, r.Method+" "+r.URL.Path)
		w.Header().Set("Content-Type", "application/json")
		switch r.URL.Path {
		case "/open/v1/memory/sync/add-message":
			_, _ = w.Write([]byte(`{"data":{"triggered":false}}`))
		case "/open/v1/memory/retrieve":
			_, _ = w.Write([]byte(`{"data":{"items":[],"insights":[],"rawData":[],"evidences":[]}}`))
		case "/open/v1/memory/async/extract", "/open/v1/memory/async/add-message", "/open/v1/memory/async/commit":
			w.WriteHeader(http.StatusAccepted)
			_, _ = w.Write([]byte(`{"data":{"operationId":"op_1","status":"accepted","mode":"async"}}`))
		default:
			_, _ = w.Write([]byte(`{"data":{"status":"SUCCESS","rawDataIds":[],"itemIds":[],"insightIds":[],"insightPending":false}}`))
		}
	}))
	defer server.Close()

	client, err := NewClient(WithBaseURL(server.URL))
	if err != nil {
		t.Fatalf("NewClient() error = %v", err)
	}
	ctx := context.Background()
	extractReq := ExtractMemoryRequest{UserID: "u", AgentID: "a", RawContent: Conversation(UserMessage("hello"))}
	addReq := AddMessageRequest{UserID: "u", AgentID: "a", Message: UserMessage("hello")}
	commitReq := CommitMemoryRequest{UserID: "u", AgentID: "a"}
	retrieveReq := RetrieveMemoryRequest{UserID: "u", AgentID: "a", Query: "q", Strategy: StrategySimple}

	_, _ = client.Memory.Extract(ctx, extractReq)
	_, _ = client.Memory.AddMessage(ctx, addReq)
	_, _ = client.Memory.Commit(ctx, commitReq)
	_, _ = client.Memory.Retrieve(ctx, retrieveReq)
	_, _ = client.Memory.EnqueueExtract(ctx, extractReq)
	_, _ = client.Memory.EnqueueAddMessage(ctx, addReq)
	_, _ = client.Memory.EnqueueCommit(ctx, commitReq)

	if len(seen) != len(expected) {
		t.Fatalf("seen = %#v", seen)
	}
	for i := range expected {
		if seen[i] != expected[i] {
			t.Fatalf("seen[%d] = %q, want %q", i, seen[i], expected[i])
		}
	}
}

func TestMutatingMethodsDoNotRetryByDefault(t *testing.T) {
	var attempts int
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		attempts++
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusServiceUnavailable)
		_, _ = w.Write([]byte(`{"error":{"code":"unavailable","message":"retry"}}`))
	}))
	defer server.Close()

	client, err := NewClient(WithBaseURL(server.URL))
	if err != nil {
		t.Fatalf("NewClient() error = %v", err)
	}
	_, _ = client.Memory.Extract(context.Background(), ExtractMemoryRequest{UserID: "u", AgentID: "a", RawContent: Conversation(UserMessage("hello"))})
	if attempts != 1 {
		t.Fatalf("attempts = %d, want 1", attempts)
	}
}

func TestMutatingMethodsRespectRequestRetryOverride(t *testing.T) {
	var attempts int
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		attempts++
		w.Header().Set("Content-Type", "application/json")
		if attempts == 1 {
			w.Header().Set("Retry-After", "0")
			w.WriteHeader(http.StatusServiceUnavailable)
			_, _ = w.Write([]byte(`{"error":{"code":"unavailable","message":"retry"}}`))
			return
		}
		_, _ = w.Write([]byte(`{"data":{"status":"SUCCESS","rawDataIds":[],"itemIds":[],"insightIds":[],"insightPending":false}}`))
	}))
	defer server.Close()

	client, err := NewClient(WithBaseURL(server.URL))
	if err != nil {
		t.Fatalf("NewClient() error = %v", err)
	}
	resp, err := client.Memory.Extract(
		context.Background(),
		ExtractMemoryRequest{UserID: "u", AgentID: "a", RawContent: Conversation(UserMessage("hello"))},
		WithRequestMaxRetries(1),
	)
	if err != nil {
		t.Fatalf("Extract error = %v", err)
	}
	if resp.Status != "SUCCESS" || attempts != 2 {
		t.Fatalf("status=%q attempts=%d, want SUCCESS and 2 attempts", resp.Status, attempts)
	}
}

func TestRetrieveRequestRetryOverrideCanDisableDefaultRetries(t *testing.T) {
	var attempts int
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		attempts++
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusServiceUnavailable)
		_, _ = w.Write([]byte(`{"error":{"code":"unavailable","message":"retry"}}`))
	}))
	defer server.Close()

	client, err := NewClient(WithBaseURL(server.URL), WithMaxRetries(2))
	if err != nil {
		t.Fatalf("NewClient() error = %v", err)
	}
	_, _ = client.Memory.Retrieve(
		context.Background(),
		RetrieveMemoryRequest{UserID: "u", AgentID: "a", Query: "q", Strategy: StrategySimple},
		WithRequestMaxRetries(0),
	)
	if attempts != 1 {
		t.Fatalf("attempts = %d, want 1", attempts)
	}
}

func TestRequestTimeoutOverridesClientTimeout(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		time.Sleep(50 * time.Millisecond)
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"data":{"status":"UP","service":"memind-server"}}`))
	}))
	defer server.Close()

	client, err := NewClient(
		WithBaseURL(server.URL),
		WithTimeout(10*time.Millisecond),
	)
	if err != nil {
		t.Fatalf("NewClient() error = %v", err)
	}
	if _, err := client.Health(context.Background(), WithRequestTimeout(200*time.Millisecond)); err != nil {
		t.Fatalf("Health with request timeout override error = %v", err)
	}
}
