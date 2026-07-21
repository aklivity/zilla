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
package io.aklivity.zilla.runtime.model.protobuf.internal;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public class ProtobufModelEventFormatterTest
{
    @Test
    public void shouldFormatValidationFailedEvent()
    {
        EngineContext context = mock(EngineContext.class);
        when(context.clock()).thenReturn(Clock.systemUTC());

        AtomicReference<DirectBufferEx> captured = new AtomicReference<>();
        MessageConsumer writer = (msgTypeId, buffer, index, length) ->
        {
            MutableDirectBufferEx copy = new UnsafeBufferEx(new byte[length]);
            copy.putBytes(0, buffer, index, length);
            captured.set(copy);
        };
        when(context.supplyEventWriter()).thenReturn(writer);

        ProtobufModelEventContext events = new ProtobufModelEventContext(context);
        events.validationFailure(0L, 0L, "boom");

        ProtobufModelEventFormatterFactory factory = new ProtobufModelEventFormatterFactory();
        assertEquals(ProtobufModel.NAME, factory.type());
        ProtobufModelEventFormatter formatter = factory.create(new Configuration());

        DirectBufferEx event = captured.get();
        String formatted = formatter.format(event, 0, event.capacity());

        assertEquals("A message payload failed validation. boom.", formatted);
    }
}
