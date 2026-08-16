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
package io.aklivity.zilla.runtime.engine.model;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class ModelEnvelopeTest
{
    private static final ModelController NO_CONTROL = new ModelController()
    {
        @Override
        public long authorization()
        {
            return 0L;
        }

        @Override
        public void reject(
            String diagnostic)
        {
        }
    };

    @Test
    public void shouldReadEmptyAndDiscardWritesWhenNone()
    {
        ModelEnvelope envelope = ModelEnvelope.NONE;

        envelope.set("trace", buffer("one"));

        assertEquals(0, envelope.count("trace"));
        assertNull(envelope.get("trace", 0));
    }

    @Test
    public void shouldCountZeroForAbsentName()
    {
        Metadata envelope = new Metadata();
        envelope.set("trace", buffer("one"));

        assertEquals(0, envelope.count("absent"));
        assertNull(envelope.get("absent", 0));
        assertNull(envelope.get("trace", 1));
    }

    @Test
    public void shouldAccumulateRepeatedValuesUnderOneName()
    {
        Metadata envelope = new Metadata();

        envelope.set("trace", buffer("one"));
        envelope.set("trace", buffer("two"));

        assertEquals(2, envelope.count("trace"));
        assertEquals("one", text(envelope.get("trace", 0)));
        assertEquals("two", text(envelope.get("trace", 1)));
    }

    @Test
    public void shouldCopyValueBytesOnSet()
    {
        Metadata envelope = new Metadata();
        byte[] bytes = "one".getBytes(UTF_8);

        envelope.set("trace", new UnsafeBufferEx(bytes));
        bytes[0] = 'X';

        assertEquals("one", text(envelope.get("trace", 0)));
    }

    @Test
    public void shouldReadAndWriteNamedMetadataFromTransform()
    {
        Metadata envelope = new Metadata();
        envelope.set("trace", buffer("one"));
        envelope.set("trace", buffer("two"));

        Carrying transform = new Carrying(envelope, "trace");

        transform.transform(NO_CONTROL, new Field("$.id", "x"), ModelEvent.FIELD, new Recorder());
        transform.flush(NO_CONTROL, new Field(null, ""), new Recorder());

        assertEquals(List.of("one", "two"), transform.read);
        assertEquals(3, envelope.count("trace"));
        assertEquals("$.id=x", text(envelope.get("trace", 2)));
    }

    @Test
    public void shouldLeaveEnvelopeUntouchedWhenTransformIgnoresIt()
    {
        Metadata envelope = new Metadata();
        Recorder recorder = new Recorder();

        ModelTransform.NONE.transform(NO_CONTROL, new Field("$.id", "x"), ModelEvent.FIELD, recorder);
        ModelTransform.NONE.flush(NO_CONTROL, new Field(null, ""), recorder);

        assertEquals(0, envelope.reads);
        assertEquals(0, envelope.writes);
        assertEquals(0, envelope.count("trace"));
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
    private static final class Metadata implements ModelEnvelope
    {
        private final Map<String, List<DirectBufferEx>> values = new LinkedHashMap<>();

        private int reads;
        private int writes;

        @Override
        public int count(
            String name)
        {
            reads++;
            List<DirectBufferEx> named = values.get(name);
            return named != null ? named.size() : 0;
        }

        @Override
        public DirectBufferEx get(
            String name,
            int index)
        {
            reads++;
            List<DirectBufferEx> named = values.get(name);
            return named != null && index < named.size() ? named.get(index) : null;
        }

        @Override
        public void set(
            String name,
            DirectBufferEx value)
        {
            writes++;
            byte[] copy = new byte[value.capacity()];
            value.getBytes(0, copy);
            values.computeIfAbsent(name, n -> new ArrayList<>()).add(new UnsafeBufferEx(copy));
        }
    }

    // a stage supplied with the same envelope the pipeline was supplied with: it reads every value the
    // envelope carries under one name as fields arrive, and writes what it saw back under the same name
    // when the value completes
    private static final class Carrying implements ModelTransform
    {
        private final ModelEnvelope envelope;
        private final String name;
        private final List<String> read = new ArrayList<>();

        private String field;

        private Carrying(
            ModelEnvelope envelope,
            String name)
        {
            this.envelope = envelope;
            this.name = name;
        }

        @Override
        public ModelStatus transform(
            ModelController control,
            ModelSource source,
            ModelEvent event,
            ModelSink sink)
        {
            if (event == ModelEvent.FIELD)
            {
                int count = envelope.count(name);
                for (int index = 0; index < count; index++)
                {
                    read.add(text(envelope.get(name, index)));
                }
                field = source.getPath() + "=" + text(source.getValue());
            }
            return sink.transform(control, source, event);
        }

        @Override
        public ModelStatus flush(
            ModelController control,
            ModelSource source,
            ModelSink sink)
        {
            if (field != null)
            {
                envelope.set(name, buffer(field));
            }
            return sink.flush(control, source);
        }
    }

    private static final class Field implements ModelSource
    {
        private final String path;
        private final DirectBufferEx value;

        private Field(
            String path,
            String value)
        {
            this.path = path;
            this.value = buffer(value);
        }

        @Override
        public String getPath()
        {
            return path;
        }

        @Override
        public DirectBufferEx getValue()
        {
            return value;
        }
    }

    private static final class Recorder implements ModelSink
    {
        @Override
        public ModelStatus transform(
            ModelController control,
            ModelSource source,
            ModelEvent event)
        {
            return ModelStatus.OK;
        }

        @Override
        public ModelStatus flush(
            ModelController control,
            ModelSource source)
        {
            return ModelStatus.OK;
        }

        @Override
        public boolean identity()
        {
            return true;
        }
    }
}
