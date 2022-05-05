/*
 * Copyright 2021-2022 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.sse.kafka.internal;

import io.aklivity.zilla.runtime.engine.Configuration;

public class SseKafkaConfiguration extends Configuration
{
    public static final IntPropertyDef SSE_KAFKA_MAXIMUM_KEY_LENGTH;

    private static final ConfigurationDef SSE_KAFKA_CONFIG;

    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.binding.sse.kafka");
        SSE_KAFKA_MAXIMUM_KEY_LENGTH = config.property("maximum.key.length", 1024);
        SSE_KAFKA_CONFIG = config;
    }

    public SseKafkaConfiguration(
        Configuration config)
    {
        super(SSE_KAFKA_CONFIG, config);
    }

    public int maximumKeyLength()
    {
        return SSE_KAFKA_MAXIMUM_KEY_LENGTH.getAsInt(this);
    }
}
