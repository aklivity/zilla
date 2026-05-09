/*
 * Copyright 2021-2024 Aklivity Inc.
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

import io.aklivity.zilla.runtime.binding.http.config.HttpWithConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpWithConfigBuilder;
import io.aklivity.zilla.runtime.binding.http.internal.HttpBinding;
import io.aklivity.zilla.runtime.binding.http.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.http.internal.types.String8FW;
import io.aklivity.zilla.runtime.engine.config.WithConfig;
import io.aklivity.zilla.runtime.engine.config.WithConfigAdapterSpi;

public class HttpWithConfigAdapter implements WithConfigAdapterSpi, JsonbAdapter<WithConfig, JsonObject>
{
    private static final String HEADERS_NAME = "headers";
    private static final String OVERRIDES_NAME = "overrides";

    @Override
    public String type()
    {
        return HttpBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        WithConfig with)
    {
        HttpWithConfig config = (HttpWithConfig) with;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (config.overrides != null &&
            !config.overrides.isEmpty())
        {
            JsonObjectBuilder entries = Json.createObjectBuilder();
            config.overrides.forEach((k, v) -> entries.add(k.asString(), v.asString()));

            object.add(HEADERS_NAME, object.add(OVERRIDES_NAME, entries));
        }

        return object.build();
    }

    @Override
    public WithConfig adaptFromJson(
        JsonObject object)
    {
        HttpWithConfigBuilder<HttpWithConfig> with = HttpWithConfigBuilder.builder();

        if (object.containsKey(HEADERS_NAME))
        {
            JsonObject headers = object.getJsonObject(HEADERS_NAME);
            if (headers.containsKey(OVERRIDES_NAME))
            {
                headers.getJsonObject(OVERRIDES_NAME)
                    .forEach((k, v) ->
                        with.override(new String8FW(k), new String16FW(JsonString.class.cast(v).getString())));
            }
        }

        return with.build();
    }
}
