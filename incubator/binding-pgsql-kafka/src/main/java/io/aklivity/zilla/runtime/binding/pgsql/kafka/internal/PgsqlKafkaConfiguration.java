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
package io.aklivity.zilla.runtime.binding.pgsql.kafka.internal;

import io.aklivity.zilla.runtime.engine.Configuration;

public class PgsqlKafkaConfiguration extends Configuration
{
    private static final ConfigurationDef PGSQL_KAFKA_CONFIG;

    public static final IntPropertyDef KAFKA_CREATE_TOPICS_REQUEST_TIMEOUT_MS;

    static
    {
        final ConfigurationDef config = new ConfigurationDef(String.format("zilla.binding.%s", PgsqlKafkaBinding.NAME));
        KAFKA_CREATE_TOPICS_REQUEST_TIMEOUT_MS = config.property("kafka.create.topics.request.timeout.ms", 30000);
        PGSQL_KAFKA_CONFIG = config;
    }

    public PgsqlKafkaConfiguration(
        Configuration config)
    {
        super(PGSQL_KAFKA_CONFIG, config);
    }

    public int kafkaCreateTopicsRequestTimeoutMs()
    {
        return KAFKA_CREATE_TOPICS_REQUEST_TIMEOUT_MS.getAsInt(this);
    }
}
