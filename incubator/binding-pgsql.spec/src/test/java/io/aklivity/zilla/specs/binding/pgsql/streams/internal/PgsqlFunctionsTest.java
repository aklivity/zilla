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
package io.aklivity.zilla.specs.binding.pgsql.streams.internal;

import static io.aklivity.zilla.specs.binding.pgsql.PgsqlFunctions.beginEx;
import static io.aklivity.zilla.specs.binding.pgsql.PgsqlFunctions.dataEx;
import static io.aklivity.zilla.specs.binding.pgsql.PgsqlFunctions.flushEx;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import java.lang.reflect.Method;

import javax.el.ELContext;
import javax.el.FunctionMapper;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.k3po.runtime.lang.internal.el.ExpressionContext;
import io.aklivity.zilla.specs.binding.pgsql.PgsqlFunctions;
import io.aklivity.zilla.specs.binding.pgsql.internal.types.stream.PgsqlBeginExFW;
import io.aklivity.zilla.specs.binding.pgsql.internal.types.stream.PgsqlDataExFW;
import io.aklivity.zilla.specs.binding.pgsql.internal.types.stream.PgsqlFlushExFW;
import io.aklivity.zilla.specs.binding.pgsql.internal.types.stream.PgsqlStatus;


public class PgsqlFunctionsTest
{
    @Test
    public void shouldResolveFunction() throws Exception
    {
        final ELContext ctx = new ExpressionContext();
        final FunctionMapper mapper = ctx.getFunctionMapper();
        final Method function = mapper.resolveFunction("pgsql", "beginEx");

        assertNotNull(function);
        assertSame(PgsqlFunctions.class, function.getDeclaringClass());
    }

    @Test
    public void shouldGenerateBeginExtension()
    {
        byte[] build = beginEx()
            .typeId(0x01)
            .parameter("name", "pgsql")
            .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        PgsqlBeginExFW beginEx = new PgsqlBeginExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(1, beginEx.parameters().fieldCount());
        beginEx.parameters().forEach(h ->
        {
            assertEquals("name\u0000", h.name().asString());
            assertEquals("pgsql\u0000", h.value().asString());
        });
    }

    @Test
    public void shouldEncodePgsqlDataQueryExtension()
    {
        final byte[] build = dataEx()
            .typeId(0x01)
            .query()
                .deferred(1)
                .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        PgsqlDataExFW dataEx = new PgsqlDataExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, dataEx.typeId());

        assertEquals(1, dataEx.query().deferred());
    }

    @Test
    public void shouldEncodePgsqlDataRowExtension()
    {
        final byte[] build = dataEx()
            .typeId(0x01)
            .row()
                .deferred(1)
                .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        PgsqlDataExFW dataEx = new PgsqlDataExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, dataEx.typeId());

        assertEquals(1, dataEx.row().deferred());
    }

    @Test
    public void shouldEncodePgsqlFlushTypeExtension()
    {
        final byte[] build = flushEx()
                              .typeId(0x01)
                              .type()
                                .column()
                                    .name("balance")
                                    .tableOid(0)
                                    .index((short) 0)
                                    .typeOid(701)
                                    .length((short)8)
                                    .modifier(-1)
                                    .format("TEXT")
                                    .build()
                                .build()
                              .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        PgsqlFlushExFW flushEx = new PgsqlFlushExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, flushEx.typeId());

        assertEquals(1, flushEx.type().columns().fieldCount());
        flushEx.type().columns().forEach(c ->
        {
            assertEquals("balance\u0000", c.name().asString());
            assertEquals(0, c.tableOid());
            assertEquals(0, c.index());
            assertEquals(701, c.typeOid());
            assertEquals(-1, c.modifier());
        });
    }

    @Test
    public void shouldEncodePgsqlFlushCompletionExtension()
    {
        final byte[] build = flushEx()
                              .typeId(0x01)
                              .completion()
                                .tag("CREATE_TABLE")
                                .build()
                              .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        PgsqlFlushExFW flushEx = new PgsqlFlushExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0x01, flushEx.typeId());
        assertEquals("CREATE_TABLE\u0000", flushEx.completion().tag().asString());
    }

    @Test
    public void shouldEncodePgsqlFlushReadyExtension()
    {
        final byte[] build = flushEx()
                              .typeId(0x01)
                              .ready()
                                .status("IDLE")
                                .build()
                              .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        PgsqlFlushExFW flushEx = new PgsqlFlushExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0x01, flushEx.typeId());
        assertEquals(PgsqlStatus.IDLE, flushEx.ready().status().get());
    }

}
