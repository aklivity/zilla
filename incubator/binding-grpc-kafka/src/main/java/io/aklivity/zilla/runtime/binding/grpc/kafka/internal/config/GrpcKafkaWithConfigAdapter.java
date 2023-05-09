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

import jakarta.json.JsonObject;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.GrpcKafkaBinding;
import io.aklivity.zilla.runtime.engine.config.WithConfig;
import io.aklivity.zilla.runtime.engine.config.WithConfigAdapterSpi;

public final class GrpcKafkaWithConfigAdapter implements WithConfigAdapterSpi, JsonbAdapter<WithConfig, JsonObject>
{
    private static final String CAPABILITY_NAME = "capability";

    private final GrpcKafkaWithFetchConfigAdapter fetch = new GrpcKafkaWithFetchConfigAdapter();
    private final GrpcKafkaWithProduceConfigAdapter produce = new GrpcKafkaWithProduceConfigAdapter();

    @Override
    public String type()
    {
        return GrpcKafkaBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        WithConfig with)
    {
        JsonObject newWith = null;

        GrpcKafkaWithConfig grpcKafkaWith = (GrpcKafkaWithConfig) with;
        switch (grpcKafkaWith.capability)
        {
        case FETCH:
            newWith = fetch.adaptToJson(grpcKafkaWith);
            break;
        case PRODUCE:
            newWith = produce.adaptToJson(grpcKafkaWith);
            break;
        }

        return newWith;
    }

    @Override
    public WithConfig adaptFromJson(
        JsonObject object)
    {
        GrpcKafkaWithConfig newWith = null;

        GrpcKafkaCapability newCapability = GrpcKafkaCapability.of(object.getString(CAPABILITY_NAME));
        switch (newCapability)
        {
        case FETCH:
            newWith = fetch.adaptFromJson(object);
            break;
        case PRODUCE:
            newWith = produce.adaptFromJson(object);
            break;
        }

        return newWith;
    }
}
