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

import static io.aklivity.zilla.runtime.engine.validator.FragmentValidator.FLAGS_COMPLETE;
import static org.junit.Assert.assertEquals;

import java.nio.charset.StandardCharsets;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.validator.function.FragmentConsumer;
import io.aklivity.zilla.runtime.engine.validator.function.ValueConsumer;
import io.aklivity.zilla.runtime.validator.core.config.StringValidatorConfig;

public class StringValidatorTest
{
    private static final int FLAGS_INIT = 0x02;

    @Test
    public void shouldVerifyValidUTF8()
    {
        StringValidatorConfig config = StringValidatorConfig.builder()
                .encoding("utf_8")
                .build();
        StringValidator validator = new StringValidator(config);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = "Valid String".getBytes();
        data.wrap(bytes, 0, bytes.length);
        assertEquals(data.capacity(), validator.validate(data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyInvalidUTF8()
    {
        StringValidatorConfig config = StringValidatorConfig.builder()
                .encoding("utf_8")
                .build();
        StringValidator validator = new StringValidator(config);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {(byte) 0xc0};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(-1, validator.validate(data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyValidUTF16()
    {
        StringValidatorConfig config = StringValidatorConfig.builder()
                .encoding("utf_16")
                .build();
        StringValidator validator = new StringValidator(config);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = "Valid String".getBytes(StandardCharsets.UTF_16);
        data.wrap(bytes, 0, bytes.length);

        assertEquals(data.capacity(), validator.validate(data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyIncompleteUTF16()
    {
        StringValidatorConfig config = StringValidatorConfig.builder()
                .encoding("utf_16")
                .build();
        StringValidator validator = new StringValidator(config);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x48};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(-1, validator.validate(data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyIncompleteSurrogatePairUTF16()
    {
        StringValidatorConfig config = StringValidatorConfig.builder()
                .encoding("utf_16")
                .build();
        StringValidator validator = new StringValidator(config);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {(byte) 0xD8, (byte) 0x00};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(-1, validator.validate(data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyInvalidSecondSurrogateUTF16()
    {
        StringValidatorConfig config = StringValidatorConfig.builder()
                .encoding("utf_16")
                .build();
        StringValidator validator = new StringValidator(config);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {(byte) 0xDC, (byte) 0x01};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(-1, validator.validate(data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyUnexpectedSecondSurrogateUTF16()
    {
        StringValidatorConfig config = StringValidatorConfig.builder()
                .encoding("utf_16")
                .build();
        StringValidator validator = new StringValidator(config);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {(byte) 0xDC, (byte) 0x80};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(-1, validator.validate(data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyValidMixedUTF16()
    {
        StringValidatorConfig config = StringValidatorConfig.builder()
                .encoding("utf_16")
                .build();
        StringValidator validator = new StringValidator(config);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0, 72, 0, 101, 0, 108, 0, 108, 0, 111, 65, 66, 67};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(-1, validator.validate(data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyCompleteAndValidMessage()
    {
        StringValidatorConfig config = StringValidatorConfig.builder()
                .encoding("utf_8")
                .build();
        StringValidator validator = new StringValidator(config);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = "Valid String".getBytes();
        data.wrap(bytes, 0, bytes.length);
        assertEquals(data.capacity(), validator.validate(FLAGS_COMPLETE, data, 0, data.capacity(), FragmentConsumer.NOP));
    }

    @Test
    public void shouldVerifyIncompleteMessage()
    {
        StringValidatorConfig config = new StringValidatorConfig("utf_8");
        StringValidator validator = new StringValidator(config);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {(byte) 0xc0};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(0, validator.validate(FLAGS_INIT, data, 0, data.capacity(), FragmentConsumer.NOP));
    }

    @Test
    public void shouldVerifyValidFragmentUTF8()
    {
        StringValidatorConfig config = StringValidatorConfig.builder()
                .encoding("utf_8")
                .build();
        StringValidator validator = new StringValidator(config);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = "Valid String".getBytes();
        data.wrap(bytes, 0, bytes.length);

        assertEquals(0, validator.validate(0x00, data, 0, data.capacity(), FragmentConsumer.NOP));

        assertEquals(data.capacity(), validator.validate(0x01, data, 0, data.capacity(), FragmentConsumer.NOP));
    }
}
