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

CREATE TABLE IF NOT EXISTS memory_raw_data (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    biz_id            VARCHAR(64)  NOT NULL,
    user_id           VARCHAR(64)  NOT NULL,
    agent_id          VARCHAR(64)  NOT NULL,
    memory_id         VARCHAR(200) NOT NULL,
    type              VARCHAR(32)  NOT NULL,
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
    type          VARCHAR(16)  NOT NULL DEFAULT 'FACT',
    raw_data_type VARCHAR(32)  NOT NULL DEFAULT 'CONVERSATION',
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
    summary_prompt        JSON,
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
    confidence        FLOAT        DEFAULT 0,
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
    timestamp  DATETIME(3),
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted    TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_buf_session (session_id),
    KEY idx_buf_memory_id (user_id, agent_id)
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
