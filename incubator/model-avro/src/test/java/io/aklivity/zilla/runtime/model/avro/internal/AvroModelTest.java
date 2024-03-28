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
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.TestCatalogHandler;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.config.TestCatalogOptionsConfig;
import io.aklivity.zilla.runtime.model.avro.config.AvroModelConfig;

public class AvroModelTest
{
    private static final String SCHEMA = "{\"fields\":[{\"name\":\"id\",\"type\":\"string\"}," +
            "{\"name\":\"status\",\"type\":\"string\"}]," +
            "\"name\":\"Event\",\"namespace\":\"io.aklivity.example\",\"type\":\"record\"}";

    private final AvroModelConfig avroConfig = AvroModelConfig.builder()
            .catalog()
                .name("test0")
                    .schema()
                        .strategy("topic")
                        .version("latest")
                        .subject("test-value")
                        .build()
                .build()
            .build();
    private EngineContext context;

    @Before
    public void init()
    {
        context = mock(EngineContext.class);
    }

    @Test
    public void shouldVerifyValidAvroEvent()
    {
        TestCatalogOptionsConfig testCatalogOptionsConfig = TestCatalogOptionsConfig.builder()
            .id(9)
            .schema(SCHEMA)
            .build();
        CatalogConfig catalogConfig = new CatalogConfig("test", "test0", "test", testCatalogOptionsConfig);
        when(context.supplyCatalog(catalogConfig.id)).thenReturn(new TestCatalogHandler(testCatalogOptionsConfig));
        AvroReadConverterHandler converter = new AvroReadConverterHandler(avroConfig, context);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x06, 0x69, 0x64,
            0x30, 0x10, 0x70, 0x6f, 0x73, 0x69, 0x74, 0x69, 0x76, 0x65};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(data.capacity(), converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldWriteValidAvroEvent()
    {
        TestCatalogOptionsConfig testCatalogOptionsConfig = TestCatalogOptionsConfig.builder()
            .id(1)
            .schema(SCHEMA)
            .build();
        CatalogConfig catalogConfig = new CatalogConfig("test", "test0", "test", testCatalogOptionsConfig);
        when(context.supplyCatalog(catalogConfig.id)).thenReturn(new TestCatalogHandler(testCatalogOptionsConfig));
        AvroWriteConverterHandler converter = new AvroWriteConverterHandler(avroConfig, context);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x06, 0x69, 0x64, 0x30, 0x10, 0x70, 0x6f,
            0x73, 0x69, 0x74, 0x69, 0x76, 0x65};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(data.capacity(), converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyInvalidAvroEvent()
    {
        TestCatalogOptionsConfig testCatalogOptionsConfig = TestCatalogOptionsConfig.builder()
            .id(9)
            .schema(SCHEMA)
            .build();
        CatalogConfig catalogConfig = new CatalogConfig("test", "test0", "test", testCatalogOptionsConfig);
        when(context.supplyCatalog(catalogConfig.id)).thenReturn(new TestCatalogHandler(testCatalogOptionsConfig));
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
        AvroReadConverterHandler converter = new AvroReadConverterHandler(avroConfig, context);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x06, 0x69, 0x64, 0x30, 0x10};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(-1, converter.convert(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldReadAvroEventExpectJson()
    {
        TestCatalogOptionsConfig testCatalogOptionsConfig = TestCatalogOptionsConfig.builder()
            .id(9)
            .schema(SCHEMA)
            .build();
        CatalogConfig catalogConfig = new CatalogConfig("test", "test0", "test", testCatalogOptionsConfig);
        when(context.supplyCatalog(catalogConfig.id)).thenReturn(new TestCatalogHandler(testCatalogOptionsConfig));
        AvroModelConfig config = AvroModelConfig.builder()
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
        AvroReadConverterHandler converter = new AvroReadConverterHandler(config, context);

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
        TestCatalogOptionsConfig testCatalogOptionsConfig = TestCatalogOptionsConfig.builder()
            .id(9)
            .schema(SCHEMA)
            .build();
        CatalogConfig catalogConfig = new CatalogConfig("test", "test0", "test", testCatalogOptionsConfig);
        when(context.supplyCatalog(catalogConfig.id)).thenReturn(new TestCatalogHandler(testCatalogOptionsConfig));
        AvroModelConfig config = AvroModelConfig.builder()
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
        AvroWriteConverterHandler converter = new AvroWriteConverterHandler(config, context);

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
    public void shouldVerifyPaddingLength()
    {
        TestCatalogOptionsConfig testCatalogOptionsConfig = TestCatalogOptionsConfig.builder()
            .id(9)
            .schema(SCHEMA)
            .build();
        CatalogConfig catalogConfig = new CatalogConfig("test", "test0", "test", testCatalogOptionsConfig);
        when(context.supplyCatalog(catalogConfig.id)).thenReturn(new TestCatalogHandler(testCatalogOptionsConfig));
        AvroModelConfig config = AvroModelConfig.builder()
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
        AvroReadConverterHandler converter = new AvroReadConverterHandler(config, context);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x06, 0x69, 0x64,
            0x30, 0x10, 0x70, 0x6f, 0x73, 0x69, 0x74, 0x69, 0x76, 0x65};
        data.wrap(bytes, 0, bytes.length);

        assertEquals(22, converter.padding(data, 0, data.capacity()));

    }
}
