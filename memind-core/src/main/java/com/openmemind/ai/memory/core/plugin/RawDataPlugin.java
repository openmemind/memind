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
package com.openmemind.ai.memory.core.plugin;

import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessor;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentTypeRegistrar;
import com.openmemind.ai.memory.core.resource.ContentParser;
import java.util.List;

/**
 * Explicit runtime contribution point for rawdata processors, parsers, and subtype registrars.
 */
public interface RawDataPlugin {

    String pluginId();

    List<RawContentProcessor<?>> processors(RawDataPluginContext context);

    default List<ContentParser> parsers(RawDataPluginContext context) {
        return List.of();
    }

    default List<RawDataIngestionPolicy> ingestionPolicies() {
        return List.of();
    }

    default List<RawContentTypeRegistrar> typeRegistrars() {
        return List.of();
    }
}
