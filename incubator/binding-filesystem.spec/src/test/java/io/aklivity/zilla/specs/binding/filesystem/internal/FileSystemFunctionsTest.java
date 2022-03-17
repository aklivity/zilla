/*
 * Copyright 2021-2022 Aklivity Inc
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.time.Instant;

import javax.el.ELContext;
import javax.el.FunctionMapper;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;
import org.kaazing.k3po.lang.el.BytesMatcher;
import org.kaazing.k3po.lang.internal.el.ExpressionContext;

import io.aklivity.zilla.specs.binding.filesystem.internal.types.stream.FileSystemBeginExFW;
import io.aklivity.zilla.specs.binding.filesystem.internal.types.stream.FileSystemDataExFW;

public class FileSystemFunctionsTest
{
    private final Instant now = Instant.now();

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
            .path("index.html")
            .modifiedSince(now.toEpochMilli())
            .maximumSize(65535L)
            .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        FileSystemBeginExFW beginEx = new FileSystemBeginExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0x01, beginEx.typeId());
        assertEquals("index.html", beginEx.path().asString());
        assertEquals(now.toEpochMilli(), beginEx.modifiedSince());
        assertEquals(65535L, beginEx.maximumSize());
    }

    @Test
    public void shouldMatchBeginExtension() throws Exception
    {
        BytesMatcher matcher = FileSystemFunctions.matchBeginEx()
            .typeId(0x01)
            .path("index.html")
            .modifiedSince(now.toEpochMilli())
            .maximumSize(65535L)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new FileSystemBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .path("index.html")
            .modifiedSince(now.toEpochMilli())
            .maximumSize(65535L)
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldNotMatchBeginExtensionWhenTypeIdMissing() throws Exception
    {
        BytesMatcher matcher = FileSystemFunctions.matchBeginEx()
            .path("index.html")
            .modifiedSince(now.toEpochMilli())
            .maximumSize(65535L)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new FileSystemBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .path("index.html")
            .modifiedSince(now.toEpochMilli())
            .maximumSize(65535L)
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchBeginExtensionWhenTypeIdDiffers() throws Exception
    {
        BytesMatcher matcher = FileSystemFunctions.matchBeginEx()
            .typeId(0x01)
            .path("index.html")
            .modifiedSince(now.toEpochMilli())
            .maximumSize(65535L)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new FileSystemBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x02)
            .path("index.html")
            .modifiedSince(now.toEpochMilli())
            .maximumSize(65535L)
            .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchBeginExtensionWhenPathDiffers() throws Exception
    {
        BytesMatcher matcher = FileSystemFunctions.matchBeginEx()
            .typeId(0x01)
            .path("index.json")
            .modifiedSince(now.toEpochMilli())
            .maximumSize(65535L)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new FileSystemBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .path("index.html")
            .modifiedSince(now.toEpochMilli())
            .maximumSize(65535L)
            .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchBeginExtensionWhenModifiedSinceDiffers() throws Exception
    {
        BytesMatcher matcher = FileSystemFunctions.matchBeginEx()
            .typeId(0x01)
            .path("index.html")
            .modifiedSince(now.toEpochMilli() + 1)
            .maximumSize(65535L)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new FileSystemBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .path("index.html")
            .modifiedSince(now.toEpochMilli())
            .maximumSize(65535L)
            .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchBeginExtensionWhenMaximumSizeDiffers() throws Exception
    {
        BytesMatcher matcher = FileSystemFunctions.matchBeginEx()
            .typeId(0x01)
            .path("index.html")
            .modifiedSince(now.toEpochMilli())
            .maximumSize(65536L)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new FileSystemBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .path("index.html")
            .modifiedSince(now.toEpochMilli())
            .maximumSize(65535L)
            .build();

        matcher.match(byteBuf);
    }

    @Test
    public void shouldNotMatchBeginExtensionWhenBufferOverflow() throws Exception
    {
        BytesMatcher matcher = FileSystemFunctions.matchBeginEx()
            .typeId(0x01)
            .path("index.html")
            .modifiedSince(now.toEpochMilli())
            .maximumSize(65535L)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(0);

        assertNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateDataExtension()
    {
        byte[] build = FileSystemFunctions.dataEx()
            .typeId(0x01)
            .deferred(32767L)
            .modifiedTime(1643869342000L)
            .path("index.html")
            .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        FileSystemDataExFW dataEx = new FileSystemDataExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0x01, dataEx.typeId());
        assertEquals(32767L, dataEx.deferred());
        assertEquals(1643869342000L, dataEx.modifiedTime());
        assertEquals("index.html", dataEx.path().asString());
    }

    @Test
    public void shouldMatchDataExtension() throws Exception
    {
        BytesMatcher matcher = FileSystemFunctions.matchDataEx()
            .typeId(0x01)
            .deferred(32767L)
            .modifiedTime(1643869342000L)
            .path("index.html")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new FileSystemDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .deferred(32767L)
            .modifiedTime(1643869342000L)
            .path("index.html")
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldNotMatchDataExtensionWhenTypeIdMissing() throws Exception
    {
        BytesMatcher matcher = FileSystemFunctions.matchDataEx()
            .deferred(32767L)
            .modifiedTime(1643869342000L)
            .path("index.html")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new FileSystemDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .deferred(32767L)
            .modifiedTime(1643869342000L)
            .path("index.html")
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchDataExtensionWhenTypeIdDiffers() throws Exception
    {
        BytesMatcher matcher = FileSystemFunctions.matchDataEx()
            .typeId(0x01)
            .deferred(32767L)
            .modifiedTime(1643869342000L)
            .path("index.html")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new FileSystemDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x02)
            .deferred(32767L)
            .modifiedTime(1643869342000L)
            .path("index.html")
            .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchDataExtensionWhenDeferredDiffers() throws Exception
    {
        BytesMatcher matcher = FileSystemFunctions.matchDataEx()
            .typeId(0x01)
            .deferred(32768L)
            .modifiedTime(1643869342000L)
            .path("index.html")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new FileSystemDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .deferred(32767L)
            .modifiedTime(1643869342000L)
            .path("index.html")
            .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchDataExtensionWhenModifiedTimeDiffers() throws Exception
    {
        BytesMatcher matcher = FileSystemFunctions.matchDataEx()
            .typeId(0x01)
            .deferred(32767L)
            .modifiedTime(1643869342001L)
            .path("index.html")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new FileSystemDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .deferred(32767L)
            .modifiedTime(1643869342000L)
            .path("index.html")
            .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchDataExtensionWhenPathDiffers() throws Exception
    {
        BytesMatcher matcher = FileSystemFunctions.matchDataEx()
            .typeId(0x01)
            .deferred(32767L)
            .modifiedTime(1643869342000L)
            .path("index.json")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new FileSystemDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .deferred(32767L)
            .modifiedTime(1643869342000L)
            .path("index.html")
            .build();

        matcher.match(byteBuf);
    }

    @Test
    public void shouldNotMatchDataExtensionWhenBufferOverflow() throws Exception
    {
        BytesMatcher matcher = FileSystemFunctions.matchDataEx()
            .typeId(0x01)
            .deferred(32767L)
            .modifiedTime(1643869342000L)
            .path("index.html")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(0);

        assertNull(matcher.match(byteBuf));
    }
}
