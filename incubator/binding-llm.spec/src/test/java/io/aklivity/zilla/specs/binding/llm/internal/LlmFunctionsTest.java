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
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmBeginExFW;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmDataExFW;

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
            .dialect("test-permissive")
            .contentType("application/vnd.zilla.test-permissive+json")
            .model("gpt-x")
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldGenerateBeginExWithoutOptionalFields()
    {
        byte[] bytes = LlmFunctions.beginEx()
            .typeId(0)
            .dialect("test-permissive")
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldMatchBeginEx() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchBeginEx()
            .typeId(0)
            .dialect("test-permissive")
            .contentType("application/vnd.zilla.test-permissive+json")
            .model("gpt-x")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmBeginExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .dialect("test-permissive")
            .contentType("application/vnd.zilla.test-permissive+json")
            .model("gpt-x")
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchBeginExMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchBeginEx()
            .typeId(0)
            .dialect("test-strict")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmBeginExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .dialect("test-permissive")
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
            .inputTokens(25)
            .outputTokens(15)
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
    public void shouldMatchDataExTokens() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchDataEx()
            .typeId(0)
            .inputTokens(25)
            .outputTokens(15)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmDataExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .inputTokens(25)
            .outputTokens(15)
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
    public void shouldFailMatchDataExInputTokensMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchDataEx()
            .typeId(0)
            .inputTokens(25)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmDataExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .inputTokens(30)
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchDataExOutputTokensMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchDataEx()
            .typeId(0)
            .outputTokens(15)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmDataExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .outputTokens(20)
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }
}
