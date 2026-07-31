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
 * A resumable, allocation-free cursor over a decoded CreateTopics response.
 * <p>
 * The caller drives the cursor: {@code while (hasNext()) { switch (next()) { ... } }}, reading
 * {@link #topic()} or {@link #config()} after each {@link #next()} call. Accessors are valid only
 * until the next {@link #next()} call. A version-specific implementation (e.g. a v7 wire decoder)
 * returns a fixed default for any field its wire version does not carry, rather than changing shape.
 * </p>
 */
public interface KafkaCreateTopicsResponse
{
    enum Kind
    {
        TOPIC,
        CONFIG
    }

    int throttleTimeMillis();

    int topicCount();

    /**
     * @return {@code true} if {@link #next()} has another topic or config to report
     */
    boolean hasNext();

    /**
     * Advances to the next topic or config in the response.
     *
     * @return the kind of the item now current, readable via {@link #topic()} or {@link #config()}
     */
    Kind next();

    /**
     * @return the current topic; valid only after {@link #next()} returns {@link Kind#TOPIC}
     */
    Topic topic();

    /**
     * @return the current config; valid only after {@link #next()} returns {@link Kind#CONFIG}
     */
    Config config();

    interface Topic
    {
        DirectBufferEx buffer();

        int nameOffset();

        int nameLength();

        /**
         * @return 0 if this response's wire version does not carry a topic id
         */
        long topicIdMostSigBits();

        /**
         * @return 0 if this response's wire version does not carry a topic id
         */
        long topicIdLeastSigBits();

        short error();

        int messageOffset();

        /**
         * @return -1 if no error message is present
         */
        int messageLength();

        int numPartitions();

        short replicationFactor();

        int configCount();
    }

    interface Config
    {
        DirectBufferEx buffer();

        int nameOffset();

        int nameLength();

        int valueOffset();

        /**
         * @return -1 if no config value is present
         */
        int valueLength();

        boolean readOnly();

        byte configSource();

        boolean isSensitive();
    }
}
