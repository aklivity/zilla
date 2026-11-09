/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.a2a.internal.stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.a2a.internal.A2aConfiguration;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public class A2aStreamFactoryTest
{
    private final A2aConfiguration config = new A2aConfiguration(new Configuration());
    private final EngineContext context = mock(EngineContext.class);

    @Test
    public void shouldRejectServerStreams()
    {
        assertThat(newStream(new A2aServerFactory(config, context)), nullValue());
    }

    @Test
    public void shouldRejectClientStreams()
    {
        assertThat(newStream(new A2aClientFactory(config, context)), nullValue());
    }

    @Test
    public void shouldRejectProxyStreams()
    {
        assertThat(newStream(new A2aProxyFactory(config, context)), nullValue());
    }

    private static MessageConsumer newStream(
        A2aStreamFactory factory)
    {
        factory.attach(null);
        factory.detach(0L);

        return factory.newStream(0, mock(DirectBufferEx.class), 0, 0, mock(MessageConsumer.class));
    }
}
