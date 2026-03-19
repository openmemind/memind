package com.openmemind.ai.memory.core.utils;

import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;

/**
 * Utility for estimating token counts of text and conversation messages.
 *
 * <p>Uses {@link JTokkitTokenCountEstimator} internally — no external configuration needed.
 */
public final class TokenUtils {

    private static final JTokkitTokenCountEstimator ESTIMATOR = new JTokkitTokenCountEstimator();

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
        return ESTIMATOR.estimate(text);
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
        return ESTIMATOR.estimate(text);
    }
}
