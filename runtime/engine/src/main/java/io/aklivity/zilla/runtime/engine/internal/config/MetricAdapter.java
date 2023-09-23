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
package io.aklivity.zilla.runtime.engine.internal.config;

import jakarta.json.Json;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.MetricConfig;

public class MetricAdapter implements JsonbAdapter<MetricConfig, JsonValue>
{
    @Override
    public JsonValue adaptToJson(
        MetricConfig metric)
    {
        return Json.createValue(metric.name);
    }

    @Override
    public MetricConfig adaptFromJson(
        JsonValue value)
    {
        String name = JsonString.class.cast(value).getString();
        String[] parts = name.split("\\.");
        String group = parts[0];

        return MetricConfig.builder()
            .group(group)
            .name(name)
            .build();
    }
}
