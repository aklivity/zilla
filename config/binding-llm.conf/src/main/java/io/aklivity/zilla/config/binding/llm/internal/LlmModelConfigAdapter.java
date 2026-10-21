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

import jakarta.json.Json;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import io.aklivity.zilla.config.binding.llm.LlmModelConfig;
import io.aklivity.zilla.config.binding.llm.LlmModelConfigBuilder;
import io.aklivity.zilla.config.engine.ConfigAdapter;

public final class LlmModelConfigAdapter extends ConfigAdapter<LlmModelConfig, JsonObject>
{
    private static final String SAMPLING_NAME = "sampling";
    private static final String THINKING_NAME = "thinking";
    private static final String BUDGET_NAME = "budget";
    private static final String BUDGET_MINIMUM_NAME = "minimum";
    private static final String BUDGET_MAXIMUM_NAME = "maximum";
    private static final String LADDER_NAME = "ladder";

    @Override
    public JsonObject adaptToJson(
        LlmModelConfig model)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();

        if (model.sampling != null)
        {
            object.add(SAMPLING_NAME, model.sampling);
        }

        if (model.thinking != null)
        {
            object.add(THINKING_NAME, model.thinking);
        }

        if (model.budgetMinimum != null || model.budgetMaximum != null)
        {
            JsonObjectBuilder budget = Json.createObjectBuilder();

            if (model.budgetMinimum != null)
            {
                budget.add(BUDGET_MINIMUM_NAME, model.budgetMinimum);
            }

            if (model.budgetMaximum != null)
            {
                budget.add(BUDGET_MAXIMUM_NAME, model.budgetMaximum);
            }

            object.add(BUDGET_NAME, budget);
        }

        if (!model.ladder.isEmpty())
        {
            JsonObjectBuilder ladder = Json.createObjectBuilder();
            model.ladder.forEach(ladder::add);
            object.add(LADDER_NAME, ladder);
        }

        return object.build();
    }

    @Override
    public LlmModelConfig adaptFromJson(
        JsonObject object)
    {
        return adaptFromJson(null, object);
    }

    public LlmModelConfig adaptFromJson(
        String name,
        JsonObject object)
    {
        LlmModelConfigBuilder<LlmModelConfig> model = LlmModelConfig.builder().name(name);

        if (object.containsKey(SAMPLING_NAME))
        {
            model.sampling(object.getBoolean(SAMPLING_NAME));
        }

        if (object.containsKey(THINKING_NAME))
        {
            model.thinking(object.getString(THINKING_NAME));
        }

        if (object.containsKey(BUDGET_NAME))
        {
            JsonObject budget = object.getJsonObject(BUDGET_NAME);

            if (budget.containsKey(BUDGET_MINIMUM_NAME))
            {
                model.budgetMinimum(budget.getInt(BUDGET_MINIMUM_NAME));
            }

            if (budget.containsKey(BUDGET_MAXIMUM_NAME))
            {
                model.budgetMaximum(budget.getInt(BUDGET_MAXIMUM_NAME));
            }
        }

        if (object.containsKey(LADDER_NAME))
        {
            object.getJsonObject(LADDER_NAME).forEach((effort, budget) ->
                model.ladder(effort, ((JsonNumber) budget).intValue()));
        }

        return model.build();
    }
}
