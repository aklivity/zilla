/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.catalog.apicurio.internal;

import static org.agrona.BitUtil.SIZE_OF_BYTE;
import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.time.Duration;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.runtime.catalog.apicurio.config.ApicurioOptionsConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;

public class ApicurioCatalogHandlerTest
{
    private static final int SIZE_OF_DEFAULT_PREFIX = SIZE_OF_BYTE + SIZE_OF_LONG;
    private static final int SIZE_OF_LEGACY_PREFIX = SIZE_OF_BYTE + SIZE_OF_INT;

    private EngineContext context = mock(EngineContext.class);

    @Test
    public void shouldVerifyDefaultEncodePadding()
    {
        ApicurioOptionsConfig config = ApicurioOptionsConfig.builder()
                .url("http://localhost:8080")
                .groupId("groupId")
                .maxAge(Duration.ofSeconds(1))
                .build();

        ApicurioCatalogHandler catalog = new ApicurioCatalogHandler(config, context, 0L);

        int actual = catalog.encodePadding(0);

        assertEquals(SIZE_OF_DEFAULT_PREFIX, actual);
    }

    @Test
    public void shouldVerifyLegacyEncodePadding()
    {
        ApicurioOptionsConfig config = ApicurioOptionsConfig.builder()
                .url("http://localhost:8080")
                .groupId("groupId")
                .maxAge(Duration.ofSeconds(1))
                .idEncoding("legacy")
                .build();

        ApicurioCatalogHandler catalog = new ApicurioCatalogHandler(config, context, 0L);

        int actual = catalog.encodePadding(0);

        assertEquals(SIZE_OF_LEGACY_PREFIX, actual);
    }

    @Test
    public void shouldEncodeDefaultSchemaId()
    {
        ApicurioOptionsConfig config = ApicurioOptionsConfig.builder()
                .url("http://localhost:8080")
                .groupId("groupId")
                .maxAge(Duration.ofSeconds(1))
                .build();

        ApicurioCatalogHandler catalog = new ApicurioCatalogHandler(config, context, 0L);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {
            0x06, 0x69, 0x64, 0x30, 0x10, 0x70, 0x6f, 0x73,
            0x69, 0x74, 0x69, 0x76, 0x65 };

        data.wrap(bytes, 0, bytes.length);

        int actual = catalog.encode(0L, 0L, 1, data, 0, data.capacity(), ValueConsumer.NOP, CatalogHandler.Encoder.IDENTITY);

        assertEquals(SIZE_OF_DEFAULT_PREFIX + bytes.length, actual);
    }

    @Test
    public void shouldEncodeLegacySchemaId()
    {
        ApicurioOptionsConfig config = ApicurioOptionsConfig.builder()
                .url("http://localhost:8080")
                .groupId("groupId")
                .maxAge(Duration.ofSeconds(1))
                .idEncoding("legacy")
                .build();

        ApicurioCatalogHandler catalog = new ApicurioCatalogHandler(config, context, 0L);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {
            0x06, 0x69, 0x64, 0x30, 0x10, 0x70, 0x6f, 0x73,
            0x69, 0x74, 0x69, 0x76, 0x65 };

        data.wrap(bytes, 0, bytes.length);

        int actual = catalog.encode(0L, 0L, 1, data, 0, data.capacity(), ValueConsumer.NOP, CatalogHandler.Encoder.IDENTITY);

        assertEquals(SIZE_OF_LEGACY_PREFIX + bytes.length, actual);
    }

    @Test
    public void shouldDecodeDefaultSchemaId()
    {
        ApicurioOptionsConfig config = ApicurioOptionsConfig.builder()
                .url("http://localhost:8080")
                .groupId("groupId")
                .maxAge(Duration.ofSeconds(1))
                .build();

        ApicurioCatalogHandler catalog = new ApicurioCatalogHandler(config, context, 0L);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x09,
            0x06, 0x69, 0x64, 0x30, 0x10, 0x70, 0x6f, 0x73,
            0x69, 0x74, 0x69, 0x76, 0x65 };

        data.wrap(bytes, 0, bytes.length);

        int actual = catalog.decode(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP, CatalogHandler.Decoder.IDENTITY);

        assertEquals(data.capacity() - SIZE_OF_DEFAULT_PREFIX, actual);
    }

    @Test
    public void shouldDecodeLegacySchemaId()
    {
        ApicurioOptionsConfig config = ApicurioOptionsConfig.builder()
                .url("http://localhost:8080")
                .groupId("groupId")
                .maxAge(Duration.ofSeconds(1))
                .idEncoding("legacy")
                .build();

        ApicurioCatalogHandler catalog = new ApicurioCatalogHandler(config, context, 0L);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {
            0x00, 0x00, 0x00, 0x00, 0x09,
            0x06, 0x69, 0x64, 0x30, 0x10, 0x70, 0x6f, 0x73,
            0x69, 0x74, 0x69, 0x76, 0x65 };

        data.wrap(bytes, 0, bytes.length);

        int actual = catalog.decode(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP, CatalogHandler.Decoder.IDENTITY);

        assertEquals(data.capacity() - SIZE_OF_LEGACY_PREFIX, actual);
    }

    @Test
    public void shouldResolveDefaultSchemaId()
    {
        ApicurioOptionsConfig config = ApicurioOptionsConfig.builder()
                .url("http://localhost:8080")
                .groupId("groupId")
                .maxAge(Duration.ofSeconds(1))
                .build();

        ApicurioCatalogHandler catalog = new ApicurioCatalogHandler(config, context, 0L);

        MutableDirectBuffer data = new UnsafeBuffer(new byte[SIZE_OF_DEFAULT_PREFIX + 13]);

        data.putByte(0, (byte) 0);

        byte[] bytes = {
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x09,
            0x06, 0x69, 0x64, 0x30, 0x10, 0x70, 0x6f, 0x73,
            0x69, 0x74, 0x69, 0x76, 0x65 };

        data.wrap(bytes, 0, bytes.length);

        int actual = catalog.resolve(data, 0, data.capacity());

        assertEquals(9, actual);
    }

    @Test
    public void shouldResolveLegacySchemaId()
    {
        ApicurioOptionsConfig config = ApicurioOptionsConfig.builder()
                .url("http://localhost:8080")
                .groupId("groupId")
                .maxAge(Duration.ofSeconds(1))
                .idEncoding("legacy")
                .build();

        ApicurioCatalogHandler catalog = new ApicurioCatalogHandler(config, context, 0L);

        MutableDirectBuffer data = new UnsafeBuffer(new byte[SIZE_OF_LEGACY_PREFIX + 13]);

        data.putByte(0, (byte) 0);

        byte[] bytes = {
            0x00, 0x00, 0x00, 0x00, 0x09,
            0x06, 0x69, 0x64, 0x30, 0x10, 0x70, 0x6f, 0x73,
            0x69, 0x74, 0x69, 0x76, 0x65 };

        data.wrap(bytes, 0, bytes.length);

        int actual = catalog.resolve(data, 0, data.capacity());

        assertEquals(9, actual);
    }
}
