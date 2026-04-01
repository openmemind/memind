/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.openmemind.ai.memory.benchmark.longmemeval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LongMemEvalDatasetLoaderTest {

    @Test
    void loadParsesQuestionsHaystackSessionsAndTypes() {
        LongMemEvalDataset dataset =
                new LongMemEvalDatasetLoader()
                        .load(Path.of("src/test/resources/fixtures/longmemeval-sample.json"));

        assertThat(dataset.name()).isEqualTo("longmemeval");
        assertThat(dataset.questions()).hasSize(1);
        assertThat(dataset.conversationCount()).isEqualTo(1);
        assertThat(dataset.questionCount()).isEqualTo(1);

        LongMemEvalDataset.LongMemEvalQuestion question = dataset.questions().getFirst();
        assertThat(question.questionId()).isEqualTo("q-1");
        assertThat(question.questionType()).isEqualTo("single-session-user");
        assertThat(question.haystackDates()).containsExactly("2024-05-01", "2024-09-01");
        assertThat(question.haystackSessions()).hasSize(2);
        assertThat(question.haystackSessions().getFirst().getFirst().speaker()).isEqualTo("user");
    }
}
