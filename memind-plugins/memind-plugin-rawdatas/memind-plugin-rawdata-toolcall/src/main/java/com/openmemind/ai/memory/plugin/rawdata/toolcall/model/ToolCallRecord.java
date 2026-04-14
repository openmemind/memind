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
package com.openmemind.ai.memory.plugin.rawdata.toolcall.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/**
 * A single tool invocation record.
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
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(
                    (toolName + ":" + input + ":" + Objects.requireNonNullElse(output, ""))
                            .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        }
    }
}
