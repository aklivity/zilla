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
 * A resumable, allocation-free cursor over a decoded DescribeGroups response.
 * <p>
 * The caller drives the cursor: {@code while (hasNext()) { switch (next()) { ... } }}, reading
 * {@link #group()} or {@link #member()} after each {@link #next()} call. Accessors are valid only
 * until the next {@link #next()} call.
 * </p>
 */
public interface KafkaDescribeGroupsResponse
{
    enum Kind
    {
        GROUP,
        MEMBER
    }

    int throttleTimeMillis();

    int groupCount();

    /**
     * @return {@code true} if {@link #next()} has another group or member to report
     */
    boolean hasNext();

    /**
     * Advances to the next group or member in the response.
     *
     * @return the kind of the item now current, readable via {@link #group()} or {@link #member()}
     */
    Kind next();

    /**
     * @return the current group; valid only after {@link #next()} returns {@link Kind#GROUP}
     */
    Group group();

    /**
     * @return the current member; valid only after {@link #next()} returns {@link Kind#MEMBER}
     */
    Member member();

    interface Group
    {
        DirectBufferEx buffer();

        short error();

        int groupIdOffset();

        int groupIdLength();

        int groupStateOffset();

        int groupStateLength();

        int protocolTypeOffset();

        int protocolTypeLength();

        int protocolDataOffset();

        int protocolDataLength();

        int memberCount();
    }

    interface Member
    {
        DirectBufferEx buffer();

        int memberIdOffset();

        int memberIdLength();

        /**
         * @return -1 if no static group instance id is present
         */
        int groupInstanceIdOffset();

        int groupInstanceIdLength();

        int clientIdOffset();

        int clientIdLength();

        int clientHostOffset();

        int clientHostLength();

        /**
         * @return -1 if no member metadata is present
         */
        int memberMetadataOffset();

        int memberMetadataLength();

        /**
         * @return -1 if no member assignment is present
         */
        int memberAssignmentOffset();

        int memberAssignmentLength();
    }
}
