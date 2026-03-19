package com.openmemind.ai.memory.core.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Vector math utility class
 *
 */
public final class VectorMath {

    private VectorMath() {}

    /**
     * Cosine similarity, returns 0.0 for invalid input
     */
    public static double cosineSimilarity(List<Float> a, List<Float> b) {
        if (a == null || b == null || a.size() != b.size() || a.isEmpty()) return 0.0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.size(); i++) {
            float ai = a.get(i), bi = b.get(i);
            dot += ai * bi;
            normA += ai * ai;
            normB += bi * bi;
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0 ? 0.0 : dot / denom;
    }

    /**
     * Vector element-wise addition: a + b
     */
    public static List<Float> add(List<Float> a, List<Float> b) {
        if (a == null || b == null || a.size() != b.size()) return a;
        var result = new ArrayList<Float>(a.size());
        for (int i = 0; i < a.size(); i++) {
            result.add(a.get(i) + b.get(i));
        }
        return result;
    }

    /**
     * Vector scalar division: v / scalar
     */
    public static List<Float> divide(List<Float> v, float scalar) {
        if (v == null || scalar == 0) return v;
        var result = new ArrayList<Float>(v.size());
        for (int i = 0; i < v.size(); i++) {
            result.add(v.get(i) / scalar);
        }
        return result;
    }
}
