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
package io.aklivity.zilla.runtime.binding.grpc.internal.config;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.function.Function;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.grpc.config.GrpcOptionsConfig;
import io.aklivity.zilla.runtime.binding.grpc.config.GrpcProtobufConfig;
import io.aklivity.zilla.runtime.binding.grpc.internal.GrpcBinding;
import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public final class GrpcOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String SERVICES_NAME = "services";

    private final GrpcProtobufParser parser = new GrpcProtobufParser();
    private Function<String, String> readURL;

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

        if (grpcOptions.protobufs != null)
        {
            JsonArrayBuilder keys = Json.createArrayBuilder();
            grpcOptions.protobufs.forEach(p -> keys.add(p.location));
            object.add(SERVICES_NAME, keys);
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        List<GrpcProtobufConfig> protobufs = object.containsKey(SERVICES_NAME)
                ? asListProtobufs(object.getJsonArray(SERVICES_NAME))
                : null;

        return new GrpcOptionsConfig(protobufs);
    }

    @Override
    public void adaptContext(
        ConfigAdapterContext context)
    {
        this.readURL = context::readURL;
    }

    private List<GrpcProtobufConfig> asListProtobufs(
        JsonArray array)
    {
        return array.stream()
            .map(this::asProtobuf)
            .collect(toList());
    }

    private GrpcProtobufConfig asProtobuf(
        JsonValue value)
    {
        final String location = ((JsonString) value).getString();
        final String protobuf = readURL.apply(location);

        return parser.parse(location, protobuf);
    }
}
