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
package io.aklivity.zilla.runtime.model.core.internal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.model.ValidatorHandler;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.model.core.config.StringModelConfig;

public class StringValidatorTest
{
    private final EngineContext context = mock(EngineContext.class);

    @Test
    public void shouldVerifyValidUtf8()
    {
        StringModelConfig config = StringModelConfig.builder()
            .encoding("utf_8")
            .build();
        StringValidatorHandler handler = new StringValidatorHandler(config, context);
        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = "Valid String".getBytes();
        data.wrap(bytes, 0, bytes.length);
        assertTrue(handler.validate(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyValidUtf8WithPattern()
    {
        StringModelConfig config = StringModelConfig.builder()
            .encoding("utf_8")
            .pattern("^[a-zA-Z\\s]+$")
            .build();
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
        StringValidatorHandler handler = new StringValidatorHandler(config, context);
        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = "Hello123".getBytes();
        data.wrap(bytes, 0, bytes.length);
        assertFalse(handler.validate(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyValidUtf8WithInvalidLength()
    {
        StringModelConfig config = StringModelConfig.builder()
            .encoding("utf_8")
            .minLength(1)
            .maxLength(10)
            .build();
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
        StringValidatorHandler handler = new StringValidatorHandler(config, context);
        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = "Valid String".getBytes();
        data.wrap(bytes, 0, bytes.length);
        assertFalse(handler.validate(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyFragmentedValidUtf8()
    {
        StringModelConfig config = StringModelConfig.builder()
                .encoding("utf_8")
                .build();
        StringValidatorHandler handler = new StringValidatorHandler(config, context);
        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = "Valid String".getBytes();

        data.wrap(bytes, 0, 6);
        assertTrue(handler.validate(0L, 0L, ValidatorHandler.FLAGS_INIT, data, 0, data.capacity(), ValueConsumer.NOP));

        data.wrap(bytes, 6, 5);
        assertTrue(handler.validate(0L, 0L, 0x00, data, 0, data.capacity(), ValueConsumer.NOP));

        data.wrap(bytes, 11, 1);
        assertTrue(handler.validate(0L, 0L, ValidatorHandler.FLAGS_FIN, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyFragmentedInValidUtf8()
    {
        StringModelConfig config = StringModelConfig.builder()
                .encoding("utf_8")
                .build();
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
        StringValidatorHandler handler = new StringValidatorHandler(config, context);
        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {
            (byte) 'S', (byte) 't', (byte) 'r', (byte) 'i', (byte) 'n', (byte) 'g',
            (byte) 0xc0, (byte) 'V', (byte) 'a', (byte) 'l', (byte) 'i',
            (byte) 'd'
        };

        data.wrap(bytes, 0, 6);
        assertTrue(handler.validate(0L, 0L, ValidatorHandler.FLAGS_INIT, data, 0, data.capacity(), ValueConsumer.NOP));

        data.wrap(bytes, 6, 5);
        assertFalse(handler.validate(0L, 0L, 0x00, data, 0, data.capacity(), ValueConsumer.NOP));

        data.wrap(bytes, 11, 1);
        assertFalse(handler.validate(0L, 0L, ValidatorHandler.FLAGS_FIN, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyWithPendingCharBytes()
    {
        StringModelConfig config = StringModelConfig.builder()
            .encoding("utf_8")
            .build();
        StringValidatorHandler handler = new StringValidatorHandler(config, context);
        UnsafeBuffer data = new UnsafeBuffer();

        byte[] bytes = {(byte) 0xc3, (byte) 0xa4};

        data.wrap(bytes, 0, 1);
        assertTrue(handler.validate(0L, 0L, ValidatorHandler.FLAGS_INIT, data, 0, data.capacity(), ValueConsumer.NOP));

        data.wrap(bytes, 1, 1);
        assertTrue(handler.validate(0L, 0L, ValidatorHandler.FLAGS_FIN, data, 0, data.capacity(), ValueConsumer.NOP));

    }
}
