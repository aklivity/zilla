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

import java.util.function.Function;

import io.aklivity.zilla.config.engine.WithConfig;

public final class TlsWithConfig extends WithConfig
{
    public final TlsWithCertificateConfig certificate;

    public static TlsWithConfigBuilder<TlsWithConfig> builder()
    {
        return new TlsWithConfigBuilder<>(TlsWithConfig.class::cast);
    }

    public static <T> TlsWithConfigBuilder<T> builder(
        Function<WithConfig, T> mapper)
    {
        return new TlsWithConfigBuilder<>(mapper);
    }

    TlsWithConfig(
        long compositeId,
        TlsWithCertificateConfig certificate)
    {
        super(compositeId);
        this.certificate = certificate;
    }
}
