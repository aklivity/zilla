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
 * A resumable, allocation-free cursor over a decoded DescribeConfigs response.
 * <p>
 * The caller drives the cursor: {@code while (hasNext()) { switch (next()) { ... } }}, mirroring
 * {@link CreateTopicsResponse}'s {@code Kind}-discriminated shape since each resource nests a
 * variable-length configs array. Config synonyms and documentation are present on the wire (this is
 * a v4 - flexible - response) but not surfaced here, since no current caller needs them; the cursor
 * still consumes their bytes internally so {@link #next()} always lands on a resource or config
 * boundary.
 * </p>
 */
public interface KafkaDescribeConfigsResponse
{
    enum Kind
    {
        RESOURCE,
        CONFIG
    }

    int throttleTimeMillis();

    int resourceCount();

    /**
     * @return {@code true} if {@link #next()} has another resource or config to report
     */
    boolean hasNext();

    /**
     * Advances the cursor and returns which kind of entry it landed on; valid only until the next
     * call. Call {@link #resource()} or {@link #config()} to read the entry itself.
     */
    Kind next();

    Resource resource();

    Config config();

    interface Resource
    {
        DirectBufferEx buffer();

        short error();

        int messageOffset();

        /**
         * @return -1 if no error message is present
         */
        int messageLength();

        /**
         * @return {@link KafkaDescribeConfigsRequest#RESOURCE_TYPE_TOPIC} or
         *         {@link KafkaDescribeConfigsRequest#RESOURCE_TYPE_BROKER}
         */
        byte type();

        int nameOffset();

        int nameLength();

        int configCount();
    }

    interface Config
    {
        DirectBufferEx buffer();

        int nameOffset();

        int nameLength();

        int valueOffset();

        /**
         * @return -1 if the value is null
         */
        int valueLength();

        boolean readOnly();

        /**
         * @return the raw {@code ConfigSource} wire value; {@code 5} (DEFAULT_CONFIG) means the
         *         config is at its default
         */
        byte configSource();

        boolean isSensitive();
    }
}
