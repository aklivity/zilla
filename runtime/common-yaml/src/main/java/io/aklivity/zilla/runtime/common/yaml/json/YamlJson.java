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
package io.aklivity.zilla.runtime.common.yaml.json;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.Map;

import jakarta.json.JsonReader;
import jakarta.json.JsonReaderFactory;
import jakarta.json.JsonWriter;
import jakarta.json.JsonWriterFactory;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonGeneratorFactory;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParserFactory;

import io.aklivity.zilla.runtime.common.yaml.internal.json.YamlJsonGenerator;
import io.aklivity.zilla.runtime.common.yaml.internal.json.YamlJsonGeneratorFactory;
import io.aklivity.zilla.runtime.common.yaml.internal.json.YamlJsonParser;
import io.aklivity.zilla.runtime.common.yaml.internal.json.YamlJsonParserFactory;
import io.aklivity.zilla.runtime.common.yaml.internal.json.YamlJsonProvider;

public final class YamlJson
{
    private YamlJson()
    {
    }

    public static JsonProvider provider()
    {
        return ProviderHolder.PROVIDER;
    }

    public static JsonProvider provider(
        Map<String, ?> config)
    {
        return new YamlJsonProvider(config);
    }

    public static JsonParser createParser(
        Reader reader)
    {
        return new YamlJsonParser(reader);
    }

    public static JsonParser createParser(
        InputStream in)
    {
        return new YamlJsonParser(in);
    }

    public static JsonParserFactory createParserFactory(
        Map<String, ?> config)
    {
        return new YamlJsonParserFactory(config);
    }

    public static JsonReader createReader(
        Reader reader)
    {
        return provider().createReader(reader);
    }

    public static JsonReader createReader(
        InputStream in)
    {
        return provider().createReader(in);
    }

    public static JsonReaderFactory createReaderFactory(
        Map<String, ?> config)
    {
        return provider().createReaderFactory(config);
    }

    public static JsonGenerator createGenerator(
        Writer writer)
    {
        return new YamlJsonGenerator(writer);
    }

    public static JsonGenerator createGenerator(
        OutputStream out)
    {
        return new YamlJsonGenerator(out);
    }

    public static JsonGeneratorFactory createGeneratorFactory(
        Map<String, ?> config)
    {
        return new YamlJsonGeneratorFactory(config);
    }

    public static JsonWriter createWriter(
        Writer writer)
    {
        return provider().createWriter(writer);
    }

    public static JsonWriter createWriter(
        OutputStream out)
    {
        return provider().createWriter(out);
    }

    public static JsonWriterFactory createWriterFactory(
        Map<String, ?> config)
    {
        return provider().createWriterFactory(config);
    }

    private static final class ProviderHolder
    {
        private static final JsonProvider PROVIDER = new YamlJsonProvider();
    }
}
