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

import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Pattern;
import reactor.core.publisher.Mono;

/**
 * Tool call caption generator — simple formatter only (no LLM).
 *
 * <p>Extracts tool names and execution results from metadata to generate concise summaries. LLM
 * processing of tool calls is handled downstream by the item layer, not the rawdata layer.
 */
public class ToolCallCaptionGenerator implements CaptionGenerator {

    private static final Pattern PARAM_KEY_PATTERN = Pattern.compile("\"(\\w+)\"\\s*:");

    @Override
    public Mono<String> generate(String content, Map<String, Object> metadata) {
        return Mono.just(buildSimpleCaption(content, metadata));
    }

    @Override
    public Mono<String> generate(String content, Map<String, Object> metadata, String language) {
        return Mono.just(buildSimpleCaption(content, metadata));
    }

    private String buildSimpleCaption(String content, Map<String, Object> metadata) {
        if (metadata == null) {
            return content.substring(0, Math.min(content.length(), 200));
        }

        Object toolName = metadata.get("toolName");
        Object status = metadata.get("status");
        Object durationMs = metadata.get("durationMs");
        Object input = metadata.get("input");
        Object outputLength = metadata.get("outputLength");

        if (toolName == null) {
            return content.substring(0, Math.min(content.length(), 200));
        }

        var sb = new StringBuilder();
        sb.append("[%s] %s".formatted(status != null ? status : "unknown", toolName));

        if (input != null) {
            var paramNames = extractParamNames(input.toString());
            if (!paramNames.isEmpty()) {
                sb.append("(").append(paramNames).append(")");
            }
        }

        sb.append(" → ");
        if (outputLength != null) {
            sb.append(outputLength).append("chars");
        }
        if (durationMs != null) {
            sb.append(", ").append(durationMs).append("ms");
        }
        return sb.toString();
    }

    private String extractParamNames(String jsonInput) {
        var keys = new ArrayList<String>();
        var matcher = PARAM_KEY_PATTERN.matcher(jsonInput);
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return String.join(", ", keys);
    }
}
