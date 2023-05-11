/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.specs.binding.filesystem.internal;

import static io.aklivity.zilla.specs.binding.filesystem.internal.types.FileSystemCapabilities.READ_EXTENSION;
import static io.aklivity.zilla.specs.binding.filesystem.internal.types.FileSystemCapabilities.READ_PAYLOAD;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import javax.el.ELContext;
import javax.el.FunctionMapper;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;
import org.kaazing.k3po.lang.el.BytesMatcher;
import org.kaazing.k3po.lang.internal.el.ExpressionContext;

import io.aklivity.zilla.specs.binding.filesystem.internal.types.stream.FileSystemBeginExFW;

public class FileSystemFunctionsTest
{
    @Test
    public void shouldResolveFunction() throws Exception
    {
        final ELContext ctx = new ExpressionContext();
        final FunctionMapper mapper = ctx.getFunctionMapper();
        final Method function = mapper.resolveFunction("filesystem", "beginEx");

        assertNotNull(function);
        assertSame(FileSystemFunctions.class, function.getDeclaringClass());
    }

    @Test
    public void shouldGenerateBeginExtension()
    {
        byte[] build = FileSystemFunctions.beginEx()
            .typeId(0x01)
            .capabilities("READ_PAYLOAD")
            .path("index.html")
            .type("text/html")
            .payloadSize(77L)
            .tag("AAAAAAAAAAAAAAAA")
            .timeout(60)
            .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        FileSystemBeginExFW beginEx = new FileSystemBeginExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0x01, beginEx.typeId());
        assertEquals("index.html", beginEx.path().asString());
        assertEquals(1 << READ_PAYLOAD.ordinal(), beginEx.capabilities());
        assertEquals(77L, beginEx.payloadSize());
    }

    @Test
    public void shouldMatchBeginExtension() throws Exception
    {
        BytesMatcher matcher = FileSystemFunctions.matchBeginEx()
            .typeId(0x01)
            .capabilities("READ_PAYLOAD")
            .path("index.html")
            .type("text/html")
            .payloadSize(77L)
            .tag("AAAAAAAAAAAAAAAA")
            .timeout(60)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new FileSystemBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .capabilities(1 << READ_PAYLOAD.ordinal())
            .path("index.html")
            .type("text/html")
            .payloadSize(77L)
            .tag("AAAAAAAAAAAAAAAA")
            .timeout(60)
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchBeginExtensionWhenUnconstrained() throws Exception
    {
        BytesMatcher matcher = FileSystemFunctions.matchBeginEx()
            .typeId(0x01)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new FileSystemBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .capabilities(1 << READ_PAYLOAD.ordinal())
            .path("index.html")
            .type("text/html")
            .payloadSize(77L)
            .tag("AAAAAAAAAAAAAAAA")
            .timeout(60)
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldNotMatchBeginExtensionWhenTypeIdMissing() throws Exception
    {
        BytesMatcher matcher = FileSystemFunctions.matchBeginEx()
            .capabilities("READ_PAYLOAD")
            .path("index.html")
            .type("text/html")
            .tag("AAAAAAAAAAAAAAAA")
            .timeout(60)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new FileSystemBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .capabilities(1 << READ_PAYLOAD.ordinal())
            .path("index.html")
            .type("text/html")
            .payloadSize(77L)
            .tag("AAAAAAAAAAAAAAAA")
            .timeout(60)
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchBeginExtensionWhenTypeIdDiffers() throws Exception
    {
        BytesMatcher matcher = FileSystemFunctions.matchBeginEx()
            .typeId(0x01)
            .capabilities("READ_PAYLOAD")
            .path("index.html")
            .type("text/html")
            .tag("AAAAAAAAAAAAAAAA")
            .timeout(60)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new FileSystemBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x02)
            .capabilities(1 << READ_PAYLOAD.ordinal())
            .path("index.html")
            .type("text/html")
            .payloadSize(77L)
            .tag("AAAAAAAAAAAAAAAA")
            .timeout(60)
            .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchBeginExtensionWhenCapabilitiesDiffer() throws Exception
    {
        BytesMatcher matcher = FileSystemFunctions.matchBeginEx()
            .typeId(0x01)
            .capabilities("READ_PAYLOAD")
            .path("index.html")
            .type("text/html")
            .payloadSize(77L)
            .tag("AAAAAAAAAAAAAAAA")
            .timeout(60)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new FileSystemBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .capabilities(1 << READ_EXTENSION.ordinal())
            .path("index.html")
            .type("text/html")
            .payloadSize(77L)
            .tag("AAAAAAAAAAAAAAAA")
            .timeout(60)
            .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchBeginExtensionWhenPathDiffers() throws Exception
    {
        BytesMatcher matcher = FileSystemFunctions.matchBeginEx()
            .typeId(0x01)
            .capabilities("READ_PAYLOAD")
            .path("index.json")
            .type("text/html")
            .payloadSize(77L)
            .tag("AAAAAAAAAAAAAAAA")
            .timeout(60)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new FileSystemBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .capabilities(1 << READ_PAYLOAD.ordinal())
            .path("index.html")
            .type("text/html")
            .payloadSize(77L)
            .tag("AAAAAAAAAAAAAAAA")
            .timeout(60)
            .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchBeginExtensionWhenTypeDiffers() throws Exception
    {
        BytesMatcher matcher = FileSystemFunctions.matchBeginEx()
            .typeId(0x01)
            .capabilities("READ_PAYLOAD")
            .path("index.html")
            .type("application/json")
            .payloadSize(77L)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new FileSystemBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .capabilities(1 << READ_PAYLOAD.ordinal())
            .path("index.html")
            .type("text/html")
            .payloadSize(77L)
            .tag("AAAAAAAAAAAAAAAA")
            .timeout(60)
            .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchBeginExtensionWhenTagDiffers() throws Exception
    {
        BytesMatcher matcher = FileSystemFunctions.matchBeginEx()
            .typeId(0x01)
            .capabilities("READ_PAYLOAD")
            .path("index.html")
            .type("text/html")
            .payloadSize(77L)
            .tag("AAAAA")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new FileSystemBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .capabilities(1 << READ_PAYLOAD.ordinal())
            .path("index.html")
            .type("text/html")
            .payloadSize(77L)
            .tag("BBBBBB")
            .timeout(60)
            .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchBeginExtensionWhenTimeoutDiffers() throws Exception
    {
        BytesMatcher matcher = FileSystemFunctions.matchBeginEx()
            .typeId(0x01)
            .capabilities("READ_PAYLOAD")
            .path("index.html")
            .type("text/html")
            .payloadSize(77L)
            .timeout(50)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new FileSystemBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .capabilities(1 << READ_PAYLOAD.ordinal())
            .path("index.html")
            .type("text/html")
            .payloadSize(77L)
            .tag("AAAAAAAAAAAAAAAA")
            .timeout(60)
            .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchBeginExtensionWhenPayloadSizeDiffers() throws Exception
    {
        BytesMatcher matcher = FileSystemFunctions.matchBeginEx()
            .typeId(0x01)
            .capabilities("READ_PAYLOAD")
            .path("index.html")
            .type("text/html")
            .payloadSize(77L)
            .tag("AAAAAAAAAAAAAAAA")
            .timeout(60)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new FileSystemBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .capabilities(1 << READ_PAYLOAD.ordinal())
            .path("index.html")
            .type("text/html")
            .payloadSize(76L)
            .tag("AAAAAAAAAAAAAAAA")
            .timeout(60)
            .build();

        matcher.match(byteBuf);
    }

    @Test
    public void shouldNotMatchBeginExtensionWhenBufferOverflow() throws Exception
    {
        BytesMatcher matcher = FileSystemFunctions.matchBeginEx()
            .typeId(0x01)
            .capabilities("READ_PAYLOAD")
            .path("index.html")
            .type("text/html")
            .payloadSize(77L)
            .tag("AAAAAAAAAAAAAAAA")
            .timeout(60)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(0);

        assertNull(matcher.match(byteBuf));
    }
}
