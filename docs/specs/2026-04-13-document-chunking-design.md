# Document Chunking Design

**Goal:** Refine `memind` document chunking so document rawdata is segmented in a structure-aware, token-safe, and retrieval-friendly way without changing the core extraction SPI or introducing a streaming parser architecture.

**Non-goal:** This design does not replace document chunking with document-only summaries, does not introduce a document-wide streaming pipeline, does not change `RawDataLayer` orchestration, and does not add a new structured `DocumentSection` extraction model for parser-backed documents.

---

## Why This Change

The current document plugin already has the right macro shape:

- `file/url -> ContentParser -> DocumentContent -> DocumentContentProcessor -> chunk -> caption -> vectorize -> item extraction`
- document parsing and document-specific chunking both live in the document plugin
- core keeps only generic chunking utilities and the extraction pipeline

However, the current chunking policy is still too coarse in the wrong places and too generic in the wrong places:

- markdown has heading-aware chunking, but other document profiles do not express their natural structure clearly enough
- `pdf` is page-aware at text-shaping time, but chunking does not yet have a page-first policy with small-page merge and large-page split behavior
- `csv` is text-shaped into `Row N:` records, but chunking still does not treat row windows as a first-class strategy
- generic paragraph chunking is useful as a fallback, but it should not be the primary semantic policy for every document type

At the same time, removing chunking entirely is not the right answer:

- one document often has one broad topic, but retrieval and item extraction still need localized evidence
- `memind` persists and vectors segment-level rawdata, not only document-level summaries
- item extraction already expects segment-level inputs and still has prompt-budget enforcement after rawdata extraction

The right direction is therefore:

- keep document chunking
- make it structure-first instead of token-first
- keep token rules as a hard safety boundary, not the primary semantic boundary
- preserve plugin-owned document logic inside the document plugin

---

## Design Summary

Document chunking will use a uniform three-stage model:

1. derive normalized structure blocks from parser-backed or direct document text
2. merge undersized neighboring blocks when the profile semantics allow it
3. split oversized blocks only inside their own local structure boundary

When `DocumentContent.sections()` is non-empty, section boundaries remain the outermost document-owned structure boundary:

- section splitting happens before profile-specific chunk routing
- profile chunking applies independently within each section
- emitted segments must preserve section metadata such as `sectionIndex` and `sectionTitle`
- emitted segment boundaries must continue to map back to absolute positions in `DocumentContent.parsedText`

The routing remains profile-aware:

- `document.markdown` -> heading-block policy
- `document.pdf.tika` -> page-first policy
- `document.csv` -> row-window policy
- `document.text`, `document.html`, `document.binary` -> paragraph-window policy

`TokenAwareSegmentAssembler` remains the shared fallback utility in core, but the document plugin owns the profile-specific block selection and merge rules.

---

## Core Decisions

### 1. Keep document chunking

Document rawdata continues to produce multiple segments when the document naturally contains multiple retrieval units.

Chunking remains responsible for:

- rawdata persistence granularity
- vector retrieval granularity
- localized item extraction input units

It is not replaced by a single document-level summary.

### 2. Keep parsed content limit, but relax it

`parsed content limit` remains as a post-parse safeguard against pathological parse expansion and overall extraction cost blow-up.

Default parsed token limits become:

- text-like: `100_000`
- binary: `100_000`

This limit is not the primary prompt-budget control. Prompt-budget enforcement remains a later stage. The parsed limit only protects the system from extremely large parser outputs.

### 3. Do not change the core SPI

This phase does not modify:

- `ContentParser`
- `FetchSession`
- `FetchedResource`
- `RawContent`
- `RawDataLayer`

The implementation stays within the document plugin and existing processor/chunker extension points.

### 4. Chunking must be robust against strange user content

Malformed or unusual document text should degrade chunk quality, not crash chunking.

Allowed outcomes for strange input:

- fallback to a less structured chunking policy
- smaller or larger than ideal chunks within configured hard limits
- reduced semantic quality

Disallowed outcome:

- throwing a chunking exception solely because the document text shape is unusual

Actual parse failures remain parse errors, not chunking errors.

---

## Chunking Model

Every document profile will follow the same logical algorithm:

1. Build structure blocks
2. Classify each block as:
   - undersized
   - normal
   - oversized
3. Merge undersized neighboring blocks when allowed by the profile
4. Keep normal blocks unchanged
5. Split oversized blocks only within the same structure block
6. Emit final `Segment`s with preserved `CharBoundary` and structure metadata

