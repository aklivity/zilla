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

import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Map;

import io.aklivity.zilla.runtime.common.yaml.YamlWriter;
import io.aklivity.zilla.runtime.common.yaml.YamlWriterFactory;

public final class YamlWriterFactoryImpl implements YamlWriterFactory
{
    private final YamlGeneratorFactoryImpl generators;

    public YamlWriterFactoryImpl(
        Map<String, ?> config)
    {
        this.generators = new YamlGeneratorFactoryImpl(config);
    }

    @Override
    public YamlWriter createWriter(
        Writer writer)
    {
        return new YamlWriterImpl(generators.createGenerator(writer));
    }

    @Override
    public YamlWriter createWriter(
        OutputStream out)
    {
        return new YamlWriterImpl(generators.createGenerator(out));
    }

    @Override
    public YamlWriter createWriter(
        OutputStream out,
        Charset charset)
    {
        return new YamlWriterImpl(generators.createGenerator(out, charset));
    }

    @Override
    public Map<String, ?> getConfigInUse()
    {
        return generators.getConfigInUse();
    }
}
