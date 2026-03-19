package com.openmemind.ai.memory.core.retrieval.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.retrieval.RetrievalResult;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caffeine-based retrieval cache implementation
 *
 * <p>5 minutes TTL, maximum 1000 entries. Uses reverse indexing to speed up batch invalidation by memoryId.
 *
 */
public class CaffeineRetrievalCache implements RetrievalCache {

    private static final int DEFAULT_MAX_SIZE = 1000;
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private final Cache<String, RetrievalResult> cache;

    /** Reverse index: memoryId -> all cache keys associated with this memoryId */
    private final ConcurrentHashMap<String, Set<String>> memoryIdToKeys = new ConcurrentHashMap<>();

    public CaffeineRetrievalCache() {
        this(DEFAULT_MAX_SIZE, DEFAULT_TTL);
    }

    public CaffeineRetrievalCache(int maxSize, Duration ttl) {
        this.cache =
                Caffeine.newBuilder()
                        .maximumSize(maxSize)
                        .expireAfterWrite(ttl)
                        .removalListener(
                                (String key, RetrievalResult value, RemovalCause cause) -> {
                                    if (key != null) {
                                        int firstColon = key.indexOf(':');
                                        if (firstColon > 0) {
                                            String memId = key.substring(0, firstColon);
                                            Set<String> keys = memoryIdToKeys.get(memId);
                                            if (keys != null) {
                                                keys.remove(key);
                                            }
                                        }
                                    }
                                })
                        .recordStats()
                        .build();
    }

    @Override
    public Optional<RetrievalResult> get(MemoryId memoryId, String queryHash, String configHash) {
        return Optional.ofNullable(cache.getIfPresent(buildKey(memoryId, queryHash, configHash)));
    }

    @Override
    public void put(
            MemoryId memoryId, String queryHash, String configHash, RetrievalResult result) {
        String key = buildKey(memoryId, queryHash, configHash);
        cache.put(key, result);
        memoryIdToKeys
                .computeIfAbsent(memoryId.toIdentifier(), k -> ConcurrentHashMap.newKeySet())
                .add(key);
    }

    @Override
    public void invalidate(MemoryId memoryId) {
        Set<String> keys = memoryIdToKeys.remove(memoryId.toIdentifier());
        if (keys != null) {
            keys.forEach(cache::invalidate);
        }
    }

    private String buildKey(MemoryId memoryId, String queryHash, String configHash) {
        return memoryId.toIdentifier() + ":" + queryHash + ":" + configHash;
    }
}
