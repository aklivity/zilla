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

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.factory.Factory;

public final class EventFormatterFactory extends Factory
{
    private final Map<String, EventFormatterFactorySpi> factories;

    public EventFormatter create(
        Configuration config,
        EngineContext context)
    {
        Long2ObjectHashMap<EventFormatterSpi> formatters = new Long2ObjectHashMap<>();
        for (Map.Entry<String, EventFormatterFactorySpi> entry : factories.entrySet())
        {
            String type = entry.getKey();

            EventFormatterFactorySpi factory = entry.getValue();
            EventFormatterSpi formatter = factory.create(config);

            int typeId = context.supplyTypeId(type);
            formatters.put(typeId, formatter);
        }
        return new EventFormatter(formatters);
    }

    public static EventFormatterFactory instantiate()
    {
        return instantiate(load(EventFormatterFactorySpi.class), EventFormatterFactory::new);
    }

    private EventFormatterFactory(
        Map<String, EventFormatterFactorySpi> factories)
    {
        this.factories = factories;
    }
}
