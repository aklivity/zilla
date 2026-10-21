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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

import io.aklivity.zilla.config.binding.llm.LlmModelConfig;

public final class LlmModelDefaults
{
    private static final String RESOURCE = "models/%s.json";

    public static List<LlmModelConfig> of(
        String dialect)
    {
        final List<LlmModelConfig> models = new ArrayList<>();

        final int variant = dialect != null ? dialect.indexOf('/') : -1;
        final String vendor = variant < 0 ? dialect : dialect.substring(0, variant);

        final InputStream input = vendor != null && vendor.matches("[a-z0-9-]+")
            ? LlmModelDefaults.class.getResourceAsStream(RESOURCE.formatted(vendor))
            : null;

        if (input != null)
        {
            final LlmModelConfigAdapter adapter = new LlmModelConfigAdapter();

            try (JsonReader reader = Json.createReader(input))
            {
                reader.readObject().forEach((name, entry) ->
                    models.add(adapter.adaptFromJson(name, (JsonObject) entry)));
            }
        }

        return List.copyOf(models);
    }

    private LlmModelDefaults()
    {
    }
}
