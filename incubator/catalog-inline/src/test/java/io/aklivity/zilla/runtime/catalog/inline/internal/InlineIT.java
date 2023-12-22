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
package io.aklivity.zilla.runtime.catalog.inline.internal;

import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.catalog.inline.config.InlineOptionsConfig;
import io.aklivity.zilla.runtime.catalog.inline.config.InlineSchemaConfig;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.validator.function.ValueConsumer;

public class InlineIT
{
    private InlineOptionsConfig config;

    @Before
    public void setup()
    {
        config = new InlineOptionsConfig(singletonList(
                new InlineSchemaConfig("subject1", "latest",
                        "{\"fields\":[{\"name\":\"id\",\"type\":\"string\"}," +
                        "{\"name\":\"status\",\"type\":\"string\"}]," +
                        "\"name\":\"Event\",\"namespace\":\"io.aklivity.example\",\"type\":\"record\"}")));
    }

    @Test
    public void shouldResolveSchemaViaSchemaId()
    {
        String expected = "{\"fields\":[{\"name\":\"id\",\"type\":\"string\"}," +
                "{\"name\":\"status\",\"type\":\"string\"}]," +
                "\"name\":\"Event\",\"namespace\":\"io.aklivity.example\",\"type\":\"record\"}";

        InlineCatalogHandler catalog = new InlineCatalogHandler(config);

        int schemaId = catalog.resolve("subject1", "latest");
        String schema = catalog.resolve(schemaId);

        assertThat(schema, not(nullValue()));
        assertEquals(expected, schema);
    }

    @Test
    public void shouldResolveSchemaIdAndProcessData()
    {
        InlineCatalogHandler catalog = new InlineCatalogHandler(config);

        DirectBuffer data = new UnsafeBuffer();

        String payload =
                "{" +
                    "\"id\": \"123\"," +
                    "\"status\": \"OK\"" +
                "}";
        byte[] bytes = payload.getBytes();
        data.wrap(bytes, 0, bytes.length);

        int valLength = catalog.decode(data, 0, data.capacity(), ValueConsumer.NOP, CatalogHandler.Decoder.IDENTITY);

        assertEquals(data.capacity(), valLength);
    }

    @Test
    public void shouldVerifyEncodedData()
    {
        InlineCatalogHandler catalog = new InlineCatalogHandler(config);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x06, 0x69, 0x64,
            0x30, 0x10, 0x70, 0x6f, 0x73, 0x69, 0x74, 0x69, 0x76, 0x65};
        data.wrap(bytes, 0, bytes.length);

        assertEquals(13, catalog.encode(1, data, 0, data.capacity(),
                ValueConsumer.NOP, CatalogHandler.Encoder.IDENTITY));
    }
}
