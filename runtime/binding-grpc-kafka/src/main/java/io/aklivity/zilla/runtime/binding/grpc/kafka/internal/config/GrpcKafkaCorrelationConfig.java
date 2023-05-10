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
package io.aklivity.zilla.runtime.binding.grpc.kafka.internal.config;


import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.String16FW;

public final class GrpcKafkaCorrelationConfig
{
    public final String16FW correlationId;
    public final String16FW service;
    public final String16FW method;
    public final String16FW replyTo;


    public GrpcKafkaCorrelationConfig(
        String16FW correlationId,
        String16FW service,
        String16FW method,
        String16FW replyTo)
    {
        this.correlationId = correlationId;
        this.service = service;
        this.method = method;
        this.replyTo = replyTo;
    }
}
