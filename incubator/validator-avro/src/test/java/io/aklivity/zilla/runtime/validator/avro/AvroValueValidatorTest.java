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
package io.aklivity.zilla.runtime.validator.avro;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DIRECTORY;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.util.Properties;
import java.util.function.LongFunction;
import java.util.function.ToLongFunction;

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
import io.aklivity.zilla.runtime.engine.internal.LabelManager;
import io.aklivity.zilla.runtime.engine.internal.stream.NamespacedId;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.TestCatalog;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.config.TestCatalogOptionsConfig;
import io.aklivity.zilla.runtime.engine.validator.function.ValueConsumer;
import io.aklivity.zilla.runtime.validator.avro.config.AvroValidatorConfig;

public class AvroValueValidatorTest
{
    private static final String SCHEMA = "{\"fields\":[{\"name\":\"id\",\"type\":\"string\"}," +
            "{\"name\":\"status\",\"type\":\"string\"}]," +
            "\"name\":\"Event\",\"namespace\":\"io.aklivity.example\",\"type\":\"record\"}";

    private final ValueConsumer fragmentFunction = (buffer, index, length) -> {};

    private final AvroValidatorConfig avroConfig = AvroValidatorConfig.builder()
            .catalog()
                .name("test0")
                    .schema()
                        .strategy("topic")
                        .version("latest")
                        .subject("test-value")
                        .build()
                .build()
            .build();

    private LabelManager labels;
    private ToLongFunction<String> resolveId;
    private CatalogContext context;

    @Before
    public void init()
    {
        Properties properties = new Properties();
        properties.setProperty(ENGINE_DIRECTORY.name(), "target/zilla-itests");
        Configuration config = new Configuration(properties);
        labels = new LabelManager(config.directory());
        resolveId = name -> name != null ? NamespacedId.id(1, labels.supplyLabelId(name)) : 0L;
        Catalog catalog = new TestCatalog(config);
        context = catalog.supply(mock(EngineContext.class));
    }

