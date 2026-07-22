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
package io.aklivity.zilla.runtime.binding.grpc.internal.config;

import static io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.GrpcType.BASE64;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.binding.grpc.config.GrpcConditionConfig;
import io.aklivity.zilla.runtime.binding.grpc.config.GrpcMetadataValueConfig;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.GrpcMetadataFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

public final class GrpcConditionMatcher
{
    private final Matcher method;
    private final Map<String8FW, GrpcMetadataValueMatcher> metadataMatch;

    public GrpcConditionMatcher(
        GrpcConditionConfig condition)
    {
        this.method = condition.method != null ? asMatcher(condition.method) : null;
        this.metadataMatch = asMetadataMatch(condition.metadata);
    }

    public boolean matches(
        CharSequence service,
        CharSequence method,
        Array32FW<GrpcMetadataFW> metadataHeaders)
    {
        boolean match = true;

        if (metadataMatch != null)
        {
            for (Map.Entry<String8FW, GrpcMetadataValueMatcher> entry : metadataMatch.entrySet())
            {
                final DirectBufferEx name = entry.getKey().value();
                final GrpcMetadataFW metadata = metadataHeaders.matchFirst(h -> name.compareTo(h.name().value()) == 0);

                final GrpcMetadataValueMatcher value = entry.getValue();
                final DirectBufferEx matcher = metadata != null && metadata.type().get() == BASE64 ?
                    value.base64Value.value() : value.textValue.value();

                match = metadata != null ? matcher.compareTo(metadata.value().value()) == 0 : match;
            }
        }

        return match && matchMethod(String.format("%s/%s", service, method));
    }

    private boolean matchMethod(
        CharSequence path)
    {
        return this.method == null || this.method.reset(path).matches();
    }

    private static Map<String8FW, GrpcMetadataValueMatcher> asMetadataMatch(
        Map<String, GrpcMetadataValueConfig> metadata)
    {
        Map<String8FW, GrpcMetadataValueMatcher> metadataMatch = null;
        if (metadata != null)
        {
            metadataMatch = new HashMap<>();
            for (Map.Entry<String, GrpcMetadataValueConfig> entry : metadata.entrySet())
            {
                metadataMatch.put(new String8FW(entry.getKey()), new GrpcMetadataValueMatcher(entry.getValue()));
            }
        }
        return metadataMatch;
    }

    private static Matcher asMatcher(
        String wildcard)
    {
        return Pattern.compile(wildcard.replace(".", "\\.")
                    .replace("*", ".*")
                    .replaceAll("\\{([a-zA-Z_]+)\\}", "(?<$1>.+)"))
            .matcher("");
    }

    private static final class GrpcMetadataValueMatcher
    {
        private final String16FW textValue;
        private final String16FW base64Value;

        private GrpcMetadataValueMatcher(
            GrpcMetadataValueConfig config)
        {
            this.textValue = config.textValue != null ? new String16FW(config.textValue) : null;
            this.base64Value = config.base64Value != null ? new String16FW(config.base64Value) : null;
        }
    }
}
