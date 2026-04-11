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
package com.openmemind.ai.memory.core.extraction.rawdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ConversationContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Helper for applying {@link RawContent} subtype registrations to an {@link ObjectMapper}.
 */
public final class RawContentJackson {

    private RawContentJackson() {}

    public static void registerCoreSubtypes(ObjectMapper mapper) {
        Objects.requireNonNull(mapper, "mapper");
        mapper.registerSubtypes(new NamedType(ConversationContent.class, "conversation"));
    }

    public static void registerPluginSubtypes(
            ObjectMapper mapper, Collection<RawContentTypeRegistrar> registrars) {
        Objects.requireNonNull(mapper, "mapper");
        for (var entry : pluginSubtypeMappings(registrars).entrySet()) {
            mapper.registerSubtypes(new NamedType(entry.getValue(), entry.getKey()));
        }
    }

    public static void registerAll(
            ObjectMapper mapper, Collection<RawContentTypeRegistrar> registrars) {
        registerCoreSubtypes(mapper);
        registerPluginSubtypes(mapper, registrars);
    }

    public static Map<String, Class<? extends RawContent>> pluginSubtypeMappings(
            Collection<RawContentTypeRegistrar> registrars) {
        Map<String, Class<? extends RawContent>> mappings = new LinkedHashMap<>();
        for (RawContentTypeRegistrar registrar : Objects.requireNonNull(registrars, "registrars")) {
            Map<String, Class<? extends RawContent>> subtypes =
                    Objects.requireNonNull(
                            Objects.requireNonNull(registrar, "registrar").subtypes(),
                            "registrar.subtypes()");
            for (var entry : subtypes.entrySet()) {
                String subtypeName = Objects.requireNonNull(entry.getKey(), "subtypeName");
                Class<? extends RawContent> subtypeClass =
                        Objects.requireNonNull(entry.getValue(), "subtypeClass");
                Class<? extends RawContent> previous =
                        mappings.putIfAbsent(subtypeName, subtypeClass);
                if (previous != null) {
                    throw new IllegalArgumentException(
                            "Duplicate RawContent subtype name: " + subtypeName);
                }
            }
        }
        return Map.copyOf(mappings);
    }
}
