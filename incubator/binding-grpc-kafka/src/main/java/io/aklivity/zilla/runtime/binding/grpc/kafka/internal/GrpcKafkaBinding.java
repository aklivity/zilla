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
package io.aklivity.zilla.runtime.binding.grpc.kafka.internal;

import java.net.URL;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.Binding;

public final class GrpcKafkaBinding implements Binding
{
    public static final String NAME = "grpc_kafka";

    private final GrpcKafkaConfiguration config;

    GrpcKafkaBinding(
        GrpcKafkaConfiguration config)
    {
        this.config = config;
    }

    @Override
    public String name()
    {
        return GrpcKafkaBinding.NAME;
    }

    @Override
    public URL type()
    {
        return getClass().getResource("schema/grpc.kafka.schema.patch.json");
    }

    @Override
    public GrpcKafkaBindingContext supply(
        EngineContext context)
    {
        return new GrpcKafkaBindingContext(config, context);
    }
}
