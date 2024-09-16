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

import io.aklivity.zilla.runtime.binding.risingwave.config.RisingwaveUdfConfig;
import io.aklivity.zilla.runtime.binding.risingwave.config.RisingwaveUdfConfigBuilder;

public final class RisingwaveUdfConfigAdapter implements JsonbAdapter<RisingwaveUdfConfig, JsonObject>
{
    private static final String URL_NAME = "url";

    @Override
    public JsonObject adaptToJson(
        RisingwaveUdfConfig udf)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();

        if (udf.url != null)
        {
            object.add(URL_NAME, udf.url);
        }

        return object.build();
    }

    @Override
    public RisingwaveUdfConfig adaptFromJson(
        JsonObject object)
    {
        RisingwaveUdfConfigBuilder<RisingwaveUdfConfig> builder = RisingwaveUdfConfig.builder();

        if (object.containsKey(URL_NAME))
        {
            builder.url(object.getString(URL_NAME));
        }

        return builder.build();
    }
}
