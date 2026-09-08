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

import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmBlockEndFlushExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmBlockStartFlushExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmBlockType;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmDataExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmFinishFlushExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmFinishReason;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmFlushExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmMessageStartFlushExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmUsageFlushExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

final class LlmEventMapperTestSupport implements LlmEventMapperOutput, LlmNativeEventOutput
{
    private static final int TYPE_ID = 1;

    private final MutableDirectBufferEx flushExBuffer = new UnsafeBufferEx(new byte[512]);
    private final LlmFlushExFW.Builder flushExRW = new LlmFlushExFW.Builder();

    final List<String> trace = new ArrayList<>();

    @Override
    public void data(
        DirectBuffer buffer,
        int offset,
        int length,
        LlmDataExFW dataEx)
    {
        trace.add("data:" + buffer.getStringWithoutLengthUtf8(offset, length));
    }

    @Override
    public void flush(
        LlmFlushExFW flushEx)
    {
        switch (flushEx.kind())
        {
        case LlmFlushExFW.KIND_MESSAGE_START:
        {
            LlmMessageStartFlushExFW m = flushEx.messageStart();
            trace.add(String.format("messageStart:%d:%s:%s:%s",
                m.choiceIndex(), m.id().asString(), m.model().asString(), m.role().asString()));
            break;
        }
        case LlmFlushExFW.KIND_BLOCK_START:
        {
            LlmBlockStartFlushExFW b = flushEx.blockStart();
            trace.add(String.format("blockStart:%d:%d:%s:%s:%s",
                b.choiceIndex(), b.blockId(), b.type().get(), b.toolId().asString(), b.toolName().asString()));
            break;
        }
        case LlmFlushExFW.KIND_BLOCK_END:
        {
            LlmBlockEndFlushExFW b = flushEx.blockEnd();
            trace.add(String.format("blockEnd:%d:%d", b.choiceIndex(), b.blockId()));
            break;
        }
        case LlmFlushExFW.KIND_FINISH:
        {
            LlmFinishFlushExFW f = flushEx.finish();
            trace.add(String.format("finish:%d:%s", f.choiceIndex(), f.reason().get()));
            break;
        }
        case LlmFlushExFW.KIND_USAGE:
        {
            LlmUsageFlushExFW u = flushEx.usage();
            trace.add(String.format("usage:%d:%d", u.inputTokens(), u.outputTokens()));
            break;
        }
        case LlmFlushExFW.KIND_KEEPALIVE:
            trace.add("keepalive");
            break;
        default:
            trace.add("raw");
            break;
        }
    }

    @Override
    public void end()
    {
        trace.add("end");
    }

    @Override
    public void event(
        String name,
        String data)
    {
        trace.add("event:" + name + ":" + data);
    }

    DirectBuffer utf8(
        String text)
    {
        return new UnsafeBuffer(text.getBytes(StandardCharsets.UTF_8));
    }

    LlmFlushExFW messageStart(
        int choiceIndex,
        String id,
        String model,
        String role)
    {
        return flushExRW
            .wrap(flushExBuffer, 0, flushExBuffer.capacity())
            .typeId(TYPE_ID)
            .messageStart(m -> m.choiceIndex(choiceIndex).id(id).model(model).role(role))
            .build();
    }

    LlmFlushExFW blockStart(
        int choiceIndex,
        int blockId,
        LlmBlockType type,
        String toolId,
        String toolName)
    {
        return flushExRW
            .wrap(flushExBuffer, 0, flushExBuffer.capacity())
            .typeId(TYPE_ID)
            .blockStart(b -> b
                .choiceIndex(choiceIndex)
                .blockId(blockId)
                .type(t -> t.set(type))
                .toolId(toolId)
                .toolName(toolName))
            .build();
    }

    LlmFlushExFW blockEnd(
        int choiceIndex,
        int blockId)
    {
        return flushExRW
            .wrap(flushExBuffer, 0, flushExBuffer.capacity())
            .typeId(TYPE_ID)
            .blockEnd(b -> b.choiceIndex(choiceIndex).blockId(blockId))
            .build();
    }

    LlmFlushExFW finish(
        int choiceIndex,
        LlmFinishReason reason)
    {
        return flushExRW
            .wrap(flushExBuffer, 0, flushExBuffer.capacity())
            .typeId(TYPE_ID)
            .finish(f -> f.choiceIndex(choiceIndex).reason(r -> r.set(reason)))
            .build();
    }

    LlmFlushExFW usage(
        int inputTokens,
        int outputTokens)
    {
        return flushExRW
            .wrap(flushExBuffer, 0, flushExBuffer.capacity())
            .typeId(TYPE_ID)
            .usage(u -> u.inputTokens(inputTokens).outputTokens(outputTokens))
            .build();
    }

    LlmFlushExFW keepalive()
    {
        return flushExRW
            .wrap(flushExBuffer, 0, flushExBuffer.capacity())
            .typeId(TYPE_ID)
            .keepalive(k ->
            {
            })
            .build();
    }
}
