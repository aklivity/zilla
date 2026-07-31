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

import java.util.PrimitiveIterator;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

/**
 * A resumable, allocation-free cursor over a decoded Metadata response.
 * <p>
 * The caller drives the cursor in three phases, in wire order: first every broker
 * ({@code while (hasNextBroker()) { ... nextBroker() ... }}), then the cluster-level fields
 * ({@link #clusterIdOffset()}, {@link #controllerId()}, {@link #topicCount()}, all valid only once
 * {@link #hasNextBroker()} returns {@code false}), then every topic and its partitions
 * ({@code while (hasNext()) { switch (next()) { ... } }}), reading {@link #topic()} or
 * {@link #partition()} after each {@link #next()} call. Accessors are valid only until the next
 * cursor-advancing call. A version-specific implementation (e.g. a v9 wire decoder) returns a fixed
 * default for any field its wire version does not carry, rather than changing shape.
 * </p>
 */
public interface KafkaMetadataResponse
{
    enum Kind
    {
        TOPIC,
        PARTITION
    }

    DirectBufferEx buffer();

    int throttleTimeMillis();

    int brokerCount();

    /**
     * @return {@code true} if {@link #nextBroker()} has another broker to report
     */
    boolean hasNextBroker();

    /**
     * Advances to the next broker in the response.
     *
     * @return the broker now current
     */
    Broker nextBroker();

    /**
     * @return -1 if this response's wire version does not carry a cluster id, or none is present
     */
    int clusterIdOffset();

    int clusterIdLength();

    int controllerId();

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

    interface Broker
    {
        int nodeId();

        int hostOffset();

        int hostLength();

        int port();

        /**
         * @return -1 if no rack is present
         */
        int rackOffset();

        int rackLength();
    }

    interface Topic
    {
        short error();

        int nameOffset();

        int nameLength();

        boolean isInternal();

        int partitionCount();
    }

    interface Partition
    {
        short error();

        int partitionId();

        int leader();

        int leaderEpoch();

        int replicaCount();

        PrimitiveIterator.OfInt replicas();

        int isrCount();

        PrimitiveIterator.OfInt isr();

        int offlineReplicaCount();

        PrimitiveIterator.OfInt offlineReplicas();
    }
}
