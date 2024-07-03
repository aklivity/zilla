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
package io.aklivity.zilla.runtime.model.protobuf.internal;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
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
import io.aklivity.zilla.runtime.model.protobuf.config.ProtobufModelConfig;

public class ProtobufModelTest
{
    private static final String SCHEMA = "syntax = \"proto3\";" +
                                    "package io.aklivity.examples.clients.proto;" +
                                    "message SimpleMessage " +
                                    "{  " +
                                        "string content = 1;" +
                                        "optional string date_time = 2;" +
                                        "message DeviceMessage2 " +
                                        "{  " +
                                        "int32 id = 1;" +
                                        "message DeviceMessage6 " +
                                        "{  " +
                                        "int32 id = 1;" +
                                        "}" +
                                        "}" +
                                        "DeviceMessage2 device = 3;" +
                                    "}" +
                                    "message DemoMessage " +
                                    "{  " +
                                        "string status = 1;" +
                                        "message DeviceMessage " +
                                        "{  " +
                                            "int32 id = 1;" +
                                        "}" +
                                        "message DeviceMessage1 " +
                                        "{  " +
                                        "int32 id = 1;" +
                                        "}" +
                                        "optional string date_time = 2;" +
                                        "message SimpleMessage " +
                                        "{  " +
                                            "string content = 1;" +
                                            "optional string date_time = 2;" +
                                        "}" +
                                    "}";

    private static final String COMPLEX_SCHEMA = "syntax = \"proto3\"; " +
                                                "package io.confluent.examples.clients.basicavro; " +
                                                "message SimpleMessage " +
                                                "{ " +
                                                    "double field_double = 1; " +
                                                    "float field_float = 2; " +
                                                    "int64 field_int64 = 3; " +
                                                    "uint64 field_uint64 = 4; " +
                                                    "int32 field_int32 = 5; " +
                                                    "fixed64 field_fixed64 = 6; " +
                                                    "fixed32 field_fixed32 = 7; " +
                                                    "string field_string = 8; " +
                                                    "bytes field_bytes = 9; " +
                                                    "uint32 field_uint32 = 10; " +
                                                    "sfixed32 field_sfixed32 = 12; " +
                                                    "sfixed64 field_sfixed64 = 13; " +
                                                    "sint32 field_sint32 = 14; " +
                                                    "sint64 field_sint64 = 15; " +
                                                "}";

    private EngineContext context;

