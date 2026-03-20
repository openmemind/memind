# RawData Time Range Design

## Overview

Add `startTime` and `endTime` fields to `MemoryRawData`, representing the first and last message timestamps within each chunk. This enables time-range awareness at the raw data level.

## Motivation

Currently, `MemoryRawData` only has a `createdAt` field (the time the raw data record was created). It does not capture when the original messages were sent. Adding `startTime` and `endTime` allows:

- Precise temporal context for each chunk
- Time-range based querying and filtering of raw data
- Better temporal ordering and retrieval in downstream memory processing

## Design

### Data Model Changes

#### MemoryRawData (core record)

Add two new `Instant` fields:

```java
public record MemoryRawData(
    String id,
    String memoryId,
    String contentType,
    String contentId,
    Segment segment,
    String caption,
    String captionVectorId,
    Map<String, Object> metadata,
    Instant createdAt,
    Instant startTime,    // NEW: timestamp of first message in this chunk
    Instant endTime       // NEW: timestamp of last message in this chunk
)
```

#### MemoryRawDataDO (persistence object)

Add corresponding fields:

```java
private Instant startTime;
private Instant endTime;
```

#### Database Schema (V1 migration)

Add columns to `memory_raw_data` table in both MySQL and SQLite V1 migration files:

**MySQL:**
```sql
start_time DATETIME(3) DEFAULT NULL,
end_time   DATETIME(3) DEFAULT NULL,
```

**SQLite:**
```sql
start_time TEXT DEFAULT NULL,
end_time   TEXT DEFAULT NULL,
```

### Time Extraction Logic

#### Unified Rule (both addMessages and addMessage modes)

For each chunk produced by the chunking step:

1. Get the chunk's `SegmentBoundary`
2. If `MessageBoundary(startMessage, endMessage)`:
   - `startTime` = `messages[startMessage].timestamp()`, fallback to `Instant.now()` if null
   - `endTime` = `messages[endMessage].timestamp()`, fallback to `Instant.now()` if null
3. If `CharBoundary` (non-conversation content):
   - Both `startTime` and `endTime` = `Instant.now()`

#### addMessages mode

Messages are passed in as a batch. Each chunk's `MessageBoundary` indexes directly into the original message list.

#### addMessage mode (streaming with boundary detection)

When boundary detection triggers extraction, the buffered messages form the batch. Same extraction logic applies — `startTime` and `endTime` come from the first and last buffered messages' timestamps.

### Converter Changes

#### RawDataConverter

`toDO()`: map `startTime` and `endTime` from `MemoryRawData` to `MemoryRawDataDO`.

`toRecord()`: map `startTime` and `endTime` from `MemoryRawDataDO` back to `MemoryRawData`.

### MemoryRawData Helper Methods

The `withVectorId()` and `withMetadata()` methods on `MemoryRawData` internally construct a new record instance. These must be updated to pass through the new `startTime` and `endTime` fields.

### Method Signature Changes

`RawDataLayer.buildAndPersist()` needs access to the original `List<Message>` to resolve timestamps from `MessageBoundary` indexes. The approach:

1. In `buildAndPersist`, check `input.content() instanceof ConversationContent cc` to extract the message list
2. Use the chunk's `MessageBoundary` to index into the message list and get timestamps
3. For the `processSegment` code path (where `input.content()` is null), `startTime` and `endTime` are set to `Instant.now()`

## Affected Files

| File | Change |
|------|--------|
| `MemoryRawData.java` | Add `startTime`, `endTime` fields |
| `MemoryRawDataDO.java` | Add `startTime`, `endTime` fields |
| `RawDataConverter.java` | Add field mapping |
| `RawDataLayer.java` | Compute startTime/endTime in `buildAndPersist` |
| `V1__init_memory_store.sql` (MySQL) | Add `start_time`, `end_time` columns |
| `V1__init_sqlite_store.sql` (SQLite) | Add `start_time`, `end_time` columns |
| Related tests | Update `MemoryRawData` construction |

## Non-Goals

- Indexing `start_time`/`end_time` columns (can be added later when query patterns are clear)
- Changing the `MemoryStore` interface (existing methods naturally accommodate the new fields)
- Backward compatibility migration (development phase, V1 schema is modified directly)
