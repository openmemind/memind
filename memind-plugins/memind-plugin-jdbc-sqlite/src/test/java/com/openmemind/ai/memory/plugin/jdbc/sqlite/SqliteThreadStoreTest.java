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
package com.openmemind.ai.memory.plugin.jdbc.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadEventType;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadIntakeStatus;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadLifecycleStatus;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadMembershipRole;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadObjectState;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadProjectionState;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadType;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadEnrichmentInput;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadEvent;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadMembership;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadProjection;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadRuntimeState;
import com.openmemind.ai.memory.core.store.thread.ThreadEnrichmentAppendResult;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

class SqliteThreadStoreTest {

    private static final Instant BASE_TIME = Instant.parse("2026-03-22T00:00:00Z");

    @TempDir Path tempDir;

    @Test
    void persistsRuntimeOutboxProjectionAndEnrichmentInputs() {
        SqliteThreadStore threadStore = new SqliteThreadStore(dataSource());

        threadStore.ensureRuntime(memoryId(), "thread-core-v1");
        assertThat(threadStore.getRuntime(memoryId()))
                .get()
                .extracting(MemoryThreadRuntimeState::projectionState)
                .isEqualTo(MemoryThreadProjectionState.REBUILD_REQUIRED);

        threadStore.enqueue(memoryId(), 10L);
        assertThat(threadStore.listOutbox(memoryId())).hasSize(1);
        var claims = threadStore.claimPending(memoryId(), BASE_TIME, BASE_TIME.plusSeconds(60), 10);
        assertThat(claims).hasSize(1);
        assertThat(threadStore.recoverAbandoned(memoryId(), BASE_TIME.plusSeconds(120), 3))
                .isEqualTo(1);
        assertThat(threadStore.listOutbox(memoryId()).get(0).status())
                .isEqualTo(MemoryThreadIntakeStatus.PENDING);
        claims =
                threadStore.claimPending(
                        memoryId(), BASE_TIME.plusSeconds(130), BASE_TIME.plusSeconds(140), 10);
        assertThat(claims).hasSize(1);
        assertThat(threadStore.recoverAbandoned(memoryId(), BASE_TIME.plusSeconds(150), 2))
                .isEqualTo(1);
        assertThat(threadStore.listOutbox(memoryId()).get(0).status())
                .isEqualTo(MemoryThreadIntakeStatus.FAILED);
        threadStore.enqueue(memoryId(), 10L);
        claims =
                threadStore.claimPending(
                        memoryId(), BASE_TIME.plusSeconds(160), BASE_TIME.plusSeconds(220), 10);
        assertThat(claims).hasSize(1);
        threadStore.finalizeOutboxSuccess(memoryId(), 10L, 10L, BASE_TIME.plusSeconds(1));
        assertThat(threadStore.listOutbox(memoryId()).get(0).lastProcessedItemId()).isEqualTo(10L);

        threadStore.replaceProjection(
                memoryId(),
                List.of(thread()),
                List.of(event()),
                List.of(membership()),
                runtime(MemoryThreadProjectionState.AVAILABLE),
                BASE_TIME.plusSeconds(2));
        assertThat(threadStore.listThreads(memoryId()))
                .extracting(MemoryThreadProjection::threadKey)
                .containsExactly("thread-1");
        assertThat(threadStore.listThreadsByItemId(memoryId(), 10L)).hasSize(1);
        assertThat(threadStore.listEvents(memoryId(), "thread-1"))
                .extracting(MemoryThreadEvent::eventKey)
                .containsExactly("event-1");
        assertThat(threadStore.listMemberships(memoryId(), "thread-1")).hasSize(1);

        MemoryThreadEnrichmentInput input = enrichmentInput();
        assertThat(threadStore.appendRunAndEnqueueReplay(memoryId(), 10L, List.of(input)))
                .isEqualTo(ThreadEnrichmentAppendResult.INSERTED);
        assertThat(threadStore.appendRunAndEnqueueReplay(memoryId(), 10L, List.of(input)))
                .isEqualTo(ThreadEnrichmentAppendResult.DUPLICATE_EQUIVALENT);
        assertThat(threadStore.listReplayable(memoryId(), 10L, "thread-core-v1"))
                .extracting(MemoryThreadEnrichmentInput::inputRunKey)
                .containsExactly("run-1");
    }

    private SQLiteDataSource dataSource() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("thread.db"));
        return dataSource;
    }

    private MemoryId memoryId() {
        return DefaultMemoryId.of("u1", "a1");
    }

    private MemoryThreadProjection thread() {
        return new MemoryThreadProjection(
                memoryId().toIdentifier(),
                "thread-1",
                MemoryThreadType.WORK,
                "project",
                "memind",
                "Memind",
                MemoryThreadLifecycleStatus.ACTIVE,
                MemoryThreadObjectState.ONGOING,
                "headline",
                Map.of("summary", "snapshot"),
                1,
                BASE_TIME,
                BASE_TIME,
                BASE_TIME,
                null,
                1,
                1,
                BASE_TIME,
                BASE_TIME);
    }

    private MemoryThreadMembership membership() {
        return new MemoryThreadMembership(
                memoryId().toIdentifier(),
                "thread-1",
                10L,
                MemoryThreadMembershipRole.TRIGGER,
                true,
                1.0d,
                BASE_TIME,
                BASE_TIME);
    }

    private MemoryThreadRuntimeState runtime(MemoryThreadProjectionState state) {
        return new MemoryThreadRuntimeState(
                memoryId().toIdentifier(),
                state,
                0,
                0,
                10L,
                10L,
                false,
                null,
                0,
                "thread-core-v1",
                null,
                BASE_TIME);
    }

    private MemoryThreadEvent event() {
        return new MemoryThreadEvent(
                memoryId().toIdentifier(),
                "thread-1",
                "event-1",
                1L,
                MemoryThreadEventType.OBSERVATION,
                BASE_TIME,
                Map.of("itemId", 10L),
                1,
                true,
                0.9d,
                BASE_TIME);
    }

    private MemoryThreadEnrichmentInput enrichmentInput() {
        return new MemoryThreadEnrichmentInput(
                memoryId().toIdentifier(),
                "thread-1",
                "run-1",
                0,
                10L,
                1L,
                "thread-core-v1",
                Map.of("payload", "value"),
                Map.of("source", "test"),
                BASE_TIME);
    }
}
