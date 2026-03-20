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
    id          INT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    biz_id      VARCHAR(64)  NOT NULL COMMENT '业务 ID（UUID）',
    user_id     VARCHAR(64)  NOT NULL COMMENT '用户 ID',
    agent_id    VARCHAR(64)  NOT NULL COMMENT '智能体 ID',
    memory_id   VARCHAR(200) NOT NULL COMMENT 'memoryId 标识符',
    type        VARCHAR(32)  NOT NULL COMMENT '数据类型',
    content_id  VARCHAR(200)          COMMENT '原始内容 ID',
    segment     JSON                  COMMENT '分段内容（JSON）',
    segment_content TEXT GENERATED ALWAYS AS (segment ->> '$.content') STORED COMMENT '分段文本（生成列）',
    caption     TEXT                  COMMENT '一句话摘要',
    caption_vector_id VARCHAR(200)    COMMENT 'Caption 向量库 ID',
    metadata    JSON                  COMMENT '附加元数据',
    start_time  DATETIME(3) DEFAULT NULL COMMENT '该分段第一条消息的时间戳',
    end_time    DATETIME(3) DEFAULT NULL COMMENT '该分段最后一条消息的时间戳',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0=正常，1=已删除）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_memory_biz_id (user_id, agent_id, biz_id),
    KEY idx_memory_content_id (user_id, agent_id, content_id),
    KEY idx_memory_id (user_id, agent_id),
    FULLTEXT KEY idx_segment_content_ngram (segment_content) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='记忆原始数据';

