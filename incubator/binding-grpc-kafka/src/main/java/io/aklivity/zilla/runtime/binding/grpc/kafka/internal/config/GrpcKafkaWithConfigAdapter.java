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

import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.GrpcKafkaBinding;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.KafkaAckMode;
import io.aklivity.zilla.runtime.engine.config.WithConfig;
import io.aklivity.zilla.runtime.engine.config.WithConfigAdapterSpi;

public final class GrpcKafkaWithConfigAdapter implements WithConfigAdapterSpi, JsonbAdapter<WithConfig, JsonObject>
{
    private static final KafkaAckMode ACKS_DEFAULT = KafkaAckMode.IN_SYNC_REPLICAS;

    private static final String TOPIC_NAME = "topic";
    private static final String ACKS_NAME = "acks";
    private static final String KEY_NAME = "key";
    private static final String OVERRIDES_NAME = "overrides";
    private static final String REPLY_TO_NAME = "reply-to";
    private static final String FILTERS_NAME = "filters";
    private static final String FILTERS_KEY_NAME = "key";
    private static final String FILTERS_HEADERS_NAME = "headers";

    @Override
    public String type()
    {
        return GrpcKafkaBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        WithConfig with)
    {
        GrpcKafkaWithConfig grpcKafkaWith = (GrpcKafkaWithConfig) with;

        JsonObjectBuilder object = Json.createObjectBuilder();

        object.add(TOPIC_NAME, grpcKafkaWith.topic);

        if (grpcKafkaWith.acks != ACKS_DEFAULT)
        {
            object.add(ACKS_NAME, grpcKafkaWith.acks.name().toLowerCase());
        }

        if (grpcKafkaWith.key.isPresent())
        {
            object.add(KEY_NAME, grpcKafkaWith.key.get());
        }

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
    public WithConfig adaptFromJson(
        JsonObject object)
    {
        String newTopic = object.getString(TOPIC_NAME);

        KafkaAckMode newProduceAcks = object.containsKey(ACKS_NAME)
            ? KafkaAckMode.valueOf(object.getString(ACKS_NAME).toUpperCase())
            : ACKS_DEFAULT;

        String newProduceKey = object.containsKey(KEY_NAME)
            ? object.getString(KEY_NAME)
            : null;

        List<GrpcKafkaWithProduceOverrideConfig> newOverrides = null;
        if (object.containsKey(OVERRIDES_NAME))
        {
            JsonObject overrides = object.getJsonObject(OVERRIDES_NAME);
            newOverrides = new ArrayList<>();

            for (String name : overrides.keySet())
            {
                String value = overrides.getString(name);

                newOverrides.add(new GrpcKafkaWithProduceOverrideConfig(name, value));
            }
        }

        String newReplyTo = object.getString(REPLY_TO_NAME);

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

        return new GrpcKafkaWithConfig(newTopic, newProduceAcks, newProduceKey, newOverrides, newReplyTo, newFilters);
    }
}
