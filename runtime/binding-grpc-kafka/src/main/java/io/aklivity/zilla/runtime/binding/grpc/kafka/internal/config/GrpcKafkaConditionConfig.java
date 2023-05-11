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
package io.aklivity.zilla.runtime.binding.grpc.kafka.internal.config;

import java.util.Map;

import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.String8FW;
import io.aklivity.zilla.runtime.engine.config.ConditionConfig;

public final class GrpcKafkaConditionConfig extends ConditionConfig
{
    public final String service;
    public final String method;
    public final Map<String8FW, GrpcKafkaMetadataValue> metadata;

    public GrpcKafkaConditionConfig(
        String service,
        String method,
        Map<String8FW, GrpcKafkaMetadataValue> metadata)
    {
        this.service = service;
        this.method = method;
        this.metadata = metadata;
    }
}
