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
