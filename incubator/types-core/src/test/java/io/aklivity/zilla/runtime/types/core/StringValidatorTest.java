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
package io.aklivity.zilla.runtime.types.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.converter.function.ValueConsumer;
import io.aklivity.zilla.runtime.engine.validator.ValidatorHandler;
import io.aklivity.zilla.runtime.types.core.config.StringValidatorConfig;

public class StringValidatorTest
{
    @Test
    public void shouldVerifyValidUtf8()
    {
        StringValidatorConfig config = StringValidatorConfig.builder()
            .encoding("utf_8")
            .build();
        StringValidatorHandler handler = new StringValidatorHandler(config);
        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = "Valid String".getBytes();
        data.wrap(bytes, 0, bytes.length);
        assertTrue(handler.validate(data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyFragmentedValidUtf8()
    {
        StringValidatorConfig config = StringValidatorConfig.builder()
                .encoding("utf_8")
                .build();
        StringValidatorHandler handler = new StringValidatorHandler(config);
        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = "Valid String".getBytes();

        data.wrap(bytes, 0, 6);
        assertTrue(handler.validate(ValidatorHandler.FLAGS_INIT, data, 0, data.capacity(), ValueConsumer.NOP));

        data.wrap(bytes, 6, 5);
        assertTrue(handler.validate(0x00, data, 0, data.capacity(), ValueConsumer.NOP));

        data.wrap(bytes, 11, 1);
        assertTrue(handler.validate(ValidatorHandler.FLAGS_FIN, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyFragmentedInValidUtf8()
    {
        StringValidatorConfig config = StringValidatorConfig.builder()
                .encoding("utf_8")
                .build();
        StringValidatorHandler handler = new StringValidatorHandler(config);
        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {
            (byte) 'S', (byte) 't', (byte) 'r', (byte) 'i', (byte) 'n', (byte) 'g',
            (byte) 0xc0, (byte) 'V', (byte) 'a', (byte) 'l', (byte) 'i',
            (byte) 'd'
        };

        data.wrap(bytes, 0, 6);
        assertTrue(handler.validate(ValidatorHandler.FLAGS_INIT, data, 0, data.capacity(), ValueConsumer.NOP));

        data.wrap(bytes, 6, 5);
        assertTrue(handler.validate(0x00, data, 0, data.capacity(), ValueConsumer.NOP));

        data.wrap(bytes, 11, 1);
        assertFalse(handler.validate(ValidatorHandler.FLAGS_FIN, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyWithPendingCharBytes()
    {
        StringValidatorHandler handler = new StringValidatorHandler(new StringValidatorConfig("UTF-8"));
        UnsafeBuffer data = new UnsafeBuffer();

        byte[] bytes = {(byte) 0xc3, (byte) 0xa4};

        data.wrap(bytes, 0, 1);
        assertTrue(handler.validate(ValidatorHandler.FLAGS_INIT, data, 0, data.capacity(), ValueConsumer.NOP));

        data.wrap(bytes, 1, 1);
        assertTrue(handler.validate(ValidatorHandler.FLAGS_FIN, data, 0, data.capacity(), ValueConsumer.NOP));

    }
}
