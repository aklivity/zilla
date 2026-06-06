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

import io.aklivity.zilla.runtime.common.yaml.YamlArray;
import io.aklivity.zilla.runtime.common.yaml.YamlObject;
import io.aklivity.zilla.runtime.common.yaml.YamlParser;
import io.aklivity.zilla.runtime.common.yaml.YamlReader;
import io.aklivity.zilla.runtime.common.yaml.YamlStructure;
import io.aklivity.zilla.runtime.common.yaml.YamlValue;

public final class YamlReaderImpl implements YamlReader
{
    private final YamlParser parser;

    public YamlReaderImpl(
        YamlParser parser)
    {
        this.parser = parser;
    }

    @Override
    public YamlStructure read()
    {
        YamlValue value = readValue();
        if (value instanceof YamlStructure structure)
        {
            return structure;
        }
        throw new IllegalStateException("YAML document is not a structure");
    }

    @Override
    public YamlObject readObject()
    {
        YamlValue value = readValue();
        if (value instanceof YamlObject object)
        {
            return object;
        }
        throw new IllegalStateException("YAML document is not an object");
    }

    @Override
    public YamlArray readArray()
    {
        YamlValue value = readValue();
        if (value instanceof YamlArray array)
        {
            return array;
        }
        throw new IllegalStateException("YAML document is not an array");
    }

    @Override
    public YamlValue readValue()
    {
        return parser.parse();
    }

    @Override
    public void close()
    {
        parser.close();
    }
}
