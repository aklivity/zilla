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

import io.aklivity.zilla.runtime.common.yaml.YamlGenerator;
import io.aklivity.zilla.runtime.common.yaml.YamlParser;
import io.aklivity.zilla.runtime.common.yaml.YamlReader;
import io.aklivity.zilla.runtime.common.yaml.YamlWriter;

public abstract class YamlProvider
{
    protected YamlProvider()
    {
    }

    public abstract YamlParser createParser(
        Reader reader);

    public abstract YamlParser createParser(
        InputStream in);

    public abstract YamlReader createReader(
        Reader reader);

    public abstract YamlReader createReader(
        InputStream in);

    public abstract YamlGenerator createGenerator(
        Writer writer);

    public abstract YamlGenerator createGenerator(
        OutputStream out);

    public abstract YamlWriter createWriter(
        Writer writer);

    public abstract YamlWriter createWriter(
        OutputStream out);
}
