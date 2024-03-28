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
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.TestCatalogHandler;
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
    private EngineContext context;

    @Before
    public void init()
    {
        context = mock(EngineContext.class);
        TestCatalogOptionsConfig testCatalogOptionsConfig = TestCatalogOptionsConfig.builder()
            .id(1)
            .schema(SCHEMA)
            .build();
        CatalogConfig catalogConfig = new CatalogConfig("test", "test0", "test", testCatalogOptionsConfig);
        when(context.supplyCatalog(catalogConfig.id)).thenReturn(new TestCatalogHandler(testCatalogOptionsConfig));
    }

    @Test
    public void shouldWriteValidProtobufEvent()
    {
        ProtobufModelConfig config = ProtobufModelConfig.builder()
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
        ProtobufWriteConverterHandler converter = new ProtobufWriteConverterHandler(config, context);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x0a, 0x02, 0x4f, 0x4b, 0x12, 0x08, 0x30, 0x31, 0x30, 0x31, 0x32, 0x30, 0x32, 0x34};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(data.capacity() + 1, converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));

        assertEquals(data.capacity() + 1, converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldWriteValidProtobufEventNestedMessage()
    {
        ProtobufModelConfig config = ProtobufModelConfig.builder()
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
        ProtobufWriteConverterHandler converter = new ProtobufWriteConverterHandler(config, context);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x0a, 0x02, 0x4f, 0x4b, 0x12, 0x08, 0x30, 0x31, 0x30, 0x31, 0x32, 0x30, 0x32, 0x34};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(data.capacity() + 3, converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldWriteValidProtobufEventIncorrectRecordName()
    {
        ProtobufModelConfig config = ProtobufModelConfig.builder()
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
        ProtobufWriteConverterHandler converter = new ProtobufWriteConverterHandler(config, context);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x0a, 0x02, 0x4f, 0x4b, 0x12, 0x08, 0x30, 0x31, 0x30, 0x31, 0x32, 0x30, 0x32, 0x34};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(-1, converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldReadValidProtobufEvent()
    {
        ProtobufModelConfig config = ProtobufModelConfig.builder()
                .catalog()
                    .name("test0")
                    .schema()
                        .strategy("topic")
                        .version("latest")
                        .subject("test-value")
                        .build()
                    .build()
                .build();
        ProtobufReadConverterHandler converter = new ProtobufReadConverterHandler(config, context);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x00, 0x0a, 0x02, 0x4f, 0x4b, 0x12, 0x08, 0x30, 0x31, 0x30, 0x31, 0x32, 0x30, 0x32, 0x34};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(data.capacity() - 1, converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));

        assertEquals(data.capacity() - 1, converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldReadValidProtobufEventNestedMessage()
    {
        ProtobufModelConfig config = ProtobufModelConfig.builder()
                .catalog()
                    .name("test0")
                        .schema()
                        .strategy("topic")
                        .version("latest")
                        .subject("test-value")
                        .build()
                    .build()
                .build();
        ProtobufReadConverterHandler converter = new ProtobufReadConverterHandler(config, context);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x04, 0x02, 0x04, 0x0a, 0x02, 0x4f, 0x4b, 0x12, 0x08, 0x30, 0x31, 0x30, 0x31, 0x32, 0x30, 0x32, 0x34};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(data.capacity() - 3, converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldReadValidProtobufEventFormatJson()
    {
        ProtobufModelConfig config = ProtobufModelConfig.builder()
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

        ProtobufReadConverterHandler converter = new ProtobufReadConverterHandler(config, context);

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
        ProtobufModelConfig config = ProtobufModelConfig.builder()
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

        ProtobufWriteConverterHandler converter = new ProtobufWriteConverterHandler(config, context);

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
        ProtobufModelConfig config = ProtobufModelConfig.builder()
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

        ProtobufWriteConverterHandler converter = new ProtobufWriteConverterHandler(config, context);

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
        ProtobufModelConfig config = ProtobufModelConfig.builder()
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
        ProtobufReadConverterHandler converter = new ProtobufReadConverterHandler(config, context);

        DirectBuffer data = new UnsafeBuffer();

        assertEquals(71, converter.padding(data, 0, data.capacity()));
    }

    @Test
    public void shouldVerifyIndexPaddingLength()
    {
        ProtobufModelConfig config = ProtobufModelConfig.builder()
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
        ProtobufWriteConverterHandler converter = new ProtobufWriteConverterHandler(config, context);

        DirectBuffer data = new UnsafeBuffer();

        assertEquals(3, converter.padding(data, 0, data.capacity()));

    }
}
