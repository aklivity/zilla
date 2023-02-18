/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.specs.binding.grpc.internal;

import static io.aklivity.zilla.specs.binding.grpc.internal.types.stream.GrpcKind.UNARY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;
import org.kaazing.k3po.lang.el.BytesMatcher;

import io.aklivity.zilla.specs.binding.grpc.internal.types.stream.GrpcBeginExFW;

public class GrpcFunctionsTest
{
    @Test
    public void shouldGenerateBeginExtension()
    {
        final String method = "EchoService/EchoUnary";
        byte[] build = GrpcFunctions.beginEx()
            .typeId(0x01)
            .method(method)
            .request(0x00)
            .response(0x00)
            .metadata("name", "value")
            .build();
        DirectBuffer buffer = new UnsafeBuffer(build);
        GrpcBeginExFW beginEx = new GrpcBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(method, beginEx.method().asString());
        assertEquals(UNARY, beginEx.request().get());
        assertEquals(UNARY, beginEx.response().get());
        beginEx.metadata().forEach(onlyHeader ->
        {
            assertEquals("name", onlyHeader.name().asString());
            assertEquals("value", onlyHeader.value().asString());
        });
        assertTrue(beginEx.metadata().sizeof() > 0);
    }

    @Test
    public void shouldMatchBeginExtension() throws Exception
    {
        final String method = "EchoService/EchoUnary";
        BytesMatcher matcher = GrpcFunctions.matchBeginEx()
            .typeId(0x01)
            .method(method)
            .request(0x00)
            .response(0x00)
            .metadata("name", "value")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new GrpcBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .method(method)
            .request(b -> b.set(UNARY).build())
            .response(b -> b.set(UNARY).build())
            .metadataItem(h -> h.name("name").value("value"))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }
}
