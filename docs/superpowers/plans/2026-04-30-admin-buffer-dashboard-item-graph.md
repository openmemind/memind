# Admin Buffer Dashboard Item Graph Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add high-quality Admin APIs for buffer inspection/correction, dashboard data, and item graph inspection/correction in `memind-server`.

**Architecture:** Follow the existing Spring MVC + service + admin query mapper pattern. Keep public Admin APIs compact while using index-friendly `user_id`/`agent_id` predicates internally. Use physical deletes for insight buffer and item graph correction deletes where table unique identities would conflict with logical-delete tombstones.

**Tech Stack:** Java 17, Spring Boot MVC, Jakarta Validation, MyBatis Plus, SQLite/MySQL-compatible mapper code, JUnit 5, MockMvc, AssertJ, Maven.

---

## Source Spec

Implement the approved spec:

- `docs/superpowers/specs/2026-04-30-admin-buffer-dashboard-item-graph-design.md`

Do not add features outside that spec. In particular, do not add graph rebuild, graph retry, entity merge, hand-authored graph creation, auth, or OpenAPI docs in this implementation.

## File Structure

### Shared Admin Support

- Create `memind-server/src/main/java/com/openmemind/ai/memory/server/domain/common/AdminUpdateResult.java`
  - Response record for patch endpoints: `updatedCount`, `affectedMemoryIds`.
- Create `memind-server/src/main/java/com/openmemind/ai/memory/server/support/AdminMemoryScope.java`
  - Parses optional `memoryId` into `userId` and `agentId`.
  - Applies `user_id` and `agent_id` predicates to MyBatis wrappers.
- Test `memind-server/src/test/java/com/openmemind/ai/memory/server/support/AdminMemoryScopeTest.java`

### Buffer Admin API

- Create `memind-server/src/main/java/com/openmemind/ai/memory/server/controller/admin/buffer/AdminBufferController.java`
- Create `memind-server/src/main/java/com/openmemind/ai/memory/server/domain/buffer/query/ConversationBufferPageQuery.java`
- Create `memind-server/src/main/java/com/openmemind/ai/memory/server/domain/buffer/query/InsightBufferPageQuery.java`
- Create `memind-server/src/main/java/com/openmemind/ai/memory/server/domain/buffer/request/AdminIdsRequest.java`
- Create `memind-server/src/main/java/com/openmemind/ai/memory/server/domain/buffer/request/InsightBufferBuiltUpdateRequest.java`
- Create `memind-server/src/main/java/com/openmemind/ai/memory/server/domain/buffer/request/InsightBufferGroupUpdateRequest.java`
- Create `memind-server/src/main/java/com/openmemind/ai/memory/server/domain/buffer/view/ConversationBufferView.java`
- Create `memind-server/src/main/java/com/openmemind/ai/memory/server/domain/buffer/view/InsightBufferView.java`
- Create `memind-server/src/main/java/com/openmemind/ai/memory/server/domain/buffer/view/InsightBufferGroupView.java`
- Create `memind-server/src/main/java/com/openmemind/ai/memory/server/mapper/buffer/AdminBufferQueryMapper.java`
- Create `memind-server/src/main/java/com/openmemind/ai/memory/server/service/buffer/BufferQueryService.java`
- Create `memind-server/src/main/java/com/openmemind/ai/memory/server/service/buffer/BufferManagementService.java`
- Test `memind-server/src/test/java/com/openmemind/ai/memory/server/controller/admin/buffer/AdminBufferControllerTest.java`

### Dashboard Admin API

- Create `memind-server/src/main/java/com/openmemind/ai/memory/server/controller/admin/dashboard/AdminDashboardController.java`
- Create `memind-server/src/main/java/com/openmemind/ai/memory/server/domain/dashboard/view/AdminDashboardView.java`
- Create `memind-server/src/main/java/com/openmemind/ai/memory/server/mapper/dashboard/AdminDashboardQueryMapper.java`
- Create `memind-server/src/main/java/com/openmemind/ai/memory/server/service/dashboard/DashboardQueryService.java`
- Test `memind-server/src/test/java/com/openmemind/ai/memory/server/controller/admin/dashboard/AdminDashboardControllerTest.java`

### Item Graph Admin API

- Create `memind-server/src/main/java/com/openmemind/ai/memory/server/controller/admin/itemgraph/AdminItemGraphController.java`
- Create `memind-server/src/main/java/com/openmemind/ai/memory/server/domain/itemgraph/query/ItemGraphPageQueries.java`
- Create `memind-server/src/main/java/com/openmemind/ai/memory/server/domain/itemgraph/request/GraphEntityDeleteRequest.java`
- Create `memind-server/src/main/java/com/openmemind/ai/memory/server/domain/itemgraph/request/GraphIdsRequest.java`
- Create `memind-server/src/main/java/com/openmemind/ai/memory/server/domain/itemgraph/view/AdminGraphEntityDeleteResult.java`
- Create `memind-server/src/main/java/com/openmemind/ai/memory/server/domain/itemgraph/view/GraphEntityDetailView.java`
- Create `memind-server/src/main/java/com/openmemind/ai/memory/server/domain/itemgraph/view/ItemGraphViews.java`
- Create `memind-server/src/main/java/com/openmemind/ai/memory/server/mapper/itemgraph/AdminItemGraphQueryMapper.java`
- Create `memind-server/src/main/java/com/openmemind/ai/memory/server/service/itemgraph/ItemGraphQueryService.java`
- Create `memind-server/src/main/java/com/openmemind/ai/memory/server/service/itemgraph/ItemGraphManagementService.java`
- Test `memind-server/src/test/java/com/openmemind/ai/memory/server/controller/admin/itemgraph/AdminItemGraphControllerTest.java`

### Integration Coverage

- Modify `memind-server/src/test/java/com/openmemind/ai/memory/server/MemindServerIntegrationTest.java`
  - Seed buffer, dashboard, and graph rows.
  - Verify endpoint behavior against real SQLite tables.
  - Verify physical-delete behavior for unique-identity tables.

---

## Task 1: Shared Admin Scope And Update Result

**Files:**
- Create: `memind-server/src/main/java/com/openmemind/ai/memory/server/domain/common/AdminUpdateResult.java`
- Create: `memind-server/src/main/java/com/openmemind/ai/memory/server/support/AdminMemoryScope.java`
- Test: `memind-server/src/test/java/com/openmemind/ai/memory/server/support/AdminMemoryScopeTest.java`

- [ ] **Step 1: Write the failing test**

Create `AdminMemoryScopeTest.java`:

