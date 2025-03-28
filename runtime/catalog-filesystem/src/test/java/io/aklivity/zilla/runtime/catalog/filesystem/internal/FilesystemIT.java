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
package io.aklivity.zilla.runtime.catalog.filesystem.internal;

import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.net.URL;
import java.nio.file.Path;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import io.aklivity.zilla.runtime.catalog.filesystem.internal.config.FilesystemOptionsConfig;
import io.aklivity.zilla.runtime.catalog.filesystem.internal.config.FilesystemSchemaConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;

public class FilesystemIT
{
    private FilesystemOptionsConfig config;
    private EngineContext context = mock(EngineContext.class);

    @Before
    public void setup() throws Exception
    {
        config = new FilesystemOptionsConfig(singletonList(
            new FilesystemSchemaConfig("subject1", "asyncapi/mqtt.yaml")));

        URL url = FilesystemIT.class.getResource("../../../../specs/catalog/filesystem/config/asyncapi/mqtt.yaml");
        Path path = Path.of(url.toURI());
        Mockito.doReturn(path).when(context).resolvePath("asyncapi/mqtt.yaml");
    }

    @Test
    public void shouldResolveSchemaViaSchemaId()
    {
        String expected = "asyncapi: 3.0.0\n" +
            "info:\n" +
            "  title: Zilla MQTT Proxy\n" +
            "  version: 1.0.0\n" +
            "  license:\n" +
            "    name: Aklivity Community License\n" +
            "servers:\n" +
            "  plain:\n" +
            "    host: mqtt://localhost:7183\n" +
            "    protocol: mqtt\n" +
            "defaultContentType: application/json\n";

        FilesystemCatalogHandler catalog = new FilesystemCatalogHandler(config, context, 0L);

        int schemaId = catalog.resolve("subject1", "latest");
        String schema = catalog.resolve(schemaId);

        assertThat(schema, not(nullValue()));
        assertEquals(expected, schema);
    }

    @Test
    public void shouldResolveSchemaIdAndProcessData()
    {
        FilesystemCatalogHandler catalog = new FilesystemCatalogHandler(config, context, 0L);

        DirectBuffer data = new UnsafeBuffer();

        String payload =
            "{" +
                "\"id\": \"123\"," +
                "\"status\": \"OK\"" +
                "}";
        byte[] bytes = payload.getBytes();
        data.wrap(bytes, 0, bytes.length);

        int valLength = catalog.decode(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP, CatalogHandler.Decoder.IDENTITY);

        assertEquals(data.capacity(), valLength);
    }

    @Test
    public void shouldVerifyEncodedData()
    {
        FilesystemCatalogHandler catalog = new FilesystemCatalogHandler(config, context, 0L);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x06, 0x69, 0x64,
            0x30, 0x10, 0x70, 0x6f, 0x73, 0x69, 0x74, 0x69, 0x76, 0x65};
        data.wrap(bytes, 0, bytes.length);

        assertEquals(13, catalog.encode(0L, 0L, 1, data, 0, data.capacity(),
            ValueConsumer.NOP, CatalogHandler.Encoder.IDENTITY));
    }
}
