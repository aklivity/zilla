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
package io.aklivity.zilla.runtime.common.yaml;

import java.util.Iterator;
import java.util.List;

public interface YamlObject extends YamlStructure, Iterable<YamlEntry>
{
    List<YamlEntry> entries();

    YamlValue get(
        String name);

    default YamlObject getObject(
        String name)
    {
        return get(name).asYamlObject();
    }

    default YamlArray getArray(
        String name)
    {
        return get(name).asYamlArray();
    }

    default YamlScalar getScalar(
        String name)
    {
        return get(name).asYamlScalar();
    }

    default String getString(
        String name)
    {
        return getScalar(name).getString();
    }

    default String getString(
        String name,
        String defaultValue)
    {
        YamlValue value = get(name);
        return value != null ? value.asYamlScalar().getString() : defaultValue;
    }

    default int getInt(
        String name)
    {
        return Integer.parseInt(getString(name));
    }

    default long getLong(
        String name)
    {
        return Long.parseLong(getString(name));
    }

    default double getDouble(
        String name)
    {
        return Double.parseDouble(getString(name));
    }

    default boolean getBoolean(
        String name)
    {
        return Boolean.parseBoolean(getString(name));
    }

    default boolean isNull(
        String name)
    {
        YamlValue value = get(name);
        return value != null && value.getValueType() == YamlValue.ValueType.NULL;
    }

    default boolean containsKey(
        String name)
    {
        return get(name) != null;
    }

    default int size()
    {
        return entries().size();
    }

    @Override
    default Iterator<YamlEntry> iterator()
    {
        return entries().iterator();
    }
}