```java
package com.openmemind.ai.memory.server.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AdminMemoryScopeTest {

    @Test
    void parsesUserOnlyMemoryId() {
        AdminMemoryScope scope = AdminMemoryScope.fromMemoryId("user-1");

        assertThat(scope.present()).isTrue();
        assertThat(scope.userId()).isEqualTo("user-1");
        assertThat(scope.agentId()).isNull();
        assertThat(scope.memoryId()).isEqualTo("user-1");
    }

    @Test
    void parsesUserAgentMemoryId() {
        AdminMemoryScope scope = AdminMemoryScope.fromMemoryId("user-1:agent-1");

        assertThat(scope.present()).isTrue();
        assertThat(scope.userId()).isEqualTo("user-1");
        assertThat(scope.agentId()).isEqualTo("agent-1");
        assertThat(scope.memoryId()).isEqualTo("user-1:agent-1");
    }

    @Test
    void emptyMemoryIdMeansGlobalScope() {
        AdminMemoryScope scope = AdminMemoryScope.fromMemoryId(" ");

        assertThat(scope.present()).isFalse();
        assertThat(scope.userId()).isNull();
        assertThat(scope.agentId()).isNull();
        assertThat(scope.memoryId()).isNull();
    }

    @Test
    void rejectsBlankUserPart() {
        assertThatThrownBy(() -> AdminMemoryScope.fromMemoryId(":agent-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("memoryId");
    }
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
mvn -pl memind-server -Dtest=AdminMemoryScopeTest test
```

Expected: compilation fails because `AdminMemoryScope` does not exist.

- [ ] **Step 3: Add shared response and scope implementation**

Create `AdminUpdateResult.java`:

```java
package com.openmemind.ai.memory.server.domain.common;

import java.util.List;

public record AdminUpdateResult(int updatedCount, List<String> affectedMemoryIds) {
    public AdminUpdateResult {
        affectedMemoryIds = affectedMemoryIds == null ? List.of() : List.copyOf(affectedMemoryIds);
    }
}
```

Create `AdminMemoryScope.java`:

```java
package com.openmemind.ai.memory.server.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.util.function.Function;
import org.springframework.util.StringUtils;

public record AdminMemoryScope(String memoryId, String userId, String agentId) {

    public static AdminMemoryScope fromMemoryId(String memoryId) {
        if (!StringUtils.hasText(memoryId)) {
            return new AdminMemoryScope(null, null, null);
        }
        String trimmed = memoryId.trim();
        int separator = trimmed.indexOf(':');
        if (separator < 0) {
            return new AdminMemoryScope(trimmed, trimmed, null);
        }
        String userId = trimmed.substring(0, separator);
        String agentId = trimmed.substring(separator + 1);
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("memoryId must include a non-empty user id");
        }
        return new AdminMemoryScope(trimmed, userId, StringUtils.hasText(agentId) ? agentId : null);
    }

    public boolean present() {
        return memoryId != null;
    }

    public <T> void applyToQuery(
            LambdaQueryWrapper<T> wrapper,
            Function<T, String> userColumn,
            Function<T, String> agentColumn) {
        if (!present()) {
            return;
        }
        wrapper.eq(userColumn, userId);
        if (agentId != null) {
            wrapper.eq(agentColumn, agentId);
        } else {
            wrapper.isNull(agentColumn);
        }
    }

    public <T> void applyToUpdate(
            LambdaUpdateWrapper<T> wrapper,
            Function<T, String> userColumn,
            Function<T, String> agentColumn) {
        if (!present()) {
            return;
        }
        wrapper.eq(userColumn, userId);
        if (agentId != null) {
            wrapper.eq(agentColumn, agentId);
        } else {
            wrapper.isNull(agentColumn);
        }
    }
}
```

If Java method references for MyBatis lambda wrappers do not accept `Function<T, String>` in this codebase, change the two helper methods to return only parsed values and apply predicates directly in each mapper. Keep the `fromMemoryId()` parsing contract and tests unchanged.

- [ ] **Step 4: Run the focused test and verify it passes**

Run:

```bash
mvn -pl memind-server -Dtest=AdminMemoryScopeTest test
```

Expected: test passes.

- [ ] **Step 5: Commit**

```bash
git add memind-server/src/main/java/com/openmemind/ai/memory/server/domain/common/AdminUpdateResult.java \
        memind-server/src/main/java/com/openmemind/ai/memory/server/support/AdminMemoryScope.java \
        memind-server/src/test/java/com/openmemind/ai/memory/server/support/AdminMemoryScopeTest.java
git commit -m "feat: add admin memory scope helpers"
```

---

## Task 2: Buffer Query API

**Files:**
- Create: `memind-server/src/main/java/com/openmemind/ai/memory/server/controller/admin/buffer/AdminBufferController.java`
- Create: `memind-server/src/main/java/com/openmemind/ai/memory/server/domain/buffer/query/ConversationBufferPageQuery.java`
- Create: `memind-server/src/main/java/com/openmemind/ai/memory/server/domain/buffer/query/InsightBufferPageQuery.java`
- Create: `memind-server/src/main/java/com/openmemind/ai/memory/server/domain/buffer/view/ConversationBufferView.java`
- Create: `memind-server/src/main/java/com/openmemind/ai/memory/server/domain/buffer/view/InsightBufferView.java`
- Create: `memind-server/src/main/java/com/openmemind/ai/memory/server/domain/buffer/view/InsightBufferGroupView.java`
- Create: `memind-server/src/main/java/com/openmemind/ai/memory/server/mapper/buffer/AdminBufferQueryMapper.java`
- Create: `memind-server/src/main/java/com/openmemind/ai/memory/server/service/buffer/BufferQueryService.java`
- Create: `memind-server/src/main/java/com/openmemind/ai/memory/server/service/buffer/BufferManagementService.java`
- Test: `memind-server/src/test/java/com/openmemind/ai/memory/server/controller/admin/buffer/AdminBufferControllerTest.java`

- [ ] **Step 1: Write controller tests for read endpoints**

Create `AdminBufferControllerTest.java` with tests for:

```java
@Test
void conversationListDefaultsToPendingAndReturnsPagePayload() throws Exception {
    mockMvc.perform(get("/admin/v1/buffers/conversations"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("success"))
            .andExpect(jsonPath("$.data.current").value(1))
            .andExpect(jsonPath("$.data.list[0].sessionId").value("s1"))
            .andExpect(jsonPath("$.data.list[0].extracted").value(false));

    assertThat(queryService.recordedConversationQuery.state()).isEqualTo("pending");
    assertThat(queryService.recordedConversationQuery.pageNo()).isEqualTo(1);
    assertThat(queryService.recordedConversationQuery.pageSize()).isEqualTo(20);
}

@Test
void insightListDefaultsToUnbuiltAndReturnsPagePayload() throws Exception {
    mockMvc.perform(get("/admin/v1/buffers/insights"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("success"))
            .andExpect(jsonPath("$.data.current").value(1))
            .andExpect(jsonPath("$.data.list[0].insightTypeName").value("preference"))
            .andExpect(jsonPath("$.data.list[0].built").value(false));

    assertThat(queryService.recordedInsightQuery.state()).isEqualTo("unbuilt");
}

@Test
void insightGroupsReturnAggregates() throws Exception {
    mockMvc.perform(get("/admin/v1/buffers/insights/groups")
                    .param("memoryId", "u1:a1")
                    .param("insightTypeName", "preference"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("success"))
            .andExpect(jsonPath("$.data[0].memoryId").value("u1:a1"))
            .andExpect(jsonPath("$.data[0].groupName").value("project"))
            .andExpect(jsonPath("$.data[0].unbuilt").value(2));
}
```

