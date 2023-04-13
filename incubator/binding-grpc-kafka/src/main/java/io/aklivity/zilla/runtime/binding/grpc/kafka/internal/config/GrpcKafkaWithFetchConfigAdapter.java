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
package io.aklivity.zilla.runtime.binding.grpc.kafka.internal.config;

import java.util.ArrayList;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

public final class GrpcKafkaWithFetchConfigAdapter implements JsonbAdapter<GrpcKafkaWithConfig, JsonObject>
{

    private static final String TOPIC_NAME = "topic";
    private static final String FILTERS_NAME = "filters";
    private static final String FILTERS_KEY_NAME = "key";
    private static final String FILTERS_HEADERS_NAME = "headers";


    @Override
    public JsonObject adaptToJson(
        GrpcKafkaWithConfig with)
    {
        GrpcKafkaWithFetchConfig grpcKafkaWith = with.fetch.get();

        JsonObjectBuilder object = Json.createObjectBuilder();

        object.add(TOPIC_NAME, grpcKafkaWith.topic);

        if (grpcKafkaWith.filters.isPresent())
        {
            JsonArrayBuilder newFilters = Json.createArrayBuilder();

            for (GrpcKafkaWithFetchFilterConfig filter : grpcKafkaWith.filters.get())
            {
                JsonObjectBuilder newFilter = Json.createObjectBuilder();

                if (filter.key.isPresent())
                {
                    newFilter.add(FILTERS_KEY_NAME, filter.key.get());
                }

                if (filter.headers.isPresent())
                {
                    JsonObjectBuilder newHeaders = Json.createObjectBuilder();

                    for (GrpcKafkaWithFetchFilterHeaderConfig header : filter.headers.get())
                    {
                        newHeaders.add(header.name, header.value);
                    }

                    newFilter.add(FILTERS_HEADERS_NAME, newHeaders);
                }

                newFilters.add(newFilter);
            }

            object.add(FILTERS_NAME, newFilters);
        }

        return object.build();
    }

    @Override
    public GrpcKafkaWithConfig adaptFromJson(
        JsonObject object)
    {
        String newTopic = object.getString(TOPIC_NAME);


        List<GrpcKafkaWithFetchFilterConfig> newFilters = null;
        if (object.containsKey(FILTERS_NAME))
        {
            JsonArray filters = object.getJsonArray(FILTERS_NAME);
            newFilters = new ArrayList<>(filters.size());

            for (int i = 0; i < filters.size(); i++)
            {
                JsonObject filter = filters.getJsonObject(i);

                String newKey = null;
                if (filter.containsKey(FILTERS_KEY_NAME))
                {
                    newKey = filter.getString(FILTERS_KEY_NAME);
                }

                List<GrpcKafkaWithFetchFilterHeaderConfig> newHeaders = null;
                if (filter.containsKey(FILTERS_HEADERS_NAME))
                {
                    JsonObject headers = filter.getJsonObject(FILTERS_HEADERS_NAME);
                    newHeaders = new ArrayList<>(headers.size());

                    for (String newHeaderName : headers.keySet())
                    {
                        String newHeaderValue = headers.getString(newHeaderName);
                        newHeaders.add(new GrpcKafkaWithFetchFilterHeaderConfig(newHeaderName, newHeaderValue));
                    }
                }

                newFilters.add(new GrpcKafkaWithFetchFilterConfig(newKey, newHeaders));
            }
        }

        return new GrpcKafkaWithConfig(new GrpcKafkaWithFetchConfig(newTopic, newFilters));
    }
}
