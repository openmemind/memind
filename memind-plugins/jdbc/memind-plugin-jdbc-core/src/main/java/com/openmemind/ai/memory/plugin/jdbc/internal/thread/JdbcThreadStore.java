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
package com.openmemind.ai.memory.plugin.jdbc.internal.thread;

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
import com.openmemind.ai.memory.core.data.thread.MemoryThreadIntakeClaim;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadIntakeOutboxEntry;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadMembership;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadProjection;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadRuntimeState;
import com.openmemind.ai.memory.core.store.thread.ThreadEnrichmentAppendResult;
import com.openmemind.ai.memory.core.store.thread.ThreadEnrichmentInputStore;
import com.openmemind.ai.memory.core.store.thread.ThreadProjectionStore;
import com.openmemind.ai.memory.plugin.jdbc.internal.jdbi.JdbiFactory;
import com.openmemind.ai.memory.plugin.jdbc.internal.schema.StoreSchemaBootstrap;
import com.openmemind.ai.memory.plugin.jdbc.internal.support.JdbcPluginException;
import com.openmemind.ai.memory.plugin.jdbc.internal.support.JsonCodec;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;
import org.jdbi.v3.core.Jdbi;
import tools.jackson.core.type.TypeReference;

public class JdbcThreadStore implements ThreadProjectionStore, ThreadEnrichmentInputStore {

    private static final TypeReference<Map<String, Object>> OBJECT_MAP_TYPE =
            new TypeReference<>() {};

    private final DataSource dataSource;
    private final Jdbi jdbi;
    private final JdbcThreadDialect dialect;
    private final JsonCodec jsonCodec = new JsonCodec();

