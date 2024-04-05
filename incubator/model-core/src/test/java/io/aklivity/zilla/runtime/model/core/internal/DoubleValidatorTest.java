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
import io.aklivity.zilla.runtime.model.core.config.DoubleModelConfig;

public class DoubleValidatorTest
{
    public static final String BINARY = "binary";

    private final EngineContext context = mock(EngineContext.class);

    @Test
    public void shouldVerifyValidDoubleCompleteMessage()
    {
        DoubleModelConfig config = DoubleModelConfig.builder()
            .format(BINARY)
            .build();
        DoubleValidatorHandler handler = new DoubleValidatorHandler(config, context);
        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {-64, 0, 0, 0, 0, 0, 0, 0};
        data.wrap(bytes, 0, bytes.length);
        assertTrue(handler.validate(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyValidAsciiDoubleCompleteMessage()
    {
        DoubleModelConfig config = DoubleModelConfig.builder()
            .build();
        DoubleValidatorHandler handler = new DoubleValidatorHandler(config, context);
        DirectBuffer data = new UnsafeBuffer();

        String payload = "+10.1119998321";
        byte[] bytes = payload.getBytes();
        data.wrap(bytes, 0, bytes.length);
        assertTrue(handler.validate(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyValidAsciiNegativeDoubleCompleteMessage()
    {
        DoubleModelConfig config = DoubleModelConfig.builder()
            .build();
        DoubleValidatorHandler handler = new DoubleValidatorHandler(config, context);
        DirectBuffer data = new UnsafeBuffer();

        String payload = "-.11190092111111112";
        byte[] bytes = payload.getBytes();
        data.wrap(bytes, 0, bytes.length);
        assertTrue(handler.validate(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyInvalidAsciiDoubleCompleteMessage()
    {
        DoubleModelConfig config = DoubleModelConfig.builder()
            .build();
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
        DoubleValidatorHandler handler = new DoubleValidatorHandler(config, context);
        DirectBuffer data = new UnsafeBuffer();

        String payload = "-.11.19987654321";
        byte[] bytes = payload.getBytes();
        data.wrap(bytes, 0, bytes.length);
        assertFalse(handler.validate(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyValidTextDoubleExclusiveMaxLimit()
    {
        DoubleModelConfig config = DoubleModelConfig.builder()
            .max(99.9987654321)
            .exclusiveMax(true)
            .build();
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
        DoubleValidatorHandler handler = new DoubleValidatorHandler(config, context);
        DirectBuffer data = new UnsafeBuffer();

        String payload = "99.9987654321";
        byte[] bytes = payload.getBytes();
        data.wrap(bytes, 0, bytes.length);
        assertFalse(handler.validate(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyValidTextDoubleMaxLimit()
    {
        DoubleModelConfig config = DoubleModelConfig.builder()
            .max(99.9987654321)
            .build();
        DoubleValidatorHandler handler = new DoubleValidatorHandler(config, context);
        DirectBuffer data = new UnsafeBuffer();

        String payload = "99.9987654321";
        byte[] bytes = payload.getBytes();
        data.wrap(bytes, 0, bytes.length);
        assertTrue(handler.validate(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyValidAsciiFragmentedMessage()
    {
        DoubleModelConfig config = DoubleModelConfig.builder()
            .build();
        DoubleValidatorHandler handler = new DoubleValidatorHandler(config, context);
        DirectBuffer data = new UnsafeBuffer();

        String payload = "-99.991";
        byte[] bytes = payload.getBytes();

        data.wrap(bytes, 0, 2);
        assertTrue(handler.validate(0L, 0L, ValidatorHandler.FLAGS_INIT, data, 0, data.capacity(), ValueConsumer.NOP));

        data.wrap(bytes, 2, 1);
        assertTrue(handler.validate(0L, 0L, 0x00, data, 0, data.capacity(), ValueConsumer.NOP));

        data.wrap(bytes, 3, 4);
        assertTrue(handler.validate(0L, 0L, ValidatorHandler.FLAGS_FIN, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyInvalidAsciiFragmentedMessage()
    {
        DoubleModelConfig config = DoubleModelConfig.builder()
            .build();
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
        DoubleValidatorHandler handler = new DoubleValidatorHandler(config, context);
        DirectBuffer data = new UnsafeBuffer();

        String payload = "-.99.991";
        byte[] bytes = payload.getBytes();

        data.wrap(bytes, 0, 2);
        assertTrue(handler.validate(0L, 0L, ValidatorHandler.FLAGS_INIT, data, 0, data.capacity(), ValueConsumer.NOP));

        data.wrap(bytes, 2, 1);
        assertTrue(handler.validate(0L, 0L, 0x00, data, 0, data.capacity(), ValueConsumer.NOP));

        data.wrap(bytes, 3, 5);
        assertFalse(handler.validate(0L, 0L, ValidatorHandler.FLAGS_FIN, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyValidDoubleFragmentedMessage()
    {
        DoubleModelConfig config = DoubleModelConfig.builder()
            .format(BINARY)
            .build();
        DoubleValidatorHandler handler = new DoubleValidatorHandler(config, context);
        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {-1, -17, 94, -95, -120, 23, -78, 63};


        data.wrap(bytes, 0, 2);
        assertTrue(handler.validate(0L, 0L, ValidatorHandler.FLAGS_INIT, data, 0, data.capacity(), ValueConsumer.NOP));

        data.wrap(bytes, 2, 1);
        assertTrue(handler.validate(0L, 0L, 0x00, data, 0, data.capacity(), ValueConsumer.NOP));

        data.wrap(bytes, 3, 5);
        assertTrue(handler.validate(0L, 0L, ValidatorHandler.FLAGS_FIN, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyInvalidDoubleFragmentedMessage()
    {
        DoubleModelConfig config = DoubleModelConfig.builder()
            .format(BINARY)
            .build();
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
        DoubleValidatorHandler handler = new DoubleValidatorHandler(config, context);
        DirectBuffer data = new UnsafeBuffer();

        byte[] firstFragment = {0, 0, 0};
        data.wrap(firstFragment, 0, firstFragment.length);
        assertTrue(handler.validate(0L, 0L, ValidatorHandler.FLAGS_INIT, data, 0, data.capacity(), ValueConsumer.NOP));

        byte[] secondFragment = {0, 0};
        data.wrap(secondFragment, 0, secondFragment.length);
        assertTrue(handler.validate(0L, 0L, 0x00, data, 0, data.capacity(), ValueConsumer.NOP));

        byte[] finalFragment = {42};
        data.wrap(finalFragment, 0, finalFragment.length);
        assertFalse(handler.validate(0L, 0L, ValidatorHandler.FLAGS_FIN, data, 0, data.capacity(), ValueConsumer.NOP));
    }

}