Use the existing `AdminItemControllerTest` stub style: instantiate `MockMvcBuilders.standaloneSetup(new AdminBufferController(queryService, managementService))`, attach `ApiExceptionHandler`, and use stub services that extend the concrete services with `super(null)`.

- [ ] **Step 2: Run controller tests and verify they fail**

Run:

```bash
mvn -pl memind-server -Dtest=AdminBufferControllerTest test
```

Expected: compilation fails because buffer controller/domain/service classes do not exist.

- [ ] **Step 3: Add buffer domain records and controller**

Implement these records:

```java
public record ConversationBufferPageQuery(
        int pageNo, int pageSize, String memoryId, String sessionId, String state) {
    public static ConversationBufferPageQuery of(
            int pageNo, int pageSize, String memoryId, String sessionId, String state) {
        return new ConversationBufferPageQuery(
                pageNo, pageSize, memoryId, sessionId, state == null ? "pending" : state);
    }
}

public record InsightBufferPageQuery(
        int pageNo, int pageSize, String memoryId, String insightTypeName, String state) {
    public static InsightBufferPageQuery of(
            int pageNo, int pageSize, String memoryId, String insightTypeName, String state) {
        return new InsightBufferPageQuery(
                pageNo, pageSize, memoryId, insightTypeName, state == null ? "unbuilt" : state);
    }
}
```

Implement view records with exactly the fields from the spec:

```java
public record ConversationBufferView(
        Integer id,
        String sessionId,
        String userId,
        String agentId,
        String memoryId,
        String role,
        String content,
        String userName,
        String sourceClient,
        Instant timestamp,
        Boolean extracted,
        Instant createdAt,
        Instant updatedAt) {}

public record InsightBufferView(
        Integer id,
        String userId,
        String agentId,
        String memoryId,
        String insightTypeName,
        Long itemId,
        String groupName,
        Boolean built,
        Instant createdAt,
        Instant updatedAt) {}

public record InsightBufferGroupView(
        String memoryId,
        String insightTypeName,
        String groupName,
        long total,
        long unbuilt,
        long built) {}
```

Implement `AdminBufferController` with these mappings:

```java
@Validated
@RestController
@RequestMapping("/admin/v1/buffers")
public class AdminBufferController {
    @GetMapping("/conversations")
    public ApiResult<PageResult<ConversationBufferView>> conversations(
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String memoryId,
            @RequestParam(required = false) String sessionId,
            @RequestParam(defaultValue = "pending") String state) {
        return ApiResult.success(PageResult.from(queryService.listConversations(
                ConversationBufferPageQuery.of(pageNo, pageSize, memoryId, sessionId, state))));
    }

    @GetMapping("/conversations/{id}")
    public ApiResult<ConversationBufferView> conversationDetail(@PathVariable Integer id) {
        return ApiResult.success(queryService.getConversation(id));
    }

    @GetMapping("/insights")
    public ApiResult<PageResult<InsightBufferView>> insights(
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String memoryId,
            @RequestParam(required = false) String insightTypeName,
            @RequestParam(defaultValue = "unbuilt") String state) {
        return ApiResult.success(PageResult.from(queryService.listInsights(
                InsightBufferPageQuery.of(pageNo, pageSize, memoryId, insightTypeName, state))));
    }

    @GetMapping("/insights/groups")
    public ApiResult<List<InsightBufferGroupView>> insightGroups(
            @RequestParam(required = false) String memoryId,
            @RequestParam(required = false) String insightTypeName) {
        return ApiResult.success(queryService.listInsightGroups(memoryId, insightTypeName));
    }
}
```

- [ ] **Step 4: Add mapper and service query implementation**

Implement `AdminBufferQueryMapper` with a MyBatis component that:

- Uses `ConversationBufferMapper` and `InsightBufferMapper`.
- Applies `AdminMemoryScope.fromMemoryId(query.memoryId())`.
- Validates conversation state: `pending`, `extracted`, `all`.
- Validates insight state: `unbuilt`, `ungrouped`, `grouped`, `built`, `all`.
- Orders conversation rows by `createdAt DESC, id DESC`.
- Orders insight rows by `createdAt DESC, id DESC`.
- Throws `NoSuchElementException("Conversation buffer not found: " + id)` for missing details.

Use MyBatis Plus `Page<T>` and return `PageResponse<T>` just like `AdminItemQueryMapper`.

- [ ] **Step 5: Run controller tests and verify they pass**

Run:

```bash
mvn -pl memind-server -Dtest=AdminBufferControllerTest test
```

Expected: pass.

- [ ] **Step 6: Commit**

```bash
git add memind-server/src/main/java/com/openmemind/ai/memory/server/controller/admin/buffer \
        memind-server/src/main/java/com/openmemind/ai/memory/server/domain/buffer \
        memind-server/src/main/java/com/openmemind/ai/memory/server/mapper/buffer \
        memind-server/src/main/java/com/openmemind/ai/memory/server/service/buffer \
        memind-server/src/test/java/com/openmemind/ai/memory/server/controller/admin/buffer
git commit -m "feat: add admin buffer query APIs"
```

---

## Task 3: Buffer Correction API

**Files:**
- Modify: `memind-server/src/main/java/com/openmemind/ai/memory/server/controller/admin/buffer/AdminBufferController.java`
- Modify: `memind-server/src/main/java/com/openmemind/ai/memory/server/mapper/buffer/AdminBufferQueryMapper.java`
- Modify: `memind-server/src/main/java/com/openmemind/ai/memory/server/service/buffer/BufferManagementService.java`
- Create: `memind-server/src/main/java/com/openmemind/ai/memory/server/domain/buffer/request/AdminIdsRequest.java`
- Create: `memind-server/src/main/java/com/openmemind/ai/memory/server/domain/buffer/request/InsightBufferBuiltUpdateRequest.java`
- Create: `memind-server/src/main/java/com/openmemind/ai/memory/server/domain/buffer/request/InsightBufferGroupUpdateRequest.java`
- Test: `memind-server/src/test/java/com/openmemind/ai/memory/server/controller/admin/buffer/AdminBufferControllerTest.java`

- [ ] **Step 1: Add failing controller tests for correction endpoints**

Add tests:

