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

/**
 * Receives the canonical events a dialect mapper's {@code decode} produces from a dialect's native
 * event sequence, purely in-process -- never itself serialized or carried on any wire. {@code buffer}
 * is only valid for the duration of the {@link #data(DirectBuffer, int, int)} call; a mapper reuses
 * its backing storage on the next event.
 */
public interface LlmCanonicalOutput
{
    void messageStart(
        int choiceIndex,
        String id,
        String model,
        String role);

    void blockStart(
        int choiceIndex,
        int blockId,
        LlmCanonicalBlockKind type,
        String toolId,
        String toolName);

    void data(
        DirectBuffer buffer,
        int offset,
        int length);

    void blockEnd(
        int choiceIndex,
        int blockId);

    void finish(
        int choiceIndex,
        LlmCanonicalFinishReason reason);

    void usage(
        int inputTokens,
        int outputTokens);

    void end();
}
