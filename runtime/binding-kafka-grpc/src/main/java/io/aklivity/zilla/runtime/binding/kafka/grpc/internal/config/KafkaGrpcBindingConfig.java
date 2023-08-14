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
package io.aklivity.zilla.runtime.binding.kafka.grpc.internal.config;

import static io.aklivity.zilla.runtime.binding.kafka.grpc.internal.config.KafkaGrpcOptionsConfigAdapter.DEFAULT;
import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Optional;

import io.aklivity.zilla.runtime.binding.kafka.grpc.config.KafkaGrpcOptionsConfig;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.stream.KafkaGrpcFetchHeaderHelper;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;

public final class KafkaGrpcBindingConfig
{
    public final long id;
    public final long entryId;
    public final KindConfig kind;
    public final KafkaGrpcOptionsConfig options;
    public final KafkaGrpcFetchHeaderHelper helper;
    public final List<KafkaGrpcRouteConfig> routes;

    public KafkaGrpcBindingConfig(
        BindingConfig binding)
    {
        this.id = binding.id;
        this.entryId = binding.entryId;
        this.kind = binding.kind;
        this.options = Optional.ofNullable(binding.options)
                .map(KafkaGrpcOptionsConfig.class::cast)
                .orElse(DEFAULT);
        this.routes = binding.routes.stream().map(r -> new KafkaGrpcRouteConfig(options, r))
            .collect(toList());
        this.helper = new KafkaGrpcFetchHeaderHelper(options.correlation);
    }
}
