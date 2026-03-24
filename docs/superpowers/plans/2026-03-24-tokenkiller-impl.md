# TokenKiller Compression Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a general-purpose context compression engine in memind-core that reduces token consumption by 60-90% through intelligent filtering.

**Architecture:** TokenKiller is a facade over a strategy-based compression pipeline. Content is routed to the best-matching `CompressStrategy` (a composable chain of `CompressAtom` transformations). A budget orchestration layer handles multi-block compression under token constraints. Extension via YAML rules, SPI, and Spring Boot.

**Tech Stack:** Java 21, Maven, Project Reactor, jtokkit, JUnit 5, Mockito, AssertJ, StepVerifier

**Spec:** `docs/superpowers/specs/2026-03-24-tokenkiller-design.md`

---

## File Structure

All new files under `memind-core/src/main/java/com/openmemind/ai/memory/core/compress/`.
All new tests under `memind-core/src/test/java/com/openmemind/ai/memory/core/compress/`.

```
compress/
├── TokenKiller.java
├── TokenKillerBuilder.java
├── DefaultTokenKillerBuilder.java
├── DefaultTokenKiller.java
├── CompressConfig.java
├── model/
│   ├── CompressRequest.java
│   ├── CompressResult.java
│   ├── CompressLevel.java
│   ├── CompressContext.java
│   ├── BudgetCompressRequest.java
│   ├── BudgetCompressResult.java
│   ├── CompressBlock.java
│   └── BlockPriority.java
├── strategy/
│   ├── CompressAtom.java
│   ├── CompressStrategy.java
│   ├── Atoms.java
│   ├── BuiltinStrategies.java
│   ├── StrategyRouter.java
│   ├── PassthroughStrategy.java
│   ├── LlmSummarizeStrategy.java
│   ├── LanguageDetector.java
│   └── declarative/
│       ├── RuleDefinition.java
│       ├── RuleParser.java
│       └── RuleToStrategyConverter.java
├── detect/
│   ├── ContentDetector.java
│   ├── CompositeDetector.java
│   └── SignatureDetector.java
├── token/
│   ├── TokenEstimator.java
│   └── JtiktokenEstimator.java
├── tee/
│   └── CompressTee.java
├── tracking/
│   ├── CompressTracker.java
│   ├── CompressRecord.java
│   └── CompressStats.java
└── pipeline/
    ├── CompressPipeline.java
    └── BudgetCompressPipeline.java
```

---

## Task 1: Data Models and TokenEstimator Interface (model/, token/)

Foundation types that everything else depends on. No logic, just records. Also includes `TokenEstimator` interface since `CompressContext` depends on it.

**Files:**
- Create: `compress/model/CompressLevel.java`
- Create: `compress/model/BlockPriority.java`
- Create: `compress/token/TokenEstimator.java` (interface only)
- Create: `compress/model/CompressContext.java`
- Create: `compress/model/CompressRequest.java`
- Create: `compress/model/CompressResult.java`
- Create: `compress/model/CompressBlock.java`
- Create: `compress/model/BudgetCompressRequest.java`
- Create: `compress/model/BudgetCompressResult.java`
- Test: `compress/model/CompressRequestTest.java`
- Test: `compress/model/CompressResultTest.java`
- Test: `compress/model/CompressBlockTest.java`
- Test: `compress/model/BudgetCompressRequestTest.java`

- [ ] **Step 1: Write tests for CompressLevel and BlockPriority**

```java
@DisplayName("CompressLevel")
class CompressLevelTest {
    @Test
    @DisplayName("LIGHT has target ratio 0.3")
    void lightTargetRatio() {
        assertThat(CompressLevel.LIGHT.targetRatio()).isEqualTo(0.3);
    }

    @Test
    @DisplayName("AGGRESSIVE has target ratio 0.85")
    void aggressiveTargetRatio() {
        assertThat(CompressLevel.AGGRESSIVE.targetRatio()).isEqualTo(0.85);
    }
}
```

- [ ] **Step 2: Implement CompressLevel and BlockPriority enums**

