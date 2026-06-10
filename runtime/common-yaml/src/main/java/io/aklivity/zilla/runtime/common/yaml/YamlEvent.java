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

public final class YamlEvent
{
    private final EventType eventType;
    private final String string;
    private final YamlValue value;

    public YamlEvent(
        EventType eventType,
        String string,
        YamlValue value)
    {
        this.eventType = eventType;
        this.string = string;
        this.value = value;
    }

    public EventType getEventType()
    {
        return eventType;
    }

    public String getString()
    {
        return string;
    }

    public YamlValue getValue()
    {
        return value;
    }

    public enum EventType
    {
        START_OBJECT,
        END_OBJECT,
        START_ARRAY,
        END_ARRAY,
        KEY_NAME,
        VALUE_STRING,
        VALUE_NUMBER,
        VALUE_TRUE,
        VALUE_FALSE,
        VALUE_NULL
    }
}
