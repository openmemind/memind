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

ALTER TABLE memory_item ADD COLUMN extraction_batch_id TEXT;
CREATE INDEX IF NOT EXISTS idx_memory_item_batch
    ON memory_item(memory_id, extraction_batch_id);

ALTER TABLE memory_item_link ADD COLUMN relation_code TEXT;
ALTER TABLE memory_item_link ADD COLUMN evidence_source TEXT;

CREATE TABLE IF NOT EXISTS memory_item_graph_batch (
    id                        INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    user_id                   TEXT    NOT NULL,
    agent_id                  TEXT    NOT NULL,
    memory_id                 TEXT    NOT NULL,
    extraction_batch_id       TEXT    NOT NULL,
    state                     TEXT    NOT NULL,
    error_message             TEXT,
    retry_promotion_supported INTEGER NOT NULL DEFAULT 0,
    created_at                TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at                TEXT    NOT NULL DEFAULT (datetime('now')),
    deleted                   INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_item_graph_batch_identity
    ON memory_item_graph_batch(memory_id, extraction_batch_id);

CREATE TABLE IF NOT EXISTS memory_graph_alias_batch_receipt (
    id                  INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    user_id             TEXT    NOT NULL,
    agent_id            TEXT    NOT NULL,
    memory_id           TEXT    NOT NULL,
    entity_key          TEXT    NOT NULL,
    entity_type         TEXT    NOT NULL,
    normalized_alias    TEXT    NOT NULL,
    extraction_batch_id TEXT    NOT NULL,
    created_at          TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at          TEXT    NOT NULL DEFAULT (datetime('now')),
    deleted             INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_graph_alias_batch_receipt_identity
    ON memory_graph_alias_batch_receipt(
        memory_id,
        entity_key,
        entity_type,
        normalized_alias,
        extraction_batch_id
    );
