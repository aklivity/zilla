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
package io.aklivity.zilla.runtime.model.json.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
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
import io.aklivity.zilla.runtime.engine.model.ConverterHandler;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.TestCatalogHandler;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.config.TestCatalogConfig;
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
                        "\"zillaId\": {" +
                        "\"type\": \"integer\"" +
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

    private EngineContext context;

    private final MutableDirectBuffer out = new UnsafeBuffer(new byte[1024]);
    private int outLength;
    private final ValueConsumer capture = (buffer, index, length) ->
    {
        buffer.getBytes(index, out, 0, length);
        outLength = length;
    };

    private String captured()
    {
        byte[] bytes = new byte[outLength];
        out.getBytes(0, bytes);
        return new String(bytes, UTF_8);
    }

    @Before
    public void init()
    {
        context = mock(EngineContext.class);
    }

    @Test
    public void shouldVerifyValidJsonObject()
    {
        TestCatalogConfig catalog = CatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(9)
                .schema(OBJECT_SCHEMA)
                .build()
            .build();

        JsonModelConfig model = JsonModelConfig.builder()
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

        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));
        JsonReadConverterHandler converter = new JsonReadConverterHandler(model, context);

        DirectBuffer data = new UnsafeBuffer();
        String payload =
                "{" +
                    "\"id\": \"123\"," +
                    "\"status\": \"OK\"" +
                "}";
        byte[] bytes = payload.getBytes();
        data.wrap(bytes, 0, bytes.length);

        String expected = "{\"id\":\"123\",\"status\":\"OK\"}";
        assertEquals(expected.length(), converter.convert(0L, 0L, data, 0, data.capacity(), capture));
        assertEquals(expected, captured());
    }

    @Test
    public void shouldVerifyValidJsonObjectAtOffset()
    {
        TestCatalogConfig catalog = CatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(9)
                .schema(OBJECT_SCHEMA)
                .build()
            .build();

        JsonModelConfig model = JsonModelConfig.builder()
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

        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));
        JsonReadConverterHandler converter = new JsonReadConverterHandler(model, context);

        String payload =
                "{" +
                    "\"id\": \"123\"," +
                    "\"status\": \"OK\"" +
                "}";
        byte[] bytes = payload.getBytes();
        int offset = 5;
        MutableDirectBuffer framed = new UnsafeBuffer(new byte[offset + bytes.length]);
        framed.putBytes(offset, bytes);

        String expected = "{\"id\":\"123\",\"status\":\"OK\"}";
        assertEquals(expected.length(), converter.convert(0L, 0L, framed, offset, bytes.length, capture));
        assertEquals(expected, captured());
    }

    @Test
    public void shouldVerifyValidJsonArray()
    {
        TestCatalogConfig catalog = CatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(9)
                .schema(ARRAY_SCHEMA)
                .build()
            .build();

        JsonModelConfig model = JsonModelConfig.builder()
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

        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));
        JsonWriteConverterHandler converter = new JsonWriteConverterHandler(model, context);

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

        String expected = "[{\"id\":\"123\",\"status\":\"OK\"}]";
        assertEquals(expected.length(), converter.convert(0L, 0L, data, 0, data.capacity(), capture));
        assertEquals(expected, captured());
    }

    @Test
    public void shouldVerifyInvalidJsonObject()
    {
        TestCatalogConfig catalog = CatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(9)
                .schema(OBJECT_SCHEMA)
                .build()
            .build();

        JsonModelConfig model = JsonModelConfig.builder()
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

        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
        JsonReadConverterHandler converter = new JsonReadConverterHandler(model, context);

        DirectBuffer data = new UnsafeBuffer();

        String payload =
                "{" +
                    "\"id\": 123," +
                    "\"status\": \"OK\"" +
                "}";
        byte[] bytes = payload.getBytes();
        data.wrap(bytes, 0, bytes.length);

        assertEquals(-1, converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldWriteValidJsonData()
    {
        TestCatalogConfig catalog = CatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(9)
                .schema(OBJECT_SCHEMA)
                .build()
            .build();

        JsonModelConfig model = JsonModelConfig.builder()
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

        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));
        JsonWriteConverterHandler converter = new JsonWriteConverterHandler(model, context);

        DirectBuffer data = new UnsafeBuffer();

        String payload =
                "{" +
                    "\"id\": \"123\"," +
                    "\"status\": \"OK\"" +
                "}";
        byte[] bytes = payload.getBytes();
        data.wrap(bytes, 0, bytes.length);

        String expected = "{\"id\":\"123\",\"status\":\"OK\"}";
        assertEquals(expected.length(), converter.convert(0L, 0L, data, 0, data.capacity(), capture));
        assertEquals(expected, captured());
    }

    @Test
    public void shouldVerifyInvalidJsonArray()
    {
        TestCatalogConfig catalog = CatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(9)
                .schema(ARRAY_SCHEMA)
                .build()
            .build();

        JsonModelConfig model = JsonModelConfig.builder()
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

        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
        JsonWriteConverterHandler converter = new JsonWriteConverterHandler(model, context);

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

    @Test
    public void shouldExtract()
    {
        TestCatalogConfig catalog = CatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(9)
                .schema(OBJECT_SCHEMA)
                .build()
            .build();

        JsonModelConfig model = JsonModelConfig.builder()
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

        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));
        when(context.clock()).thenReturn(Clock.systemUTC());
        JsonReadConverterHandler converter = new JsonReadConverterHandler(model, context);

        String statusPath = "$.status";
        converter.extract(statusPath);

        String zillaIdPath = "$.zillaId";
        converter.extract(zillaIdPath);

        DirectBuffer data = new UnsafeBuffer();

        String payload =
            "{" +
                "\"id\": \"123\"," +
                "\"zillaId\": 321," +
                "\"status\": \"OK\"" +
                "}";
        byte[] bytes = payload.getBytes();
        data.wrap(bytes, 0, bytes.length);

        String expected = "{\"id\":\"123\",\"zillaId\":321,\"status\":\"OK\"}";
        assertEquals(expected.length(), converter.convert(0L, 0L, data, 0, data.capacity(), capture));
        assertEquals(expected, captured());

        assertEquals(2, converter.extractedLength(statusPath));
        final ConverterHandler.FieldVisitor visitor = (buffer, index, length) ->
        {
            assertEquals("OK", buffer.getStringWithoutLengthUtf8(index, length));
        };
        converter.extracted(statusPath, visitor);

        assertEquals(3, converter.extractedLength(zillaIdPath));
        final ConverterHandler.FieldVisitor zillaIdVisitor = (buffer, index, length) ->
        {
            assertEquals("321", buffer.getStringWithoutLengthUtf8(index, length));
        };
        converter.extracted(zillaIdPath, zillaIdVisitor);
    }
}
