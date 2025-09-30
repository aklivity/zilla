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
package io.aklivity.zilla.runtime.exporter.otlp.internal.config;

import java.net.URI;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.exporter.otlp.config.OtlpOverridesConfig;
import io.aklivity.zilla.runtime.exporter.otlp.config.OtlpOverridesConfigBuilder;

public class OtlpOverridesAdapter implements JsonbAdapter<OtlpOverridesConfig, JsonObject>
{
    private static final String METRICS_NAME = "metrics";
    private static final String LOGS_NAME = "logs";

    @Override
    public JsonObject adaptToJson(
        OtlpOverridesConfig overrides)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();
        if (overrides.metrics != null)
        {
            object.add(METRICS_NAME, overrides.metrics.toString());
        }
        if (overrides.logs != null)
        {
            object.add(LOGS_NAME, overrides.logs.toString());
        }
        return object.build();
    }

    @Override
    public OtlpOverridesConfig adaptFromJson(
        JsonObject object)
    {
        OtlpOverridesConfigBuilder<OtlpOverridesConfig> builder = OtlpOverridesConfig.builder();
        if (object.containsKey(METRICS_NAME))
        {
            builder.metrics(URI.create(object.getString(METRICS_NAME)));
        }

        if (object.containsKey(LOGS_NAME))
        {
            builder.logs(URI.create(object.getString(LOGS_NAME)));
        }

        return builder.build();
    }
}
