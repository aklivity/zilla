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

public interface YamlGenerator extends AutoCloseable
{
    YamlGenerator writeStartObject();

    YamlGenerator writeStartObject(
        String name);

    YamlGenerator writeStartArray();

    YamlGenerator writeStartArray(
        String name);

    YamlGenerator writeKey(
        String name);

    YamlGenerator write(
        String value);

    YamlGenerator write(
        String name,
        String value);

    YamlGenerator write(
        int value);

    YamlGenerator write(
        String name,
        int value);

    YamlGenerator write(
        long value);

    YamlGenerator write(
        String name,
        long value);

    YamlGenerator write(
        double value);

    YamlGenerator write(
        String name,
        double value);

    YamlGenerator write(
        boolean value);

    YamlGenerator write(
        String name,
        boolean value);

    YamlGenerator writeNull();

    YamlGenerator writeNull(
        String name);

    YamlGenerator write(
        YamlValue value);

    YamlGenerator write(
        String name,
        YamlValue value);

    YamlGenerator writeEnd();

    void flush();

    @Override
    void close();
}
