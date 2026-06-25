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
package io.aklivity.zilla.runtime.common.json.internal.json;

import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Map;

import jakarta.json.JsonWriter;
import jakarta.json.JsonWriterFactory;

public final class JsonWriterFactoryImpl implements JsonWriterFactory
{
    private final JsonGeneratorFactoryImpl generators;

    public JsonWriterFactoryImpl(
        Map<String, ?> config)
    {
        this.generators = new JsonGeneratorFactoryImpl(config);
    }

    @Override
    public JsonWriter createWriter(
        Writer writer)
    {
        return new JsonWriterImpl(generators.createGenerator(writer));
    }

    @Override
    public JsonWriter createWriter(
        OutputStream out)
    {
        return createWriter(out, UTF_8);
    }

    @Override
    public JsonWriter createWriter(
        OutputStream out,
        Charset charset)
    {
        return new JsonWriterImpl(generators.createGenerator(out, charset));
    }

    @Override
    public Map<String, ?> getConfigInUse()
    {
        return generators.getConfigInUse();
    }
}
