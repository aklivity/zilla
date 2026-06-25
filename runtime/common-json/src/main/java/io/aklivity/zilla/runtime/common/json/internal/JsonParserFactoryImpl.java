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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Map;

import jakarta.json.JsonArray;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParserFactory;

import io.aklivity.zilla.runtime.common.json.internal.json.JsonValues;

public final class JsonParserFactoryImpl implements JsonParserFactory
{
    private final Map<String, ?> config;

    public JsonParserFactoryImpl(
        Map<String, ?> config)
    {
        this.config = config;
    }

    @Override
    public JsonParser createParser(
        Reader reader)
    {
        return new JsonParserImpl(readAll(reader), config);
    }

    @Override
    public JsonParser createParser(
        InputStream in)
    {
        return new JsonParserImpl(in, config);
    }

    @Override
    public JsonParser createParser(
        InputStream in,
        Charset charset)
    {
        return new JsonParserImpl(in, config);
    }

    @Override
    public JsonParser createParser(
        JsonObject obj)
    {
        return JsonValues.parser(obj);
    }

    @Override
    public JsonParser createParser(
        JsonArray array)
    {
        return JsonValues.parser(array);
    }

    private static InputStream readAll(
        Reader reader)
    {
        StringBuilder builder = new StringBuilder();
        char[] chunk = new char[1024];
        try
        {
            for (int read = reader.read(chunk); read != -1; read = reader.read(chunk))
            {
                builder.append(chunk, 0, read);
            }
        }
        catch (IOException ex)
        {
            throw new JsonException(ex.getMessage(), ex);
        }
        return new ByteArrayInputStream(builder.toString().getBytes(UTF_8));
    }

    @Override
    public Map<String, ?> getConfigInUse()
    {
        return config;
    }
}
