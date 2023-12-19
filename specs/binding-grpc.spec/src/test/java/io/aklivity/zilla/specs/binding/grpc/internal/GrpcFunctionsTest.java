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
package io.aklivity.zilla.specs.binding.grpc.internal;

import static io.aklivity.zilla.specs.binding.grpc.internal.types.stream.GrpcType.TEXT;
import static io.aklivity.zilla.specs.binding.http.internal.HttpFunctions.randomBytes;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import javax.el.ELContext;
import javax.el.FunctionMapper;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;
import org.kaazing.k3po.lang.el.BytesMatcher;
import org.kaazing.k3po.lang.internal.el.ExpressionContext;

import io.aklivity.zilla.specs.binding.grpc.internal.types.OctetsFW;
import io.aklivity.zilla.specs.binding.grpc.internal.types.stream.GrpcAbortExFW;
import io.aklivity.zilla.specs.binding.grpc.internal.types.stream.GrpcBeginExFW;
import io.aklivity.zilla.specs.binding.grpc.internal.types.stream.GrpcDataExFW;
import io.aklivity.zilla.specs.binding.grpc.internal.types.stream.GrpcResetExFW;

public class GrpcFunctionsTest
{
    private final OctetsFW.Builder nameBuilder =
        new OctetsFW.Builder().wrap(new UnsafeBuffer(new byte[1024 * 8]), 0, 1024 * 8);
    private final OctetsFW.Builder valueBuilder =
        new OctetsFW.Builder().wrap(new UnsafeBuffer(new byte[1024 * 8]), 0, 1024 * 8);

    @Test
    public void shouldResolveFunction() throws Exception
    {
        final ELContext ctx = new ExpressionContext();
        final FunctionMapper mapper = ctx.getFunctionMapper();
        final Method function = mapper.resolveFunction("grpc", "matchBeginEx");

        assertNotNull(function);
        assertSame(GrpcFunctions.class, function.getDeclaringClass());
    }

    @Test
    public void shouldRandomizeBytes() throws Exception
    {
        final byte[] bytes = randomBytes(42);

        assertNotNull(bytes);
        assertEquals(42, bytes.length);
    }

    @Test
    public void shouldGenerateBeginExtension()
    {
        byte[] build = GrpcFunctions.beginEx()
            .typeId(0x01)
            .scheme("http")
            .authority("localhost:8080")
            .service("example.EchoService")
            .method("EchoUnary")
            .metadata("custom", "test")
            .build();
        DirectBuffer buffer = new UnsafeBuffer(build);
        GrpcBeginExFW beginEx = new GrpcBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals("http", beginEx.scheme().asString());
        assertEquals("localhost:8080", beginEx.authority().asString());
        assertEquals("example.EchoService", beginEx.service().asString());
        assertEquals("EchoUnary", beginEx.method().asString());
        beginEx.metadata().forEach(h ->
        {
            assertTrue(nameBuilder.set("custom".getBytes()).build().equals(h.name()));
            assertTrue(valueBuilder.set("test".getBytes()).build().equals(h.value()));
        });
        assertTrue(beginEx.metadata().sizeof() > 0);
    }

    @Test
    public void shouldMatchBeginExtension() throws Exception
    {
        String value = "value";
        String custom = "custom";
        BytesMatcher matcher = GrpcFunctions.matchBeginEx()
            .typeId(0x01)
            .scheme("http")
            .authority("localhost:8080")
            .service("example.EchoService")
            .method("EchoUnary")
            .metadata(custom, value)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new GrpcBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .scheme("http")
            .authority("localhost:8080")
            .service("example.EchoService")
            .method("EchoUnary")
            .metadataItem(h -> h.type(t -> t.set(TEXT).build()).nameLen(custom.length())
                .name(nameBuilder.set(custom.getBytes()).build())
                .valueLen(value.length()).value(valueBuilder.set(value.getBytes()).build()))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateResetExtension()
    {
        byte[] build = GrpcFunctions.resetEx()
            .typeId(0x01)
            .status("10")
            .build();
        DirectBuffer buffer = new UnsafeBuffer(build);
        GrpcResetExFW resetEx = new GrpcResetExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, resetEx.typeId());

        assertEquals("10", resetEx.status().asString());
    }

    @Test
    public void shouldGenerateAbortExtension()
    {
        byte[] build = GrpcFunctions.abortEx()
            .typeId(0x01)
            .status("10")
            .build();
        DirectBuffer buffer = new UnsafeBuffer(build);
        GrpcAbortExFW abortEx = new GrpcAbortExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, abortEx.typeId());

        assertEquals("10", abortEx.status().asString());
    }

    @Test
    public void shouldGenerateDataExtension()
    {
        byte[] build = GrpcFunctions.dataEx()
            .typeId(0x01)
            .deferred(10)
            .build();
        DirectBuffer buffer = new UnsafeBuffer(build);
        GrpcDataExFW dataEx = new GrpcDataExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, dataEx.typeId());

        assertEquals(10, dataEx.deferred());
    }

    @Test
    public void shouldGenerateGrpcMessage()
    {
        byte[] message = GrpcFunctions.message().string(1, "value").build();
        byte[] expected = {0, 0, 0, 0, 7, 10, 5, 118, 97, 108, 117, 101};
        assertArrayEquals(expected, message);
    }

    @Test
    public void shouldGenerateProtobuf()
    {
        byte[] message = GrpcFunctions.protobuf()
            .string(1, "value")
            .build();
        byte[] expected = {10, 5, 118, 97, 108, 117, 101};
        assertArrayEquals(expected, message);
    }

    @Test
    public void  shouldGenerateRandomString()
    {
        String randomString = GrpcFunctions.randomString(10);
        assertEquals(10, randomString.length());
    }
}
