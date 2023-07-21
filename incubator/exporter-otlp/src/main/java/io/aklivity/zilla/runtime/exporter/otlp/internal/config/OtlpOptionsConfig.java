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

import java.util.Set;

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public class OtlpOptionsConfig extends OptionsConfig
{
    public enum OtlpSignalsConfig
    {
        METRICS,
    }

    public long interval;
    public Set<OtlpSignalsConfig> signals;
    public OtlpEndpointConfig endpoint;

    public OtlpOptionsConfig(
        long interval,
        Set<OtlpSignalsConfig> signals,
        OtlpEndpointConfig endpoint)
    {
        this.interval = interval;
        this.signals = signals;
        this.endpoint = endpoint;
    }
}
