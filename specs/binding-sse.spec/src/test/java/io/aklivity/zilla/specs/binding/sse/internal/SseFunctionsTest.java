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
package io.aklivity.zilla.specs.binding.sse.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.kaazing.k3po.lang.internal.el.ExpressionFactoryUtils.newExpressionFactory;

import java.nio.ByteBuffer;

import javax.el.ELContext;
import javax.el.ExpressionFactory;
import javax.el.ValueExpression;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;
import org.kaazing.k3po.lang.el.BytesMatcher;
import org.kaazing.k3po.lang.internal.el.ExpressionContext;

import io.aklivity.zilla.specs.binding.sse.internal.types.stream.SseBeginExFW;
import io.aklivity.zilla.specs.binding.sse.internal.types.stream.SseDataExFW;
import io.aklivity.zilla.specs.binding.sse.internal.types.stream.SseEndExFW;

public class SseFunctionsTest
{
    private ExpressionFactory factory;
    private ELContext ctx;

    @Before
    public void setUp() throws Exception
    {

        factory = newExpressionFactory();
        ctx = new ExpressionContext();
    }

    @Test
    public void shouldInvokeBeginEx() throws Exception
    {
        String expressionText = "${sse:beginEx()}";
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, Object.class);
        Object builder = expression.getValue(ctx);
        assertNotNull(builder);
    }

    @Test
    public void shouldGenerateBeginExtension()
    {
        byte[] build = SseFunctions.beginEx()
                                   .typeId(0x01)
                                   .scheme("http")
                                   .authority("localhost:8080")
                                   .path("/events")
                                   .lastId("id-42")
                                   .build();
        DirectBuffer buffer = new UnsafeBuffer(build);
        SseBeginExFW beginEx = new SseBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, beginEx.typeId());
        assertEquals("/events", beginEx.path().asString());
        assertEquals("id-42", beginEx.lastId().asString());
    }

    @Test
    public void shouldGenerateDataExtension()
    {
        byte[] build = SseFunctions.dataEx()
                                   .typeId(0x01)
                                   .timestamp(12345678L)
                                   .id("id-42")
                                   .type("custom")
                                   .build();
        DirectBuffer buffer = new UnsafeBuffer(build);
        SseDataExFW dataEx = new SseDataExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, dataEx.typeId());
        assertEquals(12345678L, dataEx.timestamp());
        assertEquals("id-42", dataEx.id().asString());
        assertEquals("custom", dataEx.type().asString());
    }

    @Test
    public void shouldGenerateDataExtensionWithInvalidUtf8()
    {
        byte[] build = SseFunctions.dataEx()
                                   .typeId(0x01)
                                   .timestamp(12345678L)
                                   .idAsRawBytes(new byte[] {(byte) 0xc3, 0x28})
                                   .typeAsRawBytes(new byte[] {(byte) 0xc3, 0x28})
                                   .build();
        DirectBuffer buffer = new UnsafeBuffer(build);
        SseDataExFW dataEx = new SseDataExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, dataEx.typeId());
        assertEquals(12345678L, dataEx.timestamp());
        assertEquals(3, dataEx.id().sizeof());
        assertEquals(3, dataEx.type().sizeof());
    }

    @Test
    public void shouldGenerateEndExtension()
    {
        byte[] build = SseFunctions.endEx()
                                   .typeId(0x01)
                                   .id("id-42")
                                   .build();
        DirectBuffer buffer = new UnsafeBuffer(build);
        SseEndExFW endEx = new SseEndExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, endEx.typeId());
        assertEquals("id-42", endEx.id().asString());
    }

    @Test
    public void shouldMatchDataExtension() throws Exception
    {
        byte[] dataEx = SseFunctions.dataEx()
                .typeId(0x01)
                .timestamp(12345678L)
                .id("id-42")
                .type("custom")
                .build();

        BytesMatcher matcher = SseFunctions.matchDataEx()
                .typeId(0x01)
                .timestamp(12345678L)
                .id("id-42")
                .type("custom")
                .build();

        assertNotNull(matcher.match(ByteBuffer.wrap(dataEx)));
    }

    @Test
    public void shouldMatchDataExtensionWithInvalidUtf8() throws Exception
    {
        byte[] dataEx = SseFunctions.dataEx()
                .typeId(0x01)
                .timestamp(12345678L)
                .idAsRawBytes(new byte[] {(byte) 0xc3, 0x28})
                .typeAsRawBytes(new byte[] {(byte) 0xc3, 0x28})
                .build();

        BytesMatcher matcher = SseFunctions.matchDataEx()
                .typeId(0x01)
                .timestamp(12345678L)
                .idAsRawBytes(new byte[] {(byte) 0xc3, 0x28})
                .typeAsRawBytes(new byte[] {(byte) 0xc3, 0x28})
                .build();

        assertNotNull(matcher.match(ByteBuffer.wrap(dataEx)));
    }

    @Test
    public void shouldNotMatchDataExtensionWhenEmpty() throws Exception
    {
        byte[] dataEx = new byte[0];

        BytesMatcher matcher = SseFunctions.matchDataEx()
                .typeId(0x01)
                .timestamp(12345678L)
                .id("id-42")
                .type("custom")
                .build();

        assertNull(matcher.match(ByteBuffer.wrap(dataEx)));
    }

    @Test
    public void shouldNotMatchDataExtensionWhenIncomplete() throws Exception
    {
        byte[] dataEx = new byte[1];

        BytesMatcher matcher = SseFunctions.matchDataEx()
                .typeId(0x01)
                .timestamp(12345678L)
                .id("id-42")
                .type("custom")
                .build();

        assertThrows(Exception.class, () -> matcher.match(ByteBuffer.wrap(dataEx)));
    }

    @Test
    public void shouldNotMatchDataExtensionWithDifferentTypeId()
    {
        byte[] dataEx = SseFunctions.dataEx()
                .typeId(0x01)
                .timestamp(12345678L)
                .id("id-42")
                .type("custom")
                .build();

        BytesMatcher matcher = SseFunctions.matchDataEx()
                .typeId(0x02)
                .build();

        assertThrows(Exception.class, () -> matcher.match(ByteBuffer.wrap(dataEx)));
    }

    @Test
    public void shouldNotMatchDataExtensionWithDifferentTimestamp()
    {
        byte[] dataEx = SseFunctions.dataEx()
                .typeId(0x01)
                .timestamp(12345678L)
                .id("id-42")
                .type("custom")
                .build();

        BytesMatcher matcher = SseFunctions.matchDataEx()
                .timestamp(12345679L)
                .build();

        assertThrows(Exception.class, () -> matcher.match(ByteBuffer.wrap(dataEx)));
    }

    @Test
    public void shouldNotMatchDataExtensionWithDifferentId()
    {
        byte[] dataEx = SseFunctions.dataEx()
                .typeId(0x01)
                .timestamp(12345678L)
                .id("id-42")
                .type("custom")
                .build();

        BytesMatcher matcher = SseFunctions.matchDataEx()
                .id("id-43")
                .build();

        assertThrows(Exception.class, () -> matcher.match(ByteBuffer.wrap(dataEx)));
    }

    @Test
    public void shouldNotMatchDataExtensionWithDifferentType()
    {
        byte[] dataEx = SseFunctions.dataEx()
                .typeId(0x01)
                .timestamp(12345678L)
                .id("id-42")
                .type("custom")
                .build();

        BytesMatcher matcher = SseFunctions.matchDataEx()
                .type("custom-x")
                .build();

        assertThrows(Exception.class, () -> matcher.match(ByteBuffer.wrap(dataEx)));
    }
}
