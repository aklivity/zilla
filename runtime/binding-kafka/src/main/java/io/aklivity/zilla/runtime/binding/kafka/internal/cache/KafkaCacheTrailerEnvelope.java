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
package io.aklivity.zilla.runtime.binding.kafka.internal.cache;

import static org.agrona.BitUtil.SIZE_OF_INT;

import io.aklivity.zilla.runtime.binding.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;

// A write-collecting ModelEnvelope for the producer-encode path: every set() call is appended, in
// call order, directly into a block already claimed for this message in the partition's log file --
// the same claim-per-message approach the log file already uses for a transformed value's own output
// (see KafkaCachePartition's convertedFile handling), rather than staging entries in a heap-resident
// arena first. Repeated names simply add further entries, so writeHeaders() below reproduces them as
// that many repeated Kafka headers, in the same order they were set. claim() is called once per
// message, right after its block is reserved; reset() clears tracking so one instance is reused
// across every message a stream produces. If a message's entries would exceed the claimed block,
// further set() calls are dropped and isOverflowed() reports the condition to the caller, mirroring
// how a transformed value exceeding its own reservation is handled.
public final class KafkaCacheTrailerEnvelope implements ModelEnvelope
{
    private final MutableDirectBufferEx nameBuffer = new UnsafeBufferEx(new byte[256]);
    private final MutableDirectBufferEx queryBuffer = new UnsafeBufferEx(new byte[256]);
    private final DirectBufferEx queryView = new UnsafeBufferEx(new byte[0]);
    private final DirectBufferEx storedNameView = new UnsafeBufferEx(new byte[0]);
    private final DirectBufferEx storedValueView = new UnsafeBufferEx(new byte[0]);

    private KafkaCacheFile logFile;
    private int claimedAt;
    private int claimedMax;
    private int claimedLength;
    private boolean overflowed;

    public void claim(
        KafkaCacheFile logFile,
        int position,
        int maxLength)
    {
        this.logFile = logFile;
        this.claimedAt = position;
        this.claimedMax = maxLength;
        this.claimedLength = 0;
        this.overflowed = false;
    }

    public void reset()
    {
        this.logFile = null;
        this.claimedLength = 0;
        this.overflowed = false;
    }

    public boolean isEmpty()
    {
        return claimedLength == 0;
    }

    public boolean isOverflowed()
    {
        return overflowed;
    }

    public void writeHeaders(
        Array32FW.Builder<KafkaHeaderFW.Builder, KafkaHeaderFW> trailers)
    {
        int position = claimedAt;
        final int limit = claimedAt + claimedLength;
        while (position < limit)
        {
            final int nameLength = logFile.readInt(position);
            final int nameAt = position + SIZE_OF_INT;
            final int valueLength = logFile.readInt(nameAt + nameLength);
            final int valueAt = nameAt + nameLength + SIZE_OF_INT;

            trailers.item(h -> h.nameLen(nameLength).name(logFile.buffer(), nameAt, nameLength)
                .valueLen(valueLength).value(logFile.buffer(), valueAt, valueLength));

            position = valueAt + valueLength;
        }
    }

    @Override
    public int count(
        String name)
    {
        final int queryLength = queryBuffer.putStringWithoutLengthUtf8(0, name);
        queryView.wrap(queryBuffer, 0, queryLength);

        int matches = 0;
        int position = claimedAt;
        final int limit = claimedAt + claimedLength;
        while (position < limit)
        {
            final int nameLength = logFile.readInt(position);
            final int nameAt = position + SIZE_OF_INT;
            final int valueLength = logFile.readInt(nameAt + nameLength);

            if (nameMatches(nameAt, nameLength, queryLength))
            {
                matches++;
            }

            position = nameAt + nameLength + SIZE_OF_INT + valueLength;
        }
        return matches;
    }

    @Override
    public DirectBufferEx get(
        String name,
        int index)
    {
        final int queryLength = queryBuffer.putStringWithoutLengthUtf8(0, name);
        queryView.wrap(queryBuffer, 0, queryLength);

        DirectBufferEx value = null;
        int seen = 0;
        int position = claimedAt;
        final int limit = claimedAt + claimedLength;
        while (position < limit && value == null)
        {
            final int nameLength = logFile.readInt(position);
            final int nameAt = position + SIZE_OF_INT;
            final int valueLength = logFile.readInt(nameAt + nameLength);
            final int valueAt = nameAt + nameLength + SIZE_OF_INT;

            if (nameMatches(nameAt, nameLength, queryLength))
            {
                if (seen == index)
                {
                    storedValueView.wrap(logFile.buffer(), valueAt, valueLength);
                    value = storedValueView;
                }
                seen++;
            }

            position = valueAt + valueLength;
        }
        return value;
    }

    @Override
    public void set(
        String name,
        DirectBufferEx value)
    {
        if (logFile != null && !overflowed)
        {
            final int nameLength = nameBuffer.putStringWithoutLengthUtf8(0, name);
            final int valueLength = value.capacity();
            final int required = SIZE_OF_INT + nameLength + SIZE_OF_INT + valueLength;

            if (claimedLength + required > claimedMax)
            {
                overflowed = true;
            }
            else
            {
                int position = claimedAt + claimedLength;
                logFile.writeInt(position, nameLength);
                position += SIZE_OF_INT;
                logFile.writeBytes(position, nameBuffer, 0, nameLength);
                position += nameLength;
                logFile.writeInt(position, valueLength);
                position += SIZE_OF_INT;
                logFile.writeBytes(position, value, 0, valueLength);

                claimedLength += required;
            }
        }
    }

    private boolean nameMatches(
        int nameAt,
        int nameLength,
        int queryLength)
    {
        boolean matches = false;
        if (nameLength == queryLength)
        {
            storedNameView.wrap(logFile.buffer(), nameAt, nameLength);
            matches = storedNameView.equals(queryView);
        }
        return matches;
    }
}
