# Memind Java Client

Official Java client modules for the Memind memory engine API.

## Agent Timeline Raw Content

The Java client keeps extension raw-content payloads available through `MapRawContent`. Coding-agent
integrations can submit `agent_timeline` data without waiting for a dedicated Java model:

```java
import com.openmemind.ai.client.MemindClient;
import com.openmemind.ai.client.model.common.MapRawContent;
import com.openmemind.ai.client.model.request.ExtractMemoryRequest;
import java.util.List;
import java.util.Map;

try (MemindClient client = MemindClient.builder().baseUrl("http://localhost:8366").build()) {
    var timeline =
            MapRawContent.of(
                    "agent_timeline",
                    Map.of(
                            "sourceClient", "claude-code",
                            "sessionId", "session-123",
                            "agentTurnId", "session-123-agent-turn-1-1",
                            "timelineId", "session-123-agent-1-2",
                            "events",
                            List.of(
                                    Map.of(
                                            "eventId", "event-id",
                                            "seq", 1,
                                            "kind", "command",
                                            "toolName", "Bash",
                                            "command", "npm test payment",
                                            "status", "failed",
                                            "exitCode", 1,
                                            "output", "{\"stdout\": \"rounding mismatch\"}"))));

    client.extract(
            ExtractMemoryRequest.builder()
                    .userId("local__alice")
                    .agentId("claude-code__project_hash")
                    .sourceClient("claude-code")
                    .rawContent(timeline)
                    .build());
}
```

The payload is sent through the normal synchronous extraction endpoint with `rawContent.type =
"agent_timeline"`.
