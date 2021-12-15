/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.specs.cog.ws.internal;

import static io.aklivity.zilla.specs.cog.ws.internal.WsFunctions.beginEx;
import static io.aklivity.zilla.specs.cog.ws.internal.WsFunctions.handshakeHash;
import static io.aklivity.zilla.specs.cog.ws.internal.WsFunctions.handshakeKey;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.kaazing.k3po.lang.internal.el.ExpressionFactoryUtils.newExpressionFactory;

import javax.el.ELContext;
import javax.el.ExpressionFactory;
import javax.el.ValueExpression;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;
import org.kaazing.k3po.lang.internal.el.ExpressionContext;

import io.aklivity.zilla.specs.cog.ws.internal.WsFunctions.WsBeginExHelper;
import io.aklivity.zilla.specs.cog.ws.internal.types.stream.WsBeginExFW;

public class WsFunctionsTest
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
    public void shouldLoadFunctions() throws Exception
    {
        String expressionText = "${ws:beginEx()}";
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, WsBeginExHelper.class);
        WsBeginExHelper builder = (WsBeginExHelper) expression.getValue(ctx);
        assertNotNull(builder);
    }

    @Test
    public void shouldGenerateHandshakeKey() throws Exception
    {
        String handshakeKey = handshakeKey();
        assertNotNull(handshakeKey);
        assertEquals(24, handshakeKey.length());
    }

    @Test
    public void shouldComputeHandshakeHash() throws Exception
    {
        String handshakeHash = handshakeHash("dGhlIHNhbXBsZSBub25jZQ==");
        assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=", handshakeHash);
    }

    @Test
    public void shouldEncodeWsBeginExt()
    {
        final byte[] array = beginEx().typeId(0x01)
                                      .protocol("primary")
                                      .scheme("http")
                                      .authority("localhost:8080")
                                      .path("/path?query")
                                      .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        WsBeginExFW wsBeginEx = new WsBeginExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(wsBeginEx.typeId(), 0x01);
        assertEquals(wsBeginEx.protocol().asString(), "primary");
        assertEquals(wsBeginEx.scheme().asString(), "http");
        assertEquals(wsBeginEx.authority().asString(), "localhost:8080");
        assertEquals(wsBeginEx.path().asString(), "/path?query");
    }
}
