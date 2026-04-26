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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Reads versioned thread semantics from item metadata with best-effort tolerance.
 */
public final class ThreadSemanticMarkerReader {

    private static final String THREAD_SEMANTICS_KEY = "threadSemantics";

    private ThreadSemanticMarkerReader() {}

    public static ThreadSemanticMarker.SemanticsEnvelope read(Map<String, Object> metadata) {
        if (metadata == null) {
            return ThreadSemanticMarker.SemanticsEnvelope.empty();
        }
        Object semanticsValue = metadata.get(THREAD_SEMANTICS_KEY);
        if (!(semanticsValue instanceof Map<?, ?> rawSemantics)) {
            return ThreadSemanticMarker.SemanticsEnvelope.empty();
        }

        int version = number(rawSemantics.get("version")).map(Number::intValue).orElse(0);
        List<ThreadSemanticMarker> markers = readMarkers(rawSemantics.get("markers"));
        List<ThreadSemanticMarker.CanonicalRef> canonicalRefs =
                readCanonicalRefs(rawSemantics.get("canonicalRefs"));
        List<ThreadSemanticMarker.ContinuityLink> continuityLinks =
                readContinuityLinks(rawSemantics.get("continuityLinks"));
        return new ThreadSemanticMarker.SemanticsEnvelope(
                version, markers, canonicalRefs, continuityLinks);
    }

    private static List<ThreadSemanticMarker> readMarkers(Object rawMarkers) {
        if (!(rawMarkers instanceof List<?> markerList)) {
            return List.of();
        }
        return markerList.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(ThreadSemanticMarkerReader::toMarker)
                .flatMap(Optional::stream)
                .toList();
    }

    private static List<ThreadSemanticMarker.CanonicalRef> readCanonicalRefs(Object rawRefs) {
        if (!(rawRefs instanceof List<?> refList)) {
            return List.of();
        }
        return refList.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(ThreadSemanticMarkerReader::toCanonicalRef)
                .flatMap(Optional::stream)
                .toList();
    }

    private static List<ThreadSemanticMarker.ContinuityLink> readContinuityLinks(Object rawLinks) {
        if (!(rawLinks instanceof List<?> linkList)) {
            return List.of();
        }
        return linkList.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(ThreadSemanticMarkerReader::toContinuityLink)
                .flatMap(Optional::stream)
                .toList();
    }

    private static Optional<ThreadSemanticMarker> toMarker(Map<?, ?> rawMarker) {
        Optional<MemoryThreadEventType> eventType = eventType(rawMarker.get("type"));
        if (eventType.isEmpty()) {
            return Optional.empty();
        }
        var attributes = new LinkedHashMap<String, Object>();
        rawMarker.forEach(
                (key, value) -> {
                    if (key instanceof String name
                            && !"type".equals(name)
                            && !"objectRef".equals(name)
                            && !"summary".equals(name)) {
                        attributes.put(name, value);
                    }
                });
        return Optional.of(
                new ThreadSemanticMarker(
                        eventType.get(),
                        stringValue(rawMarker.get("objectRef")),
                        stringValue(rawMarker.get("summary")),
                        attributes));
    }

    private static Optional<ThreadSemanticMarker.CanonicalRef> toCanonicalRef(Map<?, ?> rawRef) {
        String refType = stringValue(rawRef.get("refType"));
        String refKey = stringValue(rawRef.get("refKey"));
        if (isBlank(refType) || isBlank(refKey)) {
            return Optional.empty();
        }
        return Optional.of(new ThreadSemanticMarker.CanonicalRef(refType, refKey));
    }

    private static Optional<ThreadSemanticMarker.ContinuityLink> toContinuityLink(
            Map<?, ?> rawLink) {
        String linkType = stringValue(rawLink.get("linkType"));
        if (isBlank(linkType)) {
            return Optional.empty();
        }
        Long targetItemId = number(rawLink.get("targetItemId")).map(Number::longValue).orElse(null);
        return Optional.of(new ThreadSemanticMarker.ContinuityLink(linkType, targetItemId));
    }

    private static Optional<MemoryThreadEventType> eventType(Object rawType) {
        String value = stringValue(rawType);
        if (isBlank(value)) {
            return Optional.empty();
        }
        try {
            return Optional.of(MemoryThreadEventType.valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Number> number(Object value) {
        if (value instanceof Number number) {
            return Optional.of(number);
        }
        return Optional.empty();
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