-- ===== memory_item =====
CREATE TABLE IF NOT EXISTS memory_item (
    id               INT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    biz_id           BIGINT       NOT NULL COMMENT '业务 ID',
    user_id          VARCHAR(64)  NOT NULL COMMENT '用户 ID',
    agent_id         VARCHAR(64)  NOT NULL COMMENT '智能体 ID',
    memory_id        VARCHAR(200) NOT NULL COMMENT 'memoryId 标识符',
    content          TEXT         NOT NULL COMMENT '记忆内容文本',
    scope            VARCHAR(16)  NOT NULL COMMENT '归属范围',
    vector_id        VARCHAR(200)          COMMENT '向量 ID',
    raw_data_id      VARCHAR(64)           COMMENT '源数据业务 ID',
    content_hash     VARCHAR(128)          COMMENT '内容哈希',
    occurred_at      DATETIME(3)  NOT NULL COMMENT '记忆发生时间',
    type             VARCHAR(16)  NOT NULL DEFAULT 'FACT' COMMENT '条目类型（FACT/FORESIGHT）',
    raw_data_type    VARCHAR(32)  NOT NULL DEFAULT 'CONVERSATION' COMMENT '来源场景类型（CONVERSATION/TOOL_CALL/TRAJECTORY）',
    metadata         JSON                  COMMENT '附加元数据',
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_memory_biz_id (user_id, agent_id, biz_id),
    UNIQUE KEY uk_memory_content_hash (user_id, agent_id, content_hash),
    KEY idx_memory_id (user_id, agent_id),
    KEY idx_raw_data_id (user_id, agent_id, raw_data_id),
    KEY idx_memory_item_type (user_id, agent_id, type),
    FULLTEXT KEY idx_content_ngram (content) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='记忆条目';

-- ===== memory_insight_type =====
CREATE TABLE IF NOT EXISTS memory_insight_type (
    id                      INT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    biz_id                  BIGINT                COMMENT '业务 ID',
    name                    VARCHAR(128) NOT NULL COMMENT '类型名称',
    description             TEXT                  COMMENT '描述',
    description_vector_id   VARCHAR(200)          COMMENT '描述向量库 ID',
    categories              JSON                  COMMENT '分类列表（冗余）',
    target_tokens           INT          NOT NULL DEFAULT 0 COMMENT 'summary token 预算',
    summary_prompt          JSON                  COMMENT '自定义 summary prompt sections',
    analysis_mode           VARCHAR(32)  NULL DEFAULT 'BRANCH' COMMENT '分析模式（ROOT/BRANCH）',
    scope                   VARCHAR(32)  NULL     COMMENT 'Scope: USER / AGENT / null for ROOT types',
    tree_config             JSON         NULL     COMMENT 'InsightTreeConfig 配置',
    last_updated_at         DATETIME(3)           COMMENT '上次更新时间',
    created_at              DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at              DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted                 TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='洞察类型';

-- ===== memory_insight =====
CREATE TABLE IF NOT EXISTS memory_insight (
    id                 INT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    biz_id             BIGINT       NOT NULL COMMENT '业务 ID',
    user_id            VARCHAR(64)  NOT NULL COMMENT '用户 ID',
    agent_id           VARCHAR(64)  NOT NULL COMMENT '智能体 ID',
    memory_id          VARCHAR(200) NOT NULL COMMENT 'memoryId 标识符',
    type               VARCHAR(128) NOT NULL COMMENT '洞察类型',
    scope              VARCHAR(16)  NOT NULL COMMENT '归属范围',
    name               VARCHAR(256)          COMMENT '短名称/标题',
    categories         JSON                  COMMENT '分类列表（冗余）',
    content            MEDIUMTEXT            COMMENT 'pointsContent() 拼接文本',
    points             JSON         NULL     COMMENT '结构化点位列表（InsightPoint JSON）',
    group_name          VARCHAR(64)  NULL     COMMENT 'LLM 分组名称',
    confidence         FLOAT        NULL DEFAULT 0 COMMENT '综合置信度',
    last_reasoned_at   DATETIME(3)  NULL     COMMENT '上次推理时间',
    summary_embedding  JSON         NULL     COMMENT 'content 的 embedding 向量',
    tier               VARCHAR(16)  NULL     COMMENT 'Insight 树层级 (LEAF/BRANCH/ROOT)',
    parent_insight_id  BIGINT       NULL     COMMENT '父 Insight ID',
    child_insight_ids  JSON         NULL     COMMENT '子 Insight ID 列表',
    version            INT          NOT NULL DEFAULT 1 COMMENT '版本号',
    created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted            TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_memory_biz_id (user_id, agent_id, biz_id),
    KEY idx_memory_id (user_id, agent_id),
    KEY idx_type (user_id, agent_id, type),
    KEY idx_parent_insight (user_id, agent_id, parent_insight_id),
    KEY idx_group_name (memory_id, type, group_name),
    KEY idx_insight_tier (user_id, agent_id, tier),
    KEY idx_insight_tier_group (user_id, agent_id, type, tier, group_name),
    FULLTEXT KEY idx_content_ngram (content) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='洞察';

-- 默认 taxonomy（memory_category / memory_insight_type）
-- 由 DefaultTaxonomySeeder 在应用启动阶段按 core 默认定义写入，
-- 避免 SQL 静态副本与 DefaultMemoryCategories/DefaultInsightTypes 漂移。

-- ===== memory_conversation_buffer =====
CREATE TABLE IF NOT EXISTS memory_conversation_buffer (
    id          INT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    session_id  VARCHAR(128) NOT NULL COMMENT '会话标识',
    user_id     VARCHAR(64)  NOT NULL COMMENT '用户 ID',
    agent_id    VARCHAR(64)  NOT NULL COMMENT '智能体 ID',
    memory_id   VARCHAR(200) NOT NULL COMMENT 'memoryId 标识符',
    role        VARCHAR(16)  NOT NULL COMMENT '消息角色（USER/ASSISTANT）',
    content     TEXT         NOT NULL COMMENT '消息内容',
    user_name   VARCHAR(64)  NULL     COMMENT '发言人名称',
    timestamp   DATETIME(3)  NULL     COMMENT '消息时间戳',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0=正常，1=已删除）',
    PRIMARY KEY (id),
    KEY idx_session_id (session_id),
    KEY idx_memory_id (user_id, agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流式会话缓冲区';

-- ===== memory_insight_buffer =====
CREATE TABLE IF NOT EXISTS memory_insight_buffer (
    id                INT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id           VARCHAR(64)  NOT NULL COMMENT '用户 ID',
    agent_id          VARCHAR(64)  NOT NULL COMMENT '智能体 ID',
    memory_id         VARCHAR(200) NOT NULL COMMENT 'memoryId 标识符',
    insight_type_name VARCHAR(128) NOT NULL COMMENT 'InsightType 名称',
    item_id           BIGINT       NOT NULL COMMENT '缓冲的 item biz_id',
    group_name        VARCHAR(64)  NULL     COMMENT 'LLM 分组名称',
    built             TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已构建（0=未构建，1=已构建）',
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_buffer_entry (user_id, agent_id, insight_type_name, item_id),
    KEY idx_memory_type (user_id, agent_id, insight_type_name),
    KEY idx_group (user_id, agent_id, insight_type_name, group_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='洞察构建缓冲区';
