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
package com.openmemind.ai.memory.core.utils;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ID generation utility class
 *
 * <p>Supports two generation methods: Snowflake algorithm and UUID
 *
 */
public final class IdUtils {

    private IdUtils() {}

    /**
     * Generate UUID (remove dashes)
     *
     * @return 32-bit hex string
     */
    public static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Generate standard UUID with dashes
     *
     * @return 36-bit standard UUID string
     */
    public static String uuidWithDash() {
        return UUID.randomUUID().toString();
    }

    /**
     * Create Snowflake algorithm ID generator
     *
     * @param workerId Worker node ID (0-31)
     * @param datacenterId Data center ID (0-31)
     * @return Snowflake algorithm generator instance
     */
    public static SnowflakeIdGenerator snowflake(long workerId, long datacenterId) {
        return new SnowflakeIdGenerator(workerId, datacenterId);
    }

    /**
     * Create default Snowflake algorithm ID generator (workerId=1, datacenterId=1)
     *
     * @return Snowflake algorithm generator instance
     */
    public static SnowflakeIdGenerator snowflake() {
        return new SnowflakeIdGenerator(1, 1);
    }

    /**
     * Snowflake algorithm ID generator
     *
     * <p>64-bit ID structure: 1 bit sign + 41 bits timestamp + 5 bits data center + 5 bits
     * worker node + 12 bits sequence number
     */
    public static final class SnowflakeIdGenerator {

        private static final long EPOCH = 1704067200000L; // 2024-01-01 00:00:00 UTC

        private static final long WORKER_ID_BITS = 5L;
        private static final long DATACENTER_ID_BITS = 5L;
        private static final long SEQUENCE_BITS = 12L;

        private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
        private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);

        private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
        private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
        private static final long TIMESTAMP_SHIFT =
                SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

        private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

        private final long workerId;
        private final long datacenterId;
        private final AtomicLong sequence = new AtomicLong(0L);
        private volatile long lastTimestamp = -1L;

        private SnowflakeIdGenerator(long workerId, long datacenterId) {
            if (workerId > MAX_WORKER_ID || workerId < 0) {
                throw new IllegalArgumentException(
                        "workerId must be between 0 and %d".formatted(MAX_WORKER_ID));
            }
            if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
                throw new IllegalArgumentException(
                        "datacenterId must be between 0 and %d".formatted(MAX_DATACENTER_ID));
            }
            this.workerId = workerId;
            this.datacenterId = datacenterId;
        }

        /**
         * Generate the next ID
         *
         * @return 64-bit Snowflake ID
         */
        public synchronized long nextId() {
            long timestamp = currentTimeMillis();

            if (timestamp < lastTimestamp) {
                throw new IllegalStateException(
                        "Clock moved backwards. Refusing to generate id for %d milliseconds"
                                .formatted(lastTimestamp - timestamp));
            }

            if (timestamp == lastTimestamp) {
                long seq = sequence.incrementAndGet() & SEQUENCE_MASK;
                if (seq == 0) {
                    timestamp = waitNextMillis(lastTimestamp);
                }
            } else {
                sequence.set(0L);
            }

            lastTimestamp = timestamp;

            return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                    | (datacenterId << DATACENTER_ID_SHIFT)
                    | (workerId << WORKER_ID_SHIFT)
                    | sequence.get();
        }

        /**
         * Generate the string form of the next ID
         *
         * @return ID string
         */
        public String nextIdStr() {
            return String.valueOf(nextId());
        }

        private long waitNextMillis(long lastTimestamp) {
            long timestamp = currentTimeMillis();
            while (timestamp <= lastTimestamp) {
                timestamp = currentTimeMillis();
            }
            return timestamp;
        }

        private long currentTimeMillis() {
            return System.currentTimeMillis();
        }
    }
}
