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
package io.aklivity.zilla.runtime.exporter.otlp.internal.config;

import java.net.URI;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.exporter.otlp.config.OtlpOverridesConfig;

public class OtlpOverridesAdapter implements JsonbAdapter<OtlpOverridesConfig, JsonObject>
{
    private static final String METRICS_NAME = "metrics";

    @Override
    public JsonObject adaptToJson(
        OtlpOverridesConfig overrides)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();
        if (overrides.metrics != null)
        {
            object.add(METRICS_NAME, overrides.metrics.toString());
        }
        return object.build();
    }

    @Override
    public OtlpOverridesConfig adaptFromJson(
        JsonObject object)
    {
        URI metrics = object.containsKey(METRICS_NAME)
            ? URI.create(object.getString(METRICS_NAME))
            : null;
        return new OtlpOverridesConfig(metrics);
    }
}
