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
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public class StringEncodingTest
{
    @Test
    public void shouldVerifyValidUTF8()
    {
        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = "Valid String".getBytes();
        data.wrap(bytes, 0, bytes.length);

        assertTrue(StringEncoding.UTF_8.validate(data, 0, bytes.length));
    }

    @Test
    public void shouldVerifyValidUTF16()
    {
        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = "Valid String".getBytes(StandardCharsets.UTF_8);
        data.wrap(bytes, 0, bytes.length);

        assertTrue(StringEncoding.UTF_8.validate(data, 0, bytes.length));
    }

    @Test
    public void shouldVerifyStringEncodingOf()
    {
        assertEquals(StringEncoding.UTF_8, StringEncoding.of("utf_8"));
        assertEquals(StringEncoding.UTF_16, StringEncoding.of("utf_16"));
        assertEquals(StringEncoding.INVALID, StringEncoding.of("invalid_encoding"));
    }
}
