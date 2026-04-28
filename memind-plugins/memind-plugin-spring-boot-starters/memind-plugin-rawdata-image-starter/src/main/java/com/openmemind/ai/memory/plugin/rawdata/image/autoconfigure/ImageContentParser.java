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
package com.openmemind.ai.memory.plugin.rawdata.image.autoconfigure;

import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.resource.ContentParser;
import com.openmemind.ai.memory.core.resource.SourceDescriptor;
import com.openmemind.ai.memory.plugin.rawdata.image.parser.VisionImageContentParser;
import java.util.Set;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Mono;

final class ImageContentParser implements ContentParser {

    private final ObjectProvider<ChatModel> chatModelProvider;

    ImageContentParser(ObjectProvider<ChatModel> chatModelProvider) {
        this.chatModelProvider = chatModelProvider;
    }

    @Override
    public String parserId() {
        return "image-vision";
    }

    @Override
    public String contentType() {
        return delegateMetadata().contentType();
    }

    @Override
    public String contentProfile() {
        return delegateMetadata().contentProfile();
    }

    @Override
    public String governanceType() {
        return delegateMetadata().governanceType();
    }

    @Override
    public int priority() {
        return delegateMetadata().priority();
    }

    @Override
    public Set<String> supportedMimeTypes() {
        return delegateMetadata().supportedMimeTypes();
    }

    @Override
    public Set<String> supportedExtensions() {
        return delegateMetadata().supportedExtensions();
    }

    @Override
    public boolean supports(SourceDescriptor source) {
        return delegateMetadata().supports(source);
    }

    @Override
    public Mono<RawContent> parse(byte[] data, SourceDescriptor source) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            return Mono.error(new IllegalStateException("No ChatModel bean is available"));
        }
        return new VisionImageContentParser(chatModel).parse(data, source);
    }

    private static VisionImageContentParser delegateMetadata() {
        return MetadataHolder.DELEGATE;
    }

    private static final class MetadataHolder {
        private static final VisionImageContentParser DELEGATE =
                new VisionImageContentParser(
                        prompt -> {
                            throw new IllegalStateException(
                                    "Metadata-only chat model must not be used");
                        });
    }
}
