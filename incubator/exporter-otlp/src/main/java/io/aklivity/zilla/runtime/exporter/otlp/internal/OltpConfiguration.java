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
package io.aklivity.zilla.runtime.exporter.otlp.internal;

import java.time.Duration;

import io.aklivity.zilla.runtime.engine.Configuration;

public class OltpConfiguration extends Configuration
{
    public static final PropertyDef<Duration> OTLP_EXPORTER_RETRY_INTERVAL;
    public static final PropertyDef<Duration> OTLP_EXPORTER_TIMEOUT_INTERVAL;
    public static final PropertyDef<Duration> OTLP_EXPORTER_WARNING_INTERVAL;

    private static final ConfigurationDef OTLP_EXPORTER_CONFIG;

    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.exporter.otlp");
        OTLP_EXPORTER_RETRY_INTERVAL = config.property(Duration.class, "retry.interval",
            (c, v) -> Duration.parse(v), "PT10S");
        OTLP_EXPORTER_TIMEOUT_INTERVAL = config.property(Duration.class, "timeout.interval",
            (c, v) -> Duration.parse(v), "PT30S");
        OTLP_EXPORTER_WARNING_INTERVAL = config.property(Duration.class, "warning.interval",
            (c, v) -> Duration.parse(v), "PT5M");
        OTLP_EXPORTER_CONFIG = config;
    }

    public OltpConfiguration(
        Configuration config)
    {
        super(OTLP_EXPORTER_CONFIG, config);
    }

    public Duration retryInterval()
    {
        return OTLP_EXPORTER_RETRY_INTERVAL.get(this);
    }

    public Duration timeoutInterval()
    {
        return OTLP_EXPORTER_TIMEOUT_INTERVAL.get(this);
    }

    public Duration warningInterval()
    {
        return OTLP_EXPORTER_WARNING_INTERVAL.get(this);
    }
}