```java
@Test
void markConversationExtractedRequiresIds() throws Exception {
    mockMvc.perform(patch("/admin/v1/buffers/conversations/extracted")
                    .contentType(APPLICATION_JSON)
                    .content("{\"ids\":[]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("bad_request"));
}

@Test
void markConversationExtractedReturnsUpdateResult() throws Exception {
    mockMvc.perform(patch("/admin/v1/buffers/conversations/extracted")
                    .contentType(APPLICATION_JSON)
                    .content("{\"ids\":[1,2]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.updatedCount").value(2))
            .andExpect(jsonPath("$.data.affectedMemoryIds[0]").value("u1:a1"));
}

@Test
void updateInsightGroupAllowsNullGroup() throws Exception {
    mockMvc.perform(patch("/admin/v1/buffers/insights/group")
                    .contentType(APPLICATION_JSON)
                    .content("{\"ids\":[10],\"groupName\":null}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.updatedCount").value(1));
}

@Test
void updateInsightBuiltRequiresBuiltFlag() throws Exception {
    mockMvc.perform(patch("/admin/v1/buffers/insights/built")
                    .contentType(APPLICATION_JSON)
                    .content("{\"ids\":[10]}"))
            .andExpect(status().isBadRequest());
}

@Test
void deleteInsightBufferRowsReturnsBatchDeleteResult() throws Exception {
    mockMvc.perform(delete("/admin/v1/buffers/insights")
                    .contentType(APPLICATION_JSON)
                    .content("{\"ids\":[10,11]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.deletedCount").value(2));
}
```

- [ ] **Step 2: Run the tests and verify they fail**

Run:

```bash
mvn -pl memind-server -Dtest=AdminBufferControllerTest test
```

Expected: fails because patch/delete mappings do not exist.

- [ ] **Step 3: Implement request records**

```java
public record AdminIdsRequest(@NotEmpty List<Integer> ids) {
    public AdminIdsRequest {
        ids = ids == null ? List.of() : List.copyOf(ids);
    }
}

public record InsightBufferGroupUpdateRequest(@NotEmpty List<Integer> ids, String groupName) {
    public InsightBufferGroupUpdateRequest {
        ids = ids == null ? List.of() : List.copyOf(ids);
    }
}

public record InsightBufferBuiltUpdateRequest(@NotEmpty List<Integer> ids, @NotNull Boolean built) {
    public InsightBufferBuiltUpdateRequest {
        ids = ids == null ? List.of() : List.copyOf(ids);
    }
}
```

- [ ] **Step 4: Implement controller mappings**

Add:

```java
@PatchMapping("/conversations/extracted")
public ApiResult<AdminUpdateResult> markConversationsExtracted(
        @Valid @RequestBody AdminIdsRequest request) {
    return ApiResult.success(managementService.markConversationsExtracted(request.ids()));
}

@DeleteMapping("/conversations")
public ApiResult<BatchDeleteResult> deleteConversations(
        @Valid @RequestBody AdminIdsRequest request) {
    return ApiResult.success(managementService.deleteConversations(request.ids()));
}

@PatchMapping("/insights/group")
public ApiResult<AdminUpdateResult> updateInsightGroup(
        @Valid @RequestBody InsightBufferGroupUpdateRequest request) {
    return ApiResult.success(managementService.updateInsightGroup(request.ids(), request.groupName()));
}

@PatchMapping("/insights/built")
public ApiResult<AdminUpdateResult> updateInsightBuilt(
        @Valid @RequestBody InsightBufferBuiltUpdateRequest request) {
    return ApiResult.success(managementService.updateInsightBuilt(request.ids(), request.built()));
}

@DeleteMapping("/insights")
public ApiResult<BatchDeleteResult> deleteInsights(@Valid @RequestBody AdminIdsRequest request) {
    return ApiResult.success(managementService.deleteInsightBuffers(request.ids()));
}
```

- [ ] **Step 5: Implement management methods**

`BufferManagementService` must:

- Load rows first by IDs to compute `affectedMemoryIds`.
- Conversation mark extracted: `UPDATE memory_conversation_buffer SET extracted=true WHERE id IN (...)`.
- Conversation delete: normal mapper delete by IDs is acceptable.
- Insight group/built: normal update by IDs.
- Insight delete: explicit physical delete, not MyBatis Plus logical delete.

For physical delete, add mapper method in `AdminBufferQueryMapper` implementation using `SqlRunner` or a mapper `@Delete` method. Use this SQL shape:

```sql
DELETE FROM memory_insight_buffer WHERE id IN (...)
```

Do not call `InsightBufferMapper.deleteBatchIds()` for insight correction delete.

- [ ] **Step 6: Run controller tests and verify they pass**

Run:

```bash
mvn -pl memind-server -Dtest=AdminBufferControllerTest test
```

Expected: pass.

- [ ] **Step 7: Commit**

```bash
git add memind-server/src/main/java/com/openmemind/ai/memory/server/controller/admin/buffer \
        memind-server/src/main/java/com/openmemind/ai/memory/server/domain/buffer \
        memind-server/src/main/java/com/openmemind/ai/memory/server/mapper/buffer \
        memind-server/src/main/java/com/openmemind/ai/memory/server/service/buffer \
        memind-server/src/test/java/com/openmemind/ai/memory/server/controller/admin/buffer
git commit -m "feat: add admin buffer correction APIs"
```

---

## Task 4: Dashboard API

**Files:**
- Create: `memind-server/src/main/java/com/openmemind/ai/memory/server/controller/admin/dashboard/AdminDashboardController.java`
- Create: `memind-server/src/main/java/com/openmemind/ai/memory/server/domain/dashboard/view/AdminDashboardView.java`
- Create: `memind-server/src/main/java/com/openmemind/ai/memory/server/mapper/dashboard/AdminDashboardQueryMapper.java`
- Create: `memind-server/src/main/java/com/openmemind/ai/memory/server/service/dashboard/DashboardQueryService.java`
- Test: `memind-server/src/test/java/com/openmemind/ai/memory/server/controller/admin/dashboard/AdminDashboardControllerTest.java`

- [ ] **Step 1: Write failing controller test**

Create tests:

```java
@Test
void dashboardDefaultsToSevenDaysAndReturnsSections() throws Exception {
    mockMvc.perform(get("/admin/v1/dashboard"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("success"))
            .andExpect(jsonPath("$.data.totals.rawData").value(3))
            .andExpect(jsonPath("$.data.backlog.conversationPending").value(1))
            .andExpect(jsonPath("$.data.activity.days").value(7))
            .andExpect(jsonPath("$.data.breakdown.sourceClients[0].name").value("claude-code"))
            .andExpect(jsonPath("$.data.healthSignals.graphEnabled").value(true));

    assertThat(queryService.recordedMemoryId).isNull();
    assertThat(queryService.recordedDays).isEqualTo(7);
}

@Test
void dashboardRejectsTooManyDays() throws Exception {
    mockMvc.perform(get("/admin/v1/dashboard").param("days", "31"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("bad_request"));
}

@Test
void dashboardPassesMemoryIdToService() throws Exception {
    mockMvc.perform(get("/admin/v1/dashboard").param("memoryId", "u1:a1").param("days", "14"))
            .andExpect(status().isOk());

    assertThat(queryService.recordedMemoryId).isEqualTo("u1:a1");
    assertThat(queryService.recordedDays).isEqualTo(14);
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
mvn -pl memind-server -Dtest=AdminDashboardControllerTest test
```

