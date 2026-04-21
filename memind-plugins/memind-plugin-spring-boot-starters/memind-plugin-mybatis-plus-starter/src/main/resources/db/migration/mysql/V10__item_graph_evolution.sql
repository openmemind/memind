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
