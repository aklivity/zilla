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

public class StringModelExtHandlerTest
{
    @Test
    public void shouldReportNoPaddingInEitherDirectionByDefault()
    {
        StringModelExtHandler handler = new StringModelExtHandler()
        {
        };

        assertEquals(0, handler.decodePadding());
        assertEquals(0, handler.encodePadding());
    }

    @Test
    public void shouldForwardBothDirectionsUnchangedByDefault()
    {
        StringModelExtHandler handler = new StringModelExtHandler()
        {
        };
        Stream stream = new Stream();

        assertSame(stream, handler.decode(stream));
        assertSame(stream, handler.encode(stream));
    }

    @Test
    public void shouldExtendOneDirectionOnly()
    {
        StringModelExtHandler handler = new StringModelExtHandler()
        {
            @Override
            public <T extends StringTransformable<T>> T decode(
                T stream)
            {
                return stream.transform(StringTransform.NONE);
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
        assertTrue(StringTransform.NONE.identity());
    }

    @Test
    public void shouldForwardEveryEventForNoneTransform()
    {
        Sink sink = new Sink();
        StringSource source = () -> null;

        assertEquals(ModelStatus.OK, StringTransform.NONE.transform(new Control(), source, StringEvent.START_VALUE, sink));
        assertEquals(ModelStatus.OK, StringTransform.NONE.transform(new Control(), source, StringEvent.SEGMENT, sink));
        assertEquals(ModelStatus.OK, StringTransform.NONE.transform(new Control(), source, StringEvent.END_VALUE, sink));
        assertEquals(3, sink.count);
    }

    @Test
    public void shouldResumeThroughToSinkByDefault()
    {
        StringTransform transform = (control, source, event, sink) -> sink.transform(control, source, event);
        Sink sink = new Sink();

        assertEquals(ModelStatus.OK, transform.resume(new Control(), () -> null, StringEvent.SEGMENT, sink));
        assertFalse(transform.identity());
    }

    @Test
    public void shouldDistinguishSegmentFromFraming()
    {
        assertTrue(StringEvent.SEGMENT.segmented());
        assertFalse(StringEvent.START_VALUE.segmented());
        assertFalse(StringEvent.END_VALUE.segmented());
    }

    @Test
    public void shouldReadEmptyEnvelopeByDefault()
    {
        Control control = new Control();

        assertSame(ModelEnvelope.NONE, control.envelope());
        control.consumed(4);
    }

    private static final class Stream implements StringTransformable<Stream>
    {
        private int count;

        @Override
        public Stream transform(
            StringTransform transform)
        {
            count++;
            return this;
        }
    }

    private static final class Sink implements StringSink
    {
        private int count;

        @Override
        public ModelStatus transform(
            StringController control,
            StringSource source,
            StringEvent event)
        {
            count++;
            return ModelStatus.OK;
        }

        @Override
        public ModelStatus resume(
            StringController control,
            StringSource source,
            StringEvent event)
        {
            return ModelStatus.OK;
        }

        @Override
        public boolean identity()
        {
            return true;
        }
    }

    private static final class Control implements StringController
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
