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

CREATE TABLE IF NOT EXISTS memory_thread (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    biz_id             BIGINT       NOT NULL,
    user_id            VARCHAR(64)  NOT NULL,
    agent_id           VARCHAR(64)  NOT NULL,
    memory_id          VARCHAR(200) NOT NULL,
    thread_key         VARCHAR(255) NOT NULL,
    episode_type       VARCHAR(64),
    title              VARCHAR(255),
    summary_snapshot   TEXT,
    status             VARCHAR(32)  NOT NULL,
    confidence         DOUBLE       NOT NULL DEFAULT 0.0,
    start_at           DATETIME(3) NULL,
    end_at             DATETIME(3) NULL,
    last_activity_at   DATETIME(3) NULL,
    origin_item_id     BIGINT,
    anchor_item_id     BIGINT,
    display_order_hint INT          NOT NULL DEFAULT 0,
    metadata           JSON,
    created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted            TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_memory_thread_biz_id (user_id, agent_id, biz_id),
    UNIQUE KEY uk_memory_thread_key (memory_id, thread_key),
    KEY idx_memory_thread_status (memory_id, status, last_activity_at),
    KEY idx_memory_thread_anchor_item (memory_id, anchor_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS memory_thread_item (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    biz_id            BIGINT       NOT NULL,
    user_id           VARCHAR(64)  NOT NULL,
    agent_id          VARCHAR(64)  NOT NULL,
    memory_id         VARCHAR(200) NOT NULL,
    thread_id         BIGINT       NOT NULL,
    item_id           BIGINT       NOT NULL,
    membership_weight DOUBLE       NOT NULL,
    role              VARCHAR(32)  NOT NULL,
    sequence_hint     INT          NOT NULL,
    joined_at         DATETIME(3) NULL,
    metadata          JSON,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted           TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_memory_thread_item_biz_id (user_id, agent_id, biz_id),
    UNIQUE KEY uk_memory_thread_item_item_id (memory_id, item_id),
    KEY idx_memory_thread_item_thread_sequence (memory_id, thread_id, sequence_hint),
    KEY idx_memory_thread_item_item (memory_id, item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
