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
package io.aklivity.zilla.runtime.common.json.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.util.List;
import java.util.Map;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParserFactory;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.json.StreamingJson;

public class StreamingJsonParserFactoryTest
{
    @Test
    public void shouldCreateParserForInputStreamWithSharedConfig()
    {
        final Map<String, Object> config = Map.of(
            StreamingJson.PATH_INCLUDES, List.of("/jsonrpc"),
            StreamingJson.TOKEN_MAX_BYTES, 1024);
        final JsonParserFactory factory = StreamingJson.createParserFactory(config);

        final InputStream in1 = new BufferedInputStream(
            new ByteArrayInputStream("{\"jsonrpc\":\"2.0\"}".getBytes(UTF_8)));
        try (JsonParser parser1 = factory.createParser(in1))
        {
            assertNotNull(parser1);
        }

        // factory is reusable
        final InputStream in2 = new BufferedInputStream(
            new ByteArrayInputStream("{\"jsonrpc\":\"2.0\"}".getBytes(UTF_8)));
        try (JsonParser parser2 = factory.createParser(in2))
        {
            assertNotNull(parser2);
        }
    }

    @Test
    public void shouldExposeConfigInUse()
    {
        final Map<String, Object> config = Map.of(
            StreamingJson.PATH_INCLUDES, List.of("/jsonrpc"));
        final JsonParserFactory factory = StreamingJson.createParserFactory(config);
        assertSame(config, factory.getConfigInUse());
    }

    @Test
    public void shouldCreateParserForInputStreamWithCharset()
    {
        final JsonParserFactory factory = StreamingJson.createParserFactory(Map.of());
        final InputStream in = new BufferedInputStream(
            new ByteArrayInputStream("{}".getBytes(UTF_8)));
        try (JsonParser parser = factory.createParser(in, UTF_8))
        {
            assertEquals(JsonParser.Event.START_OBJECT, parser.next());
        }
    }

    @Test
    public void shouldThrowForReaderSource()
    {
        final JsonParserFactory factory = StreamingJson.createParserFactory(Map.of());
        assertThrows(UnsupportedOperationException.class,
            () -> factory.createParser(new StringReader("{}")));
    }

    @Test
    public void shouldThrowForJsonObjectSource()
    {
        final JsonParserFactory factory = StreamingJson.createParserFactory(Map.of());
        assertThrows(UnsupportedOperationException.class, () -> factory.createParser((JsonObject) null));
    }

    @Test
    public void shouldThrowForJsonArraySource()
    {
        final JsonParserFactory factory = StreamingJson.createParserFactory(Map.of());
        assertThrows(UnsupportedOperationException.class, () -> factory.createParser((JsonArray) null));
    }
}
