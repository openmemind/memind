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
package com.openmemind.ai.memory.benchmark.longmemeval;

import java.util.Locale;

public final class LongMemEvalJudgeRules {

    public boolean accepts(String questionType, String generated, String golden) {
        String normalizedGenerated = normalize(generated);
        String normalizedGolden = normalize(golden);
        if (normalizedGenerated == null || normalizedGolden == null) {
            return false;
        }
        if (!"temporal-reasoning".equals(questionType)) {
            return normalizedGenerated.equals(normalizedGolden);
        }

        Integer generatedNumber = extractFirstInteger(normalizedGenerated);
        Integer goldenNumber = extractFirstInteger(normalizedGolden);
        if (generatedNumber != null && goldenNumber != null) {
            return Math.abs(generatedNumber - goldenNumber) <= 1;
        }
        return normalizedGenerated.equals(normalizedGolden);
    }

    public String promptPath(String questionType) {
        return switch (questionType) {
            case "temporal-reasoning" -> "prompts/longmemeval/judge-temporal.txt";
            case "knowledge-update" -> "prompts/longmemeval/judge-knowledge-update.txt";
            case "single-session-preference" -> "prompts/longmemeval/judge-preference.txt";
            default -> "prompts/longmemeval/judge-default.txt";
        };
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private Integer extractFirstInteger(String value) {
        String digits = value.replaceAll("[^0-9]", " ").trim();
        if (digits.isBlank()) {
            return null;
        }
        return Integer.parseInt(digits.split("\\s+")[0]);
    }
}