Per spec: `CompressLevel` with `LIGHT(0.3)`, `STANDARD(0.6)`, `AGGRESSIVE(0.85)`. `BlockPriority` with `CRITICAL`, `HIGH`, `NORMAL`, `LOW`.

- [ ] **Step 3: Run tests to verify they pass**

Run: `mvn test -pl memind-core -Dtest="CompressLevelTest" -DfailIfNoTests=false`

- [ ] **Step 4: Write tests for CompressContext**

Test `estimateTokens()` delegates to `TokenEstimator`.

- [ ] **Step 5: Implement TokenEstimator interface and CompressContext record**

Create `compress/token/TokenEstimator.java` as a single-method interface:

```java
public interface TokenEstimator {
    int estimate(String text);
}
```

Then implement `CompressContext` record per spec, referencing this interface.

- [ ] **Step 6: Write tests for CompressRequest**

Test factory methods `of(String)`, `of(String, String)`, `withLevel()`, `withHint()`. Verify immutability of hints map.

- [ ] **Step 7: Implement CompressRequest record**

Per spec. Compact constructor should validate `content` is not null.

- [ ] **Step 8: Write tests for CompressResult**

Test `savedTokens()`, `savingsPercent()`, `passthrough()`, `of()` factory methods.

- [ ] **Step 9: Implement CompressResult record**

Per spec. Include `of()` and `passthrough()` factory methods.

- [ ] **Step 10: Write tests for CompressBlock**

Test that `of()` factories auto-generate non-null unique IDs. Test default priority is `NORMAL`.

- [ ] **Step 11: Implement CompressBlock record**

Per spec. Use `AtomicLong` for ID generation. Field name is `hints` (not `metadata`).

- [ ] **Step 12: Write tests for BudgetCompressRequest and BudgetCompressResult**

Test `BudgetCompressRequest.of()` validates positive `targetTokens`. Test `BudgetCompressResult.withinBudget()`.

- [ ] **Step 13: Implement BudgetCompressRequest and BudgetCompressResult records**

Per spec. `BudgetCompressRequest.of()` throws `IllegalArgumentException` for non-positive `targetTokens`.

- [ ] **Step 14: Run all model tests**

Run: `mvn test -pl memind-core -Dtest="com.openmemind.ai.memory.core.compress.model.*Test"`

- [ ] **Step 15: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/compress/model/
git add memind-core/src/main/java/com/openmemind/ai/memory/core/compress/token/TokenEstimator.java
git add memind-core/src/test/java/com/openmemind/ai/memory/core/compress/model/
git commit -m "feat(compress): add TokenKiller data models and TokenEstimator interface"
```

---

## Task 2: CompressConfig

**Files:**
- Create: `compress/CompressConfig.java`
- Test: `compress/CompressConfigTest.java`

- [ ] **Step 1: Write tests for CompressConfig**

Test `defaults()` returns expected values. Test all `with*()` methods return new instances with modified field.

- [ ] **Step 2: Implement CompressConfig record**

Per spec. `defaults()` returns `(50, STANDARD, false, false, 500)`. All `with*()` methods.

- [ ] **Step 3: Run tests**

Run: `mvn test -pl memind-core -Dtest="CompressConfigTest"`

- [ ] **Step 4: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/compress/CompressConfig.java
git add memind-core/src/test/java/com/openmemind/ai/memory/core/compress/CompressConfigTest.java
git commit -m "feat(compress): add CompressConfig with immutable modification"
```

---

## Task 3: JtiktokenEstimator Implementation (token/)

**Files:**
- Create: `compress/token/JtiktokenEstimator.java`
- Test: `compress/token/JtiktokenEstimatorTest.java`

Note: `TokenEstimator` interface was already created in Task 1.

- [ ] **Step 1: Write tests for JtiktokenEstimator**

```java
@DisplayName("JtiktokenEstimator")
class JtiktokenEstimatorTest {
    private final TokenEstimator estimator = new JtiktokenEstimator();

    @Test
    @DisplayName("returns zero for null or empty text")
    void returnsZeroForEmpty() {
        assertThat(estimator.estimate(null)).isZero();
        assertThat(estimator.estimate("")).isZero();
    }

    @Test
    @DisplayName("estimates tokens for English text")
    void estimatesEnglishText() {
        int tokens = estimator.estimate("Hello, world!");
        assertThat(tokens).isGreaterThan(0).isLessThan(10);
    }
}
```