    @Test
    public void shouldVerifyValidAvroEvent()
    {
        CatalogConfig catalogConfig = new CatalogConfig("test0", "test", new TestCatalogOptionsConfig(SCHEMA));
        LongFunction<CatalogHandler> handler = value -> context.attach(catalogConfig);
        AvroValueValidator validator = new AvroReadValueValidator(avroConfig, resolveId, handler);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x00, 0x00, 0x00, 0x00, 0x09, 0x06, 0x69, 0x64,
            0x30, 0x10, 0x70, 0x6f, 0x73, 0x69, 0x74, 0x69, 0x76, 0x65};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(data.capacity(), validator.validate(data, 0, data.capacity(), fragmentFunction));
    }

    @Test
    public void shouldWriteValidAvroEvent()
    {
        CatalogConfig catalogConfig = new CatalogConfig("test0", "test", new TestCatalogOptionsConfig(SCHEMA));
        LongFunction<CatalogHandler> handler = value -> context.attach(catalogConfig);
        AvroValueValidator validator = new AvroWriteValueValidator(avroConfig, resolveId, handler);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x06, 0x69, 0x64, 0x30, 0x10, 0x70, 0x6f,
            0x73, 0x69, 0x74, 0x69, 0x76, 0x65};
        data.wrap(bytes, 0, bytes.length);

        byte[] expectedBytes = {0x00, 0x00, 0x00, 0x00, 0x01, 0x06, 0x69, 0x64,
            0x30, 0x10, 0x70, 0x6f, 0x73, 0x69, 0x74, 0x69, 0x76, 0x65};
        DirectBuffer expected = new UnsafeBuffer();
        expected.wrap(expectedBytes);
        assertEquals(expected.capacity(), validator.validate(data, 0, data.capacity(), fragmentFunction));
    }

    @Test
    public void shouldVerifyInvalidAvroEvent()
    {
        CatalogConfig catalogConfig = new CatalogConfig("test0", "test", new TestCatalogOptionsConfig(SCHEMA));
        LongFunction<CatalogHandler> handler = value -> context.attach(catalogConfig);
        AvroValueValidator validator = new AvroReadValueValidator(avroConfig, resolveId, handler);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x00, 0x00, 0x00, 0x00, 0x09, 0x06, 0x69, 0x64, 0x30, 0x10};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(-1, validator.validate(data, 0, data.capacity(), fragmentFunction));
    }

    @Test
    public void shouldVerifyMagicBytes()
    {
        CatalogConfig catalogConfig = new CatalogConfig("test0", "test", new TestCatalogOptionsConfig(SCHEMA));
        LongFunction<CatalogHandler> handler = value -> context.attach(catalogConfig);
        AvroValueValidator validator = new AvroReadValueValidator(avroConfig, resolveId, handler);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = "Invalid Event".getBytes();
        data.wrap(bytes, 0, bytes.length);
        assertEquals(-1, validator.validate(data, 0, data.capacity(), fragmentFunction));
    }

    @Test
    public void shouldVerifyInvalidSchemaId()
    {
        CatalogConfig catalogConfig = new CatalogConfig("test0", "test", new TestCatalogOptionsConfig(SCHEMA));
        LongFunction<CatalogHandler> handler = value -> context.attach(catalogConfig);
        AvroValueValidator validator = new AvroReadValueValidator(avroConfig, resolveId, handler);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x00, 0x00, 0x00, 0x00, 0x79, 0x06, 0x69, 0x64, 0x30, 0x10};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(-1, validator.validate(data, 0, data.capacity(), fragmentFunction));
    }

    @Test
    public void shouldReadAvroEventExpectJson()
    {
        CatalogConfig catalogConfig = new CatalogConfig("test0", "test", new TestCatalogOptionsConfig(SCHEMA));
        LongFunction<CatalogHandler> handler = value -> context.attach(catalogConfig);
        AvroValidatorConfig config = AvroValidatorConfig.builder()
                .expect("json")
                .catalog()
                    .name("test0")
                        .schema()
                        .strategy("topic")
                        .version("latest")
                        .subject("test-value")
                        .build()
                    .build()
                .build();
        AvroValueValidator validator = new AvroReadValueValidator(config, resolveId, handler);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x00, 0x00, 0x00, 0x00, 0x09, 0x06, 0x69, 0x64,
            0x30, 0x10, 0x70, 0x6f, 0x73, 0x69, 0x74, 0x69, 0x76, 0x65};
        data.wrap(bytes, 0, bytes.length);

        String json = "{" +
                "\"id\":\"id0\"," +
                "\"status\":\"positive\"" +
                "}";

        DirectBuffer expected = new UnsafeBuffer();
        expected.wrap(json.getBytes(), 0, json.getBytes().length);

        int progress = validator.validate(data, 0, data.capacity(), fragmentFunction);
        assertEquals(expected.capacity(), progress);
    }

    @Test
    public void shouldWriteJsonEventExpectAvro()
    {
        CatalogConfig catalogConfig = new CatalogConfig("test0", "test", new TestCatalogOptionsConfig(SCHEMA));
        LongFunction<CatalogHandler> handler = value -> context.attach(catalogConfig);
        AvroValidatorConfig config = AvroValidatorConfig.builder()
                .expect("json")
                .catalog()
                    .name("test0")
                        .schema()
                        .strategy("topic")
                        .version("latest")
                        .subject("test-value")
                        .build()
                    .build()
                .build();
        AvroValueValidator validator = new AvroWriteValueValidator(config, resolveId, handler);

        DirectBuffer expected = new UnsafeBuffer();

        byte[] bytes = {0x00, 0x00, 0x00, 0x00, 0x01, 0x06, 0x69, 0x64,
            0x30, 0x10, 0x70, 0x6f, 0x73, 0x69, 0x74, 0x69, 0x76, 0x65};
        expected.wrap(bytes, 0, bytes.length);

        String payload = "{" +
                "\"id\":\"id0\"," +
                "\"status\":\"positive\"" +
                "}";

        DirectBuffer data = new UnsafeBuffer();
        data.wrap(payload.getBytes(), 0, payload.getBytes().length);
        int progress = validator.validate(data, 0, data.capacity(), fragmentFunction);
        assertEquals(expected.capacity(), progress);
    }
}