If the document already contains explicit `DocumentSection`s:

1. keep section as the outer boundary
2. build profile-specific structure blocks inside each section
3. never merge or split across section boundaries at the rawdata chunking stage

### Size vocabulary

- `targetTokens`: preferred chunk size
- `hardMaxTokens`: absolute upper bound
- `minChunkTokens`: lower bound used to decide whether a block should be merged instead of emitted as its own chunk

`minChunkTokens` is not a hard correctness requirement. It is a shaping heuristic.

---

## Profile-Specific Policies

### Markdown

Primary structure block:

- heading block

Rules:

- detect heading blocks using markdown heading markers
- content before the first heading forms a preamble block
- do not merge across heading boundaries
- if a heading block is within `hardMaxTokens`, emit it as one chunk even if it is small
- if a heading block exceeds `hardMaxTokens`, split only inside that heading block using paragraph-first fallback

Rationale:

- headings are strong semantic boundaries
- preserving heading ownership is more important than aggressively merging tiny blocks

Expected metadata:

- `chunkStrategy=markdown-heading`
- `structureType=headingBlock`
- `chunkIndex`
- `headingLevel` when derivable
- `headingTitle` when derivable

### PDF

Primary structure block:

- page block

Rules:

- treat each `Page N:` block as the first-class structural unit
- if page token count is between `minChunkTokens` and `hardMaxTokens`, emit one page chunk
- if page token count exceeds `hardMaxTokens`, split only inside that page using paragraph-first fallback
- if page token count is below `minChunkTokens`, allow merging with following adjacent small pages
- adjacent-page merge stops when:
  - the merged block reaches or exceeds `targetTokens`, or
  - the merge count reaches `pdfMaxMergedPages`, or
  - the next page is itself not undersized

Rationale:

- page numbers are useful retrieval anchors
- page boundaries are meaningful physical boundaries, but not always strong semantic boundaries
- small standalone pages create low-value retrieval fragments

Expected metadata:

- `chunkStrategy=pdf-page`
- `structureType=pageBlock`
- `chunkIndex`
- `pageNumber` for single-page chunks
- `pageStart` / `pageEnd` for merged-page chunks

### Text / HTML / DOCX / RTF / Binary Textual Fallback

Primary structure block:

- paragraph block

Rules:

- split by paragraph boundaries first
- merge neighboring undersized paragraphs until approaching `targetTokens`
- if a paragraph block exceeds `hardMaxTokens`, split inside it using line/token fallback
- no special cross-block preservation beyond paragraph ordering

Rationale:

- these formats generally lack a consistently strong in-band structural boundary after current plain-text shaping
- paragraph boundaries are the most stable general-purpose retrieval unit

Expected metadata:

- `chunkStrategy=paragraph-window`
- `structureType=paragraphBlock`
- `chunkIndex`

### CSV

Primary structure block:

- row block

Rules:

- consume text shaped as `Row N:` sections
- chunk by row windows, not by plain paragraph
- merge consecutive rows until approaching `targetTokens`
- record `rowStart` / `rowEnd`
- each emitted row-window chunk must retain row identity in-band in the chunk text, not only in metadata
- if a single row exceeds `hardMaxTokens`, split only inside that row
- every child chunk produced from an oversized row must repeat the stable `Row N:` identity prefix so retrieval and item extraction do not lose row ownership

Rationale:

- row-oriented content is the natural semantic unit for tabular memory extraction
- paragraph chunking is a poor fit for CSV-shaped text

Expected metadata:

- `chunkStrategy=csv-row-window`
- `structureType=rowBlock`
- `chunkIndex`
- `rowStart`
- `rowEnd`

---

## Fallback and Error Handling

Chunking must be defensive.

### Fallback rules

- markdown heading detection failure -> fallback to paragraph-window chunking
- pdf page block detection failure -> fallback to paragraph-window chunking
- csv row block detection failure -> fallback to paragraph-window chunking
- empty or blank content -> return an empty segment list

### Failure rules

Chunking may fail only when:

- chunking configuration is invalid
- plugin implementation violates segment boundary invariants
- an earlier parser stage could not produce valid textual content

Chunking must not fail merely because:

- headings are irregular
- page text is sparse
- spacing is inconsistent
- paragraphs are malformed
- row text is unexpectedly long or noisy

### Boundary invariants

All emitted document segments must:

- use `CharBoundary`
- preserve monotonic character offsets within the source document text
- keep metadata immutable in the final emitted `Segment`

---

## Configuration

