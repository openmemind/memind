# Memind Entity Resolution Hardening and Multilingual Design

Date: 2026-04-18

## Status

Proposed design. Approved at the conversational design level. Pending final user review of this written spec before implementation planning.

## Summary

This document defines how `memind` should harden entity construction while remaining consistent with the current graph-memory architecture and while becoming safely extensible beyond English-only assumptions.

The recommended direction is a conservative multilingual-capable entity pipeline:

- keep `MemoryItem` as the source of truth
- keep `GraphEntity` identity scoped per `memoryId`
- keep `item -> entity` membership as the truth layer
- keep retrieval-time entity sibling expansion instead of materializing dense `item -> item(entity)` links
- preserve type as a hard identity boundary
- add multilingual hardening at the extraction boundary
- add optional conservative entity resolution on top of current canonicalization
- treat aliases and cross-language surface forms as evidence, not automatic identity

The design explicitly avoids turning `memind` into a graph-first, fuzzy, globally resolved entity system. That would be harder to reason about, riskier for open-source users, and poorly aligned with the current bounded graph model.

## Scope

This design covers entity-related graph construction for `MemoryItem`.

It includes:

- entity type normalization
- entity name normalization
- entity noise filtering
- special entity normalization
- conservative entity resolution
- multilingual and mixed-script extensibility
- observability and test requirements for the entity path

It does not change:

- semantic link construction
- temporal link construction
- causal link construction
- retrieval fusion policy outside entity sibling expansion
- cross-`memoryId` identity
- graph-first multi-hop traversal

## Problem Statement

The current entity path is coherent but intentionally simple:

- extraction returns entity hints from the same LLM response as item extraction
- `GraphHintNormalizer` filters and bounds those hints
- `GraphEntityCanonicalizer` produces a canonical key shaped as `<entityType>:<normalizedName>`
- entities merge only when the type-normalized canonical key matches within one `memoryId`

That simplicity is a good starting point, but three weaknesses are now real:

1. current type normalization assumes a narrow English vocabulary
2. current name normalization is Unicode-safe but not multilingual-ready as an identity foundation
3. current implementation has no true alias or multilingual resolution layer

Those weaknesses are acceptable only as long as the entity path is treated as exact canonicalization. They become a problem once users expect:

- Chinese and English to coexist naturally
- more languages to be added later
- aliases and cross-language names to stop fragmenting the graph
- the design to remain rigorous enough for an open-source default

## Current Implementation Facts

### Fact 1: Entity hints come from LLM extraction

`MemoryItemExtractionResponse.ExtractedEntity` stores `name`, `entityType`, and optional `salience` as free-form strings / values from extraction output.

Implication:

- `entityType` is not strongly schema-enforced at the Java type level
- multilingual type labels can appear even if prompts prefer English examples

### Fact 2: Type normalization is currently English-enum oriented

`GraphEntityCanonicalizer.normalizeType(...)` recognizes only:

- `person`
- `organization`
- `place`
- `object`
- `concept`
- `special`

Everything else falls back to `OTHER`.

Implication:

- `"人物"` or `"公司"` are not currently recognized as `PERSON` or `ORGANIZATION`
- this is not a "Chinese entity name" problem; it is a localized type-token problem

### Fact 3: Name normalization is deterministic but intentionally shallow

`GraphEntityCanonicalizer.normalizeName(...)` uses:

- Unicode NFKC
- trim
- repeated whitespace collapse
- locale-root lowercasing

Implication:

- this is good for exact canonicalization and debugging
- it is not sufficient for multilingual alias identity
- it should remain the exact baseline, not be overextended into unsafe fuzzy logic

### Fact 4: Current merge semantics are exact canonical-key merge only

Today there is no distinct entity-resolution phase that scores alternative candidates. The system simply produces:

- `GraphEntity`
- `ItemEntityMention`
- `EntityCooccurrence`

and merges entities when canonical keys are equal.

Implication:

- current behavior is conservative and easy to reason about
- multilingual support should preserve that clarity for the default path

