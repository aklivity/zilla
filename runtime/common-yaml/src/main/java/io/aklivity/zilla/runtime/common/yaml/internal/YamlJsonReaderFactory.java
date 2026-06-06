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

import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Map;

import jakarta.json.JsonReader;
import jakarta.json.JsonReaderFactory;

public final class YamlJsonReaderFactory implements JsonReaderFactory
{
    private final Map<String, ?> config;

    public YamlJsonReaderFactory(
        Map<String, ?> config)
    {
        this.config = config;
    }

    @Override
    public JsonReader createReader(
        Reader reader)
    {
        return new YamlJsonReader(new YamlJsonParser(reader));
    }

    @Override
    public JsonReader createReader(
        InputStream in)
    {
        return createReader(in, UTF_8);
    }

    @Override
    public JsonReader createReader(
        InputStream in,
        Charset charset)
    {
        return new YamlJsonReader(new YamlJsonParser(in, charset));
    }

    @Override
    public Map<String, ?> getConfigInUse()
    {
        return config;
    }
}
