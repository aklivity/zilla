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
package io.aklivity.zilla.runtime.common.avro;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

class AvroEnvelopeTest
{
    private static final int SRC_OFFSET = 5;
    private static final int DST_OFFSET = 7;

    // "foo": length 3 (zigzag varint 0x06) followed by the three bytes
    private static final byte[] FOO = { 0x06, 0x66, 0x6f, 0x6f };

    @Test
    void shouldReadEmptyAndDiscardWritesWhenNone()
    {
        AvroEnvelope envelope = AvroEnvelope.NONE;

        envelope.set("trace", buffer("one"));

        assertEquals(0, envelope.count("trace"));
        assertNull(envelope.get("trace", 0));
    }

    @Test
    void shouldSupplyNoneToStageWhenPipelineHasNoEnvelope()
    {
        AvroSchema schema = Avro.schema("\"string\"");
        Observing observing = new Observing();
        AvroPipeline pipeline = Avro.stream(Avro.parser(schema))
            .transform(observing)
            .into(generatorFor(schema));
        pipeline.reset();

        transform(pipeline);

        assertSame(AvroEnvelope.NONE, observing.envelope);
    }

    @Test
    void shouldReadEnvelopeSuppliedToPipelineFromStage()
    {
        AvroSchema schema = Avro.schema("\"string\"");
        Metadata envelope = new Metadata();
        envelope.set("trace", buffer("one"));
        envelope.set("trace", buffer("two"));

        Reading reading = new Reading("trace");
        AvroPipeline pipeline = Avro.stream(Avro.parser(schema))
            .transform(reading)
            .envelope(envelope)
            .into(generatorFor(schema));
        pipeline.reset();

        transform(pipeline);

        assertEquals(List.of("one", "two"), reading.read);
    }

    @Test
    void shouldWriteEnvelopeSuppliedToPipelineFromStage()
    {
        AvroSchema schema = Avro.schema("\"string\"");
        Metadata envelope = new Metadata();

        AvroPipeline pipeline = Avro.stream(Avro.parser(schema))
            .transform(new Writing("seen"))
            .envelope(envelope)
            .into(generatorFor(schema));
        pipeline.reset();

        transform(pipeline);

        assertEquals(1, envelope.count("seen"));
        assertEquals("foo", text(envelope.get("seen", 0)));
    }

    @Test
    void shouldAccumulateRepeatedValuesUnderOneName()
    {
        Metadata envelope = new Metadata();

        envelope.set("trace", buffer("one"));
        envelope.set("trace", buffer("two"));

        assertEquals(2, envelope.count("trace"));
        assertEquals("one", text(envelope.get("trace", 0)));
        assertEquals("two", text(envelope.get("trace", 1)));
        assertEquals(0, envelope.count("absent"));
        assertNull(envelope.get("trace", 2));
    }

    private static void transform(
        AvroPipeline pipeline)
    {
        MutableDirectBufferEx src = new UnsafeBufferEx(new byte[SRC_OFFSET + FOO.length]);
        src.putBytes(SRC_OFFSET, FOO);
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[DST_OFFSET + 64]);

        pipeline.transform(src, SRC_OFFSET, SRC_OFFSET + FOO.length, true, dst, DST_OFFSET, DST_OFFSET + 64);
    }

    private static AvroGenerator generatorFor(
        AvroSchema schema)
    {
        return Avro.generator(schema, new UnsafeBufferEx(new byte[1]), 0);
    }

    private static DirectBufferEx buffer(
        String value)
    {
        return new UnsafeBufferEx(value.getBytes(UTF_8));
    }

    private static String text(
        DirectBufferEx value)
    {
        return value.getStringWithoutLengthUtf8(0, value.capacity());
    }

    // the envelope a caller supplies to the pipeline, backed by a copy of each value it is given
    private static final class Metadata implements AvroEnvelope
    {
        private final Map<String, List<DirectBufferEx>> values = new LinkedHashMap<>();

        @Override
        public int count(
            String name)
        {
            List<DirectBufferEx> named = values.get(name);
            return named != null ? named.size() : 0;
        }

        @Override
        public DirectBufferEx get(
            String name,
            int index)
        {
            List<DirectBufferEx> named = values.get(name);
            return named != null && index < named.size() ? named.get(index) : null;
        }

        @Override
        public void set(
            String name,
            DirectBufferEx value)
        {
            byte[] copy = new byte[value.capacity()];
            value.getBytes(0, copy);
            values.computeIfAbsent(name, n -> new ArrayList<>()).add(new UnsafeBufferEx(copy));
        }
    }

    // captures the envelope the pipeline supplies, without touching its contents
    private static final class Observing implements AvroTransform
    {
        private AvroEnvelope envelope;

        @Override
        public AvroPipeline.Status transform(
            AvroController control,
            AvroSource source,
            AvroEvent event,
            AvroSink sink)
        {
            envelope = control.envelope();
            return sink.transform(control, source, event);
        }

        @Override
        public boolean identity()
        {
            return true;
        }
    }

    // reads every value the envelope carries under one name, as the value opens
    private static final class Reading implements AvroTransform
    {
        private final String name;
        private final List<String> read = new ArrayList<>();

        private Reading(
            String name)
        {
            this.name = name;
        }

        @Override
        public AvroPipeline.Status transform(
            AvroController control,
            AvroSource source,
            AvroEvent event,
            AvroSink sink)
        {
            if (read.isEmpty())
            {
                AvroEnvelope envelope = control.envelope();
                int count = envelope.count(name);
                for (int index = 0; index < count; index++)
                {
                    read.add(text(envelope.get(name, index)));
                }
            }
            return sink.transform(control, source, event);
        }

        @Override
        public boolean identity()
        {
            return true;
        }
    }

    // writes each string value it observes into the envelope under one name
    private static final class Writing implements AvroTransform
    {
        private final String name;

        private Writing(
            String name)
        {
            this.name = name;
        }

        @Override
        public AvroPipeline.Status transform(
            AvroController control,
            AvroSource source,
            AvroEvent event,
            AvroSink sink)
        {
            if (event == AvroEvent.STRING)
            {
                control.envelope().set(name, buffer(source.getString()));
            }
            return sink.transform(control, source, event);
        }

        @Override
        public boolean identity()
        {
            return true;
        }
    }
}
