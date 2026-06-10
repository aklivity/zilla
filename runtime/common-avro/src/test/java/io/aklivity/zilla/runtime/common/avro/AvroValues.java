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
package io.aklivity.zilla.runtime.common.avro;

import java.util.ArrayList;
import java.util.List;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Test-only helpers (allocation is fine in test scope): a recording {@link AvroSink} that snapshots
 * every decoded event, and a replay {@link AvroSource} that feeds those snapshots back into an
 * {@link AvroEncodePipeline}. Together they let a test assert a decoded event stream and round-trip
 * it back to Avro binary.
 */
public final class AvroValues
{
    private AvroValues()
    {
    }

    public static final class Entry
    {
        public final AvroEvent event;
        public final boolean booleanValue;
        public final int intValue;
        public final long longValue;
        public final float floatValue;
        public final double doubleValue;
        public final byte[] bytes;
        public final String string;

        Entry(
            AvroEvent event,
            AvroSource in)
        {
            this.event = event;
            switch (event)
            {
            case BOOLEAN:
                this.booleanValue = in.getBoolean();
                this.intValue = 0;
                this.longValue = 0L;
                this.floatValue = 0f;
                this.doubleValue = 0d;
                this.bytes = null;
                this.string = null;
                break;
            case INT:
            case ENUM:
            case UNION_BRANCH:
                this.booleanValue = false;
                this.intValue = in.getInt();
                this.longValue = 0L;
                this.floatValue = 0f;
                this.doubleValue = 0d;
                this.bytes = null;
                this.string = event == AvroEvent.ENUM ? in.getString() : null;
                break;
            case LONG:
                this.booleanValue = false;
                this.intValue = 0;
                this.longValue = in.getLong();
                this.floatValue = 0f;
                this.doubleValue = 0d;
                this.bytes = null;
                this.string = null;
                break;
            case FLOAT:
                this.booleanValue = false;
                this.intValue = 0;
                this.longValue = 0L;
                this.floatValue = in.getFloat();
                this.doubleValue = 0d;
                this.bytes = null;
                this.string = null;
                break;
            case DOUBLE:
                this.booleanValue = false;
                this.intValue = 0;
                this.longValue = 0L;
                this.floatValue = 0f;
                this.doubleValue = in.getDouble();
                this.bytes = null;
                this.string = null;
                break;
            case STRING:
            case BYTES:
            case FIXED:
            case MAP_KEY:
                this.booleanValue = false;
                this.intValue = 0;
                this.longValue = 0L;
                this.floatValue = 0f;
                this.doubleValue = 0d;
                this.bytes = copy(in);
                this.string = null;
                break;
            default:
                this.booleanValue = false;
                this.intValue = 0;
                this.longValue = 0L;
                this.floatValue = 0f;
                this.doubleValue = 0d;
                this.bytes = null;
                this.string = null;
                break;
            }
        }

        private static byte[] copy(
            AvroSource in)
        {
            byte[] dst = new byte[in.length()];
            in.buffer().getBytes(in.offset(), dst);
            return dst;
        }
    }

    public static final class Recorder implements AvroSink
    {
        private final List<Entry> entries = new ArrayList<>();

        public List<Entry> entries()
        {
            return entries;
        }

        @Override
        public AvroDecodePipeline.Status feed(
            AvroEvent event,
            AvroSource in)
        {
            entries.add(new Entry(event, in));
            return AvroDecodePipeline.Status.PENDING;
        }

        @Override
        public void reset()
        {
            entries.clear();
        }
    }

    public static final class Replay implements AvroSource
    {
        private final UnsafeBuffer view = new UnsafeBuffer(0, 0);
        private Entry entry;

        public void wrap(
            Entry entry)
        {
            this.entry = entry;
            if (entry.bytes != null)
            {
                view.wrap(entry.bytes);
            }
        }

        @Override
        public boolean getBoolean()
        {
            return entry != null && entry.booleanValue;
        }

        @Override
        public int getInt()
        {
            return entry != null ? entry.intValue : 0;
        }

        @Override
        public long getLong()
        {
            return entry != null ? entry.longValue : 0L;
        }

        @Override
        public float getFloat()
        {
            return entry != null ? entry.floatValue : 0f;
        }

        @Override
        public double getDouble()
        {
            return entry != null ? entry.doubleValue : 0d;
        }

        @Override
        public String getString()
        {
            return entry != null ? entry.string : null;
        }

        @Override
        public DirectBuffer buffer()
        {
            return view;
        }

        @Override
        public int offset()
        {
            return 0;
        }

        @Override
        public int length()
        {
            return entry != null && entry.bytes != null ? entry.bytes.length : 0;
        }

        @Override
        public long position()
        {
            return 0L;
        }
    }
}
