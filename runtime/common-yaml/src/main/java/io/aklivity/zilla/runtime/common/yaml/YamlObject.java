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

public interface YamlObject extends YamlStructure
{
    boolean containsKey(
        String name);

    int size();

    YamlObject getObject(
        String name);

    YamlArray getArray(
        String name);

    YamlScalar getScalar(
        String name);

    String getString(
        String name);

    String getString(
        String name,
        String defaultValue);

    int getInt(
        String name);

    long getLong(
        String name);

    double getDouble(
        String name);

    boolean getBoolean(
        String name);

    boolean isNull(
        String name);
}
