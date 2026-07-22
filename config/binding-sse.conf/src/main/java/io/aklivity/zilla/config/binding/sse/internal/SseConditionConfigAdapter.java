/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.config.binding.sse.internal;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.config.binding.sse.SseConditionConfig;
import io.aklivity.zilla.config.engine.ConditionConfig;

public final class SseConditionConfigAdapter implements JsonbAdapter<ConditionConfig, JsonObject>
{
    private static final String PATH_NAME = "path";

    @Override
    public JsonObject adaptToJson(
        ConditionConfig condition)
    {
        SseConditionConfig sseCondition = (SseConditionConfig) condition;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (sseCondition.path != null)
        {
            object.add(PATH_NAME, sseCondition.path);
        }

        return object.build();
    }

    @Override
    public ConditionConfig adaptFromJson(
        JsonObject object)
    {
        String path = object.containsKey(PATH_NAME)
                ? object.getString(PATH_NAME)
                : null;

        return SseConditionConfig.builder().path(path).build();
    }
}
