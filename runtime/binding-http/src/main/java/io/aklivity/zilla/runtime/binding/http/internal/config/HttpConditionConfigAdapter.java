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
package io.aklivity.zilla.runtime.binding.http.internal.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.http.config.HttpConditionConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpConditionConfigBuilder;
import io.aklivity.zilla.runtime.binding.http.internal.HttpBinding;
import io.aklivity.zilla.runtime.engine.config.ConditionConfig;
import io.aklivity.zilla.runtime.engine.config.ConditionConfigAdapterSpi;

public final class HttpConditionConfigAdapter implements ConditionConfigAdapterSpi, JsonbAdapter<ConditionConfig, JsonObject>
{
    private static final String HEADERS_NAME = "headers";

    @Override
    public String type()
    {
        return HttpBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        ConditionConfig condition)
    {
        HttpConditionConfig httpCondition = (HttpConditionConfig) condition;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (httpCondition.headers != null &&
            !httpCondition.headers.isEmpty())
        {
            JsonObjectBuilder entries = Json.createObjectBuilder();
            httpCondition.headers.forEach(entries::add);

            object.add(HEADERS_NAME, entries);
        }

        return object.build();
    }

    @Override
    public ConditionConfig adaptFromJson(
        JsonObject object)
    {
        HttpConditionConfigBuilder<HttpConditionConfig> httpCondition = HttpConditionConfig.builder();

        if (object.containsKey(HEADERS_NAME))
        {
            object.getJsonObject(HEADERS_NAME)
                .forEach((k, v) -> httpCondition.header(k, JsonString.class.cast(v).getString()));
        }

        return httpCondition.build();
    }
}
