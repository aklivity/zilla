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
package io.aklivity.zilla.runtime.model.avro.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;
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

    private static final String SCHEMA_WITH_NULL = "{\"fields\":[{\"name\":\"id\",\"type\":\"string\"}," +
        "{\"name\":\"status\",\"type\":[\"null\",\"string\"]}]," +
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
    private AvroModelConfiguration config;

    @Before
    public void init()
    {
        System.setProperty("zilla.model.avro.padding.max.items", "111");
        config = new AvroModelConfiguration(new Configuration());
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
        AvroReadConverterHandler converter = new AvroReadConverterHandler(config, model, context);

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
        AvroWriteConverterHandler converter = new AvroWriteConverterHandler(config, model, context);

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
        AvroReadConverterHandler converter = new AvroReadConverterHandler(config, model, context);

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
                .schema(SCHEMA_WITH_NULL)
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
        AvroReadConverterHandler converter = new AvroReadConverterHandler(config, model, context);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x06, 0x69, 0x64,
            0x30, 0x02, 0x10, 0x70, 0x6f, 0x73, 0x69, 0x74, 0x69, 0x76, 0x65};
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
        AvroWriteConverterHandler converter = new AvroWriteConverterHandler(config, model, context);

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
        AvroWriteConverterHandler converter = new AvroWriteConverterHandler(config, model, context);

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
        AvroReadConverterHandler converter = new AvroReadConverterHandler(config, model, context);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x06, 0x69, 0x64,
            0x30, 0x10, 0x70, 0x6f, 0x73, 0x69, 0x74, 0x69, 0x76, 0x65};
        data.wrap(bytes, 0, bytes.length);

        assertEquals(525, converter.padding(data, 0, data.capacity()));
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
        AvroReadConverterHandler converter = new AvroReadConverterHandler(config, model, context);

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

    @Test
    public void shouldRoundTripComplexJson()
    {
        String schema = "{\"type\":\"record\",\"name\":\"Event\",\"namespace\":\"io.aklivity.example\"," +
            "\"fields\":[" +
            "{\"name\":\"id\",\"type\":\"long\"}," +
            "{\"name\":\"name\",\"type\":\"string\"}," +
            "{\"name\":\"active\",\"type\":\"boolean\"}," +
            "{\"name\":\"score\",\"type\":\"double\"}," +
            "{\"name\":\"tags\",\"type\":{\"type\":\"array\",\"items\":\"string\"}}," +
            "{\"name\":\"props\",\"type\":{\"type\":\"map\",\"values\":\"string\"}}," +
            "{\"name\":\"status\",\"type\":[\"null\",\"string\"]}," +
            "{\"name\":\"meta\",\"type\":{\"type\":\"record\",\"name\":\"Meta\"," +
            "\"fields\":[{\"name\":\"m\",\"type\":\"int\"}]}}]}";

        TestCatalogConfig catalog = CatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(9)
                .schema(schema)
                .build()
            .build();
        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));

        AvroModelConfig writeModel = AvroModelConfig.builder()
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
        AvroModelConfig readModel = AvroModelConfig.builder()
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

        AvroWriteConverterHandler writer = new AvroWriteConverterHandler(config, writeModel, context);
        AvroReadConverterHandler reader = new AvroReadConverterHandler(config, readModel, context);

        // a large value forces the bounded output to suspend mid-datum, so the streamed output must equal
        // the whole-buffer output
        String name = "x".repeat(1000);
        String json = "{\"id\":7,\"name\":\"" + name + "\",\"active\":true,\"score\":0.5," +
            "\"tags\":[\"a\",\"b\"],\"props\":{\"k\":\"v\"},\"status\":\"ok\",\"meta\":{\"m\":3}}";

        byte[] avro = convertToBytes(writer, json.getBytes());
        assertNotNull(avro);

        byte[] roundTrip = convertToBytes(reader, avro);
        assertNotNull(roundTrip);
        assertEquals(json, new String(roundTrip));
    }

    @Test
    public void shouldExtractBinaryTypes()
    {
        String schema = "{\"type\":\"record\",\"name\":\"Obj\",\"fields\":[" +
            "{\"name\":\"b\",\"type\":\"bytes\"}," +
            "{\"name\":\"e\",\"type\":{\"type\":\"enum\",\"name\":\"E\",\"symbols\":[\"A\",\"B\",\"C\"]}}," +
            "{\"name\":\"f\",\"type\":\"boolean\"}," +
            "{\"name\":\"x\",\"type\":{\"type\":\"fixed\",\"name\":\"F\",\"size\":2}}," +
            "{\"name\":\"s\",\"type\":\"string\"}]}";

        TestCatalogConfig catalog = CatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(9)
                .schema(schema)
                .build()
            .build();
        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));

        AvroModelConfig writeModel = AvroModelConfig.builder()
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
        AvroModelConfig readModel = AvroModelConfig.builder()
            .catalog()
                .name("test0")
                    .schema()
                        .strategy("topic")
                        .version("latest")
                        .subject("test-value")
                        .build()
                .build()
            .build();

        AvroWriteConverterHandler writer = new AvroWriteConverterHandler(config, writeModel, context);
        String json = "{\"b\":\"/wA=\",\"e\":\"C\",\"f\":true,\"x\":\"AQI=\",\"s\":\"hello\"}";
        byte[] avro = convertToBytes(writer, json.getBytes());
        assertNotNull(avro);

        AvroReadConverterHandler reader = new AvroReadConverterHandler(config, readModel, context);
        reader.extract("$.b");
        reader.extract("$.e");
        reader.extract("$.f");
        reader.extract("$.x");
        reader.extract("$.s");

        DirectBuffer data = new UnsafeBuffer(avro);
        assertEquals(avro.length, reader.convert(0L, 0L, data, 0, avro.length, ValueConsumer.NOP));

        reader.extracted("$.s", (buffer, index, length) ->
            assertEquals("hello", buffer.getStringWithoutLengthUtf8(index, length)));
        reader.extracted("$.e", (buffer, index, length) ->
            assertEquals("2", buffer.getStringWithoutLengthUtf8(index, length)));
        reader.extracted("$.f", (buffer, index, length) ->
            assertEquals("true", buffer.getStringWithoutLengthUtf8(index, length)));
        reader.extracted("$.b", (buffer, index, length) ->
        {
            assertEquals(2, length);
            assertEquals((byte) 0xff, buffer.getByte(index));
            assertEquals((byte) 0x00, buffer.getByte(index + 1));
        });
        reader.extracted("$.x", (buffer, index, length) ->
        {
            assertEquals(2, length);
            assertEquals((byte) 0x01, buffer.getByte(index));
            assertEquals((byte) 0x02, buffer.getByte(index + 1));
        });
    }

    @Test
    public void shouldRoundTripLargeArrayJson()
    {
        String schema = "{\"type\":\"array\",\"items\":{\"type\":\"record\",\"name\":\"R\"," +
            "\"fields\":[{\"name\":\"valueWithAQuiteLongFieldName\",\"type\":\"int\"}]}}";

        TestCatalogConfig catalog = CatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(9)
                .schema(schema)
                .build()
            .build();
        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));

        AvroModelConfig writeModel = AvroModelConfig.builder()
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
        AvroModelConfig readModel = AvroModelConfig.builder()
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

        // the repeated field name makes the JSON far larger than the Avro, exceeding the bounded output
        // window so the decode drains across several feeds and must reassemble identically
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < 60; i++)
        {
            builder.append(i > 0 ? "," : "").append("{\"valueWithAQuiteLongFieldName\":").append(i).append("}");
        }
        builder.append("]");
        String json = builder.toString();

        AvroWriteConverterHandler writer = new AvroWriteConverterHandler(config, writeModel, context);
        AvroReadConverterHandler reader = new AvroReadConverterHandler(config, readModel, context);

        byte[] avro = convertToBytes(writer, json.getBytes());
        assertNotNull(avro);

        byte[] roundTrip = convertToBytes(reader, avro);
        assertNotNull(roundTrip);
        assertEquals(json, new String(roundTrip));
    }

    @Test
    public void shouldReadInvalidAvroExpectJson()
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
        AvroReadConverterHandler converter = new AvroReadConverterHandler(config, model, context);

        DirectBuffer data = new UnsafeBuffer();
        byte[] bytes = {0x06, 0x69, 0x64, 0x30, 0x10};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(-1, converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    private static byte[] convertToBytes(
        ConverterHandler converter,
        byte[] input)
    {
        DirectBuffer data = new UnsafeBuffer(input);
        MutableDirectBuffer destination = new UnsafeBuffer(new byte[64 * 1024]);
        int progress = converter.convert(0L, 0L, data, 0, input.length,
            (buffer, index, length) -> destination.putBytes(0, buffer, index, length));
        byte[] result = null;
        if (progress > 0)
        {
            result = new byte[progress];
            destination.getBytes(0, result);
        }
        return result;
    }
}
