/*
 * Copyright 2021-2026 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.kafka.internal.cache;

/**
 * Latest produce acknowledgement for one cache log, published by a single writer and read by another worker.
 *
 * A sequence counter that is odd while a publish is in progress lets a reader detect and retry a torn read.
 */
public final class KafkaCacheAckRecord
{
    private static final long NO_OFFSET = -1L;

    private volatile long sequence;
    private volatile long flushOffset = NO_OFFSET;
    private volatile long ackOffset = NO_OFFSET;
    private volatile long timestamp = NO_OFFSET;

    public void publish(
        long flushOffset,
        long ackOffset,
        long timestamp)
    {
        final long current = sequence;
        this.sequence = current + 1L;
        this.flushOffset = flushOffset;
        this.ackOffset = ackOffset;
        this.timestamp = timestamp;
        this.sequence = current + 2L;
    }

    public boolean read(
        Snapshot snapshot)
    {
        long before;
        long after;
        do
        {
            before = sequence;
            snapshot.flushOffset = flushOffset;
            snapshot.ackOffset = ackOffset;
            snapshot.timestamp = timestamp;
            after = sequence;
        } while ((before & 1L) != 0L || before != after);

        return snapshot.flushOffset != NO_OFFSET;
    }

    public static final class Snapshot
    {
        private long flushOffset = NO_OFFSET;
        private long ackOffset = NO_OFFSET;
        private long timestamp = NO_OFFSET;

        public long flushOffset()
        {
            return flushOffset;
        }

        public long ackOffset()
        {
            return ackOffset;
        }

        public long timestamp()
        {
            return timestamp;
        }
    }
}
