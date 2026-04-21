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

ALTER TABLE IF EXISTS memory_thread RENAME TO memory_thread_v7_legacy;
ALTER TABLE IF EXISTS memory_thread_item RENAME TO memory_thread_item_v7_legacy;

CREATE TABLE IF NOT EXISTS memory_thread (
    id                        BIGSERIAL    PRIMARY KEY,
    memory_id                 VARCHAR(200) NOT NULL,
    thread_key                VARCHAR(255) NOT NULL,
    thread_type               VARCHAR(32)  NOT NULL,
    anchor_kind               VARCHAR(64)  NOT NULL,
    anchor_key                VARCHAR(255) NOT NULL,
    display_label             VARCHAR(255),
    lifecycle_status          VARCHAR(32)  NOT NULL,
    object_state              VARCHAR(32)  NOT NULL,
    headline                  TEXT,
    snapshot_json             JSONB        NOT NULL,
    snapshot_version          INTEGER      NOT NULL,
    opened_at                 TIMESTAMPTZ,
    last_event_at             TIMESTAMPTZ,
    last_meaningful_update_at TIMESTAMPTZ,
    closed_at                 TIMESTAMPTZ,
    event_count               BIGINT       NOT NULL,
    member_count              BIGINT       NOT NULL,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_memory_thread_key
    ON memory_thread(memory_id, thread_key);
CREATE UNIQUE INDEX IF NOT EXISTS uk_memory_thread_anchor
    ON memory_thread(memory_id, anchor_kind, anchor_key);

CREATE TABLE IF NOT EXISTS memory_thread_event (
    id                    BIGSERIAL    PRIMARY KEY,
    memory_id             VARCHAR(200) NOT NULL,
    thread_key            VARCHAR(255) NOT NULL,
    event_key             VARCHAR(255) NOT NULL,
    event_seq             BIGINT       NOT NULL,
    event_type            VARCHAR(32)  NOT NULL,
    event_time            TIMESTAMPTZ  NOT NULL,
    event_payload_json    JSONB        NOT NULL,
    event_payload_version INTEGER      NOT NULL,
    is_meaningful         BOOLEAN      NOT NULL,
    confidence            DOUBLE PRECISION,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_memory_thread_event
    ON memory_thread_event(memory_id, thread_key, event_key);
CREATE INDEX IF NOT EXISTS idx_memory_thread_event_seq
    ON memory_thread_event(memory_id, thread_key, event_seq);

CREATE TABLE IF NOT EXISTS memory_thread_membership (
    id                BIGSERIAL    PRIMARY KEY,
    memory_id         VARCHAR(200) NOT NULL,
    thread_key        VARCHAR(255) NOT NULL,
    item_id           BIGINT       NOT NULL,
    role              VARCHAR(32)  NOT NULL,
    is_primary        BOOLEAN      NOT NULL,
    relevance_weight  DOUBLE PRECISION NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_memory_thread_membership
    ON memory_thread_membership(memory_id, thread_key, item_id);
CREATE INDEX IF NOT EXISTS idx_memory_thread_membership_item
    ON memory_thread_membership(memory_id, item_id);

CREATE TABLE IF NOT EXISTS thread_intake_outbox (
    id                      BIGSERIAL    PRIMARY KEY,
    memory_id               VARCHAR(200) NOT NULL,
    trigger_item_id         BIGINT       NOT NULL,
    status                  VARCHAR(32)  NOT NULL,
    attempt_count           INTEGER      NOT NULL,
    claimed_at              TIMESTAMPTZ,
    lease_expires_at        TIMESTAMPTZ,
    failure_reason          VARCHAR(255),
    last_processed_item_id  BIGINT,
    enqueued_at             TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finalized_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_thread_intake_outbox
    ON thread_intake_outbox(memory_id, trigger_item_id);
CREATE INDEX IF NOT EXISTS idx_thread_intake_outbox_status
    ON thread_intake_outbox(memory_id, status, trigger_item_id);

CREATE TABLE IF NOT EXISTS memory_thread_runtime (
    memory_id                      VARCHAR(200) NOT NULL PRIMARY KEY,
    projection_state               VARCHAR(32)  NOT NULL,
    pending_count                  BIGINT       NOT NULL,
    failed_count                   BIGINT       NOT NULL,
    last_enqueued_item_id          BIGINT,
    last_processed_item_id         BIGINT,
    rebuild_in_progress            BOOLEAN      NOT NULL,
    rebuild_cutoff_item_id         BIGINT,
    materialization_policy_version VARCHAR(64)  NOT NULL,
    invalidation_reason            VARCHAR(255),
    updated_at                     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
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
       FALSE,
       NULL,
       'thread-core-v1',
       'migration bootstrap',
       CURRENT_TIMESTAMP
FROM (
    SELECT DISTINCT memory_id
    FROM memory_item
    WHERE deleted = FALSE
    UNION
    SELECT DISTINCT memory_id
    FROM memory_thread_v7_legacy
    WHERE deleted = FALSE
) src
WHERE src.memory_id IS NOT NULL
  AND NOT EXISTS (
        SELECT 1
        FROM memory_thread_runtime runtime
        WHERE runtime.memory_id = src.memory_id
    );
