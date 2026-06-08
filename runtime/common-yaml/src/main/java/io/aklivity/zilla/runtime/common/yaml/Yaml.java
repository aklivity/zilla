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

import io.aklivity.zilla.runtime.common.yaml.internal.YamlProviderImpl;
import io.aklivity.zilla.runtime.common.yaml.spi.YamlProvider;

public final class Yaml
{
    private Yaml()
    {
    }

    public static YamlProvider provider()
    {
        return ProviderHolder.PROVIDER;
    }

    public static YamlParser createParser(
        Reader reader)
    {
        return provider().createParser(reader);
    }

    public static YamlParser createParser(
        InputStream in)
    {
        return provider().createParser(in);
    }

    public static YamlParserFactory createParserFactory(
        Map<String, ?> config)
    {
        return provider().createParserFactory(config);
    }

    public static YamlReader createReader(
        Reader reader)
    {
        return provider().createReader(reader);
    }

    public static YamlReader createReader(
        InputStream in)
    {
        return provider().createReader(in);
    }

    public static YamlReaderFactory createReaderFactory(
        Map<String, ?> config)
    {
        return provider().createReaderFactory(config);
    }

    public static YamlGenerator createGenerator(
        Writer writer)
    {
        return provider().createGenerator(writer);
    }

    public static YamlGenerator createGenerator(
        OutputStream out)
    {
        return provider().createGenerator(out);
    }

    public static YamlGeneratorFactory createGeneratorFactory(
        Map<String, ?> config)
    {
        return provider().createGeneratorFactory(config);
    }

    public static YamlWriter createWriter(
        Writer writer)
    {
        return provider().createWriter(writer);
    }

    public static YamlWriter createWriter(
        OutputStream out)
    {
        return provider().createWriter(out);
    }

    public static YamlWriterFactory createWriterFactory(
        Map<String, ?> config)
    {
        return provider().createWriterFactory(config);
    }

    private static final class ProviderHolder
    {
        private static final YamlProvider PROVIDER = new YamlProviderImpl();
    }
}