Expected: compilation fails because dashboard classes do not exist.

- [ ] **Step 3: Implement dashboard view records**

Create `AdminDashboardView` as one top-level record with nested records:

```java
public record AdminDashboardView(
        Totals totals,
        Backlog backlog,
        Activity activity,
        Breakdown breakdown,
        HealthSignals healthSignals) {
    public record Totals(long rawData, long items, long insights, long memoryThreads,
                         long graphEntities, long itemLinks) {}
    public record Backlog(long conversationPending, long insightUnbuilt, long insightUngrouped,
                          long threadOutboxPending, long threadOutboxFailed,
                          long graphBatchRepairRequired) {}
    public record Activity(int days, List<DailyCount> rawDataCreated,
                           List<DailyCount> itemsCreated, List<DailyCount> insightsCreated) {}
    public record DailyCount(String date, long count) {}
    public record Breakdown(List<NamedCount> sourceClients, List<NamedCount> rawDataTypes,
                            List<NamedCount> itemTypes, List<NamedCount> insightTypes,
                            List<NamedCount> graphLinkTypes) {}
    public record NamedCount(String name, long count) {}
    public record HealthSignals(boolean graphEnabled, boolean retrievalGraphAssistEnabled,
                                List<NamedCount> threadProjectionStates) {}
}
```

- [ ] **Step 4: Implement controller and service**

Controller:

```java
@Validated
@RestController
@RequestMapping("/admin/v1/dashboard")
public class AdminDashboardController {
    @GetMapping
    public ApiResult<AdminDashboardView> get(
            @RequestParam(required = false) String memoryId,
            @RequestParam(defaultValue = "7") @Min(1) @Max(30) int days) {
        return ApiResult.success(queryService.getDashboard(memoryId, days));
    }
}
```

Service:

```java
@Service
public class DashboardQueryService {
    private final AdminDashboardQueryMapper mapper;
    private final MemoryOptionService memoryOptionService;

    public AdminDashboardView getDashboard(String memoryId, int days) {
        var snapshot = memoryOptionService.getCurrent();
        Map<String, MemoryOptionItemView> options = flattenOptions(snapshot.config());
        boolean graphEnabled = booleanOption(options, "extraction.item.graph.enabled");
        boolean retrievalGraphAssistEnabled =
                booleanOption(options, "retrieval.simple.graphAssist.enabled")
                        || booleanOption(options, "retrieval.deep.graphAssist.enabled");
        return mapper.dashboard(memoryId, days, graphEnabled, retrievalGraphAssistEnabled);
    }

    private static Map<String, MemoryOptionItemView> flattenOptions(
            Map<String, List<MemoryOptionItemView>> config) {
        Map<String, MemoryOptionItemView> options = new LinkedHashMap<>();
        config.values().forEach(items -> items.forEach(item -> options.put(item.key(), item)));
        return options;
    }

    private static boolean booleanOption(Map<String, MemoryOptionItemView> options, String key) {
        MemoryOptionItemView item = options.get(key);
        return item != null && Boolean.TRUE.equals(item.value());
    }
}
```

Use `MemoryOptionsSnapshot.config()` because that is the public snapshot accessor. Import `MemoryOptionItemView`, `List`, `Map`, and `LinkedHashMap` in the service.

- [ ] **Step 5: Implement mapper aggregation**

Implement `AdminDashboardQueryMapper.dashboard(String memoryId, int days, boolean graphEnabled, boolean retrievalGraphAssistEnabled)` with these aggregation rules:

- Use `AdminMemoryScope` for scoped counts.
- Count totals with MyBatis Plus `selectCount`.
- Count backlog from buffer, outbox, and graph batch tables.
- Produce daily counts for the last `days` by using grouped SQL. Keep SQLite compatibility by grouping with `substr(created_at, 1, 10)` if values are stored as text.
- Group null/blank `sourceClient` as `unknown`.
- Build `HealthSignals` from the two boolean arguments and grouped memory-thread projection runtime states from the DB.

Preferred grouped SQL shape:

```sql
SELECT COALESCE(NULLIF(source_client, ''), 'unknown') AS name, COUNT(*) AS count
FROM memory_raw_data
WHERE deleted = 0
GROUP BY COALESCE(NULLIF(source_client, ''), 'unknown')
ORDER BY count DESC, name ASC
```

- [ ] **Step 6: Run dashboard controller tests**

Run:

```bash
mvn -pl memind-server -Dtest=AdminDashboardControllerTest test
```

Expected: pass.

- [ ] **Step 7: Commit**

```bash
git add memind-server/src/main/java/com/openmemind/ai/memory/server/controller/admin/dashboard \
        memind-server/src/main/java/com/openmemind/ai/memory/server/domain/dashboard \
        memind-server/src/main/java/com/openmemind/ai/memory/server/mapper/dashboard \
        memind-server/src/main/java/com/openmemind/ai/memory/server/service/dashboard \
        memind-server/src/test/java/com/openmemind/ai/memory/server/controller/admin/dashboard
git commit -m "feat: add admin dashboard api"
```

---

## Task 5: Item Graph Query API

**Files:**
- Create: `memind-server/src/main/java/com/openmemind/ai/memory/server/controller/admin/itemgraph/AdminItemGraphController.java`
- Create: `memind-server/src/main/java/com/openmemind/ai/memory/server/domain/itemgraph/query/ItemGraphPageQueries.java`
- Create: `memind-server/src/main/java/com/openmemind/ai/memory/server/domain/itemgraph/view/GraphEntityDetailView.java`
- Create: `memind-server/src/main/java/com/openmemind/ai/memory/server/domain/itemgraph/view/ItemGraphViews.java`
- Create: `memind-server/src/main/java/com/openmemind/ai/memory/server/mapper/itemgraph/AdminItemGraphQueryMapper.java`
- Create: `memind-server/src/main/java/com/openmemind/ai/memory/server/service/itemgraph/ItemGraphQueryService.java`
- Create: `memind-server/src/main/java/com/openmemind/ai/memory/server/service/itemgraph/ItemGraphManagementService.java`
- Test: `memind-server/src/test/java/com/openmemind/ai/memory/server/controller/admin/itemgraph/AdminItemGraphControllerTest.java`

- [ ] **Step 1: Write failing controller tests for read endpoints**

Create tests for:

