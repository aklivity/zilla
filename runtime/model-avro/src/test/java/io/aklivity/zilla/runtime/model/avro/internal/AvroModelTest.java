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
package io.aklivity.zilla.runtime.model.avro.internal;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;

import org.agrona.DirectBuffer;
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
import io.aklivity.zilla.runtime.model.avro.config.AvroModelConfig;

public class AvroModelTest
{
    private static final String SCHEMA = "{\"fields\":[{\"name\":\"id\",\"type\":\"string\"}," +
            "{\"name\":\"status\",\"type\":\"string\"}]," +
            "\"name\":\"Event\",\"namespace\":\"io.aklivity.example\",\"type\":\"record\"}";

    private static final String SCHEMA_OBJECT = "{\"type\":\"record\",\"name\":\"ExampleRecord\"," +
        "\"namespace\":\"com.example\"," +
        "\"fields\":[" +
        "{\"name\":\"bytesField\",\"type\":\"bytes\"}," +
        "{\"name\":\"stringField\",\"type\":\"string\"}," +
        "{\"name\":\"intField\",\"type\":\"int\"}," +
        "{\"name\":\"floatField\",\"type\":\"float\"}," +
        "{\"name\":\"longField\",\"type\":\"long\"}," +
        "{\"name\":\"doubleField\",\"type\":\"double\"}]}";

    private static final String COMPLEX_SCHEMA = "{\"type\":\"record\",\"name\":\"example\",\"namespace\":\"com.example\"," +
        "\"fields\":[{\"name\":\"name\",\"type\":\"string\"},{\"name\":\"preferences\",\"" +
        "type\":{\"type\":\"map\",\"values\":\"string\"}},{\"name\":\"attributes\",\"" +
        "type\":{\"type\":\"map\",\"values\":{\"type\":\"record\",\"name\":\"Attribute\"," +
        "\"fields\":[{\"name\":\"value\",\"type\":\"string\"},{\"name\":\"timestamp\",\"type\":\"long\"}]}}}," +
        "{\"name\":\"addresses\",\"type\":{\"type\":\"array\",\"items\":{\"type\":\"record\",\"name\":\"Address\"," +
        "\"fields\":[{\"name\":\"street\",\"type\":\"string\"},{\"name\":\"city\",\"type\":\"string\"}," +
        "{\"name\":\"state\",\"type\":\"string\"},{\"name\":\"zip\",\"type\":\"string\"}]}}}," +
        "{\"name\":\"source\",\"type\":[\"null\",\"string\"],\"default\":null}]}";

    private EngineContext context;

    @Before
    public void init()
    {
        context = mock(EngineContext.class);
    }

