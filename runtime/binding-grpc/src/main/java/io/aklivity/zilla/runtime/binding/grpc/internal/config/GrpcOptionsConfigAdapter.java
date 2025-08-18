/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.grpc.internal.config;

import io.aklivity.zilla.runtime.binding.grpc.config.GrpcOptionsConfigBuilder;
import jakarta.json.*;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.grpc.config.GrpcOptionsConfig;
import io.aklivity.zilla.runtime.binding.grpc.internal.GrpcBinding;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public final class GrpcOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String SERVICES_NAME = "services";

    @Override
    public Kind kind()
    {
        return Kind.BINDING;
    }

    @Override
    public String type()
    {
        return GrpcBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        GrpcOptionsConfig grpcOptions = (GrpcOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (grpcOptions.services != null)
        {
            JsonArrayBuilder services = Json.createArrayBuilder();
            grpcOptions.services.forEach(services::add);
            object.add(SERVICES_NAME, services);
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        GrpcOptionsConfigBuilder<GrpcOptionsConfig> grpcOptions = GrpcOptionsConfig.builder();

        if (object.containsKey(SERVICES_NAME))
        {
            grpcOptions.services(object.getJsonArray(SERVICES_NAME).stream()
                .map(JsonString.class::cast)
                .map(JsonString::getString)
                .toList());
        }

        return grpcOptions.build();
    }

}
