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

final class YamlScalarNode extends YamlNode
{
    final YamlScalarType type;
    final String value;

    YamlScalarNode(
        YamlScalarType type,
        String value,
        int line,
        int column,
        long offset)
    {
        super(line, column, offset);
        this.type = type;
        this.value = value;
    }

    static YamlScalarNode string(
        String value,
        int line,
        int column,
        long offset)
    {
        return new YamlScalarNode(YamlScalarType.STRING, value, line, column, offset);
    }

    static YamlScalarNode number(
        String value,
        int line,
        int column,
        long offset)
    {
        return new YamlScalarNode(YamlScalarType.NUMBER, value, line, column, offset);
    }

    static YamlScalarNode literal(
        YamlScalarType type,
        int line,
        int column,
        long offset)
    {
        return new YamlScalarNode(type, null, line, column, offset);
    }
}
