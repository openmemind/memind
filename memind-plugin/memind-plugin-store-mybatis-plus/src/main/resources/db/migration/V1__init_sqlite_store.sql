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

-- ===== memory_raw_data =====
CREATE TABLE IF NOT EXISTS memory_raw_data (
    id              INTEGER      NOT NULL PRIMARY KEY AUTOINCREMENT,
    biz_id          TEXT         NOT NULL,
    user_id         TEXT         NOT NULL,
    agent_id        TEXT         NOT NULL,
    memory_id       TEXT         NOT NULL,
    type            TEXT         NOT NULL,
    content_id      TEXT,
    segment         TEXT,                           -- JSON 存为 TEXT
    segment_content TEXT GENERATED ALWAYS AS (json_extract(segment, '$.content')) STORED,
    caption         TEXT,
    caption_vector_id TEXT,
    metadata        TEXT,                           -- JSON 存为 TEXT
    created_at      TEXT         NOT NULL DEFAULT (datetime('now')),
    updated_at      TEXT         NOT NULL DEFAULT (datetime('now')),
    deleted         INTEGER      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_raw_data_biz_id ON memory_raw_data(user_id, agent_id, biz_id);
CREATE INDEX IF NOT EXISTS idx_raw_data_content_id ON memory_raw_data(user_id, agent_id, content_id);
CREATE INDEX IF NOT EXISTS idx_raw_data_memory_id ON memory_raw_data(user_id, agent_id);

-- ===== memory_item =====
CREATE TABLE IF NOT EXISTS memory_item (
    id              INTEGER      NOT NULL PRIMARY KEY AUTOINCREMENT,
    biz_id          INTEGER      NOT NULL,
    user_id         TEXT         NOT NULL,
    agent_id        TEXT         NOT NULL,
    memory_id       TEXT         NOT NULL,
    content         TEXT         NOT NULL,
    scope           TEXT         NOT NULL,
    category        TEXT,
    vector_id       TEXT,
    raw_data_id     TEXT,
    content_hash    TEXT,
    occurred_at     TEXT         NOT NULL,
    type            TEXT         NOT NULL DEFAULT 'FACT',
    raw_data_type   TEXT         NOT NULL DEFAULT 'CONVERSATION',
    metadata        TEXT,
    created_at      TEXT         NOT NULL DEFAULT (datetime('now')),
    updated_at      TEXT         NOT NULL DEFAULT (datetime('now')),
    deleted         INTEGER      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_item_biz_id ON memory_item(user_id, agent_id, biz_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_item_content_hash ON memory_item(user_id, agent_id, content_hash);
CREATE INDEX IF NOT EXISTS idx_item_memory_id ON memory_item(user_id, agent_id);
CREATE INDEX IF NOT EXISTS idx_item_raw_data_id ON memory_item(user_id, agent_id, raw_data_id);
CREATE INDEX IF NOT EXISTS idx_item_type ON memory_item(user_id, agent_id, type);
-- migration: add category column for existing DBs (no-op for new DBs; initializer skips duplicate column errors)
ALTER TABLE memory_item ADD COLUMN category TEXT;
CREATE INDEX IF NOT EXISTS idx_item_category ON memory_item(memory_id, category);
CREATE INDEX IF NOT EXISTS idx_item_scope_category ON memory_item(memory_id, scope, category);

-- ===== memory_insight_type =====
CREATE TABLE IF NOT EXISTS memory_insight_type (
    id                    INTEGER      NOT NULL PRIMARY KEY AUTOINCREMENT,
    biz_id                INTEGER,
    name                  TEXT         NOT NULL,
    description           TEXT,
    description_vector_id TEXT,
    categories            TEXT,
    target_tokens         INTEGER      NOT NULL DEFAULT 0,
    summary_prompt        TEXT,
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

-- ===== memory_insight =====
CREATE TABLE IF NOT EXISTS memory_insight (
    id                 INTEGER      NOT NULL PRIMARY KEY AUTOINCREMENT,
    biz_id             INTEGER      NOT NULL,
    user_id            TEXT         NOT NULL,
    agent_id           TEXT         NOT NULL,
    memory_id          TEXT         NOT NULL,
    type               TEXT         NOT NULL,
    scope              TEXT         NOT NULL,
    name               TEXT,
    categories         TEXT,
    content            TEXT,
    points             TEXT,
    group_name         TEXT,
    confidence         REAL         DEFAULT 0,
    last_reasoned_at   TEXT,
    summary_embedding  TEXT,
    tier               TEXT,
    parent_insight_id  INTEGER,
    child_insight_ids  TEXT,
    version            INTEGER      NOT NULL DEFAULT 1,
    created_at         TEXT         NOT NULL DEFAULT (datetime('now')),
    updated_at         TEXT         NOT NULL DEFAULT (datetime('now')),
    deleted            INTEGER      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_insight_biz_id ON memory_insight(user_id, agent_id, biz_id);
CREATE INDEX IF NOT EXISTS idx_insight_memory_id ON memory_insight(user_id, agent_id);
CREATE INDEX IF NOT EXISTS idx_insight_type ON memory_insight(user_id, agent_id, type);
CREATE INDEX IF NOT EXISTS idx_insight_parent ON memory_insight(user_id, agent_id, parent_insight_id);
CREATE INDEX IF NOT EXISTS idx_insight_tier ON memory_insight(user_id, agent_id, tier);
CREATE INDEX IF NOT EXISTS idx_insight_tier_group ON memory_insight(user_id, agent_id, type, tier, group_name);

-- ===== memory_conversation_buffer =====
CREATE TABLE IF NOT EXISTS memory_conversation_buffer (
    id          INTEGER      NOT NULL PRIMARY KEY AUTOINCREMENT,
    session_id  TEXT         NOT NULL,
    user_id     TEXT         NOT NULL,
    agent_id    TEXT         NOT NULL,
    memory_id   TEXT         NOT NULL,
    role        TEXT         NOT NULL,
    content     TEXT         NOT NULL,
    user_name   TEXT,
    timestamp   TEXT,
    created_at  TEXT         NOT NULL DEFAULT (datetime('now')),
    updated_at  TEXT         NOT NULL DEFAULT (datetime('now')),
    deleted     INTEGER      NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_buf_session ON memory_conversation_buffer(session_id);
CREATE INDEX IF NOT EXISTS idx_buf_memory_id ON memory_conversation_buffer(user_id, agent_id);

-- ===== memory_insight_buffer =====
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
CREATE UNIQUE INDEX IF NOT EXISTS uk_insight_buffer ON memory_insight_buffer(user_id, agent_id, insight_type_name, item_id);
CREATE INDEX IF NOT EXISTS idx_insight_buf_type ON memory_insight_buffer(user_id, agent_id, insight_type_name);
CREATE INDEX IF NOT EXISTS idx_insight_buf_group ON memory_insight_buffer(user_id, agent_id, insight_type_name, group_name);

-- ===== FTS5 虚表（全文搜索，trigram tokenizer 支持中文） =====

CREATE VIRTUAL TABLE IF NOT EXISTS item_fts USING fts5(
    biz_id    UNINDEXED,
    memory_id UNINDEXED,
    content,
    tokenize  = 'trigram'
);

CREATE VIRTUAL TABLE IF NOT EXISTS insight_fts USING fts5(
    biz_id    UNINDEXED,
    memory_id UNINDEXED,
    content,
    tokenize  = 'trigram'
);

CREATE VIRTUAL TABLE IF NOT EXISTS raw_data_fts USING fts5(
    biz_id    UNINDEXED,
    memory_id UNINDEXED,
    content,
    tokenize  = 'trigram'
);

-- ===== item_fts 触发器 =====

CREATE TRIGGER IF NOT EXISTS item_fts_ai AFTER INSERT ON memory_item BEGIN
    INSERT INTO item_fts(rowid, biz_id, memory_id, content)
    VALUES (new.id, new.biz_id, new.memory_id, new.content);
END;

CREATE TRIGGER IF NOT EXISTS item_fts_au AFTER UPDATE ON memory_item BEGIN
    DELETE FROM item_fts WHERE rowid = old.id;
    INSERT INTO item_fts(rowid, biz_id, memory_id, content)
    VALUES (new.id, new.biz_id, new.memory_id, new.content);
END;

CREATE TRIGGER IF NOT EXISTS item_fts_ad AFTER UPDATE ON memory_item
    WHEN new.deleted = 1 AND old.deleted = 0 BEGIN
    DELETE FROM item_fts WHERE rowid = old.id;
END;

-- ===== insight_fts 触发器 =====

CREATE TRIGGER IF NOT EXISTS insight_fts_ai AFTER INSERT ON memory_insight BEGIN
    INSERT INTO insight_fts(rowid, biz_id, memory_id, content)
    VALUES (new.id, new.biz_id, new.memory_id, new.content);
END;

CREATE TRIGGER IF NOT EXISTS insight_fts_au AFTER UPDATE ON memory_insight BEGIN
    DELETE FROM insight_fts WHERE rowid = old.id;
    INSERT INTO insight_fts(rowid, biz_id, memory_id, content)
    VALUES (new.id, new.biz_id, new.memory_id, new.content);
END;

CREATE TRIGGER IF NOT EXISTS insight_fts_ad AFTER UPDATE ON memory_insight
    WHEN new.deleted = 1 AND old.deleted = 0 BEGIN
    DELETE FROM insight_fts WHERE rowid = old.id;
END;

-- ===== raw_data_fts 触发器 =====
-- 注意：raw_data_fts 的 content 来自 segment_content（生成列），触发器需读 segment_content

CREATE TRIGGER IF NOT EXISTS raw_data_fts_ai AFTER INSERT ON memory_raw_data BEGIN
    INSERT INTO raw_data_fts(rowid, biz_id, memory_id, content)
    VALUES (new.id, new.biz_id, new.memory_id, new.segment_content);
END;

CREATE TRIGGER IF NOT EXISTS raw_data_fts_au AFTER UPDATE ON memory_raw_data BEGIN
    DELETE FROM raw_data_fts WHERE rowid = old.id;
    INSERT INTO raw_data_fts(rowid, biz_id, memory_id, content)
    VALUES (new.id, new.biz_id, new.memory_id, new.segment_content);
END;

CREATE TRIGGER IF NOT EXISTS raw_data_fts_ad AFTER UPDATE ON memory_raw_data
    WHEN new.deleted = 1 AND old.deleted = 0 BEGIN
    DELETE FROM raw_data_fts WHERE rowid = old.id;
END;
