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

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility for estimating token counts of text and conversation messages.
 *
 * <p>Uses jtokkit internally — no external configuration needed.
 */
public final class TokenUtils {

    private static final Encoding ENCODING =
            Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);

    private TokenUtils() {}

    /**
     * Estimates the token count for a plain text string.
     *
     * @param text the input text; returns 0 for null or blank input
     * @return estimated token count
     */
    public static int countTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return ENCODING.countTokens(text);
    }

    /**
     * Estimates the total token count for a list of messages.
     *
     * <p>Message contents are joined with newlines before estimation.
     *
     * @param messages the messages to count; returns 0 for null or empty input
     * @return estimated token count
     */
    public static int countTokens(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        var text = messages.stream().map(Message::textContent).collect(Collectors.joining("\n"));
        return ENCODING.countTokens(text);
    }
}
