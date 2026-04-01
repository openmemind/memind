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
package com.openmemind.ai.memory.benchmark.core.prompt;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

public final class PromptTemplate {

    private final String template;

    private PromptTemplate(String template) {
        this.template = Objects.requireNonNull(template, "template");
    }

    public static PromptTemplate inline(String template) {
        return new PromptTemplate(template);
    }

    public static PromptTemplate fromClasspath(String resourcePath) {
        Objects.requireNonNull(resourcePath, "resourcePath");
        try (var stream = PromptTemplate.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalArgumentException("Prompt resource not found: " + resourcePath);
            }
            return new PromptTemplate(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "Failed to read prompt resource: " + resourcePath, exception);
        }
    }

    public String render(Map<String, ?> variables) {
        Objects.requireNonNull(variables, "variables");
        String rendered = template;
        for (Map.Entry<String, ?> entry : variables.entrySet()) {
            rendered =
                    rendered.replace(
                            "{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
        }
        return rendered;
    }
}
