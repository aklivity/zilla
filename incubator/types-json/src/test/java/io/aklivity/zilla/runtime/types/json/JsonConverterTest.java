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
package io.aklivity.zilla.runtime.types.json;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DIRECTORY;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.util.Properties;
import java.util.function.LongFunction;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.Catalog;
import io.aklivity.zilla.runtime.engine.catalog.CatalogContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.CatalogConfig;
import io.aklivity.zilla.runtime.engine.converter.function.ValueConsumer;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.TestCatalog;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.config.TestCatalogOptionsConfig;
import io.aklivity.zilla.runtime.types.json.config.JsonConverterConfig;

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

    private final JsonConverterConfig config = JsonConverterConfig.builder()
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
    private CatalogContext context;

    @Before
    public void init()
    {
        Properties properties = new Properties();
        properties.setProperty(ENGINE_DIRECTORY.name(), "target/zilla-itests");
        Configuration config = new Configuration(properties);
        Catalog catalog = new TestCatalog(config);
        context = catalog.supply(mock(EngineContext.class));
    }

    @Test
    public void shouldVerifyValidJsonObject()
    {
        CatalogConfig catalogConfig = new CatalogConfig("test0", "test",
            TestCatalogOptionsConfig.builder()
                .id(9)
                .schema(OBJECT_SCHEMA)
                .build());
        LongFunction<CatalogHandler> handler = value -> context.attach(catalogConfig);
        JsonReadConverter converter = new JsonReadConverter(config, handler);

        DirectBuffer data = new UnsafeBuffer();

        String payload =
                "{" +
                    "\"id\": \"123\"," +
                    "\"status\": \"OK\"" +
                "}";
        byte[] bytes = payload.getBytes();
        data.wrap(bytes, 0, bytes.length);
        assertEquals(data.capacity(), converter.convert(data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyValidJsonArray()
    {
        CatalogConfig catalogConfig = new CatalogConfig("test0", "test",
            TestCatalogOptionsConfig.builder()
                .id(9)
                .schema(ARRAY_SCHEMA)
                .build());
        LongFunction<CatalogHandler> handler = value -> context.attach(catalogConfig);
        JsonWriteConverter converter = new JsonWriteConverter(config, handler);

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

        assertEquals(data.capacity(), converter.convert(data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyInvalidJsonObject()
    {
        CatalogConfig catalogConfig = new CatalogConfig("test0", "test",
            TestCatalogOptionsConfig.builder()
                .id(9)
                .schema(OBJECT_SCHEMA)
                .build());
        LongFunction<CatalogHandler> handler = value -> context.attach(catalogConfig);
        JsonReadConverter converter = new JsonReadConverter(config, handler);

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

        assertEquals(-1, converter.convert(data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldWriteValidJsonData()
    {
        CatalogConfig catalogConfig = new CatalogConfig("test0", "test",
            TestCatalogOptionsConfig.builder()
                .id(9)
                .schema(OBJECT_SCHEMA)
                .build());
        LongFunction<CatalogHandler> handler = value -> context.attach(catalogConfig);
        JsonWriteConverter converter = new JsonWriteConverter(config, handler);

        DirectBuffer data = new UnsafeBuffer();

        String payload =
                "{" +
                    "\"id\": \"123\"," +
                    "\"status\": \"OK\"" +
                "}";
        byte[] bytes = payload.getBytes();
        data.wrap(bytes, 0, bytes.length);

        assertEquals(data.capacity(), converter.convert(data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyInvalidJsonArray()
    {
        CatalogConfig catalogConfig = new CatalogConfig("test0", "test",
            TestCatalogOptionsConfig.builder()
                .id(9)
                .schema(ARRAY_SCHEMA)
                .build());
        LongFunction<CatalogHandler> handler = value -> context.attach(catalogConfig);
        JsonWriteConverter converter = new JsonWriteConverter(config, handler);

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
        assertEquals(-1, converter.convert(data, 0, data.capacity(), ValueConsumer.NOP));
    }
}
