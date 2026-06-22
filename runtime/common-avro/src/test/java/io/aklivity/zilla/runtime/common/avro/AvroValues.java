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
import org.agrona.MutableDirectBuffer;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

import io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status;
import io.aklivity.zilla.runtime.common.avro.AvroSink.Delivery;

/**
 * Test-only helpers (allocation is fine in test scope) for driving {@code common-avro} pipelines: a
 * recording {@link AvroSink} that captures the structured event stream (framing skipped) and a transcode
 * helper that parses a datum straight into a generator-backed sink. A no-op {@link AvroController}
 * suffices for hand-fed sinks.
 */
public final class AvroValues
{
    public static final AvroController NO_CONTROL = () ->
    {
    };

    private AvroValues()
    {
    }

    public static List<AvroEvent> parse(
        AvroSchema schema,
        byte[] binary)
    {
        Recorder recorder = new Recorder();
        AvroPipeline pipeline = Avro.stream(Avro.parser(schema)).into(recorder);
        pipeline.reset();
        recorder.status = pipeline.transform(new UnsafeBufferEx(binary), 0, binary.length);
        return recorder.events;
    }

    public static Recorder record(
        AvroSchema schema,
        byte[] binary)
    {
        Recorder recorder = new Recorder();
        AvroPipeline pipeline = Avro.stream(Avro.parser(schema)).into(recorder);
        pipeline.reset();
        recorder.status = pipeline.transform(new UnsafeBufferEx(binary), 0, binary.length);
        return recorder;
    }

    public static byte[] transcode(
        AvroSchema schema,
        byte[] binary,
        Delivery delivery)
    {
        MutableDirectBuffer out = new UnsafeBufferEx(new byte[Math.max(64, binary.length * 4)]);
        AvroGenerator generator = Avro.generator(schema, out, 0);
        AvroPipeline pipeline = Avro.stream(Avro.parser(schema))
            .transform(schema.validator())
            .into(AvroSink.of(generator, delivery));
        pipeline.reset();
        Status status = pipeline.transform(new UnsafeBufferEx(binary), 0, binary.length);
        if (status != Status.COMPLETED)
        {
            throw new AssertionError("parse did not complete: " + status);
        }
        byte[] bytes = new byte[generator.length()];
        out.getBytes(0, bytes);
        return bytes;
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
            this.booleanValue = event == AvroEvent.BOOLEAN && in.getBoolean();
            this.intValue = event == AvroEvent.INT || event == AvroEvent.ENUM || event == AvroEvent.UNION_BRANCH
                ? in.getInt()
                : 0;
            this.longValue = event == AvroEvent.LONG ? in.getLong() : 0L;
            this.floatValue = event == AvroEvent.FLOAT ? in.getFloat() : 0f;
            this.doubleValue = event == AvroEvent.DOUBLE ? in.getDouble() : 0d;
            this.string = event == AvroEvent.ENUM ? in.getString() : null;
            this.bytes = event == AvroEvent.STRING || event == AvroEvent.BYTES ||
                event == AvroEvent.FIXED || event == AvroEvent.MAP_KEY
                ? copy(in)
                : null;
        }

        private static byte[] copy(
            AvroSource in)
        {
            DirectBuffer segment = in.getSegment();
            byte[] dst = new byte[segment.capacity()];
            segment.getBytes(0, dst);
            return dst;
        }
    }

    public static final class Recorder implements AvroSink
    {
        public final List<AvroEvent> events = new ArrayList<>();
        public final List<Entry> entries = new ArrayList<>();
        public Status status;

        @Override
        public Status transform(
            AvroController control,
            AvroSource source,
            AvroEvent event)
        {
            // a value streamed across the window arrives as several chunks; coalesce by recording only the
            // final chunk (deferredBytes() == 0), so a fragmented parse yields the same events as a whole one
            boolean pendingChunk = source.deferredBytes() > 0;
            if (event != AvroEvent.START_MESSAGE && event != AvroEvent.END_MESSAGE && !event.segmented() && !pendingChunk)
            {
                events.add(event);
                entries.add(new Entry(event, source));
            }
            return event == AvroEvent.END_MESSAGE ? Status.COMPLETED : Status.ADVANCED;
        }

        @Override
        public void reset()
        {
            events.clear();
            entries.clear();
            status = null;
        }
    }
}
