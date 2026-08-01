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
 * A resumable, allocation-free cursor over a decoded CreateAcls response.
 * <p>
 * The caller drives the cursor: {@code while (hasNext()) { Result result = next(); ... }}.
 * Accessors are valid only until the next {@link #next()} call. Like {@link KafkaAlterConfigsResponse},
 * CreateAcls has exactly one result shape per entry, so there is no {@code Kind} discriminator.
 * </p>
 */
public interface KafkaCreateAclsResponse
{
    int throttleTimeMillis();

    int resultCount();

    /**
     * @return {@code true} if {@link #next()} has another result to report
     */
    boolean hasNext();

    /**
     * Advances to and returns the next result in the response; valid only until the next call.
     */
    Result next();

    interface Result
    {
        DirectBufferEx buffer();

        short error();

        /**
         * @return -1 if no error message is present
         */
        int messageOffset();

        int messageLength();
    }
}
