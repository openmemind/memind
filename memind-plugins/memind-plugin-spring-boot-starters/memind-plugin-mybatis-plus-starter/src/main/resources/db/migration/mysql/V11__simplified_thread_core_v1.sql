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

RENAME TABLE memory_thread TO memory_thread_v7_legacy,
             memory_thread_item TO memory_thread_item_v7_legacy;

CREATE TABLE IF NOT EXISTS memory_thread (
    id                        BIGINT       NOT NULL AUTO_INCREMENT,
    memory_id                 VARCHAR(200) NOT NULL,
    thread_key                VARCHAR(255) NOT NULL,
    thread_type               VARCHAR(32)  NOT NULL,
    anchor_kind               VARCHAR(64)  NOT NULL,
    anchor_key                VARCHAR(255) NOT NULL,
    display_label             VARCHAR(255),
    lifecycle_status          VARCHAR(32)  NOT NULL,
    object_state              VARCHAR(32)  NOT NULL,
    headline                  TEXT,
    snapshot_json             JSON         NOT NULL,
    snapshot_version          INT          NOT NULL,
    opened_at                 DATETIME(3) NULL,
    last_event_at             DATETIME(3) NULL,
    last_meaningful_update_at DATETIME(3) NULL,
    closed_at                 DATETIME(3) NULL,
    event_count               BIGINT       NOT NULL,
    member_count              BIGINT       NOT NULL,
    created_at                DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at                DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_memory_thread_key (memory_id, thread_key),
    UNIQUE KEY uk_memory_thread_anchor (memory_id, anchor_kind, anchor_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS memory_thread_event (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    memory_id             VARCHAR(200) NOT NULL,
    thread_key            VARCHAR(255) NOT NULL,
    event_key             VARCHAR(255) NOT NULL,
    event_seq             BIGINT       NOT NULL,
    event_type            VARCHAR(32)  NOT NULL,
    event_time            DATETIME(3)  NOT NULL,
    event_payload_json    JSON         NOT NULL,
    event_payload_version INT          NOT NULL,
    is_meaningful         TINYINT      NOT NULL,
    confidence            DOUBLE,
    created_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_memory_thread_event (memory_id, thread_key, event_key),
    KEY idx_memory_thread_event_seq (memory_id, thread_key, event_seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS memory_thread_membership (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    memory_id         VARCHAR(200) NOT NULL,
    thread_key        VARCHAR(255) NOT NULL,
    item_id           BIGINT       NOT NULL,
    role              VARCHAR(32)  NOT NULL,
    is_primary        TINYINT      NOT NULL,
    relevance_weight  DOUBLE       NOT NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_memory_thread_membership (memory_id, thread_key, item_id),
    KEY idx_memory_thread_membership_item (memory_id, item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS thread_intake_outbox (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    memory_id               VARCHAR(200) NOT NULL,
    trigger_item_id         BIGINT       NOT NULL,
    status                  VARCHAR(32)  NOT NULL,
    attempt_count           INT          NOT NULL,
    claimed_at              DATETIME(3) NULL,
    lease_expires_at        DATETIME(3) NULL,
    failure_reason          VARCHAR(255),
    last_processed_item_id  BIGINT,
    enqueued_at             DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    finalized_at            DATETIME(3) NULL,
    created_at              DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at              DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_thread_intake_outbox (memory_id, trigger_item_id),
    KEY idx_thread_intake_outbox_status (memory_id, status, trigger_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS memory_thread_runtime (
    memory_id                      VARCHAR(200) NOT NULL,
    projection_state               VARCHAR(32)  NOT NULL,
    pending_count                  BIGINT       NOT NULL,
    failed_count                   BIGINT       NOT NULL,
    last_enqueued_item_id          BIGINT,
    last_processed_item_id         BIGINT,
    rebuild_in_progress            TINYINT      NOT NULL,
    rebuild_cutoff_item_id         BIGINT,
    materialization_policy_version VARCHAR(64)  NOT NULL,
    invalidation_reason            VARCHAR(255),
    updated_at                     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (memory_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
       CURRENT_TIMESTAMP(3)
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
