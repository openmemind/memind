# Memind Rust Client

Official Rust client for the Memind memory engine Open API.

## Installation

```toml
[dependencies]
memind = "0.2"
tokio = { version = "1", features = ["macros", "rt-multi-thread"] }
```

## Quick Start

```rust,no_run
use memind::{
    ExtractMemoryRequest, MemindClient, Message, RawContent, RetrieveMemoryRequest, Strategy,
};

#[tokio::main]
async fn main() -> memind::Result<()> {
    let client = MemindClient::builder()
        .base_url("http://localhost:8366")
        .api_token_from_env()
        .build()?;

    client.health().await?;

    let raw_content = RawContent::conversation(vec![
        Message::user("Remember that I prefer concise answers."),
    ])?;

    let extraction = client
        .memory()
        .extract(ExtractMemoryRequest::new("user-1", "agent-1", raw_content))
        .await?;

    println!("extraction status: {:?}", extraction.status);

    let result = client
        .memory()
        .retrieve(
            RetrieveMemoryRequest::new(
                "user-1",
                "agent-1",
                "What does the user prefer?",
                Strategy::Simple,
            )
            .trace(true),
        )
        .await?;

    println!("evidences: {:?}", result.evidences);
    Ok(())
}
```

## Retry Behavior

`health` and `retrieve` retry transient failures by default. `extract`,
`add_message`, and `commit` do not retry by default because retrying mutating
operations can duplicate extraction, buffered messages, or commits after an
ambiguous network failure.

```rust,no_run
use std::time::Duration;

# async fn example(client: memind::MemindClient, request: memind::ExtractMemoryRequest) -> memind::Result<()> {
let response = client
    .memory()
    .extract_with_options(
        request,
        memind::RequestOptions::new()
            .timeout(Duration::from_secs(60))
            .max_retries(1),
    )
    .await?;
# let _ = response;
# Ok(())
# }
```

## Error Handling

```rust,no_run
# async fn example(client: memind::MemindClient, request: memind::RetrieveMemoryRequest) -> memind::Result<()> {
match client.memory().retrieve(request).await {
    Ok(response) => {
        println!("items: {}", response.items.len());
    }
    Err(memind::MemindError::Api(err)) => {
        eprintln!(
            "api error: status={} code={} request_id={:?}",
            err.status, err.code, err.request_id
        );
    }
    Err(err) => return Err(err),
}
# Ok(())
# }
```