- [ ] **Step 2: Implement JtiktokenEstimator**

Delegates to existing `TokenUtils.countTokens()`. Handles null/empty input.

- [ ] **Step 3: Run tests**

Run: `mvn test -pl memind-core -Dtest="JtiktokenEstimatorTest"`

- [ ] **Step 4: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/compress/token/
git add memind-core/src/test/java/com/openmemind/ai/memory/core/compress/token/
git commit -m "feat(compress): add TokenEstimator with jtokkit implementation"
```

---

## Task 4: CompressAtom and Core Atoms (strategy/)

The composable transformation primitives.

**Files:**
- Create: `compress/strategy/CompressAtom.java`
- Create: `compress/strategy/Atoms.java`
- Create: `compress/strategy/LanguageDetector.java`
- Create: `compress/strategy/StateMachineConfig.java`
- Test: `compress/strategy/CompressAtomTest.java`
- Test: `compress/strategy/AtomsGeneralTest.java`
- Test: `compress/strategy/AtomsSpecializedTest.java`

**Dependencies:** Task 1 (models + TokenEstimator interface)

- [ ] **Step 1: Write tests for CompressAtom composition**

Test `andThen()` chains two atoms. Test `safe()` catches exceptions and returns input.

```java
@DisplayName("CompressAtom")
class CompressAtomTest {
    @Test
    @DisplayName("andThen chains two atoms")
    void andThenChains() {
        CompressAtom upper = (content, ctx) -> content.toUpperCase();
        CompressAtom trim = (content, ctx) -> content.trim();
        CompressAtom pipeline = trim.andThen(upper);

        String result = pipeline.apply("  hello  ", mockContext());
        assertThat(result).isEqualTo("HELLO");
    }

    @Test
    @DisplayName("safe returns input on exception")
    void safeReturnsInputOnException() {
        CompressAtom failing = (content, ctx) -> { throw new RuntimeException("boom"); };
        CompressAtom safe = failing.safe();

        String result = safe.apply("original", mockContext());
        assertThat(result).isEqualTo("original");
    }
}
```

- [ ] **Step 2: Implement CompressAtom interface**

Per spec. `@FunctionalInterface` with `andThen()` and `safe()` default methods.

- [ ] **Step 3: Run CompressAtom tests**

Run: `mvn test -pl memind-core -Dtest="CompressAtomTest"`

- [ ] **Step 4: Write tests for general-purpose atoms**

Test each atom in `Atoms`: `stripAnsi()`, `stripLines()`, `keepLines()`, `dedup()`, `maxLines()`, `headLines()`, `tailLines()`, `truncateLineAt()`, `replace()`.

Use realistic inputs (e.g., actual ANSI escape sequences, duplicate log lines).

- [ ] **Step 5: Implement general-purpose atoms in Atoms.java**

Each atom is a static factory method returning a `CompressAtom`. Implementation uses `String.lines()`, `Pattern`, `Stream` operations.

- [ ] **Step 6: Run atoms tests**

Run: `mvn test -pl memind-core -Dtest="AtomsGeneralTest"`

- [ ] **Step 6.5: Commit general-purpose atoms**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/compress/strategy/CompressAtom.java
git add memind-core/src/main/java/com/openmemind/ai/memory/core/compress/strategy/Atoms.java
git add memind-core/src/test/java/com/openmemind/ai/memory/core/compress/strategy/CompressAtomTest.java
git add memind-core/src/test/java/com/openmemind/ai/memory/core/compress/strategy/AtomsGeneralTest.java
git commit -m "feat(compress): add CompressAtom interface and general-purpose atoms"
```

- [ ] **Step 7: Write tests for specialized atoms**

Test `patternGroup()`, `errorExtract()`, `codeFold()`, `jsonSchema()`, `jsonCompact()`, `logNormalize()`, `treeCompact()`, `statsSummary()`, `ndjsonAggregate()`, `stateMachineParse()`.

