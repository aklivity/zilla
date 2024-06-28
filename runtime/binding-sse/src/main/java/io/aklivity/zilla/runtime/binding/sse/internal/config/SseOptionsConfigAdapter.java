/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.sse.internal.config;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.sse.config.SseOptionsConfig;
import io.aklivity.zilla.runtime.binding.sse.config.SseOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.sse.config.SseRequestConfig;
import io.aklivity.zilla.runtime.binding.sse.internal.SseBinding;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public final class SseOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String RETRY_NAME = "retry";
    private static final String REQUESTS_NAME = "requests";
    public static final int RETRY_DEFAULT = 2000;


    private final SseRequestConfigAdapter ssePath = new SseRequestConfigAdapter();

    @Override
    public Kind kind()
    {
        return Kind.BINDING;
    }

    @Override
    public String type()
    {
        return SseBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        SseOptionsConfig sseOptions = (SseOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (sseOptions.retry != SseOptionsConfigAdapter.RETRY_DEFAULT)
        {
            object.add(RETRY_NAME, sseOptions.retry);
        }

        if (sseOptions.requests != null)
        {
            JsonArrayBuilder requests = Json.createArrayBuilder();
            sseOptions.requests.stream()
                .map(ssePath::adaptToJson)
                .forEach(requests::add);
            object.add(REQUESTS_NAME, requests);
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        SseOptionsConfigBuilder<SseOptionsConfig> sseOptions = SseOptionsConfig.builder();

        if (object.containsKey(RETRY_NAME))
        {
            sseOptions.retry(object.getInt(RETRY_NAME));
        }
        else
        {
            sseOptions.retry(SseOptionsConfigAdapter.RETRY_DEFAULT);
        }

        if (object.containsKey(REQUESTS_NAME))
        {
            List<SseRequestConfig> requests = object.getJsonArray(REQUESTS_NAME).stream()
                .map(item -> ssePath.adaptFromJson((JsonObject) item))
                .collect(Collectors.toList());
            sseOptions.requests(requests);
        }

        return sseOptions.build();
    }
}
