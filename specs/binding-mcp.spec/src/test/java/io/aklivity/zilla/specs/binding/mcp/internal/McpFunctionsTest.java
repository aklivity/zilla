/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.specs.binding.mcp.internal;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.nio.ByteBuffer;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.k3po.runtime.lang.el.BytesMatcher;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpBeginExFW;

public class McpFunctionsTest
{
    @Test
    public void shouldGetPrefixName()
    {
        assertNotNull(new McpFunctions.Mapper().getPrefixName());
    }

    @Test
    public void shouldGenerateBeginExtension()
    {
        byte[] bytes = McpFunctions.beginEx()
            .typeId(0x01)
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldGenerateBeginExtensionWithSessionId()
    {
        byte[] bytes = McpFunctions.beginEx()
            .typeId(0x01)
            .sessionId("session-123")
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldGenerateBeginExtensionWithMethod()
    {
        byte[] bytes = McpFunctions.beginEx()
            .typeId(0x01)
            .method("initialize")
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldMatchBeginExtension() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchBeginEx()
            .typeId(0x01)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchBeginExtensionWithoutTypeId() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchBeginEx()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchBeginExtensionWithSessionId() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchBeginEx()
            .typeId(0x01)
            .sessionId("session-123")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .sessionId("session-123")
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchBeginExtensionWithMethod() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchBeginEx()
            .typeId(0x01)
            .method("initialize")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .method("initialize")
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchBeginExtensionWithoutMethod() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchBeginEx()
            .typeId(0x01)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .method("initialize")
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldFailWhenTypeIdDoesNotMatch() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchBeginEx()
            .typeId(0x01)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x02)
            .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldFailWhenSessionIdDoesNotMatch() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchBeginEx()
            .typeId(0x01)
            .sessionId("session-456")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .sessionId("session-123")
            .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldFailWhenMethodDoesNotMatch() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchBeginEx()
            .typeId(0x01)
            .method("ping")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .method("initialize")
            .build();

        matcher.match(byteBuf);
    }

    @Test
    public void shouldReturnNullWhenBufferIsEmpty() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchBeginEx()
            .typeId(0x01)
            .build();

        assertNull(matcher.match(ByteBuffer.allocate(0)));
    }
}