Use realistic test data for each (e.g., actual ESLint output for `patternGroup`, actual pytest output for `errorExtract`).

- [ ] **Step 8: Implement specialized atoms**

Key implementation notes:
- `codeFold()`: Use `LanguageDetector` (create in this step as internal utility). Behavior varies by `CompressLevel` from context.
- `jsonSchema()`: Use Jackson `ObjectMapper` to parse JSON, recursively extract types.
- `logNormalize()`: Regex patterns for timestamps, UUIDs, hex values, paths.
- `statsSummary()`: Count lines, extract numeric patterns.
- `stateMachineParse()`: Takes a `StateMachineConfig` record that defines state transitions. Create `StateMachineConfig` as:

```java
public record StateMachineConfig(
    String headerPattern,       // regex to identify header section
    String failurePattern,      // regex to identify failure lines
    String summaryPattern       // regex to identify summary section
) {
    public static StateMachineConfig pytest() {
        return new StateMachineConfig("^=+.*=+$", "^FAILED", "^=+.*=+$");
    }
}
```

- [ ] **Step 9: Run all atom tests**

Run: `mvn test -pl memind-core -Dtest="AtomsSpecializedTest"`

- [ ] **Step 10: Commit specialized atoms**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/compress/strategy/Atoms.java
git add memind-core/src/main/java/com/openmemind/ai/memory/core/compress/strategy/LanguageDetector.java
git add memind-core/src/main/java/com/openmemind/ai/memory/core/compress/strategy/StateMachineConfig.java
git add memind-core/src/test/java/com/openmemind/ai/memory/core/compress/strategy/AtomsSpecializedTest.java
git commit -m "feat(compress): add specialized atoms (codeFold, jsonSchema, stateMachine, etc.)"
```

---

## Task 5: CompressStrategy and StrategyRouter

**Files:**
- Create: `compress/strategy/CompressStrategy.java`
- Create: `compress/strategy/StrategyRouter.java`
- Create: `compress/strategy/PassthroughStrategy.java`
- Create: `compress/strategy/BuiltinStrategies.java`
- Test: `compress/strategy/CompressStrategyTest.java`
- Test: `compress/strategy/StrategyRouterTest.java`

- [ ] **Step 1: Write tests for CompressStrategy builder**

Test `CompressStrategy.named("test").formatHint("test").atoms(...).build()`. Verify `supports()` matches by formatHint. Verify `compress()` applies atom chain.

- [ ] **Step 2: Implement CompressStrategy interface with StrategyBuilder**

Per spec. `StrategyBuilder` is a static inner class. `build()` wraps atoms with `safe()` before composition.

- [ ] **Step 3: Implement PassthroughStrategy**

Returns input unchanged. Used as default fallback when no LLM client is configured.

- [ ] **Step 4: Run CompressStrategy tests**

Run: `mvn test -pl memind-core -Dtest="CompressStrategyTest"`

- [ ] **Step 5: Write tests for StrategyRouter**

Test layered priority: user strategy overrides builtin. Test fallback when no strategy matches. Test immutability (all lists are `List.copyOf`).

- [ ] **Step 6: Implement StrategyRouter**

Per spec. Constructor takes 4 strategy lists + fallback. All stored as immutable copies.

- [ ] **Step 7: Implement BuiltinStrategies**

Register all 14 built-in strategies per spec table. Each uses `CompressStrategy.named().formatHint().detect().atoms().build()`.

- [ ] **Step 7.5: Write tests for BuiltinStrategies**

Create `BuiltinStrategiesTest.java`. For each of the 14 strategies, verify:
- `name()` returns expected value
- `supports(matchingContent, formatHint)` returns true
- `supports(nonMatchingContent, null)` returns false

- [ ] **Step 8: Run all strategy tests**

Run: `mvn test -pl memind-core -Dtest="com.openmemind.ai.memory.core.compress.strategy.*Test"`

- [ ] **Step 9: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/compress/strategy/CompressStrategy.java
git add memind-core/src/main/java/com/openmemind/ai/memory/core/compress/strategy/StrategyRouter.java
git add memind-core/src/main/java/com/openmemind/ai/memory/core/compress/strategy/PassthroughStrategy.java
git add memind-core/src/main/java/com/openmemind/ai/memory/core/compress/strategy/BuiltinStrategies.java
git add memind-core/src/test/java/com/openmemind/ai/memory/core/compress/strategy/
git commit -m "feat(compress): add CompressStrategy, StrategyRouter, and built-in strategies"
```

