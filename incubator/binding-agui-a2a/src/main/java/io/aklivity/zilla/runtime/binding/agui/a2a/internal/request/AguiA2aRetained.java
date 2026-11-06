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
package io.aklivity.zilla.runtime.binding.agui.a2a.internal.request;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;

public final class AguiA2aRetained
{
    public enum Kind
    {
        MESSAGE,
        STATE,
        CONTEXT,
        FORWARDED_PROPS,
        RESUME,
        FORWARDED_VALUE
    }

    public enum Append
    {
        OK,
        FULL,
        UNAVAILABLE
    }

    private static final Kind[] KINDS = Kind.values();
    private static final int ENTRY_SIZE = 3 * Integer.BYTES;

    private final AguiA2aRetention retention;
    private final long streamId;

    private int dataLimit;
    private int count;

    public AguiA2aRetained(
        AguiA2aRetention retention,
        long streamId)
    {
        this.retention = retention;
        this.streamId = streamId;
    }

    public int count()
    {
        return count;
    }

    public Kind kind(
        int index)
    {
        return KINDS[retention.buffer().getInt(slotOf(index))];
    }

    public int offset(
        int index)
    {
        return retention.buffer().getInt(slotOf(index) + Integer.BYTES);
    }

    public int length(
        int index)
    {
        return retention.buffer().getInt(slotOf(index) + 2 * Integer.BYTES);
    }

    public int indexOf(
        Kind kind)
    {
        int index = 0;
        while (index < count && kind(index) != kind)
        {
            index++;
        }

        return index < count ? index : -1;
    }

    public int indexOf(
        Kind kind,
        int fromIndex)
    {
        int index = fromIndex;
        while (index < count && kind(index) != kind)
        {
            index++;
        }

        return index < count ? index : -1;
    }

    public int lastIndexOf(
        Kind kind)
    {
        int index = count - 1;
        while (index >= 0 && kind(index) != kind)
        {
            index--;
        }

        return index;
    }

    public DirectBufferEx buffer()
    {
        return retention.buffer();
    }

    public void release()
    {
        retention.release();
        dataLimit = 0;
        count = 0;
    }

    int mark()
    {
        return dataLimit;
    }

    Append append(
        DirectBufferEx source,
        int offset,
        int length)
    {
        Append result = Append.OK;

        if (!retention.acquired() && !retention.acquire(streamId))
        {
            result = Append.UNAVAILABLE;
        }
        else if (dataLimit + length > retention.capacity() - (count + 1) * ENTRY_SIZE)
        {
            result = Append.FULL;
        }
        else
        {
            retention.buffer().putBytes(dataLimit, source, offset, length);
            dataLimit += length;
        }

        return result;
    }

    void entry(
        Kind kind,
        int mark)
    {
        entry(kind, mark, dataLimit);
    }

    void entryMembers(
        Kind kind,
        int mark)
    {
        final MutableDirectBufferEx buffer = retention.buffer();
        final int offset = trimStart(buffer, mark, dataLimit);
        final int limit = trimEnd(buffer, offset, dataLimit);

        entry(kind, offset + 1, limit - 1);
    }

    private void entry(
        Kind kind,
        int mark,
        int limit)
    {
        final MutableDirectBufferEx buffer = retention.buffer();
        final int offset = trimStart(buffer, mark, limit);
        final int length = trimEnd(buffer, offset, limit) - offset;

        final int entry = slotOf(count);
        buffer.putInt(entry, kind.ordinal());
        buffer.putInt(entry + Integer.BYTES, offset);
        buffer.putInt(entry + 2 * Integer.BYTES, length);
        count++;
    }

    void truncate(
        int mark,
        int entries)
    {
        dataLimit = mark;
        count = entries;
    }

    private int slotOf(
        int index)
    {
        return retention.capacity() - (index + 1) * ENTRY_SIZE;
    }

    private static int trimStart(
        MutableDirectBufferEx buffer,
        int start,
        int end)
    {
        int index = start;
        while (index < end && (isWhitespace(buffer.getByte(index)) || buffer.getByte(index) == ':'))
        {
            index++;
        }

        return index;
    }

    private static int trimEnd(
        MutableDirectBufferEx buffer,
        int start,
        int end)
    {
        int index = end;
        while (index > start && isWhitespace(buffer.getByte(index - 1)))
        {
            index--;
        }

        return index;
    }

    private static boolean isWhitespace(
        byte octet)
    {
        return octet == ' ' || octet == '\n' || octet == '\r' || octet == '\t';
    }
}