    protected JdbcThreadStore(
            DataSource dataSource, JdbcThreadDialect dialect, boolean createIfNotExist) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.jdbi = JdbiFactory.create(this.dataSource);
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        ensureSchema(createIfNotExist);
    }

    @Override
    public void ensureRuntime(MemoryId memoryId, String materializationPolicyVersion) {
        Objects.requireNonNull(materializationPolicyVersion, "materializationPolicyVersion");
        update(
                insertRuntimeIfAbsentSql(),
                memoryId.toIdentifier(),
                MemoryThreadProjectionState.REBUILD_REQUIRED.name(),
                0L,
                0L,
                bool(false),
                materializationPolicyVersion,
                "runtime bootstrap",
                nowValue());
    }

    @Override
    public Optional<MemoryThreadRuntimeState> getRuntime(MemoryId memoryId) {
        return Optional.ofNullable(
                queryOne(
                        "SELECT * FROM memory_thread_runtime WHERE memory_id = ?",
                        this::mapRuntime,
                        memoryId.toIdentifier()));
    }

    @Override
    public List<MemoryThreadProjection> listThreads(MemoryId memoryId) {
        return queryList(
                "SELECT * FROM memory_thread WHERE memory_id = ? ORDER BY thread_key ASC",
                this::mapThread,
                memoryId.toIdentifier());
    }

    @Override
    public Optional<MemoryThreadProjection> getThread(MemoryId memoryId, String threadKey) {
        return Optional.ofNullable(
                queryOne(
                        "SELECT * FROM memory_thread WHERE memory_id = ? AND thread_key = ? LIMIT"
                                + " 1",
                        this::mapThread,
                        memoryId.toIdentifier(),
                        threadKey));
    }

    @Override
    public List<MemoryThreadEvent> listEvents(MemoryId memoryId, String threadKey) {
        return queryList(
                "SELECT * FROM memory_thread_event WHERE memory_id = ? AND thread_key = ? ORDER BY"
                        + " event_seq ASC, event_key ASC",
                this::mapEvent,
                memoryId.toIdentifier(),
                threadKey);
    }

    @Override
    public List<MemoryThreadMembership> listMemberships(MemoryId memoryId, String threadKey) {
        return queryList(
                "SELECT * FROM memory_thread_membership WHERE memory_id = ? AND thread_key = ?"
                        + " ORDER BY item_id ASC, role ASC",
                this::mapMembership,
                memoryId.toIdentifier(),
                threadKey);
    }

    @Override
    public List<MemoryThreadProjection> listThreadsByItemId(MemoryId memoryId, long itemId) {
        return queryList(
                "SELECT t.* FROM memory_thread t JOIN memory_thread_membership m ON t.memory_id ="
                        + " m.memory_id AND t.thread_key = m.thread_key WHERE t.memory_id = ? AND"
                        + " m.item_id = ? ORDER BY t.thread_key ASC",
                this::mapThread,
                memoryId.toIdentifier(),
                itemId);
    }

    @Override
    public void enqueue(MemoryId memoryId, long triggerItemId) {
        enqueueInternal(memoryId, triggerItemId);
    }

    @Override
    public void enqueueReplay(MemoryId memoryId, long replayCutoffItemId) {
        enqueueInternal(memoryId, replayCutoffItemId);
    }

    @Override
    public List<MemoryThreadIntakeOutboxEntry> listOutbox(MemoryId memoryId) {
        return queryList(
                "SELECT * FROM thread_intake_outbox WHERE memory_id = ? ORDER BY trigger_item_id"
                        + " ASC",
                this::mapOutbox,
                memoryId.toIdentifier());
    }

    @Override
    public List<MemoryThreadIntakeClaim> claimPending(
            MemoryId memoryId, Instant claimedAt, Instant leaseExpiresAt, int batchSize) {
        if (batchSize <= 0) {
            return List.of();
        }
        return inTransaction(
                connection -> {
                    List<MemoryThreadIntakeOutboxEntry> pending =
                            queryOutbox(
                                    connection,
                                    "SELECT * FROM thread_intake_outbox WHERE memory_id = ? AND"
                                            + " status = ? ORDER BY trigger_item_id ASC LIMIT ?",
                                    memoryId.toIdentifier(),
                                    MemoryThreadIntakeStatus.PENDING.name(),
                                    batchSize);
                    for (MemoryThreadIntakeOutboxEntry row : pending) {
                        executeUpdate(
                                connection,
                                "UPDATE thread_intake_outbox SET status = ?, claimed_at = ?,"
                                    + " lease_expires_at = ?, updated_at = ? WHERE memory_id = ?"
                                    + " AND trigger_item_id = ?",
                                MemoryThreadIntakeStatus.PROCESSING.name(),
                                temporal(claimedAt),
                                temporal(leaseExpiresAt),
                                temporal(Instant.now()),
                                memoryId.toIdentifier(),
                                row.triggerItemId());
                    }
                    return pending.stream()
                            .map(
                                    row ->
                                            new MemoryThreadIntakeClaim(
                                                    row.triggerItemId(),
                                                    row.enqueueGeneration(),
                                                    claimedAt,
                                                    leaseExpiresAt))
                            .toList();
                });
    }

    @Override
    public int recoverAbandoned(MemoryId memoryId, Instant now, int maxAttempts) {
        return inTransaction(
                connection -> {
                    List<MemoryThreadIntakeOutboxEntry> abandoned =
                            queryOutbox(
                                    connection,
                                    "SELECT * FROM thread_intake_outbox WHERE memory_id = ? AND"
                                            + " status = ? AND lease_expires_at IS NOT NULL AND"
                                            + " lease_expires_at < ? ORDER BY trigger_item_id ASC",
                                    memoryId.toIdentifier(),
                                    MemoryThreadIntakeStatus.PROCESSING.name(),
                                    temporal(now));
                    for (MemoryThreadIntakeOutboxEntry row : abandoned) {
                        int nextAttempt = row.attemptCount() + 1;
                        boolean failed = maxAttempts > 0 && nextAttempt >= maxAttempts;
                        executeUpdate(
                                connection,
                                "UPDATE thread_intake_outbox SET status = ?, attempt_count = ?,"
                                    + " failure_reason = ?, claimed_at = NULL, lease_expires_at ="
                                    + " NULL, updated_at = ? WHERE memory_id = ? AND"
                                    + " trigger_item_id = ?",
                                failed
                                        ? MemoryThreadIntakeStatus.FAILED.name()
                                        : MemoryThreadIntakeStatus.PENDING.name(),
                                nextAttempt,
                                failed ? "lease expired" : row.failureReason(),
                                temporal(now),
                                memoryId.toIdentifier(),
                                row.triggerItemId());
                    }
                    return abandoned.size();
                });
    }

    @Override
    public void finalizeOutboxSuccess(
            MemoryId memoryId, long triggerItemId, long lastProcessedItemId, Instant finalizedAt) {
        update(
                "UPDATE thread_intake_outbox SET status = ?, last_processed_item_id = ?,"
                    + " finalized_at = ?, updated_at = ? WHERE memory_id = ? AND trigger_item_id ="
                    + " ?",
                MemoryThreadIntakeStatus.COMPLETED.name(),
                lastProcessedItemId,
                temporal(finalizedAt),
                temporal(finalizedAt),
                memoryId.toIdentifier(),
                triggerItemId);
    }

    @Override
    public void finalizeClaimedIntakeFailure(
            MemoryId memoryId,
            List<MemoryThreadIntakeClaim> claimedEntries,
            String reason,
            int maxAttempts,
            Instant finalizedAt) {
        for (MemoryThreadIntakeClaim claim : emptyIfNull(claimedEntries)) {
            update(
                    "UPDATE thread_intake_outbox SET status = ?, failure_reason = ?, finalized_at ="
                            + " ?, updated_at = ? WHERE memory_id = ? AND trigger_item_id = ?",
                    MemoryThreadIntakeStatus.FAILED.name(),
                    reason,
                    temporal(finalizedAt),
                    temporal(finalizedAt),
                    memoryId.toIdentifier(),
                    claim.triggerItemId());
        }
    }

    @Override
    public void finalizeOutboxSkippedPrefix(
            MemoryId memoryId, long rebuildCutoffItemId, Instant finalizedAt) {
        update(
                "UPDATE thread_intake_outbox SET status = ?, finalized_at = ?, updated_at = ? WHERE"
                        + " memory_id = ? AND trigger_item_id <= ? AND status <> ?",
                MemoryThreadIntakeStatus.SKIPPED.name(),
                temporal(finalizedAt),
                temporal(finalizedAt),
                memoryId.toIdentifier(),
                rebuildCutoffItemId,
                MemoryThreadIntakeStatus.COMPLETED.name());
    }

    @Override
    public void markRebuildRequired(MemoryId memoryId, String reason) {
        update(
                "UPDATE memory_thread_runtime SET projection_state = ?, rebuild_in_progress = ?,"
                        + " invalidation_reason = ?, updated_at = ? WHERE memory_id = ?",
                MemoryThreadProjectionState.REBUILD_REQUIRED.name(),
                bool(false),
                reason,
                temporal(Instant.now()),
                memoryId.toIdentifier());
    }

    @Override
    public boolean beginRebuild(
            MemoryId memoryId, String materializationPolicyVersion, long rebuildCutoffItemId) {
        ensureRuntime(memoryId, materializationPolicyVersion);
        return update(
                        "UPDATE memory_thread_runtime SET rebuild_in_progress = ?,"
                                + " rebuild_cutoff_item_id = ?, materialization_policy_version = ?,"
                                + " updated_at = ? WHERE memory_id = ? AND rebuild_in_progress = ?",
                        bool(true),
                        rebuildCutoffItemId,
                        materializationPolicyVersion,
                        temporal(Instant.now()),
                        memoryId.toIdentifier(),
                        bool(false))
                > 0;
    }

    @Override
    public boolean commitClaimedIntakeReplaySuccess(
            MemoryId memoryId,
            List<MemoryThreadIntakeClaim> claimedEntries,
            long replayCutoffItemId,
            List<MemoryThreadProjection> threads,
            List<MemoryThreadEvent> events,
            List<MemoryThreadMembership> memberships,
            MemoryThreadRuntimeState runtimeState,
            Instant finalizedAt) {
        replaceProjection(memoryId, threads, events, memberships, runtimeState, finalizedAt);
        for (MemoryThreadIntakeClaim claim : emptyIfNull(claimedEntries)) {
            finalizeOutboxSuccess(memoryId, claim.triggerItemId(), replayCutoffItemId, finalizedAt);
        }
        return true;
    }

    @Override
    public void releaseClaims(MemoryId memoryId, List<MemoryThreadIntakeClaim> claimedEntries) {
        for (MemoryThreadIntakeClaim claim : emptyIfNull(claimedEntries)) {
            update(
                    "UPDATE thread_intake_outbox SET status = ?, claimed_at = NULL,"
                            + " lease_expires_at = NULL, updated_at = ? WHERE memory_id = ? AND"
                            + " trigger_item_id = ?",
                    MemoryThreadIntakeStatus.PENDING.name(),
                    temporal(Instant.now()),
                    memoryId.toIdentifier(),
                    claim.triggerItemId());
        }
    }

    @Override
    public void commitRebuildReplaySuccess(
            MemoryId memoryId,
            long rebuildCutoffItemId,
            List<MemoryThreadProjection> threads,
            List<MemoryThreadEvent> events,
            List<MemoryThreadMembership> memberships,
            MemoryThreadRuntimeState runtimeState,
            Instant finalizedAt) {
        replaceProjection(memoryId, threads, events, memberships, runtimeState, finalizedAt);
    }

    @Override
    public void replaceProjection(
            MemoryId memoryId,
            List<MemoryThreadProjection> threads,
            List<MemoryThreadEvent> events,
            List<MemoryThreadMembership> memberships,
            MemoryThreadRuntimeState runtimeState,
            Instant finalizedAt) {
        inTransaction(
                connection -> {
                    String memory = memoryId.toIdentifier();
                    executeUpdate(
                            connection,
                            "DELETE FROM memory_thread_membership WHERE memory_id = ?",
                            memory);
                    executeUpdate(
                            connection,
                            "DELETE FROM memory_thread_event WHERE memory_id = ?",
                            memory);
                    executeUpdate(
                            connection, "DELETE FROM memory_thread WHERE memory_id = ?", memory);
                    for (MemoryThreadProjection thread : emptyIfNull(threads)) {
                        insertThread(connection, thread);
                    }
                    for (MemoryThreadEvent event : emptyIfNull(events)) {
                        insertEvent(connection, event);
                    }
                    for (MemoryThreadMembership membership : emptyIfNull(memberships)) {
                        insertMembership(connection, membership);
                    }
                    if (runtimeState != null) {
                        upsertRuntime(connection, runtimeState);
                    }
                    return null;
                });
    }

    @Override
    public ThreadEnrichmentAppendResult appendRunAndEnqueueReplay(
            MemoryId memoryId,
            long replayCutoffItemId,
            List<MemoryThreadEnrichmentInput> runInputs) {
        if (runInputs == null || runInputs.isEmpty()) {
            return ThreadEnrichmentAppendResult.DUPLICATE_EQUIVALENT;
        }
        return inTransaction(
                connection -> {
                    boolean insertedAny = false;
                    for (MemoryThreadEnrichmentInput input : runInputs) {
                        MemoryThreadEnrichmentInput existing =
                                loadEnrichmentInput(connection, input);
                        if (existing != null) {
                            if (!existing.equals(input)) {
                                throw new IllegalStateException(
                                        "conflicting duplicate enrichment input");
                            }
                            continue;
                        }
                        insertEnrichmentInput(connection, input);
                        insertedAny = true;
                    }
                    if (insertedAny) {
                        enqueueInternal(connection, memoryId, replayCutoffItemId);
                        return ThreadEnrichmentAppendResult.INSERTED;
                    }
                    return ThreadEnrichmentAppendResult.DUPLICATE_EQUIVALENT;
                });
    }

    @Override
    public List<MemoryThreadEnrichmentInput> listReplayable(
            MemoryId memoryId, long cutoffItemId, String materializationPolicyVersion) {
        if (cutoffItemId <= 0) {
            return List.of();
        }
        return queryList(
                "SELECT * FROM memory_thread_enrichment_input WHERE memory_id = ? AND"
                        + " basis_cutoff_item_id <= ? AND basis_materialization_policy_version = ?"
                        + " ORDER BY basis_cutoff_item_id ASC, basis_meaningful_event_count ASC,"
                        + " input_run_key ASC, entry_seq ASC",
                this::mapEnrichmentInput,
                memoryId.toIdentifier(),
                cutoffItemId,
                materializationPolicyVersion);
    }

    private void enqueueInternal(MemoryId memoryId, long triggerItemId) {
        inTransaction(
                connection -> {
                    enqueueInternal(connection, memoryId, triggerItemId);
                    return null;
                });
    }

    private void enqueueInternal(Connection connection, MemoryId memoryId, long triggerItemId) {
        executeUpdate(
                connection,
                upsertOutboxSql(),
                memoryId.toIdentifier(),
                triggerItemId,
                MemoryThreadIntakeStatus.PENDING.name(),
                0,
                temporal(Instant.now()),
                temporal(Instant.now()));
    }

    private void insertThread(Connection connection, MemoryThreadProjection thread) {
        executeUpdate(
                connection,
                "INSERT INTO memory_thread (memory_id, thread_key, thread_type, anchor_kind,"
                    + " anchor_key, display_label, lifecycle_status, object_state, headline,"
                    + " snapshot_json, snapshot_version, opened_at, last_event_at,"
                    + " last_meaningful_update_at, closed_at, event_count, member_count,"
                    + " created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,"
                    + " ?, ?, ?, ?, ?)",
                thread.memoryId(),
                thread.threadKey(),
                thread.threadType().name(),
                thread.anchorKind(),
                thread.anchorKey(),
                thread.displayLabel(),
                thread.lifecycleStatus().name(),
                thread.objectState().name(),
                thread.headline(),
                jsonCodec.toJson(thread.snapshotJson()),
                thread.snapshotVersion(),
                temporal(thread.openedAt()),
                temporal(thread.lastEventAt()),
                temporal(thread.lastMeaningfulUpdateAt()),
                temporal(thread.closedAt()),
                thread.eventCount(),
                thread.memberCount(),
                temporal(thread.createdAt()),
                temporal(thread.updatedAt()));
    }

    private void insertMembership(Connection connection, MemoryThreadMembership membership) {
        executeUpdate(
                connection,
                "INSERT INTO memory_thread_membership (memory_id, thread_key, item_id, role,"
                    + " is_primary, relevance_weight, created_at, updated_at) VALUES (?, ?, ?, ?,"
                    + " ?, ?, ?, ?)",
                membership.memoryId(),
                membership.threadKey(),
                membership.itemId(),
                membership.role().name(),
                bool(membership.primary()),
                membership.relevanceWeight(),
                temporal(membership.createdAt()),
                temporal(membership.updatedAt()));
    }

    private void insertEvent(Connection connection, MemoryThreadEvent event) {
        executeUpdate(
                connection,
                "INSERT INTO memory_thread_event (memory_id, thread_key, event_key, event_seq,"
                    + " event_type, event_time, event_payload_json, event_payload_version,"
                    + " is_meaningful, confidence, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?,"
                    + " ?, ?)",
                event.memoryId(),
                event.threadKey(),
                event.eventKey(),
                event.eventSeq(),
                event.eventType().name(),
                temporal(event.eventTime()),
                jsonCodec.toJson(event.eventPayloadJson()),
                event.eventPayloadVersion(),
                bool(event.meaningful()),
                event.confidence(),
                temporal(event.createdAt()));
    }

    private void upsertRuntime(Connection connection, MemoryThreadRuntimeState runtime) {
        executeUpdate(
                connection,
                upsertRuntimeSql(),
                runtime.memoryId(),
                runtime.projectionState().name(),
                runtime.pendingCount(),
                runtime.failedCount(),
                runtime.lastEnqueuedItemId(),
                runtime.lastProcessedItemId(),
                bool(runtime.rebuildInProgress()),
                runtime.rebuildCutoffItemId(),
                runtime.rebuildEpoch(),
                runtime.materializationPolicyVersion(),
                runtime.invalidationReason(),
                temporal(runtime.updatedAt()));
    }

    private void insertEnrichmentInput(Connection connection, MemoryThreadEnrichmentInput input) {
        executeUpdate(
                connection,
                "INSERT INTO memory_thread_enrichment_input (memory_id, thread_key, input_run_key,"
                        + " entry_seq, basis_cutoff_item_id, basis_meaningful_event_count,"
                        + " basis_materialization_policy_version, payload_json, provenance_json,"
                        + " created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                input.memoryId(),
                input.threadKey(),
                input.inputRunKey(),
                input.entrySeq(),
                input.basisCutoffItemId(),
                input.basisMeaningfulEventCount(),
                input.basisMaterializationPolicyVersion(),
                jsonCodec.toJson(input.payloadJson()),
                jsonCodec.toJson(input.provenanceJson()),
                temporal(input.createdAt()));
    }

    private MemoryThreadEnrichmentInput loadEnrichmentInput(
            Connection connection, MemoryThreadEnrichmentInput input) {
        List<MemoryThreadEnrichmentInput> rows =
                queryList(
                        connection,
                        "SELECT * FROM memory_thread_enrichment_input WHERE memory_id = ? AND"
                                + " input_run_key = ? AND entry_seq = ?",
                        this::mapEnrichmentInput,
                        input.memoryId(),
                        input.inputRunKey(),
                        input.entrySeq());
        return rows.isEmpty() ? null : rows.get(0);
    }

    private MemoryThreadProjection mapThread(ResultSet rs) throws SQLException {
        return new MemoryThreadProjection(
                rs.getString("memory_id"),
                rs.getString("thread_key"),
                MemoryThreadType.valueOf(rs.getString("thread_type")),
                rs.getString("anchor_kind"),
                rs.getString("anchor_key"),
                rs.getString("display_label"),
                MemoryThreadLifecycleStatus.valueOf(rs.getString("lifecycle_status")),
                MemoryThreadObjectState.valueOf(rs.getString("object_state")),
                rs.getString("headline"),
                readMap(rs.getString("snapshot_json")),
                rs.getInt("snapshot_version"),
                parseTemporal(rs, "opened_at"),
                parseTemporal(rs, "last_event_at"),
                parseTemporal(rs, "last_meaningful_update_at"),
                parseTemporal(rs, "closed_at"),
                rs.getLong("event_count"),
                rs.getLong("member_count"),
                parseTemporal(rs, "created_at"),
                parseTemporal(rs, "updated_at"));
    }

    private MemoryThreadMembership mapMembership(ResultSet rs) throws SQLException {
        return new MemoryThreadMembership(
                rs.getString("memory_id"),
                rs.getString("thread_key"),
                rs.getLong("item_id"),
                MemoryThreadMembershipRole.valueOf(rs.getString("role")),
                boolColumn(rs, "is_primary"),
                rs.getDouble("relevance_weight"),
                parseTemporal(rs, "created_at"),
                parseTemporal(rs, "updated_at"));
    }

    private MemoryThreadEvent mapEvent(ResultSet rs) throws SQLException {
        double confidence = rs.getDouble("confidence");
        return new MemoryThreadEvent(
                rs.getString("memory_id"),
                rs.getString("thread_key"),
                rs.getString("event_key"),
                rs.getLong("event_seq"),
                MemoryThreadEventType.valueOf(rs.getString("event_type")),
                parseTemporal(rs, "event_time"),
                readMap(rs.getString("event_payload_json")),
                rs.getInt("event_payload_version"),
                boolColumn(rs, "is_meaningful"),
                rs.wasNull() ? null : confidence,
                parseTemporal(rs, "created_at"));
    }

    private MemoryThreadRuntimeState mapRuntime(ResultSet rs) throws SQLException {
        return new MemoryThreadRuntimeState(
                rs.getString("memory_id"),
                MemoryThreadProjectionState.valueOf(rs.getString("projection_state")),
                rs.getLong("pending_count"),
                rs.getLong("failed_count"),
                nullableLong(rs, "last_enqueued_item_id"),
                nullableLong(rs, "last_processed_item_id"),
                boolColumn(rs, "rebuild_in_progress"),
                nullableLong(rs, "rebuild_cutoff_item_id"),
                rs.getLong("rebuild_epoch"),
                rs.getString("materialization_policy_version"),
                rs.getString("invalidation_reason"),
                parseTemporal(rs, "updated_at"));
    }

    private MemoryThreadIntakeOutboxEntry mapOutbox(ResultSet rs) throws SQLException {
        return new MemoryThreadIntakeOutboxEntry(
                rs.getString("memory_id"),
                rs.getLong("trigger_item_id"),
                rs.getLong("enqueue_generation"),
                MemoryThreadIntakeStatus.valueOf(rs.getString("status")),
                rs.getInt("attempt_count"),
                parseTemporal(rs, "claimed_at"),
                parseTemporal(rs, "lease_expires_at"),
                rs.getString("failure_reason"),
                nullableLong(rs, "last_processed_item_id"),
                parseTemporal(rs, "enqueued_at"),
                parseTemporal(rs, "finalized_at"));
    }

    private MemoryThreadEnrichmentInput mapEnrichmentInput(ResultSet rs) throws SQLException {
        return new MemoryThreadEnrichmentInput(
                rs.getString("memory_id"),
                rs.getString("thread_key"),
                rs.getString("input_run_key"),
                rs.getInt("entry_seq"),
                rs.getLong("basis_cutoff_item_id"),
                rs.getLong("basis_meaningful_event_count"),
                rs.getString("basis_materialization_policy_version"),
                readMap(rs.getString("payload_json")),
                readMap(rs.getString("provenance_json")),
                parseTemporal(rs, "created_at"));
    }

    private List<MemoryThreadIntakeOutboxEntry> queryOutbox(
            Connection connection, String sql, Object... params) {
        return queryList(connection, sql, this::mapOutbox, params);
    }

    private <T> T inTransaction(TransactionCallback<T> callback) {
        return jdbi.inTransaction(
                handle -> {
                    try {
                        return callback.execute(handle.getConnection());
                    } catch (SQLException e) {
                        throw new JdbcPluginException(e);
                    }
                });
    }

    private <T> T queryOne(String sql, ResultSetMapper<T> mapper, Object... params) {
        List<T> results = queryList(sql, mapper, params);
        return results.isEmpty() ? null : results.get(0);
    }

    private <T> List<T> queryList(String sql, ResultSetMapper<T> mapper, Object... params) {
        return jdbi.withHandle(handle -> queryList(handle.getConnection(), sql, mapper, params));
    }

    private int update(String sql, Object... params) {
        return jdbi.withHandle(handle -> executeUpdate(handle.getConnection(), sql, params));
    }

    private static <T> List<T> queryList(
            Connection connection, String sql, ResultSetMapper<T> mapper, Object... params) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet rs = statement.executeQuery()) {
                List<T> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapper.map(rs));
                }
                return results;
            }
        } catch (SQLException e) {
            throw new JdbcPluginException(e);
        }
    }

    private static int executeUpdate(Connection connection, String sql, Object... params) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new JdbcPluginException(e);
        }
    }

    private static void bind(PreparedStatement statement, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, params[i]);
        }
    }

    @FunctionalInterface
    private interface ResultSetMapper<T> {
        T map(ResultSet resultSet) throws SQLException;
    }

    @FunctionalInterface
    private interface TransactionCallback<T> {
        T execute(Connection connection) throws SQLException;
    }

    private String insertRuntimeIfAbsentSql() {
        return switch (dialect) {
            case SQLITE ->
                    "INSERT OR IGNORE INTO memory_thread_runtime (memory_id, projection_state,"
                            + " pending_count, failed_count, rebuild_in_progress,"
                            + " materialization_policy_version, invalidation_reason, updated_at,"
                            + " rebuild_epoch) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)";
            case MYSQL ->
                    "INSERT IGNORE INTO memory_thread_runtime (memory_id, projection_state,"
                            + " pending_count, failed_count, rebuild_in_progress,"
                            + " materialization_policy_version, invalidation_reason, updated_at,"
                            + " rebuild_epoch) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)";
            case POSTGRESQL ->
                    "INSERT INTO memory_thread_runtime (memory_id, projection_state, pending_count,"
                        + " failed_count, rebuild_in_progress, materialization_policy_version,"
                        + " invalidation_reason, updated_at, rebuild_epoch) VALUES (?, ?, ?, ?, ?,"
                        + " ?, ?, ?, 0) ON CONFLICT(memory_id) DO NOTHING";
        };
    }

    private String upsertRuntimeSql() {
        return switch (dialect) {
            case SQLITE, POSTGRESQL ->
                    "INSERT INTO memory_thread_runtime (memory_id, projection_state, pending_count,"
                        + " failed_count, last_enqueued_item_id, last_processed_item_id,"
                        + " rebuild_in_progress, rebuild_cutoff_item_id, rebuild_epoch,"
                        + " materialization_policy_version, invalidation_reason, updated_at) VALUES"
                        + " (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(memory_id) DO UPDATE"
                        + " SET projection_state = excluded.projection_state, pending_count ="
                        + " excluded.pending_count, failed_count = excluded.failed_count,"
                        + " last_enqueued_item_id = excluded.last_enqueued_item_id,"
                        + " last_processed_item_id = excluded.last_processed_item_id,"
                        + " rebuild_in_progress = excluded.rebuild_in_progress,"
                        + " rebuild_cutoff_item_id = excluded.rebuild_cutoff_item_id, rebuild_epoch"
                        + " = excluded.rebuild_epoch, materialization_policy_version ="
                        + " excluded.materialization_policy_version, invalidation_reason ="
                        + " excluded.invalidation_reason, updated_at = excluded.updated_at";
            case MYSQL ->
                    "INSERT INTO memory_thread_runtime (memory_id, projection_state, pending_count,"
                        + " failed_count, last_enqueued_item_id, last_processed_item_id,"
                        + " rebuild_in_progress, rebuild_cutoff_item_id, rebuild_epoch,"
                        + " materialization_policy_version, invalidation_reason, updated_at) VALUES"
                        + " (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE"
                        + " projection_state = VALUES(projection_state), pending_count ="
                        + " VALUES(pending_count), failed_count = VALUES(failed_count),"
                        + " last_enqueued_item_id = VALUES(last_enqueued_item_id),"
                        + " last_processed_item_id = VALUES(last_processed_item_id),"
                        + " rebuild_in_progress = VALUES(rebuild_in_progress),"
                        + " rebuild_cutoff_item_id = VALUES(rebuild_cutoff_item_id), rebuild_epoch"
                        + " = VALUES(rebuild_epoch), materialization_policy_version ="
                        + " VALUES(materialization_policy_version), invalidation_reason ="
                        + " VALUES(invalidation_reason), updated_at = VALUES(updated_at)";
        };
    }

    private String upsertOutboxSql() {
        return switch (dialect) {
            case SQLITE, POSTGRESQL ->
                    "INSERT INTO thread_intake_outbox (memory_id, trigger_item_id, status,"
                        + " attempt_count, enqueued_at, updated_at, enqueue_generation) VALUES (?,"
                        + " ?, ?, ?, ?, ?, 1) ON CONFLICT(memory_id, trigger_item_id) DO UPDATE SET"
                        + " status = excluded.status, attempt_count = excluded.attempt_count,"
                        + " enqueued_at = excluded.enqueued_at, updated_at = excluded.updated_at,"
                        + " enqueue_generation = thread_intake_outbox.enqueue_generation + 1";
            case MYSQL ->
                    "INSERT INTO thread_intake_outbox (memory_id, trigger_item_id, status,"
                        + " attempt_count, enqueued_at, updated_at, enqueue_generation) VALUES (?,"
                        + " ?, ?, ?, ?, ?, 1) ON DUPLICATE KEY UPDATE status = VALUES(status),"
                        + " attempt_count = VALUES(attempt_count), enqueued_at ="
                        + " VALUES(enqueued_at), updated_at = VALUES(updated_at),"
                        + " enqueue_generation = enqueue_generation + 1";
        };
    }

    private Object temporal(Instant instant) {
        if (instant == null) {
            return null;
        }
        return dialect == JdbcThreadDialect.SQLITE ? instant.toString() : Timestamp.from(instant);
    }

    private Object nowValue() {
        return temporal(Instant.now());
    }

    private Object bool(boolean value) {
        return dialect == JdbcThreadDialect.POSTGRESQL ? value : (value ? 1 : 0);
    }

    private boolean boolColumn(ResultSet rs, String column) throws SQLException {
        return rs.getBoolean(column);
    }

    private Instant parseTemporal(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        return Instant.parse(
                String.valueOf(value).replace(' ', 'T')
                        + (String.valueOf(value).contains("Z") ? "" : "Z"));
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Map<String, Object> readMap(String json) {
        Map<String, Object> value = jsonCodec.fromJson(json, OBJECT_MAP_TYPE);
        return value == null ? Map.of() : value;
    }

    private <T> List<T> emptyIfNull(List<T> values) {
        return values == null ? List.of() : values;
    }

    private void ensureSchema(boolean createIfNotExist) {
        switch (dialect) {
            case SQLITE -> StoreSchemaBootstrap.ensureSqlite(dataSource, createIfNotExist);
            case MYSQL -> StoreSchemaBootstrap.ensureMysql(dataSource, createIfNotExist);
            case POSTGRESQL -> StoreSchemaBootstrap.ensurePostgresql(dataSource, createIfNotExist);
        }
    }
}
