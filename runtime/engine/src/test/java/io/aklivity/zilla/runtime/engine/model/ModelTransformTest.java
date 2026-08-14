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
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class ModelTransformTest
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
    public void shouldForwardEveryFieldWhenNone()
    {
        Recorder recorder = new Recorder();
        Field field = new Field("$.id", "one");

        ModelStatus status = ModelTransform.NONE.transform(NO_CONTROL, field, ModelEvent.FIELD, recorder);

        assertEquals(ModelStatus.OK, status);
        assertTrue(ModelTransform.NONE.identity());
        assertEquals(List.of("$.id=one"), recorder.events);
    }

    @Test
    public void shouldFeedEachStageTheAnswerOfThePreviousWhenChainedThreeDeep()
    {
        Recorder recorder = new Recorder();
        ModelTransform composed = new Replacing("$.id", "first")
            .andThen(new Appending("!"))
            .andThen(new Appending("?"));

        composed.transform(NO_CONTROL, new Field("$.id", "one"), ModelEvent.FIELD, recorder);

        assertEquals(List.of("$.id=first!?"), recorder.events);
    }

    @Test
    public void shouldComposeAnyNumberOfStagesByReducing()
    {
        Recorder recorder = new Recorder();
        ModelTransform composed = Stream.of(new Appending("a"), new Appending("b"), new Appending("c"))
            .map(ModelTransform.class::cast)
            .reduce(ModelTransform::andThen)
            .orElse(ModelTransform.NONE);

        composed.transform(NO_CONTROL, new Field("$.id", "x"), ModelEvent.FIELD, recorder);

        assertEquals(List.of("$.id=xabc"), recorder.events);
    }

    @Test
    public void shouldFeedEachStageTheAnswerOfThePrevious()
    {
        Recorder recorder = new Recorder();
        ModelTransform composed = new Replacing("$.id", "first").andThen(new Appending("!"));

        ModelStatus status = composed.transform(NO_CONTROL, new Field("$.id", "one"), ModelEvent.FIELD, recorder);

        assertEquals(ModelStatus.OK, status);
        assertEquals(List.of("$.id=first!"), recorder.events);
    }

    @Test
    public void shouldDeclineThroughEveryStage()
    {
        Recorder recorder = new Recorder();
        ModelTransform composed = new Declining("$.id").andThen(new Appending("!"));

        composed.transform(NO_CONTROL, new Field("$.id", "one"), ModelEvent.FIELD, recorder);

        assertEquals(List.of("DECLINED $.id="), recorder.events);
    }

    @Test
    public void shouldComposeIdentityOnlyWhenEveryStageIsIdentity()
    {
        assertTrue(new Observing().andThen(new Observing()).identity());
        assertFalse(new Observing().andThen(new Appending("!")).identity());
        assertFalse(new Appending("!").andThen(new Observing()).identity());
    }

    @Test
    public void shouldYieldTheOtherStageWhenComposingWithNone()
    {
        ModelTransform only = new Appending("!");

        assertSame(only, ModelTransform.NONE.andThen(only));
        assertSame(only, only.andThen(ModelTransform.NONE));
        assertSame(ModelTransform.NONE, ModelTransform.NONE.andThen(ModelTransform.NONE));
    }

    @Test
    public void shouldComposeAnIdentityStageThatIsNotNone()
    {
        // an identity stage still runs — an observing stage forwards every field while reacting to the
        // ones it cares about — so it composes like any other stage
        Observing observing = new Observing();
        Recorder recorder = new Recorder();
        ModelTransform composed = observing.andThen(new Appending("!"));

        assertNotSame(observing, composed);

        composed.transform(NO_CONTROL, new Field("$.id", "one"), ModelEvent.FIELD, recorder);

        assertEquals(1, observing.seen);
        assertEquals(List.of("$.id=one!"), recorder.events);
    }

    @Test
    public void shouldResetEveryStage()
    {
        Appending first = new Appending("!");
        Appending second = new Appending("?");
        ModelTransform composed = first.andThen(second);

        composed.transform(NO_CONTROL, new Field("$.id", "one"), ModelEvent.FIELD, new Recorder());
        composed.reset();

        assertEquals(1, first.resets);
        assertEquals(1, second.resets);
    }

    @Test
    public void shouldFlushThroughEveryStage()
    {
        Recorder recorder = new Recorder();
        ModelTransform composed = new Appending("!").andThen(new Appending("?"));

        assertEquals(ModelStatus.OK, composed.flush(NO_CONTROL, new Field(null, ""), recorder));
        assertEquals(1, recorder.flushes);
    }

    @Test
    public void shouldBridgeCompletedFieldsToTransform()
    {
        Recording transform = new Recording();
        ModelFieldBridge bridge = new ModelFieldBridge(transform);
        DirectBufferEx value = new UnsafeBufferEx("one".getBytes(UTF_8));

        bridge.start(0L);
        bridge.field("$.id", value, 0, value.capacity());
        bridge.end();

        assertEquals(List.of("START_VALUE", "$.id=one", "END_VALUE"), transform.events);
        assertEquals(1, transform.flushes);
    }

    @Test
    public void shouldExposeAuthorizationToBridgedTransform()
    {
        Recording transform = new Recording();
        ModelFieldBridge bridge = new ModelFieldBridge(transform);
        DirectBufferEx value = new UnsafeBufferEx("one".getBytes(UTF_8));

        bridge.start(0x0102L);
        bridge.field("$.id", value, 0, value.capacity());
        bridge.end();

        assertEquals(List.of(0x0102L, 0x0102L, 0x0102L), transform.authorizations);
    }

    @Test
    public void shouldObserveChangedAuthorizationOnNextValue()
    {
        Recording transform = new Recording();
        ModelFieldBridge bridge = new ModelFieldBridge(transform);
        DirectBufferEx value = new UnsafeBufferEx("one".getBytes(UTF_8));

        bridge.start(0x0102L);
        bridge.field("$.id", value, 0, value.capacity());
        bridge.end();

        bridge.start(0x0304L);
        bridge.field("$.id", value, 0, value.capacity());
        bridge.end();

        assertEquals(List.of(0x0102L, 0x0102L, 0x0102L, 0x0304L, 0x0304L, 0x0304L), transform.authorizations);
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
        public ModelStatus transform(
            ModelController control,
            ModelSource source,
            ModelEvent event)
        {
            String prefix = event == ModelEvent.DECLINED ? "DECLINED " : "";
            events.add(prefix + source.getPath() + "=" + text(source));
            return ModelStatus.OK;
        }

        @Override
        public ModelStatus flush(
            ModelController control,
            ModelSource source)
        {
            flushes++;
            return ModelStatus.OK;
        }

        @Override
        public boolean identity()
        {
            return true;
        }
    }

    // forwards every field unchanged while counting what it saw, as an accumulating observer does
    private static final class Observing implements ModelTransform
    {
        private int seen;

        @Override
        public ModelStatus transform(
            ModelController control,
            ModelSource source,
            ModelEvent event,
            ModelSink sink)
        {
            if (event == ModelEvent.FIELD)
            {
                seen++;
            }
            return sink.transform(control, source, event);
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
        public ModelStatus transform(
            ModelController control,
            ModelSource source,
            ModelEvent event,
            ModelSink sink)
        {
            return event == ModelEvent.FIELD && path.equals(source.getPath())
                ? sink.transform(control, substitute, ModelEvent.REPLACED)
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
        public ModelStatus transform(
            ModelController control,
            ModelSource source,
            ModelEvent event,
            ModelSink sink)
        {
            return event == ModelEvent.FIELD || event == ModelEvent.REPLACED
                ? sink.transform(control, new Field(source.getPath(), text(source) + suffix), ModelEvent.REPLACED)
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
        public ModelStatus transform(
            ModelController control,
            ModelSource source,
            ModelEvent event,
            ModelSink sink)
        {
            return event == ModelEvent.FIELD && path.equals(source.getPath())
                ? sink.transform(control, new Field(path, ""), ModelEvent.DECLINED)
                : sink.transform(control, source, event);
        }
    }

    private static final class Recording implements ModelTransform
    {
        private final List<String> events = new ArrayList<>();
        private final List<Long> authorizations = new ArrayList<>();

        private int flushes;

        @Override
        public ModelStatus transform(
            ModelController control,
            ModelSource source,
            ModelEvent event,
            ModelSink sink)
        {
            events.add(event == ModelEvent.FIELD ? source.getPath() + "=" + text(source) : event.name());
            authorizations.add(control.authorization());
            return sink.transform(control, source, event);
        }

        @Override
        public ModelStatus flush(
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