### Fact 5: Retrieval is mention-driven, not dense entity-link driven

Entity-related retrieval expansion is based on shared entity membership, not on materialized `item -> item(entity)` links.

Implication:

- improving entity identity quality improves retrieval without needing graph inflation
- there is no need to copy hindsight's denser entity-link materialization model

### Fact 6: Current noise filtering is language-limited

`GraphHintNormalizer` already filters some English and Chinese pronoun-like and temporal names.

Implication:

- this is a good pattern
- the current implementation is not extensible enough for broader multilingual coverage

## Goals

- Harden entity construction without destabilizing current graph architecture.
- Make the default entity path safe for Chinese and English mixed usage.
- Make the design extensible to more languages without rewriting storage semantics.
- Reduce false fragmentation where safe evidence exists.
- Keep false merges rarer than false splits.
- Preserve open-source portability and avoid mandatory database-specific or proprietary dependencies.

## Non-Goals

- Global entity resolution across all memories.
- A universal multilingual knowledge graph.
- Aggressive fuzzy matching across scripts by default.
- Mandatory transliteration libraries or language models in the core path.
- Storing `entity -> entity` semantic facts as a new truth layer.
- Materializing `item -> item(entity)` links for every shared entity pair.
- Perfect multilingual alias resolution in the synchronous write path.

## Design Principles

### 1. Keep identity scoped and conservative

`GraphEntity` identity remains scoped by `memoryId`. V1 does not introduce cross-memory or global identity.

### 2. Keep type as a hard constraint

Type remains part of identity. Resolution must not merge across effective types in the default path.

Examples:

- `person:apple` and `organization:apple` remain distinct
- `other:openai` does not automatically merge into `organization:openai`

### 3. Keep multilingual handling at the extraction boundary, not in storage semantics

The storage model should continue using a small, language-neutral internal type set. Localized labels must map into that internal contract before persistence.

### 4. Separate exact identity from alias evidence

Exact canonical keys define primary identity. Aliases, transliterations, abbreviations, and cross-language surface forms are evidence that may support later resolution, but they are not identity by themselves.

### 5. Prefer false split over false merge

This matters even more in multilingual settings. A missed merge is repairable. A false merge corrupts retrieval and graph interpretation.

### 6. Degrade safely for unsupported languages

If the system cannot confidently normalize or resolve a language/script, it must fall back to exact canonical behavior rather than guessing.

### 7. Keep the open-source default backend-neutral

The core design must not depend on:

- `pg_trgm`
- DB-specific case-folding behavior
- external multilingual NER models
- proprietary alias knowledge bases

Optional accelerators may exist later, but the baseline path must remain portable.

## Alternatives Considered

### Option A: Minimal patching of the current code

Add more hard-coded Chinese and English strings directly into the current canonicalizer and keep exact merge only.

Pros:

- cheapest short-term change
- preserves current architecture

Cons:

- does not scale to more languages
- keeps locale logic tangled in one class
- provides no path for aliases or controlled resolution

### Option B: Aggressive fuzzy multilingual resolver

Adopt a hindsight-like candidate scoring model with substring similarity, fuzzy lookup, co-occurrence, and temporal proximity as the default merge path.

Pros:

- reduces fragmentation faster
- may improve recall on some alias cases

Cons:

- too risky for `memind`'s truth model
- type is too easy to accidentally soften
- cross-script fuzzy matching is error-prone
- tends to become backend-specific
- not strong enough as a default open-source multilingual identity strategy

### Option C: Conservative multilingual-capable resolution layer

Keep current exact canonicalization as the truth baseline, then add pluggable multilingual normalization and optional conservative resolution on top.

Pros:

- aligned with current architecture
- safe default behavior
- extensible to more languages
- allows gradual improvement with observability

Cons:

- fragmentation improves incrementally rather than dramatically
- requires a few new abstractions instead of a single utility class

### Recommendation

Choose Option C.

It matches `memind`'s bounded graph philosophy, keeps the synchronous write path understandable, and gives the project a clean extension point for multilingual support without forcing unsafe fuzzy identity.

