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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Map;

import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonGeneratorFactory;

public final class JsonpGeneratorFactory implements JsonGeneratorFactory
{
    private final Map<String, ?> config;

    public JsonpGeneratorFactory(
        Map<String, ?> config)
    {
        this.config = config == null ? Map.of() : Map.copyOf(config);
    }

    @Override
    public JsonGenerator createGenerator(
        Writer writer)
    {
        return new JsonTextGenerator(writer);
    }

    @Override
    public JsonGenerator createGenerator(
        OutputStream out)
    {
        return new JsonTextGenerator(new OutputStreamWriter(out, UTF_8));
    }

    @Override
    public JsonGenerator createGenerator(
        OutputStream out,
        Charset charset)
    {
        return new JsonTextGenerator(new OutputStreamWriter(out, charset));
    }

    @Override
    public Map<String, ?> getConfigInUse()
    {
        return config;
    }
}
