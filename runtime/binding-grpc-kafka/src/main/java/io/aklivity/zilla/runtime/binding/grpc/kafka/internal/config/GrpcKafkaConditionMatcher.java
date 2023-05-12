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

import static io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.stream.GrpcType.BASE64;

import java.util.Map;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.stream.GrpcMetadataFW;

public final class GrpcKafkaConditionMatcher
{
    private static final String16FW WILDCARD = new String16FW("*");

    private final String16FW service;
    private final String16FW method;
    private final Map<String8FW, GrpcKafkaMetadataValue> metadataMatch;

    public GrpcKafkaConditionMatcher(
        GrpcKafkaConditionConfig condition)
    {
        this.service = condition.service != null ? new String16FW(condition.service) : null;
        this.method = condition.method != null ? new String16FW(condition.method) : null;
        this.metadataMatch = condition.metadata;
    }

    public boolean matches(
        String16FW service,
        String16FW method,
        Array32FW<GrpcMetadataFW> metadataHeaders)
    {
        boolean match = true;

        if (metadataMatch != null)
        {
            for (Map.Entry<String8FW, GrpcKafkaMetadataValue> entry : metadataMatch.entrySet())
            {
                final DirectBuffer name = entry.getKey().value();
                final GrpcMetadataFW metadata = metadataHeaders.matchFirst(h -> name.compareTo(h.name().value()) == 0);

                final GrpcKafkaMetadataValue value = entry.getValue();
                final DirectBuffer matcher = metadata != null && metadata.type().get() == BASE64 ?
                    value.base64Value.value() : value.textValue.value();

                match = metadata != null ? matcher.compareTo(metadata.value().value()) == 0 : match;
            }
        }

        return match && matchService(service) && matchMethod(method);
    }

    private boolean matchService(
        String16FW service)
    {
        return this.service == null || this.service.value().compareTo(service.value()) == 0;
    }

    private boolean matchMethod(
        String16FW method)
    {
        return this.method == null || WILDCARD.value().compareTo(this.method.value()) == 0 ||
            this.method.value().compareTo(method.value()) == 0;
    }
}
