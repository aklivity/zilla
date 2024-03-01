/*
 * Copyright 2021-2023 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine.event;

import static java.util.ServiceLoader.load;

import java.util.Map;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.engine.factory.Factory;

public final class EventFormatter extends Factory
{
    private final Map<String, EventFormatterSpi> formatters;

    public static EventFormatter instantiate()
    {
        return instantiate(load(EventFormatterSpi.class), EventFormatter::new);
    }

    public String format(
        String type,
        DirectBuffer buffer,
        int index,
        int length)
    {
        String result = null;
        if (formatters.containsKey(type))
        {
            result = formatters.get(type).format(buffer, index, length);
        }
        return result;
    }

    private EventFormatter(
        Map<String, EventFormatterSpi> formatters)
    {
        this.formatters = formatters;
    }
}
