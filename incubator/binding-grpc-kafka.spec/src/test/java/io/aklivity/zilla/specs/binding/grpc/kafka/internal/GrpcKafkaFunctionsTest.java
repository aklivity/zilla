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
package io.aklivity.zilla.specs.binding.grpc.kafka.internal;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import java.lang.reflect.Method;

import javax.el.ELContext;
import javax.el.FunctionMapper;

import org.junit.Test;
import org.kaazing.k3po.lang.internal.el.ExpressionContext;

public class GrpcKafkaFunctionsTest
{
    @Test
    public void shouldResolveFunction() throws Exception
    {
        final ELContext ctx = new ExpressionContext();
        final FunctionMapper mapper = ctx.getFunctionMapper();
        final Method function = mapper.resolveFunction("grpc_kafka", "messageId");

        assertNotNull(function);
        assertSame(GrpcKafkaFunctions.class, function.getDeclaringClass());
    }

    @Test
    public void shouldGenerateProtobuf()
    {
        byte[] message = GrpcKafkaFunctions.messageId()
            .partition(0, 2)
            .build();
        byte[] expected = {32, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
        assertArrayEquals(expected, message);
    }
}
