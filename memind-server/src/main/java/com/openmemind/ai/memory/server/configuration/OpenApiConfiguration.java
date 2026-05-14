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
package com.openmemind.ai.memory.server.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    private static final String TAG_MEMORY = "memory";
    private static final String TAG_ADMIN = "admin";

    private static final Map<String, String> OPERATION_SUMMARIES =
            Map.ofEntries(
                    Map.entry("GET /open/v1/health", "Health"),
                    Map.entry("POST /open/v1/memory/retrieve", "Retrieve Memory"),
                    Map.entry("POST /open/v1/memory/sync/extract", "Extract Memory Sync"),
                    Map.entry("POST /open/v1/memory/sync/add-message", "Add Message Sync"),
                    Map.entry("POST /open/v1/memory/sync/commit", "Commit Memory Sync"),
                    Map.entry("POST /open/v1/memory/async/extract", "Extract Memory Async"),
                    Map.entry("POST /open/v1/memory/async/add-message", "Add Message Async"),
                    Map.entry("POST /open/v1/memory/async/commit", "Commit Memory Async"),
                    Map.entry("GET /admin/v1/config/memory-options", "Get Memory Options"),
                    Map.entry("PUT /admin/v1/config/memory-options", "Update Memory Options"),
                    Map.entry("GET /admin/v1/dashboard", "Get Dashboard"),
                    Map.entry("GET /admin/v1/items", "List Memory Items"),
                    Map.entry("GET /admin/v1/items/{itemId}", "Get Memory Item"),
                    Map.entry(
                            "GET /admin/v1/items/{itemId}/memory-threads",
                            "List Item Memory Threads"),
                    Map.entry("DELETE /admin/v1/items", "Delete Memory Items"),
                    Map.entry("GET /admin/v1/insights", "List Insights"),
                    Map.entry("GET /admin/v1/insights/{insightId}", "Get Insight"),
                    Map.entry("DELETE /admin/v1/insights", "Delete Insights"),
                    Map.entry("GET /admin/v1/raw-data", "List Raw Data"),
                    Map.entry("GET /admin/v1/raw-data/{rawDataId}", "Get Raw Data"),
                    Map.entry("DELETE /admin/v1/raw-data", "Delete Raw Data"),
                    Map.entry("GET /admin/v1/memory-threads", "List Memory Threads"),
                    Map.entry("GET /admin/v1/memory-threads/{threadKey}", "Get Memory Thread"),
                    Map.entry(
                            "GET /admin/v1/memory-threads/{threadKey}/items",
                            "List Memory Thread Items"),
                    Map.entry("GET /admin/v1/memory-threads/status", "Get Memory Thread Status"),
                    Map.entry("POST /admin/v1/memory-threads/rebuild", "Rebuild Memory Threads"),
                    Map.entry("GET /admin/v1/item-graph/summary", "Get Item Graph Summary"),
                    Map.entry("GET /admin/v1/item-graph/entities", "List Graph Entities"),
                    Map.entry("GET /admin/v1/item-graph/entities/{id}", "Get Graph Entity"),
                    Map.entry("DELETE /admin/v1/item-graph/entities", "Delete Graph Entities"),
                    Map.entry("GET /admin/v1/item-graph/aliases", "List Graph Aliases"),
                    Map.entry("DELETE /admin/v1/item-graph/aliases", "Delete Graph Aliases"),
                    Map.entry("GET /admin/v1/item-graph/mentions", "List Graph Mentions"),
                    Map.entry("DELETE /admin/v1/item-graph/mentions", "Delete Graph Mentions"),
                    Map.entry("GET /admin/v1/item-graph/item-links", "List Item Graph Links"),
                    Map.entry("DELETE /admin/v1/item-graph/item-links", "Delete Item Graph Links"),
                    Map.entry("GET /admin/v1/item-graph/cooccurrences", "List Graph Cooccurrences"),
                    Map.entry(
                            "DELETE /admin/v1/item-graph/cooccurrences",
                            "Delete Graph Cooccurrences"),
                    Map.entry("GET /admin/v1/item-graph/batches", "List Item Graph Batches"),
                    Map.entry("GET /admin/v1/buffers/conversations", "List Conversation Buffers"),
                    Map.entry(
                            "GET /admin/v1/buffers/conversations/{id}", "Get Conversation Buffer"),
                    Map.entry(
                            "PATCH /admin/v1/buffers/conversations/extracted",
                            "Mark Conversation Buffers Extracted"),
                    Map.entry(
                            "DELETE /admin/v1/buffers/conversations",
                            "Delete Conversation Buffers"),
                    Map.entry("GET /admin/v1/buffers/insights", "List Insight Buffers"),
                    Map.entry(
                            "GET /admin/v1/buffers/insights/groups", "List Insight Buffer Groups"),
                    Map.entry(
                            "PATCH /admin/v1/buffers/insights/group",
                            "Update Insight Buffer Group"),
                    Map.entry(
                            "PATCH /admin/v1/buffers/insights/built",
                            "Update Insight Buffer Built"),
                    Map.entry("DELETE /admin/v1/buffers/insights", "Delete Insight Buffers"));

    @Bean
    OpenAPI memindServerOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Memind Server API")
                                .version("v1")
                                .description(
                                        "HTTP API for Memind memory ingestion, retrieval, and"
                                                + " administration."));
    }

    @Bean
    OpenApiCustomizer memindServerOpenApiCustomizer() {
        return openApi -> {
            openApi.setServers(null);
            openApi.setTags(
                    List.of(
                            tag(
                                    TAG_MEMORY,
                                    "Runtime memory ingestion, retrieval, and health endpoints."),
                            tag(TAG_ADMIN, "Administrative endpoints for memory operations.")));
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths()
                    .forEach(
                            (path, pathItem) ->
                                    pathItem.readOperationsMap()
                                            .forEach(
                                                    (method, operation) ->
                                                            customizeOperation(
                                                                    path, method, operation)));
        };
    }

    private static void customizeOperation(
            String path, PathItem.HttpMethod method, Operation operation) {
        String tag = tagForPath(path);
        String group = groupForPath(path);
        String key = method.name() + " " + path;
        String summary = OPERATION_SUMMARIES.getOrDefault(key, titleFromPath(path));

        operation.setTags(List.of(tag));
        operation.setSummary(summary);
        operation.setOperationId(operationId(summary));
        operation.addExtension(
                "x-mint",
                Map.of(
                        "href",
                        href(tag, group, summary),
                        "metadata",
                        Map.of(
                                "title", summary,
                                "sidebarTitle", summary)));
    }

    private static Tag tag(String name, String description) {
        Tag tag = new Tag().name(name).description(description);
        tag.addExtension("x-group", name);
        return tag;
    }

    private static String tagForPath(String path) {
        if (!path.startsWith("/admin/")) {
            return TAG_MEMORY;
        }
        return TAG_ADMIN;
    }

    private static String groupForPath(String path) {
        if (!path.startsWith("/admin/")) {
            return "";
        }
        if (path.startsWith("/admin/v1/config/")) {
            return "config";
        }
        if (path.startsWith("/admin/v1/items")) {
            return "memory-items";
        }
        if (path.startsWith("/admin/v1/raw-data")) {
            return "raw-data";
        }
        if (path.startsWith("/admin/v1/memory-threads")) {
            return "memory-threads";
        }
        if (path.startsWith("/admin/v1/item-graph")) {
            return "item-graph";
        }
        if (path.startsWith("/admin/v1/insights")) {
            return "insights";
        }
        if (path.startsWith("/admin/v1/buffers")) {
            return "buffers";
        }
        return "dashboard";
    }

    private static String href(String tag, String group, String summary) {
        if (TAG_MEMORY.equals(tag)) {
            return "/api-reference/memory/" + slug(summary);
        }
        return "/api-reference/admin/" + group + "/" + slug(summary);
    }

    private static String operationId(String summary) {
        String words = summary.replace('-', ' ');
        String[] parts = words.trim().split("\\s+");
        StringBuilder id = new StringBuilder(parts[0].toLowerCase(Locale.ROOT));
        for (int i = 1; i < parts.length; i++) {
            id.append(capitalize(parts[i]));
        }
        return id.toString();
    }

    private static String titleFromPath(String path) {
        return Optional.of(path)
                .map(value -> value.replaceAll("\\{([^}]+)}", "$1"))
                .map(value -> value.replace('-', ' '))
                .map(value -> value.replace('/', ' '))
                .map(String::trim)
                .map(value -> value.replaceFirst("^(open|admin)\\s+v\\d+\\s+", ""))
                .map(OpenApiConfiguration::titleCase)
                .orElse("Endpoint");
    }

    private static String slug(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    private static String titleCase(String value) {
        String[] parts = value.split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(capitalize(part));
        }
        return result.toString();
    }

    private static String capitalize(String value) {
        if (value.isBlank()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }
}
