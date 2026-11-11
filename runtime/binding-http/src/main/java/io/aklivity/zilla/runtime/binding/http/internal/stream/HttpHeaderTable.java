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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.aklivity.zilla.runtime.binding.http.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.http.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

/**
 * Holds the headers of a decoded message as bytes, keyed by name in insertion order, without creating a
 * {@code String} per header. A name that is added more than once has its values joined with a comma, as
 * {@link Map#merge} of the joined values would, and the entry keeps its first position.
 * <p>
 * It is a {@link Map} so rarely used paths can treat it as one; {@link #entrySet()} is a copy, and entries can
 * only be removed by {@link #clear()}.
 */
final class HttpHeaderTable extends AbstractMap<String, String>
{
    private static final int INITIAL_BYTES = 4096;
    private static final int INITIAL_ENTRIES = 32;
    private static final int MAXIMUM_RETAINED_BYTES = 256 * 1024;
    private static final byte[] SEPARATOR = ", ".getBytes(UTF_8);

    private final Consumer<HttpHeaderFW.Builder> itemWriter = this::writeItem;

    private byte[] bytes = new byte[INITIAL_BYTES];
    private final UnsafeBufferEx buffer = new UnsafeBufferEx(bytes);
    private int used;
    private int[] nameOffsets = new int[INITIAL_ENTRIES];
    private int[] nameLengths = new int[INITIAL_ENTRIES];
    private int[] valueOffsets = new int[INITIAL_ENTRIES];
    private int[] valueLengths = new int[INITIAL_ENTRIES];
    private boolean[] nonAscii = new boolean[INITIAL_ENTRIES];
    private String[] values = new String[INITIAL_ENTRIES];
    private int[] slots = new int[INITIAL_ENTRIES * 2];
    private int count;
    private int writing;

    void add(
        DirectBufferEx name,
        DirectBufferEx value)
    {
        final int nameLength = name.capacity();
        final int valueLength = value.capacity();
        final int hash = hashOf(name, nameLength);
        final int header = find(name, nameLength, hash);

        if (header == -1)
        {
            ensureEntry();
            ensureBytes(nameLength + valueLength);

            nameOffsets[count] = used;
            nameLengths[count] = nameLength;
            buffer.putBytes(used, name, 0, nameLength);
            used += nameLength;

            valueOffsets[count] = used;
            valueLengths[count] = valueLength;
            buffer.putBytes(used, value, 0, valueLength);
            used += valueLength;

            nonAscii[count] = hasHighBit(nameOffsets[count], nameLength) || hasHighBit(valueOffsets[count], valueLength);
            insert(count, hash);
            count++;
        }
        else
        {
            final int previousOffset = valueOffsets[header];
            final int previousLength = valueLengths[header];

            ensureBytes(previousLength + SEPARATOR.length + valueLength);

            final int joinedOffset = used;
            buffer.putBytes(used, buffer, previousOffset, previousLength);
            used += previousLength;
            buffer.putBytes(used, SEPARATOR);
            used += SEPARATOR.length;
            buffer.putBytes(used, value, 0, valueLength);
            used += valueLength;

            valueOffsets[header] = joinedOffset;
            valueLengths[header] = previousLength + SEPARATOR.length + valueLength;
            values[header] = null;
            nonAscii[header] |= hasHighBit(joinedOffset, valueLengths[header]);
        }
    }

    void writeTo(
        Array32FW.Builder<HttpHeaderFW.Builder, HttpHeaderFW> items)
    {
        for (writing = 0; writing < count; writing++)
        {
            items.item(itemWriter);
        }
    }

    @Override
    public void clear()
    {
        Arrays.fill(values, 0, count, null);
        count = 0;
        used = 0;
        Arrays.fill(slots, 0);

        if (bytes.length > MAXIMUM_RETAINED_BYTES)
        {
            bytes = new byte[INITIAL_BYTES];
            buffer.wrap(bytes);
        }
    }

    @Override
    public int size()
    {
        return count;
    }

    @Override
    public String get(
        Object key)
    {
        final int header = key instanceof String name ? find(name) : -1;

        return header != -1 ? valueAt(header) : null;
    }

    @Override
    public boolean containsKey(
        Object key)
    {
        return key instanceof String name && find(name) != -1;
    }

    @Override
    public String put(
        String name,
        String value)
    {
        final byte[] nameBytes = name.getBytes(UTF_8);
        final byte[] valueBytes = value.getBytes(UTF_8);
        final UnsafeBufferEx nameView = new UnsafeBufferEx(nameBytes);
        final int hash = hashOf(nameView, nameBytes.length);
        final int header = find(nameView, nameBytes.length, hash);

        final String previous;
        if (header == -1)
        {
            add(nameView, new UnsafeBufferEx(valueBytes));
            previous = null;
        }
        else
        {
            previous = valueAt(header);

            ensureBytes(valueBytes.length);
            valueOffsets[header] = used;
            valueLengths[header] = valueBytes.length;
            values[header] = null;
            buffer.putBytes(used, valueBytes);
            used += valueBytes.length;
            nonAscii[header] = hasHighBit(nameOffsets[header], nameLengths[header]) ||
                               hasHighBit(valueOffsets[header], valueLengths[header]);
        }

        return previous;
    }

