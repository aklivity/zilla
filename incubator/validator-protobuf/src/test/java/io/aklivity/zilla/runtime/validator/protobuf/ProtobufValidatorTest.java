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
package io.aklivity.zilla.runtime.validator.protobuf;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DIRECTORY;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.function.LongFunction;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.Catalog;
import io.aklivity.zilla.runtime.engine.catalog.CatalogContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.CatalogConfig;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.TestCatalog;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.config.TestCatalogOptionsConfig;
import io.aklivity.zilla.runtime.engine.validator.function.ValueConsumer;
import io.aklivity.zilla.runtime.validator.protobuf.config.ProtobufValidatorConfig;

public class ProtobufValidatorTest
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
    public void shouldWriteValidProtobufEvent()
    {
        CatalogConfig catalogConfig = new CatalogConfig("test0", "test",
            TestCatalogOptionsConfig.builder()
                .id(1)
                .schema(SCHEMA)
                .build());

        ProtobufValidatorConfig config = ProtobufValidatorConfig.builder()
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
        LongFunction<CatalogHandler> handler = value -> context.attach(catalogConfig);
        ProtobufWriteValidator validator = new ProtobufWriteValidator(config, handler);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x0a, 0x02, 0x4f, 0x4b, 0x12, 0x08, 0x30, 0x31, 0x30, 0x31, 0x32, 0x30, 0x32, 0x34};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(data.capacity() + 1, validator.validate(data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldWriteValidProtobufEventNestedMessage()
    {
        CatalogConfig catalogConfig = new CatalogConfig("test0", "test",
            TestCatalogOptionsConfig.builder()
                .id(1)
                .schema(SCHEMA)
                .build());

        ProtobufValidatorConfig config = ProtobufValidatorConfig.builder()
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
        LongFunction<CatalogHandler> handler = value -> context.attach(catalogConfig);
        ProtobufWriteValidator validator = new ProtobufWriteValidator(config, handler);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x0a, 0x02, 0x4f, 0x4b, 0x12, 0x08, 0x30, 0x31, 0x30, 0x31, 0x32, 0x30, 0x32, 0x34};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(data.capacity() + 3, validator.validate(data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldWriteValidProtobufEventIncorrectRecordName()
    {
        CatalogConfig catalogConfig = new CatalogConfig("test0", "test",
            TestCatalogOptionsConfig.builder()
                .id(1)
                .schema(SCHEMA)
                .build());

        ProtobufValidatorConfig config = ProtobufValidatorConfig.builder()
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
        LongFunction<CatalogHandler> handler = value -> context.attach(catalogConfig);
        ProtobufWriteValidator validator = new ProtobufWriteValidator(config, handler);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x0a, 0x02, 0x4f, 0x4b, 0x12, 0x08, 0x30, 0x31, 0x30, 0x31, 0x32, 0x30, 0x32, 0x34};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(-1, validator.validate(data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldReadValidProtobufEvent()
    {
        CatalogConfig catalogConfig = new CatalogConfig("test0", "test",
            TestCatalogOptionsConfig.builder()
                .id(1)
                .schema(SCHEMA)
                .build());

        ProtobufValidatorConfig config = ProtobufValidatorConfig.builder()
                .catalog()
                    .name("test0")
                    .schema()
                        .strategy("topic")
                        .version("latest")
                        .subject("test-value")
                        .build()
                    .build()
                .build();
        LongFunction<CatalogHandler> handler = value -> context.attach(catalogConfig);
        ProtobufReadValidator validator = new ProtobufReadValidator(config, handler);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x00, 0x0a, 0x02, 0x4f, 0x4b, 0x12, 0x08, 0x30, 0x31, 0x30, 0x31, 0x32, 0x30, 0x32, 0x34};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(data.capacity() - 1, validator.validate(data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldReadValidProtobufEventNestedMessage()
    {
        CatalogConfig catalogConfig = new CatalogConfig("test0", "test",
            TestCatalogOptionsConfig.builder()
                .id(1)
                .schema(SCHEMA)
                .build());

        ProtobufValidatorConfig config = ProtobufValidatorConfig.builder()
                .catalog()
                    .name("test0")
                        .schema()
                        .strategy("topic")
                        .version("latest")
                        .subject("test-value")
                        .build()
                    .build()
                .build();
        LongFunction<CatalogHandler> handler = value -> context.attach(catalogConfig);
        ProtobufReadValidator validator = new ProtobufReadValidator(config, handler);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x04, 0x02, 0x04, 0x0a, 0x02, 0x4f, 0x4b, 0x12, 0x08, 0x30, 0x31, 0x30, 0x31, 0x32, 0x30, 0x32, 0x34};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(data.capacity() - 3, validator.validate(data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldReadValidProtobufEventFormatJson()
    {
        CatalogConfig catalogConfig = new CatalogConfig("test0", "test",
            TestCatalogOptionsConfig.builder()
                .id(1)
                .schema(SCHEMA)
                .build());

        ProtobufValidatorConfig config = ProtobufValidatorConfig.builder()
                .format("json")
                .catalog()
                    .name("test0")
                    .schema()
                        .strategy("topic")
                        .version("latest")
                        .subject("test-value")
                        .build()
                    .build()
                .build();

        LongFunction<CatalogHandler> handler = value -> context.attach(catalogConfig);
        ProtobufReadValidator validator = new ProtobufReadValidator(config, handler);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x00, 0x0a, 0x02, 0x4f, 0x4b, 0x12, 0x08, 0x30, 0x31, 0x30, 0x31, 0x32, 0x30, 0x32, 0x34};
        data.wrap(bytes, 0, bytes.length);

        String json =
                "{\n" +
                    "  \"content\": \"OK\",\n" +
                    "  \"dateTime\": \"01012024\"\n" +
                "}";

        final ValueConsumer consumer = (buffer, index, length) ->
        {
            byte[] jsonBytes = new byte[length];
            buffer.getBytes(index, jsonBytes);
            assertEquals(json, new String(jsonBytes, StandardCharsets.UTF_8));
        };
        validator.validate(data, 0, data.capacity(), consumer);
    }

    @Test
    public void shouldWriteValidProtobufEventFormatJson()
    {
        CatalogConfig catalogConfig = new CatalogConfig("test0", "test",
            TestCatalogOptionsConfig.builder()
                .id(1)
                .schema(SCHEMA)
                .build());

        ProtobufValidatorConfig config = ProtobufValidatorConfig.builder()
                .format("json")
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

        LongFunction<CatalogHandler> handler = value -> context.attach(catalogConfig);
        ProtobufWriteValidator validator = new ProtobufWriteValidator(config, handler);

        DirectBuffer data = new UnsafeBuffer();

        String json =
                "{\n" +
                    "  \"content\": \"OK\",\n" +
                    "  \"dateTime\": \"01012024\"\n" +
                "}";
        data.wrap(json.getBytes(), 0, json.getBytes().length);

        byte[] expectedBytes = {0x00, 0x0a, 0x02, 0x4f, 0x4b, 0x12, 0x08, 0x30, 0x31, 0x30, 0x31, 0x32, 0x30, 0x32, 0x34};
        DirectBuffer expected = new UnsafeBuffer();
        expected.wrap(expectedBytes, 0, expectedBytes.length);

        assertEquals(expected.capacity(), validator.validate(data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyJsonFormatPaddingLength()
    {
        CatalogConfig catalogConfig = new CatalogConfig("test0", "test",
                TestCatalogOptionsConfig.builder()
                        .id(9)
                        .schema(SCHEMA)
                        .build());
        LongFunction<CatalogHandler> handler = value -> context.attach(catalogConfig);
        ProtobufValidatorConfig config = ProtobufValidatorConfig.builder()
                .format("json")
                .catalog()
                    .name("test0")
                        .schema()
                        .strategy("topic")
                        .version("latest")
                        .subject("test-value")
                        .build()
                    .build()
                .build();
        ProtobufReadValidator validator = new ProtobufReadValidator(config, handler);

        DirectBuffer data = new UnsafeBuffer();

        assertEquals(1584, validator.padding(data, 0, data.capacity()));
    }

    @Test
    public void shouldVerifyIndexPaddingLength()
    {
        CatalogConfig catalogConfig = new CatalogConfig("test0", "test",
                TestCatalogOptionsConfig.builder()
                        .id(9)
                        .schema(SCHEMA)
                        .build());
        LongFunction<CatalogHandler> handler = value -> context.attach(catalogConfig);
        ProtobufValidatorConfig config = ProtobufValidatorConfig.builder()
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
        ProtobufWriteValidator validator = new ProtobufWriteValidator(config, handler);

        DirectBuffer data = new UnsafeBuffer();

        assertEquals(3, validator.padding(data, 0, data.capacity()));

    }
}
