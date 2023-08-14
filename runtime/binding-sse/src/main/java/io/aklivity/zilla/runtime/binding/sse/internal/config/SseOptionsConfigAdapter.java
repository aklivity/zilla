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

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.sse.config.SseOptionsConfig;
import io.aklivity.zilla.runtime.binding.sse.internal.SseBinding;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public final class SseOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String RETRY_NAME = "retry";
    public static final int RETRY_DEFAULT = 2000;

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

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        int retry = object.containsKey(RETRY_NAME)
                ? object.getInt(RETRY_NAME)
                : SseOptionsConfigAdapter.RETRY_DEFAULT;

        return new SseOptionsConfig(retry);
    }
}
