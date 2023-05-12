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
package io.aklivity.zilla.runtime.binding.http.kafka.internal;

import io.aklivity.zilla.runtime.engine.Configuration;

public class HttpKafkaConfiguration extends Configuration
{
    private static final ConfigurationDef HTTP_KAFKA_CONFIG;

    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.binding.http.kafka");
        HTTP_KAFKA_CONFIG = config;
    }

    public HttpKafkaConfiguration(
        Configuration config)
    {
        super(HTTP_KAFKA_CONFIG, config);
    }
}
