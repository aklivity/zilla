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
package io.aklivity.zilla.runtime.validator.core;

import static org.junit.Assert.assertEquals;

import java.nio.charset.StandardCharsets;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.validator.function.FragmentConsumer;
import io.aklivity.zilla.runtime.validator.core.config.StringValidatorConfig;

public class StringFragmentValidatorTest
{
    private final FragmentConsumer fragmentConsumer = (flags, buffer, index, length) -> {};
    private static final int FLAGS_INIT = 0x02;
    private static final int FLAGS_FIN = 0x01;

    @Test
    public void shouldVerifyCompleteAndValidMessage()
    {
        StringValidatorConfig config = new StringValidatorConfig("utf_8");
        StringFragmentValidator validator = new StringFragmentValidator(config);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = "Valid String".getBytes();
        data.wrap(bytes, 0, bytes.length);
        assertEquals(data.capacity(), validator.validate(FLAGS_FIN, data, 0, data.capacity(), fragmentConsumer));
    }

    @Test
    public void shouldVerifyIncompleteMessage()
    {
        StringValidatorConfig config = new StringValidatorConfig("utf_8");
        StringFragmentValidator validator = new StringFragmentValidator(config);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {(byte) 0xc0};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(0, validator.validate(FLAGS_INIT, data, 0, data.capacity(), fragmentConsumer));
    }

    @Test
    public void shouldVerifyValidUTF16()
    {
        StringValidatorConfig config = new StringValidatorConfig("utf_16");
        StringFragmentValidator validator = new StringFragmentValidator(config);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = "Valid String".getBytes(StandardCharsets.UTF_16);
        data.wrap(bytes, 0, bytes.length);

        assertEquals(data.capacity(), validator.validate(FLAGS_FIN, data, 0, data.capacity(), fragmentConsumer));
    }

    @Test
    public void shouldVerifyIncompleteUTF16()
    {
        StringValidatorConfig config = new StringValidatorConfig("utf_16");
        StringFragmentValidator validator = new StringFragmentValidator(config);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x48};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(-1, validator.validate(FLAGS_FIN, data, 0, data.capacity(), fragmentConsumer));
    }
}
