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
package io.aklivity.zilla.config.binding.kafka.grpc.internal;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.config.binding.kafka.grpc.KafkaGrpcWithConfig;
import io.aklivity.zilla.config.engine.WithConfig;
import io.aklivity.zilla.config.engine.WithConfigAdapterSpi;

public class KafkaGrpcWithConfigAdapter implements WithConfigAdapterSpi, JsonbAdapter<WithConfig, JsonObject>
{
    private static final String SCHEME_NAME = "scheme";
    private static final String AUTHORITY_NAME = "authority";

    @Override
    public String type()
    {
        return KafkaGrpcBindingInfo.TYPE;
    }

    @Override
    public JsonObject adaptToJson(
        WithConfig with)
    {
        KafkaGrpcWithConfig kafkaGrpcWith = (KafkaGrpcWithConfig) with;

        JsonObjectBuilder object = Json.createObjectBuilder();

        object.add(SCHEME_NAME, kafkaGrpcWith.scheme);
        object.add(AUTHORITY_NAME, kafkaGrpcWith.authority);

        return object.build();
    }

    @Override
    public WithConfig adaptFromJson(
        JsonObject object)
    {
        String newScheme = object.getString(SCHEME_NAME);
        String newAuthority = object.getString(AUTHORITY_NAME);

        return new KafkaGrpcWithConfig(newScheme, newAuthority);
    }
}
