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

public class OtlpEndpointConfig
{
    public final String protocol;
    public final URI location;
    public final OtlpOverridesConfig overrides;

    public static OtlpEndpointConfigBuilder<OtlpEndpointConfig> builder()
    {
        return new OtlpEndpointConfigBuilder<>(OtlpEndpointConfig.class::cast);
    }

    public static <T> OtlpEndpointConfigBuilder<T> builder(
        Function<OtlpEndpointConfig, T> mapper)
    {
        return new OtlpEndpointConfigBuilder<>(mapper);
    }

    protected OtlpEndpointConfig(
        String protocol,
        URI location,
        OtlpOverridesConfig overrides)
    {
        this.protocol = protocol;
        this.location = location;
        this.overrides = overrides;
    }
}
