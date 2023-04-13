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
package io.aklivity.zilla.runtime.binding.kafka.grpc.internal.config;

import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.bind.adapter.JsonbAdapter;

import org.agrona.collections.Object2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.KafkaGrpcBinding;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.String8FW;
import io.aklivity.zilla.runtime.engine.config.ConditionConfig;
import io.aklivity.zilla.runtime.engine.config.ConditionConfigAdapterSpi;

public final class KafkaGrpcConditionConfigAdapter implements ConditionConfigAdapterSpi, JsonbAdapter<ConditionConfig, JsonObject>
{
    private static final String TOPIC_NAME = "topic";
    private static final String KEY_NAME = "key";
    private static final String HEADERS_NAME = "headers";
    private static final String REPLY_TO_NAME = "reply-to";

    @Override
    public String type()
    {
        return KafkaGrpcBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        ConditionConfig adaptable)
    {
        KafkaGrpcConditionConfig condition = (KafkaGrpcConditionConfig) adaptable;

        JsonObjectBuilder object = Json.createObjectBuilder();

        object.add(TOPIC_NAME, condition.topic.asString());

        if (condition.key.isPresent())
        {
            object.add(KEY_NAME, condition.key.get().asString());
        }

        if (condition.replyTo != null)
        {
            object.add(REPLY_TO_NAME, condition.replyTo.asString());
        }

        if (condition.headers.isPresent() &&
            !condition.headers.isEmpty())
        {
            JsonObjectBuilder entries = Json.createObjectBuilder();
            condition.headers.get().forEach((k, v) ->
            {
                String name = k.asString();
                String value = v.asString();
                entries.add(name, value);
            });

            object.add(HEADERS_NAME, entries);
        }

        return object.build();
    }

    @Override
    public ConditionConfig adaptFromJson(
        JsonObject object)
    {
        String16FW topic = new String16FW(object.getString(TOPIC_NAME));

        String16FW key = object.containsKey(KEY_NAME)
            ? new String16FW(object.getString(KEY_NAME))
            : null;
        String16FW replyTo = object.containsKey(REPLY_TO_NAME)
            ? new String16FW(object.getString(REPLY_TO_NAME))
            : null;

        JsonObject headers = object.containsKey(HEADERS_NAME)
            ? object.getJsonObject(HEADERS_NAME)
            : null;

        final Map<String8FW, String16FW> newHeaders = new Object2ObjectHashMap<>();

        if (headers != null)
        {
            headers.forEach((k, v) ->
            {
                final String8FW name = new String8FW(k);
                final String16FW value = new String16FW(((JsonString) v).getString());

                newHeaders.put(name, value);
            });
        }

        return new KafkaGrpcConditionConfig(topic, key, replyTo, newHeaders);
    }
}
