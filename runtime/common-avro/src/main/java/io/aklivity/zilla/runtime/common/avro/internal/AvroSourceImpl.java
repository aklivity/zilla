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
package io.aklivity.zilla.runtime.common.avro.internal;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.common.avro.AvroLocation;
import io.aklivity.zilla.runtime.common.avro.AvroSource;

/**
 * Mutable {@link AvroSource} reused across events on a single thread. The decoder positions it before
 * each {@code feed} to its sink; the {@link #getSegment()} view for {@code BYTES}/{@code FIXED} and
 * segment events borrows the decoder work buffer and is valid only for the duration of that call.
 * Schema-supplied strings (enum symbols, field names) are returned without allocation;
 * {@link #getString()} / {@link #getKey()} decode data-driven UTF-8 on demand.
 */
final class AvroSourceImpl implements AvroSource
{
    private final UnsafeBuffer segmentView = new UnsafeBuffer(0, 0);
    private final AvroLocationImpl location = new AvroLocationImpl();

    private DirectBuffer buffer;
    private int offset;
    private int length;
    private boolean booleanValue;
    private int intValue;
    private long longValue;
    private float floatValue;
    private double doubleValue;
    private String string;
    private String field;

    void setBoolean(
        boolean value)
    {
        this.booleanValue = value;
        this.string = null;
        this.length = 0;
    }

    void setInt(
        int value)
    {
        this.intValue = value;
        this.string = null;
        this.length = 0;
    }

    void setLong(
        long value)
    {
        this.longValue = value;
        this.string = null;
        this.length = 0;
    }

    void setFloat(
        float value)
    {
        this.floatValue = value;
        this.string = null;
        this.length = 0;
    }

    void setDouble(
        double value)
    {
        this.doubleValue = value;
        this.string = null;
        this.length = 0;
    }

    void setBytes(
        DirectBuffer buffer,
        int offset,
        int length)
    {
        this.buffer = buffer;
        this.offset = offset;
        this.length = length;
        this.string = null;
    }

    void setEnum(
        int index,
        String symbol)
    {
        this.intValue = index;
        this.string = symbol;
        this.length = 0;
    }

    void setName(
        String name)
    {
        this.field = name;
        this.string = null;
        this.length = 0;
    }

    void setSegment(
        DirectBuffer buffer,
        int offset,
        int length)
    {
        this.buffer = buffer;
        this.offset = offset;
        this.length = length;
    }

    void clear()
    {
        this.string = null;
        this.length = 0;
    }

    void locate(
        int depth,
        long position)
    {
        location.locate(depth, position);
    }

    @Override
    public boolean getBoolean()
    {
        return booleanValue;
    }

    @Override
    public int getInt()
    {
        return intValue;
    }

    @Override
    public long getLong()
    {
        return longValue;
    }

    @Override
    public float getFloat()
    {
        return floatValue;
    }

    @Override
    public double getDouble()
    {
        return doubleValue;
    }

    @Override
    public String getString()
    {
        String value = string;
        if (value == null && length > 0)
        {
            value = decode();
        }
        return value;
    }

    @Override
    public String getField()
    {
        return field;
    }

    @Override
    public String getKey()
    {
        return length > 0 ? decode() : null;
    }

    @Override
    public DirectBuffer getSegment()
    {
        segmentView.wrap(buffer, offset, length);
        return segmentView;
    }

    @Override
    public AvroLocation getLocation()
    {
        return location;
    }

    private String decode()
    {
        byte[] dst = new byte[length];
        buffer.getBytes(offset, dst);
        return new String(dst, UTF_8);
    }
}
