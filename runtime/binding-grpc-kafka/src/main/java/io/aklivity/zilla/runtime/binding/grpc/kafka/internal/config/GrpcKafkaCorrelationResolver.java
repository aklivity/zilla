/*
 * Copyright 2021-2026 Aklivity Inc
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

import io.aklivity.zilla.config.binding.grpc.kafka.GrpcKafkaCorrelationConfig;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.String16FW;

public final class GrpcKafkaCorrelationResolver
{
    public final String16FW correlationId;
    public final String16FW service;
    public final String16FW method;
    public final String16FW replyTo;

    public GrpcKafkaCorrelationResolver(
        GrpcKafkaCorrelationConfig correlation)
    {
        this.correlationId = new String16FW(correlation.correlationId);
        this.service = new String16FW(correlation.service);
        this.method = new String16FW(correlation.method);
        this.replyTo = new String16FW(correlation.replyTo);
    }
}
