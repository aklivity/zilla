/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.mcp.internal.config;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.zip.CRC32C;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;

public final class McpAggregateEventId
{
    static final byte PAIR_DELIMITER = (byte) ';';
    static final byte KEY_VALUE_DELIMITER = (byte) '=';
    static final int MAX_PREFIX_LENGTH = 6;

    private McpAggregateEventId()
    {
    }

    public static Map<String, String> computePrefixes(
        Collection<String> toolkits)
    {
        if (toolkits == null || toolkits.isEmpty())
        {
            return Map.of();
        }

        final Set<String> distinct = new HashSet<>(toolkits);
        final Map<String, String> encoded = distinct.stream()
            .collect(toMap(identity(), McpAggregateEventId::encodeCrc32c));

        int length = 1;
        while (length <= MAX_PREFIX_LENGTH)
        {
            final Set<String> seen = new HashSet<>();
            boolean unique = true;
            for (String code : encoded.values())
            {
                if (!seen.add(code.substring(0, length)))
                {
                    unique = false;
                    break;
                }
            }
            if (unique)
            {
                break;
            }
            length++;
        }

        if (length > MAX_PREFIX_LENGTH)
        {
            throw new IllegalStateException("unable to derive unique prefixes for toolkits: " + distinct);
        }

        final int prefixLength = length;
        return encoded.entrySet().stream()
            .collect(toMap(Map.Entry::getKey, e -> e.getValue().substring(0, prefixLength)));
    }

    public static int encode(
        McpAggregateRoute[] routes,
        Long2ObjectHashMap<String> ids,
        MutableDirectBufferEx buffer,
        int offset)
    {
        int progress = offset;
        for (McpAggregateRoute route : routes)
        {
            final String id = ids.get(route.routedId());
            if (id == null)
            {
                continue;
            }
            if (progress > offset)
            {
                buffer.putByte(progress++, PAIR_DELIMITER);
            }
            progress += buffer.putStringWithoutLengthUtf8(progress, route.prefix());
            buffer.putByte(progress++, KEY_VALUE_DELIMITER);
            progress += buffer.putStringWithoutLengthUtf8(progress, id);
        }
        return progress == offset ? -1 : progress - offset;
    }

    public static void decode(
        String aggregate,
        BiConsumer<String, String> visitor)
    {
        if (aggregate == null || aggregate.isEmpty())
        {
            return;
        }

        int start = 0;
        final int length = aggregate.length();
        while (start < length)
        {
            int end = aggregate.indexOf((char) PAIR_DELIMITER, start);
            if (end < 0)
            {
                end = length;
            }
            final int sep = aggregate.indexOf((char) KEY_VALUE_DELIMITER, start);
            if (sep > start && sep < end)
            {
                final String prefix = aggregate.substring(start, sep);
                final String value = aggregate.substring(sep + 1, end);
                visitor.accept(prefix, value);
            }
            start = end + 1;
        }
    }

    private static String encodeCrc32c(
        String toolkit)
    {
        final CRC32C crc = new CRC32C();
        crc.update(toolkit.getBytes(StandardCharsets.UTF_8));
        final long value = crc.getValue();
        final byte[] bytes = new byte[]
        {
            (byte) (value >>> 24),
            (byte) (value >>> 16),
            (byte) (value >>> 8),
            (byte) value
        };
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
