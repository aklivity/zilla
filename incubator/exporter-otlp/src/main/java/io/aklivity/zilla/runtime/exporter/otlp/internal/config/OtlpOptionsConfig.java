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

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public class OtlpOptionsConfig extends OptionsConfig
{
    public long publishInterval;
    public long retryInterval;
    public long warningInterval;
    public OtlpSignalsConfig signals;
    public OtlpEndpointConfig endpoint;

    public OtlpOptionsConfig(
        long publishInterval,
        long retryInterval,
        long warningInterval,
        OtlpSignalsConfig signals,
        OtlpEndpointConfig endpoint)
    {
        this.publishInterval = publishInterval;
        this.retryInterval = retryInterval;
        this.warningInterval = warningInterval;
        this.signals = signals;
        this.endpoint = endpoint;
    }
}
