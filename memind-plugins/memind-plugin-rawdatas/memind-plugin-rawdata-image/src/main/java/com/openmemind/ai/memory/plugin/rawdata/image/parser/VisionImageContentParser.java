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
package com.openmemind.ai.memory.plugin.rawdata.image.parser;

import com.openmemind.ai.memory.core.data.ContentTypes;
import com.openmemind.ai.memory.core.extraction.BuiltinContentProfiles;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.resource.ContentParser;
import com.openmemind.ai.memory.core.resource.SourceDescriptor;
import com.openmemind.ai.memory.core.utils.JsonUtils;
import com.openmemind.ai.memory.plugin.rawdata.image.content.ImageContent;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeType;
import reactor.core.publisher.Mono;

/**
 * Content parser that converts image payloads into {@link ImageContent}.
 */
public final class VisionImageContentParser implements ContentParser {

    private static final String RESPONSE_INSTRUCTION = "Return JSON only.";
    private static final String DEFAULT_INSTRUCTION =
            "Describe the image faithfully and extract any visible text. "
                    + "Return concise semantic description plus OCR text only.";
    private static final Set<String> SUPPORTED_MIME_TYPES =
            Set.of("image/png", "image/jpeg", "image/webp", "image/gif");
    private static final Set<String> SUPPORTED_EXTENSIONS =
            Set.of(".png", ".jpg", ".jpeg", ".webp", ".gif");

    private final ChatModel chatModel;

    public VisionImageContentParser(ChatModel chatModel) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel must not be null");
    }

    @Override
    public String parserId() {
        return "image-vision";
    }

    @Override
    public String contentType() {
        return ContentTypes.IMAGE;
    }

    @Override
    public String contentProfile() {
        return BuiltinContentProfiles.IMAGE_CAPTION_OCR;
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
            return Mono.error(new IllegalArgumentException("Image payload must not be empty"));
        }

        String resolvedMimeType = resolveMimeType(source);
        return Mono.fromCallable(
                        () -> chatModel.call(buildPrompt(data, source, DEFAULT_INSTRUCTION)))
                .map(
                        response -> {
                            var result = response == null ? null : response.getResult();
                            var message = result == null ? null : result.getOutput();
                            String payload = message == null ? null : message.getText();
                            if (payload == null || payload.isBlank()) {
                                throw new IllegalStateException(
                                        "Image analysis response must not be blank");
                            }
                            ImageAnalysisResult parsed =
                                    JsonUtils.fromJson(
                                            stripMarkdownFence(payload), ImageAnalysisResult.class);
                            if (parsed == null) {
                                throw new IllegalStateException(
                                        "Image analysis response could not be parsed");
                            }
                            var metadata =
                                    new LinkedHashMap<String, Object>(
                                            enrichMetadata(parsed.metadata(), source));
                            metadata.put("parserId", parserId());
                            metadata.put("contentProfile", contentProfile());
                            metadata.put("parser", "vision");
                            return new ImageContent(
                                    resolvedMimeType,
                                    parsed.description(),
                                    parsed.ocrText(),
                                    source.sourceUrl(),
                                    metadata);
                        });
    }

    private Prompt buildPrompt(byte[] data, SourceDescriptor source, String instruction) {
        String mimeType = resolveMimeType(source);
        Media media =
                Media.builder()
                        .mimeType(MimeType.valueOf(mimeType))
                        .data(new NamedByteArrayResource(data, source.fileName()))
                        .name(source.fileName())
                        .build();
        return new Prompt(
                java.util.List.of(
                        new SystemMessage(defaultInstruction(instruction)),
                        UserMessage.builder().text(RESPONSE_INSTRUCTION).media(media).build()));
    }

    private static String defaultInstruction(String instruction) {
        if (instruction == null || instruction.isBlank()) {
            return "Describe the image faithfully and extract any visible text.";
        }
        return instruction;
    }

    private static Map<String, Object> enrichMetadata(
            Map<String, Object> metadata, SourceDescriptor source) {
        var merged = new LinkedHashMap<String, Object>();
        if (metadata != null) {
            merged.putAll(metadata);
        }
        merged.putIfAbsent("provider", "spring-ai");
        if (source.mimeType() != null) {
            merged.putIfAbsent("sourceMimeType", source.mimeType());
        }
        return Map.copyOf(merged);
    }

    private static String stripMarkdownFence(String value) {
        String trimmed = value.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewline = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        if (firstNewline < 0 || lastFence <= firstNewline) {
            return trimmed;
        }
        return trimmed.substring(firstNewline + 1, lastFence).trim();
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
            return "image/png";
        }
        String lowerCase = fileName.toLowerCase(Locale.ROOT);
        if (lowerCase.endsWith(".jpg") || lowerCase.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lowerCase.endsWith(".webp")) {
            return "image/webp";
        }
        if (lowerCase.endsWith(".gif")) {
            return "image/gif";
        }
        return "image/png";
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
