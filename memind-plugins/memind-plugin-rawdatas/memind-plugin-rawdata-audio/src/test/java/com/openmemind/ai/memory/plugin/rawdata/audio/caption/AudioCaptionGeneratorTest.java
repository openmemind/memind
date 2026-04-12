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
package com.openmemind.ai.memory.plugin.rawdata.audio.caption;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AudioCaptionGeneratorTest {

    @Test
    void defaultsToTranscriptPrefixWhenSpeakerMetadataMissing() {
        var generator = new AudioCaptionGenerator();

        assertThat(generator.generate("hello world", Map.of()).block())
                .isEqualTo("Transcript: hello world");
    }

    @Test
    void speakerPrefixWinsWhenSingleSpeakerPresent() {
        var generator = new AudioCaptionGenerator();

        assertThat(generator.generate("hello world", Map.of("speaker", "Alice")).block())
                .isEqualTo("Alice: hello world");
    }

    @Test
    void mergedSpeakersPrefixIsStable() {
        var generator = new AudioCaptionGenerator();

        assertThat(
                        generator
                                .generate(
                                        "hello world", Map.of("speakers", List.of("Alice", "Bob")))
                                .block())
                .isEqualTo("Speakers (Alice, Bob): hello world");
    }
}
