package com.openmemind.ai.memory.core.extraction.rawdata.content.tool;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/**
 * A single tool invocation record.
 *
 * @param toolName     tool name
 * @param input        raw input JSON
 * @param output       raw return
 * @param status       success / error / timeout / partial
 * @param durationMs   execution time (milliseconds)
 * @param inputTokens  number of input tokens
 * @param outputTokens number of output tokens
 * @param contentHash  MD5(toolName + ":" + input + ":" + output)
 * @param calledAt     call time
 */
public record ToolCallRecord(
        String toolName,
        String input,
        String output,
        String status,
        long durationMs,
        int inputTokens,
        int outputTokens,
        String contentHash,
        Instant calledAt) {

    public static String computeHash(String toolName, String input, String output) {
        Objects.requireNonNull(toolName, "toolName must not be null");
        Objects.requireNonNull(input, "input must not be null");
        var safeOutput = output != null ? output : "";
        try {
            var md = MessageDigest.getInstance("MD5");
            md.update((toolName + ":" + input + ":" + safeOutput).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        }
    }
}
