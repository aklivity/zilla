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
package io.aklivity.zilla.runtime.model.core.ext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;

public class BytesModelExtHandlerTest
{
    @Test
    public void shouldReportNoPaddingInEitherDirectionByDefault()
    {
        BytesModelExtHandler handler = new BytesModelExtHandler()
        {
        };

        assertEquals(0, handler.decodePadding());
        assertEquals(0, handler.encodePadding());
    }

    @Test
    public void shouldForwardBothDirectionsUnchangedByDefault()
    {
        BytesModelExtHandler handler = new BytesModelExtHandler()
        {
        };
        Stream stream = new Stream();

        assertSame(stream, handler.decode(stream));
        assertSame(stream, handler.encode(stream));
    }

    @Test
    public void shouldExtendOneDirectionOnly()
    {
        BytesModelExtHandler handler = new BytesModelExtHandler()
        {
            @Override
            public <T extends BytesTransformable<T>> T decode(
                T stream)
            {
                return stream.transform(BytesTransform.NONE);
            }
        };
        Stream stream = new Stream();

        handler.decode(stream);
        handler.encode(stream);

        assertEquals(1, stream.count);
    }

    @Test
    public void shouldIdentifyNoneAsIdentity()
    {
        assertTrue(BytesTransform.NONE.identity());
    }

    @Test
    public void shouldForwardEveryEventForNoneTransform()
    {
        Sink sink = new Sink();
        BytesSource source = () -> null;

        assertEquals(ModelStatus.OK, BytesTransform.NONE.transform(new Control(), source, BytesEvent.START_VALUE, sink));
        assertEquals(ModelStatus.OK, BytesTransform.NONE.transform(new Control(), source, BytesEvent.SEGMENT, sink));
        assertEquals(ModelStatus.OK, BytesTransform.NONE.transform(new Control(), source, BytesEvent.END_VALUE, sink));
        assertEquals(3, sink.count);
    }

    @Test
    public void shouldResumeThroughToSinkByDefault()
    {
        BytesTransform transform = (control, source, event, sink) -> sink.transform(control, source, event);
        Sink sink = new Sink();

        assertEquals(ModelStatus.OK, transform.resume(new Control(), () -> null, BytesEvent.SEGMENT, sink));
        assertFalse(transform.identity());
    }

    @Test
    public void shouldDistinguishSegmentFromFraming()
    {
        assertTrue(BytesEvent.SEGMENT.segmented());
        assertFalse(BytesEvent.START_VALUE.segmented());
        assertFalse(BytesEvent.END_VALUE.segmented());
    }

    @Test
    public void shouldReadEmptyEnvelopeByDefault()
    {
        Control control = new Control();

        assertSame(ModelEnvelope.NONE, control.envelope());
        control.consumed(4);
    }

    private static final class Stream implements BytesTransformable<Stream>
    {
        private int count;

        @Override
        public Stream transform(
            BytesTransform transform)
        {
            count++;
            return this;
        }
    }

    private static final class Sink implements BytesSink
    {
        private int count;

        @Override
        public ModelStatus transform(
            BytesController control,
            BytesSource source,
            BytesEvent event)
        {
            count++;
            return ModelStatus.OK;
        }

        @Override
        public ModelStatus resume(
            BytesController control,
            BytesSource source,
            BytesEvent event)
        {
            return ModelStatus.OK;
        }

        @Override
        public boolean identity()
        {
            return true;
        }
    }

    private static final class Control implements BytesController
    {
        @Override
        public void reject(
            String diagnostic)
        {
        }

        @Override
        public void withhold()
        {
        }
    }
}
