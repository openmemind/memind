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
package com.openmemind.ai.memory.plugin.rawdata.document.chunk;

import com.openmemind.ai.memory.core.extraction.rawdata.segment.CharBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import com.openmemind.ai.memory.core.utils.TokenUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class DocumentChunkSupport {

    CharBoundary boundaryOf(Segment segment) {
        if (segment.boundary() instanceof CharBoundary charBoundary) {
            return charBoundary;
        }
        throw new IllegalArgumentException("Document chunkers require CharBoundary segments");
    }

    Segment withMetadata(Segment segment, Map<String, Object> metadata) {
        return new Segment(
                segment.content(),
                segment.caption(),
                segment.boundary(),
                Map.copyOf(metadata),
                segment.runtimeContext());
    }

    Segment mergeMetadata(Segment segment, Map<String, Object> metadata) {
        Map<String, Object> merged = new LinkedHashMap<>(segment.metadata());
        merged.putAll(metadata);
        return new Segment(
                segment.content(),
                segment.caption(),
                segment.boundary(),
                Map.copyOf(merged),
                segment.runtimeContext());
    }

    Segment shift(Segment parent, Segment child) {
        CharBoundary parentBoundary = boundaryOf(parent);
        CharBoundary childBoundary = boundaryOf(child);
        return new Segment(
                child.content(),
                child.caption(),
                new CharBoundary(
                        parentBoundary.startChar() + childBoundary.startChar(),
                        parentBoundary.startChar() + childBoundary.endChar()),
                child.metadata(),
                child.runtimeContext());
    }

    boolean isUndersized(Segment block, int minChunkTokens) {
        return TokenUtils.countTokens(block.content()) < minChunkTokens;
    }

    int mergedTokenCount(List<Segment> segments, Segment next) {
        int tokenCount =
                segments.stream().map(Segment::content).mapToInt(TokenUtils::countTokens).sum();
        return tokenCount + TokenUtils.countTokens(next.content());
    }

    List<Segment> withChunkIndexes(List<Segment> segments) {
        List<Segment> indexed = new ArrayList<>(segments.size());
        for (int index = 0; index < segments.size(); index++) {
            Segment segment = segments.get(index);
            Map<String, Object> metadata = new LinkedHashMap<>(segment.metadata());
            metadata.put("chunkIndex", index);
            indexed.add(
                    new Segment(
                            segment.content(),
                            segment.caption(),
                            segment.boundary(),
                            Map.copyOf(metadata),
                            segment.runtimeContext()));
        }
        return List.copyOf(indexed);
    }
}
