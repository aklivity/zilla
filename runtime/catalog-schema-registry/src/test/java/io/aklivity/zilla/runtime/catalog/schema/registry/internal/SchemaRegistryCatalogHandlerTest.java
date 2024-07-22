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
package io.aklivity.zilla.runtime.catalog.schema.registry.internal;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.time.Duration;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.catalog.schema.registry.config.SchemaRegistryOptionsConfig;
import io.aklivity.zilla.runtime.catalog.schema.registry.internal.config.SchemaRegistryCatalogConfig;
import io.aklivity.zilla.runtime.catalog.schema.registry.internal.handler.SchemaRegistryCatalogHandler;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.CatalogConfig;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;

public class SchemaRegistryCatalogHandlerTest
{
    private SchemaRegistryCatalogConfig config;
    private EngineContext context = mock(EngineContext.class);

    @Before
    public void setup()
    {
        config = CatalogConfig.builder(c -> new SchemaRegistryCatalogConfig(context, c))
            .namespace("test")
            .name("test0")
            .type(SchemaRegistryCatalogFactorySpi.TYPE)
            .options(SchemaRegistryOptionsConfig::builder)
                .url("http://localhost:8081")
                .context("default")
                .maxAge(Duration.ofSeconds(1))
                .build()
            .build();
    }

    @Test
    public void shouldVerifyMaxPadding()
    {
        SchemaRegistryCatalogHandler catalog = new SchemaRegistryCatalogHandler(config);

        assertEquals(5, catalog.encodePadding(0));
    }

    @Test
    public void shouldVerifyEncodedData()
    {
        SchemaRegistryCatalogHandler catalog = new SchemaRegistryCatalogHandler(config);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x06, 0x69, 0x64,
            0x30, 0x10, 0x70, 0x6f, 0x73, 0x69, 0x74, 0x69, 0x76, 0x65};
        data.wrap(bytes, 0, bytes.length);

        assertEquals(18, catalog.encode(0L, 0L, 1, data, 0, data.capacity(),
            ValueConsumer.NOP, CatalogHandler.Encoder.IDENTITY));
    }

    @Test
    public void shouldResolveSchemaIdAndProcessData()
    {

        SchemaRegistryCatalogHandler catalog = new SchemaRegistryCatalogHandler(config);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x00, 0x00, 0x00, 0x00, 0x09, 0x06, 0x69, 0x64,
            0x30, 0x10, 0x70, 0x6f, 0x73, 0x69, 0x74, 0x69, 0x76, 0x65};
        data.wrap(bytes, 0, bytes.length);

        int valLength = catalog.decode(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP, CatalogHandler.Decoder.IDENTITY);

        assertEquals(data.capacity() - 5, valLength);
    }

    @Test
    public void shouldResolveSchemaIdFromData()
    {
        SchemaRegistryCatalogHandler catalog = new SchemaRegistryCatalogHandler(config);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x00, 0x00, 0x00, 0x00, 0x09, 0x06, 0x69, 0x64,
            0x30, 0x10, 0x70, 0x6f, 0x73, 0x69, 0x74, 0x69, 0x76, 0x65};
        data.wrap(bytes, 0, bytes.length);

        int schemaId = catalog.resolve(data, 0, data.capacity());

        assertEquals(9, schemaId);
    }
}
