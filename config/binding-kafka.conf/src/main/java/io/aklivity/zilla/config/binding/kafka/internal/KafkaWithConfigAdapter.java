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
package io.aklivity.zilla.config.binding.kafka.internal;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.config.binding.kafka.KafkaBindingInfo;
import io.aklivity.zilla.config.binding.kafka.KafkaWithConfig;
import io.aklivity.zilla.config.engine.WithConfig;
import io.aklivity.zilla.config.engine.WithConfigAdapterSpi;

public final class KafkaWithConfigAdapter implements WithConfigAdapterSpi, JsonbAdapter<WithConfig, JsonObject>
{
    private static final String DEFAULT_OFFSET_NAME = "defaultOffset";
    private static final String DELTA_TYPE_NAME = "deltaType";
    private static final String ACKS_NAME = "acks";

    private static final String ACKS_DEFAULT = "in_sync_replicas";

    @Override
    public String type()
    {
        return KafkaBindingInfo.TYPE;
    }

    @Override
    public JsonObject adaptToJson(
        WithConfig with)
    {
        KafkaWithConfig kafkaWith = (KafkaWithConfig) with;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (kafkaWith.defaultOffset != null)
        {
            object.add(DEFAULT_OFFSET_NAME, kafkaWith.defaultOffset);
        }

        if (kafkaWith.deltaType != null)
        {
            object.add(DELTA_TYPE_NAME, kafkaWith.deltaType);
        }

        if (!ACKS_DEFAULT.equals(kafkaWith.ackMode))
        {
            object.add(ACKS_NAME, kafkaWith.ackMode);
        }

        return object.build();
    }

    @Override
    public WithConfig adaptFromJson(
        JsonObject object)
    {
        String defaultOffset = object.containsKey(DEFAULT_OFFSET_NAME)
                ? object.getString(DEFAULT_OFFSET_NAME)
                : null;

        String deltaType = object.containsKey(DELTA_TYPE_NAME)
                ? object.getString(DELTA_TYPE_NAME)
                : null;

        String ackMode = object.containsKey(ACKS_NAME)
                ? object.getString(ACKS_NAME)
                : ACKS_DEFAULT;

        return KafkaWithConfig.builder()
            .defaultOffset(defaultOffset)
            .deltaType(deltaType)
            .ackMode(ackMode)
            .build();
    }
}
