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
package io.aklivity.zilla.runtime.binding.grpc.internal.config;

import java.util.Base64;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.binding.grpc.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.String8FW;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Object2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

public final class GrpcConditionMatcher
{
    private final byte[] base64RW = new byte[256];
    private final String16FW.Builder stringRW = new String16FW.Builder().wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);
    private final MutableDirectBuffer bufferRW = new UnsafeBuffer(0L, 0);
    private final Base64.Encoder encoder64 = Base64.getUrlEncoder();
    private final Matcher method;
    public final Map<String8FW, String16FW> metadataMatch;
    public final Map<String8FW, BinMetadata> metadataBinMatch;

    public GrpcConditionMatcher(
        GrpcConditionConfig condition)
    {
        this.method = condition.method != null ? asMatcher(condition.method) : null;
        this.metadataMatch = condition.metadata;
        this.metadataBinMatch = new Object2ObjectHashMap<>();
        this.metadataMatch.forEach((k, v) ->
        {
            final DirectBuffer buffer = v.value();
            final int index = 0;
            final int length = buffer.capacity();
            String16FW encodedBuf = encode16(buffer, index, length);
            metadataBinMatch.put(k, new BinMetadata(new String8FW(String.format("%s-bin", k.asString())), encodedBuf));
        });
    }

    public boolean matches(
        CharSequence path,
        Function<String8FW, String16FW> metadataByName)
    {
        boolean match = true;

        if (metadataMatch != null)
        {
            for (Map.Entry<String8FW, String16FW> entry : metadataMatch.entrySet())
            {
                String8FW name = entry.getKey();
                String16FW matcher = entry.getValue();

                String16FW value = metadataByName.apply(name);
                match = matcher.equals(value);
                if (!match)
                {
                    BinMetadata binMetadata = metadataBinMatch.get(name);
                    String8FW binName = binMetadata.name;
                    String16FW binMatcher = binMetadata.value;
                    String16FW binValue = metadataByName.apply(binName);
                    match = binMatcher.equals(binValue);
                }
            }
        }

        return match && matchMethod(path);
    }

    private boolean matchMethod(
        CharSequence path)
    {
        return this.method == null || this.method.reset(path).matches();
    }

    private static Matcher asMatcher(
        String wildcard)
    {
        return Pattern.compile(wildcard
                .replace(".", "\\.")
                .replace("$", "\\$")
                .replace("+", "[^/]*")
                .replace("#", ".*"))
            .matcher("");
    }

    private String16FW encode16(
        final DirectBuffer buffer,
        final int index,
        final int length)
    {
        final byte[] encodableRaw = new byte[length];
        buffer.getBytes(index, encodableRaw);

        final byte[] encodedBase64 = base64RW;
        final int encodedBytes = encoder64.encode(encodableRaw, encodedBase64);
        MutableDirectBuffer encodeBuf = bufferRW;
        encodeBuf.wrap(encodedBase64, 0, encodedBytes);
        return stringRW.set(encodeBuf, 0, encodeBuf.capacity()).build();
    }

    private class BinMetadata
    {
        public String8FW name;
        public String16FW value;

        BinMetadata(
            String8FW name,
            String16FW value)
        {

            this.name = name;
            this.value = value;
        }
    }
}
