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
    id               INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    user_id          TEXT    NOT NULL,
    agent_id         TEXT    NOT NULL,
    memory_id        TEXT    NOT NULL,
    entity_key       TEXT    NOT NULL,
    entity_type      TEXT    NOT NULL,
    normalized_alias TEXT    NOT NULL,
    evidence_count   INTEGER NOT NULL,
    metadata         TEXT,
    created_at       TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at       TEXT    NOT NULL DEFAULT (datetime('now')),
    deleted          INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_graph_entity_alias_identity
    ON memory_graph_entity_alias(memory_id, entity_type, entity_key, normalized_alias);
CREATE INDEX IF NOT EXISTS idx_graph_entity_alias_lookup
    ON memory_graph_entity_alias(memory_id, entity_type, normalized_alias);
