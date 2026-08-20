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
package io.aklivity.zilla.config.binding.tls;

import static io.aklivity.zilla.config.engine.WithConfig.NO_COMPOSITE_ID;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.WithConfig;

public final class TlsWithConfigBuilder<T> extends ConfigBuilder<T, TlsWithConfigBuilder<T>>
{
    private final Function<WithConfig, T> mapper;

    private long compositeId = NO_COMPOSITE_ID;
    private TlsWithCertificateConfig certificate;

    TlsWithConfigBuilder(
        Function<WithConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<TlsWithConfigBuilder<T>> thisType()
    {
        return (Class<TlsWithConfigBuilder<T>>) getClass();
    }

    public TlsWithConfigBuilder<T> compositeId(
        long compositeId)
    {
        this.compositeId = compositeId;
        return this;
    }

    public TlsWithCertificateConfigBuilder<TlsWithConfigBuilder<T>> certificate()
    {
        return TlsWithCertificateConfig.builder(this::certificate);
    }

    public TlsWithConfigBuilder<T> certificate(
        TlsWithCertificateConfig certificate)
    {
        this.certificate = certificate;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new TlsWithConfig(compositeId, certificate));
    }
}
