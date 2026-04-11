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
package com.openmemind.ai.memory.plugin.content.parser.document.tika;

import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.resource.ContentParser;
import com.openmemind.ai.memory.core.resource.SourceDescriptor;
import java.util.Set;
import reactor.core.publisher.Mono;

@Deprecated(forRemoval = false)
public final class TikaDocumentContentParser implements ContentParser {

    private final com.openmemind.ai.memory.plugin.rawdata.document.parser.tika
                    .TikaDocumentContentParser
            delegate =
                    new com.openmemind.ai.memory.plugin.rawdata.document.parser.tika
                            .TikaDocumentContentParser();

    @Override
    public String parserId() {
        return delegate.parserId();
    }

    @Override
    public String contentType() {
        return delegate.contentType();
    }

    @Override
    public String contentProfile() {
        return delegate.contentProfile();
    }

    @Override
    public int priority() {
        return delegate.priority();
    }

    @Override
    public Set<String> supportedMimeTypes() {
        return delegate.supportedMimeTypes();
    }

    @Override
    public Set<String> supportedExtensions() {
        return delegate.supportedExtensions();
    }

    @Override
    public boolean supports(SourceDescriptor source) {
        return delegate.supports(source);
    }

    @Override
    public Mono<RawContent> parse(byte[] data, SourceDescriptor source) {
        return delegate.parse(data, source);
    }
}
