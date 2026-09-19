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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

final class LlmEventMapperTestSupport implements LlmCanonicalOutput, LlmNativeEventOutput
{
    final List<String> trace = new ArrayList<>();

    @Override
    public void messageStart(
        int choiceIndex,
        String id,
        String model,
        String role)
    {
        trace.add(String.format("messageStart:%d:%s:%s:%s", choiceIndex, id, model, role));
    }

    @Override
    public void blockStart(
        int choiceIndex,
        int blockId,
        LlmCanonicalBlockKind type,
        String toolId,
        String toolName)
    {
        trace.add(String.format("blockStart:%d:%d:%s:%s:%s", choiceIndex, blockId, type, toolId, toolName));
    }

    @Override
    public void data(
        DirectBuffer buffer,
        int offset,
        int length)
    {
        trace.add("data:" + buffer.getStringWithoutLengthUtf8(offset, length));
    }

    @Override
    public void blockEnd(
        int choiceIndex,
        int blockId)
    {
        trace.add(String.format("blockEnd:%d:%d", choiceIndex, blockId));
    }

    @Override
    public void finish(
        int choiceIndex,
        LlmCanonicalFinishReason reason)
    {
        trace.add(String.format("finish:%d:%s", choiceIndex, reason));
    }

    @Override
    public void usage(
        int inputTokens,
        int outputTokens)
    {
        trace.add(String.format("usage:%d:%d", inputTokens, outputTokens));
    }

    @Override
    public void end()
    {
        trace.add("end");
    }

    @Override
    public void event(
        String name,
        DirectBuffer buffer,
        int offset,
        int length)
    {
        trace.add("event:" + name + ":" + buffer.getStringWithoutLengthUtf8(offset, length));
    }

    DirectBuffer utf8(
        String text)
    {
        return new UnsafeBuffer(text.getBytes(StandardCharsets.UTF_8));
    }
}