```java
@Test
void summaryReturnsGraphCounts() throws Exception {
    mockMvc.perform(get("/admin/v1/item-graph/summary").param("memoryId", "u1:a1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.entityCount").value(2))
            .andExpect(jsonPath("$.data.itemLinkCountByType[0].name").value("SEMANTIC"));
}

@Test
void entityListReturnsPage() throws Exception {
    mockMvc.perform(get("/admin/v1/item-graph/entities")
                    .param("entityType", "PERSON")
                    .param("q", "alice"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.list[0].entityKey").value("person:alice"));
}

@Test
void entityDetailReturnsRelatedCounts() throws Exception {
    mockMvc.perform(get("/admin/v1/item-graph/entities/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.entity.entityKey").value("person:alice"))
            .andExpect(jsonPath("$.data.mentionCount").value(3))
            .andExpect(jsonPath("$.data.entityOverlapItemLinkCount").value(1));
}

@Test
void itemLinksCanFilterByEvidenceSource() throws Exception {
    mockMvc.perform(get("/admin/v1/item-graph/item-links")
                    .param("evidenceSource", "entity_overlap"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.list[0].evidenceSource").value("entity_overlap"));
}
```

- [ ] **Step 2: Run focused test and verify it fails**

Run:

```bash
mvn -pl memind-server -Dtest=AdminItemGraphControllerTest test
```

Expected: compilation fails because item graph admin classes do not exist.

- [ ] **Step 3: Implement item graph view records**

Use one `ItemGraphViews.java` file for small view records:

```java
public final class ItemGraphViews {
    private ItemGraphViews() {}
    public record NamedCount(String name, long count) {}
    public record SummaryView(long entityCount, long aliasCount, long mentionCount,
                              long itemLinkCount, long cooccurrenceCount,
                              List<NamedCount> graphBatchCountByState,
                              List<NamedCount> itemLinkCountByType,
                              List<NamedCount> entityCountByType) {}
    public record EntityView(Integer id, String memoryId, String userId, String agentId,
                             String entityKey, String displayName, String entityType,
                             Map<String, Object> metadata, Instant createdAt, Instant updatedAt) {}
    public record AliasView(Integer id, String memoryId, String userId, String agentId,
                            String entityKey, String entityType, String normalizedAlias,
                            Integer evidenceCount, Map<String, Object> metadata,
                            Instant createdAt, Instant updatedAt) {}
    public record MentionView(Integer id, String memoryId, String userId, String agentId,
                              Long itemId, String entityKey, Float confidence,
                              Map<String, Object> metadata, Instant createdAt, Instant updatedAt) {}
    public record ItemLinkView(Integer id, String memoryId, String userId, String agentId,
                               Long sourceItemId, Long targetItemId, String linkType,
                               String relationCode, String evidenceSource, Double strength,
                               Map<String, Object> metadata, Instant createdAt, Instant updatedAt) {}
    public record CooccurrenceView(Integer id, String memoryId, String userId, String agentId,
                                   String leftEntityKey, String rightEntityKey,
                                   Integer cooccurrenceCount, Map<String, Object> metadata,
                                   Instant createdAt, Instant updatedAt) {}
    public record BatchView(Integer id, String memoryId, String userId, String agentId,
                            String extractionBatchId, String state, String errorMessage,
                            Boolean retryPromotionSupported, Instant createdAt, Instant updatedAt) {}
}
```

Create `GraphEntityDetailView`:

```java
public record GraphEntityDetailView(
        ItemGraphViews.EntityView entity,
        List<ItemGraphViews.AliasView> aliases,
        long mentionCount,
        List<Long> topMentionedItemIds,
        List<ItemGraphViews.CooccurrenceView> topCooccurrences,
        long entityOverlapItemLinkCount) {}
```

Create `ItemGraphPageQueries`:

```java
public final class ItemGraphPageQueries {
    private ItemGraphPageQueries() {}

    public record EntityPageQuery(
            int pageNo, int pageSize, String memoryId, String entityType, String q) {}

    public record AliasPageQuery(
            int pageNo, int pageSize, String memoryId, String entityKey,
            String entityType, String q) {}

    public record MentionPageQuery(
            int pageNo, int pageSize, String memoryId, Long itemId, String entityKey) {}

    public record ItemLinkPageQuery(
            int pageNo, int pageSize, String memoryId, Long itemId,
            String linkType, String evidenceSource) {}

    public record CooccurrencePageQuery(
            int pageNo, int pageSize, String memoryId, String entityKey) {}

    public record BatchPageQuery(
            int pageNo, int pageSize, String memoryId, String state) {}
}
```

- [ ] **Step 4: Implement query endpoint mappings**

Add GET endpoint methods to `AdminItemGraphController`:

```java
@GetMapping("/summary")
public ApiResult<ItemGraphViews.SummaryView> summary(
        @RequestParam(required = false) String memoryId) {
    return ApiResult.success(queryService.summary(memoryId));
}

@GetMapping("/entities")
public ApiResult<PageResult<ItemGraphViews.EntityView>> entities(
        @RequestParam(defaultValue = "1") @Min(1) int pageNo,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
        @RequestParam(required = false) String memoryId,
        @RequestParam(required = false) String entityType,
        @RequestParam(required = false) String q) {
    return ApiResult.success(PageResult.from(queryService.listEntities(
            new ItemGraphPageQueries.EntityPageQuery(pageNo, pageSize, memoryId, entityType, q))));
}

@GetMapping("/entities/{id}")
public ApiResult<GraphEntityDetailView> entityDetail(@PathVariable Integer id) {
    return ApiResult.success(queryService.getEntity(id));
}

@GetMapping("/aliases")
public ApiResult<PageResult<ItemGraphViews.AliasView>> aliases(
        @RequestParam(defaultValue = "1") @Min(1) int pageNo,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
        @RequestParam(required = false) String memoryId,
        @RequestParam(required = false) String entityKey,
        @RequestParam(required = false) String entityType,
        @RequestParam(required = false) String q) {
    return ApiResult.success(PageResult.from(queryService.listAliases(
            new ItemGraphPageQueries.AliasPageQuery(
                    pageNo, pageSize, memoryId, entityKey, entityType, q))));
}

@GetMapping("/mentions")
public ApiResult<PageResult<ItemGraphViews.MentionView>> mentions(
        @RequestParam(defaultValue = "1") @Min(1) int pageNo,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
        @RequestParam(required = false) String memoryId,
        @RequestParam(required = false) Long itemId,
        @RequestParam(required = false) String entityKey) {
    return ApiResult.success(PageResult.from(queryService.listMentions(
            new ItemGraphPageQueries.MentionPageQuery(pageNo, pageSize, memoryId, itemId, entityKey))));
}

@GetMapping("/item-links")
public ApiResult<PageResult<ItemGraphViews.ItemLinkView>> itemLinks(
        @RequestParam(defaultValue = "1") @Min(1) int pageNo,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
        @RequestParam(required = false) String memoryId,
        @RequestParam(required = false) Long itemId,
        @RequestParam(required = false) String linkType,
        @RequestParam(required = false) String evidenceSource) {
    return ApiResult.success(PageResult.from(queryService.listItemLinks(
            new ItemGraphPageQueries.ItemLinkPageQuery(
                    pageNo, pageSize, memoryId, itemId, linkType, evidenceSource))));
}

@GetMapping("/cooccurrences")
public ApiResult<PageResult<ItemGraphViews.CooccurrenceView>> cooccurrences(
        @RequestParam(defaultValue = "1") @Min(1) int pageNo,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
        @RequestParam(required = false) String memoryId,
        @RequestParam(required = false) String entityKey) {
    return ApiResult.success(PageResult.from(queryService.listCooccurrences(
            new ItemGraphPageQueries.CooccurrencePageQuery(pageNo, pageSize, memoryId, entityKey))));
}

@GetMapping("/batches")
public ApiResult<PageResult<ItemGraphViews.BatchView>> batches(
        @RequestParam(defaultValue = "1") @Min(1) int pageNo,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
        @RequestParam(required = false) String memoryId,
        @RequestParam(required = false) String state) {
    return ApiResult.success(PageResult.from(queryService.listBatches(
            new ItemGraphPageQueries.BatchPageQuery(pageNo, pageSize, memoryId, state))));
}
```

