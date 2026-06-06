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
package io.aklivity.zilla.runtime.common.yaml;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.Map;

import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonGeneratorFactory;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParserFactory;

import io.aklivity.zilla.runtime.common.yaml.internal.YamlJsonGenerator;
import io.aklivity.zilla.runtime.common.yaml.internal.YamlJsonGeneratorFactory;
import io.aklivity.zilla.runtime.common.yaml.internal.YamlJsonParser;
import io.aklivity.zilla.runtime.common.yaml.internal.YamlJsonParserFactory;
import io.aklivity.zilla.runtime.common.yaml.internal.YamlJsonProvider;

public final class YamlJson
{
    private YamlJson()
    {
    }

    public static JsonProvider provider()
    {
        return ProviderHolder.PROVIDER;
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

    private static final class ProviderHolder
    {
        private static final JsonProvider PROVIDER = new YamlJsonProvider();
    }
}
