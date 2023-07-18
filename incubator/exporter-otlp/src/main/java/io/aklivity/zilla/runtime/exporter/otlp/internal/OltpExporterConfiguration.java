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

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

import io.aklivity.zilla.runtime.engine.Configuration;

public class OltpExporterConfiguration extends Configuration
{
    public static final LongPropertyDef OTLP_EXPORTER_RETRY_INTERVAL;
    public static final LongPropertyDef OTLP_EXPORTER_WARNING_INTERVAL;

    private static final ConfigurationDef OTLP_EXPORTER_CONFIG;

    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.exporter.otlp");
        OTLP_EXPORTER_RETRY_INTERVAL = config.property("retry.interval", SECONDS.toMillis(10L));
        OTLP_EXPORTER_WARNING_INTERVAL = config.property("warning.interval", MINUTES.toMillis(5L));
        OTLP_EXPORTER_CONFIG = config;
    }

    public OltpExporterConfiguration(
        Configuration config)
    {
        super(OTLP_EXPORTER_CONFIG, config);
    }

    public long retryInterval()
    {
        return OTLP_EXPORTER_RETRY_INTERVAL.getAsLong(this);
    }

    public long warningInterval()
    {
        return OTLP_EXPORTER_WARNING_INTERVAL.getAsLong(this);
    }
}
