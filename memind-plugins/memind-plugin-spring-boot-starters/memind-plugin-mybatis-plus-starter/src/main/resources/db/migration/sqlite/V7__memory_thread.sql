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

CREATE TABLE IF NOT EXISTS memory_thread (
    id                 INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    biz_id             INTEGER NOT NULL,
    user_id            TEXT    NOT NULL,
    agent_id           TEXT    NOT NULL,
    memory_id          TEXT    NOT NULL,
    thread_key         TEXT    NOT NULL,
    episode_type       TEXT,
    title              TEXT,
    summary_snapshot   TEXT,
    status             TEXT    NOT NULL,
    confidence         REAL    NOT NULL DEFAULT 0.0,
    start_at           TEXT,
    end_at             TEXT,
    last_activity_at   TEXT,
    origin_item_id     INTEGER,
    anchor_item_id     INTEGER,
    display_order_hint INTEGER NOT NULL DEFAULT 0,
    metadata           TEXT,
    created_at         TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at         TEXT    NOT NULL DEFAULT (datetime('now')),
    deleted            INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_memory_thread_biz_id
    ON memory_thread(user_id, agent_id, biz_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_memory_thread_key
    ON memory_thread(memory_id, thread_key);
CREATE INDEX IF NOT EXISTS idx_memory_thread_status
    ON memory_thread(memory_id, status, last_activity_at);
CREATE INDEX IF NOT EXISTS idx_memory_thread_anchor_item
    ON memory_thread(memory_id, anchor_item_id);

CREATE TABLE IF NOT EXISTS memory_thread_item (
    id                INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    biz_id            INTEGER NOT NULL,
    user_id           TEXT    NOT NULL,
    agent_id          TEXT    NOT NULL,
    memory_id         TEXT    NOT NULL,
    thread_id         INTEGER NOT NULL,
    item_id           INTEGER NOT NULL,
    membership_weight REAL    NOT NULL,
    role              TEXT    NOT NULL,
    sequence_hint     INTEGER NOT NULL,
    joined_at         TEXT,
    metadata          TEXT,
    created_at        TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at        TEXT    NOT NULL DEFAULT (datetime('now')),
    deleted           INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_memory_thread_item_biz_id
    ON memory_thread_item(user_id, agent_id, biz_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_memory_thread_item_item_id
    ON memory_thread_item(memory_id, item_id);
CREATE INDEX IF NOT EXISTS idx_memory_thread_item_thread_sequence
    ON memory_thread_item(memory_id, thread_id, sequence_hint);
CREATE INDEX IF NOT EXISTS idx_memory_thread_item_item
    ON memory_thread_item(memory_id, item_id);
