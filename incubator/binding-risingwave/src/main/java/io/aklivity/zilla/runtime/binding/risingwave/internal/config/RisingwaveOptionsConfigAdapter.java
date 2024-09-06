/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.risingwave.internal.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.risingwave.config.RisingwaveOptionConfigBuilder;
import io.aklivity.zilla.runtime.binding.risingwave.config.RisingwaveOptionsConfig;
import io.aklivity.zilla.runtime.binding.risingwave.internal.RisingwaveBinding;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public final class RisingwaveOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String KAFKA_NAME = "kafka";

    private final RisingwaveKafkaConfigAdapter kafka = new RisingwaveKafkaConfigAdapter();

    @Override
    public Kind kind()
    {
        return Kind.BINDING;
    }

    @Override
    public String type()
    {
        return RisingwaveBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        RisingwaveOptionsConfig risingwaveOptions = (RisingwaveOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (risingwaveOptions.kafka != null)
        {
            object.add(KAFKA_NAME, kafka.adaptToJson(risingwaveOptions.kafka));
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        final RisingwaveOptionConfigBuilder<RisingwaveOptionsConfig> builder = RisingwaveOptionsConfig.builder();
        if (object.containsKey(KAFKA_NAME))
        {
            builder.kafka(kafka.adaptFromJson(object.getJsonObject(KAFKA_NAME)));
        }

        return builder.build();
    }
}
