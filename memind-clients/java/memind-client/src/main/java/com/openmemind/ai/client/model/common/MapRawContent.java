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
package com.openmemind.ai.client.model.common;

import java.util.Map;
import java.util.Objects;

public final class MapRawContent extends RawContent {

    private final String type;
    private final Map<String, Object> properties;

    private MapRawContent(String type, Map<String, Object> properties) {
        this.type = Objects.requireNonNull(type, "type");
        this.properties = Map.copyOf(Objects.requireNonNull(properties, "properties"));
    }

    public static MapRawContent of(String type, Map<String, Object> properties) {
        return new MapRawContent(type, properties);
    }

    @Override
    public String type() {
        return type;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }
}
