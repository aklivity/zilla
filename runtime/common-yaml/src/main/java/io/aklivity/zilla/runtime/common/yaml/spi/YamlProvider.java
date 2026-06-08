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
package io.aklivity.zilla.runtime.common.yaml.spi;

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

public abstract class YamlProvider
{
    protected YamlProvider()
    {
    }

    public abstract YamlParser createParser(
        Reader reader);

    public abstract YamlParser createParser(
        InputStream in);

    public abstract YamlParserFactory createParserFactory(
        Map<String, ?> config);

    public abstract YamlReader createReader(
        Reader reader);

    public abstract YamlReader createReader(
        InputStream in);

    public abstract YamlReaderFactory createReaderFactory(
        Map<String, ?> config);

    public abstract YamlGenerator createGenerator(
        Writer writer);

    public abstract YamlGenerator createGenerator(
        OutputStream out);

    public abstract YamlGeneratorFactory createGeneratorFactory(
        Map<String, ?> config);

    public abstract YamlWriter createWriter(
        Writer writer);

    public abstract YamlWriter createWriter(
        OutputStream out);

    public abstract YamlWriterFactory createWriterFactory(
        Map<String, ?> config);

    public abstract YamlObjectBuilder createObjectBuilder();

    public abstract YamlObjectBuilder createObjectBuilder(
        YamlObject object);

    public abstract YamlObjectBuilder createObjectBuilder(
        Map<String, ?> map);

    public abstract YamlArrayBuilder createArrayBuilder();

    public abstract YamlArrayBuilder createArrayBuilder(
        YamlArray array);

    public abstract YamlArrayBuilder createArrayBuilder(
        Collection<?> collection);

    public abstract YamlBuilderFactory createBuilderFactory(
        Map<String, ?> config);

    public abstract YamlValue createValue(
        String value);

    public abstract YamlValue createValue(
        BigDecimal value);

    public abstract YamlValue createValue(
        BigInteger value);

    public abstract YamlValue createValue(
        Number value);

    public abstract YamlValue createValue(
        int value);

    public abstract YamlValue createValue(
        long value);

    public abstract YamlValue createValue(
        double value);

    public abstract YamlValue createValue(
        boolean value);

    public abstract YamlValue createNullValue();
}
