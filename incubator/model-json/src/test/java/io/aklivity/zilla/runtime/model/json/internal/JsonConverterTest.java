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
package io.aklivity.zilla.runtime.model.json.internal;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.config.CatalogConfig;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.TestCatalogHandler;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.config.TestCatalogOptionsConfig;
import io.aklivity.zilla.runtime.model.json.config.JsonModelConfig;

public class JsonConverterTest
{
    private static final String OBJECT_SCHEMA = "{" +
                    "\"type\": \"object\"," +
                    "\"properties\": " +
                    "{" +
                        "\"id\": {" +
                        "\"type\": \"string\"" +
                        "}," +
                        "\"status\": {" +
                        "\"type\": \"string\"" +
                        "}" +
                    "}," +
                    "\"required\": [" +
                    "\"id\"," +
                    "\"status\"" +
                    "]" +
                "}";

    private static final String ARRAY_SCHEMA = "{" +
                    "\"type\": \"array\"," +
                    "\"items\": " +
                        OBJECT_SCHEMA +
                    "}";

    private final JsonModelConfig config = JsonModelConfig.builder()
            .catalog()
                .name("test0")
                    .schema()
                        .strategy("topic")
                        .subject(null)
                        .version("latest")
                        .id(0)
                        .build()
                .build()
            .build();
    private EngineContext context;

    @Before
    public void init()
    {
        context = mock(EngineContext.class);
    }

    @Test
    public void shouldVerifyValidJsonObject()
    {
        TestCatalogOptionsConfig testCatalogOptionsConfig = TestCatalogOptionsConfig.builder()
            .id(9)
            .schema(OBJECT_SCHEMA)
            .build();
        CatalogConfig catalogConfig = new CatalogConfig("test", "test0", "test", testCatalogOptionsConfig);
        when(context.supplyCatalog(catalogConfig.id)).thenReturn(new TestCatalogHandler(testCatalogOptionsConfig));
        JsonReadConverterHandler converter = new JsonReadConverterHandler(config, context);

        DirectBuffer data = new UnsafeBuffer();

        String payload =
                "{" +
                    "\"id\": \"123\"," +
                    "\"status\": \"OK\"" +
                "}";
        byte[] bytes = payload.getBytes();
        data.wrap(bytes, 0, bytes.length);
        assertEquals(data.capacity(), converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyValidJsonArray()
    {
        TestCatalogOptionsConfig testCatalogOptionsConfig = TestCatalogOptionsConfig.builder()
            .id(9)
            .schema(ARRAY_SCHEMA)
            .build();
        CatalogConfig catalogConfig = new CatalogConfig("test", "test0", "test", testCatalogOptionsConfig);
        when(context.supplyCatalog(catalogConfig.id)).thenReturn(new TestCatalogHandler(testCatalogOptionsConfig));
        JsonWriteConverterHandler converter = new JsonWriteConverterHandler(config, context);

        DirectBuffer data = new UnsafeBuffer();

        String payload =
            "[" +
                "{" +
                    "\"id\": \"123\"," +
                    "\"status\": \"OK\"" +
                "}" +
            "]";
        byte[] bytes = payload.getBytes();
        data.wrap(bytes, 0, bytes.length);

        assertEquals(data.capacity(), converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyInvalidJsonObject()
    {
        TestCatalogOptionsConfig testCatalogOptionsConfig = TestCatalogOptionsConfig.builder()
            .id(9)
            .schema(OBJECT_SCHEMA)
            .build();
        CatalogConfig catalogConfig = new CatalogConfig("test", "test0", "test", testCatalogOptionsConfig);
        when(context.supplyCatalog(catalogConfig.id)).thenReturn(new TestCatalogHandler(testCatalogOptionsConfig));
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
        JsonReadConverterHandler converter = new JsonReadConverterHandler(config, context);

        DirectBuffer data = new UnsafeBuffer();

        String payload =
                "{" +
                    "\"id\": 123," +
                    "\"status\": \"OK\"" +
                "}";
        byte[] bytes = payload.getBytes();
        data.wrap(bytes, 0, bytes.length);

        MutableDirectBuffer value = new UnsafeBuffer(new byte[data.capacity() + 5]);
        value.putBytes(0, new byte[]{0x00, 0x00, 0x00, 0x00, 0x01});
        value.putBytes(5, bytes);

        assertEquals(-1, converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldWriteValidJsonData()
    {
        TestCatalogOptionsConfig testCatalogOptionsConfig = TestCatalogOptionsConfig.builder()
            .id(9)
            .schema(OBJECT_SCHEMA)
            .build();
        CatalogConfig catalogConfig = new CatalogConfig("test", "test0", "test", testCatalogOptionsConfig);
        when(context.supplyCatalog(catalogConfig.id)).thenReturn(new TestCatalogHandler(testCatalogOptionsConfig));
        JsonWriteConverterHandler converter = new JsonWriteConverterHandler(config, context);

        DirectBuffer data = new UnsafeBuffer();

        String payload =
                "{" +
                    "\"id\": \"123\"," +
                    "\"status\": \"OK\"" +
                "}";
        byte[] bytes = payload.getBytes();
        data.wrap(bytes, 0, bytes.length);

        assertEquals(data.capacity(), converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyInvalidJsonArray()
    {
        TestCatalogOptionsConfig testCatalogOptionsConfig = TestCatalogOptionsConfig.builder()
            .id(9)
            .schema(ARRAY_SCHEMA)
            .build();
        CatalogConfig catalogConfig = new CatalogConfig("test", "test0", "test", testCatalogOptionsConfig);
        when(context.supplyCatalog(catalogConfig.id)).thenReturn(new TestCatalogHandler(testCatalogOptionsConfig));
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
        JsonWriteConverterHandler converter = new JsonWriteConverterHandler(config, context);

        DirectBuffer data = new UnsafeBuffer();

        String payload =
            "[" +
                "{" +
                    "\"id\": 123," +
                    "\"status\": \"OK\"" +
                "}" +
            "]";
        byte[] bytes = payload.getBytes();
        data.wrap(bytes, 0, bytes.length);

        assertEquals(-1, converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }
}
