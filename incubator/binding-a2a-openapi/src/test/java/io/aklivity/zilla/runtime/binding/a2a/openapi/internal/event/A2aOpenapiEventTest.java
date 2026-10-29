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
package io.aklivity.zilla.runtime.binding.a2a.openapi.internal.event;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.event.EventFormatterSpi;

public class A2aOpenapiEventTest
{
    @Test
    public void shouldFormatOperationDenied()
    {
        final EngineContext context = mock(EngineContext.class);
        final DirectBufferEx[] written = new DirectBufferEx[1];
        when(context.supplyTypeId("a2a-openapi")).thenReturn(7);
        when(context.supplyEventId("binding.a2a.openapi.operation.denied")).thenReturn(1);
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn((typeId, buffer, index, length) ->
        {
            final byte[] copy = new byte[length];
            buffer.getBytes(index, copy);
            written[0] = new UnsafeBufferEx(copy);
        });

        new A2aOpenapiEventContext(context).operationDenied(42L, "operation \"unmapped\" requires scheme \"oauthAuth\"");

        final A2aOpenapiEventFormatterFactory factory = new A2aOpenapiEventFormatterFactory();
        final EventFormatterSpi formatter = factory.create(new Configuration());

        assertThat(factory.type(), equalTo("a2a-openapi"));
        assertThat(formatter.format(written[0], 0, written[0].capacity()),
            equalTo("Operation denied (operation \"unmapped\" requires scheme \"oauthAuth\")."));
    }
}
