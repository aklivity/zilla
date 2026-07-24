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
package io.aklivity.zilla.config.binding.http.internal;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.config.binding.http.HttpBindingInfo;
import io.aklivity.zilla.config.binding.http.HttpWithConfig;
import io.aklivity.zilla.config.binding.http.HttpWithConfigBuilder;
import io.aklivity.zilla.config.engine.WithConfig;
import io.aklivity.zilla.config.engine.WithConfigAdapterSpi;

public class HttpWithConfigAdapter implements WithConfigAdapterSpi, JsonbAdapter<WithConfig, JsonObject>
{
    private static final String HEADERS_NAME = "headers";
    private static final String OVERRIDES_NAME = "overrides";

    @Override
    public String type()
    {
        return HttpBindingInfo.TYPE;
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
            config.overrides.forEach(entries::add);

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
                        with.override(k, JsonString.class.cast(v).getString()));
            }
        }

        return with.build();
    }
}
