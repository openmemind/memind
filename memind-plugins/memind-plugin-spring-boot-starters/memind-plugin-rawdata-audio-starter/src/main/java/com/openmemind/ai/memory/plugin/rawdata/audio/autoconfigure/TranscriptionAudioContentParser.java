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
package com.openmemind.ai.memory.plugin.rawdata.audio.autoconfigure;

import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.resource.ContentParser;
import com.openmemind.ai.memory.core.resource.SourceDescriptor;
import java.util.Set;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Mono;

final class TranscriptionAudioContentParser implements ContentParser {

    private final ObjectProvider<TranscriptionModel> transcriptionModelProvider;

    TranscriptionAudioContentParser(ObjectProvider<TranscriptionModel> transcriptionModelProvider) {
        this.transcriptionModelProvider = transcriptionModelProvider;
    }

    @Override
    public String parserId() {
        return "audio-transcription";
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
        TranscriptionModel transcriptionModel = transcriptionModelProvider.getIfAvailable();
        if (transcriptionModel == null) {
            return Mono.error(new IllegalStateException("No TranscriptionModel bean is available"));
        }
        return new com.openmemind.ai.memory.plugin.rawdata.audio.parser
                        .TranscriptionAudioContentParser(transcriptionModel)
                .parse(data, source);
    }

    private static com.openmemind.ai.memory.plugin.rawdata.audio.parser
                    .TranscriptionAudioContentParser
            delegateMetadata() {
        return MetadataHolder.DELEGATE;
    }

    private static final class MetadataHolder {
        private static final com.openmemind.ai.memory.plugin.rawdata.audio.parser
                        .TranscriptionAudioContentParser
                DELEGATE =
                        new com.openmemind.ai.memory.plugin.rawdata.audio.parser
                                .TranscriptionAudioContentParser(
                                prompt -> {
                                    throw new IllegalStateException(
                                            "Metadata-only transcription model must not be used");
                                });
    }
}
