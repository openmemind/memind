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
    id                 BIGSERIAL    PRIMARY KEY,
    biz_id             BIGINT       NOT NULL,
    user_id            VARCHAR(64)  NOT NULL,
    agent_id           VARCHAR(64)  NOT NULL,
    memory_id          VARCHAR(200) NOT NULL,
    thread_key         VARCHAR(255) NOT NULL,
    episode_type       VARCHAR(64),
    title              VARCHAR(255),
    summary_snapshot   TEXT,
    status             VARCHAR(32)  NOT NULL,
    confidence         DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    start_at           TIMESTAMPTZ,
    end_at             TIMESTAMPTZ,
    last_activity_at   TIMESTAMPTZ,
    origin_item_id     BIGINT,
    anchor_item_id     BIGINT,
    display_order_hint INTEGER      NOT NULL DEFAULT 0,
    metadata           JSONB,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            BOOLEAN      NOT NULL DEFAULT FALSE
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
    id                BIGSERIAL    PRIMARY KEY,
    biz_id            BIGINT       NOT NULL,
    user_id           VARCHAR(64)  NOT NULL,
    agent_id          VARCHAR(64)  NOT NULL,
    memory_id         VARCHAR(200) NOT NULL,
    thread_id         BIGINT       NOT NULL,
    item_id           BIGINT       NOT NULL,
    membership_weight DOUBLE PRECISION NOT NULL,
    role              VARCHAR(32)  NOT NULL,
    sequence_hint     INTEGER      NOT NULL,
    joined_at         TIMESTAMPTZ,
    metadata          JSONB,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           BOOLEAN      NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_memory_thread_item_biz_id
    ON memory_thread_item(user_id, agent_id, biz_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_memory_thread_item_item_id
    ON memory_thread_item(memory_id, item_id);
CREATE INDEX IF NOT EXISTS idx_memory_thread_item_thread_sequence
    ON memory_thread_item(memory_id, thread_id, sequence_hint);
CREATE INDEX IF NOT EXISTS idx_memory_thread_item_item
    ON memory_thread_item(memory_id, item_id);
