# TokenKiller Compression Engine — Design Spec

## Overview

TokenKiller is a general-purpose context compression engine for memind-core. It reduces token consumption by 60-90% through intelligent filtering of text content (command outputs, code files, structured data, logs) before they reach LLMs.

TokenKiller sits alongside the Memory engine as a peer capability in memind-core, sharing infrastructure (StructuredChatClient, TokenUtils) but with no mutual dependency.

### Design Goals

- **Budget-aware**: Support token budget constraints, not just best-effort compression
- **Extensible**: Four-layer extension model (YAML rules → Builder → SPI → Spring Boot)
- **Robust**: Three-layer fault tolerance — never break the caller's workflow
- **Consistent**: Follow memind's existing patterns (record, Mono, Builder, factory methods)

### Reference

Core compression strategies are informed by [RTK](https://github.com/cased/rtk) (Rust Token Killer), adapted from CLI tool form to Java SDK form. The mapping between RTK modules and TokenKiller components is documented in the [RTK Capability Mapping](#rtk-capability-mapping) section.

---

## Architecture

```
com.openmemind.ai.memory.core.compress
│
├─ TokenKiller.java                        (facade)
├─ TokenKillerBuilder.java                 (builder interface)
├─ DefaultTokenKillerBuilder.java          (builder implementation)
├─ CompressConfig.java                     (global configuration)
│
├─ model/
│   ├─ CompressRequest.java                (single-block input)
│   ├─ CompressResult.java                 (single-block output)
│   ├─ CompressLevel.java                  (LIGHT / STANDARD / AGGRESSIVE)
│   ├─ CompressContext.java                (strategy execution context)
│   ├─ BudgetCompressRequest.java          (multi-block + budget input)
│   ├─ BudgetCompressResult.java           (multi-block output + block traces)
│   ├─ CompressBlock.java                  (block definition)
│   └─ BlockPriority.java                  (CRITICAL / HIGH / NORMAL / LOW)
│
├─ strategy/
│   ├─ CompressStrategy.java               (strategy interface + builder factory)
│   ├─ CompressAtom.java                   (atomic transformation, composable)
│   ├─ Atoms.java                          (built-in atom factory)
│   ├─ BuiltinStrategies.java             (built-in strategy registry)
│   ├─ StrategyRouter.java                (layered routing)
│   ├─ LanguageDetector.java              (internal utility for codeFold atom)
│   ├─ PassthroughStrategy.java
│   ├─ LlmSummarizeStrategy.java          (LLM fallback with cost gate)
│   └─ declarative/
│       ├─ RuleDefinition.java             (declarative rule model)
│       ├─ RuleParser.java                 (YAML parsing)
│       └─ RuleToStrategyConverter.java    (YAML → CompressStrategy)
│
├─ detect/
│   ├─ ContentDetector.java                (detection interface)
│   ├─ CompositeDetector.java              (detection chain)
│   └─ SignatureDetector.java              (signature-based fast matching)
│
├─ token/
│   ├─ TokenEstimator.java                (estimation interface)
│   └─ JtiktokenEstimator.java            (default: wraps existing TokenUtils)
│
├─ tee/
│   └─ CompressTee.java                   (original output preservation)
│
├─ tracking/
│   ├─ CompressTracker.java               (tracking interface)
│   ├─ CompressRecord.java                (tracking record)
│   └─ CompressStats.java                 (aggregated statistics)
│
└─ pipeline/
    ├─ CompressPipeline.java              (single-block execution)
    └─ BudgetCompressPipeline.java        (multi-block budget orchestration)
```

---

## Core Interfaces

### TokenKiller (Facade)

```java
public interface TokenKiller {

    /**
     * Compress a single piece of content.
     */
    Mono<CompressResult> compress(CompressRequest request);

    /**
     * Convenience: compress with auto-detection.
     */
    default Mono<CompressResult> compress(String content) {
        return compress(CompressRequest.of(content));
    }

    /**
     * Convenience: compress with explicit format hint.
     */
    default Mono<CompressResult> compress(String content, String formatHint) {
        return compress(CompressRequest.of(content, formatHint));
    }

    /**
     * Compress multiple blocks under a token budget.
     */
    Mono<BudgetCompressResult> compressWithBudget(BudgetCompressRequest request);

    static TokenKillerBuilder builder() {
        return new DefaultTokenKillerBuilder();
    }

    /**
     * Zero-config creation: loads built-in strategies + SPI + classpath rules.
     */
    static TokenKiller create() {
        return builder().build();
    }
}
```

### CompressAtom (Atomic Transformation)

```java
@FunctionalInterface
public interface CompressAtom {

    String apply(String content, CompressContext context);

    default CompressAtom andThen(CompressAtom next) {
        return (content, ctx) -> next.apply(this.apply(content, ctx), ctx);
    }

    /**
     * Wraps this atom with error tolerance.
     * On failure, returns the input unchanged — never breaks the chain.
     */
    default CompressAtom safe() {
        return (content, ctx) -> {
            try {
                return this.apply(content, ctx);
            }
            catch (Exception e) {
                return content;
            }
        };
    }
}
```

### CompressStrategy (Named Atom Chain)

```java
public interface CompressStrategy {

    String name();

    /**
     * Whether this strategy can handle the given content.
     * formatHint takes priority over content-based detection.
     */
    boolean supports(String content, String formatHint);

    Mono<CompressResult> compress(String content, CompressContext context);

    /**
     * Builder factory for creating strategies from atom chains.
     * Unifies declarative (YAML) and programmatic approaches.
     */
    static StrategyBuilder named(String name) {
        return new StrategyBuilder(name);
    }

    class StrategyBuilder {
        private final String name;
        private Predicate<String> detectPredicate;
        private String formatHintMatch;
        private final List<CompressAtom> atoms = new ArrayList<>();

        StrategyBuilder(String name) {
            this.name = Objects.requireNonNull(name);
        }

        public StrategyBuilder detect(Predicate<String> predicate) {
            this.detectPredicate = predicate;
            return this;
        }

        public StrategyBuilder formatHint(String hint) {
            this.formatHintMatch = hint;
            return this;
        }

        public StrategyBuilder atoms(CompressAtom... atoms) {
            this.atoms.addAll(List.of(atoms));
            return this;
        }

        public CompressStrategy build() {
            List<CompressAtom> safeAtoms = List.copyOf(this.atoms);
            return new CompressStrategy() {
                @Override
                public String name() { return name; }

                @Override
                public boolean supports(String content, String formatHint) {
                    if (formatHintMatch != null && formatHintMatch.equals(formatHint)) {
                        return true;
                    }
                    return detectPredicate != null && detectPredicate.test(content);
                }

                @Override
                public Mono<CompressResult> compress(String content, CompressContext ctx) {
                    return Mono.fromCallable(() -> {
                        CompressAtom pipeline = safeAtoms.stream()
                                .map(CompressAtom::safe)
                                .reduce(CompressAtom::andThen)
                                .orElse((c, x) -> c);
                        String compressed = pipeline.apply(content, ctx);
                        return CompressResult.of(content, compressed, name, ctx);
                    });
                }
            };
        }
    }
}
```

---

## Data Models

### CompressRequest (Single-Block Input)

```java
public record CompressRequest(
    String content,
    String formatHint,
    CompressLevel level,
    Map<String, Object> hints
) {
    public static CompressRequest of(String content) {
        return new CompressRequest(content, null, CompressLevel.STANDARD, Map.of());
    }

    public static CompressRequest of(String content, String formatHint) {
        return new CompressRequest(content, formatHint, CompressLevel.STANDARD, Map.of());
    }

    public CompressRequest withLevel(CompressLevel level) {
        return new CompressRequest(content, formatHint, level, hints);
    }

    public CompressRequest withHint(String key, Object value) {
        var newHints = new HashMap<>(hints);
        newHints.put(key, value);
        return new CompressRequest(content, formatHint, level, Map.copyOf(newHints));
    }
}
```

### CompressResult (Single-Block Output)

```java
public record CompressResult(
    String compressed,
    int originalTokens,
    int compressedTokens,
    String formatDetected,
    String strategyUsed,
    Duration duration
) {
    public int savedTokens() {
        return originalTokens - compressedTokens;
    }

    public double savingsPercent() {
        return originalTokens == 0 ? 0.0 : (double) savedTokens() / originalTokens * 100;
    }

    public static CompressResult of(String original, String compressed,
            String strategyName, CompressContext ctx) {
        return new CompressResult(
                compressed,
                ctx.estimateTokens(original),
                ctx.estimateTokens(compressed),
                null, strategyName, Duration.ZERO);
    }

    public static CompressResult passthrough(String content, int tokens) {
        return new CompressResult(content, tokens, tokens, null, "passthrough", Duration.ZERO);
    }
}
```

### CompressLevel

```java
public enum CompressLevel {
    LIGHT(0.3),
    STANDARD(0.6),
    AGGRESSIVE(0.85);

    private final double targetRatio;

    CompressLevel(double targetRatio) {
        this.targetRatio = targetRatio;
    }

    /**
     * Target compression ratio. Atoms can use this to adjust behavior.
     * E.g., LIGHT(0.3) aims to remove ~30% of tokens, keeping ~70%.
     * AGGRESSIVE(0.85) aims to remove ~85% of tokens, keeping only ~15%.
     */
    public double targetRatio() {
        return targetRatio;
    }
}
```

### CompressContext

```java
public record CompressContext(
    TokenEstimator estimator,
    CompressLevel level,
    Map<String, Object> hints
) {
    public int estimateTokens(String text) {
        return estimator.estimate(text);
    }
}
```

### CompressConfig

```java
public record CompressConfig(
    int minTokenThreshold,
    CompressLevel defaultLevel,
    boolean enableTee,
    boolean enableTracking,
    int llmCostThreshold
) {
    public static CompressConfig defaults() {
        return new CompressConfig(50, CompressLevel.STANDARD, false, false, 500);
    }

    public CompressConfig withMinTokenThreshold(int minTokenThreshold) {
        return new CompressConfig(minTokenThreshold, defaultLevel, enableTee, enableTracking, llmCostThreshold);
    }

    public CompressConfig withDefaultLevel(CompressLevel defaultLevel) {
        return new CompressConfig(minTokenThreshold, defaultLevel, enableTee, enableTracking, llmCostThreshold);
    }

    public CompressConfig withEnableTee(boolean enableTee) {
        return new CompressConfig(minTokenThreshold, defaultLevel, enableTee, enableTracking, llmCostThreshold);
    }

    public CompressConfig withEnableTracking(boolean enableTracking) {
        return new CompressConfig(minTokenThreshold, defaultLevel, enableTee, enableTracking, llmCostThreshold);
    }

    public CompressConfig withLlmCostThreshold(int llmCostThreshold) {
        return new CompressConfig(minTokenThreshold, defaultLevel, enableTee, enableTracking, llmCostThreshold);
    }
}
```

### BudgetCompressRequest (Multi-Block + Budget)

```java
public record BudgetCompressRequest(
    List<CompressBlock> blocks,
    int targetTokens
) {
    public static BudgetCompressRequest of(List<CompressBlock> blocks, int targetTokens) {
        Objects.requireNonNull(blocks);
        if (targetTokens <= 0) {
            throw new IllegalArgumentException("targetTokens must be positive");
        }
        return new BudgetCompressRequest(List.copyOf(blocks), targetTokens);
    }
}
```

### CompressBlock

```java
public record CompressBlock(
    String id,
    String content,
    String formatHint,
    BlockPriority priority,
    Map<String, Object> hints
) {
    private static final AtomicLong ID_SEQ = new AtomicLong();

    private static String generateId() {
        return "block-" + ID_SEQ.incrementAndGet();
    }

    public static CompressBlock of(String content) {
        return new CompressBlock(generateId(), content, null, BlockPriority.NORMAL, Map.of());
    }

    public static CompressBlock of(String content, String formatHint) {
        return new CompressBlock(generateId(), content, formatHint, BlockPriority.NORMAL, Map.of());
    }

    public static CompressBlock of(String content, String formatHint, BlockPriority priority) {
        return new CompressBlock(generateId(), content, formatHint, priority, Map.of());
    }
}
```

### BlockPriority

```java
public enum BlockPriority {
    CRITICAL,   // Never compress, truncate, or drop
    HIGH,       // May compress, never truncate or drop
    NORMAL,     // May compress and truncate
    LOW         // May compress, truncate, or drop
}
```

### BudgetCompressResult (Multi-Block Output)

```java
public record BudgetCompressResult(
    String merged,
    int originalTokens,
    int compressedTokens,
    int targetTokens,
    List<BlockTrace> blockTraces,
    Duration duration
) {
    public int savedTokens() {
        return originalTokens - compressedTokens;
    }

    public double savingsPercent() {
        return originalTokens == 0 ? 0.0 : (double) savedTokens() / originalTokens * 100;
    }

    public boolean withinBudget() {
        return compressedTokens <= targetTokens;
    }

    public record BlockTrace(
        String blockId,
        String formatDetected,
        String strategyUsed,
        int originalTokens,
        int compressedTokens,
        String compressed,
        String action   // "compressed", "truncated", "dropped"
    ) {}
}
```

---

## Strategy Layer

### Strategy Router

Layered priority routing. Immutable after construction — all registration happens during `build()`.

```java
public class StrategyRouter {

    private final List<CompressStrategy> userStrategies;
    private final List<CompressStrategy> spiStrategies;
    private final List<CompressStrategy> declarativeStrategies;
    private final List<CompressStrategy> builtinStrategies;
    private final CompressStrategy fallback;

    public StrategyRouter(
            List<CompressStrategy> userStrategies,
            List<CompressStrategy> spiStrategies,
            List<CompressStrategy> declarativeStrategies,
            List<CompressStrategy> builtinStrategies,
            CompressStrategy fallback) {
        this.userStrategies = List.copyOf(userStrategies);
        this.spiStrategies = List.copyOf(spiStrategies);
        this.declarativeStrategies = List.copyOf(declarativeStrategies);
        this.builtinStrategies = List.copyOf(builtinStrategies);
        this.fallback = Objects.requireNonNull(fallback);
    }

    public CompressStrategy route(String content, String formatHint) {
        return findIn(userStrategies, content, formatHint)
                .or(() -> findIn(spiStrategies, content, formatHint))
                .or(() -> findIn(declarativeStrategies, content, formatHint))
                .or(() -> findIn(builtinStrategies, content, formatHint))
                .orElse(fallback);
    }

    private Optional<CompressStrategy> findIn(
            List<CompressStrategy> strategies, String content, String formatHint) {
        return strategies.stream()
                .filter(s -> s.supports(content, formatHint))
                .findFirst();
    }
}
```

### Built-in Atoms

```java
public final class Atoms {

    private Atoms() {}

    // === General-purpose atoms (usable across all content types) ===

    /** Remove ANSI escape sequences. */
    public static CompressAtom stripAnsi() { ... }

    /** Remove lines matching the given regex pattern. */
    public static CompressAtom stripLines(String pattern) { ... }

    /** Keep only lines matching the given regex pattern. */
    public static CompressAtom keepLines(String pattern) { ... }

    /** Deduplicate lines: normalize, count occurrences, show unique with counts. */
    public static CompressAtom dedup() { ... }

    /** Limit output to first N lines. */
    public static CompressAtom maxLines(int limit) { ... }

    /** Keep first N lines. */
    public static CompressAtom headLines(int n) { ... }

    /** Keep last N lines. */
    public static CompressAtom tailLines(int n) { ... }

    /** Truncate each line at maxChars characters. */
    public static CompressAtom truncateLineAt(int maxChars) { ... }

    /** Regex replace across all lines. */
    public static CompressAtom replace(String pattern, String replacement) { ... }

    // === Specialized atoms ===

    /**
     * Group lines by a regex pattern and count occurrences.
     * E.g., lint output grouped by rule name.
     */
    public static CompressAtom patternGroup(String groupPattern) { ... }

    /**
     * Extract only error/failure lines.
     * Recognizes common patterns: "error", "FAILED", "Error:", etc.
     */
    public static CompressAtom errorExtract() { ... }

    /**
     * Language-aware code folding.
     * LIGHT: strip comments only.
     * STANDARD: strip comments + fold long methods.
     * AGGRESSIVE: signatures only.
     */
    public static CompressAtom codeFold() { ... }

    /**
     * Extract JSON structure (keys + types), discard values.
     * E.g., {"name": "John", "age": 30} → {"name": "string", "age": "int"}
     */
    public static CompressAtom jsonSchema() { ... }

    /**
     * Compact JSON: truncate long strings, summarize large arrays.
     */
    public static CompressAtom jsonCompact(int maxDepth) { ... }

    /**
     * Normalize log lines: replace timestamps, UUIDs, hex values, paths
     * with placeholders, then deduplicate.
     */
    public static CompressAtom logNormalize() { ... }

    /**
     * Compact directory tree: filter noise dirs (node_modules, .git, etc.),
     * aggregate file counts per directory.
     */
    public static CompressAtom treeCompact() { ... }

    /**
     * Produce a statistical summary: line counts, file counts, etc.
     * Used for git status, git log.
     */
    public static CompressAtom statsSummary() { ... }

    /**
     * Parse NDJSON stream (one JSON object per line), aggregate results.
     * Used for go test -json output.
     */
    public static CompressAtom ndjsonAggregate() { ... }

    /**
     * State machine parser for test output (pytest, minitest).
     * Tracks test lifecycle, extracts only failures.
     */
    public static CompressAtom stateMachineParse(StateMachineConfig config) { ... }
}
```

### Built-in Strategies

Each strategy is a named atom chain. Atoms adjust behavior based on `CompressLevel` from context.

| Strategy Name | Format Hint | Atom Chain | Maps to RTK |
|---------------|-------------|------------|-------------|
| `git-status` | `git-status` | stripAnsi → statsSummary | git.rs (status) |
| `git-log` | `git-log` | stripAnsi → statsSummary | git.rs (log) |
| `git-diff` | `git-diff` | stripAnsi → statsSummary | git.rs (diff) |
| `lint` | `lint` | stripAnsi → patternGroup | lint_cmd.rs |
| `test` | `test` | stripAnsi → errorExtract → maxLines | runner.rs |
| `pytest` | `pytest` | stripAnsi → stateMachineParse → errorExtract | pytest_cmd.rs |
| `vitest` | `vitest` | stripAnsi → jsonCompact → errorExtract | vitest_cmd.rs |
| `go-test` | `go-test` | ndjsonAggregate → errorExtract | go_cmd.rs |
| `build` | `build` | stripAnsi → errorExtract → maxLines | runner.rs |
| `json` | `json` | jsonSchema or jsonCompact | json_cmd.rs |
| `ndjson` | `ndjson` | ndjsonAggregate | go_cmd.rs |
| `log` | `log` | logNormalize → dedup → maxLines | log_cmd.rs |
| `directory` | `directory` | treeCompact | ls.rs |
| `code` | `code` | codeFold | read.rs + filter.rs |

Example registration:

```java
CompressStrategy.named("git-status")
    .formatHint("git-status")
    .detect(content -> content.contains("On branch")
            || content.contains("Changes not staged"))
    .atoms(Atoms.stripAnsi(), Atoms.statsSummary())
    .build();
```

### LLM Fallback Strategy

LLM summarization is **not** an unconditional fallback. It has a strict cost gate:

```java
public class LlmSummarizeStrategy implements CompressStrategy {

    private final StructuredChatClient chatClient;
    private final int costThreshold;  // from CompressConfig.llmCostThreshold

    @Override
    public String name() { return "llm-summarize"; }

    @Override
    public boolean supports(String content, String formatHint) {
        return false;  // Never auto-matches; only used as explicit fallback
    }

    @Override
    public Mono<CompressResult> compress(String content, CompressContext ctx) {
        int originalTokens = ctx.estimateTokens(content);

        // Cost gate: only invoke LLM when savings justify the cost
        if (originalTokens < costThreshold) {
            return Mono.just(CompressResult.passthrough(content, originalTokens));
        }

        int estimatedLlmCost = 200;
        int estimatedSaving = (int) (originalTokens * 0.6);
        if (estimatedSaving < estimatedLlmCost * 2) {
            return Mono.just(CompressResult.passthrough(content, originalTokens));
        }

        return chatClient.call(List.of(
                new ChatMessage(ChatRole.SYSTEM, SUMMARIZE_PROMPT),
                new ChatMessage(ChatRole.USER, content)
        ))
        .filter(summary -> summary != null && !summary.isBlank())
        .map(summary -> CompressResult.of(content, summary, name(), ctx))
        .switchIfEmpty(Mono.just(CompressResult.passthrough(content, originalTokens)));
    }
}
```

---

## Declarative Rule Engine

Zero-code extension via YAML rule files. Rules are converted to `CompressStrategy` instances through the same builder path as programmatic strategies.

### Rule Definition Model

```java
public record RuleDefinition(
    String name,
    String description,
    DetectRule detect,
    List<FilterStep> steps,
    String onEmpty
) {
    public record DetectRule(
        String matchCommand,
        String startsWith,
        String contains,
        String pattern
    ) {}

    /**
     * Filter steps map 1:1 to RTK's TOML filter pipeline (8 stages).
     * Sealed interface ensures compile-time exhaustiveness.
     */
    public sealed interface FilterStep {
        record StripAnsi() implements FilterStep {}
        record Replace(String pattern, String replacement) implements FilterStep {}
        record MatchOutput(String pattern, String unless, String message) implements FilterStep {}
        record StripLines(String pattern) implements FilterStep {}
        record KeepLines(String pattern) implements FilterStep {}
        record TruncateLineAt(int maxChars) implements FilterStep {}
        record HeadLines(int count) implements FilterStep {}
        record TailLines(int count) implements FilterStep {}
        record MaxLines(int count) implements FilterStep {}
    }
}
```

### YAML Format

```yaml
# classpath:compress-rules/docker.yml
docker-build:
  description: "Docker build output"
  detect:
    startsWith: "Sending build context"
  steps:
    - stripAnsi: true
    - stripLines: "^\\s+Compiling"
    - stripLines: "^\\s+Downloading"
    - keepLines: "^(Step|Successfully|ERROR|Error)"
    - maxLines: 30
  onEmpty: "docker build: ok"

mvn-install:
  description: "Maven build output"
  detect:
    contains: "[INFO] Building"
  steps:
    - stripAnsi: true
    - stripLines: "^\\[INFO\\] Downloading"
    - matchOutput:
        pattern: "BUILD SUCCESS"
        message: "mvn: BUILD SUCCESS"
    - keepLines: "^\\[(ERROR|WARNING)\\]"
    - maxLines: 40
  onEmpty: "mvn: ok"
```

### Conversion: FilterStep → CompressAtom

```java
public class RuleToStrategyConverter {

    public CompressStrategy convert(RuleDefinition rule) {
        List<CompressAtom> atoms = rule.steps().stream()
                .map(this::toAtom)
                .toList();

        StrategyBuilder builder = CompressStrategy.named(rule.name());

        // Set up detection
        DetectRule detect = rule.detect();
        if (detect.startsWith() != null) {
            builder.detect(content -> content.startsWith(detect.startsWith()));
        } else if (detect.contains() != null) {
            builder.detect(content -> content.contains(detect.contains()));
        } else if (detect.pattern() != null) {
            Pattern p = Pattern.compile(detect.pattern());
            builder.detect(content -> p.matcher(content).find());
        }

        return builder.atoms(atoms.toArray(CompressAtom[]::new)).build();
    }

    private CompressAtom toAtom(FilterStep step) {
        return switch (step) {
            case StripAnsi() -> Atoms.stripAnsi();
            case Replace(var p, var r) -> Atoms.replace(p, r);
            case MatchOutput(var p, var u, var msg) -> matchOutputAtom(p, u, msg);
            case StripLines(var p) -> Atoms.stripLines(p);
            case KeepLines(var p) -> Atoms.keepLines(p);
            case TruncateLineAt(var max) -> Atoms.truncateLineAt(max);
            case HeadLines(var n) -> Atoms.headLines(n);
            case TailLines(var n) -> Atoms.tailLines(n);
            case MaxLines(var n) -> Atoms.maxLines(n);
        };
    }

    private CompressAtom matchOutputAtom(String pattern, String unless, String message) {
        Pattern p = Pattern.compile(pattern);
        Pattern u = unless != null ? Pattern.compile(unless) : null;
        return (content, ctx) -> {
            if (p.matcher(content).find()
                    && (u == null || !u.matcher(content).find())) {
                return message;
            }
            return content;
        };
    }
}
```

### Loading Priority

1. User-registered rules via `TokenKillerBuilder.rules(path)` — highest priority
2. `classpath:compress-rules/*.yml` — built-in rules shipped in the jar
3. SPI-discovered `CompressStrategy` implementations
4. Built-in programmatic strategies

---

## Content Detection

Auto-detection is a **fallback** mechanism. Callers should provide `formatHint` when possible.

```java
public interface ContentDetector {
    /** Returns a format hint string, or null if unrecognized. */
    String detect(String content);
}

public class CompositeDetector implements ContentDetector {
    private final List<ContentDetector> detectors;

    @Override
    public String detect(String content) {
        return detectors.stream()
                .map(d -> d.detect(content))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
```

### SignatureDetector

Matches content against known signatures using the first 10 lines:

| Format Hint | Signature |
|-------------|-----------|
| `git-status` | Contains "On branch" or "Changes not staged" or "Untracked files" |
| `git-diff` | Starts with "diff --git" |
| `git-log` | Lines match `^[a-f0-9]{7,40}\s` |
| `test` | Contains "PASSED", "FAILED", or "test result:" |
| `lint` | Lines match `.*:\d+:\d+:.*` |
| `json` | Trimmed content starts with `{` or `[` |
| `ndjson` | First 3 lines all start with `{` |
| `log` | Lines match `^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}` |
| `directory` | Lines match `^[drwx-]{10}\s` or `^total\s+\d+` |

### LanguageDetector

Internal utility used by the `codeFold` atom for language-aware code filtering. Not a `ContentDetector` — does not participate in top-level format detection.

```java
public class LanguageDetector {
    public enum Language {
        JAVA, PYTHON, JAVASCRIPT, TYPESCRIPT, GO, RUST,
        C, CPP, RUBY, SHELL, KOTLIN, SCALA, UNKNOWN
    }

    public Language detect(String content, Map<String, Object> hints) {
        // 1. Check hints for file extension
        String ext = (String) hints.get("fileExtension");
        if (ext != null) return fromExtension(ext);

        // 2. Content heuristics (shebang, keywords)
        return fromContent(content);
    }

    /**
     * Comment patterns per language.
     * Maps to RTK filter.rs CommentPatterns.
     */
    public CommentPatterns commentPatterns(Language lang) { ... }
}
```

---

## Token Estimation

```java
public interface TokenEstimator {
    int estimate(String text);
}

/**
 * Default implementation wrapping memind's existing TokenUtils (jtokkit CL100K_BASE).
 * More accurate than RTK's chars/4 heuristic.
 */
public class JtiktokenEstimator implements TokenEstimator {
    @Override
    public int estimate(String text) {
        if (text == null || text.isEmpty()) return 0;
        return TokenUtils.countTokens(text);
    }
}
```

---

## Tee System (Original Output Preservation)

Maps to RTK's `tee.rs`. Preserves original content for recovery when compression is too aggressive.

```java
public class CompressTee {
    private final Path teeDir;
    private final int maxFiles;       // default 20
    private final long maxSizeBytes;  // default 1MB per file

    /**
     * Save original content. Returns file path if saved, empty if content too small.
     */
    public Optional<Path> save(String originalContent, String label) {
        if (originalContent.length() < 500) return Optional.empty();
        Path file = teeDir.resolve(
                Instant.now().toEpochMilli() + "_" + sanitize(label) + ".log");
        Files.writeString(file, originalContent);
        rotate();
        return Optional.of(file);
    }

    public String appendHint(String compressed, Path teePath) {
        return compressed + "\n[full output: " + teePath + "]";
    }

    private void rotate() {
        // Keep only the most recent maxFiles files, delete oldest
    }
}
```

---

## Tracking System

Maps to RTK's `tracking.rs` + `gain.rs`. Pluggable — not mandatory.

```java
public interface CompressTracker {
    void record(CompressRecord record);
    CompressStats summary();
    CompressStats summary(String projectPath);
    List<CompressRecord> recent(int limit);
}

public record CompressRecord(
    Instant timestamp,
    String formatHint,
    String strategyUsed,
    int originalTokens,
    int compressedTokens,
    int savedTokens,
    double savingsPercent,
    Duration executionTime,
    String projectPath
) {}

public record CompressStats(
    long totalCompressions,
    long totalTokensSaved,
    double avgSavingsPercent,
    Map<String, Long> topStrategies
) {}
```

---

## Execution Pipelines

### CompressPipeline (Single-Block)

```java
public class CompressPipeline {

    private final ContentDetector detector;
    private final StrategyRouter router;
    private final TokenEstimator estimator;
    private final CompressConfig config;
    private final CompressTee tee;          // nullable
    private final CompressTracker tracker;  // nullable

    public Mono<CompressResult> execute(CompressRequest request) {
        return Mono.defer(() -> {
            long start = System.nanoTime();
            String content = request.content();

            // 1. Pre-check: content too short to compress
            int originalTokens = estimator.estimate(content);
            if (originalTokens < config.minTokenThreshold()) {
                return Mono.just(CompressResult.passthrough(content, originalTokens));
            }

            // 2. Format detection (explicit hint takes priority)
            String format = request.formatHint();
            if (format == null) {
                format = detector.detect(content);
            }

            // 3. Route to strategy
            CompressStrategy strategy = router.route(content, format);

            // 4. Build context
            CompressLevel level = request.level() != null
                    ? request.level() : config.defaultLevel();
            CompressContext ctx = new CompressContext(estimator, level, request.hints());

            // 5. Execute compression
            String detectedFormat = format;
            return strategy.compress(content, ctx)
                    .onErrorResume(e -> {
                        // Strategy failure → passthrough (never break workflow)
                        return Mono.just(
                                CompressResult.passthrough(content, originalTokens));
                    })
                    .map(result -> {
                        // 6. Safety: compressed should not be longer than original
                        if (result.compressedTokens() >= originalTokens) {
                            return CompressResult.passthrough(content, originalTokens);
                        }

                        Duration duration = Duration.ofNanos(System.nanoTime() - start);

                        // 7. Tee (optional)
                        String finalCompressed = result.compressed();
                        if (config.enableTee() && tee != null) {
                            Optional<Path> teePath = tee.save(content, detectedFormat);
                            if (teePath.isPresent()) {
                                finalCompressed = tee.appendHint(
                                        finalCompressed, teePath.get());
                            }
                        }

                        // 8. Tracking (optional)
                        CompressResult finalResult = new CompressResult(
                                finalCompressed, originalTokens,
                                result.compressedTokens(),
                                detectedFormat, strategy.name(), duration);

                        if (config.enableTracking() && tracker != null) {
                            tracker.record(new CompressRecord(
                                    Instant.now(), detectedFormat, strategy.name(),
                                    originalTokens, result.compressedTokens(),
                                    finalResult.savedTokens(),
                                    finalResult.savingsPercent(),
                                    duration, null));
                        }

                        return finalResult;
                    });
        });
    }
}
```

### BudgetCompressPipeline (Multi-Block)

```java
public class BudgetCompressPipeline {

    private final CompressPipeline singlePipeline;
    private final TokenEstimator estimator;

    public Mono<BudgetCompressResult> execute(BudgetCompressRequest request) {
        long start = System.nanoTime();

        return Flux.fromIterable(request.blocks())
                // Parallel compression — blocks are independent
                .flatMap(block -> compressBlock(block), request.blocks().size())
                .collectList()
                .map(traces -> {
                    // Preserve original block order
                    traces.sort(Comparator.comparingInt(t ->
                            indexOfBlock(request.blocks(), t.blockId())));

                    int totalTokens = traces.stream()
                            .mapToInt(BlockTrace::compressedTokens).sum();

                    // Budget allocation if over budget
                    if (totalTokens > request.targetTokens()) {
                        traces = budgetAllocate(
                                traces, request.blocks(), request.targetTokens());
                    }

                    return assembleResult(traces, request, start);
                });
    }

    private Mono<BlockTrace> compressBlock(CompressBlock block) {
        CompressRequest req = new CompressRequest(
                block.content(), block.formatHint(),
                null, block.hints());

        return singlePipeline.execute(req)
                .map(result -> new BlockTrace(
                        block.id(),
                        result.formatDetected(),
                        result.strategyUsed(),
                        result.originalTokens(),
                        result.compressedTokens(),
                        result.compressed(),
                        "compressed"));
    }
}
```

### Budget Allocation Algorithm

Single-pass greedy. CRITICAL blocks are reserved first, remaining budget distributed by priority.

```java
private List<BlockTrace> budgetAllocate(
        List<BlockTrace> traces,
        List<CompressBlock> blocks,
        int targetTokens) {

    Map<String, BlockPriority> priorities = blocks.stream()
            .collect(toMap(CompressBlock::id, CompressBlock::priority));

    // Step 1: Reserve CRITICAL blocks (untouchable)
    int reserved = traces.stream()
            .filter(t -> priorities.get(t.blockId()) == BlockPriority.CRITICAL)
            .mapToInt(BlockTrace::compressedTokens)
            .sum();

    int available = targetTokens - reserved;
    if (available <= 0) {
        // Budget can't even fit CRITICAL blocks — keep only CRITICAL
        return traces.stream()
                .map(t -> priorities.get(t.blockId()) == BlockPriority.CRITICAL
                        ? t : drop(t))
                .toList();
    }

    // Step 2: Distribute remaining budget by priority (HIGH first)
    List<BlockTrace> nonCritical = traces.stream()
            .filter(t -> priorities.get(t.blockId()) != BlockPriority.CRITICAL)
            .sorted(comparingInt(t -> priorities.get(t.blockId()).ordinal()))
            .toList();

    List<BlockTrace> allocated = new ArrayList<>();
    int remaining = available;

    for (BlockTrace trace : nonCritical) {
        BlockPriority priority = priorities.get(trace.blockId());

        if (remaining <= 0) {
            allocated.add(priority == BlockPriority.LOW
                    ? drop(trace) : truncate(trace, 20));
            remaining -= (priority == BlockPriority.LOW ? 0 : 20);
        } else if (trace.compressedTokens() <= remaining) {
            allocated.add(trace);
            remaining -= trace.compressedTokens();
        } else {
            if (priority == BlockPriority.LOW && remaining < 20) {
                allocated.add(drop(trace));
            } else {
                allocated.add(truncate(trace, remaining));
                remaining = 0;
            }
        }
    }

    // Step 3: Reassemble in original order
    Map<String, BlockTrace> allocatedMap = allocated.stream()
            .collect(toMap(BlockTrace::blockId, t -> t));

    return traces.stream()
            .map(t -> priorities.get(t.blockId()) == BlockPriority.CRITICAL
                    ? t : allocatedMap.getOrDefault(t.blockId(), drop(t)))
            .toList();
}

private BlockTrace drop(BlockTrace trace) {
    return new BlockTrace(trace.blockId(), trace.formatDetected(),
            trace.strategyUsed(), trace.originalTokens(),
            0, "", "dropped");
}

private BlockTrace truncate(BlockTrace trace, int maxTokens) {
    String truncated = truncateToTokens(
            trace.compressed(), maxTokens, estimator);
    return new BlockTrace(trace.blockId(), trace.formatDetected(),
            trace.strategyUsed() + "+truncate", trace.originalTokens(),
            estimator.estimate(truncated), truncated, "truncated");
}
```

---

## Builder

```java
public interface TokenKillerBuilder {
    TokenKillerBuilder chatClient(StructuredChatClient chatClient);
    TokenKillerBuilder estimator(TokenEstimator estimator);
    TokenKillerBuilder config(CompressConfig config);
    TokenKillerBuilder tee(CompressTee tee);
    TokenKillerBuilder tracker(CompressTracker tracker);
    TokenKillerBuilder strategy(CompressStrategy strategy);
    TokenKillerBuilder rules(String resourcePath);
    TokenKiller build();
}

public class DefaultTokenKillerBuilder implements TokenKillerBuilder {

    private StructuredChatClient chatClient;
    private TokenEstimator estimator;
    private CompressConfig config;
    private CompressTee tee;
    private CompressTracker tracker;
    private final List<CompressStrategy> userStrategies = new ArrayList<>();
    private final List<String> ruleResources = new ArrayList<>();

    @Override
    public TokenKiller build() {
        // 1. Token estimator (default: jtokkit)
        TokenEstimator est = estimator != null
                ? estimator : new JtiktokenEstimator();

        // 2. Config (default: CompressConfig.defaults())
        CompressConfig cfg = config != null
                ? config : CompressConfig.defaults();

        // 3. Load strategies by layer
        List<CompressStrategy> spiStrategies = new ArrayList<>();
        ServiceLoader.load(CompressStrategy.class).forEach(spiStrategies::add);

        List<CompressStrategy> declarativeStrategies =
                loadDeclarativeRules(ruleResources);

        List<CompressStrategy> builtinStrategies = BuiltinStrategies.all();

        // 4. Fallback strategy
        CompressStrategy fallback = chatClient != null
                ? new LlmSummarizeStrategy(chatClient, cfg.llmCostThreshold())
                : new PassthroughStrategy();

        // 5. Assemble router (immutable after construction)
        StrategyRouter router = new StrategyRouter(
                List.copyOf(userStrategies),
                List.copyOf(spiStrategies),
                List.copyOf(declarativeStrategies),
                List.copyOf(builtinStrategies),
                fallback);

        // 6. Assemble detectors
        ContentDetector detector = new CompositeDetector(List.of(
                new SignatureDetector()));

        // 7. Assemble pipelines
        CompressPipeline singlePipeline = new CompressPipeline(
                detector, router, est, cfg, tee, tracker);
        BudgetCompressPipeline budgetPipeline = new BudgetCompressPipeline(
                singlePipeline, est);

        return new DefaultTokenKiller(singlePipeline, budgetPipeline);
    }

    private List<CompressStrategy> loadDeclarativeRules(List<String> resources) {
        RuleParser parser = new RuleParser();
        RuleToStrategyConverter converter = new RuleToStrategyConverter();
        List<CompressStrategy> strategies = new ArrayList<>();

        // Load user-specified rule files
        for (String resource : resources) {
            parser.parse(resource).stream()
                    .map(converter::convert)
                    .forEach(strategies::add);
        }

        // Load classpath built-in rules
        parser.parseClasspath("compress-rules/").stream()
                .map(converter::convert)
                .forEach(strategies::add);

        return strategies;
    }
}
```

---

## Extension Model

Four layers, from simplest to most powerful:

### 1. Declarative YAML Rules (Zero Code)

Create a YAML file and place it on the classpath or pass to `TokenKillerBuilder.rules()`:

```yaml
my-tool:
  description: "My CI tool output"
  detect:
    startsWith: "[myci]"
  steps:
    - stripAnsi: true
    - stripLines: "^Downloading"
    - stripLines: "^\\s*$"
    - maxLines: 50
  onEmpty: "myci: ok"
```

### 2. Builder Registration (Few Lines of Code)

```java
TokenKiller tk = TokenKiller.builder()
    .strategy(CompressStrategy.named("my-tool")
        .formatHint("my-tool")
        .atoms(Atoms.stripAnsi(), Atoms.stripLines("^Downloading"), Atoms.maxLines(50))
        .build())
    .build();
```

### 3. SPI Auto-Discovery (Distribute as JAR)

```
META-INF/services/com.openmemind.ai.memory.core.compress.strategy.CompressStrategy
→ com.mycompany.MyToolStrategy
```

### 4. Spring Boot Auto-Configuration

```java
@Component
public class MyToolStrategy implements CompressStrategy {
    @Override public String name() { return "my-tool"; }
    @Override public boolean supports(String content, String formatHint) { ... }
    @Override public Mono<CompressResult> compress(String content, CompressContext ctx) { ... }
}
// Automatically discovered and registered by Spring Boot starter
```

---

## RTK Capability Mapping

| # | RTK Module | RTK File | Purpose | memind Component | Difference |
|---|-----------|----------|---------|-----------------|------------|
| 1 | Filter strategies | filter.rs + 45 cmd modules | Core compression 60-90% | strategy/atom + BuiltinStrategies | Atoms are composable; RTK modules are monolithic |
| 2 | Content detection | discover/registry.rs RegexSet | Command classification | detect/ (SignatureDetector) | RTK matches commands; memind matches content |
| 3 | Strategy routing | main.rs Commands match | Command → module dispatch | StrategyRouter (layered) | RTK compile-time; memind runtime with priority layers |
| 4 | Declarative filters | toml_filter.rs + 58 TOML files | Zero-code extension | strategy/declarative + YAML | Same 8-stage pipeline, YAML instead of TOML |
| 5 | Token estimation | tracking.rs `chars/4` | Compression metrics | JtiktokenEstimator (jtokkit) | memind uses precise tokenizer |
| 6 | Tee system | tee.rs | Original output preservation | CompressTee | Functionally equivalent |
| 7 | Tracking + Gain | tracking.rs + gain.rs | Quantifiable savings | CompressTracker + CompressStats | Pluggable storage (not SQLite-only) |
| 8 | LLM fallback | **N/A** | Unknown content compression | LlmSummarizeStrategy | memind-only, with cost gate |
| 9 | Facade / CLI | main.rs Cli struct | Unified entry point | TokenKiller + Builder | SDK form vs CLI form |
| 10 | Execution pipeline | main.rs main flow | detect → route → compress → track | CompressPipeline + BudgetCompressPipeline | Adds budget orchestration |

### RTK capabilities intentionally excluded from v1

| RTK Capability | Reason |
|---------------|--------|
| Hook rewrite (rewrite_cmd.rs) | CLI-specific; SDK doesn't need command interception |
| Discover (session scanning) | Depends on Claude Code session file format |
| Learn (correction detection) | Depends on command execution history sequence |
| Init (hook installation) | CLI-specific |
| Integrity (SHA-256 verification) | CLI-specific |
| Gain (ASCII chart reports) | Data available via CompressTracker.summary(); rendering is caller's concern |

---

## Future Integration Point: Memory Engine

TokenKiller and Memory are peer engines in memind-core. They share infrastructure but have no mutual dependency.

The integration point is a **bridge adapter on the Memory side** (not in TokenKiller core):

```java
// In Memory engine or a bridge class — NOT in compress package
public class MemoryContextCompressor {

    private final TokenKiller tokenKiller;

    /**
     * Convert ContextWindow components into CompressBlocks
     * and compress under a token budget.
     */
    public Mono<BudgetCompressResult> compressContext(
            ContextWindow window, int targetTokens) {

        List<CompressBlock> blocks = new ArrayList<>();

        // Recent messages — high priority, light compression
        if (window.recentMessages() != null) {
            String messages = formatMessages(window.recentMessages());
            blocks.add(CompressBlock.of(messages, null, BlockPriority.HIGH));
        }

        // Retrieved memories — normal priority
        if (window.memories() != null) {
            String memories = window.memories().formattedResult();
            blocks.add(CompressBlock.of(memories, null, BlockPriority.NORMAL));
        }

        return tokenKiller.compressWithBudget(
                BudgetCompressRequest.of(blocks, targetTokens));
    }
}
```

Dependency direction: `Memory → TokenKiller` (Memory can depend on TokenKiller's model classes). TokenKiller never imports Memory types.

---

## Known Limitations (v1)

1. **Atom intermediate representation**: Atoms pass `String` between each other. For very large inputs this may have performance overhead. A richer IR can be introduced later without breaking the API.
2. **Budget truncation**: `truncateToTokens` is a hard cut. It does not respect content structure (e.g., may cut in the middle of a JSON object). Smarter truncation can be added per content type.
3. **Built-in strategy coverage**: v1 will not match RTK's 45+ command modules. Strategies will be added incrementally, prioritizing the most common tools.
4. **Declarative rule expressiveness**: The YAML schema covers RTK's 8-stage TOML pipeline. More complex logic (state machines, JSON parsing) requires programmatic strategies.
5. **Atoms are synchronous**: `CompressAtom` is `String -> String` (synchronous). If a future use case requires an async atom (e.g., LLM-based atom), an `AsyncCompressAtom` returning `Mono<String>` can be introduced. For v1, async operations are handled at the `CompressStrategy` level.
6. **AutoCloseable**: `TokenKiller` does not implement `AutoCloseable` in v1. If `CompressTee` or `CompressTracker` implementations require cleanup, they should manage their own lifecycle. `AutoCloseable` support can be added in a future version if needed.
