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

import io.aklivity.zilla.runtime.common.avro.AvroSource;

/**
 * Mutable {@link AvroSource} reused across events on a worker thread. The decoder positions it
 * before each {@code feed} to its sink; the bytes view for {@code STRING}/{@code BYTES}/{@code FIXED}
 * borrows the decoder work buffer and is valid only for the duration of that call. Schema-supplied
 * strings (enum symbols, field names) are returned without allocation; {@link #getString()} for a
 * data-driven {@code STRING} decodes UTF-8 on demand.
 */
final class AvroCursor implements AvroSource
{
    private DirectBuffer buffer;
    private int offset;
    private int length;
    private boolean booleanValue;
    private int intValue;
    private long longValue;
    private float floatValue;
    private double doubleValue;
    private String string;
    private long position;

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
        this.string = name;
        this.length = 0;
    }

    void clear()
    {
        this.string = null;
        this.length = 0;
    }

    void position(
        long position)
    {
        this.position = position;
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
            byte[] dst = new byte[length];
            buffer.getBytes(offset, dst);
            value = new String(dst, UTF_8);
        }
        return value;
    }

    @Override
    public DirectBuffer buffer()
    {
        return buffer;
    }

    @Override
    public int offset()
    {
        return offset;
    }

    @Override
    public int length()
    {
        return length;
    }

    @Override
    public long position()
    {
        return position;
    }
}
