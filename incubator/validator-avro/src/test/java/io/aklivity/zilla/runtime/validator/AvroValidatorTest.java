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
package io.aklivity.zilla.runtime.validator;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DIRECTORY;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.Collections;
import java.util.List;
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
import io.aklivity.zilla.runtime.engine.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.config.SchemaConfig;
import io.aklivity.zilla.runtime.engine.internal.LabelManager;
import io.aklivity.zilla.runtime.engine.internal.stream.NamespacedId;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.TestCatalog;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.config.TestCatalogOptionsConfig;
import io.aklivity.zilla.runtime.validator.config.AvroValidatorConfig;

public class AvroValidatorTest
{
    private static final String SCHEMA = "{\"fields\":[{\"name\":\"id\",\"type\":\"string\"}," +
            "{\"name\":\"status\",\"type\":\"string\"}]," +
            "\"name\":\"Event\",\"namespace\":\"io.aklivity.example\",\"type\":\"record\"}";

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

        AvroValidatorConfig avroConfig = new AvroValidatorConfig(Collections.singletonList(
                new CatalogedConfig("test0",
                    List.of(new SchemaConfig(null, "topic", "latest", 0)))), "test-value");
        AvroValidator validator = new AvroValidator(avroConfig, resolveId, handler);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x00, 0x00, 0x00, 0x00, 0x09, 0x06, 0x69, 0x64,
            0x30, 0x10, 0x70, 0x6f, 0x73, 0x69, 0x74, 0x69, 0x76, 0x65};
        data.wrap(bytes, 0, bytes.length);
        assertTrue(validator.read(data, 0, data.capacity()));
    }

    @Test
    public void shouldVerifyInvalidAvroEvent()
    {
        CatalogConfig catalogConfig = new CatalogConfig("test0", "test", new TestCatalogOptionsConfig(SCHEMA));
        LongFunction<CatalogHandler> handler = value -> context.attach(catalogConfig);

        AvroValidatorConfig avroConfig = new AvroValidatorConfig(Collections.singletonList(
            new CatalogedConfig("test0",
                List.of(new SchemaConfig(null, "topic", "latest", 0)))), "test-value");
        AvroValidator validator = new AvroValidator(avroConfig, resolveId, handler);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x00, 0x00, 0x00, 0x00, 0x09, 0x06, 0x69, 0x64, 0x30, 0x10};
        data.wrap(bytes, 0, bytes.length);
        assertFalse(validator.read(data, 0, data.capacity()));
    }

    @Test
    public void shouldVerifyMagicBytes()
    {
        CatalogConfig catalogConfig = new CatalogConfig("test0", "test", new TestCatalogOptionsConfig(SCHEMA));
        LongFunction<CatalogHandler> handler = value -> context.attach(catalogConfig);

        AvroValidatorConfig avroConfig = new AvroValidatorConfig(Collections.singletonList(
            new CatalogedConfig("test0",
                List.of(new SchemaConfig(null, "topic", "latest", 0)))), "test-value");
        AvroValidator validator = new AvroValidator(avroConfig, resolveId, handler);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = "Invalid Event".getBytes();
        data.wrap(bytes, 0, bytes.length);
        assertFalse(validator.read(data, 0, data.capacity()));
    }

    @Test
    public void shouldVerifyInvalidSchemaId()
    {
        CatalogConfig catalogConfig = new CatalogConfig("test0", "test", new TestCatalogOptionsConfig(SCHEMA));
        LongFunction<CatalogHandler> handler = value -> context.attach(catalogConfig);

        AvroValidatorConfig avroConfig = new AvroValidatorConfig(Collections.singletonList(
            new CatalogedConfig("test0",
                List.of(new SchemaConfig(null, "topic", "latest", 0)))), "test-value");
        AvroValidator validator = new AvroValidator(avroConfig, resolveId, handler);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x00, 0x00, 0x00, 0x00, 0x79, 0x06, 0x69, 0x64, 0x30, 0x10};
        data.wrap(bytes, 0, bytes.length);
        assertFalse(validator.read(data, 0, data.capacity()));
    }
}
