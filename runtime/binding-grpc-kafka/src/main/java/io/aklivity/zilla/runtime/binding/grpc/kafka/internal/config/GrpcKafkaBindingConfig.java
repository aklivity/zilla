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

import static io.aklivity.zilla.runtime.binding.grpc.kafka.internal.config.GrpcKafkaOptionsConfigAdapter.DEFAULT;
import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Optional;

import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.stream.GrpcBeginExFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.stream.GrpcMetadataFW;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;

public final class GrpcKafkaBindingConfig
{
    public final long id;
    public final String name;
    public final KindConfig kind;
    public final GrpcKafkaOptionsConfig options;
    public final List<GrpcKafkaRouteConfig> routes;

    public GrpcKafkaBindingConfig(
        BindingConfig binding)
    {
        this.id = binding.id;
        this.name = binding.name;
        this.kind = binding.kind;
        this.options = Optional.ofNullable(binding.options)
                .map(GrpcKafkaOptionsConfig.class::cast)
                .orElse(DEFAULT);
        this.routes = binding.routes.stream().map(r -> new GrpcKafkaRouteConfig(options, r)).collect(toList());
    }

    public GrpcKafkaRouteConfig resolve(
        long authorization,
        GrpcBeginExFW beginEx)
    {
        final String16FW service = beginEx.service();
        final String16FW method = beginEx.method();
        final Array32FW<GrpcMetadataFW> metadata = beginEx.metadata();

        return routes.stream()
            .filter(r -> r.authorized(authorization) && r.matches(service, method, metadata))
            .findFirst()
            .orElse(null);
    }
}
