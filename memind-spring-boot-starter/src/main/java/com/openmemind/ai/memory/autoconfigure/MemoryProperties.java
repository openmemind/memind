package com.openmemind.ai.memory.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * memind configuration properties
 *
 */
@ConfigurationProperties(prefix = "memind")
public class MemoryProperties {

    private Vector vector = new Vector();

    public Vector getVector() {
        return vector;
    }

    public void setVector(Vector vector) {
        this.vector = vector;
    }

    public static class Vector {
        private String storePath = "./data/vector-store.json";

        public String getStorePath() {
            return storePath;
        }

        public void setStorePath(String storePath) {
            this.storePath = storePath;
        }
    }
}
