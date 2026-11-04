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
package io.aklivity.zilla.runtime.model.core.internal;

import static io.aklivity.zilla.runtime.engine.util.Flags.COMPLETE;
import static io.aklivity.zilla.runtime.engine.util.Flags.FIN;
import static io.aklivity.zilla.runtime.engine.util.Flags.INIT;
import static io.aklivity.zilla.runtime.engine.util.Flags.NONE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.model.core.ext.BytesController;
import io.aklivity.zilla.runtime.model.core.ext.BytesEvent;
import io.aklivity.zilla.runtime.model.core.ext.BytesSink;
import io.aklivity.zilla.runtime.model.core.ext.BytesSource;
import io.aklivity.zilla.runtime.model.core.ext.BytesTransform;
import io.aklivity.zilla.runtime.model.core.ext.StringController;
import io.aklivity.zilla.runtime.model.core.ext.StringEvent;
import io.aklivity.zilla.runtime.model.core.ext.StringSink;
import io.aklivity.zilla.runtime.model.core.ext.StringSource;
import io.aklivity.zilla.runtime.model.core.ext.StringTransform;

public class CoreExtModelPipelineSuspendTest
{
    private final Queue<Runnable> signals = new ArrayDeque<>();
    private int resumed;

    @Test
    public void shouldSuspendStringAtValueStartAndResumeOnceCompleted()
    {
        StringExtModelPipeline pipeline = string(new AwaitString(StringEvent.START_VALUE, 1));
        UnsafeBufferEx dst = new UnsafeBufferEx(new byte[16]);

        ModelPipelineResult suspended = pipeline.transform(0L, 0L, 0L, COMPLETE,
            buffer("abc"), 0, 3, dst, 0, dst.capacity());

        assertEquals(ModelStatus.SUSPENDED, suspended.status());
        assertEquals(3, suspended.consumed());
        assertEquals(0, suspended.produced());
        assertEquals(0, resumed);

        ModelPipelineResult refused = pipeline.transform(0L, 0L, 0L, NONE,
            buffer("xyz"), 0, 3, dst, 0, dst.capacity());

        assertEquals(ModelStatus.SUSPENDED, refused.status());
        assertEquals(0, refused.consumed());

        completeAsynchronously();
        assertEquals(1, resumed);

        ModelPipelineResult result = pipeline.transform(0L, 0L, 0L, NONE,
            buffer(""), 0, 0, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals("abc", dst.getStringWithoutLengthUtf8(0, result.produced()));
    }

    @Test
    public void shouldSuspendStringInTheMiddleOfMultiFragmentValue()
    {
        StringExtModelPipeline pipeline = string(new AwaitString(StringEvent.SEGMENT, 2));
        UnsafeBufferEx dst = new UnsafeBufferEx(new byte[16]);

        ModelPipelineResult first = pipeline.transform(0L, 0L, 0L, INIT,
            buffer("abc"), 0, 3, dst, 0, dst.capacity());

        assertEquals(ModelStatus.UNDERFLOW, first.status());
        int produced = first.produced();

        ModelPipelineResult second = pipeline.transform(0L, 0L, 0L, FIN,
            buffer("def"), 0, 3, dst, produced, dst.capacity());

        assertEquals(ModelStatus.SUSPENDED, second.status());
        assertEquals(3, second.consumed());
        assertEquals(0, second.produced());

        completeAsynchronously();

        ModelPipelineResult result = pipeline.transform(0L, 0L, 0L, NONE,
            buffer(""), 0, 0, dst, produced, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals("abcdef", dst.getStringWithoutLengthUtf8(0, produced + result.produced()));
    }

    @Test
    public void shouldOverflowStringAfterResumption()
    {
        StringExtModelPipeline pipeline = string(new AwaitString(StringEvent.START_VALUE, 1));
        UnsafeBufferEx dst = new UnsafeBufferEx(new byte[4]);

        ModelPipelineResult suspended = pipeline.transform(0L, 0L, 0L, COMPLETE,
            buffer("0123456789"), 0, 10, dst, 0, dst.capacity());

        assertEquals(ModelStatus.SUSPENDED, suspended.status());
        completeAsynchronously();

        StringBuilder drained = new StringBuilder();
        ModelStatus status;
        int overflows = 0;
        do
        {
            ModelPipelineResult result = pipeline.transform(0L, 0L, 0L, NONE,
                buffer(""), 0, 0, dst, 0, dst.capacity());
            status = result.status();
            drained.append(dst.getStringWithoutLengthUtf8(0, result.produced()));
            overflows += status == ModelStatus.OVERFLOW ? 1 : 0;
        } while (status == ModelStatus.OVERFLOW);

        assertEquals(ModelStatus.COMPLETE, status);
        assertEquals("0123456789", drained.toString());
        assertTrue(overflows > 0);
        assertEquals(1, resumed);
    }

    @Test
    public void shouldSuspendBytesInTheMiddleOfMultiFragmentValueAndOverflowAfterResumption()
    {
        BytesExtModelPipeline pipeline = bytes(new AwaitBytes(BytesEvent.SEGMENT, 2));
        UnsafeBufferEx dst = new UnsafeBufferEx(new byte[4]);

        ModelPipelineResult first = pipeline.transform(0L, 0L, 0L, INIT,
            buffer("0123"), 0, 4, dst, 0, dst.capacity());

        assertEquals(ModelStatus.UNDERFLOW, first.status());
        StringBuilder drained = new StringBuilder(dst.getStringWithoutLengthUtf8(0, first.produced()));

        ModelPipelineResult second = pipeline.transform(0L, 0L, 0L, FIN,
            buffer("456789"), 0, 6, dst, 0, dst.capacity());

        assertEquals(ModelStatus.SUSPENDED, second.status());
        assertEquals(6, second.consumed());
        completeAsynchronously();

        ModelStatus status;
        do
        {
            ModelPipelineResult result = pipeline.transform(0L, 0L, 0L, NONE,
                buffer(""), 0, 0, dst, 0, dst.capacity());
            status = result.status();
            drained.append(dst.getStringWithoutLengthUtf8(0, result.produced()));
        } while (status == ModelStatus.OVERFLOW);

        assertEquals(ModelStatus.COMPLETE, status);
        assertEquals("0123456789", drained.toString());
    }

    private StringExtModelPipeline string(
        StringTransform transform)
    {
        CoreModelValidator validator = (flags, data, index, length) -> Validity.VALID;
        return new StringExtModelPipeline(
            new CoreModelHandler(mock(EngineContext.class), StringModel.NAME, () -> validator, false, false),
            validator, false, List.of(transform), ModelEnvelope.NONE, 0, () -> resumed++);
    }

    private BytesExtModelPipeline bytes(
        BytesTransform transform)
    {
        CoreModelValidator validator = (flags, data, index, length) -> Validity.VALID;
        return new BytesExtModelPipeline(
            new CoreModelHandler(mock(EngineContext.class), BytesModel.NAME, () -> validator, false, false),
            validator, false, List.of(transform), ModelEnvelope.NONE, 0, () -> resumed++);
    }

    private void completeAsynchronously()
    {
        while (!signals.isEmpty())
        {
            signals.poll().run();
        }
    }

    private static DirectBufferEx buffer(
        String value)
    {
        return new UnsafeBufferEx(value.getBytes());
    }

    // suspends on the nth presentation of an event, standing in for work completed through the signaler
    // back on the pipeline's own thread, then forwards that event once resumed
    private final class AwaitString implements StringTransform
    {
        private final StringEvent awaited;
        private final int occurrence;

        private int seen;
        private boolean awaiting;

        private AwaitString(
            StringEvent awaited,
            int occurrence)
        {
            this.awaited = awaited;
            this.occurrence = occurrence;
        }

        @Override
        public ModelStatus transform(
            StringController control,
            StringSource source,
            StringEvent event,
            StringSink sink)
        {
            ModelStatus status;
            if (event == awaited && ++seen == occurrence)
            {
                awaiting = true;
                signals.add(control.resumer());
                status = ModelStatus.SUSPENDED;
            }
            else
            {
                status = sink.transform(control, source, event);
            }
            return status;
        }

        @Override
        public ModelStatus resume(
            StringController control,
            StringSource source,
            StringEvent event,
            StringSink sink)
        {
            ModelStatus status;
            if (awaiting)
            {
                awaiting = false;
                status = sink.transform(control, source, event);
            }
            else
            {
                status = sink.resume(control, source, event);
            }
            return status;
        }

        @Override
        public void reset()
        {
            seen = 0;
            awaiting = false;
        }

        @Override
        public boolean deterministic()
        {
            return false;
        }
    }

    private final class AwaitBytes implements BytesTransform
    {
        private final BytesEvent awaited;
        private final int occurrence;

        private int seen;
        private boolean awaiting;

        private AwaitBytes(
            BytesEvent awaited,
            int occurrence)
        {
            this.awaited = awaited;
            this.occurrence = occurrence;
        }

        @Override
        public ModelStatus transform(
            BytesController control,
            BytesSource source,
            BytesEvent event,
            BytesSink sink)
        {
            ModelStatus status;
            if (event == awaited && ++seen == occurrence)
            {
                awaiting = true;
                signals.add(control.resumer());
                status = ModelStatus.SUSPENDED;
            }
            else
            {
                status = sink.transform(control, source, event);
            }
            return status;
        }

        @Override
        public ModelStatus resume(
            BytesController control,
            BytesSource source,
            BytesEvent event,
            BytesSink sink)
        {
            ModelStatus status;
            if (awaiting)
            {
                awaiting = false;
                status = sink.transform(control, source, event);
            }
            else
            {
                status = sink.resume(control, source, event);
            }
            return status;
        }

        @Override
        public void reset()
        {
            seen = 0;
            awaiting = false;
        }

        @Override
        public boolean deterministic()
        {
            return false;
        }
    }
}
