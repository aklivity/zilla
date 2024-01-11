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
package io.aklivity.zilla.runtime.binding.kafka.grpc.internal;


import io.aklivity.zilla.runtime.engine.Configuration;

public class KafkaGrpcConfiguration extends Configuration
{
    private static final ConfigurationDef KAFKA_GRPC_CONFIG;
    public static final PropertyDef<String> KAFKA_GROUP_ID;


    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.binding.kafka.grpc");
        KAFKA_GROUP_ID = config.property("group.id.format", "zilla:%s-%s");
        KAFKA_GRPC_CONFIG = config;
    }

    public KafkaGrpcConfiguration(
        Configuration config)
    {
        super(KAFKA_GRPC_CONFIG, config);
    }

    public String groupIdFormat()
    {
        return KAFKA_GROUP_ID.get(this);
    }
}
