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
package com.openmemind.ai.memory.core.builder;

/**
 * Deterministic caps and thresholds for memory-thread derivation.
 */
public record MemoryThreadRuleOptions(
        double matchThreshold,
        double newThreadThreshold,
        int maxCandidateThreads,
        int maxMembersPerThread,
        int maxRetrievalMembersPerThread) {

    public MemoryThreadRuleOptions {
        if (Double.isNaN(matchThreshold) || matchThreshold < 0.0d || matchThreshold > 1.0d) {
            throw new IllegalArgumentException("matchThreshold must be in [0,1]");
        }
        if (Double.isNaN(newThreadThreshold)
                || newThreadThreshold < 0.0d
                || newThreadThreshold > 1.0d) {
            throw new IllegalArgumentException("newThreadThreshold must be in [0,1]");
        }
        if (maxCandidateThreads <= 0) {
            throw new IllegalArgumentException("maxCandidateThreads must be positive");
        }
        if (maxMembersPerThread <= 0) {
            throw new IllegalArgumentException("maxMembersPerThread must be positive");
        }
        if (maxRetrievalMembersPerThread <= 0) {
            throw new IllegalArgumentException("maxRetrievalMembersPerThread must be positive");
        }
        if (maxRetrievalMembersPerThread > maxMembersPerThread) {
            throw new IllegalArgumentException(
                    "maxRetrievalMembersPerThread must not exceed maxMembersPerThread");
        }
    }

    public static MemoryThreadRuleOptions defaults() {
        return new MemoryThreadRuleOptions(0.78d, 0.70d, 4, 32, 6);
    }

    public MemoryThreadRuleOptions withMatchThreshold(double matchThreshold) {
        return new MemoryThreadRuleOptions(
                matchThreshold,
                newThreadThreshold,
                maxCandidateThreads,
                maxMembersPerThread,
                maxRetrievalMembersPerThread);
    }

    public MemoryThreadRuleOptions withNewThreadThreshold(double newThreadThreshold) {
        return new MemoryThreadRuleOptions(
                matchThreshold,
                newThreadThreshold,
                maxCandidateThreads,
                maxMembersPerThread,
                maxRetrievalMembersPerThread);
    }

    public MemoryThreadRuleOptions withMaxCandidateThreads(int maxCandidateThreads) {
        return new MemoryThreadRuleOptions(
                matchThreshold,
                newThreadThreshold,
                maxCandidateThreads,
                maxMembersPerThread,
                maxRetrievalMembersPerThread);
    }

    public MemoryThreadRuleOptions withMaxMembersPerThread(int maxMembersPerThread) {
        return new MemoryThreadRuleOptions(
                matchThreshold,
                newThreadThreshold,
                maxCandidateThreads,
                maxMembersPerThread,
                maxRetrievalMembersPerThread);
    }

    public MemoryThreadRuleOptions withMaxRetrievalMembersPerThread(
            int maxRetrievalMembersPerThread) {
        return new MemoryThreadRuleOptions(
                matchThreshold,
                newThreadThreshold,
                maxCandidateThreads,
                maxMembersPerThread,
                maxRetrievalMembersPerThread);
    }
}
