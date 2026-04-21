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

ALTER TABLE memory_thread RENAME TO memory_thread_v7_legacy;
ALTER TABLE memory_thread_item RENAME TO memory_thread_item_v7_legacy;

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
    UNION
    SELECT DISTINCT memory_id
    FROM memory_thread_v7_legacy
    WHERE deleted = 0
) src
WHERE src.memory_id IS NOT NULL
  AND NOT EXISTS (
        SELECT 1
        FROM memory_thread_runtime runtime
        WHERE runtime.memory_id = src.memory_id
    );
