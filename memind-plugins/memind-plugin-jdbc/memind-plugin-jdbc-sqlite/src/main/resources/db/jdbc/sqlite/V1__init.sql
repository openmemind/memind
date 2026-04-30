--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
-- http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

-- Squashed schema for sqlite.
-- This file supersedes the previous V1-V14 development migration chain.

-- === V1__init_store.sql ===
-- http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

CREATE TABLE IF NOT EXISTS memory_raw_data (
    id                INTEGER      NOT NULL PRIMARY KEY AUTOINCREMENT,
    biz_id            TEXT         NOT NULL,
    user_id           TEXT         NOT NULL,
    agent_id          TEXT         NOT NULL,
    memory_id         TEXT         NOT NULL,
    type              TEXT         NOT NULL,
    content_id        TEXT,
    segment           TEXT,
    caption           TEXT,
    caption_vector_id TEXT,
    metadata          TEXT,
    start_time        TEXT,
    end_time          TEXT,
    created_at        TEXT         NOT NULL DEFAULT (datetime('now')),
    updated_at        TEXT         NOT NULL DEFAULT (datetime('now')),
    deleted           INTEGER      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_raw_data_biz_id ON memory_raw_data(user_id, agent_id, biz_id);
CREATE INDEX IF NOT EXISTS idx_raw_data_content_id ON memory_raw_data(user_id, agent_id, content_id);
CREATE INDEX IF NOT EXISTS idx_raw_data_memory_id ON memory_raw_data(user_id, agent_id);

CREATE TABLE IF NOT EXISTS memory_item (
    id            INTEGER      NOT NULL PRIMARY KEY AUTOINCREMENT,
    biz_id        INTEGER      NOT NULL,
    user_id       TEXT         NOT NULL,
    agent_id      TEXT         NOT NULL,
    memory_id     TEXT         NOT NULL,
    content       TEXT         NOT NULL,
    scope         TEXT         NOT NULL,
    category      TEXT,
    vector_id     TEXT,
    raw_data_id   TEXT,
    content_hash  TEXT,
    occurred_at   TEXT,
    observed_at   TEXT,
    type          TEXT         NOT NULL DEFAULT 'FACT',
    raw_data_type TEXT         NOT NULL DEFAULT 'CONVERSATION',
    metadata      TEXT,
    created_at    TEXT         NOT NULL DEFAULT (datetime('now')),
    updated_at    TEXT         NOT NULL DEFAULT (datetime('now')),
    deleted       INTEGER      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_item_biz_id ON memory_item(user_id, agent_id, biz_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_item_content_hash ON memory_item(user_id, agent_id, content_hash);
CREATE INDEX IF NOT EXISTS idx_item_memory_id ON memory_item(user_id, agent_id);
CREATE INDEX IF NOT EXISTS idx_item_raw_data_id ON memory_item(user_id, agent_id, raw_data_id);
CREATE INDEX IF NOT EXISTS idx_item_type ON memory_item(user_id, agent_id, type);
CREATE INDEX IF NOT EXISTS idx_item_category ON memory_item(memory_id, category);
CREATE INDEX IF NOT EXISTS idx_item_scope_category ON memory_item(memory_id, scope, category);

CREATE TABLE IF NOT EXISTS memory_insight_type (
    id                    INTEGER      NOT NULL PRIMARY KEY AUTOINCREMENT,
    biz_id                INTEGER,
    name                  TEXT         NOT NULL,
    description           TEXT,
    description_vector_id TEXT,
    categories            TEXT,
    target_tokens         INTEGER      NOT NULL DEFAULT 0,
    analysis_mode         TEXT         DEFAULT 'BRANCH',
    scope                 TEXT,
    tree_config           TEXT,
    last_updated_at       TEXT,
    created_at            TEXT         NOT NULL DEFAULT (datetime('now')),
    updated_at            TEXT         NOT NULL DEFAULT (datetime('now')),
    deleted               INTEGER      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_insight_type_name ON memory_insight_type(name);
CREATE INDEX IF NOT EXISTS idx_insight_type_all ON memory_insight_type(id);

CREATE TABLE IF NOT EXISTS memory_insight (
    id                INTEGER      NOT NULL PRIMARY KEY AUTOINCREMENT,
    biz_id            INTEGER      NOT NULL,
    user_id           TEXT         NOT NULL,
    agent_id          TEXT         NOT NULL,
    memory_id         TEXT         NOT NULL,
    type              TEXT         NOT NULL,
    scope             TEXT         NOT NULL,
    name              TEXT,
    categories        TEXT,
    content           TEXT,
    points            TEXT,
    group_name        TEXT,
    last_reasoned_at  TEXT,
    summary_embedding TEXT,
    tier              TEXT,
    parent_insight_id INTEGER,
    child_insight_ids TEXT,
    version           INTEGER      NOT NULL DEFAULT 1,
    created_at        TEXT         NOT NULL DEFAULT (datetime('now')),
    updated_at        TEXT         NOT NULL DEFAULT (datetime('now')),
    deleted           INTEGER      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_insight_biz_id ON memory_insight(user_id, agent_id, biz_id);
CREATE INDEX IF NOT EXISTS idx_insight_memory_id ON memory_insight(user_id, agent_id);
CREATE INDEX IF NOT EXISTS idx_insight_type ON memory_insight(user_id, agent_id, type);
CREATE INDEX IF NOT EXISTS idx_insight_parent ON memory_insight(user_id, agent_id, parent_insight_id);
CREATE INDEX IF NOT EXISTS idx_insight_tier ON memory_insight(user_id, agent_id, tier);
CREATE INDEX IF NOT EXISTS idx_insight_tier_group ON memory_insight(user_id, agent_id, type, tier, group_name);

CREATE TABLE IF NOT EXISTS memory_conversation_buffer (
    id         INTEGER      NOT NULL PRIMARY KEY AUTOINCREMENT,
    session_id TEXT         NOT NULL,
    user_id    TEXT         NOT NULL,
    agent_id   TEXT         NOT NULL,
    memory_id  TEXT         NOT NULL,
    role       TEXT         NOT NULL,
    content    TEXT         NOT NULL,
    user_name  TEXT,
    timestamp  TEXT,
    extracted  INTEGER      NOT NULL DEFAULT 0,
    created_at TEXT         NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT         NOT NULL DEFAULT (datetime('now')),
    deleted    INTEGER      NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_buf_pending
    ON memory_conversation_buffer(session_id, extracted, deleted, id);
CREATE INDEX IF NOT EXISTS idx_buf_recent ON memory_conversation_buffer(session_id, deleted, id);
CREATE INDEX IF NOT EXISTS idx_buf_memory_id ON memory_conversation_buffer(user_id, agent_id, deleted, id);

CREATE TABLE IF NOT EXISTS memory_insight_buffer (
    id                INTEGER      NOT NULL PRIMARY KEY AUTOINCREMENT,
    user_id           TEXT         NOT NULL,
    agent_id          TEXT         NOT NULL,
    memory_id         TEXT         NOT NULL,
    insight_type_name TEXT         NOT NULL,
    item_id           INTEGER      NOT NULL,
    group_name        TEXT,
    built             INTEGER      NOT NULL DEFAULT 0,
    created_at        TEXT         NOT NULL DEFAULT (datetime('now')),
    updated_at        TEXT         NOT NULL DEFAULT (datetime('now')),
    deleted           INTEGER      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_insight_buffer
    ON memory_insight_buffer(user_id, agent_id, insight_type_name, item_id);
CREATE INDEX IF NOT EXISTS idx_insight_buf_type
    ON memory_insight_buffer(user_id, agent_id, insight_type_name);
CREATE INDEX IF NOT EXISTS idx_insight_buf_group
    ON memory_insight_buffer(user_id, agent_id, insight_type_name, group_name);

-- === V2__init_text_search.sql ===
-- http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

CREATE VIRTUAL TABLE IF NOT EXISTS item_fts USING fts5(
    biz_id    UNINDEXED,
    memory_id UNINDEXED,
    content,
    tokenize = 'trigram'
);

CREATE VIRTUAL TABLE IF NOT EXISTS insight_fts USING fts5(
    biz_id    UNINDEXED,
    memory_id UNINDEXED,
    content,
    tokenize = 'trigram'
);

CREATE VIRTUAL TABLE IF NOT EXISTS raw_data_fts USING fts5(
    biz_id    UNINDEXED,
    memory_id UNINDEXED,
    content,
    tokenize = 'trigram'
);

CREATE TRIGGER IF NOT EXISTS item_fts_ai AFTER INSERT ON memory_item BEGIN INSERT INTO item_fts(rowid, biz_id, memory_id, content) VALUES (new.id, new.biz_id, new.memory_id, new.content); END;

CREATE TRIGGER IF NOT EXISTS item_fts_au AFTER UPDATE ON memory_item WHEN new.deleted = 0 BEGIN DELETE FROM item_fts WHERE rowid = old.id; INSERT INTO item_fts(rowid, biz_id, memory_id, content) VALUES (new.id, new.biz_id, new.memory_id, new.content); END;

CREATE TRIGGER IF NOT EXISTS item_fts_ad AFTER UPDATE ON memory_item WHEN new.deleted = 1 AND old.deleted = 0 BEGIN DELETE FROM item_fts WHERE rowid = old.id; END;

CREATE TRIGGER IF NOT EXISTS insight_fts_ai AFTER INSERT ON memory_insight BEGIN INSERT INTO insight_fts(rowid, biz_id, memory_id, content) VALUES (new.id, new.biz_id, new.memory_id, new.content); END;

CREATE TRIGGER IF NOT EXISTS insight_fts_au AFTER UPDATE ON memory_insight WHEN new.deleted = 0 BEGIN DELETE FROM insight_fts WHERE rowid = old.id; INSERT INTO insight_fts(rowid, biz_id, memory_id, content) VALUES (new.id, new.biz_id, new.memory_id, new.content); END;

CREATE TRIGGER IF NOT EXISTS insight_fts_ad AFTER UPDATE ON memory_insight WHEN new.deleted = 1 AND old.deleted = 0 BEGIN DELETE FROM insight_fts WHERE rowid = old.id; END;

CREATE TRIGGER IF NOT EXISTS raw_data_fts_ai AFTER INSERT ON memory_raw_data BEGIN INSERT INTO raw_data_fts(rowid, biz_id, memory_id, content) VALUES (new.id, new.biz_id, new.memory_id, json_extract(new.segment, '$.content')); END;

CREATE TRIGGER IF NOT EXISTS raw_data_fts_au AFTER UPDATE ON memory_raw_data WHEN new.deleted = 0 BEGIN DELETE FROM raw_data_fts WHERE rowid = old.id; INSERT INTO raw_data_fts(rowid, biz_id, memory_id, content) VALUES (new.id, new.biz_id, new.memory_id, json_extract(new.segment, '$.content')); END;

CREATE TRIGGER IF NOT EXISTS raw_data_fts_ad AFTER UPDATE ON memory_raw_data WHEN new.deleted = 1 AND old.deleted = 0 BEGIN DELETE FROM raw_data_fts WHERE rowid = old.id; END;

INSERT OR REPLACE INTO item_fts(rowid, biz_id, memory_id, content)
SELECT id, biz_id, memory_id, content
FROM memory_item
WHERE deleted = 0;

INSERT OR REPLACE INTO insight_fts(rowid, biz_id, memory_id, content)
SELECT id, biz_id, memory_id, content
FROM memory_insight
WHERE deleted = 0;

INSERT OR REPLACE INTO raw_data_fts(rowid, biz_id, memory_id, content)
SELECT id, biz_id, memory_id, json_extract(segment, '$.content')
FROM memory_raw_data
WHERE deleted = 0
  AND json_extract(segment, '$.content') IS NOT NULL;

-- === V3__multimodal.sql ===
-- http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

CREATE TABLE IF NOT EXISTS memory_resource (
    id         INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    biz_id     TEXT    NOT NULL,
    user_id    TEXT    NOT NULL,
    agent_id   TEXT    NOT NULL,
    memory_id  TEXT    NOT NULL,
    source_uri TEXT,
    storage_uri TEXT,
    file_name  TEXT,
    mime_type  TEXT,
    checksum   TEXT,
    size_bytes INTEGER,
    metadata   TEXT,
    created_at TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT    NOT NULL DEFAULT (datetime('now')),
    deleted    INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_resource_biz_id
    ON memory_resource(user_id, agent_id, biz_id);
CREATE INDEX IF NOT EXISTS idx_resource_memory_id
    ON memory_resource(user_id, agent_id);

ALTER TABLE memory_raw_data ADD COLUMN resource_id TEXT;
ALTER TABLE memory_raw_data ADD COLUMN mime_type TEXT;
CREATE INDEX IF NOT EXISTS idx_raw_data_resource_id
    ON memory_raw_data(user_id, agent_id, resource_id);

-- === V4__bubble_state.sql ===
-- http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

CREATE TABLE IF NOT EXISTS memory_insight_bubble_state (
    id           INTEGER      NOT NULL PRIMARY KEY AUTOINCREMENT,
    memory_id    TEXT         NOT NULL,
    tier         TEXT         NOT NULL,
    insight_type TEXT         NOT NULL,
    dirty_count  INTEGER      NOT NULL DEFAULT 0,
    created_at   TEXT         NOT NULL DEFAULT (datetime('now')),
    updated_at   TEXT         NOT NULL DEFAULT (datetime('now')),
    deleted      INTEGER      NOT NULL DEFAULT 0,
    UNIQUE(memory_id, tier, insight_type)
);

-- === V5__item_temporal_fields.sql ===
-- http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

-- Temporal item columns are ensured by MemoryStoreDdl for dialect-safe upgrades.
SELECT 1;

-- === V6__graph_store.sql ===
-- http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

CREATE TABLE IF NOT EXISTS memory_graph_entity (
    id           INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    user_id      TEXT    NOT NULL,
    agent_id     TEXT    NOT NULL,
    memory_id    TEXT    NOT NULL,
    entity_key   TEXT    NOT NULL,
    display_name TEXT    NOT NULL,
    entity_type  TEXT    NOT NULL,
    metadata     TEXT,
    created_at   TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at   TEXT    NOT NULL DEFAULT (datetime('now')),
    deleted      INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_graph_entity_identity
    ON memory_graph_entity(memory_id, entity_key);

CREATE TABLE IF NOT EXISTS memory_item_entity_mention (
    id         INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    user_id    TEXT    NOT NULL,
    agent_id   TEXT    NOT NULL,
    memory_id  TEXT    NOT NULL,
    item_id    INTEGER NOT NULL,
    entity_key TEXT    NOT NULL,
    confidence REAL,
    metadata   TEXT,
    created_at TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT    NOT NULL DEFAULT (datetime('now')),
    deleted    INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_item_entity_mention_identity
    ON memory_item_entity_mention(memory_id, item_id, entity_key);
CREATE INDEX IF NOT EXISTS idx_item_entity_mention_item
    ON memory_item_entity_mention(memory_id, item_id);
CREATE INDEX IF NOT EXISTS idx_item_entity_mention_entity_key
    ON memory_item_entity_mention(memory_id, entity_key);

CREATE TABLE IF NOT EXISTS memory_item_link (
    id             INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    user_id        TEXT    NOT NULL,
    agent_id       TEXT    NOT NULL,
    memory_id      TEXT    NOT NULL,
    source_item_id INTEGER NOT NULL,
    target_item_id INTEGER NOT NULL,
    link_type      TEXT    NOT NULL,
    strength       REAL,
    metadata       TEXT,
    created_at     TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at     TEXT    NOT NULL DEFAULT (datetime('now')),
    deleted        INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_item_link_identity
    ON memory_item_link(memory_id, source_item_id, target_item_id, link_type);
CREATE INDEX IF NOT EXISTS idx_item_link_source_type
    ON memory_item_link(memory_id, source_item_id, link_type);
CREATE INDEX IF NOT EXISTS idx_item_link_target_type
    ON memory_item_link(memory_id, target_item_id, link_type);

CREATE TABLE IF NOT EXISTS memory_entity_cooccurrence (
    id                 INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    user_id            TEXT    NOT NULL,
    agent_id           TEXT    NOT NULL,
    memory_id          TEXT    NOT NULL,
    left_entity_key    TEXT    NOT NULL,
    right_entity_key   TEXT    NOT NULL,
    cooccurrence_count INTEGER NOT NULL,
    metadata           TEXT,
    created_at         TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at         TEXT    NOT NULL DEFAULT (datetime('now')),
    deleted            INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_entity_cooccurrence_identity
    ON memory_entity_cooccurrence(memory_id, left_entity_key, right_entity_key);

-- === V8__graph_entity_alias_store.sql ===
-- http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

CREATE TABLE IF NOT EXISTS memory_graph_entity_alias (
    id               INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    user_id          TEXT    NOT NULL,
    agent_id         TEXT    NOT NULL,
    memory_id        TEXT    NOT NULL,
    entity_key       TEXT    NOT NULL,
    entity_type      TEXT    NOT NULL,
    normalized_alias TEXT    NOT NULL,
    evidence_count   INTEGER NOT NULL,
    metadata         TEXT,
    created_at       TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at       TEXT    NOT NULL DEFAULT (datetime('now')),
    deleted          INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_graph_entity_alias_identity
    ON memory_graph_entity_alias(memory_id, entity_type, entity_key, normalized_alias);
CREATE INDEX IF NOT EXISTS idx_graph_entity_alias_lookup
    ON memory_graph_entity_alias(memory_id, entity_type, normalized_alias);

-- === V9__item_temporal_lookup.sql ===
-- http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

-- Temporal lookup columns, backfill, and indexes are ensured by MemoryStoreDdl.
SELECT 1;

-- === V10__item_graph_evolution.sql ===
-- http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

ALTER TABLE memory_item ADD COLUMN extraction_batch_id TEXT;
CREATE INDEX IF NOT EXISTS idx_memory_item_batch
    ON memory_item(memory_id, extraction_batch_id);

ALTER TABLE memory_item_link ADD COLUMN relation_code TEXT;
ALTER TABLE memory_item_link ADD COLUMN evidence_source TEXT;

CREATE TABLE IF NOT EXISTS memory_item_graph_batch (
    id                        INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    user_id                   TEXT    NOT NULL,
    agent_id                  TEXT    NOT NULL,
    memory_id                 TEXT    NOT NULL,
    extraction_batch_id       TEXT    NOT NULL,
    state                     TEXT    NOT NULL,
    error_message             TEXT,
    retry_promotion_supported INTEGER NOT NULL DEFAULT 0,
    created_at                TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at                TEXT    NOT NULL DEFAULT (datetime('now')),
    deleted                   INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_item_graph_batch_identity
    ON memory_item_graph_batch(memory_id, extraction_batch_id);

CREATE TABLE IF NOT EXISTS memory_graph_alias_batch_receipt (
    id                  INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    user_id             TEXT    NOT NULL,
    agent_id            TEXT    NOT NULL,
    memory_id           TEXT    NOT NULL,
    entity_key          TEXT    NOT NULL,
    entity_type         TEXT    NOT NULL,
    normalized_alias    TEXT    NOT NULL,
    extraction_batch_id TEXT    NOT NULL,
    created_at          TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at          TEXT    NOT NULL DEFAULT (datetime('now')),
    deleted             INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_graph_alias_batch_receipt_identity
    ON memory_graph_alias_batch_receipt(
        memory_id,
        entity_key,
        entity_type,
        normalized_alias,
        extraction_batch_id
    );

-- === V11__simplified_thread_core_v1.sql ===
-- http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

CREATE TABLE IF NOT EXISTS memory_thread (
    id                          INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    memory_id                   TEXT    NOT NULL,
    thread_key                  TEXT    NOT NULL,
    thread_type                 TEXT    NOT NULL,
    anchor_kind                 TEXT    NOT NULL,
    anchor_key                  TEXT    NOT NULL,
    display_label               TEXT,
    lifecycle_status            TEXT    NOT NULL,
    object_state                TEXT    NOT NULL,
    headline                    TEXT,
    snapshot_json               TEXT    NOT NULL,
    snapshot_version            INTEGER NOT NULL,
    opened_at                   TEXT,
    last_event_at               TEXT,
    last_meaningful_update_at   TEXT,
    closed_at                   TEXT,
    event_count                 INTEGER NOT NULL,
    member_count                INTEGER NOT NULL,
    created_at                  TEXT    NOT NULL,
    updated_at                  TEXT    NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_memory_thread_key
    ON memory_thread(memory_id, thread_key);
CREATE UNIQUE INDEX IF NOT EXISTS uk_memory_thread_anchor
    ON memory_thread(memory_id, anchor_kind, anchor_key);

CREATE TABLE IF NOT EXISTS memory_thread_event (
    id                    INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    memory_id             TEXT    NOT NULL,
    thread_key            TEXT    NOT NULL,
    event_key             TEXT    NOT NULL,
    event_seq             INTEGER NOT NULL,
    event_type            TEXT    NOT NULL,
    event_time            TEXT    NOT NULL,
    event_payload_json    TEXT    NOT NULL,
    event_payload_version INTEGER NOT NULL,
    is_meaningful         INTEGER NOT NULL,
    confidence            REAL,
    created_at            TEXT    NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_memory_thread_event
    ON memory_thread_event(memory_id, thread_key, event_key);
CREATE INDEX IF NOT EXISTS idx_memory_thread_event_seq
    ON memory_thread_event(memory_id, thread_key, event_seq);

CREATE TABLE IF NOT EXISTS memory_thread_membership (
    id                INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    memory_id         TEXT    NOT NULL,
    thread_key        TEXT    NOT NULL,
    item_id           INTEGER NOT NULL,
    role              TEXT    NOT NULL,
    is_primary        INTEGER NOT NULL,
    relevance_weight  REAL    NOT NULL,
    created_at        TEXT    NOT NULL,
    updated_at        TEXT    NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_memory_thread_membership
    ON memory_thread_membership(memory_id, thread_key, item_id);
CREATE INDEX IF NOT EXISTS idx_memory_thread_membership_item
    ON memory_thread_membership(memory_id, item_id);

CREATE TABLE IF NOT EXISTS thread_intake_outbox (
    id                      INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    memory_id               TEXT    NOT NULL,
    trigger_item_id         INTEGER NOT NULL,
    enqueue_generation      INTEGER NOT NULL DEFAULT 1,
    status                  TEXT    NOT NULL,
    attempt_count           INTEGER NOT NULL,
    claimed_at              TEXT,
    lease_expires_at        TEXT,
    failure_reason          TEXT,
    last_processed_item_id  INTEGER,
    enqueued_at             TEXT    NOT NULL,
    finalized_at            TEXT,
    created_at              TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at              TEXT    NOT NULL DEFAULT (datetime('now'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_thread_intake_outbox
    ON thread_intake_outbox(memory_id, trigger_item_id);
CREATE INDEX IF NOT EXISTS idx_thread_intake_outbox_status
    ON thread_intake_outbox(memory_id, status, trigger_item_id);

CREATE TABLE IF NOT EXISTS memory_thread_runtime (
    memory_id                       TEXT    NOT NULL PRIMARY KEY,
    projection_state                TEXT    NOT NULL,
    pending_count                   INTEGER NOT NULL,
    failed_count                    INTEGER NOT NULL,
    last_enqueued_item_id           INTEGER,
    last_processed_item_id          INTEGER,
    rebuild_in_progress             INTEGER NOT NULL,
    rebuild_cutoff_item_id          INTEGER,
    rebuild_epoch                   INTEGER NOT NULL DEFAULT 0,
    materialization_policy_version  TEXT    NOT NULL,
    invalidation_reason             TEXT,
    updated_at                      TEXT    NOT NULL
);

INSERT INTO memory_thread_runtime (
    memory_id,
    projection_state,
    pending_count,
    failed_count,
    last_enqueued_item_id,
    last_processed_item_id,
    rebuild_in_progress,
    rebuild_cutoff_item_id,
    materialization_policy_version,
    invalidation_reason,
    updated_at
)
SELECT src.memory_id,
       'REBUILD_REQUIRED',
       0,
       0,
       NULL,
       NULL,
       0,
       NULL,
       'thread-core-v1',
       'migration bootstrap',
       datetime('now')
FROM (
    SELECT DISTINCT memory_id
    FROM memory_item
    WHERE deleted = 0
) src
WHERE src.memory_id IS NOT NULL
  AND NOT EXISTS (
        SELECT 1
        FROM memory_thread_runtime runtime
        WHERE runtime.memory_id = src.memory_id
    );

-- === V12__memory_thread_enrichment_input.sql ===
-- http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

CREATE TABLE IF NOT EXISTS memory_thread_enrichment_input (
    id                                   INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    memory_id                            TEXT    NOT NULL,
    thread_key                           TEXT    NOT NULL,
    input_run_key                        TEXT    NOT NULL,
    entry_seq                            INTEGER NOT NULL,
    basis_cutoff_item_id                 INTEGER NOT NULL,
    basis_meaningful_event_count         INTEGER NOT NULL,
    basis_materialization_policy_version TEXT    NOT NULL,
    payload_json                         TEXT    NOT NULL,
    provenance_json                      TEXT    NOT NULL,
    created_at                           TEXT    NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_memory_thread_enrichment_input_run_entry
    ON memory_thread_enrichment_input(memory_id, input_run_key, entry_seq);

CREATE INDEX IF NOT EXISTS idx_memory_thread_enrichment_input_replay
    ON memory_thread_enrichment_input(
        memory_id,
        basis_materialization_policy_version,
        basis_cutoff_item_id,
        basis_meaningful_event_count,
        input_run_key,
        entry_seq
    );



