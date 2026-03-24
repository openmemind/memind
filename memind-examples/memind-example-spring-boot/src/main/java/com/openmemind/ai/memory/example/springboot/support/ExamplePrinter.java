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
package com.openmemind.ai.memory.example.springboot.support;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.ToolCallStats;
import com.openmemind.ai.memory.core.data.enums.InsightTier;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.extraction.ExtractionResult;
import com.openmemind.ai.memory.core.retrieval.RetrievalResult;
import com.openmemind.ai.memory.core.store.MemoryStore;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Formatted output utility shared by Spring Boot examples.
 */
public final class ExamplePrinter {

    private static final Logger log = LoggerFactory.getLogger(ExamplePrinter.class);

    private ExamplePrinter() {}

    public static void printSection(String title) {
        log.info("");
        log.info("═══════════════════════════════════════════════════════");
        log.info("  {}", title);
        log.info("═══════════════════════════════════════════════════════");
    }

    public static void printExtractionResult(ExtractionResult result) {
        log.info("  status       : {}", result.status());
        log.info("  duration     : {}ms", result.duration().toMillis());

        var rawResult = result.rawDataResult();
        if (rawResult != null) {
            log.info("  segments     : {}", rawResult.segments().size());
            if (!rawResult.segments().isEmpty()) {
                log.info("  ── RawData Captions ──");
                for (int i = 0; i < rawResult.segments().size(); i++) {
                    log.info("    [{}] {}", i + 1, rawResult.segments().get(i).caption());
                }
            }
        }

        log.info("  memory items : {}", result.totalMemoryItems());
        log.info("  insights     : {}", result.totalInsights());

        var itemResult = result.memoryItemResult();
        if (itemResult != null && !itemResult.newItems().isEmpty()) {
            log.info("  ── Memory Items ──");
            for (var item : itemResult.newItems()) {
                log.info("    [{}|{}] {}", item.category(), item.type(), item.content());
            }
        }
    }

    public static void printMemoryItems(List<MemoryItem> items) {
        var facts = items.stream().filter(i -> i.type() == MemoryItemType.FACT).toList();
        var foresights = items.stream().filter(i -> i.type() == MemoryItemType.FORESIGHT).toList();

        if (!facts.isEmpty()) {
            log.info("  ── FACT Items ({}) ──", facts.size());
            for (int i = 0; i < facts.size(); i++) {
                var item = facts.get(i);
                log.info("    {}. [{}] {}", i + 1, item.category(), item.content());
            }
        }

        if (!foresights.isEmpty()) {
            log.info("  ── FORESIGHT Items ({}) ──", foresights.size());
            for (int i = 0; i < foresights.size(); i++) {
                var item = foresights.get(i);
                log.info("    {}. {}", i + 1, item.content());
                var meta = item.metadata();
                if (meta != null) {
                    if (meta.containsKey("evidence")) {
                        log.info("       Evidence: {}", meta.get("evidence"));
                    }
                    if (meta.containsKey("validUntil")) {
                        log.info("       ValidUntil: {}", meta.get("validUntil"));
                    }
                    if (meta.containsKey("durationDays")) {
                        log.info("       DurationDays: {}", meta.get("durationDays"));
                    }
                }
            }
        }
    }

    public static void printRetrievalResult(RetrievalResult result, long durationMs) {
        log.info("  duration : {}ms", durationMs);
        log.info("  strategy : {}", result.strategy());
        log.info("  items    : {}", result.items().size());
        log.info("  insights : {}", result.insights().size());

        result.items().forEach(i -> log.info("    [item  score={}] {}", i.finalScore(), i.text()));
        result.insights().forEach(i -> log.info("    [insight tier={}] {}", i.tier(), i.text()));
    }

    public static void printToolStats(String toolName, ToolCallStats stats) {
        log.info(
                "    [{}] calls={}, successRate={}, avgTime={}ms",
                toolName,
                stats.totalCalls(),
                stats.successRate(),
                stats.avgTimeCost());
    }

    public static void printAllToolStats(Map<String, ToolCallStats> allStats) {
        log.info("  ── Tool Statistics ({} tools) ──", allStats.size());
        allStats.forEach(ExamplePrinter::printToolStats);
    }

    public static void printInsightTree(MemoryStore store, MemoryId memoryId) {
        var allInsights = store.insightOperations().listInsights(memoryId);
        if (allInsights.isEmpty()) {
            log.info("  (no insights built yet)");
            return;
        }

        var byType = allInsights.stream().collect(Collectors.groupingBy(MemoryInsight::type));
        byType.forEach(
                (type, insights) -> {
                    var branches =
                            insights.stream().filter(i -> i.tier() == InsightTier.BRANCH).toList();
                    var leafs =
                            insights.stream().filter(i -> i.tier() == InsightTier.LEAF).toList();
                    var roots =
                            insights.stream().filter(i -> i.tier() == InsightTier.ROOT).toList();

                    if (!branches.isEmpty()) {
                        for (var branch : branches) {
                            log.info("");
                            log.info("  {} (BRANCH) [confidence: {}]", type, branch.confidence());
                            printInsightPoints(branch, "  ");

                            var childIds = branch.childInsightIds();
                            for (var leaf : leafs) {
                                if (childIds != null && childIds.contains(leaf.id())) {
                                    log.info(
                                            "    ├── LEAF: \"{}\" [{} points]",
                                            leaf.group() != null ? leaf.group() : leaf.name(),
                                            leaf.points().size());
                                    printInsightPoints(leaf, "    │   ");
                                }
                            }
                        }
                    }

                    if (!roots.isEmpty()) {
                        for (var root : roots) {
                            log.info("");
                            log.info("  {} (ROOT) [confidence: {}]", type, root.confidence());
                            printInsightPoints(root, "  ");
                        }
                    }
                });
    }

    private static void printInsightPoints(MemoryInsight insight, String indent) {
        for (var point : insight.points()) {
            log.info("{}  [{}] {} ({})", indent, point.type(), point.content(), point.confidence());
        }
    }
}
