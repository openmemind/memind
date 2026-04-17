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
    id           INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    user_id      TEXT    NOT NULL,
    agent_id     TEXT    NOT NULL,
    memory_id    TEXT    NOT NULL,
    entity_key   TEXT    NOT NULL,
    display_name TEXT    NOT NULL,
    entity_type  TEXT    NOT NULL,
    metadata     TEXT,
    created_at   TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at   TEXT    NOT NULL DEFAULT (datetime('now')),
    deleted      INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_graph_entity_identity
    ON memory_graph_entity(memory_id, entity_key);

CREATE TABLE IF NOT EXISTS memory_item_entity_mention (
    id         INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    user_id    TEXT    NOT NULL,
    agent_id   TEXT    NOT NULL,
    memory_id  TEXT    NOT NULL,
    item_id    INTEGER NOT NULL,
    entity_key TEXT    NOT NULL,
    confidence REAL,
    metadata   TEXT,
    created_at TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT    NOT NULL DEFAULT (datetime('now')),
    deleted    INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_item_entity_mention_identity
    ON memory_item_entity_mention(memory_id, item_id, entity_key);
CREATE INDEX IF NOT EXISTS idx_item_entity_mention_item
    ON memory_item_entity_mention(memory_id, item_id);
CREATE INDEX IF NOT EXISTS idx_item_entity_mention_entity_key
    ON memory_item_entity_mention(memory_id, entity_key);

CREATE TABLE IF NOT EXISTS memory_item_link (
    id             INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    user_id        TEXT    NOT NULL,
    agent_id       TEXT    NOT NULL,
    memory_id      TEXT    NOT NULL,
    source_item_id INTEGER NOT NULL,
    target_item_id INTEGER NOT NULL,
    link_type      TEXT    NOT NULL,
    strength       REAL,
    metadata       TEXT,
    created_at     TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at     TEXT    NOT NULL DEFAULT (datetime('now')),
    deleted        INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_item_link_identity
    ON memory_item_link(memory_id, source_item_id, target_item_id, link_type);
CREATE INDEX IF NOT EXISTS idx_item_link_source_type
    ON memory_item_link(memory_id, source_item_id, link_type);
CREATE INDEX IF NOT EXISTS idx_item_link_target_type
    ON memory_item_link(memory_id, target_item_id, link_type);

CREATE TABLE IF NOT EXISTS memory_entity_cooccurrence (
    id                 INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    user_id            TEXT    NOT NULL,
    agent_id           TEXT    NOT NULL,
    memory_id          TEXT    NOT NULL,
    left_entity_key    TEXT    NOT NULL,
    right_entity_key   TEXT    NOT NULL,
    cooccurrence_count INTEGER NOT NULL,
    metadata           TEXT,
    created_at         TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at         TEXT    NOT NULL DEFAULT (datetime('now')),
    deleted            INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_entity_cooccurrence_identity
    ON memory_entity_cooccurrence(memory_id, left_entity_key, right_entity_key);
