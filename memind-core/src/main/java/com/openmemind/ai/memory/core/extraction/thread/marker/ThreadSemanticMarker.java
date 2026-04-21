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

import com.openmemind.ai.memory.core.data.enums.MemoryThreadEventType;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Versioned semantic marker payload parsed from {@code MemoryItem.metadata["threadSemantics"]}.
 */
public record ThreadSemanticMarker(
        MemoryThreadEventType eventType,
        String objectRef,
        String summary,
        Map<String, Object> attributes) {

    public ThreadSemanticMarker {
        eventType = Objects.requireNonNull(eventType, "eventType");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public record CanonicalRef(String refType, String refKey) {

        public CanonicalRef {
            refType = Objects.requireNonNull(refType, "refType");
            refKey = Objects.requireNonNull(refKey, "refKey");
        }
    }

    public record ContinuityLink(String linkType, Long targetItemId) {

        public ContinuityLink {
            linkType = Objects.requireNonNull(linkType, "linkType");
        }
    }

    public record SemanticsEnvelope(
            int version,
            List<ThreadSemanticMarker> markers,
            List<CanonicalRef> canonicalRefs,
            List<ContinuityLink> continuityLinks) {

        public SemanticsEnvelope {
            version = Math.max(version, 0);
            markers = markers == null ? List.of() : List.copyOf(markers);
            canonicalRefs = canonicalRefs == null ? List.of() : List.copyOf(canonicalRefs);
            continuityLinks = continuityLinks == null ? List.of() : List.copyOf(continuityLinks);
        }

        public static SemanticsEnvelope empty() {
            return new SemanticsEnvelope(0, List.of(), List.of(), List.of());
        }
    }
}