    @Before
    public void init()
    {
        context = mock(EngineContext.class);

        TestCatalogConfig catalog = CatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(1)
                .schema(SCHEMA)
                .build()
            .build();

        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));
    }

    @Test
    public void shouldWriteValidProtobufEvent()
    {
        ProtobufModelConfig model = ProtobufModelConfig.builder()
            .catalog()
                .name("test0")
                .schema()
                    .strategy("topic")
                    .version("latest")
                    .subject("test-value")
                    .record("SimpleMessage")
                    .build()
                .build()
            .build();
        ProtobufWriteConverterHandler converter = new ProtobufWriteConverterHandler(model, context);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x0a, 0x02, 0x4f, 0x4b, 0x12, 0x08, 0x30, 0x31, 0x30, 0x31, 0x32, 0x30, 0x32, 0x34};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(data.capacity() + 1, converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));

        assertEquals(data.capacity() + 1, converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldWriteValidProtobufEventNestedMessage()
    {
        ProtobufModelConfig model = ProtobufModelConfig.builder()
            .catalog()
                .name("test0")
                    .schema()
                    .strategy("topic")
                    .version("latest")
                    .subject("test-value")
                    .record("DemoMessage.SimpleMessage")
                    .build()
                .build()
            .build();
        ProtobufWriteConverterHandler converter = new ProtobufWriteConverterHandler(model, context);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x0a, 0x02, 0x4f, 0x4b, 0x12, 0x08, 0x30, 0x31, 0x30, 0x31, 0x32, 0x30, 0x32, 0x34};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(data.capacity() + 3, converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldWriteValidProtobufEventIncorrectRecordName()
    {
        ProtobufModelConfig model = ProtobufModelConfig.builder()
            .catalog()
                .name("test0")
                    .schema()
                    .strategy("topic")
                    .version("latest")
                    .subject("test-value")
                    .record("DemoMessage.IncorrectRecord")
                    .build()
                .build()
            .build();
        ProtobufWriteConverterHandler converter = new ProtobufWriteConverterHandler(model, context);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x0a, 0x02, 0x4f, 0x4b, 0x12, 0x08, 0x30, 0x31, 0x30, 0x31, 0x32, 0x30, 0x32, 0x34};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(-1, converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldReadValidProtobufEvent()
    {
        ProtobufModelConfig model = ProtobufModelConfig.builder()
            .catalog()
                .name("test0")
                .schema()
                    .strategy("topic")
                    .version("latest")
                    .subject("test-value")
                    .build()
                .build()
            .build();
        ProtobufReadConverterHandler converter = new ProtobufReadConverterHandler(model, context);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x00, 0x0a, 0x02, 0x4f, 0x4b, 0x12, 0x08, 0x30, 0x31, 0x30, 0x31, 0x32, 0x30, 0x32, 0x34};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(data.capacity() - 1, converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));

        assertEquals(data.capacity() - 1, converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldReadValidProtobufEventNestedMessage()
    {
        ProtobufModelConfig model = ProtobufModelConfig.builder()
            .catalog()
                .name("test0")
                    .schema()
                    .strategy("topic")
                    .version("latest")
                    .subject("test-value")
                    .build()
                .build()
            .build();
        ProtobufReadConverterHandler converter = new ProtobufReadConverterHandler(model, context);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x04, 0x02, 0x04, 0x0a, 0x02, 0x4f, 0x4b, 0x12, 0x08, 0x30, 0x31, 0x30, 0x31, 0x32, 0x30, 0x32, 0x34};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(data.capacity() - 3, converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldReadValidProtobufEventFormatJson()
    {
        ProtobufModelConfig model = ProtobufModelConfig.builder()
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

        ProtobufReadConverterHandler converter = new ProtobufReadConverterHandler(model, context);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x00, 0x0a, 0x02, 0x4f, 0x4b, 0x12, 0x08, 0x30, 0x31, 0x30, 0x31, 0x32, 0x30, 0x32, 0x34};
        data.wrap(bytes, 0, bytes.length);

        String json =
                "{" +
                    "\"content\":\"OK\"," +
                    "\"date_time\":\"01012024\"" +
                "}";

        final ValueConsumer consumer = (buffer, index, length) ->
        {
            byte[] jsonBytes = new byte[length];
            buffer.getBytes(index, jsonBytes);
            assertEquals(json, new String(jsonBytes, StandardCharsets.UTF_8));
        };
        converter.convert(0L, 0L, data, 0, data.capacity(), consumer);

        converter.convert(0L, 0L, data, 0, data.capacity(), consumer);
    }

    @Test
    public void shouldWriteValidProtobufEventFormatJson()
    {
        ProtobufModelConfig model = ProtobufModelConfig.builder()
            .view("json")
            .catalog()
                .name("test0")
                .schema()
                    .strategy("topic")
                    .version("latest")
                    .subject("test-value")
                    .record("SimpleMessage")
                    .build()
                .build()
            .build();

        ProtobufWriteConverterHandler converter = new ProtobufWriteConverterHandler(model, context);

        DirectBuffer data = new UnsafeBuffer();

        String json =
                "{" +
                    "\"content\":\"OK\"," +
                    "\"date_time\":\"01012024\"" +
                "}";
        data.wrap(json.getBytes(), 0, json.getBytes().length);

        byte[] expectedBytes = {0x00, 0x0a, 0x02, 0x4f, 0x4b, 0x12, 0x08, 0x30, 0x31, 0x30, 0x31, 0x32, 0x30, 0x32, 0x34};
        DirectBuffer expected = new UnsafeBuffer();
        expected.wrap(expectedBytes, 0, expectedBytes.length);

        assertEquals(expected.capacity(), converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));

        assertEquals(expected.capacity(), converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldWriteInvalidProtobufEventFormatJson()
    {
        ProtobufModelConfig model = ProtobufModelConfig.builder()
            .view("json")
            .catalog()
                .name("test0")
                .schema()
                    .strategy("topic")
                    .version("latest")
                    .subject("test-value")
                    .record("SimpleMessage")
                    .build()
                .build()
            .build();

        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));

        ProtobufWriteConverterHandler converter = new ProtobufWriteConverterHandler(model, context);

        DirectBuffer data = new UnsafeBuffer();

        String json =
            "{" +
                "\"content\":\"OK\"," +
                "\"date\":\"01012024\"" +
            "}";
        data.wrap(json.getBytes(), 0, json.getBytes().length);

        assertEquals(-1, converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));

        assertEquals(-1, converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyJsonFormatPaddingLength()
    {
        ProtobufModelConfig model = ProtobufModelConfig.builder()
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
        ProtobufReadConverterHandler converter = new ProtobufReadConverterHandler(model, context);

        DirectBuffer data = new UnsafeBuffer();

        assertEquals(71, converter.padding(data, 0, data.capacity()));
    }

    @Test
    public void shouldVerifyIndexPaddingLength()
    {
        ProtobufModelConfig model = ProtobufModelConfig.builder()
            .catalog()
                .name("test0")
                    .schema()
                    .strategy("topic")
                    .version("latest")
                    .subject("test-value")
                    .record("DemoMessage.SimpleMessage")
                    .build()
                .build()
            .build();
        ProtobufWriteConverterHandler converter = new ProtobufWriteConverterHandler(model, context);

        DirectBuffer data = new UnsafeBuffer();

        assertEquals(3, converter.padding(data, 0, data.capacity()));

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
                .schema(COMPLEX_SCHEMA)
                .build()
            .build();
        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));

        ProtobufModelConfig model = ProtobufModelConfig.builder()
            .catalog()
                .name("test0")
                .schema()
                    .strategy("topic")
                    .version("latest")
                    .subject("test-value")
                    .build()
                .build()
            .build();
        ProtobufReadConverterHandler converter = new ProtobufReadConverterHandler(model, context);

        String stringPath = "$.field_string";
        converter.extract(stringPath);

        String floatPath = "$.field_float";
        converter.extract(floatPath);

        String int64Path = "$.field_int64";
        converter.extract(int64Path);

        String int32Path = "$.field_int32";
        converter.extract(int32Path);

        String doublePath = "$.field_double";
        converter.extract(doublePath);

        String sfixed32Path = "$.field_sfixed32";
        converter.extract(sfixed32Path);

        String sint64Path = "$.field_sint64";
        converter.extract(sint64Path);

        String fixed64Path = "$.field_fixed64";
        converter.extract(fixed64Path);

        String sint32Path = "$.field_sint32";
        converter.extract(sint32Path);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0, 9, 119, -66, -97, 26, 47, -35, 94, 64, 21, 102, -26, -11, 66, 24, -107, -102, -17, 58,
            32, -79, -47, -7, -42, 3, 40, -71, 96, 49, 21, -51, 91, 7, 0, 0, 0, 0, 61, 57, 48, 0, 0, 66, 12, 100,
            117, 109, 109, 121, 32, 115, 116, 114, 105, 110, 103, 74, 5, 1, 2, 3, 4, 5, 80, -78, -110, 4, 101, 57,
            48, 0, 0, 105, 21, -51, 91, 7, 0, 0, 0, 0, 112, -28, -92, 8, 120, -30, -94, -13, -83, 7};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(data.capacity() - 1, converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));

        ConverterHandler.FieldVisitor visitor;

        assertEquals(12, converter.extractedLength(stringPath));
        visitor = (buffer, index, length) ->
        {
            assertEquals("dummy string", buffer.getStringWithoutLengthUtf8(index, length));
        };
        converter.extracted(stringPath, visitor);

        assertEquals(7, converter.extractedLength(doublePath));

        visitor = (buffer, index, length) ->
        {
            assertEquals("123.456", buffer.getStringWithoutLengthUtf8(index, length));
        };
        converter.extracted(doublePath, visitor);

        visitor = (buffer, index, length) ->
        {
            assertEquals("12345", buffer.getStringWithoutLengthUtf8(index, length));
        };
        converter.extracted(int32Path, visitor);

        visitor = (buffer, index, length) ->
        {
            assertEquals("122.95", buffer.getStringWithoutLengthUtf8(index, length));
        };
        converter.extracted(floatPath, visitor);

        visitor = (buffer, index, length) ->
        {
            assertEquals("123456789", buffer.getStringWithoutLengthUtf8(index, length));
        };
        converter.extracted(int64Path, visitor);

    }
}