---

## Task 6: Content Detection (detect/)

**Files:**
- Create: `compress/detect/ContentDetector.java`
- Create: `compress/detect/CompositeDetector.java`
- Create: `compress/detect/SignatureDetector.java`
- Test: `compress/detect/SignatureDetectorTest.java`
- Test: `compress/detect/CompositeDetectorTest.java`

- [ ] **Step 1: Write tests for SignatureDetector**

Test each signature from the spec table. Use realistic content samples:
- Git status output → `"git-status"`
- Git diff output → `"git-diff"`
- JSON content → `"json"`
- Unknown content → `null`

- [ ] **Step 2: Implement ContentDetector, CompositeDetector, SignatureDetector**

`ContentDetector` is a single-method interface returning `String` (format hint) or `null`. `CompositeDetector` chains detectors. `SignatureDetector` checks first 10 lines against known patterns.

- [ ] **Step 3: Run detection tests**

Run: `mvn test -pl memind-core -Dtest="com.openmemind.ai.memory.core.compress.detect.*Test"`

- [ ] **Step 4: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/compress/detect/
git add memind-core/src/test/java/com/openmemind/ai/memory/core/compress/detect/
git commit -m "feat(compress): add content detection with signature matching"
```

---

## Task 7: LLM Fallback Strategy

**Files:**
- Create: `compress/strategy/LlmSummarizeStrategy.java`
- Test: `compress/strategy/LlmSummarizeStrategyTest.java`

- [ ] **Step 1: Write tests for LlmSummarizeStrategy**

Test cost gate: content below threshold → passthrough. Test savings check: estimated saving < 2× LLM cost → passthrough. Test successful summarization. Test empty LLM response → passthrough. Mock `StructuredChatClient`.

- [ ] **Step 2: Implement LlmSummarizeStrategy**

Per spec. `supports()` always returns false (only used as explicit fallback). Cost gate logic in `compress()`. Guard against null/blank LLM response.

Uses `ChatMessage` and `ChatRole` from `com.openmemind.ai.memory.core.llm` package. Define `SUMMARIZE_PROMPT` as a private constant:

```java
private static final String SUMMARIZE_PROMPT =
    "Compress the following text to preserve all key information while minimizing token count. "
    + "Remove redundancy, noise, and formatting. Output only the compressed result.";
```

- [ ] **Step 3: Run tests**

Run: `mvn test -pl memind-core -Dtest="LlmSummarizeStrategyTest"`

- [ ] **Step 4: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/compress/strategy/LlmSummarizeStrategy.java
git add memind-core/src/test/java/com/openmemind/ai/memory/core/compress/strategy/LlmSummarizeStrategyTest.java
git commit -m "feat(compress): add LLM fallback strategy with cost gate"
```

---

## Task 8: Declarative Rule Engine (strategy/declarative/)

**Files:**
- Create: `compress/strategy/declarative/RuleDefinition.java`
- Create: `compress/strategy/declarative/RuleParser.java`
- Create: `compress/strategy/declarative/RuleToStrategyConverter.java`
- Create: `src/main/resources/compress-rules/common.yml` (built-in rules)
- Test: `compress/strategy/declarative/RuleParserTest.java`
- Test: `compress/strategy/declarative/RuleToStrategyConverterTest.java`

- [ ] **Step 1: Add jackson-dataformat-yaml dependency to memind-core/pom.xml**

Add to `memind-core/pom.xml` dependencies section:

```xml
<dependency>
    <groupId>com.fasterxml.jackson.dataformat</groupId>
    <artifactId>jackson-dataformat-yaml</artifactId>
</dependency>
```

