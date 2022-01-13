/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.cog.kafka.internal.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.cog.kafka.internal.KafkaBinding;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.KafkaDeltaType;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.KafkaOffsetType;
import io.aklivity.zilla.runtime.engine.config.WithConfig;
import io.aklivity.zilla.runtime.engine.config.WithConfigAdapterSpi;

public final class KafkaWithConfigAdapter implements WithConfigAdapterSpi, JsonbAdapter<WithConfig, JsonObject>
{
    private static final String DEFAULT_OFFSET_NAME = "defaultOffset";
    private static final String DELTA_TYPE_NAME = "deltaType";

    @Override
    public String type()
    {
        return KafkaBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        WithConfig with)
    {
        KafkaWithConfig kafkaWith = (KafkaWithConfig) with;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (kafkaWith.defaultOffset != null)
        {
            object.add(DEFAULT_OFFSET_NAME, kafkaWith.defaultOffset.toString().toLowerCase());
        }

        if (kafkaWith.deltaType != null)
        {
            object.add(DELTA_TYPE_NAME, kafkaWith.deltaType.toString().toLowerCase());
        }

        return object.build();
    }

    @Override
    public WithConfig adaptFromJson(
        JsonObject object)
    {
        KafkaOffsetType defaultOffset = object.containsKey(DEFAULT_OFFSET_NAME)
                ? KafkaOffsetType.valueOf(object.getString(DEFAULT_OFFSET_NAME).toUpperCase())
                : null;

        KafkaDeltaType deltaType = object.containsKey(DELTA_TYPE_NAME)
                ? KafkaDeltaType.valueOf(object.getString(DELTA_TYPE_NAME).toUpperCase())
                : null;

        return new KafkaWithConfig(defaultOffset, deltaType);
    }
}
