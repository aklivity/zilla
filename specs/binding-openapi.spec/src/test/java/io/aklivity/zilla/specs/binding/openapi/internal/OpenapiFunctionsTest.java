/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.specs.binding.openapi.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import javax.el.ELContext;
import javax.el.FunctionMapper;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;
import org.kaazing.k3po.lang.el.BytesMatcher;
import org.kaazing.k3po.lang.internal.el.ExpressionContext;

import io.aklivity.zilla.specs.binding.openapi.OpenapiFunctions;
import io.aklivity.zilla.specs.binding.openapi.internal.types.OctetsFW;
import io.aklivity.zilla.specs.binding.openapi.internal.types.stream.OpenapiBeginExFW;

public class OpenapiFunctionsTest
{
    @Test
    public void shouldResolveFunction() throws Exception
    {
        final ELContext ctx = new ExpressionContext();
        final FunctionMapper mapper = ctx.getFunctionMapper();
        final Method function = mapper.resolveFunction("openapi", "beginEx");

        assertNotNull(function);
        assertSame(OpenapiFunctions.class, function.getDeclaringClass());
    }

    @Test
    public void shouldGenerateBeginExtension()
    {
        byte[] build = OpenapiFunctions.beginEx()
            .typeId(0x01)
            .apiId(1L)
            .operationId("test")
            .extension("extension".getBytes())
            .build();

        DirectBuffer buffer = new UnsafeBuffer(build);

        OpenapiBeginExFW beginEx = new OpenapiBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(1L, beginEx.apiId());
        assertEquals("test", beginEx.operationId().asString());
        assertEquals("extension".length(), beginEx.extension().sizeof());
    }

    @Test
    public void shouldMatchOpenapiBeginExtension() throws Exception
    {
        BytesMatcher matcher = OpenapiFunctions.matchBeginEx()
            .typeId(0x00)
            .apiId(1)
            .operationId("operationId")
            .extension(new byte[] {1})
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(26);
        MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1]);

        new OpenapiBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x00)
            .apiId(1)
            .operationId("operationId")
            .extension(new OctetsFW.Builder().wrap(writeBuffer, 0, 1).set(new byte[] {1}).build())
            .build();

        assertNotNull(matcher.match(byteBuf));
    }
}
