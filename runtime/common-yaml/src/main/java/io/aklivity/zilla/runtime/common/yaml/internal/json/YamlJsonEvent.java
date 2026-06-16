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
package io.aklivity.zilla.runtime.common.yaml.internal.json;

import jakarta.json.stream.JsonParser;

import io.aklivity.zilla.runtime.common.yaml.internal.YamlLocation;
import io.aklivity.zilla.runtime.common.yaml.internal.YamlNode;

final class YamlJsonEvent
{
    final JsonParser.Event event;
    final String value;
    final YamlNode node;

    private final int line;
    private final int column;
    private final long offset;
    private YamlJsonLocation location;

    YamlJsonEvent(
        JsonParser.Event event,
        String value,
        int line,
        int column,
        long offset)
    {
        this(event, value, null, line, column, offset);
    }

    YamlJsonEvent(
        JsonParser.Event event,
        String value,
        YamlNode node,
        int line,
        int column,
        long offset)
    {
        this.event = event;
        this.value = value;
        this.node = node;
        this.line = line;
        this.column = column;
        this.offset = offset;
    }

    YamlJsonEvent(
        JsonParser.Event event,
        String value,
        YamlNode node,
        YamlJsonLocation location)
    {
        this.event = event;
        this.value = value;
        this.node = node;
        this.location = location;
        this.line = (int) location.getLineNumber();
        this.column = (int) location.getColumnNumber();
        this.offset = location.getStreamOffset();
    }

    YamlJsonLocation location()
    {
        if (location == null)
        {
            location = new YamlJsonLocation(new YamlLocation(line, column, offset));
        }
        return location;
    }
}
