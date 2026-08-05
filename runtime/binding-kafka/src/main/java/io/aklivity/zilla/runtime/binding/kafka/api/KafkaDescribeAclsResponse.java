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
 * A resumable, allocation-free cursor over a decoded DescribeAcls response.
 * <p>
 * The caller drives the cursor: {@code while (hasNext()) { switch (next()) { ... } }}, mirroring
 * {@link KafkaDescribeConfigsResponse}'s {@code Kind}-discriminated shape since each resource nests a
 * variable-length ACLs array. Unlike DescribeConfigs, DescribeAcls carries a request-level error and
 * message (a single filter can itself be rejected, e.g. an unsupported resource type), surfaced by
 * {@link #error()}/{@link #messageOffset()}/{@link #messageLength()} rather than through the cursor.
 * </p>
 */
public interface KafkaDescribeAclsResponse
{
    enum Kind
    {
        RESOURCE,
        ACL
    }

    DirectBufferEx buffer();

    int throttleTimeMillis();

    short error();

    /**
     * @return -1 if no error message is present
     */
    int messageOffset();

    int messageLength();

    int resourceCount();

    /**
     * @return {@code true} if {@link #next()} has another resource or ACL to report
     */
    boolean hasNext();

    /**
     * Advances the cursor and returns which kind of entry it landed on; valid only until the next
     * call. Call {@link #resource()} or {@link #acl()} to read the entry itself.
     */
    Kind next();

    Resource resource();

    Acl acl();

    interface Resource
    {
        DirectBufferEx buffer();

        byte type();

        int nameOffset();

        int nameLength();

        byte patternType();

        int aclCount();
    }

    interface Acl
    {
        DirectBufferEx buffer();

        int principalOffset();

        int principalLength();

        int hostOffset();

        int hostLength();

        byte operation();

        byte permissionType();
    }
}
