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

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Map;

import io.aklivity.zilla.runtime.common.yaml.YamlArray;
import io.aklivity.zilla.runtime.common.yaml.YamlArrayBuilder;
import io.aklivity.zilla.runtime.common.yaml.YamlBuilderFactory;
import io.aklivity.zilla.runtime.common.yaml.YamlGenerator;
import io.aklivity.zilla.runtime.common.yaml.YamlGeneratorFactory;
import io.aklivity.zilla.runtime.common.yaml.YamlObject;
import io.aklivity.zilla.runtime.common.yaml.YamlObjectBuilder;
import io.aklivity.zilla.runtime.common.yaml.YamlParser;
import io.aklivity.zilla.runtime.common.yaml.YamlParserFactory;
import io.aklivity.zilla.runtime.common.yaml.YamlReader;
import io.aklivity.zilla.runtime.common.yaml.YamlReaderFactory;
import io.aklivity.zilla.runtime.common.yaml.YamlValue;
import io.aklivity.zilla.runtime.common.yaml.YamlWriter;
import io.aklivity.zilla.runtime.common.yaml.YamlWriterFactory;
import io.aklivity.zilla.runtime.common.yaml.spi.YamlProvider;

public final class YamlProviderImpl extends YamlProvider
{
    @Override
    public YamlParser createParser(
        Reader reader)
    {
        return createParserFactory(Map.of()).createParser(reader);
    }

    @Override
    public YamlParser createParser(
        InputStream in)
    {
        return createParserFactory(Map.of()).createParser(in);
    }

    @Override
    public YamlParserFactory createParserFactory(
        Map<String, ?> config)
    {
        return new YamlParserFactoryImpl(config);
    }

    @Override
    public YamlReader createReader(
        Reader reader)
    {
        return createReaderFactory(Map.of()).createReader(reader);
    }

    @Override
    public YamlReader createReader(
        InputStream in)
    {
        return createReaderFactory(Map.of()).createReader(in);
    }

    @Override
    public YamlReaderFactory createReaderFactory(
        Map<String, ?> config)
    {
        return new YamlReaderFactoryImpl(config);
    }

    @Override
    public YamlGenerator createGenerator(
        Writer writer)
    {
        return createGeneratorFactory(Map.of()).createGenerator(writer);
    }

    @Override
    public YamlGenerator createGenerator(
        OutputStream out)
    {
        return createGeneratorFactory(Map.of()).createGenerator(out);
    }

    @Override
    public YamlGeneratorFactory createGeneratorFactory(
        Map<String, ?> config)
    {
        return new YamlGeneratorFactoryImpl(config);
    }

    @Override
    public YamlWriter createWriter(
        Writer writer)
    {
        return createWriterFactory(Map.of()).createWriter(writer);
    }

    @Override
    public YamlWriter createWriter(
        OutputStream out)
    {
        return createWriterFactory(Map.of()).createWriter(out);
    }

    @Override
    public YamlWriterFactory createWriterFactory(
        Map<String, ?> config)
    {
        return new YamlWriterFactoryImpl(config);
    }

    @Override
    public YamlObjectBuilder createObjectBuilder()
    {
        return createBuilderFactory(Map.of()).createObjectBuilder();
    }

    @Override
    public YamlObjectBuilder createObjectBuilder(
        YamlObject object)
    {
        return createBuilderFactory(Map.of()).createObjectBuilder(object);
    }

    @Override
    public YamlObjectBuilder createObjectBuilder(
        Map<String, ?> map)
    {
        return createBuilderFactory(Map.of()).createObjectBuilder(map);
    }

    @Override
    public YamlArrayBuilder createArrayBuilder()
    {
        return createBuilderFactory(Map.of()).createArrayBuilder();
    }

    @Override
    public YamlArrayBuilder createArrayBuilder(
        YamlArray array)
    {
        return createBuilderFactory(Map.of()).createArrayBuilder(array);
    }

    @Override
    public YamlArrayBuilder createArrayBuilder(
        Collection<?> collection)
    {
        return createBuilderFactory(Map.of()).createArrayBuilder(collection);
    }

    @Override
    public YamlBuilderFactory createBuilderFactory(
        Map<String, ?> config)
    {
        return new YamlBuilderFactoryImpl(config);
    }

    @Override
    public YamlValue createValue(
        String value)
    {
        return YamlBuilderFactoryImpl.value(value);
    }

    @Override
    public YamlValue createValue(
        BigDecimal value)
    {
        return YamlBuilderFactoryImpl.value(value);
    }

    @Override
    public YamlValue createValue(
        BigInteger value)
    {
        return YamlBuilderFactoryImpl.value(value);
    }

    @Override
    public YamlValue createValue(
        Number value)
    {
        return YamlBuilderFactoryImpl.value(value);
    }

    @Override
    public YamlValue createValue(
        int value)
    {
        return YamlBuilderFactoryImpl.value(value);
    }

    @Override
    public YamlValue createValue(
        long value)
    {
        return YamlBuilderFactoryImpl.value(value);
    }

    @Override
    public YamlValue createValue(
        double value)
    {
        return YamlBuilderFactoryImpl.value(value);
    }

    @Override
    public YamlValue createValue(
        boolean value)
    {
        return YamlBuilderFactoryImpl.value(value);
    }

    @Override
    public YamlValue createNullValue()
    {
        return YamlBuilderFactoryImpl.nullValue();
    }
}