Use `PageResult.from(...)` for list endpoints. Validate page params the same way as existing admin controllers.

- [ ] **Step 5: Implement query mapper**

Implement `AdminItemGraphQueryMapper` with these query rules:

- Use existing graph mappers.
- Apply `memoryId` scope directly to `memory_id` for graph tables because graph indexes are memory-scoped.
- Implement `q` filters with `LIKE` on `entity_key/display_name` or `normalized_alias`.
- Filter item links by `itemId` using `(source_item_id = ? OR target_item_id = ?)`.
- Filter `evidenceSource` with `evidence_source = ?`.
- For entity detail, load by numeric `id`; throw `NoSuchElementException` if missing.
- Compute `entityOverlapItemLinkCount` by finding item IDs that mention the entity and counting `memory_item_link` rows with `evidence_source='entity_overlap'` where source or target is in that item set.

- [ ] **Step 6: Run focused test and verify it passes**

Run:

```bash
mvn -pl memind-server -Dtest=AdminItemGraphControllerTest test
```

Expected: pass.

- [ ] **Step 7: Commit**

```bash
git add memind-server/src/main/java/com/openmemind/ai/memory/server/controller/admin/itemgraph \
        memind-server/src/main/java/com/openmemind/ai/memory/server/domain/itemgraph \
        memind-server/src/main/java/com/openmemind/ai/memory/server/mapper/itemgraph \
        memind-server/src/main/java/com/openmemind/ai/memory/server/service/itemgraph \
        memind-server/src/test/java/com/openmemind/ai/memory/server/controller/admin/itemgraph
git commit -m "feat: add admin item graph query APIs"
```

---

## Task 6: Item Graph Correction API

**Files:**
- Modify: `memind-server/src/main/java/com/openmemind/ai/memory/server/controller/admin/itemgraph/AdminItemGraphController.java`
- Modify: `memind-server/src/main/java/com/openmemind/ai/memory/server/mapper/itemgraph/AdminItemGraphQueryMapper.java`
- Modify: `memind-server/src/main/java/com/openmemind/ai/memory/server/service/itemgraph/ItemGraphManagementService.java`
- Create: `memind-server/src/main/java/com/openmemind/ai/memory/server/domain/itemgraph/request/GraphEntityDeleteRequest.java`
- Create: `memind-server/src/main/java/com/openmemind/ai/memory/server/domain/itemgraph/request/GraphIdsRequest.java`
- Create: `memind-server/src/main/java/com/openmemind/ai/memory/server/domain/itemgraph/view/AdminGraphEntityDeleteResult.java`
- Test: `memind-server/src/test/java/com/openmemind/ai/memory/server/controller/admin/itemgraph/AdminItemGraphControllerTest.java`

- [ ] **Step 1: Add failing controller tests for delete endpoints**

Add tests:

```java
@Test
void entityDeleteRequiresMemoryId() throws Exception {
    mockMvc.perform(delete("/admin/v1/item-graph/entities")
                    .contentType(APPLICATION_JSON)
                    .content("{\"entityKeys\":[\"person:alice\"]}"))
            .andExpect(status().isBadRequest());
}

@Test
void entityDeleteReturnsCascadeCounts() throws Exception {
    mockMvc.perform(delete("/admin/v1/item-graph/entities")
                    .contentType(APPLICATION_JSON)
                    .content("{\"memoryId\":\"u1:a1\",\"entityKeys\":[\"person:alice\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.deletedCount").value(1))
            .andExpect(jsonPath("$.data.deletedAliases").value(2))
            .andExpect(jsonPath("$.data.possiblyStaleEntityOverlapLinks").value(1));
}

@Test
void deleteItemLinksRequiresIds() throws Exception {
    mockMvc.perform(delete("/admin/v1/item-graph/item-links")
                    .contentType(APPLICATION_JSON)
                    .content("{\"ids\":[]}"))
            .andExpect(status().isBadRequest());
}
```

- [ ] **Step 2: Run focused test and verify it fails**

Run:

```bash
mvn -pl memind-server -Dtest=AdminItemGraphControllerTest test
```

Expected: fails because delete mappings do not exist.

- [ ] **Step 3: Implement request and result records**

```java
public record GraphIdsRequest(@NotEmpty List<Integer> ids) {
    public GraphIdsRequest {
        ids = ids == null ? List.of() : List.copyOf(ids);
    }
}

public record GraphEntityDeleteRequest(@NotBlank String memoryId, @NotEmpty List<String> entityKeys) {
    public GraphEntityDeleteRequest {
        entityKeys = entityKeys == null ? List.of() : List.copyOf(entityKeys);
    }
}

public record AdminGraphEntityDeleteResult(
        int deletedCount,
        List<String> affectedMemoryIds,
        int deletedAliases,
        int deletedMentions,
        int deletedCooccurrences,
        long possiblyStaleEntityOverlapLinks) {
    public AdminGraphEntityDeleteResult {
        affectedMemoryIds = affectedMemoryIds == null ? List.of() : List.copyOf(affectedMemoryIds);
    }
}
```

- [ ] **Step 4: Implement delete mappings**

Add:

