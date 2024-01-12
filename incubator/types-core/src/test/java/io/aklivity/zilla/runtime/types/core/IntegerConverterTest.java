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

import static org.junit.Assert.assertEquals;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.converter.function.ValueConsumer;
import io.aklivity.zilla.runtime.types.core.config.IntegerConverterConfig;

public class IntegerConverterTest
{
    private final IntegerConverterConfig config = new IntegerConverterConfig();
    private final IntegerConverter converter = new IntegerConverter(config);

    @Test
    public void shouldVerifyValidInteger()
    {
        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0, 0, 0, 42};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(data.capacity(), converter.convert(data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyInvalidInteger()
    {
        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = "Not an Integer".getBytes();
        data.wrap(bytes, 0, bytes.length);
        assertEquals(-1, converter.convert(data, 0, data.capacity(), ValueConsumer.NOP));
    }
}
