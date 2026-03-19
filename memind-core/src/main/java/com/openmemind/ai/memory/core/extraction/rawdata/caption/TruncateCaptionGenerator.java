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
package com.openmemind.ai.memory.core.extraction.rawdata.caption;

import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * Truncation-based summary generator
 *
 * <p>Take the first line, truncate if too long.
 *
 */
public class TruncateCaptionGenerator implements CaptionGenerator {

    private final int maxLength;

    public TruncateCaptionGenerator() {
        this(200);
    }

    public TruncateCaptionGenerator(int maxLength) {
        this.maxLength = maxLength;
    }

    @Override
    public Mono<String> generate(String content, Map<String, Object> metadata) {
        String firstLine = content.lines().findFirst().orElse("");
        String caption =
                firstLine.length() > maxLength
                        ? firstLine.substring(0, maxLength) + "..."
                        : firstLine;
        return Mono.just(caption);
    }
}
