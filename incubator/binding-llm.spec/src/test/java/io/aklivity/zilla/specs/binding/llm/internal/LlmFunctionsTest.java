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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.nio.ByteBuffer;

import org.junit.Test;

import io.aklivity.k3po.runtime.lang.el.BytesMatcher;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmBeginExFW;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmDataExFW;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmFlushExFW;

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
            .build();

        assertNotNull(bytes);

        LlmBeginExFW beginEx = new LlmBeginExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
        assertNotNull(beginEx.dialect());
    }

    @Test
    public void shouldMatchBeginEx() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchBeginEx()
            .typeId(0)
            .dialect("openai")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmBeginExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .dialect("openai")
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldNotMatchBeginExWithDifferentDialect()
    {
        BytesMatcher matcher = LlmFunctions.matchBeginEx()
            .typeId(0)
            .dialect("anthropic")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmBeginExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .dialect("openai")
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldSkipMatchBeginExWhenNoFieldsSet() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchBeginEx().build();

        assertNull(matcher.match(ByteBuffer.allocate(0)));
    }

    @Test
    public void shouldGenerateDataEx()
    {
        byte[] bytes = LlmFunctions.dataEx()
            .typeId(0)
            .logProbability("-0.25")
            .build();

        assertNotNull(bytes);

        LlmDataExFW dataEx = new LlmDataExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
        assertNotNull(dataEx.logProbability());
    }

    @Test
    public void shouldMatchDataEx() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchDataEx()
            .typeId(0)
            .logProbability("-0.25")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmDataExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .logProbability("-0.25")
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchDataExLogProbabilityNull() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchDataEx()
            .typeId(0)
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
    public void shouldSkipMatchDataExWhenNoFieldsSet() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchDataEx().build();

        assertNull(matcher.match(ByteBuffer.allocate(0)));
    }

    @Test
    public void shouldGenerateRawFlushEx()
    {
        byte[] bytes = LlmFunctions.flushEx()
            .typeId(0)
            .raw()
                .choiceIndex(0)
                .type("message")
                .payload("hello")
                .build()
            .build();

        assertNotNull(bytes);

        LlmFlushExFW flushEx = new LlmFlushExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
        assertNotNull(flushEx.raw());
    }

    @Test
    public void shouldGenerateRawFlushExWithNoTypeOrPayload()
    {
        byte[] bytes = LlmFunctions.flushEx()
            .typeId(0)
            .raw()
                .choiceIndex(0)
                .build()
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldMatchRawFlushEx() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchFlushEx()
            .typeId(0)
            .raw()
                .choiceIndex(0)
                .type("message")
                .payload("hello")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmFlushExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .raw(r -> r.choiceIndex(0)
                .type("message")
                .payload(new UnsafeBufferEx("hello".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    0, "hello".length()))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchRawFlushExWithNullTypeAndPayload() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchFlushEx()
            .typeId(0)
            .raw()
                .choiceIndex(0)
                .typeNull()
                .payloadNull()
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmFlushExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .raw(r -> r.choiceIndex(0))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldNotMatchRawFlushExWithDifferentType()
    {
        BytesMatcher matcher = LlmFunctions.matchFlushEx()
            .typeId(0)
            .raw()
                .type("message")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmFlushExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .raw(r -> r.choiceIndex(0).type("usage"))
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldSkipMatchFlushExWhenNoFieldsSet() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchFlushEx().build();

        assertNull(matcher.match(ByteBuffer.allocate(0)));
    }
}
