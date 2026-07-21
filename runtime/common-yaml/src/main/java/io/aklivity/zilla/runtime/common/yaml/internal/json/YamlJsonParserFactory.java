/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.common.yaml.internal.json;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Map;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParserFactory;

public final class YamlJsonParserFactory implements JsonParserFactory
{
    private final Map<String, ?> config;

    public YamlJsonParserFactory(
        Map<String, ?> config)
    {
        this.config = config == null ? Map.of() : Map.copyOf(config);
    }

    @Override
    public JsonParser createParser(
        Reader reader)
    {
        return new YamlJsonParser(reader, config);
    }

    @Override
    public JsonParser createParser(
        InputStream in)
    {
        return new YamlJsonParser(in, UTF_8, config);
    }

    @Override
    public JsonParser createParser(
        InputStream in,
        Charset charset)
    {
        return new YamlJsonParser(in, charset, config);
    }

    @Override
    public JsonParser createParser(
        JsonObject obj)
    {
        return YamlJsonValues.parser(obj);
    }

    @Override
    public JsonParser createParser(
        JsonArray array)
    {
        return YamlJsonValues.parser(array);
    }

    @Override
    public Map<String, ?> getConfigInUse()
    {
        return config;
    }

}
