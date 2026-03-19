package com.openmemind.ai.memory.core.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Hash utility class
 *
 */
public final class HashUtils {

    private HashUtils() {}

    private static final int CHUNK_SIZE = 8192;

    private static final int SAMPLE_THRESHOLD = 2048;

    private static final int SAMPLE_SIZE = 1024;

    /**
     * Calculate the SHA-256 hash value of a string (streaming chunked, suitable for large text)
     *
     * <p>Chunked calls {@link MessageDigest#update}, memory peak is only a single chunk's byte[],
     * avoiding a single {@code getBytes()} producing a byte array as large as the original text.
     *
     * @param content The content to calculate the hash for
     * @return The hash value in hexadecimal format, returns null if the content is null
     */
    public static String sha256(String content) {
        if (content == null) {
            return null;
        }
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            int length = content.length();
            for (int i = 0; i < length; i += CHUNK_SIZE) {
                int end = Math.min(i + CHUNK_SIZE, length);
                digest.update(content.substring(i, end).getBytes(StandardCharsets.UTF_8));
            }
            return bytesToHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Calculate the hash of memory item content (used for deduplication)
     *
     * <p>Generate a 16-bit hex hash based on normalized content
     *
     * @param content Memory content
     * @return 16-bit hex hash
     */
    public static String contentHash(String content) {
        String normalized = normalizeContent(content);
        return sha256(normalized).substring(0, 16);
    }

    /**
     * Sampled SHA-256 hash (O(1) performance for large text)
     *
     * <p>Small text (≤2KB) uses the complete hash; large text takes the first 1KB + last 1KB + length to generate the hash,
     * avoiding traversing the entire text. Returns 16-bit hex.
     *
     * @param content The content to calculate the hash for
     * @return 16-bit hex hash, returns null if the content is null
     */
    public static String sampledSha256(String content) {
        if (content == null) {
            return null;
        }
        if (content.length() <= SAMPLE_THRESHOLD) {
            return sha256(content).substring(0, 16);
        }
        String sample =
                content.substring(0, SAMPLE_SIZE)
                        + "|"
                        + content.length()
                        + "|"
                        + content.substring(content.length() - SAMPLE_SIZE);
        return sha256(sample).substring(0, 16);
    }

    private static String normalizeContent(String content) {
        return content.strip().toLowerCase().replaceAll("\\s+", " ");
    }

    private static String bytesToHex(byte[] bytes) {
        var sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
