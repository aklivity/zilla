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
package io.aklivity.zilla.specs.binding.agui.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.nio.ByteBuffer;

import org.junit.Test;

import io.aklivity.k3po.runtime.lang.el.BytesMatcher;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiBeginExFW;

public class AguiFunctionsTest
{
    @Test
    public void shouldGetPrefixName()
    {
        assertEquals("agui", new AguiFunctions.Mapper().getPrefixName());
    }

    @Test
    public void shouldGenerateBeginEx()
    {
        byte[] bytes = AguiFunctions.beginEx()
            .typeId(0x01)
            .threadId("thread-1")
            .runId("run-1")
            .parentRunId("run-0")
            .build();

        AguiBeginExFW beginEx = new AguiBeginExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertEquals(0x01, beginEx.typeId());
        assertEquals("thread-1", beginEx.threadId().asString());
        assertEquals("run-1", beginEx.runId().asString());
        assertEquals("run-0", beginEx.parentRunId().asString());
    }

    @Test
    public void shouldGenerateBeginExWithoutParentRunId()
    {
        byte[] bytes = AguiFunctions.beginEx()
            .typeId(0x01)
            .threadId("thread-1")
            .runId("run-1")
            .build();

        AguiBeginExFW beginEx = new AguiBeginExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertNull(beginEx.parentRunId().asString());
    }

    @Test
    public void shouldMatchBeginEx() throws Exception
    {
        BytesMatcher matcher = AguiFunctions.matchBeginEx()
            .typeId(0x01)
            .threadId("thread-1")
            .runId("run-1")
            .parentRunId("run-0")
            .build();

        ByteBuffer byteBuf = ByteBuffer.wrap(AguiFunctions.beginEx()
            .typeId(0x01)
            .threadId("thread-1")
            .runId("run-1")
            .parentRunId("run-0")
            .build());

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchBeginExWithoutConstraints() throws Exception
    {
        BytesMatcher matcher = AguiFunctions.matchBeginEx().build();

        ByteBuffer byteBuf = ByteBuffer.wrap(AguiFunctions.beginEx()
            .typeId(0x01)
            .threadId("thread-1")
            .runId("run-1")
            .build());

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldNotMatchEmptyBeginEx() throws Exception
    {
        BytesMatcher matcher = AguiFunctions.matchBeginEx().build();

        assertNull(matcher.match(ByteBuffer.allocate(0)));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchBeginExWithDifferentRunId() throws Exception
    {
        BytesMatcher matcher = AguiFunctions.matchBeginEx()
            .runId("run-2")
            .build();

        matcher.match(ByteBuffer.wrap(AguiFunctions.beginEx()
            .typeId(0x01)
            .threadId("thread-1")
            .runId("run-1")
            .build()));
    }
}
