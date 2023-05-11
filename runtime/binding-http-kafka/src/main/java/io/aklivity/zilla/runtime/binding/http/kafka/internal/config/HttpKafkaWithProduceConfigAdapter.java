/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.http.kafka.internal.config;

import java.util.ArrayList;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.KafkaAckMode;

public final class HttpKafkaWithProduceConfigAdapter implements JsonbAdapter<HttpKafkaWithConfig, JsonObject>
{
    private static final String CAPABILITY_NAME = "capability";
    private static final String TOPIC_NAME = "topic";
    private static final String ACKS_NAME = "acks";
    private static final String KEY_NAME = "key";
    private static final String OVERRIDES_NAME = "overrides";
    private static final String REPLY_TO_NAME = "reply-to";
    private static final String ASYNC_NAME = "async";

    private static final KafkaAckMode ACKS_DEFAULT = KafkaAckMode.IN_SYNC_REPLICAS;

    @Override
    public JsonObject adaptToJson(
        HttpKafkaWithConfig with)
    {
        HttpKafkaWithProduceConfig produce = with.produce.get();

        JsonObjectBuilder object = Json.createObjectBuilder();

        object.add(CAPABILITY_NAME, HttpKafkaCapability.PRODUCE.asString());

        object.add(TOPIC_NAME, produce.topic);

        if (produce.acks != ACKS_DEFAULT)
        {
            object.add(ACKS_NAME, produce.acks.name().toLowerCase());
        }

        if (produce.key.isPresent())
        {
            object.add(KEY_NAME, produce.key.get());
        }

        if (produce.overrides.isPresent())
        {
            JsonObjectBuilder newOverrides = Json.createObjectBuilder();

            for (HttpKafkaWithProduceOverrideConfig override : produce.overrides.get())
            {
                newOverrides.add(override.name, override.value);
            }

            object.add(OVERRIDES_NAME, newOverrides);
        }

        if (produce.replyTo.isPresent())
        {
            object.add(REPLY_TO_NAME, produce.replyTo.get());
        }

        if (produce.async.isPresent())
        {
            JsonObjectBuilder newAsync = Json.createObjectBuilder();

            for (HttpKafkaWithProduceAsyncHeaderConfig header : produce.async.get())
            {
                newAsync.add(header.name, header.value);
            }

            object.add(ASYNC_NAME, newAsync);
        }

        return object.build();
    }

    @Override
    public HttpKafkaWithConfig adaptFromJson(
        JsonObject object)
    {
        String newTopic = object.getString(TOPIC_NAME);

        KafkaAckMode newAcks = object.containsKey(ACKS_NAME)
            ? KafkaAckMode.valueOf(object.getString(ACKS_NAME).toUpperCase())
            : ACKS_DEFAULT;

        String newKey = object.containsKey(KEY_NAME)
            ? object.getString(KEY_NAME)
            : null;

        List<HttpKafkaWithProduceOverrideConfig> newOverrides = null;
        if (object.containsKey(OVERRIDES_NAME))
        {
            JsonObject overrides = object.getJsonObject(OVERRIDES_NAME);
            newOverrides = new ArrayList<>();

            for (String name : overrides.keySet())
            {
                String value = overrides.getString(name);

                newOverrides.add(new HttpKafkaWithProduceOverrideConfig(name, value));
            }
        }

        String newReplyTo = object.containsKey(REPLY_TO_NAME)
                ? object.getString(REPLY_TO_NAME)
                : null;

        List<HttpKafkaWithProduceAsyncHeaderConfig> newAsync = null;
        if (object.containsKey(ASYNC_NAME))
        {
            JsonObject async = object.getJsonObject(ASYNC_NAME);
            newAsync = new ArrayList<>();

            for (String name : async.keySet())
            {
                String value = async.getString(name);

                newAsync.add(new HttpKafkaWithProduceAsyncHeaderConfig(name, value));
            }
        }

        return new HttpKafkaWithConfig(
                new HttpKafkaWithProduceConfig(newTopic, newAcks, newKey, newOverrides, newReplyTo, newAsync));
    }
}
