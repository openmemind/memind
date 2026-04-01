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
package com.openmemind.ai.memory.server.service.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import org.junit.jupiter.api.Test;

class MemoryOptionsCodecTest {

    private final MemoryOptionsCodec codec = new MemoryOptionsCodec(new ObjectMapper());

    @Test
    void roundTripsMemoryBuildOptions() {
        var options = MemoryBuildOptions.builder().build();

        String json = codec.write(options);

        assertThat(codec.read(json)).isEqualTo(options);
    }
}
