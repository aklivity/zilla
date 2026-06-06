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
package io.aklivity.zilla.runtime.common.yaml.internal;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;

import jakarta.json.stream.JsonParsingException;

import io.aklivity.zilla.runtime.common.yaml.YamlParser;
import io.aklivity.zilla.runtime.common.yaml.YamlValue;

public final class YamlParserImpl implements YamlParser
{
    private final String text;
    private boolean parsed;

    public YamlParserImpl(
        Reader reader)
    {
        this.text = readAll(reader);
    }

    public YamlParserImpl(
        InputStream in)
    {
        this(in, UTF_8);
    }

    public YamlParserImpl(
        InputStream in,
        Charset charset)
    {
        this.text = readAll(in, charset);
    }

    @Override
    public YamlValue parse()
    {
        if (parsed)
        {
            throw new IllegalStateException("YAML document has already been parsed");
        }
        parsed = true;
        return YamlValues.wrap(YamlDocumentParser.parse(text).node);
    }

    @Override
    public void close()
    {
    }

    private static String readAll(
        Reader reader)
    {
        try
        {
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1)
            {
                builder.append(buffer, 0, read);
            }
            return builder.toString();
        }
        catch (IOException ex)
        {
            throw new JsonParsingException(ex.getMessage(), ex,
                new YamlLocation(1, 1, 0));
        }
    }

    private static String readAll(
        InputStream in,
        Charset charset)
    {
        try
        {
            return new String(in.readAllBytes(), charset);
        }
        catch (IOException ex)
        {
            throw new JsonParsingException(ex.getMessage(), ex,
                new YamlLocation(1, 1, 0));
        }
    }
}
