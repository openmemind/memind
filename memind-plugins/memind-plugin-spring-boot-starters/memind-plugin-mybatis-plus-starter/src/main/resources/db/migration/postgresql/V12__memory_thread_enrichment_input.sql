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

CREATE TABLE IF NOT EXISTS memory_thread_enrichment_input (
    id                                   BIGSERIAL    PRIMARY KEY,
    memory_id                            VARCHAR(200) NOT NULL,
    thread_key                           VARCHAR(255) NOT NULL,
    input_run_key                        VARCHAR(255) NOT NULL,
    entry_seq                            INTEGER      NOT NULL,
    basis_cutoff_item_id                 BIGINT       NOT NULL,
    basis_meaningful_event_count         BIGINT       NOT NULL,
    basis_materialization_policy_version VARCHAR(64)  NOT NULL,
    payload_json                         JSONB        NOT NULL,
    provenance_json                      JSONB        NOT NULL,
    created_at                           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_memory_thread_enrichment_input_run_entry
    ON memory_thread_enrichment_input(memory_id, input_run_key, entry_seq);

CREATE INDEX IF NOT EXISTS idx_memory_thread_enrichment_input_replay
    ON memory_thread_enrichment_input(
        memory_id,
        basis_materialization_policy_version,
        basis_cutoff_item_id,
        basis_meaningful_event_count,
        input_run_key,
        entry_seq
    );
