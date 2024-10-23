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
package io.aklivity.zilla.runtime.binding.risingwave.internal.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.risingwave.config.RisingwaveUdfConfig;
import io.aklivity.zilla.runtime.binding.risingwave.config.RisingwaveUdfConfigBuilder;

public final class RisingwaveUdfConfigAdapter implements JsonbAdapter<RisingwaveUdfConfig, JsonObject>
{
    private static final String SERVER_NAME = "server";
    private static final String LANGUAGE_NAME = "language";

    @Override
    public JsonObject adaptToJson(
        RisingwaveUdfConfig udf)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();

        if (udf.server != null)
        {
            object.add(SERVER_NAME, udf.server);
        }

        if (udf.language != null)
        {
            object.add(LANGUAGE_NAME, udf.language);
        }

        return object.build();
    }

    @Override
    public RisingwaveUdfConfig adaptFromJson(
        JsonObject object)
    {
        RisingwaveUdfConfigBuilder<RisingwaveUdfConfig> builder = RisingwaveUdfConfig.builder();

        if (object.containsKey(SERVER_NAME))
        {
            builder.server(object.getString(SERVER_NAME));
        }

        if (object.containsKey(LANGUAGE_NAME))
        {
            builder.language(object.getString(LANGUAGE_NAME));
        }

        return builder.build();
    }
}
