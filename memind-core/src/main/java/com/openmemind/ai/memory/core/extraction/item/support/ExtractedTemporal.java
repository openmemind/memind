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
package com.openmemind.ai.memory.core.extraction.item.support;

import java.time.Instant;

/**
 * Normalized temporal payload used by the extraction pipeline before persistence.
 *
 * @param occurredStart semantic lower bound in UTC
 * @param occurredEnd semantic exclusive upper bound in UTC
 * @param granularity normalized temporal granularity
 * @param expression original temporal phrase from source text
 * @param compatibilityOccurredAt compatibility anchor for legacy occurredAt
 */
public record ExtractedTemporal(
        Instant occurredStart,
        Instant occurredEnd,
        String granularity,
        String expression,
        Instant compatibilityOccurredAt) {}