Version is managed by `memind-dependencies` BOM (Jackson version already declared there).

- [ ] **Step 2: Write tests for RuleDefinition**

Test record construction and sealed FilterStep variants.

- [ ] **Step 2: Implement RuleDefinition record**

Per spec. `DetectRule` and sealed `FilterStep` interface with 9 record variants.

- [ ] **Step 3: Write tests for RuleParser**

Parse a YAML string into `List<RuleDefinition>`. Test all FilterStep types are parsed correctly.

- [ ] **Step 4: Implement RuleParser**

Use Jackson YAML (`jackson-dataformat-yaml`). Parse YAML into `RuleDefinition` objects. `parseClasspath(String dir)` scans all `*.yml` files under the given classpath directory. Support both classpath and filesystem loading.

- [ ] **Step 5: Write tests for RuleToStrategyConverter**

Convert a `RuleDefinition` to `CompressStrategy`. Verify the resulting strategy's `supports()` and `compress()` work correctly.

- [ ] **Step 6: Implement RuleToStrategyConverter**

Per spec. `FilterStep → CompressAtom` conversion using pattern matching (`switch` expression).

- [ ] **Step 7: Create built-in rules file**

Create `memind-core/src/main/resources/compress-rules/common.yml` with a few representative rules (docker-build, mvn-install, npm-install, cargo-build).

- [ ] **Step 8: Run all declarative tests**

Run: `mvn test -pl memind-core -Dtest="com.openmemind.ai.memory.core.compress.strategy.declarative.*Test"`

- [ ] **Step 9: Commit**

```bash
git add memind-core/pom.xml
git add memind-core/src/main/java/com/openmemind/ai/memory/core/compress/strategy/declarative/
git add memind-core/src/test/java/com/openmemind/ai/memory/core/compress/strategy/declarative/
git add memind-core/src/main/resources/compress-rules/
git commit -m "feat(compress): add declarative YAML rule engine"
```

---

## Task 9: Tee and Tracking (tee/, tracking/)

**Files:**
- Create: `compress/tee/CompressTee.java`
- Create: `compress/tracking/CompressTracker.java`
- Create: `compress/tracking/CompressRecord.java`
- Create: `compress/tracking/CompressStats.java`
- Create: `compress/tracking/InMemoryCompressTracker.java`
- Test: `compress/tee/CompressTeeTest.java`
- Test: `compress/tracking/InMemoryCompressTrackerTest.java`

- [ ] **Step 1: Write tests for CompressTee**

Test: content < 500 chars → not saved. Test: content >= 500 chars → saved to file. Test: `appendHint()` appends path. Test: rotation keeps only `maxFiles` files. Use `@TempDir` for test directory.

- [ ] **Step 2: Implement CompressTee**

Per spec. File naming: `{epochMillis}_{label}.log`. Rotation deletes oldest files.

- [ ] **Step 3: Implement tracking records, interface, and InMemoryCompressTracker**

`CompressRecord` and `CompressStats` are records. `CompressTracker` is an interface with `record()`, `summary()`, `recent()`. `InMemoryCompressTracker` is a simple in-memory implementation using `CopyOnWriteArrayList` for thread safety.

- [ ] **Step 3.5: Write tests for InMemoryCompressTracker**

Test `record()` stores records, `summary()` aggregates correctly, `recent(limit)` returns most recent N records.

- [ ] **Step 4: Run tests**

Run: `mvn test -pl memind-core -Dtest="CompressTeeTest,InMemoryCompressTrackerTest"`

