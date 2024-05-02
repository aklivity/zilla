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
import io.aklivity.zilla.runtime.model.core.config.Int32ModelConfig;

public class Int32ValidatorTest
{
    public static final String BINARY = "binary";

    private final EngineContext context = mock(EngineContext.class);

    @Test
    public void shouldVerifyValidIntegerCompleteMessage()
    {
        Int32ModelConfig config = Int32ModelConfig.builder()
            .format(BINARY)
            .build();
        Int32ValidatorHandler handler = new Int32ValidatorHandler(config, context);
        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0, 0, 0, 42};
        data.wrap(bytes, 0, bytes.length);
        assertTrue(handler.validate(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyValidSignedIntegerCompleteMessage()
    {
        Int32ModelConfig config = Int32ModelConfig.builder()
            .format(BINARY)
            .build();
        Int32ValidatorHandler handler = new Int32ValidatorHandler(config, context);
        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {-1, -1, -1, -25};
        data.wrap(bytes, 0, bytes.length);
        assertTrue(handler.validate(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyValidAsciiIntegerCompleteMessage()
    {
        Int32ModelConfig config = Int32ModelConfig.builder().build();
        Int32ValidatorHandler handler = new Int32ValidatorHandler(config, context);
        DirectBuffer data = new UnsafeBuffer();

        String payload = "+8449999";
        byte[] bytes = payload.getBytes();
        data.wrap(bytes, 0, bytes.length);
        assertTrue(handler.validate(0L, 0L, ValidatorHandler.FLAGS_COMPLETE, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyValidTextIntegerMaxLimit()
    {
        Int32ModelConfig config = Int32ModelConfig.builder()
            .max(999)
            .build();
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
        Int32ValidatorHandler handler = new Int32ValidatorHandler(config, context);
        DirectBuffer data = new UnsafeBuffer();

        String payload = "8449999";
        byte[] bytes = payload.getBytes();
        data.wrap(bytes, 0, bytes.length);
        assertFalse(handler.validate(0L, 0L, ValidatorHandler.FLAGS_COMPLETE, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyValidTextIntegerExclusiveMaxLimit()
    {
        Int32ModelConfig config = Int32ModelConfig.builder()
            .max(999)
            .exclusiveMax(true)
            .build();
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
        Int32ValidatorHandler handler = new Int32ValidatorHandler(config, context);
        DirectBuffer data = new UnsafeBuffer();

        String payload = "999";
        byte[] bytes = payload.getBytes();
        data.wrap(bytes, 0, bytes.length);
        assertFalse(handler.validate(0L, 0L, ValidatorHandler.FLAGS_COMPLETE, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyValidTextIntegerExclusiveMinLimit()
    {
        Int32ModelConfig config = Int32ModelConfig.builder()
            .min(999)
            .exclusiveMin(true)
            .build();
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
        Int32ValidatorHandler handler = new Int32ValidatorHandler(config, context);
        DirectBuffer data = new UnsafeBuffer();

        String payload = "999";
        byte[] bytes = payload.getBytes();
        data.wrap(bytes, 0, bytes.length);
        assertFalse(handler.validate(0L, 0L, ValidatorHandler.FLAGS_COMPLETE, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyInvalidAsciiIntegerCompleteMessage()
    {
        Int32ModelConfig config = Int32ModelConfig.builder().build();
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
        Int32ValidatorHandler handler = new Int32ValidatorHandler(config, context);
        DirectBuffer data = new UnsafeBuffer();

        String payload = "-.1a1";
        byte[] bytes = payload.getBytes();
        data.wrap(bytes, 0, bytes.length);
        assertFalse(handler.validate(0L, 0L, ValidatorHandler.FLAGS_COMPLETE, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyValidAsciiNegativeCompleteMessage()
    {
        Int32ModelConfig config = Int32ModelConfig.builder().build();
        Int32ValidatorHandler handler = new Int32ValidatorHandler(config, context);
        DirectBuffer data = new UnsafeBuffer();

        String payload = "-125";
        byte[] bytes = payload.getBytes();
        data.wrap(bytes, 0, bytes.length);
        assertTrue(handler.validate(0L, 0L, ValidatorHandler.FLAGS_COMPLETE, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyValidAsciiFragmentedMessage()
    {
        Int32ModelConfig config = Int32ModelConfig.builder().build();
        Int32ValidatorHandler handler = new Int32ValidatorHandler(config, context);
        DirectBuffer data = new UnsafeBuffer();

        String payload = "-458";
        byte[] bytes = payload.getBytes();

        data.wrap(bytes, 0, 2);
        assertTrue(handler.validate(0L, 0L, ValidatorHandler.FLAGS_INIT, data, 0, data.capacity(), ValueConsumer.NOP));

        data.wrap(bytes, 2, 1);
        assertTrue(handler.validate(0L, 0L, 0x00, data, 0, data.capacity(), ValueConsumer.NOP));

        data.wrap(bytes, 3, 1);
        assertTrue(handler.validate(0L, 0L, ValidatorHandler.FLAGS_FIN, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyInvalidAsciiFragmentedMessage()
    {
        Int32ModelConfig config = Int32ModelConfig.builder().build();
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
        Int32ValidatorHandler handler = new Int32ValidatorHandler(config, context);
        DirectBuffer data = new UnsafeBuffer();

        String payload = "-4a4";
        byte[] bytes = payload.getBytes();

        data.wrap(bytes, 0, 2);
        assertTrue(handler.validate(0L, 0L, ValidatorHandler.FLAGS_INIT, data, 0, data.capacity(), ValueConsumer.NOP));

        data.wrap(bytes, 2, 1);
        assertFalse(handler.validate(0L, 0L, 0x00, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyValidIntegerFragmentedMessage()
    {
        Int32ModelConfig config = Int32ModelConfig.builder()
            .format(BINARY)
            .build();
        Int32ValidatorHandler handler = new Int32ValidatorHandler(config, context);
        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0, 0, 1, 42};

        data.wrap(bytes, 0, 2);
        assertTrue(handler.validate(0L, 0L, ValidatorHandler.FLAGS_INIT, data, 0, data.capacity(), ValueConsumer.NOP));

        data.wrap(bytes, 2, 1);
        assertTrue(handler.validate(0L, 0L, 0x00, data, 0, data.capacity(), ValueConsumer.NOP));

        data.wrap(bytes, 3, 1);
        assertTrue(handler.validate(0L, 0L, ValidatorHandler.FLAGS_FIN, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyInvalidIntegerCompleteMessage()
    {
        Int32ModelConfig config = Int32ModelConfig.builder()
            .format(BINARY)
            .build();
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
        Int32ValidatorHandler handler = new Int32ValidatorHandler(config, context);
        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = "Test value".getBytes();
        data.wrap(bytes, 0, bytes.length);
        assertFalse(handler.validate(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyInValidIntegerFragmentedMessage()
    {
        Int32ModelConfig config = Int32ModelConfig.builder()
            .format(BINARY)
            .build();
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
        Int32ValidatorHandler handler = new Int32ValidatorHandler(config, context);
        DirectBuffer data = new UnsafeBuffer();

        byte[] firstFragment = {0, 0, 0};
        data.wrap(firstFragment, 0, firstFragment.length);
        assertTrue(handler.validate(0L, 0L, ValidatorHandler.FLAGS_INIT, data, 0, data.capacity(), ValueConsumer.NOP));

        byte[] secondFragment = {0, 0};
        data.wrap(secondFragment, 0, secondFragment.length);
        assertFalse(handler.validate(0L, 0L, 0x00, data, 0, data.capacity(), ValueConsumer.NOP));

        byte[] finalFragment = {42};
        data.wrap(finalFragment, 0, finalFragment.length);
        assertFalse(handler.validate(0L, 0L, ValidatorHandler.FLAGS_FIN, data, 0, data.capacity(), ValueConsumer.NOP));
    }
}
