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
package com.openmemind.ai.memory.core.utils;

import com.openmemind.ai.memory.core.extraction.rawdata.RawContentJackson;
import java.util.List;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * JSON utility class
 *
 */
public final class JsonUtils {

    private static final JsonMapper MAPPER = createMapper();

    private JsonUtils() {}

    private static JsonMapper createMapper() {
        JsonMapper mapper =
                JsonMapper.builder()
                        .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .build();
        return RawContentJackson.registerCoreSubtypes(mapper);
    }

    /**
     * Convert object to JSON string
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JacksonException e) {
            throw new JsonException("Failed to serialize object to JSON", e);
        }
    }

    /**
     * Convert object to formatted JSON string
     */
    public static String toPrettyJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JacksonException e) {
            throw new JsonException("Failed to serialize object to JSON", e);
        }
    }

    /**
     * Convert JSON string to object
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, clazz);
        } catch (JacksonException e) {
            throw new JsonException("Failed to deserialize JSON to " + clazz.getSimpleName(), e);
        }
    }

    /**
     * Convert JSON string to generic object
     */
    public static <T> T fromJson(String json, TypeReference<T> typeRef) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, typeRef);
        } catch (JacksonException e) {
            throw new JsonException("Failed to deserialize JSON", e);
        }
    }

    /**
     * Convert JSON string to List
     */
    public static <T> List<T> toList(String json, Class<T> elementClass) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            var type = MAPPER.getTypeFactory().constructCollectionType(List.class, elementClass);
            return MAPPER.readValue(json, type);
        } catch (JacksonException e) {
            throw new JsonException(
                    "Failed to deserialize JSON to List<" + elementClass.getSimpleName() + ">", e);
        }
    }

    /**
     * Convert JSON string to Map
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> toMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, Map.class);
        } catch (JacksonException e) {
            throw new JsonException("Failed to deserialize JSON to Map", e);
        }
    }

    /**
     * Get ObjectMapper instance (for advanced scenarios)
     */
    public static JsonMapper mapper() {
        return MAPPER;
    }

    /**
     * Create an independent mapper instance preserving the shared baseline
     * configuration, modules and raw-content subtype registrations.
     */
    public static JsonMapper newMapper() {
        return MAPPER.rebuild().build();
    }

    /**
     * JSON processing exception
     */
    public static class JsonException extends RuntimeException {
        public JsonException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
