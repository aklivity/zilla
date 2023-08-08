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
package io.aklivity.zilla.runtime.binding.grpc.internal.config;

import static io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.GrpcType.BASE64;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.binding.grpc.config.GrpcConditionConfig;
import io.aklivity.zilla.runtime.binding.grpc.config.GrpcMetadataValueConfig;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.GrpcMetadataFW;

public final class GrpcConditionMatcher
{
    private final Matcher method;
    private final Map<String8FW, GrpcMetadataValueConfig> metadataMatch;

    public GrpcConditionMatcher(
        GrpcConditionConfig condition)
    {
        this.method = condition.method != null ? asMatcher(condition.method) : null;
        this.metadataMatch = condition.metadata;
    }

    public boolean matches(
        CharSequence service,
        CharSequence method,
        Array32FW<GrpcMetadataFW> metadataHeaders)
    {
        boolean match = true;

        if (metadataMatch != null)
        {
            for (Map.Entry<String8FW, GrpcMetadataValueConfig> entry : metadataMatch.entrySet())
            {
                final DirectBuffer name = entry.getKey().value();
                final GrpcMetadataFW metadata = metadataHeaders.matchFirst(h -> name.compareTo(h.name().value()) == 0);

                final GrpcMetadataValueConfig value = entry.getValue();
                final DirectBuffer matcher = metadata != null && metadata.type().get() == BASE64 ?
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

    private static Matcher asMatcher(
        String wildcard)
    {
        return Pattern.compile(wildcard.replace(".", "\\.")
                    .replace("*", ".*")
                    .replaceAll("\\{([a-zA-Z_]+)\\}", "(?<$1>.+)"))
            .matcher("");
    }
}
