# Memind Go Client

Official Go client for the Memind memory engine Open API.

## Installation

```bash
go get github.com/openmemind/memind/memind-clients/go
```

## Quick Start

```go
package main

import (
	"context"
	"errors"
	"log"
	"os"

	memind "github.com/openmemind/memind/memind-clients/go"
)

func main() {
	ctx := context.Background()

	client, err := memind.NewClient(
		memind.WithBaseURL("http://localhost:8366"),
		memind.WithAPIToken(os.Getenv("MEMIND_API_TOKEN")),
	)
	if err != nil {
		log.Fatal(err)
	}

	health, err := client.Health(ctx)
	if err != nil {
		log.Fatal(err)
	}
	log.Println(health.Status)

	extract, err := client.Memory.Extract(ctx, memind.ExtractMemoryRequest{
		UserID:     "user-1",
		AgentID:    "agent-1",
		RawContent: memind.Conversation(memind.UserMessage("Remember that I prefer concise answers.")),
	})
	if err != nil {
		var apiErr *memind.APIError
		if errors.As(err, &apiErr) {
			log.Printf("memind API error: status=%d code=%s requestID=%s", apiErr.StatusCode, apiErr.ErrorCode, apiErr.RequestID)
		}
		log.Fatal(err)
	}
	log.Println(extract.Status)

	result, err := client.Memory.Retrieve(ctx, memind.RetrieveMemoryRequest{
		UserID:   "user-1",
		AgentID:  "agent-1",
		Query:    "What does the user like?",
		Strategy: memind.StrategySimple,
	})
	if err != nil {
		log.Fatal(err)
	}
	log.Println(len(result.Items))
}
```

## Configuration

Configuration precedence:

1. Constructor options
2. Environment variables: `MEMIND_BASE_URL`, `MEMIND_API_TOKEN`
3. Defaults

| Setting | Option | Default |
| --- | --- | --- |
| Base URL | `WithBaseURL` | Required |
| API token | `WithAPIToken` | Omitted |
| Timeout | `WithTimeout` | `30s` |
| Max retries | `WithMaxRetries` | `2` |
| HTTP client | `WithHTTPClient` | `http.DefaultClient` |
| Extra header | `WithHeader` | None |
| User agent | `WithUserAgent` | `memind-go/<version>` |

`Authorization` is sent only when an API token is configured. SDK-owned headers cannot be overridden by custom headers.

## Raw Content

Use conversation raw content for chat-like memory extraction:

```go
raw := memind.Conversation(
	memind.UserMessage("My preferred timezone is Asia/Shanghai."),
	memind.AssistantMessage("I will remember that."),
)
```

Use map raw content for plugin-specific object payloads:

```go
raw, err := memind.RawMap("document", map[string]any{
	"title": "Project note",
	"body":  "The release checklist is ready.",
})
```

Custom raw content types can implement `json.Marshaler`. The client validates the marshaled root object and required `type` field before network I/O.

## Retry Behavior

`Health` and `Retrieve` retry transient failures by default. `Extract`, `AddMessage`, `Commit`, and async enqueue methods do not retry by default because they mutate server state.

Opt into retries for mutating calls only when duplicate side effects are acceptable:

```go
resp, err := client.Memory.Extract(ctx, req, memind.WithRequestMaxRetries(1))
```

Treat only `ExtractMemoryResponse.Status == "SUCCESS"` as safe to clear caller-owned retry payloads. `PARTIAL_SUCCESS` is returned as successful data so applications can decide whether to keep or re-enqueue the original payload.

## Async Enqueue

Async methods enqueue server work and return accepted operation metadata only:

```go
accepted, err := client.Memory.EnqueueExtract(ctx, req)
```

The Open API currently does not expose polling endpoints, so this client does not provide completion tracking.

## Development

```bash
go test ./...
go test -race ./...
go vet ./...
gofmt -w .
```
