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
package io.aklivity.zilla.runtime.model.protobuf.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
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
    private static final String SCHEMA = """
                                            syntax = "proto3";
                                            package io.aklivity.examples.clients.proto;
                                            message SimpleMessage {
                                                string content = 1;
                                                optional string date_time = 2;
                                                message DeviceMessage2 {
                                                    int32 id = 1;
                                                    message DeviceMessage6 {
                                                        int32 id = 1;
                                                    }
                                                }
                                                DeviceMessage2 device = 3;
                                            }
                                            message DemoMessage {
                                                string status = 1;
                                                message DeviceMessage {
                                                    int32 id = 1;
                                                }
                                                message DeviceMessage1 {
                                                    int32 id = 1;
                                                }
                                                optional string date_time = 2;
                                                message SimpleMessage {
                                                    string content = 1;
                                                    optional string date_time = 2;
                                                }
                                            }
                                            """;

    private static final String COMPLEX_SCHEMA = """
                                                    syntax = "proto3";
                                                    package io.confluent.examples.clients.basicavro;
                                                    message SimpleMessage {
                                                        double field_double = 1;
                                                        float field_float = 2;
                                                        int64 field_int64 = 3;
                                                        uint64 field_uint64 = 4;
                                                        int32 field_int32 = 5;
                                                        fixed64 field_fixed64 = 6;
                                                        fixed32 field_fixed32 = 7;
                                                        string field_string = 8;
                                                        bytes field_bytes = 9;
                                                        uint32 field_uint32 = 10;
                                                        sfixed32 field_sfixed32 = 12;
                                                        sfixed64 field_sfixed64 = 13;
                                                        sint32 field_sint32 = 14;
                                                        sint64 field_sint64 = 15;
                                                    }
                                                    """;

    private static final String PROTO2_SCHEMA = """
                                                    syntax = "proto2";
                                                    package io.aklivity.examples.proto2;
                                                    import "google/protobuf/descriptor.proto";
                                                       extend google.protobuf.MessageOptions {
                                                           optional string enter_option_statement = 50001;
                                                       }
                                                    message Proto2Message {
                                                        required string required_field = 1;
                                                        optional string optional_field = 2;
                                                        optional int32 optional_with_default = 3 [default = 42];
                                                        repeated string repeated_field = 4;
                                                        optional bool bool_field = 5 [default = true];
                                                        enum Status {
                                                            UNKNOWN = 0;
                                                            ACTIVE = 1;
                                                            INACTIVE = 2;
                                                        }
                                                        optional Status status = 6 [default = ACTIVE];
                                                        message NestedMessage {
                                                            required int32 nested_id = 1;
                                                            optional string nested_name = 2;
                                                        }
                                                        optional NestedMessage nested = 7;
                                                        repeated NestedMessage nested_list = 8;
                                                        oneof choice {
                                                            string choice_string = 9;
                                                            int32 choice_int = 10;
                                                        }
                                                        map<string, int32> string_int_map = 11;
                                                        extensions 100 to 199;
                                                    }
                                                    extend Proto2Message {
                                                        optional string extension_field = 100;
                                                    }
                                                    message ComplexProto2Message {
                                                        required Proto2Message main_message = 1;
                                                        optional bytes binary_data = 2;
                                                        repeated double double_values = 3 [packed = true];
                                                        optional group GroupField = 4 {
                                                            optional int32 group_id = 5;
                                                            optional string group_name = 6;
                                                        }
                                                    }
                                                    """;

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

    @Test
    public void shouldWriteValidProto2MessageWithRequiredFields()
    {
        TestCatalogConfig catalog = CatalogConfig.builder(TestCatalogConfig::new)
                .namespace("test")
                .name("proto2test")
                .type("test")
                .options(TestCatalogOptionsConfig::builder)
                    .id(10)
                    .schema(PROTO2_SCHEMA)
                    .build()
                .build();
        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));

        ProtobufModelConfig model = ProtobufModelConfig.builder()
                .catalog()
                    .name("proto2test")
                    .schema()
                        .strategy("topic")
                        .version("latest")
                        .subject("proto2-value")
                        .record("Proto2Message")
                        .build()
                    .build()
                .build();
        ProtobufWriteConverterHandler converter = new ProtobufWriteConverterHandler(model, context);

        DirectBuffer data = new UnsafeBuffer();
        // Message with required field and optional fields
        byte[] bytes = {
            0x0a, 0x08, 0x72, 0x65, 0x71, 0x75, 0x69, 0x72, 0x65, 0x64,  // required_field: "required"
            0x12, 0x08, 0x6f, 0x70, 0x74, 0x69, 0x6f, 0x6e, 0x61, 0x6c,  // optional_field: "optional"
            0x18, 0x64  // optional_with_default: 100
        };
        data.wrap(bytes, 0, bytes.length);
        assertEquals(data.capacity() + 1, converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldReadValidProto2MessageWithDefaults()
    {
        TestCatalogConfig catalog = CatalogConfig.builder(TestCatalogConfig::new)
                .namespace("test")
                .name("proto2test")
                .type("test")
                .options(TestCatalogOptionsConfig::builder)
                    .id(10)
                    .schema(PROTO2_SCHEMA)
                    .build()
                .build();
        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));

        ProtobufModelConfig model = ProtobufModelConfig.builder()
                .catalog()
                    .name("proto2test")
                    .schema()
                        .strategy("topic")
                        .version("latest")
                        .subject("proto2-value")
                        .build()
                    .build()
                .build();
        ProtobufReadConverterHandler converter = new ProtobufReadConverterHandler(model, context);

        DirectBuffer data = new UnsafeBuffer();
        // Message with index prefix and only required field (defaults should apply)
        byte[] bytes = {
            0x00,  // message index
            0x0a, 0x08, 0x72, 0x65, 0x71, 0x75, 0x69, 0x72, 0x65, 0x64  // required_field: "required"
        };
        data.wrap(bytes, 0, bytes.length);
        assertEquals(data.capacity() - 1, converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldWriteProto2MessageWithRepeatedFields()
    {
        TestCatalogConfig catalog = CatalogConfig.builder(TestCatalogConfig::new)
                .namespace("test")
                .name("proto2test")
                .type("test")
                .options(TestCatalogOptionsConfig::builder)
                    .id(10)
                    .schema(PROTO2_SCHEMA)
                    .build()
                .build();
        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));

        ProtobufModelConfig model = ProtobufModelConfig.builder()
                .catalog()
                    .name("proto2test")
                    .schema()
                        .strategy("topic")
                        .version("latest")
                        .subject("proto2-value")
                        .record("Proto2Message")
                        .build()
                    .build()
                .build();
        ProtobufWriteConverterHandler converter = new ProtobufWriteConverterHandler(model, context);

        DirectBuffer data = new UnsafeBuffer();
        // Message with required field and repeated fields
        byte[] bytes = {
            0x0a, 0x08, 0x72, 0x65, 0x71, 0x75, 0x69, 0x72, 0x65, 0x64,  // required_field: "required"
            0x22, 0x05, 0x66, 0x69, 0x72, 0x73, 0x74,  // repeated_field[0]: "first"
            0x22, 0x06, 0x73, 0x65, 0x63, 0x6f, 0x6e, 0x64,  // repeated_field[1]: "second"
            0x22, 0x05, 0x74, 0x68, 0x69, 0x72, 0x64  // repeated_field[2]: "third"
        };
        data.wrap(bytes, 0, bytes.length);
        assertEquals(data.capacity() + 1, converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldReadProto2MessageWithNestedMessage()
    {
        TestCatalogConfig catalog = CatalogConfig.builder(TestCatalogConfig::new)
                .namespace("test")
                .name("proto2test")
                .type("test")
                .options(TestCatalogOptionsConfig::builder)
                    .id(10)
                    .schema(PROTO2_SCHEMA)
                    .build()
                .build();
        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));

        ProtobufModelConfig model = ProtobufModelConfig.builder()
                .catalog()
                    .name("proto2test")
                    .schema()
                        .strategy("topic")
                        .version("latest")
                        .subject("proto2-value")
                        .build()
                    .build()
                .build();
        ProtobufReadConverterHandler converter = new ProtobufReadConverterHandler(model, context);

        DirectBuffer data = new UnsafeBuffer();
        // Message with nested message
        byte[] bytes = {
            0x00,  // message index
            0x0a, 0x08, 0x72, 0x65, 0x71, 0x75, 0x69, 0x72, 0x65, 0x64,  // required_field: "required"
            0x3a, 0x0b,  // nested message tag and length
            0x08, 0x01,  // nested_id: 1
            0x12, 0x07, 0x6e, 0x65, 0x73, 0x74, 0x65, 0x64, 0x31  // nested_name: "nested1"
        };
        data.wrap(bytes, 0, bytes.length);
        assertEquals(data.capacity() - 1, converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldWriteProto2MessageJsonFormatWithDefaults()
    {
        TestCatalogConfig catalog = CatalogConfig.builder(TestCatalogConfig::new)
                .namespace("test")
                .name("proto2test")
                .type("test")
                .options(TestCatalogOptionsConfig::builder)
                    .id(10)
                    .schema(PROTO2_SCHEMA)
                    .build()
                .build();
        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));

        ProtobufModelConfig model = ProtobufModelConfig.builder()
                .view("json")
                .catalog()
                    .name("proto2test")
                    .schema()
                        .strategy("topic")
                        .version("latest")
                        .subject("proto2-value")
                        .record("Proto2Message")
                        .build()
                    .build()
                .build();
        ProtobufWriteConverterHandler converter = new ProtobufWriteConverterHandler(model, context);

        DirectBuffer data = new UnsafeBuffer();
        String json = """
                        {
                            "required_field":"test",
                            "optional_field":"optional",
                            "repeated_field":["one","two"],
                            "bool_field":false,
                            "status":"INACTIVE"
                        }
                        """;
        data.wrap(json.getBytes(), 0, json.getBytes().length);

        int result = converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP);
        assertTrue(result > 0);
    }
}
