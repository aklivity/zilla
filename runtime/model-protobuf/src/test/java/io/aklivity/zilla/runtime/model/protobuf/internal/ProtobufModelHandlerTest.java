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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.CatalogConfig;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.TestCatalogHandler;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.config.TestCatalogConfig;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.config.TestCatalogOptionsConfig;
import io.aklivity.zilla.runtime.model.protobuf.config.ProtobufModelConfig;

public class ProtobufModelHandlerTest
{
    private static final String SCHEMA = """
                                            syntax = "proto3";
                                            package io.aklivity.examples.clients.proto;
                                            message SimpleMessage {
                                                string content = 1;
                                                optional string date_time = 2;
                                            }
                                            """;

    private EngineContext context;

    @Before
    public void init()
    {
        context = mock(EngineContext.class);
    }

    @Test
    public void shouldEncodeIndexesWithCountPrefix()
    {
        TestHandler handler = newHandler(null, null);

        // encodeIndexes(int[]) prepends the path length as the leading entry; decodedPath mirrors the list
        handler.encodeIndexes(new int[]{0, 1, 2});
        assertArrayEquals(new int[]{3, 0, 1, 2}, handler.decodedPath());
    }

    @Test
    public void shouldEncodeAndDecodeZigZagIndexes()
    {
        TestHandler handler = newHandler(null, null);

        // populate the indexes list with a count prefix followed by the path entries
        handler.encodeIndexes(new int[]{3, 5, 7});
        byte[] encoded = handler.encodeIndexes();

        // decodeIndexes reads the leading count then that many zig-zag varint entries
        int progress = handler.decodeIndexes(new UnsafeBufferEx(encoded), 0, encoded.length);
        assertEquals(encoded.length, progress);
        assertArrayEquals(new int[]{3, 5, 7}, handler.decodedPath());
    }

    @Test
    public void shouldDecodeZeroLengthIndex()
    {
        TestHandler handler = newHandler(null, null);

        // a single zero byte encodes a zero-length index path; decodeIndexes records the lone zero entry
        byte[] wire = {0x00};
        int progress = handler.decodeIndexes(new UnsafeBufferEx(wire), 0, wire.length);
        assertEquals(1, progress);
        assertArrayEquals(new int[]{0}, handler.decodedPath());
    }

    @Test
    public void shouldSupplySchema()
    {
        TestHandler handler = newHandler(null, null);

        ProtobufSchema schema = handler.supplySchema(1);
        assertNotNull(schema);
        // a second lookup is served from the per-handler cache
        assertEquals(schema, handler.supplySchema(1));
    }

    @Test
    public void shouldNotSupplySchemaForUnknownId()
    {
        TestHandler handler = newHandler(null, null);

        assertNull(handler.supplySchema(99));
    }

    @Test
    public void shouldSupplyIndexPadding()
    {
        TestHandler handler = newHandler(null, "SimpleMessage");

        int padding = handler.supplyIndexPadding(1);
        // SimpleMessage resolves to a single top-level message index path of length one, plus the count byte
        assertEquals(2, padding);
        // a second call is served from the per-handler padding cache
        assertEquals(padding, handler.supplyIndexPadding(1));
    }

    @Test
    public void shouldSupplyZeroIndexPaddingWithoutRecord()
    {
        TestHandler handler = newHandler(null, null);

        assertEquals(0, handler.supplyIndexPadding(1));
    }

    @Test
    public void shouldSupplyZeroIndexPaddingForUnknownSchema()
    {
        TestHandler handler = newHandler(null, "SimpleMessage");

        assertEquals(0, handler.supplyIndexPadding(99));
    }

    @Test
    public void shouldSupplyJsonFormatPadding()
    {
        TestHandler handler = newHandler("json", null);

        int padding = handler.supplyJsonFormatPadding(1);
        // two fields ("content", "date_time") plus the enclosing braces of the single top-level message
        int expected = 2 + "content".length() + "\"\":\"\",".length() +
            "date_time".length() + "\"\":\"\",".length();
        assertEquals(expected, padding);
        // a second call is served from the per-handler padding cache
        assertEquals(padding, handler.supplyJsonFormatPadding(1));
    }

    @Test
    public void shouldSupplyZeroJsonFormatPaddingForUnknownSchema()
    {
        TestHandler handler = newHandler("json", null);

        assertEquals(0, handler.supplyJsonFormatPadding(99));
    }

    private TestHandler newHandler(
        String view,
        String record)
    {
        TestCatalogConfig catalog = CatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(1)
                .schema(SCHEMA)
                .build()
            .build();
        var builder = ProtobufModelConfig.builder();
        if (view != null)
        {
            builder.view(view);
        }
        ProtobufModelConfig model = builder
            .catalog()
                .name("test0")
                .schema()
                    .strategy("topic")
                    .version("latest")
                    .subject("test-value")
                    .record(record)
                    .build()
                .build()
            .build();
        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));
        return new TestHandler(model, context);
    }

    private static final class TestHandler extends ProtobufModelHandler
    {
        private TestHandler(
            ProtobufModelConfig config,
            EngineContext context)
        {
            super(config, context);
        }
    }
}
