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
package com.openmemind.ai.memory.core.extraction.thread.marker;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.enums.MemoryThreadEventType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ThreadSemanticMarkerReader")
class ThreadSemanticMarkerReaderTest {

    @Test
    @DisplayName("read should parse versioned thread semantics metadata")
    void readShouldParseVersionedThreadSemanticsMetadata() {
        var metadata =
                Map.<String, Object>of(
                        "threadSemantics",
                        Map.of(
                                "version",
                                1,
                                "markers",
                                List.of(
                                        Map.of(
                                                "type",
                                                "STATE_CHANGE",
                                                "objectRef",
                                                "project:memind-v1",
                                                "fromState",
                                                "planning",
                                                "toState",
                                                "implementation",
                                                "summary",
                                                "Memind v1 moved into implementation")),
                                "canonicalRefs",
                                List.of(Map.of("refType", "project", "refKey", "memind-v1")),
                                "continuityLinks",
                                List.of(Map.of("linkType", "CONTINUES", "targetItemId", 301L))));

        var semantics = ThreadSemanticMarkerReader.read(metadata);

        assertThat(semantics.version()).isEqualTo(1);
        assertThat(semantics.markers())
                .singleElement()
                .satisfies(
                        marker -> {
                            assertThat(marker.eventType())
                                    .isEqualTo(MemoryThreadEventType.STATE_CHANGE);
                            assertThat(marker.objectRef()).isEqualTo("project:memind-v1");
                            assertThat(marker.summary())
                                    .isEqualTo("Memind v1 moved into implementation");
                            assertThat(marker.attributes())
                                    .containsEntry("fromState", "planning")
                                    .containsEntry("toState", "implementation");
                        });
        assertThat(semantics.canonicalRefs())
                .singleElement()
                .extracting(
                        ThreadSemanticMarker.CanonicalRef::refType,
                        ThreadSemanticMarker.CanonicalRef::refKey)
                .containsExactly("project", "memind-v1");
        assertThat(semantics.continuityLinks())
                .singleElement()
                .extracting(
                        ThreadSemanticMarker.ContinuityLink::linkType,
                        ThreadSemanticMarker.ContinuityLink::targetItemId)
                .containsExactly("CONTINUES", 301L);
    }

    @Test
    @DisplayName("read should tolerate missing or malformed thread semantics")
    void readShouldTolerateMissingOrMalformedThreadSemantics() {
        assertThat(ThreadSemanticMarkerReader.read(null).markers()).isEmpty();
        assertThat(ThreadSemanticMarkerReader.read(Map.of()).markers()).isEmpty();
        assertThat(
                        ThreadSemanticMarkerReader.read(
                                        Map.of("threadSemantics", List.of("legacy-shape")))
                                .markers())
                .isEmpty();
    }
}
