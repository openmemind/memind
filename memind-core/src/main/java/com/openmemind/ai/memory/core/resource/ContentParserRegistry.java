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
package com.openmemind.ai.memory.core.resource;

import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import reactor.core.publisher.Mono;

/**
 * Registry that routes file payloads to an appropriate {@link ContentParser}.
 */
public interface ContentParserRegistry {

    Mono<ParserResolution> resolve(SourceDescriptor source);

    Mono<RawContent> parse(byte[] data, SourceDescriptor source);

    List<ContentCapability> capabilities();

    default Mono<RawContent> parse(byte[] data, String fileName, String mimeType) {
        return parse(
                data,
                new SourceDescriptor(
                        SourceKind.FILE,
                        fileName,
                        mimeType,
                        data == null ? null : (long) data.length,
                        null));
    }

    default Map<String, Set<String>> supportedMimeTypesByContentType() {
        Map<String, Set<String>> grouped = new LinkedHashMap<>();
        for (ContentCapability capability : capabilities()) {
            grouped.computeIfAbsent(capability.contentType(), ignored -> new LinkedHashSet<>())
                    .addAll(capability.supportedMimeTypes());
        }

        Map<String, Set<String>> immutable = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : grouped.entrySet()) {
            immutable.put(
                    entry.getKey(),
                    Collections.unmodifiableSet(new LinkedHashSet<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(immutable);
    }
}
