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
package com.openmemind.ai.memory.core.extraction.item.graph.link.temporal;

import java.time.Instant;
import java.util.Objects;

/**
 * Resolved temporal window used by temporal graph stages.
 */
public record TemporalWindow(Instant start, Instant end, Instant anchor) {

    public TemporalWindow {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(anchor, "anchor");
    }

    public Instant endOrAnchor() {
        return end != null ? end : anchor;
    }
}
