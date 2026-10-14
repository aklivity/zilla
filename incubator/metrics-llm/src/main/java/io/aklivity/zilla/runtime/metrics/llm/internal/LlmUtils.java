/*
 * Copyright 2021-2026 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.metrics.llm.internal;

final class LlmUtils
{
    static final long RECEIVED = 1L;
    static final long SENT = 0L;

    private LlmUtils()
    {
    }

    static long initialId(
        long streamId)
    {
        return streamId & ~0b01L;
    }

    static long direction(
        long streamId)
    {
        return streamId & 0b01L;
    }
}
