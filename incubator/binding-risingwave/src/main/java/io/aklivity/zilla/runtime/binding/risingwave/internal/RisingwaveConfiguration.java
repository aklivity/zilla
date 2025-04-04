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
package io.aklivity.zilla.runtime.binding.risingwave.internal;

import io.aklivity.zilla.runtime.engine.Configuration;

public class RisingwaveConfiguration extends Configuration
{
    private static final ConfigurationDef RISINGWAVE_CONFIG;

    public static final LongPropertyDef KAFKA_SCAN_STARTUP_TIMESTAMP_MILLIS;

    static
    {
        final ConfigurationDef config = new ConfigurationDef(String.format("zilla.binding.%s", RisingwaveBinding.NAME));
        KAFKA_SCAN_STARTUP_TIMESTAMP_MILLIS = config.property("kafka.scan.startup.timestamp.millis", 140000000L);
        RISINGWAVE_CONFIG = config;
    }

    public RisingwaveConfiguration(
        Configuration config)
    {
        super(RISINGWAVE_CONFIG, config);
    }

    public long kafkaScanStartupTimestampMillis()
    {
        return KAFKA_SCAN_STARTUP_TIMESTAMP_MILLIS.getAsLong(this);
    }
}
