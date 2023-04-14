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
package io.aklivity.zilla.runtime.binding.kafka.grpc.internal;

import java.net.URL;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.Binding;
import io.aklivity.zilla.runtime.engine.config.KindConfig;

public final class KafkaGrpcBinding implements Binding
{
    public static final String NAME = "kafka_grpc";

    private final KafkaGrpcConfiguration config;

    KafkaGrpcBinding(
        KafkaGrpcConfiguration config)
    {
        this.config = config;
    }

    @Override
    public String name()
    {
        return KafkaGrpcBinding.NAME;
    }

    @Override
    public URL type()
    {
        return getClass().getResource("schema/kafka.grpc.schema.patch.json");
    }

    @Override
    public int workers(
        KindConfig kind)
    {
        return 1;
    }

    @Override
    public KafkaGrpcBindingContext supply(
        EngineContext context)
    {
        return new KafkaGrpcBindingContext(config, context);
    }
}
