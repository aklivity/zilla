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
package io.aklivity.zilla.runtime.binding.http.kafka.internal.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaConditionConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.HttpKafkaBinding;
import io.aklivity.zilla.runtime.engine.config.ConditionConfig;
import io.aklivity.zilla.runtime.engine.config.ConditionConfigAdapterSpi;

public final class HttpKafkaConditionConfigAdapter implements ConditionConfigAdapterSpi, JsonbAdapter<ConditionConfig, JsonObject>
{
    private static final String METHOD_NAME = "method";
    private static final String PATH_NAME = "path";

    @Override
    public String type()
    {
        return HttpKafkaBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        ConditionConfig condition)
    {
        HttpKafkaConditionConfig httpCondition = (HttpKafkaConditionConfig) condition;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (httpCondition.method != null)
        {
            object.add(METHOD_NAME, httpCondition.method);
        }

        if (httpCondition.path != null)
        {
            object.add(PATH_NAME, httpCondition.path);
        }

        return object.build();
    }

    @Override
    public ConditionConfig adaptFromJson(
        JsonObject object)
    {
        String method = object.containsKey(METHOD_NAME)
                ? object.getString(METHOD_NAME)
                : null;

        String path = object.containsKey(PATH_NAME)
                ? object.getString(PATH_NAME)
                : null;

        return new HttpKafkaConditionConfig(method, path);
    }
}
