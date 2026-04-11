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

import com.openmemind.ai.memory.core.data.ContentTypes;
import java.util.List;
import java.util.Set;

/**
 * Runtime snapshot of direct and parser-backed ingestion capabilities.
 */
public record IngestionCapabilitySnapshot(
        Set<String> directContentTypes, List<ContentCapability> parserBackedCapabilities) {

    public IngestionCapabilitySnapshot {
        directContentTypes = Set.copyOf(directContentTypes);
        parserBackedCapabilities = List.copyOf(parserBackedCapabilities);
    }

    public static IngestionCapabilitySnapshot of(ContentParserRegistry registry) {
        return new IngestionCapabilitySnapshot(
                Set.of(ContentTypes.DOCUMENT, ContentTypes.IMAGE, ContentTypes.AUDIO),
                registry == null ? List.of() : registry.capabilities());
    }
}
