/*
 * Copyright 2021-2024 Aklivity Inc
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

import static io.aklivity.zilla.specs.binding.filesystem.internal.types.FileSystemCapabilities.READ_FILE;
import static io.aklivity.zilla.specs.binding.filesystem.internal.types.FileSystemCapabilities.READ_METADATA;
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

import io.aklivity.k3po.runtime.lang.el.BytesMatcher;
import io.aklivity.k3po.runtime.lang.internal.el.ExpressionContext;
import io.aklivity.zilla.specs.binding.filesystem.internal.types.stream.FileSystemBeginExFW;
import io.aklivity.zilla.specs.binding.filesystem.internal.types.stream.FileSystemError;
import io.aklivity.zilla.specs.binding.filesystem.internal.types.stream.FileSystemErrorFW;
import io.aklivity.zilla.specs.binding.filesystem.internal.types.stream.FileSystemResetExFW;

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
            .capabilities("READ_FILE")
            .directory("var/www")
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
        assertEquals("var/www", beginEx.directory().asString());
        assertEquals(1 << READ_FILE.ordinal(), beginEx.capabilities());
        assertEquals(77L, beginEx.payloadSize());
    }

    @Test
    public void shouldMatchBeginExtension() throws Exception
    {
        BytesMatcher matcher = FileSystemFunctions.matchBeginEx()
            .typeId(0x01)
            .capabilities("READ_FILE")
            .directory("var/www")
            .path("index.html")
            .type("text/html")
            .payloadSize(77L)
            .tag("AAAAAAAAAAAAAAAA")
            .timeout(60)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new FileSystemBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .capabilities(1 << READ_FILE.ordinal())
            .directory("var/www")
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
            .capabilities(1 << READ_FILE.ordinal())
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
            .capabilities("READ_FILE")
            .path("index.html")
            .type("text/html")
            .tag("AAAAAAAAAAAAAAAA")
            .timeout(60)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new FileSystemBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .capabilities(1 << READ_FILE.ordinal())
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
            .capabilities("READ_FILE")
            .path("index.html")
            .type("text/html")
            .tag("AAAAAAAAAAAAAAAA")
            .timeout(60)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new FileSystemBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x02)
            .capabilities(1 << READ_FILE.ordinal())
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
            .capabilities("READ_FILE")
            .path("index.html")
            .type("text/html")
            .payloadSize(77L)
            .tag("AAAAAAAAAAAAAAAA")
            .timeout(60)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new FileSystemBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .capabilities(1 << READ_METADATA.ordinal())
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
            .capabilities("READ_FILE")
            .path("index.json")
            .type("text/html")
            .payloadSize(77L)
            .tag("AAAAAAAAAAAAAAAA")
            .timeout(60)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new FileSystemBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .capabilities(1 << READ_FILE.ordinal())
            .path("index.html")
            .type("text/html")
            .payloadSize(77L)
            .tag("AAAAAAAAAAAAAAAA")
            .timeout(60)
            .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchBeginExtensionWhenDirectoryDiffers() throws Exception
    {
        BytesMatcher matcher = FileSystemFunctions.matchBeginEx()
            .typeId(0x01)
            .capabilities("READ_FILE")
            .directory("var/www")
            .path("index.json")
            .type("text/html")
            .payloadSize(77L)
            .tag("AAAAAAAAAAAAAAAA")
            .timeout(60)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new FileSystemBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .capabilities(1 << READ_FILE.ordinal())
            .directory("var/www/tmp")
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
            .capabilities("READ_FILE")
            .path("index.html")
            .type("application/json")
            .payloadSize(77L)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new FileSystemBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .capabilities(1 << READ_FILE.ordinal())
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
            .capabilities("READ_FILE")
            .path("index.html")
            .type("text/html")
            .payloadSize(77L)
            .tag("AAAAA")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new FileSystemBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .capabilities(1 << READ_FILE.ordinal())
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
            .capabilities("READ_FILE")
            .path("index.html")
            .type("text/html")
            .payloadSize(77L)
            .timeout(50)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new FileSystemBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .capabilities(1 << READ_FILE.ordinal())
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
            .capabilities("READ_FILE")
            .path("index.html")
            .type("text/html")
            .payloadSize(77L)
            .tag("AAAAAAAAAAAAAAAA")
            .timeout(60)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new FileSystemBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .capabilities(1 << READ_FILE.ordinal())
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
            .capabilities("READ_FILE")
            .path("index.html")
            .type("text/html")
            .payloadSize(77L)
            .tag("AAAAAAAAAAAAAAAA")
            .timeout(60)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(0);

        assertNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateResetExtension()
    {
        byte[] build = FileSystemFunctions.resetEx()
            .typeId(0x01)
            .error("FILE_MODIFIED")
            .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        FileSystemResetExFW resetEx = new FileSystemResetExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0x01, resetEx.typeId());
        assertEquals(FileSystemError.FILE_MODIFIED, resetEx.error().get());
    }

    @Test
    public void shouldMatchResetExtension() throws Exception
    {
        BytesMatcher matcher = FileSystemFunctions.matchResetEx()
            .typeId(0x01)
            .error("FILE_MODIFIED")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new FileSystemResetExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .error(new FileSystemErrorFW.Builder().wrap(new UnsafeBuffer(new byte[1]), 0, 1)
                .set(FileSystemError.FILE_MODIFIED).build())
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchResetExtensionWhenErrorDiffers() throws Exception
    {
        BytesMatcher matcher = FileSystemFunctions.matchResetEx()
            .typeId(0x01)
            .error("FILE_MODIFIED")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new FileSystemResetExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .error(new FileSystemErrorFW.Builder().wrap(new UnsafeBuffer(new byte[1]), 0, 1)
                .set(FileSystemError.FILE_NOT_FOUND).build())
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchResetExtensionWhenTypeIdDiffers() throws Exception
    {
        BytesMatcher matcher = FileSystemFunctions.matchResetEx()
            .typeId(0x01)
            .error("FILE_MODIFIED")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new FileSystemResetExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x02)
            .error(new FileSystemErrorFW.Builder().wrap(new UnsafeBuffer(new byte[1]), 0, 1)
                .set(FileSystemError.FILE_NOT_FOUND).build())
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldNotMatchResetExtensionWhenBufferOverflow() throws Exception
    {
        BytesMatcher matcher = FileSystemFunctions.matchResetEx()
            .typeId(0x01)
            .error("FILE_MODIFIED")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(0);

        assertNull(matcher.match(byteBuf));
    }
}