## Proposed Architecture

The entity pipeline should be split into explicit phases.

### Phase 0: Raw Entity Hint Intake

Input remains the extracted entity hint:

- `name`
- `entityType`
- `salience`

The system should preserve the raw surface form for observability even if later normalization changes it.

### Phase 1: Localized Type Mapping

Introduce `EntityTypeMapper` with a default implementation `LocalizedEntityTypeMapper`.

Responsibilities:

- accept free-form type labels from extraction
- normalize them to internal `GraphEntityType`
- support a built-in registry of localized synonyms
- fall back to `OTHER` when no safe mapping exists

Internal storage types remain:

- `PERSON`
- `ORGANIZATION`
- `PLACE`
- `OBJECT`
- `CONCEPT`
- `OTHER`
- `SPECIAL`

Key rule:

- the storage contract stays language-neutral
- multilingual handling happens before persistence

Initial built-in localized mappings should cover English and Chinese. The design must support adding more language packs without changing the storage enum.

### Phase 2: Language-Aware Name Normalization

Introduce `EntityNameNormalizer` with a default implementation `LanguageAwareEntityNameNormalizer`.

The normalizer has two layers.

#### Layer A: Language-neutral core normalization

Always apply:

- Unicode NFKC
- trim
- control-character cleanup
- repeated whitespace collapse where whitespace is meaningful

Case handling rule:

- do not assume every script uses case
- only apply case folding where the script/language meaningfully supports it

#### Layer B: Script- and locale-aware adapters

Optional adapters may refine normalization for known scripts or locales.

Examples:

- English adapter: stable case folding, punctuation cleanup
- Chinese adapter: full-width normalization already covered by NFKC; do not assume script conversion or romanization
- Turkish adapter: explicit handling strategy for case-folding-sensitive letters

Hard rule:

- primary canonical normalization must not transliterate
- primary canonical normalization must not perform cross-script rewriting by default
- primary canonical normalization must not strip meaningful diacritics globally

Rationale:

- transliteration and accent-stripping are alias operations, not exact identity operations

### Phase 3: Special Entity Normalization

Introduce `SpecialEntityMapper`.

Responsibilities:

- map localized conversational anchors to internal reserved special identities
- keep the reserved internal keys language-neutral:
  - `special:self`
  - `special:user`
  - `special:assistant`

Important constraint:

- special mapping should only happen in explicitly safe contexts
- it should not blindly rewrite arbitrary lexical occurrences of words like `"assistant"` or `"用户"`

The mapping decision may depend on:

- extracted type already being `SPECIAL`
- extraction prompt contract
- runtime conversation role context

### Phase 4: Language-Aware Noise Filtering

Introduce `EntityNoiseFilter` with a default implementation `LanguageAwareEntityNoiseFilter`.

The filter should evaluate:

- blank or punctuation-only names
- date-like strings
- temporal expressions
- pronoun-like mentions
- known conversational filler entities

The filter model should use:

- a language-neutral base
- built-in English and Chinese packs
- extensible locale packs for future languages

Unsupported-language rule:

- lack of a language pack must not block exact entity creation
- it simply means fewer language-specific drops are available

### Phase 5: Exact Canonical Identity Construction

The default identity contract remains:

- `entityKey = <effectiveType>:<normalizedName>`

Uniqueness remains scoped to:

- `(memoryId, entityKey)`

This remains the default and safest entity-resolution mode:

- no candidate lookup
- no fuzzy merge
- no cross-language alias merge

This mode must remain fully supported even after all later enhancements are added.

### Phase 6: Optional Conservative Entity Resolution

Introduce `EntityResolutionStrategy`.

Required implementations:

- `ExactCanonicalEntityResolutionStrategy`
- `ConservativeHeuristicEntityResolutionStrategy`

#### Default behavior

The default open-source behavior should remain `ExactCanonicalEntityResolutionStrategy`.

#### Conservative heuristic behavior

This mode may attempt to resolve a new mention against existing entities within the same `memoryId`, but only under guarded rules.

