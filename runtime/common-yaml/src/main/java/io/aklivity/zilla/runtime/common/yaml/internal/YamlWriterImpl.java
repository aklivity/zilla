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
import io.aklivity.zilla.runtime.common.yaml.YamlGenerator;
import io.aklivity.zilla.runtime.common.yaml.YamlObject;
import io.aklivity.zilla.runtime.common.yaml.YamlValue;
import io.aklivity.zilla.runtime.common.yaml.YamlWriter;

public final class YamlWriterImpl implements YamlWriter
{
    private final YamlGenerator generator;

    public YamlWriterImpl(
        YamlGenerator generator)
    {
        this.generator = generator;
    }

    @Override
    public void write(
        YamlValue value)
    {
        generator.write(value).close();
    }

    @Override
    public void writeObject(
        YamlObject object)
    {
        write(object);
    }

    @Override
    public void writeArray(
        YamlArray array)
    {
        write(array);
    }

    @Override
    public void close()
    {
        generator.close();
    }
}
