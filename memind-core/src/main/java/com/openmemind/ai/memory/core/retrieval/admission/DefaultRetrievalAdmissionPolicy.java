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
package com.openmemind.ai.memory.core.retrieval.admission;

import com.openmemind.ai.memory.core.retrieval.RetrievalRequest;
import com.openmemind.ai.memory.core.utils.TokenUtils;
import java.util.Objects;

public final class DefaultRetrievalAdmissionPolicy implements RetrievalAdmissionPolicy {

    private final RetrievalAdmissionOptions options;

    public DefaultRetrievalAdmissionPolicy(RetrievalAdmissionOptions options) {
        this.options = Objects.requireNonNull(options, "options must not be null");
    }

    @Override
    public RetrievalAdmissionResult evaluate(RetrievalRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String query = request.query();
        if (query == null || query.isBlank()) {
            return result(
                    RetrievalAdmissionDecision.SKIP, RetrievalAdmissionReason.EMPTY_QUERY, query);
        }
        if (hasInvalidControlCharacter(query)) {
            return result(
                    RetrievalAdmissionDecision.REJECT,
                    RetrievalAdmissionReason.INVALID_CONTROL_CHARACTER,
                    query);
        }
        if (!hasTextToken(query)) {
            return result(
                    RetrievalAdmissionDecision.SKIP, RetrievalAdmissionReason.NO_TEXT_TOKEN, query);
        }

        int charCount = query.codePointCount(0, query.length());
        if (charCount > options.maxQueryChars()) {
            return new RetrievalAdmissionResult(
                    RetrievalAdmissionDecision.QUERY_TOO_LONG,
                    RetrievalAdmissionReason.QUERY_TOO_MANY_CHARS,
                    TokenUtils.countTokens(query),
                    charCount);
        }

        int tokenCount = TokenUtils.countTokens(query);
        if (tokenCount > options.maxQueryTokens()) {
            return new RetrievalAdmissionResult(
                    RetrievalAdmissionDecision.QUERY_TOO_LONG,
                    RetrievalAdmissionReason.QUERY_TOO_MANY_TOKENS,
                    tokenCount,
                    charCount);
        }

        return RetrievalAdmissionResult.admit(tokenCount, charCount);
    }

    private static RetrievalAdmissionResult result(
            RetrievalAdmissionDecision decision, RetrievalAdmissionReason reason, String query) {
        return new RetrievalAdmissionResult(
                decision,
                reason,
                query == null ? 0 : TokenUtils.countTokens(query),
                query == null ? 0 : query.codePointCount(0, query.length()));
    }

    private static boolean hasInvalidControlCharacter(String query) {
        return query.codePoints()
                .anyMatch(
                        codePoint ->
                                Character.isISOControl(codePoint)
                                        && !Character.isWhitespace(codePoint));
    }

    private static boolean hasTextToken(String query) {
        return query.codePoints().anyMatch(Character::isLetterOrDigit);
    }
}
