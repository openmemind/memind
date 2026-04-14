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
import java.util.Objects;
import java.util.Set;
import reactor.core.publisher.Mono;

/**
 * Parser implementation that converts raw file bytes into typed {@link RawContent}.
 */
public interface ContentParser {

    String parserId();

    String contentType();

    String contentProfile();

    String governanceType();

    default int priority() {
        return 0;
    }

    Set<String> supportedMimeTypes();

    default Set<String> supportedExtensions() {
        return Set.of();
    }

    default boolean supports(SourceDescriptor source) {
        Objects.requireNonNull(source, "source is required");
        return supports(source.fileName(), source.mimeType());
    }

    default boolean supports(String fileName, String mimeType) {
        return mimeType != null && supportedMimeTypes().contains(mimeType);
    }

    Mono<RawContent> parse(byte[] data, SourceDescriptor source);

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
}
