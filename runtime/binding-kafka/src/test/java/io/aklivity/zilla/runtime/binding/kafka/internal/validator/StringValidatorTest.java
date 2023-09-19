/*
 * Copyright 2021-2023 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.kafka.internal.validator;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.kafka.internal.validator.config.StringValidatorConfig;

public class StringValidatorTest
{
    @Test
    public void shouldVerifyValidUTF8()
    {
        StringValidatorConfig config = new StringValidatorConfig("utf_8");
        StringValidator validator = new StringValidator(config);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = "Valid String".getBytes();
        data.wrap(bytes, 0, bytes.length);
        assertTrue(validator.read(data, 0, data.capacity()));
    }

    @Test
    public void shouldVerifyInvalidUTF8()
    {
        StringValidatorConfig config = new StringValidatorConfig("utf_8");
        StringValidator validator = new StringValidator(config);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {(byte) 0xc0};
        data.wrap(bytes, 0, bytes.length);
        assertFalse(validator.read(data, 0, data.capacity()));
    }

    @Test
    public void shouldVerifyValidUTF16()
    {
        StringValidatorConfig config = new StringValidatorConfig("utf_16");
        StringValidator validator = new StringValidator(config);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = "Valid String".getBytes(StandardCharsets.UTF_16);
        data.wrap(bytes, 0, bytes.length);

        assertTrue(validator.read(data, 0, data.capacity()));
    }

    @Test
    public void shouldVerifyIncompleteUTF16()
    {
        StringValidatorConfig config = new StringValidatorConfig("utf_16");
        StringValidator validator = new StringValidator(config);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x48};
        data.wrap(bytes, 0, bytes.length);
        assertFalse(validator.read(data, 0, data.capacity()));
    }

    @Test
    public void shouldVerifyIncompleteSurrogatePairUTF16()
    {
        StringValidatorConfig config = new StringValidatorConfig("utf_16");
        StringValidator validator = new StringValidator(config);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {(byte) 0xD8, (byte) 0x00};
        data.wrap(bytes, 0, bytes.length);
        assertFalse(validator.read(data, 0, data.capacity()));
    }

    @Test
    public void shouldVerifyInvalidSecondSurrogateUTF16()
    {
        StringValidatorConfig config = new StringValidatorConfig("utf_16");
        StringValidator validator = new StringValidator(config);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {(byte) 0xDC, (byte) 0x01};
        data.wrap(bytes, 0, bytes.length);
        assertFalse(validator.read(data, 0, data.capacity()));
    }

    @Test
    public void shouldVerifyUnexpectedSecondSurrogateUTF16()
    {
        StringValidatorConfig config = new StringValidatorConfig("utf_16");
        StringValidator validator = new StringValidator(config);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {(byte) 0xDC, (byte) 0x80};
        data.wrap(bytes, 0, bytes.length);
        assertFalse(validator.read(data, 0, data.capacity()));
    }

    @Test
    public void shouldVerifyValidMixedUTF16()
    {
        StringValidatorConfig config = new StringValidatorConfig("utf_16");
        StringValidator validator = new StringValidator(config);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0, 72, 0, 101, 0, 108, 0, 108, 0, 111, 65, 66, 67};
        data.wrap(bytes, 0, bytes.length);
        assertFalse(validator.write(data, 0, data.capacity()));
    }
}
