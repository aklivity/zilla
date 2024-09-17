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

import io.aklivity.zilla.runtime.binding.risingwave.config.RisingwaveKafkaPropertiesConfig;
import io.aklivity.zilla.runtime.binding.risingwave.config.RisingwaveKafkaPropertiesConfigBuilder;

public final class RisingwaveKafkaPropertiesConfigAdapter implements JsonbAdapter<RisingwaveKafkaPropertiesConfig, JsonObject>
{
    private static final String BOOTSTRAP_SERVER_NAME = "bootstrap.server";

    @Override
    public JsonObject adaptToJson(
        RisingwaveKafkaPropertiesConfig properties)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();

        if (properties.bootstrapServer != null)
        {
            object.add(BOOTSTRAP_SERVER_NAME, properties.bootstrapServer);
        }

        return object.build();
    }

    @Override
    public RisingwaveKafkaPropertiesConfig adaptFromJson(
        JsonObject object)
    {
        RisingwaveKafkaPropertiesConfigBuilder<RisingwaveKafkaPropertiesConfig> builder =
            RisingwaveKafkaPropertiesConfig.builder();

        builder.bootstrapServer(object.getString(BOOTSTRAP_SERVER_NAME, null));

        return builder.build();
    }
}
