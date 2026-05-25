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
package com.openmemind.ai.memory.plugin.rawdata.agent.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * Episode outcome.
 */
public enum AgentOutcome {
    SUCCESS,
    FAILED,
    PARTIAL_SUCCESS,
    CANCELLED,
    UNKNOWN;

    @JsonCreator
    public static AgentOutcome fromWireValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return AgentOutcome.valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
