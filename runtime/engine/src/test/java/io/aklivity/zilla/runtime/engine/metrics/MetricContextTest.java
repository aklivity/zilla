/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.metrics;

import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.function.IntFunction;
import java.util.function.LongConsumer;
import java.util.function.ToLongFunction;

import org.junit.Test;

import io.aklivity.zilla.config.engine.AttributeConfig;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public class MetricContextTest
{
    @Test
    public void shouldDelegateResolvedAttributesToAttributesSupply()
    {
        MessageConsumer expected = mock(MessageConsumer.class);
        MetricContext context = new MetricContext()
        {
            @Override
            public String group()
            {
                return "test";
            }

            @Override
            public Metric.Kind kind()
            {
                return Metric.Kind.COUNTER;
            }

            @Override
            public Direction direction()
            {
                return Direction.BOTH;
            }

            @Override
            public MessageConsumer supply(
                LongConsumer recorder)
            {
                return MessageConsumer.NOOP;
            }

            @Override
            public MessageConsumer supply(
                IntFunction<LongConsumer> recorder,
                List<AttributeConfig> attributes)
            {
                return expected;
            }
        };

        MessageConsumer handler = context.supply(
            attributesId -> mock(LongConsumer.class),
            List.of(AttributeConfig.builder()
                .name("user")
                .value("${guarded['test0'].identity}")
                .build()),
            mock(ToLongFunction.class));

        assertThat(handler, sameInstance(expected));
    }
}
