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

import java.net.URI;

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public class OtlpEndpointConfig extends OptionsConfig
{
    private static final String DEFAULT_METRICS_URI = "/v1/metrics";

    public String protocol;
    public URI location;
    public OtlpOverridesConfig overrides;

    public OtlpEndpointConfig(
        String protocol,
        URI location,
        OtlpOverridesConfig overrides)
    {
        this.protocol = protocol;
        this.location = location;
        this.overrides = overrides;
    }

    public URI resolveMetrics()
    {
        URI result;
        if (overrides != null && overrides.metrics != null)
        {
            result = location.resolve(overrides.metrics);
        }
        else
        {
            result = location.resolve(DEFAULT_METRICS_URI);
        }
        return result;
    }
}
