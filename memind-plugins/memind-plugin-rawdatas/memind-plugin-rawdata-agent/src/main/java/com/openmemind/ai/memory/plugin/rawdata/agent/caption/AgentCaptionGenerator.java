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
package com.openmemind.ai.memory.plugin.rawdata.agent.caption;

import com.openmemind.ai.memory.core.extraction.rawdata.caption.CaptionGenerator;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * Deterministic caption generator for agent episode segments.
 */
public final class AgentCaptionGenerator implements CaptionGenerator {

    @Override
    public Mono<String> generate(String content, Map<String, Object> metadata) {
        return Mono.just(caption(content, metadata));
    }

    @Override
    public Mono<String> generate(String content, Map<String, Object> metadata, String language) {
        return generate(content, metadata);
    }

    private String caption(String content, Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return truncate(content, 160);
        }
        String goal = stringValue(metadata.get("goal"));
        String outcome = stringValue(metadata.get("outcome"));
        String summary = summary(metadata);
        String base =
                "Agent episode: "
                        + (goal.isBlank() ? "unknown goal" : goal)
                        + " -> "
                        + (outcome.isBlank() ? "unknown" : outcome);
        if (summary.isBlank()) {
            return base;
        }
        return base + " (" + summary + ")";
    }

    private String summary(Map<String, Object> metadata) {
        String file = first(metadata.get("files"));
        String command = first(metadata.get("commands"));
        if (!file.isBlank() && !command.isBlank()) {
            return file + "; " + command;
        }
        if (!file.isBlank()) {
            return file;
        }
        return command;
    }

    private String first(Object value) {
        if (value instanceof List<?> list && !list.isEmpty() && list.getFirst() != null) {
            return list.getFirst().toString();
        }
        return "";
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String truncate(String content, int maxChars) {
        if (content == null) {
            return "";
        }
        return content.length() <= maxChars ? content : content.substring(0, maxChars);
    }
}