```java
@DeleteMapping("/entities")
public ApiResult<AdminGraphEntityDeleteResult> deleteEntities(
        @Valid @RequestBody GraphEntityDeleteRequest request) {
    return ApiResult.success(managementService.deleteEntities(request.memoryId(), request.entityKeys()));
}

@DeleteMapping("/aliases")
public ApiResult<BatchDeleteResult> deleteAliases(@Valid @RequestBody GraphIdsRequest request) {
    return ApiResult.success(managementService.deleteAliases(request.ids()));
}

@DeleteMapping("/mentions")
public ApiResult<BatchDeleteResult> deleteMentions(@Valid @RequestBody GraphIdsRequest request) {
    return ApiResult.success(managementService.deleteMentions(request.ids()));
}

@DeleteMapping("/item-links")
public ApiResult<BatchDeleteResult> deleteItemLinks(@Valid @RequestBody GraphIdsRequest request) {
    return ApiResult.success(managementService.deleteItemLinks(request.ids()));
}

@DeleteMapping("/cooccurrences")
public ApiResult<BatchDeleteResult> deleteCooccurrences(@Valid @RequestBody GraphIdsRequest request) {
    return ApiResult.success(managementService.deleteCooccurrences(request.ids()));
}
```

- [ ] **Step 5: Implement management service with transactions**

Rules:

- Annotate `deleteEntities()` with `@Transactional`.
- Compute `possiblyStaleEntityOverlapLinks` before deleting mentions.
- Use physical delete SQL for graph correction deletes.
- Do not delete item links during entity delete.
- Return affected memory IDs from rows loaded before delete.

Physical delete SQL shapes:

```sql
DELETE FROM memory_graph_entity WHERE memory_id = ? AND entity_key IN (...)
DELETE FROM memory_graph_entity_alias WHERE memory_id = ? AND entity_key IN (...)
DELETE FROM memory_item_entity_mention WHERE memory_id = ? AND entity_key IN (...)
DELETE FROM memory_entity_cooccurrence
WHERE memory_id = ? AND (left_entity_key IN (...) OR right_entity_key IN (...))
DELETE FROM memory_item_link WHERE id IN (...)
```

Do not call MyBatis Plus `deleteBatchIds()` for item graph correction deletes.

- [ ] **Step 6: Run focused test and verify it passes**

Run:

```bash
mvn -pl memind-server -Dtest=AdminItemGraphControllerTest test
```

Expected: pass.

- [ ] **Step 7: Commit**

```bash
git add memind-server/src/main/java/com/openmemind/ai/memory/server/controller/admin/itemgraph \
        memind-server/src/main/java/com/openmemind/ai/memory/server/domain/itemgraph \
        memind-server/src/main/java/com/openmemind/ai/memory/server/mapper/itemgraph \
        memind-server/src/main/java/com/openmemind/ai/memory/server/service/itemgraph \
        memind-server/src/test/java/com/openmemind/ai/memory/server/controller/admin/itemgraph
git commit -m "feat: add admin item graph correction APIs"
```

---

## Task 7: SQLite Integration Tests

**Files:**
- Modify: `memind-server/src/test/java/com/openmemind/ai/memory/server/MemindServerIntegrationTest.java`

- [ ] **Step 1: Add integration tests for buffer APIs**

Add tests that:

- Insert pending and extracted rows into `memory_conversation_buffer`.
- Verify `GET /admin/v1/buffers/conversations` returns only pending by default.
- Patch extracted rows and verify DB state.
- Insert insight buffer row, delete it through Admin API, then insert the same `(user_id, agent_id, insight_type_name, item_id)` again with JDBC to prove physical delete frees the unique identity.

Use `jdbcTemplate.update(...)` in test setup, matching existing integration test style.

- [ ] **Step 2: Add integration tests for dashboard API**

Seed:

- `memory_raw_data`
- `memory_item`
- `memory_insight`
- `memory_conversation_buffer`
- `memory_insight_buffer`
- graph tables

Assert:

```java
mockMvc.perform(get("/admin/v1/dashboard").param("memoryId", "u1:a1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totals.rawData").value(1))
        .andExpect(jsonPath("$.data.backlog.conversationPending").value(1))
        .andExpect(jsonPath("$.data.breakdown.sourceClients[0].name").value("claude-code"));
```

- [ ] **Step 3: Add integration tests for item graph physical deletes**

Seed graph entity, alias, mention, cooccurrence, and item link rows. Call entity delete. Assert:

- entity row is gone.
- alias row is gone.
- mention row is gone.
- cooccurrence row is gone.
- item link row remains.
- inserting the same entity key again succeeds.

- [ ] **Step 4: Run integration test class**

Run:

```bash
mvn -pl memind-server -Dtest=MemindServerIntegrationTest test
```

Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add memind-server/src/test/java/com/openmemind/ai/memory/server/MemindServerIntegrationTest.java
git commit -m "test: cover admin buffer dashboard graph APIs"
```

---

## Task 8: Full Verification And Quality Pass

**Files:**
- No planned source edits unless verification exposes a real defect.

- [ ] **Step 1: Run focused admin test set**

Run:

```bash
mvn -pl memind-server -Dtest=AdminBufferControllerTest,AdminDashboardControllerTest,AdminItemGraphControllerTest,AdminMemoryScopeTest test
```

Expected: pass.

- [ ] **Step 2: Run server tests**

Run:

```bash
mvn -pl memind-server test
```

Expected: pass.

- [ ] **Step 3: Run graph-related module tests**
Run graph-related module tests to verify admin graph APIs stay compatible with the core/store graph contracts:

```bash
mvn -pl memind-core,memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter test
```

Expected: pass.

- [ ] **Step 4: Review public API shape**

Check:

- No endpoint exposes internal graph thread IDs.
- Graph entity detail uses numeric entity row ID in path.
- `memoryId` is accepted externally and converted internally where useful.
- Physical delete methods are named clearly and are not accidentally MyBatis Plus logical deletes.
- No rebuild, retry, entity merge, or manual graph creation endpoint was added.

- [ ] **Step 5: Final commit if verification required fixes**

If Step 1 or Step 2 required fixes:

```bash
git add memind-server/src/main/java memind-server/src/test/java
git commit -m "fix: harden admin api implementation"
```

If no fixes were needed, do not create an empty commit.

---

## Plan Self-Review

Spec coverage:

- Buffer list, detail, mark extracted, delete, insight groups, group update, built update: Tasks 2 and 3.
- Dashboard totals, backlog, activity, breakdown, health signals: Task 4 and Task 7.
- Item graph summary, entities, aliases, mentions, item links, cooccurrences, batches: Task 5.
- Item graph correction deletes and entity cascade: Task 6 and Task 7.
- `memoryId` internal scope handling: Task 1 plus Tasks 2, 4, 5, and 7.
- Physical delete semantics for unique-identity correction tables: Tasks 3, 6, and 7.
- Transaction requirement for multi-table graph correction: Task 6.

Placeholder scan:

- No unresolved implementation markers remain in the plan.
- Each task has exact file paths, commands, expected outcomes, and concrete code shape for new public records/controllers.

Type consistency:

- `AdminUpdateResult`, `BatchDeleteResult`, and `AdminGraphEntityDeleteResult` match the spec response shapes.
- `memoryId`, `userId`, `agentId`, `entityKey`, `evidenceSource`, `relationCode`, and `retryPromotionSupported` names are consistent across tasks.
