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

import io.aklivity.zilla.runtime.common.yaml.YamlGenerator;
import io.aklivity.zilla.runtime.common.yaml.YamlParser;
import io.aklivity.zilla.runtime.common.yaml.YamlReader;
import io.aklivity.zilla.runtime.common.yaml.YamlWriter;
import io.aklivity.zilla.runtime.common.yaml.spi.YamlProvider;

public final class YamlProviderImpl extends YamlProvider
{
    @Override
    public YamlParser createParser(
        Reader reader)
    {
        return new YamlParserImpl(reader);
    }

    @Override
    public YamlParser createParser(
        InputStream in)
    {
        return new YamlParserImpl(in);
    }

    @Override
    public YamlReader createReader(
        Reader reader)
    {
        return new YamlReaderImpl(createParser(reader));
    }

    @Override
    public YamlReader createReader(
        InputStream in)
    {
        return new YamlReaderImpl(createParser(in));
    }

    @Override
    public YamlGenerator createGenerator(
        Writer writer)
    {
        return new YamlGeneratorImpl(writer);
    }

    @Override
    public YamlGenerator createGenerator(
        OutputStream out)
    {
        return new YamlGeneratorImpl(out);
    }

    @Override
    public YamlWriter createWriter(
        Writer writer)
    {
        return new YamlWriterImpl(createGenerator(writer));
    }

    @Override
    public YamlWriter createWriter(
        OutputStream out)
    {
        return new YamlWriterImpl(createGenerator(out));
    }
}
