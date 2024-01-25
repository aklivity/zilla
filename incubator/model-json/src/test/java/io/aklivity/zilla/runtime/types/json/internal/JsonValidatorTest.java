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
package io.aklivity.zilla.runtime.types.json.internal;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DIRECTORY;
import static io.aklivity.zilla.runtime.engine.validator.ValidatorHandler.FLAGS_FIN;
import static io.aklivity.zilla.runtime.engine.validator.ValidatorHandler.FLAGS_INIT;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

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
import io.aklivity.zilla.runtime.engine.converter.function.ValueConsumer;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.TestCatalog;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.config.TestCatalogOptionsConfig;
import io.aklivity.zilla.runtime.types.json.config.JsonValidatorConfig;

public class JsonValidatorTest
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

    private final JsonValidatorConfig config = JsonValidatorConfig.builder()
            .catalog()
            .name("test0")
                .schema()
                    .strategy("topic")
                    .subject(null)
                    .version("latest")
                    .id(1)
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
    public void shouldVerifyValidCompleteJsonObject()
    {
        CatalogConfig catalogConfig = new CatalogConfig("test0", "test",
            TestCatalogOptionsConfig.builder()
                .id(1)
                .schema(OBJECT_SCHEMA)
                .build());
        LongFunction<CatalogHandler> handler = value -> context.attach(catalogConfig);
        JsonValidatorHandler validator = new JsonValidatorHandler(config, handler);

        DirectBuffer data = new UnsafeBuffer();

        String payload =
                "{" +
                    "\"id\": \"123\"," +
                    "\"status\": \"OK\"" +
                "}";
        byte[] bytes = payload.getBytes();
        data.wrap(bytes, 0, bytes.length);

        assertTrue(validator.validate(data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyInvalidCompleteJsonObject()
    {
        CatalogConfig catalogConfig = new CatalogConfig("test0", "test",
                TestCatalogOptionsConfig.builder()
                        .id(1)
                        .schema(OBJECT_SCHEMA)
                        .build());
        LongFunction<CatalogHandler> handler = value -> context.attach(catalogConfig);
        JsonValidatorHandler validator = new JsonValidatorHandler(config, handler);

        DirectBuffer data = new UnsafeBuffer();

        String payload =
                "{" +
                    "\"id\": 123," +
                    "\"status\": \"OK\"" +
                "}";
        byte[] bytes = payload.getBytes();
        data.wrap(bytes, 0, bytes.length);

        assertFalse(validator.validate(data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyValidFragmentedJsonObject()
    {
        CatalogConfig catalogConfig = new CatalogConfig("test0", "test",
            TestCatalogOptionsConfig.builder()
                .id(1)
                .schema(OBJECT_SCHEMA)
                .build());
        LongFunction<CatalogHandler> handler = value -> context.attach(catalogConfig);
        JsonValidatorHandler validator = new JsonValidatorHandler(config, handler);

        DirectBuffer data = new UnsafeBuffer();

        String payload =
                "{" +
                    "\"id\": \"123\"," +
                    "\"status\": \"OK\"" +
                "}";
        byte[] bytes = payload.getBytes();
        data.wrap(bytes, 0, bytes.length);

        assertTrue(validator.validate(FLAGS_INIT, data, 0, 12, ValueConsumer.NOP));
        assertTrue(validator.validate(FLAGS_FIN, data, 12, data.capacity() - 12, ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyInalidFragmentedJsonObject()
    {
        CatalogConfig catalogConfig = new CatalogConfig("test0", "test",
            TestCatalogOptionsConfig.builder()
                .id(1)
                .schema(OBJECT_SCHEMA)
                .build());
        LongFunction<CatalogHandler> handler = value -> context.attach(catalogConfig);
        JsonValidatorHandler validator = new JsonValidatorHandler(config, handler);

        DirectBuffer data = new UnsafeBuffer();

        String payload =
                "{" +
                    "\"id\": 123," +
                    "\"status\": \"OK\"" +
                "}";
        byte[] bytes = payload.getBytes();
        data.wrap(bytes, 0, bytes.length);

        assertTrue(validator.validate(FLAGS_INIT, data, 0, 12, ValueConsumer.NOP));
        assertFalse(validator.validate(FLAGS_FIN, data, 12, data.capacity() - 12, ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyValidJsonArray()
    {
        CatalogConfig catalogConfig = new CatalogConfig("test0", "test",
            TestCatalogOptionsConfig.builder()
                .id(1)
                .schema(ARRAY_SCHEMA)
                .build());
        LongFunction<CatalogHandler> handler = value -> context.attach(catalogConfig);
        JsonValidatorHandler validator = new JsonValidatorHandler(config, handler);

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

        assertTrue(validator.validate(data, 0, data.capacity(), ValueConsumer.NOP));
    }
}
