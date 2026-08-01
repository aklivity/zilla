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
package io.aklivity.zilla.runtime.binding.kafka.api;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

/**
 * A resumable, allocation-free cursor over a decoded OffsetFetch response.
 * <p>
 * The caller drives the cursor: {@code while (hasNext()) { switch (next()) { ... } }}, reading
 * {@link #topic()} or {@link #partition()} after each {@link #next()} call. Accessors are valid
 * only until the next {@link #next()} call. {@link #error()} reports the group-level error and is
 * valid only once {@link #hasNext()} has returned {@code false}, since it is the last field read
 * off the wire.
 * </p>
 */
public interface KafkaOffsetFetchResponse
{
    enum Kind
    {
        TOPIC,
        PARTITION
    }

    int throttleTimeMillis();

    int topicCount();

    /**
     * @return {@code true} if {@link #next()} has another topic or partition to report
     */
    boolean hasNext();

    /**
     * Advances to the next topic or partition in the response.
     *
     * @return the kind of the item now current, readable via {@link #topic()} or {@link #partition()}
     */
    Kind next();

    /**
     * @return the current topic; valid only after {@link #next()} returns {@link Kind#TOPIC}
     */
    Topic topic();

    /**
     * @return the current partition; valid only after {@link #next()} returns {@link Kind#PARTITION}
     */
    Partition partition();

    /**
     * @return the group-level error code; valid only once {@link #hasNext()} returns {@code false}
     */
    short error();

    interface Topic
    {
        DirectBufferEx buffer();

        int nameOffset();

        int nameLength();

        int partitionCount();
    }

    interface Partition
    {
        int partitionIndex();

        long committedOffset();

        int committedLeaderEpoch();

        DirectBufferEx buffer();

        /**
         * @return -1 if no metadata is present
         */
        int metadataOffset();

        int metadataLength();

        short errorCode();
    }
}
