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

import io.aklivity.zilla.config.binding.llm.LlmWithConfig;
import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.WithConfig;

public final class LlmWithConfigAdapter extends ConfigAdapter<WithConfig, JsonObject>
{
    private static final String FALLBACK_NAME = "fallback";

    @Override
    public JsonObject adaptToJson(
        WithConfig with)
    {
        LlmWithConfig llmWith = (LlmWithConfig) with;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (llmWith.fallback != null)
        {
            JsonArrayBuilder fallback = Json.createArrayBuilder();
            llmWith.fallback.forEach(fallback::add);
            object.add(FALLBACK_NAME, fallback);
        }

        return object.build();
    }

    @Override
    public WithConfig adaptFromJson(
        JsonObject object)
    {
        List<String> fallback = object.containsKey(FALLBACK_NAME)
            ? object.getJsonArray(FALLBACK_NAME).stream()
                .map(JsonString.class::cast)
                .map(JsonString::getString)
                .toList()
            : null;

        return LlmWithConfig.builder()
            .fallback(fallback)
            .build();
    }
}
