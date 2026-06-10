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

public interface YamlArray extends YamlStructure
{
    int size();

    YamlValue get(
        int index);

    YamlObject getObject(
        int index);

    YamlArray getArray(
        int index);

    YamlScalar getScalar(
        int index);

    String getString(
        int index);

    int getInt(
        int index);

    long getLong(
        int index);

    double getDouble(
        int index);

    boolean getBoolean(
        int index);

    boolean isNull(
        int index);
}