Allowed signals:

- exact normalized-name match
- safe same-script alias match
- explicit alias evidence
- nearby-entity overlap
- temporal proximity
- mention confidence / salience thresholds

Hard constraints:

- effective type is mandatory gate
- unsupported-language uncertainty must reduce confidence, not increase it
- cross-script fuzzy edit distance alone must never trigger a merge

### Safe alias classes

The heuristic path may auto-merge only within clearly safe alias classes.

Examples of potentially safe classes:

- case-only variants in case-aware scripts
- punctuation-only differences
- spacing variants
- known organizational suffix variants within the same language pack
- explicitly observed alias pairs in the same item, such as `清华大学 (Tsinghua University)`

Examples of classes that must not auto-merge by default:

- arbitrary cross-script transliterations
- abbreviation expansion without explicit evidence
- machine-generated phonetic similarity alone
- name similarity across types

## Alias Evidence Model

Multilingual support needs a place to record alias evidence without corrupting identity.

Introduce a derived alias-evidence layer, not a replacement identity layer.

Two acceptable implementation shapes are:

1. lightweight alias evidence in entity metadata for early stages
2. a dedicated `GraphEntityAlias` / `EntityAliasObservation` object once alias evidence needs first-class querying

Recommended staged direction:

- Stage 1: metadata-backed evidence is acceptable
- Stage 2+: promote alias evidence to a dedicated persisted object if needed

Alias evidence examples:

- raw observed surface forms
- script classification
- explicit alias pattern source, such as parentheses or slash-apposition
- user-provided alias dictionary matches
- evidence count
- first seen / last seen

Key rule:

- alias evidence informs candidate resolution
- alias evidence does not redefine the primary canonical key

## Multilingual Rules

### Rule 1: Internal types stay language-neutral

All persisted types remain the internal enum. Localized labels are ingestion-time concerns.

### Rule 2: Primary canonical keys stay script-local by default

If a Chinese name is extracted in Chinese, the canonical key should remain Chinese-script based. If an English name is extracted in English, the canonical key should remain English-script based.

Do not force transliteration into one script for storage.

### Rule 3: Cross-script identity requires stronger evidence than same-script identity

Safe default:

- no automatic merge between `马斯克` and `Elon Musk`
- no automatic merge between `清华大学` and `Tsinghua University`

Those pairs may be linked by alias evidence or merged only when explicit evidence exists.

### Rule 4: Unsupported languages fall back to exact canonical mode

The system should continue functioning even when a language pack is missing. The fallback is conservative fragmentation, not guesswork.

### Rule 5: Do not globalize locale-specific rewriting

Operations such as diacritic stripping, script conversion, or article removal must be locale-pack decisions, never unconditional global rules.

## Why Hindsight Is Useful but Not a Drop-In Model

`hindsight` is still valuable as a reference in three areas:

1. it separates candidate retrieval from candidate scoring
2. it uses co-occurrence and temporal signals as supporting evidence
3. it explicitly documents Unicode case-folding pitfalls between application code and the database

However, `memind` should not copy hindsight's resolver wholesale.

Reasons:

- `memind`'s entity truth model is more conservative
- `memind` should keep type as a hard constraint
- `memind` should not require DB-specific fuzzy lookup as a core dependency
- multilingual cross-script resolution is riskier than hindsight's mostly lowercased string-based scoring suggests

Practical takeaway:

- borrow the separation of concerns and Unicode rigor
- do not borrow aggressive fuzzy identity as the default write-path behavior

## Data Model Impact

### Required baseline impact

No change to the primary truth model:

- `GraphEntity`
- `ItemEntityMention`
- `EntityCooccurrence`

must remain the core persisted graph primitives.

### Optional additive impact

Later stages may add alias evidence storage.

Candidate object:

- `GraphEntityAlias`

Possible fields:

- `memoryId`
- `entityKey`
- `aliasNormalized`
- `aliasDisplay`
- `aliasClass`
- `script`
- `evidenceCount`
- `firstSeenAt`
- `lastSeenAt`
- `metadata`

