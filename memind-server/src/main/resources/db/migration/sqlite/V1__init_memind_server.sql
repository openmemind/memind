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

CREATE TABLE IF NOT EXISTS memind_server_runtime_config (
    id             INTEGER     NOT NULL PRIMARY KEY AUTOINCREMENT,
    config_key     TEXT        NOT NULL,
    config_version INTEGER     NOT NULL,
    config_json    TEXT        NOT NULL,
    created_at     TEXT        NOT NULL DEFAULT (datetime('now')),
    updated_at     TEXT        NOT NULL DEFAULT (datetime('now')),
    deleted        INTEGER     NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_memind_server_runtime_config_key
    ON memind_server_runtime_config(config_key, deleted);
