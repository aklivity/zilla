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
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
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

    public static YamlObjectBuilder createObjectBuilder()
    {
        return provider().createObjectBuilder();
    }

    public static YamlObjectBuilder createObjectBuilder(
        YamlObject object)
    {
        return provider().createObjectBuilder(object);
    }

    public static YamlObjectBuilder createObjectBuilder(
        Map<String, ?> map)
    {
        return provider().createObjectBuilder(map);
    }

    public static YamlArrayBuilder createArrayBuilder()
    {
        return provider().createArrayBuilder();
    }

    public static YamlArrayBuilder createArrayBuilder(
        YamlArray array)
    {
        return provider().createArrayBuilder(array);
    }

    public static YamlArrayBuilder createArrayBuilder(
        Collection<?> collection)
    {
        return provider().createArrayBuilder(collection);
    }

    public static YamlBuilderFactory createBuilderFactory(
        Map<String, ?> config)
    {
        return provider().createBuilderFactory(config);
    }

    public static YamlValue createValue(
        String value)
    {
        return provider().createValue(value);
    }

    public static YamlValue createValue(
        BigDecimal value)
    {
        return provider().createValue(value);
    }

    public static YamlValue createValue(
        BigInteger value)
    {
        return provider().createValue(value);
    }

    public static YamlValue createValue(
        Number value)
    {
        return provider().createValue(value);
    }

    public static YamlValue createValue(
        int value)
    {
        return provider().createValue(value);
    }

    public static YamlValue createValue(
        long value)
    {
        return provider().createValue(value);
    }

    public static YamlValue createValue(
        double value)
    {
        return provider().createValue(value);
    }

    public static YamlValue createValue(
        boolean value)
    {
        return provider().createValue(value);
    }

    public static YamlValue createNullValue()
    {
        return provider().createNullValue();
    }

    private static final class ProviderHolder
    {
        private static final YamlProvider PROVIDER = new YamlProviderImpl();
    }
}
