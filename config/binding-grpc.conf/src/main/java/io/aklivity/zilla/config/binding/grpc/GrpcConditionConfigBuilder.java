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
package io.aklivity.zilla.config.binding.grpc;

import java.util.Map;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class GrpcConditionConfigBuilder<T> extends ConfigBuilder<T, GrpcConditionConfigBuilder<T>>
{
    private final Function<ConditionConfig, T> mapper;

    private String method;
    private Map<String, GrpcMetadataValueConfig> metadata;

    GrpcConditionConfigBuilder(
        Function<ConditionConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<GrpcConditionConfigBuilder<T>> thisType()
    {
        return (Class<GrpcConditionConfigBuilder<T>>) getClass();
    }

    public GrpcConditionConfigBuilder<T> method(
        String method)
    {
        this.method = method;
        return this;
    }

    public GrpcConditionConfigBuilder<T> metadata(
        Map<String, GrpcMetadataValueConfig> metadata)
    {
        this.metadata = metadata;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new GrpcConditionConfig(method, metadata));
    }
}
