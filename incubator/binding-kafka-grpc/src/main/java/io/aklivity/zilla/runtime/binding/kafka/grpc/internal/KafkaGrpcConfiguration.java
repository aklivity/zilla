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
package io.aklivity.zilla.runtime.binding.kafka.grpc.internal;

import io.aklivity.zilla.runtime.engine.Configuration;

public class KafkaGrpcConfiguration extends Configuration
{
    private static final ConfigurationDef KAFKA_GRPC_CONFIG;

    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.binding.kafka.grpc");
        KAFKA_GRPC_CONFIG = config;
    }

    public KafkaGrpcConfiguration(
        Configuration config)
    {
        super(KAFKA_GRPC_CONFIG, config);
    }
}
