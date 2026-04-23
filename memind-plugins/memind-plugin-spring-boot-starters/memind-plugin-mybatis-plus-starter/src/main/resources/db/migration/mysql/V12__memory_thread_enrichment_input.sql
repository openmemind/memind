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
    id                                   BIGINT       NOT NULL AUTO_INCREMENT,
    memory_id                            VARCHAR(200) NOT NULL,
    thread_key                           VARCHAR(255) NOT NULL,
    input_run_key                        VARCHAR(255) NOT NULL,
    entry_seq                            INT          NOT NULL,
    basis_cutoff_item_id                 BIGINT       NOT NULL,
    basis_meaningful_event_count         BIGINT       NOT NULL,
    basis_materialization_policy_version VARCHAR(64)  NOT NULL,
    payload_json                         JSON         NOT NULL,
    provenance_json                      JSON         NOT NULL,
    created_at                           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_memory_thread_enrichment_input_run_entry (memory_id, input_run_key, entry_seq),
    KEY idx_memory_thread_enrichment_input_replay (
        memory_id,
        basis_materialization_policy_version,
        basis_cutoff_item_id,
        basis_meaningful_event_count,
        input_run_key,
        entry_seq
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
