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

import static org.junit.Assert.assertEquals;

import java.nio.charset.StandardCharsets;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.model.core.config.StringModelConfig;

public class StringConverterTest
{
    @Test
    public void shouldVerifyValidUtf8()
    {
        StringModelConfig config = StringModelConfig.builder()
                .encoding("utf_8")
                .build();
        StringConverterHandler converter = new StringConverterHandler(config);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = "Valid String".getBytes();
        data.wrap(bytes, 0, bytes.length);
        assertEquals(data.capacity(), converter.convert(data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyInvalidUtf8()
    {
        StringModelConfig config = StringModelConfig.builder()
                .encoding("utf_8")
                .build();
        StringConverterHandler converter = new StringConverterHandler(config);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {(byte) 0xc0};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(-1, converter.convert(data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyValidUtf16()
    {
        StringModelConfig config = StringModelConfig.builder()
                .encoding("utf_16")
                .build();
        StringConverterHandler converter = new StringConverterHandler(config);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = "Valid String".getBytes(StandardCharsets.UTF_16);
        data.wrap(bytes, 0, bytes.length);

        assertEquals(data.capacity(), converter.convert(data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyIncompleteUtf16()
    {
        StringModelConfig config = StringModelConfig.builder()
                .encoding("utf_16")
                .build();
        StringConverterHandler converter = new StringConverterHandler(config);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x48};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(-1, converter.convert(data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyIncompleteSurrogatePairUtf16()
    {
        StringModelConfig config = StringModelConfig.builder()
                .encoding("utf_16")
                .build();
        StringConverterHandler converter = new StringConverterHandler(config);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {(byte) 0xD8, (byte) 0x00};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(-1, converter.convert(data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyInvalidSecondSurrogateUtf16()
    {
        StringModelConfig config = StringModelConfig.builder()
                .encoding("utf_16")
                .build();
        StringConverterHandler converter = new StringConverterHandler(config);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {(byte) 0xDC, (byte) 0x01};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(-1, converter.convert(data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyUnexpectedSecondSurrogateUtf16()
    {
        StringModelConfig config = StringModelConfig.builder()
                .encoding("utf_16")
                .build();
        StringConverterHandler converter = new StringConverterHandler(config);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {(byte) 0xDC, (byte) 0x80};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(-1, converter.convert(data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyValidMixedUtf16()
    {
        StringModelConfig config = StringModelConfig.builder()
                .encoding("utf_16")
                .build();
        StringConverterHandler converter = new StringConverterHandler(config);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0, 72, 0, 101, 0, 108, 0, 108, 0, 111, 65, 66, 67};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(-1, converter.convert(data, 0, data.capacity(), ValueConsumer.NOP));
    }
}
