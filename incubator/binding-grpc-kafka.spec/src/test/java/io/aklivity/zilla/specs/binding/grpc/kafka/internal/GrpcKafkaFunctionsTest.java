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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import java.lang.reflect.Method;

import javax.el.ELContext;
import javax.el.FunctionMapper;

import org.agrona.DirectBuffer;
import org.agrona.collections.MutableInteger;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;
import org.kaazing.k3po.lang.internal.el.ExpressionContext;

import io.aklivity.zilla.specs.binding.grpc.kafka.internal.types.Array32FW;
import io.aklivity.zilla.specs.binding.grpc.kafka.internal.types.KafkaOffsetFW;

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
        byte[] build = GrpcKafkaFunctions.messageId()
            .partition(0, 2)
            .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        final Array32FW<KafkaOffsetFW> partitions =
            new Array32FW<>(new KafkaOffsetFW());
        partitions.wrap(buffer, 0, buffer.capacity());

        final MutableInteger partitionCount = new MutableInteger();
        partitions.forEach(f -> partitionCount.value++);
        assertEquals(1, partitionCount.value);

        assertNotNull(partitions
            .matchFirst(p -> p.partitionId() == 0 && p.partitionOffset() == 2L));
    }
}
