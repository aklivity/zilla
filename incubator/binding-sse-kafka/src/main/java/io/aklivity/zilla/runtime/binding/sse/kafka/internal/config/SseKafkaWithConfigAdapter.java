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

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.sse.kafka.internal.SseKafkaBinding;
import io.aklivity.zilla.runtime.engine.config.WithConfig;
import io.aklivity.zilla.runtime.engine.config.WithConfigAdapterSpi;

public final class SseKafkaWithConfigAdapter implements WithConfigAdapterSpi, JsonbAdapter<WithConfig, JsonObject>
{
    private static final String TOPIC_NAME = "topic";

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

        if (sseKafkaWith.topic != null)
        {
            object.add(TOPIC_NAME, sseKafkaWith.topic);
        }

        // TODO: filters

        return object.build();
    }

    @Override
    public WithConfig adaptFromJson(
        JsonObject object)
    {
        String topic = object.containsKey(TOPIC_NAME)
                ? object.getString(TOPIC_NAME)
                : null;

        // TODO: filters

        return new SseKafkaWithConfig(topic);
    }
}
