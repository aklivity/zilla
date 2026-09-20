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
package io.aklivity.zilla.specs.binding.llm.internal;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import java.nio.ByteBuffer;

import org.junit.Test;

import io.aklivity.k3po.runtime.lang.el.BytesMatcher;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmAbortExFW;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmBeginExFW;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmDataExFW;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmEndExFW;

public class LlmFunctionsTest
{
    @Test
    public void shouldGetPrefixName()
    {
        assertNotNull(new LlmFunctions.Mapper().getPrefixName());
    }

    @Test
    public void shouldGenerateBeginEx()
    {
        byte[] bytes = LlmFunctions.beginEx()
            .typeId(0)
            .dialect("openai")
            .contentType("application/json")
            .model("gpt-x")
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldGenerateBeginExWithoutOptionalFields()
    {
        byte[] bytes = LlmFunctions.beginEx()
            .typeId(0)
            .dialect("openai")
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldMatchBeginEx() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchBeginEx()
            .typeId(0)
            .dialect("openai")
            .contentType("application/json")
            .model("gpt-x")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmBeginExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .dialect("openai")
            .contentType("application/json")
            .model("gpt-x")
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchBeginExMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchBeginEx()
            .typeId(0)
            .dialect("openai")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmBeginExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .dialect("anthropic")
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateDataExWithAllFields()
    {
        byte[] bytes = LlmFunctions.dataEx()
            .typeId(0)
            .type("message")
            .logProbability("-0.1")
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldGenerateDataExWithoutOptionalFields()
    {
        byte[] bytes = LlmFunctions.dataEx()
            .typeId(0)
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldMatchDataExWithoutConstraints() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchDataEx()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmDataExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .type("message")
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchDataExType() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchDataEx()
            .typeId(0)
            .type("message")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmDataExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .type("message")
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchDataExNullFields() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchDataEx()
            .typeId(0)
            .typeNull()
            .logProbabilityNull()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmDataExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchDataExTypeIdMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchDataEx()
            .typeId(1)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmDataExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchDataExTypeMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchDataEx()
            .typeId(0)
            .type("message")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmDataExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .type("other")
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchDataExTypeNotNullMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchDataEx()
            .typeId(0)
            .typeNull()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmDataExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .type("message")
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchDataExLogProbabilityMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchDataEx()
            .typeId(0)
            .logProbability("-0.1")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmDataExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .logProbability("-0.9")
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchDataExLogProbabilityNotNullMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchDataEx()
            .typeId(0)
            .logProbabilityNull()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmDataExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .logProbability("-0.1")
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateEndExWithAllFields()
    {
        byte[] bytes = LlmFunctions.endEx()
            .typeId(0)
            .inputTokens(25)
            .cacheWriteTokens(5)
            .cacheReadTokens(10)
            .outputTokens(15)
            .reasoningTokens(8)
            .totalTokens(40)
            .nativeUsage("{\"prompt_tokens\":25,\"completion_tokens\":15}")
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldGenerateEndExWithoutOptionalFields()
    {
        byte[] bytes = LlmFunctions.endEx()
            .typeId(0)
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldMatchEndExTokens() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchEndEx()
            .typeId(0)
            .inputTokens(25)
            .cacheWriteTokens(5)
            .cacheReadTokens(10)
            .outputTokens(15)
            .reasoningTokens(8)
            .totalTokens(40)
            .nativeUsage("{\"prompt_tokens\":25}")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmEndExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(25).cacheWriteTokens(5).cacheReadTokens(10).outputTokens(15)
                .reasoningTokens(8).totalTokens(40).nativeUsage("{\"prompt_tokens\":25}"))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchEndExWithoutConstraints() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchEndEx()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmEndExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(25).cacheWriteTokens(-1).cacheReadTokens(-1).outputTokens(15)
                .reasoningTokens(-1).totalTokens(-1))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchEndExNativeUsageNull() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchEndEx()
            .typeId(0)
            .nativeUsageNull()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmEndExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(-1).cacheWriteTokens(-1).cacheReadTokens(-1).outputTokens(-1)
                .reasoningTokens(-1).totalTokens(-1))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchEndExTypeIdMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchEndEx()
            .typeId(1)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmEndExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(-1).cacheWriteTokens(-1).cacheReadTokens(-1).outputTokens(-1)
                .reasoningTokens(-1).totalTokens(-1))
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchEndExInputTokensMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchEndEx()
            .typeId(0)
            .inputTokens(25)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmEndExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(30).cacheWriteTokens(-1).cacheReadTokens(-1).outputTokens(-1)
                .reasoningTokens(-1).totalTokens(-1))
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchEndExCacheWriteTokensMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchEndEx()
            .typeId(0)
            .cacheWriteTokens(5)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmEndExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(-1).cacheWriteTokens(9).cacheReadTokens(-1).outputTokens(-1)
                .reasoningTokens(-1).totalTokens(-1))
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchEndExCacheReadTokensMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchEndEx()
            .typeId(0)
            .cacheReadTokens(10)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmEndExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(-1).cacheWriteTokens(-1).cacheReadTokens(20).outputTokens(-1)
                .reasoningTokens(-1).totalTokens(-1))
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchEndExOutputTokensMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchEndEx()
            .typeId(0)
            .outputTokens(15)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmEndExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(-1).cacheWriteTokens(-1).cacheReadTokens(-1).outputTokens(20)
                .reasoningTokens(-1).totalTokens(-1))
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchEndExReasoningTokensMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchEndEx()
            .typeId(0)
            .reasoningTokens(8)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmEndExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(-1).cacheWriteTokens(-1).cacheReadTokens(-1).outputTokens(-1)
                .reasoningTokens(2).totalTokens(-1))
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchEndExTotalTokensMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchEndEx()
            .typeId(0)
            .totalTokens(40)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmEndExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(-1).cacheWriteTokens(-1).cacheReadTokens(-1).outputTokens(-1)
                .reasoningTokens(-1).totalTokens(10))
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchEndExNativeUsageMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchEndEx()
            .typeId(0)
            .nativeUsage("{\"prompt_tokens\":25}")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmEndExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(-1).cacheWriteTokens(-1).cacheReadTokens(-1).outputTokens(-1)
                .reasoningTokens(-1).totalTokens(-1).nativeUsage("{\"prompt_tokens\":30}"))
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchEndExNativeUsageNotNullMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchEndEx()
            .typeId(0)
            .nativeUsageNull()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmEndExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(-1).cacheWriteTokens(-1).cacheReadTokens(-1).outputTokens(-1)
                .reasoningTokens(-1).totalTokens(-1).nativeUsage("{\"prompt_tokens\":30}"))
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateAbortExWithAllFields()
    {
        byte[] bytes = LlmFunctions.abortEx()
            .typeId(0)
            .inputTokens(25)
            .cacheWriteTokens(5)
            .cacheReadTokens(10)
            .outputTokens(5)
            .reasoningTokens(2)
            .totalTokens(30)
            .nativeUsage("{\"input_tokens\":25}")
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldGenerateAbortExWithoutOptionalFields()
    {
        byte[] bytes = LlmFunctions.abortEx()
            .typeId(0)
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldMatchAbortExTokens() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchAbortEx()
            .typeId(0)
            .inputTokens(25)
            .cacheWriteTokens(5)
            .cacheReadTokens(10)
            .outputTokens(5)
            .reasoningTokens(2)
            .totalTokens(30)
            .nativeUsage("{\"input_tokens\":25}")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmAbortExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(25).cacheWriteTokens(5).cacheReadTokens(10).outputTokens(5)
                .reasoningTokens(2).totalTokens(30).nativeUsage("{\"input_tokens\":25}"))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchAbortExWithoutConstraints() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchAbortEx()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmAbortExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(25).cacheWriteTokens(-1).cacheReadTokens(-1).outputTokens(-1)
                .reasoningTokens(-1).totalTokens(-1))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchAbortExNativeUsageNull() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchAbortEx()
            .typeId(0)
            .nativeUsageNull()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmAbortExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(-1).cacheWriteTokens(-1).cacheReadTokens(-1).outputTokens(-1)
                .reasoningTokens(-1).totalTokens(-1))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchAbortExTypeIdMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchAbortEx()
            .typeId(1)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmAbortExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(-1).cacheWriteTokens(-1).cacheReadTokens(-1).outputTokens(-1)
                .reasoningTokens(-1).totalTokens(-1))
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchAbortExInputTokensMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchAbortEx()
            .typeId(0)
            .inputTokens(25)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmAbortExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(5).cacheWriteTokens(-1).cacheReadTokens(-1).outputTokens(-1)
                .reasoningTokens(-1).totalTokens(-1))
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchAbortExCacheWriteTokensMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchAbortEx()
            .typeId(0)
            .cacheWriteTokens(5)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmAbortExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(-1).cacheWriteTokens(9).cacheReadTokens(-1).outputTokens(-1)
                .reasoningTokens(-1).totalTokens(-1))
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchAbortExCacheReadTokensMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchAbortEx()
            .typeId(0)
            .cacheReadTokens(10)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmAbortExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(-1).cacheWriteTokens(-1).cacheReadTokens(20).outputTokens(-1)
                .reasoningTokens(-1).totalTokens(-1))
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchAbortExOutputTokensMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchAbortEx()
            .typeId(0)
            .outputTokens(5)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmAbortExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(-1).cacheWriteTokens(-1).cacheReadTokens(-1).outputTokens(9)
                .reasoningTokens(-1).totalTokens(-1))
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchAbortExReasoningTokensMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchAbortEx()
            .typeId(0)
            .reasoningTokens(2)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmAbortExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(-1).cacheWriteTokens(-1).cacheReadTokens(-1).outputTokens(-1)
                .reasoningTokens(7).totalTokens(-1))
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchAbortExTotalTokensMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchAbortEx()
            .typeId(0)
            .totalTokens(30)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmAbortExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(-1).cacheWriteTokens(-1).cacheReadTokens(-1).outputTokens(-1)
                .reasoningTokens(-1).totalTokens(10))
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchAbortExNativeUsageMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchAbortEx()
            .typeId(0)
            .nativeUsage("{\"input_tokens\":25}")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmAbortExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(-1).cacheWriteTokens(-1).cacheReadTokens(-1).outputTokens(-1)
                .reasoningTokens(-1).totalTokens(-1).nativeUsage("{\"input_tokens\":5}"))
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchAbortExNativeUsageNotNullMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchAbortEx()
            .typeId(0)
            .nativeUsageNull()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmAbortExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(-1).cacheWriteTokens(-1).cacheReadTokens(-1).outputTokens(-1)
                .reasoningTokens(-1).totalTokens(-1).nativeUsage("{\"input_tokens\":5}"))
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }
}
