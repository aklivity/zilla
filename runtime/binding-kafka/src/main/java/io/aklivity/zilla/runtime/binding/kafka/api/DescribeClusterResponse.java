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
 * A resumable, allocation-free cursor over a decoded DescribeCluster response.
 * <p>
 * The caller drives the cursor: {@code while (hasNext()) { Broker broker = next(); ... }}. Accessors
 * are valid only until the next {@link #next()} call. Like {@link DeleteTopicsResponse}, DescribeCluster
 * has exactly one result shape per entry, so there is no {@code Kind} discriminator. {@link #authorizedOperations()}
 * is only valid once {@link #hasNext()} has returned {@code false} at least once, since it is decoded from the
 * response footer that follows the last broker entry.
 * </p>
 */
public interface DescribeClusterResponse
{
    int throttleTimeMillis();

    short error();

    int messageOffset();

    /**
     * @return -1 if no error message is present
     */
    int messageLength();

    int clusterIdOffset();

    /**
     * @return -1 if the cluster id is null
     */
    int clusterIdLength();

    int controllerId();

    int brokerCount();

    /**
     * @return {@code true} if {@link #next()} has another broker to report
     */
    boolean hasNext();

    /**
     * Advances to and returns the next broker in the response; valid only until the next call.
     */
    Broker next();

    /**
     * @return the cluster's authorized operations bitfield; valid only once {@link #hasNext()} has
     *         returned {@code false}
     */
    int authorizedOperations();

    interface Broker
    {
        DirectBufferEx buffer();

        int brokerId();

        int hostOffset();

        int hostLength();

        int port();

        int rackOffset();

        /**
         * @return -1 if the rack is null
         */
        int rackLength();
    }
}
