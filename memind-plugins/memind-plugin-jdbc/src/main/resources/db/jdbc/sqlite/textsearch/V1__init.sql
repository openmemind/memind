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

CREATE TRIGGER IF NOT EXISTS item_fts_ai AFTER INSERT ON memory_item BEGIN INSERT INTO item_fts(rowid, biz_id, memory_id, content) VALUES (new.id, new.biz_id, new.memory_id, new.content); END;

CREATE TRIGGER IF NOT EXISTS item_fts_au AFTER UPDATE ON memory_item WHEN new.deleted = 0 BEGIN DELETE FROM item_fts WHERE rowid = old.id; INSERT INTO item_fts(rowid, biz_id, memory_id, content) VALUES (new.id, new.biz_id, new.memory_id, new.content); END;

CREATE TRIGGER IF NOT EXISTS item_fts_ad AFTER UPDATE ON memory_item WHEN new.deleted = 1 AND old.deleted = 0 BEGIN DELETE FROM item_fts WHERE rowid = old.id; END;

CREATE TRIGGER IF NOT EXISTS insight_fts_ai AFTER INSERT ON memory_insight BEGIN INSERT INTO insight_fts(rowid, biz_id, memory_id, content) VALUES (new.id, new.biz_id, new.memory_id, new.content); END;

CREATE TRIGGER IF NOT EXISTS insight_fts_au AFTER UPDATE ON memory_insight WHEN new.deleted = 0 BEGIN DELETE FROM insight_fts WHERE rowid = old.id; INSERT INTO insight_fts(rowid, biz_id, memory_id, content) VALUES (new.id, new.biz_id, new.memory_id, new.content); END;

CREATE TRIGGER IF NOT EXISTS insight_fts_ad AFTER UPDATE ON memory_insight WHEN new.deleted = 1 AND old.deleted = 0 BEGIN DELETE FROM insight_fts WHERE rowid = old.id; END;

CREATE TRIGGER IF NOT EXISTS raw_data_fts_ai AFTER INSERT ON memory_raw_data BEGIN INSERT INTO raw_data_fts(rowid, biz_id, memory_id, content) VALUES (new.id, new.biz_id, new.memory_id, json_extract(new.segment, '$.content')); END;

CREATE TRIGGER IF NOT EXISTS raw_data_fts_au AFTER UPDATE ON memory_raw_data WHEN new.deleted = 0 BEGIN DELETE FROM raw_data_fts WHERE rowid = old.id; INSERT INTO raw_data_fts(rowid, biz_id, memory_id, content) VALUES (new.id, new.biz_id, new.memory_id, json_extract(new.segment, '$.content')); END;

CREATE TRIGGER IF NOT EXISTS raw_data_fts_ad AFTER UPDATE ON memory_raw_data WHEN new.deleted = 1 AND old.deleted = 0 BEGIN DELETE FROM raw_data_fts WHERE rowid = old.id; END;

INSERT OR REPLACE INTO item_fts(rowid, biz_id, memory_id, content)
SELECT id, biz_id, memory_id, content
FROM memory_item
WHERE deleted = 0;

INSERT OR REPLACE INTO insight_fts(rowid, biz_id, memory_id, content)
SELECT id, biz_id, memory_id, content
FROM memory_insight
WHERE deleted = 0;

INSERT OR REPLACE INTO raw_data_fts(rowid, biz_id, memory_id, content)
SELECT id, biz_id, memory_id, json_extract(segment, '$.content')
FROM memory_raw_data
WHERE deleted = 0
  AND json_extract(segment, '$.content') IS NOT NULL;
