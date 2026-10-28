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
package io.aklivity.zilla.config.binding.a2a.http.internal;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import io.aklivity.zilla.config.binding.a2a.http.A2aHttpWithConfig;
import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.engine.ModelConfigAdapter;
import io.aklivity.zilla.config.engine.WithConfig;

public final class A2aHttpWithConfigAdapter extends ConfigAdapter<WithConfig, JsonObject>
{
    private static final String HEADERS_NAME = "headers";
    private static final String BODY_NAME = "body";

    private final ModelConfigAdapter model = new ModelConfigAdapter();

    @Override
    public JsonObject adaptToJson(
        WithConfig with)
    {
        A2aHttpWithConfig a2aHttpWith = (A2aHttpWithConfig) with;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (a2aHttpWith.headers != null)
        {
            JsonObjectBuilder headers = Json.createObjectBuilder();
            a2aHttpWith.headers.forEach(headers::add);
            object.add(HEADERS_NAME, headers);
        }

        if (a2aHttpWith.body != null)
        {
            object.add(BODY_NAME, model.adaptToJson(a2aHttpWith.body));
        }

        return object.build();
    }

    @Override
    public WithConfig adaptFromJson(
        JsonObject object)
    {
        Map<String, String> headers = null;
        if (object.containsKey(HEADERS_NAME))
        {
            JsonObject headersObject = object.getJsonObject(HEADERS_NAME);
            headers = new LinkedHashMap<>();
            for (String name : headersObject.keySet())
            {
                headers.put(name, headersObject.getString(name));
            }
        }

        ModelConfig body = object.containsKey(BODY_NAME)
            ? model.adaptFromJson(object.get(BODY_NAME))
            : null;

        return A2aHttpWithConfig.builder()
            .headers(headers)
            .body(body)
            .build();
    }
}
