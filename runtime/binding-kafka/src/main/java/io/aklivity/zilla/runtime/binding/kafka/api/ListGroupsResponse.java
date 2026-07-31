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
 * A resumable, allocation-free cursor over a decoded ListGroups response.
 * <p>
 * The caller drives the cursor: {@code while (hasNext()) { Group group = next(); ... }}. Accessors
 * are valid only until the next {@link #next()} call.
 * </p>
 */
public interface ListGroupsResponse
{
    short error();

    int groupCount();

    /**
     * @return {@code true} if {@link #next()} has another group to report
     */
    boolean hasNext();

    /**
     * Advances to and returns the next group in the response; valid only until the next call.
     */
    Group next();

    interface Group
    {
        DirectBufferEx buffer();

        int groupIdOffset();

        int groupIdLength();

        int protocolTypeOffset();

        int protocolTypeLength();

        int groupStateOffset();

        int groupStateLength();
    }
}