This object must be additive. It must not become the new identity source of truth.

## Retrieval Impact

Retrieval architecture remains unchanged in principle.

Entity-related recall should continue to work by query-time expansion over shared entity membership.

Expected benefit from this design:

- fewer false splits for safe localized variants
- bounded improvement in entity sibling expansion quality
- no explosion from materialized `item -> item(entity)` edges

No change in this design:

- no unbounded traversal
- no graph-first entity search path
- no reliance on `EntityCooccurrence` as the primary retrieval path

## Configuration

Recommended configuration shape:

- `graph.entity.typeMappingMode = localized`
- `graph.entity.resolutionMode = exact | conservative`
- `graph.entity.supportedLanguagePacks = [en, zh, ...]`
- `graph.entity.crossScriptMergePolicy = off`
- `graph.entity.aliasEvidenceMode = metadata | persisted`

Default open-source values:

- `typeMappingMode = localized`
- `resolutionMode = exact`
- `supportedLanguagePacks = [en, zh]`
- `crossScriptMergePolicy = off`
- `aliasEvidenceMode = metadata`

## Observability Requirements

Add metrics and tracing for:

- entity type mapping fallback rate to `OTHER`
- dropped-entity counts by reason
- canonical entity creation count
- exact canonical merges
- heuristic candidate evaluations
- heuristic merge accept / reject counts
- cross-script merge attempts
- cross-script merge rejections
- alias evidence accumulation counts
- top unresolved localized type labels

Rationale:

- multilingual issues are often data-distribution problems rather than code-only problems
- observability is required before widening merge rules

## Testing Requirements

The entity path must add focused tests for:

### Type mapping

- English type labels
- Chinese type labels
- unknown localized type labels falling back to `OTHER`

### Exact normalization

- case-only English variants
- full-width / half-width variants
- whitespace normalization
- punctuation variants that should remain exact-safe

### Unicode correctness

- Turkish case-folding-sensitive examples
- mixed composed / decomposed Unicode forms

### Noise filtering

- English pronouns and temporal expressions
- Chinese pronouns and temporal expressions
- unsupported-language strings that should pass through conservatively

### Resolution gating

- same-type exact match merges
- different-type same-name does not merge
- same-script safe alias cases
- cross-script pairs do not auto-merge without explicit evidence

### Explicit alias evidence

- appositional or parenthetical bilingual forms such as `清华大学 (Tsinghua University)`
- slash-separated explicit alias forms where extraction clearly marks them as equivalent

## Rollout Plan

### Stage 1: Multilingual Hardening of the Exact Path

Deliver:

- `LocalizedEntityTypeMapper`
- `LanguageAwareEntityNameNormalizer`
- `SpecialEntityMapper`
- `LanguageAwareEntityNoiseFilter`
- metrics and tests

Do not deliver:

- heuristic auto-merge
- new alias table
- cross-script merge

### Stage 2: Conservative Resolution and Alias Evidence

Deliver:

- `EntityResolutionStrategy`
- `ExactCanonicalEntityResolutionStrategy`
- `ConservativeHeuristicEntityResolutionStrategy`
- metadata-backed alias evidence

Still avoid by default:

- cross-script auto-merge
- global fuzzy identity

### Stage 3: Optional Persisted Alias Layer and More Language Packs

Deliver only if justified by evidence:

- dedicated alias evidence persistence
- additional language packs
- optional user-configured alias dictionaries

This stage must remain additive and must not replace the exact canonical baseline.

## Final Recommendation

`memind` should evolve entity construction by adding multilingual hardening and conservative resolution around the current exact canonical model, not by replacing it with aggressive fuzzy identity.

That means:

- exact canonical identity remains the truth baseline
- type remains a hard gate
- multilingual handling is introduced through localized mapping and language-aware normalization
- alias evidence is accumulated separately from identity
- cross-script auto-merge stays off by default

This gives `memind` a design that is rigorous enough for open-source defaults, safe enough for multilingual data, and flexible enough to improve over time without corrupting the graph's foundational truth model.
