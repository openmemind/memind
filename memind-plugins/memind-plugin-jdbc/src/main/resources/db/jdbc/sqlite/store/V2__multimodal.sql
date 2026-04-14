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
    id         INTEGER      NOT NULL PRIMARY KEY AUTOINCREMENT,
    biz_id     TEXT         NOT NULL,
    user_id    TEXT         NOT NULL,
    agent_id   TEXT         NOT NULL,
    memory_id  TEXT         NOT NULL,
    source_uri TEXT,
    storage_uri TEXT,
    file_name  TEXT,
    mime_type  TEXT,
    checksum   TEXT,
    size_bytes INTEGER,
    metadata   TEXT,
    created_at TEXT         NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT         NOT NULL DEFAULT (datetime('now')),
    deleted    INTEGER      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_resource_biz_id ON memory_resource(user_id, agent_id, biz_id);
CREATE INDEX IF NOT EXISTS idx_resource_memory_id ON memory_resource(user_id, agent_id);

ALTER TABLE memory_raw_data ADD COLUMN resource_id TEXT;
ALTER TABLE memory_raw_data ADD COLUMN mime_type TEXT;
