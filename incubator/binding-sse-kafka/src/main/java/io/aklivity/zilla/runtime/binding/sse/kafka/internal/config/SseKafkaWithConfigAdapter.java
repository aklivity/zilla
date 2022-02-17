/*
 * Copyright 2021-2022 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.sse.kafka.internal.config;

import java.util.ArrayList;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.sse.kafka.internal.SseKafkaBinding;
import io.aklivity.zilla.runtime.engine.config.WithConfig;
import io.aklivity.zilla.runtime.engine.config.WithConfigAdapterSpi;

public final class SseKafkaWithConfigAdapter implements WithConfigAdapterSpi, JsonbAdapter<WithConfig, JsonObject>
{
    private static final String TOPIC_NAME = "topic";
    private static final String FILTERS_NAME = "filters";
    private static final String KEY_NAME = "key";
    private static final String HEADERS_NAME = "headers";

    @Override
    public String type()
    {
        return SseKafkaBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        WithConfig with)
    {
        SseKafkaWithConfig sseKafkaWith = (SseKafkaWithConfig) with;

        JsonObjectBuilder object = Json.createObjectBuilder();

        object.add(TOPIC_NAME, sseKafkaWith.topic);

        if (sseKafkaWith.filters.isPresent())
        {
            JsonArrayBuilder newFilters = Json.createArrayBuilder();

            for (SseKafkaWithFilterConfig filter : sseKafkaWith.filters.get())
            {
                JsonObjectBuilder newFilter = Json.createObjectBuilder();

                if (filter.key.isPresent())
                {
                    newFilter.add(KEY_NAME, filter.key.get());
                }

                if (filter.headers.isPresent())
                {
                    JsonObjectBuilder newHeaders = Json.createObjectBuilder();

                    for (SseKafkaWithFilterHeaderConfig header : filter.headers.get())
                    {
                        newHeaders.add(header.name, header.value);
                    }

                    newFilter.add(HEADERS_NAME, newHeaders);
                }

                newFilters.add(newFilter);
            }

            object.add(FILTERS_NAME, newFilters);
        }

        return object.build();
    }

    @Override
    public WithConfig adaptFromJson(
        JsonObject object)
    {
        String newTopic = object.getString(TOPIC_NAME);
        List<SseKafkaWithFilterConfig> newFilters = null;

        if (object.containsKey(FILTERS_NAME))
        {
            JsonArray filters = object.getJsonArray(FILTERS_NAME);
            newFilters = new ArrayList<>(filters.size());

            for (int i = 0; i < filters.size(); i++)
            {
                JsonObject filter = filters.getJsonObject(i);

                String newKey = null;
                if (filter.containsKey(KEY_NAME))
                {
                    newKey = filter.getString(KEY_NAME);
                }

                List<SseKafkaWithFilterHeaderConfig> newHeaders = null;
                if (filter.containsKey(HEADERS_NAME))
                {
                    JsonObject headers = filter.getJsonObject(HEADERS_NAME);
                    newHeaders = new ArrayList<>(headers.size());

                    for (String newHeaderName : headers.keySet())
                    {
                        String newHeaderValue = headers.getString(newHeaderName);
                        newHeaders.add(new SseKafkaWithFilterHeaderConfig(newHeaderName, newHeaderValue));
                    }
                }

                newFilters.add(new SseKafkaWithFilterConfig(newKey, newHeaders));
            }
        }

        return new SseKafkaWithConfig(newTopic, newFilters);
    }
}
