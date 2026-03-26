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

ALTER TABLE memory_raw_data
    ADD COLUMN IF NOT EXISTS segment_content TEXT GENERATED ALWAYS AS
        (JSON_UNQUOTE(JSON_EXTRACT(segment, '$.content'))) STORED;

DROP PROCEDURE IF EXISTS init_memind_mysql_textsearch;

CREATE PROCEDURE init_memind_mysql_textsearch()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM INFORMATION_SCHEMA.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'memory_item'
          AND INDEX_NAME = 'ft_item_content'
    ) THEN
        SET @create_item_index = 'CREATE FULLTEXT INDEX ft_item_content ON memory_item(content) WITH PARSER ngram';
        PREPARE stmt FROM @create_item_index;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM INFORMATION_SCHEMA.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'memory_insight'
          AND INDEX_NAME = 'ft_insight_content'
    ) THEN
        SET @create_insight_index = 'CREATE FULLTEXT INDEX ft_insight_content ON memory_insight(content) WITH PARSER ngram';
        PREPARE stmt FROM @create_insight_index;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM INFORMATION_SCHEMA.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'memory_raw_data'
          AND INDEX_NAME = 'ft_raw_data_segment_content'
    ) THEN
        SET @create_raw_data_index = 'CREATE FULLTEXT INDEX ft_raw_data_segment_content ON memory_raw_data(segment_content) WITH PARSER ngram';
        PREPARE stmt FROM @create_raw_data_index;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END;

CALL init_memind_mysql_textsearch();

DROP PROCEDURE IF EXISTS init_memind_mysql_textsearch;