- [ ] **Step 5: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/compress/tee/
git add memind-core/src/main/java/com/openmemind/ai/memory/core/compress/tracking/
git add memind-core/src/test/java/com/openmemind/ai/memory/core/compress/tee/
git commit -m "feat(compress): add Tee system and tracking interfaces"
```

---

## Task 10: CompressPipeline (pipeline/)

**Files:**
- Create: `compress/pipeline/CompressPipeline.java`
- Test: `compress/pipeline/CompressPipelineTest.java`

- [ ] **Step 1: Write tests for CompressPipeline**

Test cases:
1. Content below `minTokenThreshold` → passthrough
2. With formatHint → routes to matching strategy
3. Without formatHint → auto-detects and routes
4. Strategy failure → passthrough (onErrorResume)
5. Compressed longer than original → passthrough
6. Tee saves original when enabled
7. Tracker records when enabled

Mock `StrategyRouter`, `ContentDetector`, `TokenEstimator`.

- [ ] **Step 2: Implement CompressPipeline**

Per spec. Fully reactive (no `.block()` calls). Use `Mono.defer()` for lazy evaluation. `onErrorResume` for strategy failures. Tee and tracking are optional (null-safe).

- [ ] **Step 3: Run tests**

Run: `mvn test -pl memind-core -Dtest="CompressPipelineTest"`

- [ ] **Step 4: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/compress/pipeline/CompressPipeline.java
git add memind-core/src/test/java/com/openmemind/ai/memory/core/compress/pipeline/CompressPipelineTest.java
git commit -m "feat(compress): add single-block CompressPipeline"
```

---

## Task 11: BudgetCompressPipeline

**Files:**
- Create: `compress/pipeline/BudgetCompressPipeline.java`
- Test: `compress/pipeline/BudgetCompressPipelineTest.java`

- [ ] **Step 1: Write tests for BudgetCompressPipeline**

Test cases:
1. All blocks fit within budget → no trimming
2. Over budget → LOW blocks dropped first
3. Over budget → NORMAL blocks truncated
4. CRITICAL blocks never touched
5. Block order preserved in output
6. `withinBudget()` returns correct value

- [ ] **Step 2: Implement BudgetCompressPipeline**

Per spec. `Flux.flatMap()` for parallel block compression. `budgetAllocate()` for single-pass greedy allocation. `truncateToTokens()` helper for hard truncation.

- [ ] **Step 3: Run tests**

Run: `mvn test -pl memind-core -Dtest="BudgetCompressPipelineTest"`

- [ ] **Step 4: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/compress/pipeline/BudgetCompressPipeline.java
git add memind-core/src/test/java/com/openmemind/ai/memory/core/compress/pipeline/BudgetCompressPipelineTest.java
git commit -m "feat(compress): add BudgetCompressPipeline with greedy allocation"
```

---

## Task 12: TokenKiller Facade and Builder

**Files:**
- Create: `compress/TokenKiller.java`
- Create: `compress/TokenKillerBuilder.java`
- Create: `compress/DefaultTokenKillerBuilder.java`
- Create: `compress/DefaultTokenKiller.java`
- Test: `compress/TokenKillerTest.java`
- Test: `compress/DefaultTokenKillerBuilderTest.java`

- [ ] **Step 1: Write tests for TokenKiller.create() (zero-config)**

Use sufficiently long test data to exceed `minTokenThreshold` (50 tokens), or configure builder with `config(CompressConfig.defaults().withMinTokenThreshold(0))`:

```java
@Test
@DisplayName("create() returns working TokenKiller with built-in strategies")
void createReturnsWorkingInstance() {
    TokenKiller tk = TokenKiller.builder()
        .config(CompressConfig.defaults().withMinTokenThreshold(0))
        .build();
    String gitStatus = "On branch main\n"
        + "Changes not staged for commit:\n"
        + "  (use \"git add <file>...\" to update what will be committed)\n"
        + "  (use \"git restore <file>...\" to discard changes in working directory)\n"
        + "\tmodified:   src/main/java/App.java\n"
        + "\tmodified:   src/main/java/Config.java\n"
        + "\tmodified:   src/test/java/AppTest.java\n"
        + "\nUntracked files:\n"
        + "  (use \"git add <file>...\" to include in what will be committed)\n"
        + "\tnew-feature.txt\n";
    StepVerifier.create(tk.compress(gitStatus))
        .assertNext(result -> {
            assertThat(result.savedTokens()).isGreaterThan(0);
            assertThat(result.strategyUsed()).isEqualTo("git-status");
        })
        .verifyComplete();
}
```

- [ ] **Step 2: Write tests for builder with custom strategy**

Test that user-registered strategy takes priority over builtin.

- [ ] **Step 3: Write tests for compressWithBudget**

Test multi-block compression with budget constraint.

- [ ] **Step 4: Implement TokenKiller interface**

Per spec. `compress()`, convenience methods, `compressWithBudget()`, `builder()`, `create()`.

- [ ] **Step 5: Implement TokenKillerBuilder interface and DefaultTokenKillerBuilder**

Per spec. Loads strategies in order: user → SPI → declarative → builtin. Assembles router, detector, pipelines. Returns `DefaultTokenKiller`.

- [ ] **Step 6: Implement DefaultTokenKiller**

Simple delegation to `CompressPipeline` and `BudgetCompressPipeline`.

- [ ] **Step 7: Run all TokenKiller tests**

Run: `mvn test -pl memind-core -Dtest="com.openmemind.ai.memory.core.compress.TokenKiller*Test"`

- [ ] **Step 8: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/compress/TokenKiller.java
git add memind-core/src/main/java/com/openmemind/ai/memory/core/compress/TokenKillerBuilder.java
git add memind-core/src/main/java/com/openmemind/ai/memory/core/compress/DefaultTokenKillerBuilder.java
git add memind-core/src/main/java/com/openmemind/ai/memory/core/compress/DefaultTokenKiller.java
git add memind-core/src/test/java/com/openmemind/ai/memory/core/compress/
git commit -m "feat(compress): add TokenKiller facade and builder"
```

