/*
 * Copyright 2021-2026 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.http.internal.stream;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import io.aklivity.zilla.runtime.binding.http.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.http.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

/**
 * Looks up the headers of a decoded request by name, in place, without creating a {@code String} per header.
 * <p>
 * The view is valid only until the buffer holding the headers is overwritten. A header name that occurs more
 * than once resolves to its last value, as it does in {@link #materialize()}.
 */
final class HttpRequestHeaders extends AbstractMap<String, String>
{
    private static final int INITIAL_CAPACITY = 32;
    private static final int NO_SLOT = 0;

    private final Consumer<HttpHeaderFW> indexer = this::index;

    private DirectBufferEx buffer;
    private int[] nameOffsets = new int[INITIAL_CAPACITY];
    private int[] nameLengths = new int[INITIAL_CAPACITY];
    private int[] valueOffsets = new int[INITIAL_CAPACITY];
    private int[] valueLengths = new int[INITIAL_CAPACITY];
    private int[] slots = new int[INITIAL_CAPACITY * 2];
    private int count;

    void reset(
        Array32FW<HttpHeaderFW> headers)
    {
        this.buffer = headers.buffer();
        this.count = 0;
        headers.forEach(indexer);
    }

    boolean duplicates()
    {
        boolean duplicates = false;

        if (count > 1)
        {
            int capacity = Integer.highestOneBit(count * 2 - 1) << 1;
            if (slots.length < capacity)
            {
                slots = new int[capacity];
            }
            else
            {
                capacity = slots.length;
            }
            Arrays.fill(slots, 0, capacity, NO_SLOT);

            final int mask = capacity - 1;
            for (int header = 0; !duplicates && header < count; header++)
            {
                int slot = hashOfName(header) & mask;
                while (!duplicates && slots[slot] != NO_SLOT)
                {
                    duplicates = sameName(slots[slot] - 1, header);
                    slot = (slot + 1) & mask;
                }
                slots[slot] = header + 1;
            }
        }

        return duplicates;
    }

    Map<String, String> materialize()
    {
        final Map<String, String> materialized = new LinkedHashMap<>();

        for (int header = 0; header < count; header++)
        {
            final String name = buffer.getStringWithoutLengthUtf8(nameOffsets[header], nameLengths[header]);
            final String value = buffer.getStringWithoutLengthUtf8(valueOffsets[header], valueLengths[header]);
            materialized.put(name.toLowerCase(), value);
        }

        return materialized;
    }

    @Override
    public String get(
        Object key)
    {
        final int header = key instanceof String name ? lastIndexOf(name) : -1;

        return header != -1
            ? buffer.getStringWithoutLengthUtf8(valueOffsets[header], valueLengths[header])
            : null;
    }

    @Override
    public boolean containsKey(
        Object key)
    {
        return key instanceof String name && lastIndexOf(name) != -1;
    }

    @Override
    public Set<Entry<String, String>> entrySet()
    {
        return materialize().entrySet();
    }

    private void index(
        HttpHeaderFW header)
    {
        if (count == nameOffsets.length)
        {
            final int capacity = count << 1;
            nameOffsets = Arrays.copyOf(nameOffsets, capacity);
            nameLengths = Arrays.copyOf(nameLengths, capacity);
            valueOffsets = Arrays.copyOf(valueOffsets, capacity);
            valueLengths = Arrays.copyOf(valueLengths, capacity);
        }

        nameOffsets[count] = header.name().offset() + header.name().fieldSizeLength();
        nameLengths[count] = header.name().length();
        valueOffsets[count] = header.value().offset() + header.value().fieldSizeLength();
        valueLengths[count] = header.value().length();
        count++;
    }

    private int lastIndexOf(
        String name)
    {
        final boolean ascii = !nonAscii(name);

        int header = count - 1;
        while (header >= 0 && !(ascii ? nameEquals(header, name) : nameDecodedEquals(header, name)))
        {
            header--;
        }

        return header;
    }

    private boolean nameEquals(
        int header,
        String name)
    {
        final int length = nameLengths[header];
        boolean equals = length == name.length();

        for (int index = 0; equals && index < length; index++)
        {
            equals = name.charAt(index) == (buffer.getByte(nameOffsets[header] + index) & 0xff);
        }

        return equals;
    }

    private boolean nameDecodedEquals(
        int header,
        String name)
    {
        final String decoded = buffer.getStringWithoutLengthUtf8(nameOffsets[header], nameLengths[header]);

        return decoded.toLowerCase().equals(name);
    }

    private boolean sameName(
        int header,
        int other)
    {
        final int length = nameLengths[header];
        boolean same = length == nameLengths[other];

        for (int index = 0; same && index < length; index++)
        {
            same = buffer.getByte(nameOffsets[header] + index) == buffer.getByte(nameOffsets[other] + index);
        }

        return same;
    }

    private int hashOfName(
        int header)
    {
        int hash = 0;

        for (int index = 0; index < nameLengths[header]; index++)
        {
            hash = 31 * hash + buffer.getByte(nameOffsets[header] + index);
        }

        return hash ^ (hash >>> 16);
    }

    private static boolean nonAscii(
        String name)
    {
        boolean nonAscii = false;

        for (int index = 0; !nonAscii && index < name.length(); index++)
        {
            nonAscii = name.charAt(index) >= 0x80;
        }

        return nonAscii;
    }
}