`DocumentExtractionOptions` will be extended so profile shaping uses explicit, documented knobs instead of hidden magic values.

### Default values

- `textLikeSourceLimit = 2 MB`
- `binarySourceLimit = 20 MB`
- `textLikeParsedLimit.maxTokens = 100_000`
- `binaryParsedLimit.maxTokens = 100_000`
- `textLikeChunking = TokenChunkingOptions(1200, 1800)`
- `binaryChunking = TokenChunkingOptions(1200, 1800)`
- `textLikeMinChunkTokens = 300`
- `binaryMinChunkTokens = 300`
- `pdfMaxMergedPages = 3`

### Interpretation

- `targetTokens` controls preferred chunk size, not a mandatory fixed width
- `hardMaxTokens` is the strict upper bound before fallback splitting
- `minChunkTokens` influences merge-small behavior
- `pdfMaxMergedPages` prevents a long run of tiny pages from collapsing into an overly broad chunk

---

## Implementation Shape

The document plugin keeps the existing public routing shape but refactors internal chunking helpers.

### Kept components

- `DocumentContentProcessor`
- `ProfileAwareDocumentChunker`
- `MarkdownDocumentChunker`
- `TokenAwareSegmentAssembler` in core

### New internal helpers

- `ParagraphWindowDocumentChunker`
- `PdfPageDocumentChunker`
- `CsvRowWindowDocumentChunker`
- `DocumentChunkSupport` or equivalent utility for block classification, merge-small, and metadata helpers

### Responsibilities

- `ProfileAwareDocumentChunker`: profile routing only
- profile chunkers: define structure blocks and merge policy
- `TokenAwareSegmentAssembler`: generic paragraph/line/token fallback only
- `DocumentContentProcessor`: keep parsed-content validation and item-budget normalization

This keeps profile semantics out of core and avoids hardcoding document-type behavior into generic infrastructure.

---

## Interaction with Item Budget Enforcement

Rawdata chunking and item-budget normalization remain distinct responsibilities.

Rawdata chunking:

- aims for retrieval-friendly and persistence-friendly semantic units
- should be relatively coarse
- should preserve structure

Item budget normalization:

- happens later
- is allowed to split again if an emitted segment still exceeds the effective LLM prompt budget
- is a technical safety net, not the main document chunking strategy

Therefore:

- this design does not try to make rawdata chunks exactly match model prompt budgets
- it only ensures they are structurally good and bounded by reasonable hard limits

---

## Testing

The implementation must include focused tests for both chunk semantics and robustness.

### Markdown tests

- preamble plus headings produce heading-owned chunks
- chunks do not merge across heading boundaries
- oversized heading blocks split within the same heading block

### PDF tests

- normal page emits single-page chunk with page metadata
- consecutive small pages merge into a bounded page range chunk
- oversized page splits only inside the page
- page-aware routing falls back safely when page blocks cannot be derived

### Paragraph-window tests

- short neighboring paragraphs merge
- normal paragraphs stay stable
- oversized paragraphs split correctly
- `CharBoundary` offsets remain correct after merge and split

### Section-boundary tests

- direct `DocumentContent` with populated `sections()` keeps section as the outer chunking boundary
- profile-specific chunking runs inside each section rather than across sections
- emitted segment metadata preserves `sectionIndex` and `sectionTitle`
- absolute `CharBoundary` values remain correct after section-local chunking

### CSV tests

- row windows emit `rowStart` / `rowEnd`
- multiple short rows merge into one chunk
- oversized row splits within the row window
- oversized single-row splits repeat the `Row N:` identity prefix in every child chunk
- malformed row text degrades gracefully without chunk crash

### Robustness tests

- blank content returns empty chunk list
- irregular spacing does not throw
- profile-specific block detection failures fall back to paragraph chunking

---

## Rollout Strategy

This change should be implemented as an internal refactor of the document plugin only.

Recommended order:

1. extend `DocumentExtractionOptions`
2. introduce paragraph-window helper
3. introduce pdf page-first helper
4. introduce csv row-window helper
5. wire all helpers through `ProfileAwareDocumentChunker`
6. update tests

No migration of persisted data is required. Existing rawdata will remain readable. New document ingestions will simply produce better segment boundaries and metadata.

---

## Open Decision Closed in This Design

This design intentionally chooses:

- keep chunking
- do not replace chunking with a single whole-document caption
- do not make document ingestion streaming in this phase
- keep parsed content limit, but raise it to `100_000 tokens`

This gives `memind` a higher-quality document chunking model now without forcing a larger architectural rewrite.
