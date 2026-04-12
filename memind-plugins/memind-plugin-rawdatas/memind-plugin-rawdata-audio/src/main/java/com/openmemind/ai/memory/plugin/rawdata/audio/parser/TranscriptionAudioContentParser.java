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
package com.openmemind.ai.memory.plugin.rawdata.audio.parser;

import com.openmemind.ai.memory.core.data.ContentTypes;
import com.openmemind.ai.memory.core.extraction.BuiltinContentProfiles;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.resource.ContentParser;
import com.openmemind.ai.memory.core.resource.SourceDescriptor;
import com.openmemind.ai.memory.plugin.rawdata.audio.content.AudioContent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.core.io.ByteArrayResource;
import reactor.core.publisher.Mono;

/**
 * Content parser that converts audio payloads into {@link AudioContent}.
 */
public final class TranscriptionAudioContentParser implements ContentParser {

    private static final Set<String> SUPPORTED_MIME_TYPES =
            Set.of("audio/mpeg", "audio/mp4", "audio/wav", "audio/x-wav", "audio/webm");
    private static final Set<String> SUPPORTED_EXTENSIONS =
            Set.of(".mp3", ".m4a", ".mp4", ".wav", ".webm");

    private final TranscriptionModel transcriptionModel;

    public TranscriptionAudioContentParser(TranscriptionModel transcriptionModel) {
        this.transcriptionModel =
                Objects.requireNonNull(transcriptionModel, "transcriptionModel must not be null");
    }

    @Override
    public String parserId() {
        return "audio-transcription";
    }

    @Override
    public String contentType() {
        return ContentTypes.AUDIO;
    }

    @Override
    public String contentProfile() {
        return BuiltinContentProfiles.AUDIO_TRANSCRIPT;
    }

    @Override
    public int priority() {
        return 50;
    }

    @Override
    public Set<String> supportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }

    @Override
    public Set<String> supportedExtensions() {
        return SUPPORTED_EXTENSIONS;
    }

    @Override
    public boolean supports(SourceDescriptor source) {
        if (source.mimeType() != null && supportedMimeTypes().contains(source.mimeType())) {
            return true;
        }
        return hasSupportedExtension(source.fileName());
    }

    @Override
    public Mono<RawContent> parse(byte[] data, SourceDescriptor source) {
        if (data == null || data.length == 0) {
            return Mono.error(new IllegalArgumentException("Audio payload must not be empty"));
        }

        String resolvedMimeType = resolveMimeType(source);
        return Mono.fromCallable(
                        () ->
                                transcriptionModel.call(
                                        new AudioTranscriptionPrompt(
                                                new NamedByteArrayResource(
                                                        data, source.fileName()))))
                .map(
                        response -> {
                            var result = response == null ? null : response.getResult();
                            String transcript = result == null ? null : result.getOutput();
                            if (transcript == null || transcript.isBlank()) {
                                throw new IllegalStateException(
                                        "Audio transcription response must not be blank");
                            }
                            var metadata = new LinkedHashMap<String, Object>();
                            metadata.put("provider", "spring-ai");
                            if (source.mimeType() != null) {
                                metadata.put("sourceMimeType", source.mimeType());
                            }
                            metadata.put("parserId", parserId());
                            metadata.put("contentProfile", contentProfile());
                            metadata.put("parser", "transcription");
                            return new AudioContent(
                                    resolvedMimeType,
                                    transcript,
                                    List.of(),
                                    source.sourceUrl(),
                                    metadata);
                        });
    }

    private static boolean hasSupportedExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        String lowerCase = fileName.toLowerCase(Locale.ROOT);
        return SUPPORTED_EXTENSIONS.stream().anyMatch(lowerCase::endsWith);
    }

    private static String resolveMimeType(SourceDescriptor source) {
        if (source.mimeType() != null && SUPPORTED_MIME_TYPES.contains(source.mimeType())) {
            return source.mimeType();
        }
        String fileName = source.fileName();
        if (fileName == null || fileName.isBlank()) {
            return "audio/mpeg";
        }
        String lowerCase = fileName.toLowerCase(Locale.ROOT);
        if (lowerCase.endsWith(".m4a") || lowerCase.endsWith(".mp4")) {
            return "audio/mp4";
        }
        if (lowerCase.endsWith(".wav")) {
            return "audio/wav";
        }
        if (lowerCase.endsWith(".webm")) {
            return "audio/webm";
        }
        return "audio/mpeg";
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {

        private final String fileName;

        private NamedByteArrayResource(byte[] data, String fileName) {
            super(Objects.requireNonNull(data, "data must not be null"));
            this.fileName = fileName;
        }

        @Override
        public String getFilename() {
            return fileName;
        }
    }
}
