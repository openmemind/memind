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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentJackson;
import java.util.List;
import java.util.Map;

/**
 * JSON utility class
 *
 */
public final class JsonUtils {

    private static final ObjectMapper MAPPER = createMapper();

    private JsonUtils() {}

    private static ObjectMapper createMapper() {
        ObjectMapper mapper =
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        RawContentJackson.registerCoreSubtypes(mapper);
        return mapper;
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
        } catch (JsonProcessingException e) {
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
        } catch (JsonProcessingException e) {
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
        } catch (JsonProcessingException e) {
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
        } catch (JsonProcessingException e) {
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
        } catch (JsonProcessingException e) {
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
        } catch (JsonProcessingException e) {
            throw new JsonException("Failed to deserialize JSON to Map", e);
        }
    }

    /**
     * Get ObjectMapper instance (for advanced scenarios)
     */
    public static ObjectMapper mapper() {
        return MAPPER;
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
