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
    id                INTEGER      NOT NULL PRIMARY KEY AUTOINCREMENT,
    biz_id            TEXT         NOT NULL,
    user_id           TEXT         NOT NULL,
    agent_id          TEXT         NOT NULL,
    memory_id         TEXT         NOT NULL,
    type              TEXT         NOT NULL,
    content_id        TEXT,
    segment           TEXT,
    caption           TEXT,
    caption_vector_id TEXT,
    metadata          TEXT,
    start_time        TEXT,
    end_time          TEXT,
    created_at        TEXT         NOT NULL DEFAULT (datetime('now')),
    updated_at        TEXT         NOT NULL DEFAULT (datetime('now')),
    deleted           INTEGER      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_raw_data_biz_id ON memory_raw_data(user_id, agent_id, biz_id);
CREATE INDEX IF NOT EXISTS idx_raw_data_content_id ON memory_raw_data(user_id, agent_id, content_id);
CREATE INDEX IF NOT EXISTS idx_raw_data_memory_id ON memory_raw_data(user_id, agent_id);

CREATE TABLE IF NOT EXISTS memory_item (
    id            INTEGER      NOT NULL PRIMARY KEY AUTOINCREMENT,
    biz_id        INTEGER      NOT NULL,
    user_id       TEXT         NOT NULL,
    agent_id      TEXT         NOT NULL,
    memory_id     TEXT         NOT NULL,
    content       TEXT         NOT NULL,
    scope         TEXT         NOT NULL,
    category      TEXT,
    vector_id     TEXT,
    raw_data_id   TEXT,
    content_hash  TEXT,
    occurred_at   TEXT,
    observed_at   TEXT,
    type          TEXT         NOT NULL DEFAULT 'FACT',
    raw_data_type TEXT         NOT NULL DEFAULT 'CONVERSATION',
    metadata      TEXT,
    created_at    TEXT         NOT NULL DEFAULT (datetime('now')),
    updated_at    TEXT         NOT NULL DEFAULT (datetime('now')),
    deleted       INTEGER      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_item_biz_id ON memory_item(user_id, agent_id, biz_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_item_content_hash ON memory_item(user_id, agent_id, content_hash);
CREATE INDEX IF NOT EXISTS idx_item_memory_id ON memory_item(user_id, agent_id);
CREATE INDEX IF NOT EXISTS idx_item_raw_data_id ON memory_item(user_id, agent_id, raw_data_id);
CREATE INDEX IF NOT EXISTS idx_item_type ON memory_item(user_id, agent_id, type);
CREATE INDEX IF NOT EXISTS idx_item_category ON memory_item(memory_id, category);
CREATE INDEX IF NOT EXISTS idx_item_scope_category ON memory_item(memory_id, scope, category);

CREATE TABLE IF NOT EXISTS memory_insight_type (
    id                    INTEGER      NOT NULL PRIMARY KEY AUTOINCREMENT,
    biz_id                INTEGER,
    name                  TEXT         NOT NULL,
    description           TEXT,
    description_vector_id TEXT,
    categories            TEXT,
    target_tokens         INTEGER      NOT NULL DEFAULT 0,
    analysis_mode         TEXT         DEFAULT 'BRANCH',
    scope                 TEXT,
    tree_config           TEXT,
    last_updated_at       TEXT,
    created_at            TEXT         NOT NULL DEFAULT (datetime('now')),
    updated_at            TEXT         NOT NULL DEFAULT (datetime('now')),
    deleted               INTEGER      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_insight_type_name ON memory_insight_type(name);
CREATE INDEX IF NOT EXISTS idx_insight_type_all ON memory_insight_type(id);

CREATE TABLE IF NOT EXISTS memory_insight (
    id                INTEGER      NOT NULL PRIMARY KEY AUTOINCREMENT,
    biz_id            INTEGER      NOT NULL,
    user_id           TEXT         NOT NULL,
    agent_id          TEXT         NOT NULL,
    memory_id         TEXT         NOT NULL,
    type              TEXT         NOT NULL,
    scope             TEXT         NOT NULL,
    name              TEXT,
    categories        TEXT,
    content           TEXT,
    points            TEXT,
    group_name        TEXT,
    last_reasoned_at  TEXT,
    summary_embedding TEXT,
    tier              TEXT,
    parent_insight_id INTEGER,
    child_insight_ids TEXT,
    version           INTEGER      NOT NULL DEFAULT 1,
    created_at        TEXT         NOT NULL DEFAULT (datetime('now')),
    updated_at        TEXT         NOT NULL DEFAULT (datetime('now')),
    deleted           INTEGER      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_insight_biz_id ON memory_insight(user_id, agent_id, biz_id);
CREATE INDEX IF NOT EXISTS idx_insight_memory_id ON memory_insight(user_id, agent_id);
CREATE INDEX IF NOT EXISTS idx_insight_type ON memory_insight(user_id, agent_id, type);
CREATE INDEX IF NOT EXISTS idx_insight_parent ON memory_insight(user_id, agent_id, parent_insight_id);
CREATE INDEX IF NOT EXISTS idx_insight_tier ON memory_insight(user_id, agent_id, tier);
CREATE INDEX IF NOT EXISTS idx_insight_tier_group ON memory_insight(user_id, agent_id, type, tier, group_name);

CREATE TABLE IF NOT EXISTS memory_conversation_buffer (
    id         INTEGER      NOT NULL PRIMARY KEY AUTOINCREMENT,
    session_id TEXT         NOT NULL,
    user_id    TEXT         NOT NULL,
    agent_id   TEXT         NOT NULL,
    memory_id  TEXT         NOT NULL,
    role       TEXT         NOT NULL,
    content    TEXT         NOT NULL,
    user_name  TEXT,
    timestamp  TEXT,
    extracted  INTEGER      NOT NULL DEFAULT 0,
    created_at TEXT         NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT         NOT NULL DEFAULT (datetime('now')),
    deleted    INTEGER      NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_buf_pending
    ON memory_conversation_buffer(session_id, extracted, deleted, id);
CREATE INDEX IF NOT EXISTS idx_buf_recent ON memory_conversation_buffer(session_id, deleted, id);
CREATE INDEX IF NOT EXISTS idx_buf_memory_id ON memory_conversation_buffer(user_id, agent_id, deleted, id);

CREATE TABLE IF NOT EXISTS memory_insight_buffer (
    id                INTEGER      NOT NULL PRIMARY KEY AUTOINCREMENT,
    user_id           TEXT         NOT NULL,
    agent_id          TEXT         NOT NULL,
    memory_id         TEXT         NOT NULL,
    insight_type_name TEXT         NOT NULL,
    item_id           INTEGER      NOT NULL,
    group_name        TEXT,
    built             INTEGER      NOT NULL DEFAULT 0,
    created_at        TEXT         NOT NULL DEFAULT (datetime('now')),
    updated_at        TEXT         NOT NULL DEFAULT (datetime('now')),
    deleted           INTEGER      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_insight_buffer
    ON memory_insight_buffer(user_id, agent_id, insight_type_name, item_id);
CREATE INDEX IF NOT EXISTS idx_insight_buf_type
    ON memory_insight_buffer(user_id, agent_id, insight_type_name);
CREATE INDEX IF NOT EXISTS idx_insight_buf_group
    ON memory_insight_buffer(user_id, agent_id, insight_type_name, group_name);
