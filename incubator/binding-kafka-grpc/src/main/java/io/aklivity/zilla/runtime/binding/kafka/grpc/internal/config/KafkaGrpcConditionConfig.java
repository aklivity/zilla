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
package io.aklivity.zilla.runtime.binding.kafka.grpc.internal.config;

import java.util.Map;
import java.util.Optional;

import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.String8FW;
import io.aklivity.zilla.runtime.engine.config.ConditionConfig;


public final class KafkaGrpcConditionConfig extends ConditionConfig
{
    public final String16FW topic;
    public final Optional<String16FW> key;
    public final Optional<Map<String8FW, String16FW>> headers;
    public final String16FW replyTo;
    public final Optional<String16FW> service;
    public final Optional<String16FW> method;

    public KafkaGrpcConditionConfig(
        String16FW topic,
        String16FW key,
        String16FW replyTo,
        Map<String8FW, String16FW> headers,
        String16FW service,
        String16FW method)
    {
        this.topic = topic;
        this.key =  Optional.ofNullable(key);
        this.headers = Optional.ofNullable(headers);
        this.replyTo = replyTo;
        this.service =  Optional.ofNullable(service);
        this.method =  Optional.ofNullable(method);
    }
}
