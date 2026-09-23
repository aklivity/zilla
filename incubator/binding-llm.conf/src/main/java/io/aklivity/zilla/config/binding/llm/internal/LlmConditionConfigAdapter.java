/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.config.binding.llm.internal;

import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;

import io.aklivity.zilla.config.binding.llm.LlmConditionConfig;
import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.ConfigAdapter;

public final class LlmConditionConfigAdapter extends ConfigAdapter<ConditionConfig, JsonObject>
{
    private static final String DIALECT_NAME = "dialect";
    private static final String MODEL_NAME = "model";

    @Override
    public JsonObject adaptToJson(
        ConditionConfig condition)
    {
        LlmConditionConfig llmCondition = (LlmConditionConfig) condition;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (llmCondition.dialect != null)
        {
            object.add(DIALECT_NAME, llmCondition.dialect);
        }

        if (llmCondition.model != null)
        {
            JsonArrayBuilder model = Json.createArrayBuilder();
            llmCondition.model.forEach(model::add);
            object.add(MODEL_NAME, model);
        }

        return object.build();
    }

    @Override
    public ConditionConfig adaptFromJson(
        JsonObject object)
    {
        String dialect = object.containsKey(DIALECT_NAME)
            ? object.getString(DIALECT_NAME)
            : null;

        List<String> model = object.containsKey(MODEL_NAME)
            ? object.getJsonArray(MODEL_NAME).stream()
                .map(JsonString.class::cast)
                .map(JsonString::getString)
                .toList()
            : null;

        return LlmConditionConfig.builder()
            .dialect(dialect)
            .model(model)
            .build();
    }
}