    @Override
    public String remove(
        Object key)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void forEach(
        BiConsumer<? super String, ? super String> action)
    {
        for (int header = 0; header < count; header++)
        {
            action.accept(nameAt(header), valueAt(header));
        }
    }

    @Override
    public Set<Entry<String, String>> entrySet()
    {
        final Map<String, String> copy = new LinkedHashMap<>();

        for (int header = 0; header < count; header++)
        {
            copy.put(nameAt(header), valueAt(header));
        }

        return copy.entrySet();
    }

    private void writeItem(
        HttpHeaderFW.Builder item)
    {
        if (nonAscii[writing])
        {
            item.name(nameAt(writing)).value(valueAt(writing));
        }
        else
        {
            item.name(buffer, nameOffsets[writing], nameLengths[writing])
                .value(buffer, valueOffsets[writing], valueLengths[writing]);
        }
    }

    private String nameAt(
        int header)
    {
        return buffer.getStringWithoutLengthUtf8(nameOffsets[header], nameLengths[header]);
    }

    private String valueAt(
        int header)
    {
        String value = values[header];

        if (value == null)
        {
            value = buffer.getStringWithoutLengthUtf8(valueOffsets[header], valueLengths[header]);
            values[header] = value;
        }

        return value;
    }

    private int find(
        String name)
    {
        final int found;

        if (isAscii(name))
        {
            final int mask = slots.length - 1;

            int match = -1;
            int slot = hashOf(name) & mask;
            while (match == -1 && slots[slot] != 0)
            {
                final int header = slots[slot] - 1;
                if (nameLengths[header] == name.length() && sameChars(header, name))
                {
                    match = header;
                }
                slot = (slot + 1) & mask;
            }
            found = match;
        }
        else
        {
            final byte[] nameBytes = name.getBytes(UTF_8);
            final UnsafeBufferEx nameView = new UnsafeBufferEx(nameBytes);

            found = find(nameView, nameBytes.length, hashOf(nameView, nameBytes.length));
        }

        return found;
    }

    private int find(
        DirectBufferEx name,
        int nameLength,
        int hash)
    {
        final int mask = slots.length - 1;

        int found = -1;
        int slot = hash & mask;
        while (found == -1 && slots[slot] != 0)
        {
            final int header = slots[slot] - 1;
            if (nameLengths[header] == nameLength && sameBytes(header, name, nameLength))
            {
                found = header;
            }
            slot = (slot + 1) & mask;
        }

        return found;
    }

    private boolean sameChars(
        int header,
        String name)
    {
        boolean same = true;
        for (int index = 0; same && index < name.length(); index++)
        {
            same = buffer.getByte(nameOffsets[header] + index) == name.charAt(index);
        }
        return same;
    }

    private static boolean isAscii(
        String name)
    {
        boolean ascii = true;
        for (int index = 0; ascii && index < name.length(); index++)
        {
            ascii = name.charAt(index) < 0x80;
        }
        return ascii;
    }

    private static int hashOf(
        String name)
    {
        int hash = 0;
        for (int index = 0; index < name.length(); index++)
        {
            hash = 31 * hash + name.charAt(index);
        }
        return hash ^ (hash >>> 16);
    }

    private boolean sameBytes(
        int header,
        DirectBufferEx name,
        int nameLength)
    {
        boolean same = true;
        for (int index = 0; same && index < nameLength; index++)
        {
            same = buffer.getByte(nameOffsets[header] + index) == name.getByte(index);
        }
        return same;
    }

    private void insert(
        int header,
        int hash)
    {
        if ((count + 1) * 2 > slots.length)
        {
            slots = new int[slots.length << 1];
            for (int existing = 0; existing < header; existing++)
            {
                place(existing, hashOf(buffer, nameOffsets[existing], nameLengths[existing]));
            }
        }

        place(header, hash);
    }

    private void place(
        int header,
        int hash)
    {
        final int mask = slots.length - 1;

        int slot = hash & mask;
        while (slots[slot] != 0)
        {
            slot = (slot + 1) & mask;
        }
        slots[slot] = header + 1;
    }

    private void ensureEntry()
    {
        if (count == nameOffsets.length)
        {
            final int capacity = count << 1;
            nameOffsets = Arrays.copyOf(nameOffsets, capacity);
            nameLengths = Arrays.copyOf(nameLengths, capacity);
            valueOffsets = Arrays.copyOf(valueOffsets, capacity);
            valueLengths = Arrays.copyOf(valueLengths, capacity);
            nonAscii = Arrays.copyOf(nonAscii, capacity);
            values = Arrays.copyOf(values, capacity);
        }
    }

    private void ensureBytes(
        int length)
    {
        if (used + length > bytes.length)
        {
            bytes = Arrays.copyOf(bytes, Math.max(bytes.length << 1, used + length));
            buffer.wrap(bytes);
        }
    }

    private boolean hasHighBit(
        int offset,
        int length)
    {
        boolean high = false;
        for (int index = 0; !high && index < length; index++)
        {
            high = buffer.getByte(offset + index) < 0;
        }
        return high;
    }

    private static int hashOf(
        DirectBufferEx source,
        int length)
    {
        return hashOf(source, 0, length);
    }

    private static int hashOf(
        DirectBufferEx source,
        int offset,
        int length)
    {
        int hash = 0;
        for (int index = 0; index < length; index++)
        {
            hash = 31 * hash + source.getByte(offset + index);
        }
        return hash ^ (hash >>> 16);
    }
}
