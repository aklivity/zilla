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
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Map;

import io.aklivity.zilla.runtime.common.yaml.YamlReader;
import io.aklivity.zilla.runtime.common.yaml.YamlReaderFactory;

public final class YamlReaderFactoryImpl implements YamlReaderFactory
{
    private final YamlParserFactoryImpl parsers;

    public YamlReaderFactoryImpl(
        Map<String, ?> config)
    {
        this.parsers = new YamlParserFactoryImpl(config);
    }

    @Override
    public YamlReader createReader(
        Reader reader)
    {
        return new YamlReaderImpl(parsers.createParser(reader));
    }

    @Override
    public YamlReader createReader(
        InputStream in)
    {
        return new YamlReaderImpl(parsers.createParser(in));
    }

    @Override
    public YamlReader createReader(
        InputStream in,
        Charset charset)
    {
        return new YamlReaderImpl(parsers.createParser(in, charset));
    }

    @Override
    public Map<String, ?> getConfigInUse()
    {
        return parsers.getConfigInUse();
    }
}