    @Test
    public void shouldVerifyValidAvroEvent()
    {
        TestCatalogConfig catalog = CatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(9)
                .schema(SCHEMA)
                .build()
            .build();
        AvroModelConfig model = AvroModelConfig.builder()
            .catalog()
                .name("test0")
                    .schema()
                        .strategy("topic")
                        .version("latest")
                        .subject("test-value")
                        .build()
                .build()
            .build();

        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));
        AvroReadConverterHandler converter = new AvroReadConverterHandler(model, context);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x06, 0x69, 0x64,
            0x30, 0x10, 0x70, 0x6f, 0x73, 0x69, 0x74, 0x69, 0x76, 0x65};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(data.capacity(), converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldWriteValidAvroEvent()
    {
        TestCatalogConfig catalog = CatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(9)
                .schema(SCHEMA)
                .build()
            .build();
        AvroModelConfig model = AvroModelConfig.builder()
            .catalog()
                .name("test0")
                    .schema()
                        .strategy("topic")
                        .version("latest")
                        .subject("test-value")
                        .build()
                .build()
            .build();

        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));
        AvroWriteConverterHandler converter = new AvroWriteConverterHandler(model, context);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x06, 0x69, 0x64, 0x30, 0x10, 0x70, 0x6f,
            0x73, 0x69, 0x74, 0x69, 0x76, 0x65};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(data.capacity(), converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyInvalidAvroEvent()
    {
        TestCatalogConfig catalog = CatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(9)
                .schema(SCHEMA)
                .build()
            .build();
        AvroModelConfig model = AvroModelConfig.builder()
            .catalog()
                .name("test0")
                    .schema()
                        .strategy("topic")
                        .version("latest")
                        .subject("test-value")
                        .build()
                .build()
            .build();

        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
        AvroReadConverterHandler converter = new AvroReadConverterHandler(model, context);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x06, 0x69, 0x64, 0x30, 0x10};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(-1, converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldReadAvroEventExpectJson()
    {
        TestCatalogConfig catalog = CatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(9)
                .schema(SCHEMA)
                .build()
            .build();
        AvroModelConfig model = AvroModelConfig.builder()
            .view("json")
            .catalog()
                .name("test0")
                    .schema()
                    .strategy("topic")
                    .version("latest")
                    .subject("test-value")
                    .build()
                .build()
            .build();

        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));
        AvroReadConverterHandler converter = new AvroReadConverterHandler(model, context);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x06, 0x69, 0x64,
            0x30, 0x10, 0x70, 0x6f, 0x73, 0x69, 0x74, 0x69, 0x76, 0x65};
        data.wrap(bytes, 0, bytes.length);

        String json =
                "{" +
                    "\"id\":\"id0\"," +
                    "\"status\":\"positive\"" +
                "}";

        DirectBuffer expected = new UnsafeBuffer();
        expected.wrap(json.getBytes(), 0, json.getBytes().length);

        int progress = converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP);
        assertEquals(expected.capacity(), progress);

        assertEquals(expected.capacity(), converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldWriteJsonEventExpectAvro()
    {
        TestCatalogConfig catalog = CatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(9)
                .schema(SCHEMA)
                .build()
            .build();
        AvroModelConfig model = AvroModelConfig.builder()
            .view("json")
            .catalog()
                .name("test0")
                    .schema()
                    .strategy("topic")
                    .version("latest")
                    .subject("test-value")
                    .build()
                .build()
            .build();

        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));
        AvroWriteConverterHandler converter = new AvroWriteConverterHandler(model, context);

        DirectBuffer expected = new UnsafeBuffer();

        byte[] bytes = {0x06, 0x69, 0x64,
            0x30, 0x10, 0x70, 0x6f, 0x73, 0x69, 0x74, 0x69, 0x76, 0x65};
        expected.wrap(bytes, 0, bytes.length);

        String payload =
                "{" +
                    "\"id\":\"id0\"," +
                    "\"status\":\"positive\"" +
                "}";

        DirectBuffer data = new UnsafeBuffer();
        data.wrap(payload.getBytes(), 0, payload.getBytes().length);
        int progress = converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP);
        assertEquals(expected.capacity(), progress);

        assertEquals(expected.capacity(), converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldWriteInvalidJsonEvent()
    {
        TestCatalogConfig catalog = CatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(9)
                .schema(SCHEMA)
                .build()
            .build();
        AvroModelConfig model = AvroModelConfig.builder()
            .view("json")
            .catalog()
                .name("test0")
                    .schema()
                    .strategy("topic")
                    .version("latest")
                    .subject("test-value")
                    .build()
                .build()
            .build();

        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
        AvroWriteConverterHandler converter = new AvroWriteConverterHandler(model, context);

        String payload =
            "{" +
                "\"id\":123," +
                "\"status\":\"positive\"" +
            "}";

        DirectBuffer data = new UnsafeBuffer();
        data.wrap(payload.getBytes(), 0, payload.getBytes().length);
        int progress = converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP);
        assertEquals(-1, progress);
    }

    @Test
    public void shouldVerifyPaddingLength()
    {
        TestCatalogConfig catalog = CatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(9)
                .schema(COMPLEX_SCHEMA)
                .build()
            .build();
        AvroModelConfig model = AvroModelConfig.builder()
            .view("json")
            .catalog()
                .name("test0")
                .schema()
                    .strategy("topic")
                    .version("latest")
                    .subject("test-value")
                    .build()
                .build()
            .build();

        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));
        AvroReadConverterHandler converter = new AvroReadConverterHandler(model, context);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x06, 0x69, 0x64,
            0x30, 0x10, 0x70, 0x6f, 0x73, 0x69, 0x74, 0x69, 0x76, 0x65};
        data.wrap(bytes, 0, bytes.length);

        assertEquals(292, converter.padding(data, 0, data.capacity()));
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
                .schema(SCHEMA_OBJECT)
                .build()
            .build();
        AvroModelConfig model = AvroModelConfig.builder()
            .catalog()
                .name("test0")
                    .schema()
                        .strategy("topic")
                        .version("latest")
                        .subject("test-value")
                        .build()
                .build()
            .build();

        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));
        AvroReadConverterHandler converter = new AvroReadConverterHandler(model, context);

        String stringPath = "$.stringField";
        converter.extract(stringPath);

        String intPath = "$.intField";
        converter.extract(intPath);

        String floatPath = "$.floatField";
        converter.extract(floatPath);

        String longPath = "$.longField";
        converter.extract(longPath);

        String doublePath = "$.doubleField";
        converter.extract(doublePath);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0, 16, 112, 111, 115, 105, 116, 105, 118, 101, 2, -51, -52, 12, 64, 2, 51, 51, 51, 51, 51, 51, -13, 63};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(data.capacity(), converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));

        assertEquals(8, converter.extractedLength(stringPath));
        ConverterHandler.FieldVisitor visitor = (buffer, index, length) ->
        {
            assertEquals("positive", buffer.getStringWithoutLengthUtf8(index, length));
        };
        converter.extracted(stringPath, visitor);

        ConverterHandler.FieldVisitor doubleVisitor = (buffer, index, length) ->
        {
            assertEquals("1.2", buffer.getStringWithoutLengthUtf8(index, length));
        };
        converter.extracted(doublePath, doubleVisitor);

        ConverterHandler.FieldVisitor intVisitor = (buffer, index, length) ->
        {
            assertEquals("1", buffer.getStringWithoutLengthUtf8(index, length));
        };
        converter.extracted(intPath, intVisitor);

        ConverterHandler.FieldVisitor floatVisitor = (buffer, index, length) ->
        {
            assertEquals("2.2", buffer.getStringWithoutLengthUtf8(index, length));
        };
        converter.extracted(floatPath, floatVisitor);
    }
}
