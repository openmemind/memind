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
