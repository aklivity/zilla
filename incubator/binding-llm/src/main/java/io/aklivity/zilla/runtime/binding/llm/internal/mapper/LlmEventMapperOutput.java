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
package io.aklivity.zilla.runtime.binding.llm.internal.mapper;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmDataExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmFlushExFW;

/**
 * Receives the canonical events a dialect mapper's {@code decode} produces from a
 * dialect's native event sequence. {@code flushEx} and {@code dataEx} are only valid
 * for the duration of the call; the mapper reuses its backing buffer on the next event.
 */
public interface LlmEventMapperOutput
{
    void data(
        DirectBuffer buffer,
        int offset,
        int length,
        LlmDataExFW dataEx);

    void flush(
        LlmFlushExFW flushEx);

    void end();
}
