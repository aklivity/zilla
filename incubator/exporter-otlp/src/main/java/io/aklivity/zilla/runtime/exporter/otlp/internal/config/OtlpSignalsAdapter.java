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

import static io.aklivity.zilla.runtime.exporter.otlp.internal.config.OtlpSignalsConfig.Signals.METRICS;

import java.util.Set;
import java.util.TreeSet;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

public class OtlpSignalsAdapter implements JsonbAdapter<OtlpSignalsConfig, JsonArray>
{
    private static final String METRICS_NAME = "metrics";

    @Override
    public JsonArray adaptToJson(
        OtlpSignalsConfig signals)
    {
        JsonArrayBuilder array = Json.createArrayBuilder();
        signals.signals.forEach(signal -> array.add(Json.createValue(signal.name().toLowerCase())));
        return array.build();
    }

    @Override
    public OtlpSignalsConfig adaptFromJson(
        JsonArray array)
    {
        Set<OtlpSignalsConfig.Signals> signals = new TreeSet<>();
        if (array.contains(Json.createValue(METRICS_NAME)))
        {
            signals.add(METRICS);
        }
        return new OtlpSignalsConfig(signals);
    }
}
