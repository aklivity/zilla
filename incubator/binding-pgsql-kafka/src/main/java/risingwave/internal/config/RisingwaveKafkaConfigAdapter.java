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
package risingwave.internal.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.risingwave.config.RisingwaveKafkaConfig;
import io.aklivity.zilla.runtime.binding.risingwave.config.RisingwaveKafkaConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.ModelConfigAdapter;

public final class RisingwaveKafkaConfigAdapter implements JsonbAdapter<RisingwaveKafkaConfig, JsonObject>
{
    private static final String PROPERTIES_NAME = "properties";
    private static final String FORMAT_NAME = "format";

    private final RisingwaveKafkaPropertiesConfigAdapter properties = new RisingwaveKafkaPropertiesConfigAdapter();
    private final ModelConfigAdapter model = new ModelConfigAdapter();

    @Override
    public JsonObject adaptToJson(
        RisingwaveKafkaConfig kafka)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();

        if (kafka.properties != null)
        {
            object.add(PROPERTIES_NAME, properties.adaptToJson(kafka.properties));
        }

        if (kafka.format != null)
        {
            model.adaptType(kafka.format.model);

            object.add(FORMAT_NAME, model.adaptToJson(kafka.format));
        }

        return object.build();
    }

    @Override
    public RisingwaveKafkaConfig adaptFromJson(
        JsonObject object)
    {
        RisingwaveKafkaConfigBuilder<RisingwaveKafkaConfig> builder = RisingwaveKafkaConfig.builder();

        if (object.containsKey(PROPERTIES_NAME))
        {
            builder.properties(properties.adaptFromJson(object.getJsonObject(PROPERTIES_NAME)));
        }

        if (object.containsKey(FORMAT_NAME))
        {
            builder.format(model.adaptFromJson(object.getJsonObject(FORMAT_NAME)));
        }

        return builder.build();
    }
}
