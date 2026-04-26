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
package com.openmemind.ai.memory.core.extraction.item.graph.entity.normalize;

import com.openmemind.ai.memory.core.store.graph.GraphEntityType;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Default exact-path noise filter for built-in English and Chinese coverage.
 */
public final class LanguageAwareEntityNoiseFilter implements EntityNoiseFilter {

    private static final Pattern ISO_DATE_LIKE = Pattern.compile("^\\d{4}(?:-\\d{2}){0,2}$");
    private static final Pattern PUNCTUATION_ONLY = Pattern.compile("^[\\p{P}\\p{S}]+$");
    private static final Set<String> PRONOUN_LIKE =
            Set.of(
                    "i", "me", "my", "mine", "you", "your", "yours", "we", "our", "ours", "they",
                    "their", "theirs", "he", "him", "his", "she", "her", "hers", "it", "its", "我",
                    "我们", "你", "你们", "他", "她", "他们", "她们", "它", "自己");
    private static final Set<String> TEMPORAL =
            Set.of(
                    "today",
                    "yesterday",
                    "tomorrow",
                    "last week",
                    "this week",
                    "next week",
                    "last month",
                    "this month",
                    "next month",
                    "last year",
                    "this year",
                    "next year",
                    "前天",
                    "昨天",
                    "今天",
                    "明天",
                    "后天",
                    "上周",
                    "这周",
                    "本周",
                    "下周",
                    "上个月",
                    "这个月",
                    "本月",
                    "下个月",
                    "去年",
                    "今年",
                    "明年");

    @Override
    public Optional<EntityDropReason> dropReason(
            String normalizedName, GraphEntityType entityType) {
        if (normalizedName == null || normalizedName.isBlank()) {
            return Optional.of(EntityDropReason.BLANK);
        }
        if (PUNCTUATION_ONLY.matcher(normalizedName).matches()) {
            return Optional.of(EntityDropReason.PUNCTUATION_ONLY);
        }
        // Only mentions that can be safely mapped to a reserved special anchor bypass
        // the generic pronoun/temporal/date-like noise gates.
        boolean isReservedAnchorUnderSpecial =
                entityType == GraphEntityType.SPECIAL
                        && SpecialEntityAnchors.isReservedAnchorName(normalizedName);
        if (isReservedAnchorUnderSpecial) {
            return Optional.empty();
        }
        if (entityType != GraphEntityType.SPECIAL
                && SpecialEntityAnchors.isReservedAnchorName(normalizedName)) {
            return Optional.of(EntityDropReason.RESERVED_SPECIAL_COLLISION);
        }
        if (PRONOUN_LIKE.contains(normalizedName)) {
            return Optional.of(EntityDropReason.PRONOUN_LIKE);
        }
        if (TEMPORAL.contains(normalizedName)) {
            return Optional.of(EntityDropReason.TEMPORAL);
        }
        if (ISO_DATE_LIKE.matcher(normalizedName).matches()) {
            return Optional.of(EntityDropReason.DATE_LIKE);
        }
        return Optional.empty();
    }
}
