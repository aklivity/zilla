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
package io.aklivity.zilla.runtime.exporter.otlp.config;

import java.net.URI;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public final class OtlpOverridesConfigBuilder<T> extends ConfigBuilder<T, OtlpOverridesConfigBuilder<T>>
{
    private static final URI DEFAULT_METRICS_PATH = URI.create("/v1/metrics");
    private static final URI DEFAULT_LOGS_PATH = URI.create("/v1/logs");

    private final Function<OtlpOverridesConfig, T> mapper;

    private URI metrics;
    private URI logs;

    OtlpOverridesConfigBuilder(
        Function<OtlpOverridesConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<OtlpOverridesConfigBuilder<T>> thisType()
    {
        return (Class<OtlpOverridesConfigBuilder<T>>) getClass();
    }

    public OtlpOverridesConfigBuilder<T> metrics(
        URI metrics)
    {
        this.metrics = metrics;
        return this;
    }

    public OtlpOverridesConfigBuilder<T> logs(
        URI logs)
    {
        this.logs = logs;
        return this;
    }

    @Override
    public T build()
    {
        final URI metrics = this.metrics != null ? this.metrics : DEFAULT_METRICS_PATH;
        final URI logs = this.logs != null ? this.logs : DEFAULT_LOGS_PATH;
        return mapper.apply(new OtlpOverridesConfig(metrics, logs));
    }
}
