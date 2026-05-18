/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.tls.config;

import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public final class TlsCertConditionConfigBuilder<T> extends ConfigBuilder<T, TlsCertConditionConfigBuilder<T>>
{
    private final Function<TlsCertConditionConfig, T> mapper;

    private Boolean present;
    private TlsSignerConditionConfig signer;

    TlsCertConditionConfigBuilder(
        Function<TlsCertConditionConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<TlsCertConditionConfigBuilder<T>> thisType()
    {
        return (Class<TlsCertConditionConfigBuilder<T>>) getClass();
    }

    public TlsCertConditionConfigBuilder<T> present(
        boolean present)
    {
        this.present = present;
        return this;
    }

    public TlsCertConditionConfigBuilder<T> signer(
        TlsSignerConditionConfig signer)
    {
        this.signer = signer;
        return this;
    }

    public TlsSignerConditionConfigBuilder<TlsCertConditionConfigBuilder<T>> signer()
    {
        return TlsSignerConditionConfig.builder(this::signer);
    }

    @Override
    public T build()
    {
        return mapper.apply(new TlsCertConditionConfig(present, signer));
    }
}
