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

import static io.aklivity.zilla.runtime.exporter.otlp.config.OtlpOptionsConfig.OtlpSignalsConfig.LOGS;
import static io.aklivity.zilla.runtime.exporter.otlp.config.OtlpOptionsConfig.OtlpSignalsConfig.METRICS;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public final class OtlpOptionsConfigBuilder<T> extends ConfigBuilder<T, OtlpOptionsConfigBuilder<T>>
{
    private static final Set<OtlpOptionsConfig.OtlpSignalsConfig> DEFAULT_SIGNALS = Set.of(METRICS, LOGS);
    private static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(30);

    private final Function<OptionsConfig, T> mapper;

    private Long interval;
    private Set<OtlpOptionsConfig.OtlpSignalsConfig> signals;
    private OtlpEndpointConfig endpoint;
    private List<String> keys;
    private List<String> trust;
    private Boolean trustcacerts;
    private String authorization;

    OtlpOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<OtlpOptionsConfigBuilder<T>> thisType()
    {
        return (Class<OtlpOptionsConfigBuilder<T>>) getClass();
    }

    public OtlpOptionsConfigBuilder<T> interval(
        long interval)
    {
        this.interval = interval;
        return this;
    }

    public OtlpOptionsConfigBuilder<T> signals(
        Set<OtlpOptionsConfig.OtlpSignalsConfig> signals)
    {
        this.signals = signals;
        return this;
    }

    public OtlpEndpointConfigBuilder<OtlpOptionsConfigBuilder<T>> endpoint()
    {
        return OtlpEndpointConfig.builder(this::endpoint);
    }

    public OtlpOptionsConfigBuilder<T> endpoint(
        OtlpEndpointConfig endpoint)
    {
        this.endpoint = endpoint;
        return this;
    }

    public OtlpOptionsConfigBuilder<T> keys(
        List<String> keys)
    {
        this.keys = keys;
        return thisType().cast(this);
    }

    public OtlpOptionsConfigBuilder<T> trust(
        List<String> trust)
    {
        this.trust = trust;
        return thisType().cast(this);
    }

    public OtlpOptionsConfigBuilder<T> trustcacerts(
        boolean trustcacerts)
    {
        this.trustcacerts = trustcacerts;
        return thisType().cast(this);
    }

    public OtlpOptionsConfigBuilder<T> authorization(
        String authorization)
    {
        this.authorization = authorization;
        return thisType().cast(this);
    }

    @Override
    public T build()
    {
        final boolean trustcacerts = this.trustcacerts == null ? this.trust == null : this.trustcacerts;
        final Duration interval = this.interval != null ? Duration.ofSeconds(this.interval) : DEFAULT_INTERVAL;
        final Set<OtlpOptionsConfig.OtlpSignalsConfig> signals = this.signals != null ? this.signals : DEFAULT_SIGNALS;
        return mapper.apply(new OtlpOptionsConfig(interval, signals, endpoint, keys, trust, trustcacerts, authorization));
    }
}
