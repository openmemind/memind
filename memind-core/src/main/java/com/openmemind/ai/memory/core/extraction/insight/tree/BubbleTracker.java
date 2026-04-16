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
package com.openmemind.ai.memory.core.extraction.insight.tree;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Delayed Bubble Tracker
 *
 * <p>Tracks the dirtyCount of Branch/Root, triggers re-summarize when the update count of child nodes reaches the threshold.
 */
public class BubbleTracker implements BubbleTrackerStore {

    private final Map<String, AtomicInteger> dirtyCounts = new ConcurrentHashMap<>();

    @Override
    public int incrementAndGet(String key, int delta) {
        return dirtyCounts.computeIfAbsent(key, ignored -> new AtomicInteger()).addAndGet(delta);
    }

    /** Gets the current dirty count */
    @Override
    public int getDirtyCount(String key) {
        var counter = dirtyCounts.get(key);
        return counter == null ? 0 : counter.get();
    }

    /** Resets the dirty count after re-summarize */
    @Override
    public void reset(String key) {
        dirtyCounts.computeIfAbsent(key, ignored -> new AtomicInteger()).set(0);
    }
}
