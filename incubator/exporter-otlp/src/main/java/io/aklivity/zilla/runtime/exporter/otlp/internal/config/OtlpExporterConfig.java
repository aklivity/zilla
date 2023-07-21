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
package io.aklivity.zilla.runtime.exporter.otlp.internal.config;

import static io.aklivity.zilla.runtime.exporter.otlp.internal.config.OtlpOptionsConfig.OtlpSignalsConfig.METRICS;

import java.net.URI;
import java.util.Set;

import io.aklivity.zilla.runtime.engine.config.ExporterConfig;

public class OtlpExporterConfig
{
    private static final String DEFAULT_METRICS_PATH = "/v1/metrics";
    private static final Set<OtlpOptionsConfig.OtlpSignalsConfig> DEFAULT_SIGNALS = Set.of(METRICS);
    private static final String DEFAULT_PROTOCOL = "http";

    private final OtlpOptionsConfig options;

    public OtlpExporterConfig(
        ExporterConfig exporter)
    {
        this.options = (OtlpOptionsConfig)exporter.options;
    }

    public OtlpOptionsConfig options()
    {
        return options;
    }

    public URI resolveMetrics()
    {
        assert options != null;
        assert options.endpoint != null;
        assert options.endpoint.location != null;

        URI result;
        URI location = options.endpoint.location;
        if (options.endpoint.overrides != null && options.endpoint.overrides.metrics != null)
        {
            result = location.resolve(options.endpoint.overrides.metrics);
        }
        else
        {
            result = location.resolve(DEFAULT_METRICS_PATH);
        }
        return result;
    }

    public Set<OtlpOptionsConfig.OtlpSignalsConfig> resolveSignals()
    {
        assert options != null;

        Set<OtlpOptionsConfig.OtlpSignalsConfig> result;
        if (options.signals == null)
        {
            result = DEFAULT_SIGNALS;
        }
        else
        {
            result = options.signals;
        }
        return result;
    }

    public String resolveProtocol()
    {
        assert options != null;
        assert options.endpoint != null;

        String result;
        if (options.endpoint.protocol == null)
        {
            result = DEFAULT_PROTOCOL;
        }
        else
        {
            result = options.endpoint.protocol;
        }
        return result;
    }
}
