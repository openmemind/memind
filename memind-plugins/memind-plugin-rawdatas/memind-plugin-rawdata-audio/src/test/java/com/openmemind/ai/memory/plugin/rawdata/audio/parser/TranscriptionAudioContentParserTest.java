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

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.resource.SourceDescriptor;
import com.openmemind.ai.memory.core.resource.SourceKind;
import com.openmemind.ai.memory.plugin.rawdata.audio.content.AudioContent;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import org.springframework.ai.audio.transcription.AudioTranscription;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import reactor.test.StepVerifier;

class TranscriptionAudioContentParserTest {

    @Test
    void supportsOctetStreamByAudioExtension() {
        var parser =
                new TranscriptionAudioContentParser(
                        transcriptionModel(
                                new AudioTranscriptionResponse(
                                        new AudioTranscription("speaker one said hello"))));

        assertThat(
                        parser.supports(
                                new SourceDescriptor(
                                        SourceKind.FILE,
                                        "sample.mp3",
                                        "application/octet-stream",
                                        3L,
                                        null)))
                .isTrue();
    }

    @Test
    void parserMapsTranscriptAndSegmentsIntoAudioContent() {
        var parser =
                new TranscriptionAudioContentParser(
                        transcriptionModel(
                                new AudioTranscriptionResponse(
                                        new AudioTranscription("speaker one said hello"))));

        var content =
                (AudioContent)
                        parser.parse(
                                        new byte[] {1, 2, 3},
                                        new SourceDescriptor(
                                                SourceKind.FILE,
                                                "sample.mp3",
                                                "audio/mpeg",
                                                3L,
                                                null))
                                .block();

        assertThat(content).isNotNull();
        assertThat(content.transcript()).contains("speaker one said hello");
        assertThat(content.segments()).isEmpty();
        assertThat(content.metadata())
                .containsEntry("parserId", "audio-transcription")
                .containsEntry("provider", "spring-ai");
    }

    @Test
    void parserFailsFastWhenModelDoesNotSupportAudioTranscription() {
        var parser = new TranscriptionAudioContentParser(unsupportedTranscriptionModel());

        StepVerifier.create(
                        parser.parse(
                                new byte[] {1, 2, 3},
                                new SourceDescriptor(
                                        SourceKind.FILE, "sample.mp3", "audio/mpeg", 3L, null)))
                .expectErrorSatisfies(
                        error -> {
                            assertThat(error)
                                    .isInstanceOf(IllegalStateException.class)
                                    .hasMessageContaining("audio transcription");
                            assertThat(error.getCause())
                                    .isInstanceOf(UnsupportedOperationException.class)
                                    .hasMessageContaining("audio transcription unsupported");
                        })
                .verify();
    }

    @SuppressWarnings("unchecked")
    private static TranscriptionModel transcriptionModel(AudioTranscriptionResponse response) {
        return (TranscriptionModel)
                Proxy.newProxyInstance(
                        TranscriptionAudioContentParserTest.class.getClassLoader(),
                        new Class<?>[] {TranscriptionModel.class},
                        (proxy, method, args) ->
                                switch (method.getName()) {
                                    case "call" -> {
                                        if (args.length == 1
                                                && args[0] instanceof AudioTranscriptionPrompt) {
                                            yield response;
                                        }
                                        throw new UnsupportedOperationException(method.getName());
                                    }
                                    case "toString" -> "FakeTranscriptionModel";
                                    case "hashCode" -> System.identityHashCode(proxy);
                                    case "equals" -> proxy == args[0];
                                    default ->
                                            throw new UnsupportedOperationException(
                                                    method.getName());
                                });
    }

    @SuppressWarnings("unchecked")
    private static TranscriptionModel unsupportedTranscriptionModel() {
        return (TranscriptionModel)
                Proxy.newProxyInstance(
                        TranscriptionAudioContentParserTest.class.getClassLoader(),
                        new Class<?>[] {TranscriptionModel.class},
                        (proxy, method, args) ->
                                switch (method.getName()) {
                                    case "call" ->
                                            throw new UnsupportedOperationException(
                                                    "audio transcription unsupported");
                                    case "toString" -> "UnsupportedTranscriptionModel";
                                    case "hashCode" -> System.identityHashCode(proxy);
                                    case "equals" -> proxy == args[0];
                                    default ->
                                            throw new UnsupportedOperationException(
                                                    method.getName());
                                });
    }
}
