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

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.model.ValidatorHandler;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.model.core.config.IntegerModelConfig;

public class IntegerValidatorTest
{
    private final IntegerModelConfig config = IntegerModelConfig.builder().build();
    private final IntegerValidatorHandler handler = new IntegerValidatorHandler(config);

    @Test
    public void shouldVerifyValidIntegerCompleteMessage()
    {
        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0, 0, 0, 42};
        data.wrap(bytes, 0, bytes.length);
        assertTrue(handler.validate(data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyValidIntegerFragmentedMessage()
    {
        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0, 0, 0, 42};

        data.wrap(bytes, 0, 2);
        assertTrue(handler.validate(ValidatorHandler.FLAGS_INIT, data, 0, data.capacity(), ValueConsumer.NOP));

        data.wrap(bytes, 2, 1);
        assertTrue(handler.validate(0x00, data, 0, data.capacity(), ValueConsumer.NOP));

        data.wrap(bytes, 3, 1);
        assertTrue(handler.validate(ValidatorHandler.FLAGS_FIN, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyInvalidIntegerCompleteMessage()
    {
        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = "Not an Integer".getBytes();
        data.wrap(bytes, 0, bytes.length);
        assertFalse(handler.validate(data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyInValidIntegerFragmentedMessage()
    {
        DirectBuffer data = new UnsafeBuffer();

        byte[] firstFragment = {0, 0, 0};
        data.wrap(firstFragment, 0, firstFragment.length);
        assertTrue(handler.validate(ValidatorHandler.FLAGS_INIT, data, 0, data.capacity(), ValueConsumer.NOP));

        byte[] secondFragment = {0, 0};
        data.wrap(secondFragment, 0, secondFragment.length);
        assertFalse(handler.validate(0x00, data, 0, data.capacity(), ValueConsumer.NOP));

        byte[] finalFragment = {42};
        data.wrap(finalFragment, 0, finalFragment.length);
        assertFalse(handler.validate(ValidatorHandler.FLAGS_FIN, data, 0, data.capacity(), ValueConsumer.NOP));
    }
}
