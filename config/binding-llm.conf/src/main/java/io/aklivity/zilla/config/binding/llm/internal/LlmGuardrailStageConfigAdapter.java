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
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import io.aklivity.zilla.config.binding.llm.LlmGuardrailStageConfig;
import io.aklivity.zilla.config.binding.llm.LlmGuardrailStageConfigBuilder;
import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.ModelConfigAdapter;

public final class LlmGuardrailStageConfigAdapter extends ConfigAdapter<LlmGuardrailStageConfig, JsonObject>
{
    private static final String SECTION_NAME = "section";
    private static final String VALUE_NAME = "value";

    private final ModelConfigAdapter model = new ModelConfigAdapter();

    @Override
    public JsonObject adaptToJson(
        LlmGuardrailStageConfig stage)
    {
        JsonArrayBuilder sections = Json.createArrayBuilder();
        stage.sections.forEach(sections::add);

        model.adaptType(stage.value.model);

        JsonObjectBuilder object = Json.createObjectBuilder();
        object.add(SECTION_NAME, sections);
        object.add(VALUE_NAME, model.adaptToJson(stage.value));

        return object.build();
    }

    @Override
    public LlmGuardrailStageConfig adaptFromJson(
        JsonObject object)
    {
        LlmGuardrailStageConfigBuilder<LlmGuardrailStageConfig> stage = LlmGuardrailStageConfig.builder();

        JsonValue sections = object.get(SECTION_NAME);
        if (sections instanceof JsonArray array)
        {
            array.forEach(section -> stage.section(((JsonString) section).getString()));
        }
        else
        {
            stage.section(((JsonString) sections).getString());
        }

        stage.value(model.adaptFromJson(object.get(VALUE_NAME)));

        return stage.build();
    }
}
