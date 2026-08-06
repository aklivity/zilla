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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class ModelTransformTest
{
    private static final ModelController NO_CONTROL = diagnostic -> {};

    @Test
    public void shouldForwardEveryFieldWhenNone()
    {
        Recorder recorder = new Recorder();
        Field field = new Field("$.id", "one");

        FieldStatus status = ModelTransform.NONE.transform(NO_CONTROL, field, FieldEvent.FIELD, recorder);

        assertEquals(FieldStatus.ADVANCED, status);
        assertTrue(ModelTransform.NONE.identity());
        assertEquals(List.of("$.id=one"), recorder.events);
    }

    @Test
    public void shouldComposeNothingWhenEmpty()
    {
        assertSame(ModelTransform.NONE, CompositeModelTransform.of(null));
        assertSame(ModelTransform.NONE, CompositeModelTransform.of(List.of()));
    }

    @Test
    public void shouldComposeSingleWhenOne()
    {
        ModelTransform only = new Replacing("$.id", "first");

        assertSame(only, CompositeModelTransform.of(List.of(only)));
    }

    @Test
    public void shouldFeedEachStageTheAnswerOfThePrevious()
    {
        Recorder recorder = new Recorder();
        ModelTransform composed = CompositeModelTransform.of(List.of(
            new Replacing("$.id", "first"),
            new Appending("!")));

        FieldStatus status = composed.transform(NO_CONTROL, new Field("$.id", "one"), FieldEvent.FIELD, recorder);

        assertEquals(FieldStatus.ADVANCED, status);
        assertEquals(List.of("$.id=first!"), recorder.events);
    }

    @Test
    public void shouldDeclineThroughEveryStage()
    {
        Recorder recorder = new Recorder();
        ModelTransform composed = CompositeModelTransform.of(List.of(
            new Declining("$.id"),
            new Appending("!")));

        composed.transform(NO_CONTROL, new Field("$.id", "one"), FieldEvent.FIELD, recorder);

        assertEquals(List.of("DECLINED $.id="), recorder.events);
    }

    @Test
    public void shouldComposeIdentityOnlyWhenEveryStageIsIdentity()
    {
        assertTrue(CompositeModelTransform.of(List.of(ModelTransform.NONE, ModelTransform.NONE)).identity());
        assertFalse(CompositeModelTransform.of(List.of(ModelTransform.NONE, new Appending("!"))).identity());
    }

    @Test
    public void shouldResetEveryStage()
    {
        Appending first = new Appending("!");
        Appending second = new Appending("?");
        ModelTransform composed = CompositeModelTransform.of(List.of(first, second));

        composed.transform(NO_CONTROL, new Field("$.id", "one"), FieldEvent.FIELD, new Recorder());
        composed.reset();

        assertEquals(1, first.resets);
        assertEquals(1, second.resets);
    }

    @Test
    public void shouldFlushThroughEveryStage()
    {
        Recorder recorder = new Recorder();
        ModelTransform composed = CompositeModelTransform.of(List.of(new Appending("!"), new Appending("?")));

        assertEquals(FieldStatus.ADVANCED, composed.flush(NO_CONTROL, new Field(null, ""), recorder));
        assertEquals(1, recorder.flushes);
    }

    @Test
    public void shouldBridgeCompletedFieldsToTransform()
    {
        Recording transform = new Recording();
        ModelFieldBridge bridge = new ModelFieldBridge(transform);
        DirectBufferEx value = new UnsafeBufferEx("one".getBytes(UTF_8));

        bridge.start();
        bridge.field("$.id", value, 0, value.capacity());
        bridge.end();

        assertEquals(List.of("START_VALUE", "$.id=one", "END_VALUE"), transform.events);
        assertEquals(1, transform.flushes);
    }

    private static String text(
        ModelSource source)
    {
        DirectBufferEx value = source.getValue();
        return value.getStringWithoutLengthUtf8(0, value.capacity());
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
            this.value = new UnsafeBufferEx(value.getBytes(UTF_8));
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
        private final List<String> events = new ArrayList<>();

        private int flushes;

        @Override
        public FieldStatus transform(
            ModelController control,
            ModelSource source,
            FieldEvent event)
        {
            String prefix = event == FieldEvent.DECLINED ? "DECLINED " : "";
            events.add(prefix + source.getPath() + "=" + text(source));
            return FieldStatus.ADVANCED;
        }

        @Override
        public FieldStatus flush(
            ModelController control,
            ModelSource source)
        {
            flushes++;
            return FieldStatus.ADVANCED;
        }

        @Override
        public boolean identity()
        {
            return true;
        }
    }

    // replaces the value of one path with a fixed substitute
    private static final class Replacing implements ModelTransform
    {
        private final String path;
        private final Field substitute;

        private Replacing(
            String path,
            String value)
        {
            this.path = path;
            this.substitute = new Field(path, value);
        }

        @Override
        public FieldStatus transform(
            ModelController control,
            ModelSource source,
            FieldEvent event,
            ModelSink sink)
        {
            return event == FieldEvent.FIELD && path.equals(source.getPath())
                ? sink.transform(control, substitute, FieldEvent.REPLACED)
                : sink.transform(control, source, event);
        }
    }

    // appends a suffix to whatever value reaches it, so a chained stage proves it saw the previous answer
    private static final class Appending implements ModelTransform
    {
        private final String suffix;

        private int resets;

        private Appending(
            String suffix)
        {
            this.suffix = suffix;
        }

        @Override
        public FieldStatus transform(
            ModelController control,
            ModelSource source,
            FieldEvent event,
            ModelSink sink)
        {
            return event == FieldEvent.FIELD || event == FieldEvent.REPLACED
                ? sink.transform(control, new Field(source.getPath(), text(source) + suffix), FieldEvent.REPLACED)
                : sink.transform(control, source, event);
        }

        @Override
        public void reset()
        {
            resets++;
        }
    }

    private static final class Declining implements ModelTransform
    {
        private final String path;

        private Declining(
            String path)
        {
            this.path = path;
        }

        @Override
        public FieldStatus transform(
            ModelController control,
            ModelSource source,
            FieldEvent event,
            ModelSink sink)
        {
            return event == FieldEvent.FIELD && path.equals(source.getPath())
                ? sink.transform(control, new Field(path, ""), FieldEvent.DECLINED)
                : sink.transform(control, source, event);
        }
    }

    private static final class Recording implements ModelTransform
    {
        private final List<String> events = new ArrayList<>();

        private int flushes;

        @Override
        public FieldStatus transform(
            ModelController control,
            ModelSource source,
            FieldEvent event,
            ModelSink sink)
        {
            events.add(event == FieldEvent.FIELD ? source.getPath() + "=" + text(source) : event.name());
            return sink.transform(control, source, event);
        }

        @Override
        public FieldStatus flush(
            ModelController control,
            ModelSource source,
            ModelSink sink)
        {
            flushes++;
            return sink.flush(control, source);
        }

        @Override
        public boolean identity()
        {
            return true;
        }
    }
}
