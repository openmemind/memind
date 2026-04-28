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

-- Squashed schema for mysql.
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
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    biz_id            VARCHAR(64)  NOT NULL,
    user_id           VARCHAR(64)  NOT NULL,
    agent_id          VARCHAR(64)  NOT NULL,
    memory_id         VARCHAR(200) NOT NULL,
    type              VARCHAR(32)  NOT NULL,
    source_client     VARCHAR(64),
    content_id        VARCHAR(200),
    segment           JSON,
    caption           TEXT,
    caption_vector_id VARCHAR(200),
    metadata          JSON,
    start_time        DATETIME(3) DEFAULT NULL,
    end_time          DATETIME(3) DEFAULT NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted           TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_raw_data_biz_id (user_id, agent_id, biz_id),
    KEY idx_raw_data_content_id (user_id, agent_id, content_id),
    KEY idx_raw_data_memory_id (user_id, agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS memory_item (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    biz_id        BIGINT       NOT NULL,
    user_id       VARCHAR(64)  NOT NULL,
    agent_id      VARCHAR(64)  NOT NULL,
    memory_id     VARCHAR(200) NOT NULL,
    content       TEXT         NOT NULL,
    scope         VARCHAR(16)  NOT NULL,
    category      VARCHAR(64),
    vector_id     VARCHAR(200),
    raw_data_id   VARCHAR(64),
    content_hash  VARCHAR(128),
    occurred_at   DATETIME(3),
    observed_at   DATETIME(3),
    type          VARCHAR(16)  NOT NULL DEFAULT 'FACT',
    raw_data_type VARCHAR(32)  NOT NULL DEFAULT 'CONVERSATION',
    source_client VARCHAR(64),
    metadata      JSON,
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_biz_id (user_id, agent_id, biz_id),
    UNIQUE KEY uk_item_content_hash (user_id, agent_id, content_hash),
    KEY idx_item_memory_id (user_id, agent_id),
    KEY idx_item_raw_data_id (user_id, agent_id, raw_data_id),
    KEY idx_item_type (user_id, agent_id, type),
    KEY idx_item_category (memory_id, category),
    KEY idx_item_scope_category (memory_id, scope, category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS memory_insight_type (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    biz_id                BIGINT,
    name                  VARCHAR(128) NOT NULL,
    description           TEXT,
    description_vector_id VARCHAR(200),
    categories            JSON,
    target_tokens         INT          NOT NULL DEFAULT 0,
    analysis_mode         VARCHAR(32) DEFAULT 'BRANCH',
    scope                 VARCHAR(32),
    tree_config           JSON,
    last_updated_at       DATETIME(3),
    created_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted               TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_insight_type_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS memory_insight (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    biz_id            BIGINT       NOT NULL,
    user_id           VARCHAR(64)  NOT NULL,
    agent_id          VARCHAR(64)  NOT NULL,
    memory_id         VARCHAR(200) NOT NULL,
    type              VARCHAR(128) NOT NULL,
    scope             VARCHAR(16)  NOT NULL,
    name              VARCHAR(256),
    categories        JSON,
    content           MEDIUMTEXT,
    points            JSON,
    group_name        VARCHAR(64),
    last_reasoned_at  DATETIME(3),
    summary_embedding JSON,
    tier              VARCHAR(16),
    parent_insight_id BIGINT,
    child_insight_ids JSON,
    version           INT          NOT NULL DEFAULT 1,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted           TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_insight_biz_id (user_id, agent_id, biz_id),
    KEY idx_insight_memory_id (user_id, agent_id),
    KEY idx_insight_type (user_id, agent_id, type),
    KEY idx_insight_parent (user_id, agent_id, parent_insight_id),
    KEY idx_insight_tier (user_id, agent_id, tier),
    KEY idx_insight_tier_group (user_id, agent_id, type, tier, group_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS memory_conversation_buffer (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    session_id VARCHAR(128) NOT NULL,
    user_id    VARCHAR(64)  NOT NULL,
    agent_id   VARCHAR(64)  NOT NULL,
    memory_id  VARCHAR(200) NOT NULL,
    role       VARCHAR(16)  NOT NULL,
    content    TEXT         NOT NULL,
    user_name  VARCHAR(64),
    source_client VARCHAR(64),
    timestamp  DATETIME(3),
    extracted  TINYINT      NOT NULL DEFAULT 0,
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted    TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_buf_pending (session_id, extracted, deleted, id),
    KEY idx_buf_recent (session_id, deleted, id),
    KEY idx_buf_memory_id (user_id, agent_id, deleted, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS memory_insight_buffer (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    user_id           VARCHAR(64)  NOT NULL,
    agent_id          VARCHAR(64)  NOT NULL,
    memory_id         VARCHAR(200) NOT NULL,
    insight_type_name VARCHAR(128) NOT NULL,
    item_id           BIGINT       NOT NULL,
    group_name        VARCHAR(64),
    built             TINYINT      NOT NULL DEFAULT 0,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted           TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_insight_buffer (user_id, agent_id, insight_type_name, item_id),
    KEY idx_insight_buf_type (user_id, agent_id, insight_type_name),
    KEY idx_insight_buf_group (user_id, agent_id, insight_type_name, group_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- === V2__init_text_search.sql ===
-- http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

ALTER TABLE memory_raw_data
    ADD COLUMN IF NOT EXISTS segment_content TEXT GENERATED ALWAYS AS
        (JSON_UNQUOTE(JSON_EXTRACT(segment, '$.content'))) STORED;

DROP PROCEDURE IF EXISTS init_memind_mysql_text_search;

CREATE PROCEDURE init_memind_mysql_text_search()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM INFORMATION_SCHEMA.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'memory_item'
          AND INDEX_NAME = 'ft_item_content'
    ) THEN
        SET @create_item_index =
                'CREATE FULLTEXT INDEX ft_item_content ON memory_item(content) WITH PARSER ngram';
        PREPARE stmt FROM @create_item_index;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM INFORMATION_SCHEMA.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'memory_insight'
          AND INDEX_NAME = 'ft_insight_content'
    ) THEN
        SET @create_insight_index =
                'CREATE FULLTEXT INDEX ft_insight_content ON memory_insight(content) WITH PARSER ngram';
        PREPARE stmt FROM @create_insight_index;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM INFORMATION_SCHEMA.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'memory_raw_data'
          AND INDEX_NAME = 'ft_raw_data_segment_content'
    ) THEN
        SET @create_raw_data_index =
                'CREATE FULLTEXT INDEX ft_raw_data_segment_content ON memory_raw_data(segment_content) WITH PARSER ngram';
        PREPARE stmt FROM @create_raw_data_index;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END;

CALL init_memind_mysql_text_search();

DROP PROCEDURE IF EXISTS init_memind_mysql_text_search;

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
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    biz_id     VARCHAR(64)  NOT NULL,
    user_id    VARCHAR(64)  NOT NULL,
    agent_id   VARCHAR(64)  NOT NULL,
    memory_id  VARCHAR(200) NOT NULL,
    source_uri VARCHAR(1024),
    storage_uri VARCHAR(1024),
    file_name  VARCHAR(512),
    mime_type  VARCHAR(128),
    checksum   VARCHAR(128),
    size_bytes BIGINT,
    metadata   JSON,
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted    TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_resource_biz_id (user_id, agent_id, biz_id),
    KEY idx_resource_memory_id (user_id, agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE memory_raw_data
    ADD COLUMN IF NOT EXISTS resource_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS mime_type VARCHAR(128);
CREATE INDEX idx_raw_data_resource_id
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
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    memory_id    VARCHAR(255) NOT NULL,
    tier         VARCHAR(32)  NOT NULL,
    insight_type VARCHAR(255) NOT NULL,
    dirty_count  INT          NOT NULL DEFAULT 0,
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_memory_insight_bubble_state (memory_id, tier, insight_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      VARCHAR(64)  NOT NULL,
    agent_id     VARCHAR(64)  NOT NULL,
    memory_id    VARCHAR(200) NOT NULL,
    entity_key   VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    entity_type  VARCHAR(32)  NOT NULL,
    metadata     JSON,
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_graph_entity_identity (memory_id, entity_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS memory_item_entity_mention (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    VARCHAR(64)  NOT NULL,
    agent_id   VARCHAR(64)  NOT NULL,
    memory_id  VARCHAR(200) NOT NULL,
    item_id    BIGINT       NOT NULL,
    entity_key VARCHAR(255) NOT NULL,
    confidence FLOAT,
    metadata   JSON,
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted    TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_entity_mention_identity (memory_id, item_id, entity_key),
    KEY idx_item_entity_mention_item (memory_id, item_id),
    KEY idx_item_entity_mention_entity_key (memory_id, entity_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS memory_item_link (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        VARCHAR(64)  NOT NULL,
    agent_id       VARCHAR(64)  NOT NULL,
    memory_id      VARCHAR(200) NOT NULL,
    source_item_id BIGINT       NOT NULL,
    target_item_id BIGINT       NOT NULL,
    link_type      VARCHAR(32)  NOT NULL,
    strength       DOUBLE,
    metadata       JSON,
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted        TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_link_identity (memory_id, source_item_id, target_item_id, link_type),
    KEY idx_item_link_source_type (memory_id, source_item_id, link_type),
    KEY idx_item_link_target_type (memory_id, target_item_id, link_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS memory_entity_cooccurrence (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    user_id            VARCHAR(64)  NOT NULL,
    agent_id           VARCHAR(64)  NOT NULL,
    memory_id          VARCHAR(200) NOT NULL,
    left_entity_key    VARCHAR(255) NOT NULL,
    right_entity_key   VARCHAR(255) NOT NULL,
    cooccurrence_count INT          NOT NULL,
    metadata           JSON,
    created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted            TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_entity_cooccurrence_identity (memory_id, left_entity_key, right_entity_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          VARCHAR(64)  NOT NULL,
    agent_id         VARCHAR(64)  NOT NULL,
    memory_id        VARCHAR(200) NOT NULL,
    entity_key       VARCHAR(255) NOT NULL,
    entity_type      VARCHAR(32)  NOT NULL,
    normalized_alias VARCHAR(255) NOT NULL,
    evidence_count   INT          NOT NULL,
    metadata         JSON,
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted          TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_graph_entity_alias_identity (memory_id, entity_type, entity_key, normalized_alias),
    KEY idx_graph_entity_alias_lookup (memory_id, entity_type, normalized_alias)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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

ALTER TABLE memory_item
    ADD COLUMN extraction_batch_id VARCHAR(64) NULL;
CREATE INDEX idx_memory_item_batch ON memory_item(memory_id, extraction_batch_id);

ALTER TABLE memory_item_link
    ADD COLUMN relation_code VARCHAR(64) NULL,
    ADD COLUMN evidence_source VARCHAR(64) NULL;

CREATE TABLE IF NOT EXISTS memory_item_graph_batch (
    id                        BIGINT       NOT NULL AUTO_INCREMENT,
    user_id                   VARCHAR(64)  NOT NULL,
    agent_id                  VARCHAR(64)  NOT NULL,
    memory_id                 VARCHAR(200) NOT NULL,
    extraction_batch_id       VARCHAR(64)  NOT NULL,
    state                     VARCHAR(32)  NOT NULL,
    error_message             VARCHAR(1024),
    retry_promotion_supported TINYINT      NOT NULL DEFAULT 0,
    created_at                DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at                DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted                   TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_graph_batch_identity (memory_id, extraction_batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS memory_graph_alias_batch_receipt (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    user_id             VARCHAR(64)  NOT NULL,
    agent_id            VARCHAR(64)  NOT NULL,
    memory_id           VARCHAR(200) NOT NULL,
    entity_key          VARCHAR(255) NOT NULL,
    entity_type         VARCHAR(32)  NOT NULL,
    normalized_alias    VARCHAR(255) NOT NULL,
    extraction_batch_id VARCHAR(64)  NOT NULL,
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted             TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_graph_alias_batch_receipt_identity (
        memory_id,
        entity_key,
        entity_type,
        normalized_alias,
        extraction_batch_id
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
    enqueue_generation      BIGINT       NOT NULL DEFAULT 1,
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
    id                                   BIGINT       NOT NULL AUTO_INCREMENT,
    memory_id                            VARCHAR(200) NOT NULL,
    thread_key                           VARCHAR(255) NOT NULL,
    input_run_key                        VARCHAR(255) NOT NULL,
    entry_seq                            INT          NOT NULL,
    basis_cutoff_item_id                 BIGINT       NOT NULL,
    basis_meaningful_event_count         BIGINT       NOT NULL,
    basis_materialization_policy_version VARCHAR(64)  NOT NULL,
    payload_json                         JSON         NOT NULL,
    provenance_json                      JSON         NOT NULL,
    created_at                           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_memory_thread_enrichment_input_run_entry (memory_id, input_run_key, entry_seq),
    KEY idx_memory_thread_enrichment_input_replay (
        memory_id,
        basis_materialization_policy_version,
        basis_cutoff_item_id,
        basis_meaningful_event_count,
        input_run_key,
        entry_seq
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


