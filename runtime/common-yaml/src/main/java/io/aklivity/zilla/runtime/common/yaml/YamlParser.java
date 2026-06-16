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

public interface YamlParser extends AutoCloseable
{
    boolean hasNext();

    YamlEvent next();

    YamlValue parse();

    /**
     * Non-owning, on-stack {@link CharSequence} view of the current scalar token — a string value, a
     * number lexeme, or a mapping key — valid only until the parser advances. The allocation-free peer
     * of {@link #getString()}: a streaming caller reads or matches the scalar without materializing a
     * {@code String}. The backing buffer is reused across events, so copy out anything retained beyond
     * the current event.
     */
    CharSequence getStringView();

    YamlValue getValue();

    YamlObject getObject();

    YamlArray getArray();

    YamlScalar getScalar();

    String getString();

    @Override
    void close();
}
