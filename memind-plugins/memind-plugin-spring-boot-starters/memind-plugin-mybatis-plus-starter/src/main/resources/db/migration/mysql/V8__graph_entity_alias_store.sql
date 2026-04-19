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
