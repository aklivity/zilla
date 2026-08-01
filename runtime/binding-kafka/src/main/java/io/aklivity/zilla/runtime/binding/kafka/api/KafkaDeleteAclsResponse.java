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
 * A resumable, allocation-free cursor over a decoded DeleteAcls response.
 * <p>
 * The caller drives the cursor: {@code while (hasNext()) { switch (next()) { ... } }}, mirroring
 * {@link KafkaDescribeAclsResponse}'s {@code Kind}-discriminated shape since each filter result nests
 * a variable-length matching-ACLs array.
 * </p>
 */
public interface KafkaDeleteAclsResponse
{
    enum Kind
    {
        FILTER_RESULT,
        MATCHING_ACL
    }

    int throttleTimeMillis();

    int filterResultCount();

    /**
     * @return {@code true} if {@link #next()} has another filter result or matching ACL to report
     */
    boolean hasNext();

    /**
     * Advances the cursor and returns which kind of entry it landed on; valid only until the next
     * call. Call {@link #filterResult()} or {@link #matchingAcl()} to read the entry itself.
     */
    Kind next();

    FilterResult filterResult();

    MatchingAcl matchingAcl();

    interface FilterResult
    {
        DirectBufferEx buffer();

        short error();

        /**
         * @return -1 if no error message is present
         */
        int messageOffset();

        int messageLength();

        int matchingAclCount();
    }

    interface MatchingAcl
    {
        DirectBufferEx buffer();

        short error();

        /**
         * @return -1 if no error message is present
         */
        int messageOffset();

        int messageLength();

        byte resourceType();

        int resourceNameOffset();

        int resourceNameLength();

        byte patternType();

        int principalOffset();

        int principalLength();

        int hostOffset();

        int hostLength();

        byte operation();

        byte permissionType();
    }
}
