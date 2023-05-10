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
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.KafkaAckMode;

public final class GrpcKafkaWithProduceConfigAdapter implements JsonbAdapter<GrpcKafkaWithConfig, JsonObject>
{
    private static final KafkaAckMode ACKS_DEFAULT = KafkaAckMode.IN_SYNC_REPLICAS;

    private static final String CAPABILITY_NAME = "capability";
    private static final String TOPIC_NAME = "topic";
    private static final String ACKS_NAME = "acks";
    private static final String KEY_NAME = "key";
    private static final String OVERRIDES_NAME = "overrides";
    private static final String REPLY_TO_NAME = "reply-to";

    @Override
    public JsonObject adaptToJson(
        GrpcKafkaWithConfig with)
    {
        GrpcKafkaWithProduceConfig grpcKafkaWith = with.produce.get();

        JsonObjectBuilder object = Json.createObjectBuilder();

        object.add(CAPABILITY_NAME, GrpcKafkaCapability.PRODUCE.asString());
        object.add(TOPIC_NAME, grpcKafkaWith.topic);

        if (grpcKafkaWith.acks != ACKS_DEFAULT)
        {
            object.add(ACKS_NAME, grpcKafkaWith.acks.name().toLowerCase());
        }

        if (grpcKafkaWith.key.isPresent())
        {
            object.add(KEY_NAME, grpcKafkaWith.key.get());
        }

        if (grpcKafkaWith.overrides.isPresent())
        {
            JsonObjectBuilder newOverrides = Json.createObjectBuilder();

            for (GrpcKafkaWithProduceOverrideConfig override : grpcKafkaWith.overrides.get())
            {
                newOverrides.add(override.name, override.value);
            }

            object.add(OVERRIDES_NAME, newOverrides);
        }

        object.add(REPLY_TO_NAME, grpcKafkaWith.replyTo);

        return object.build();
    }

    @Override
    public GrpcKafkaWithConfig adaptFromJson(
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

        return new GrpcKafkaWithConfig(
            new GrpcKafkaWithProduceConfig(newTopic, newProduceAcks, newProduceKey, newOverrides, newReplyTo));
    }
}
