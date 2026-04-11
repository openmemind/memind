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
package com.openmemind.ai.memory.core.support;

import com.openmemind.ai.memory.core.data.ContentTypes;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessor;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import com.openmemind.ai.memory.core.utils.TokenUtils;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import reactor.core.publisher.Mono;

/**
 * Test-only processor used by core multimodal tests after document ownership moved to plugins.
 */
public final class TestDocumentProcessor implements RawContentProcessor<TestDocumentContent> {

    private final boolean usesSourceIdentity;
    private final int maxParsedTokens;
    private final Function<TestDocumentContent, List<Segment>> chunker;

    public TestDocumentProcessor() {
        this(
                false,
                Integer.MAX_VALUE,
                content -> List.of(Segment.single(content.toContentString())));
    }

    public TestDocumentProcessor(boolean usesSourceIdentity) {
        this(
                usesSourceIdentity,
                Integer.MAX_VALUE,
                content -> List.of(Segment.single(content.toContentString())));
    }

    public TestDocumentProcessor(boolean usesSourceIdentity, int maxParsedTokens) {
        this(
                usesSourceIdentity,
                maxParsedTokens,
                content -> List.of(Segment.single(content.toContentString())));
    }

    public TestDocumentProcessor(
            boolean usesSourceIdentity,
            int maxParsedTokens,
            Function<TestDocumentContent, List<Segment>> chunker) {
        this.usesSourceIdentity = usesSourceIdentity;
        this.maxParsedTokens = maxParsedTokens;
        this.chunker = Objects.requireNonNull(chunker, "chunker");
    }

    @Override
    public Class<TestDocumentContent> contentClass() {
        return TestDocumentContent.class;
    }

    @Override
    public String contentType() {
        return ContentTypes.DOCUMENT;
    }

    @Override
    public Mono<List<Segment>> chunk(TestDocumentContent content) {
        return Mono.just(chunker.apply(content));
    }

    @Override
    public boolean usesSourceIdentity() {
        return usesSourceIdentity;
    }

    @Override
    public void validateParsedContent(TestDocumentContent content) {
        if (TokenUtils.countTokens(content.toContentString()) > maxParsedTokens) {
            throw new IllegalArgumentException(
                    "Parsed content exceeds token limit for " + content.directContentProfile());
        }
    }
}
