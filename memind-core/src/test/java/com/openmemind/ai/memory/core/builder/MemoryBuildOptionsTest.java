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
package com.openmemind.ai.memory.core.builder;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.context.CommitDetectorConfig;
import com.openmemind.ai.memory.core.extraction.insight.scheduler.InsightBuildConfig;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.ConversationChunkingConfig;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MemoryBuildOptionsTest {

    @Test
    void defaultsExposeExtractionAndRetrievalSubtrees() {
        var options = MemoryBuildOptions.defaults();

        assertThat(options.extraction().common().defaultScope()).isEqualTo(MemoryScope.USER);
        assertThat(options.extraction().rawdata().chunking())
                .isEqualTo(ConversationChunkingConfig.DEFAULT);
        assertThat(options.extraction().rawdata().commitDetection())
                .isEqualTo(CommitDetectorConfig.defaults());
        assertThat(options.extraction().insight().build())
                .isEqualTo(new InsightBuildConfig(3, 2, 8, 2));
        assertThat(options.retrieval().simple().timeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(options.retrieval().deep().timeout()).isEqualTo(Duration.ofSeconds(120));
        assertThat(options.retrieval().simple().keywordSearchEnabled()).isTrue();
        assertThat(options.retrieval().advanced().rerank().mode()).isEqualTo(RerankMode.PURE);
    }
}
