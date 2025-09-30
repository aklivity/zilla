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

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public class OtlpOptionsConfig extends OptionsConfig
{
    public enum OtlpSignalsConfig
    {
        METRICS,
        LOGS,
    }

    public final Duration interval;
    public final Set<OtlpSignalsConfig> signals;
    public final OtlpEndpointConfig endpoint;
    public final List<String> keys;
    public final List<String> trust;
    public final boolean trustcacerts;
    public final String authorization;

    public static OtlpOptionsConfigBuilder<OtlpOptionsConfig> builder()
    {
        return new OtlpOptionsConfigBuilder<>(OtlpOptionsConfig.class::cast);
    }

    public static <T> OtlpOptionsConfigBuilder<T> builder(
        Function<OptionsConfig, T> mapper)
    {
        return new OtlpOptionsConfigBuilder<>(mapper);
    }

    protected OtlpOptionsConfig(
        Duration interval,
        Set<OtlpSignalsConfig> signals,
        OtlpEndpointConfig endpoint,
        List<String> keys,
        List<String> trust,
        boolean trustcacerts,
        String authorization)
    {
        this.interval = interval;
        this.signals = signals;
        this.endpoint = endpoint;
        this.keys = keys;
        this.trust = trust;
        this.trustcacerts = trustcacerts;
        this.authorization = authorization;
    }
}