---

## Task 13: Integration Tests

End-to-end tests that verify the full pipeline works together.

**Files:**
- Test: `compress/TokenKillerIntegrationTest.java`

- [ ] **Step 1: Write integration tests**

Test scenarios with realistic data:
1. Git status output → compressed summary
2. ESLint output → grouped by rule
3. JSON content → schema extraction
4. Unknown content → passthrough (no LLM configured)
5. Multi-block budget compression with mixed priorities
6. Declarative YAML rule applied correctly

- [ ] **Step 2: Run integration tests**

Run: `mvn test -pl memind-core -Dtest="TokenKillerIntegrationTest"`

- [ ] **Step 3: Commit**

```bash
git add memind-core/src/test/java/com/openmemind/ai/memory/core/compress/TokenKillerIntegrationTest.java
git commit -m "test(compress): add TokenKiller integration tests"
```

---

## Task 14: Build Verification and Cleanup

- [ ] **Step 1: Run full build with all checks**

Run: `mvn clean verify -pl memind-core`

This runs: compile, tests, spotless check, checkstyle, license check, jacoco.

- [ ] **Step 2: Fix any spotless/checkstyle violations**

Run: `mvn spotless:apply -pl memind-core` to auto-fix formatting.

- [ ] **Step 3: Fix any license header issues**

Run: `mvn license:format -pl memind-core` if needed.

- [ ] **Step 4: Verify all tests pass**

Run: `mvn test -pl memind-core`

Expected: All tests pass, including existing tests (no regressions).

- [ ] **Step 5: Commit any fixes**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/compress/
git add memind-core/src/test/java/com/openmemind/ai/memory/core/compress/
git commit -m "chore(compress): fix code style and build checks"
```

---

## Task Summary

| Task | Component | Dependencies | Estimated Steps |
|------|-----------|-------------|-----------------|
| 1 | Data Models + TokenEstimator interface | None | 15 |
| 2 | CompressConfig | None | 4 |
| 3 | JtiktokenEstimator | Task 1 | 4 |
| 4 | CompressAtom + Atoms | Task 1 | 12 |
| 5 | CompressStrategy + Router | Task 4 | 10 |
| 6 | Content Detection | None | 4 |
| 7 | LLM Fallback | Task 5 | 4 |
| 8 | Declarative Rules | Task 4, 5 | 10 |
| 9 | Tee + Tracking | Task 1 | 6 |
| 10 | CompressPipeline | Task 5, 6, 9 | 4 |
| 11 | BudgetCompressPipeline | Task 10 | 4 |
| 12 | TokenKiller Facade | Task 10, 11 | 8 |
| 13 | Integration Tests | Task 12 | 3 |
| 14 | Build Verification | Task 13 | 5 |
