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

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.ExpandableArrayBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.model.core.ext.BytesController;
import io.aklivity.zilla.runtime.model.core.ext.BytesEvent;
import io.aklivity.zilla.runtime.model.core.ext.BytesSink;
import io.aklivity.zilla.runtime.model.core.ext.BytesSource;
import io.aklivity.zilla.runtime.model.core.ext.BytesTransform;

// A test-only substituting stage: it uppercases each segment as it flows, feeding its downstream a source
// of its own over the rewritten bytes rather than accumulating the value first. Uppercasing is 1:1, so a
// bounded write downstream consumes exactly as many source bytes as it wrote and this stage passes the
// control handle straight through rather than mediating it.
//
// A whole value that is one marker byte terminates instead: withholding raises nothing, rejecting supplies
// a diagnostic, so the two outcomes are distinguishable by the event each does or does not produce.
final class UppercaseBytesTransform implements BytesTransform
{
    static final int NO_MARKER = -1;

    private final int marker;
    private final int withhold;
    private final int reject;
    private final String diagnostic;

    private final ExpandableArrayBufferEx scratch;
    private final UnsafeBufferEx view;
    private final BytesSource source;

    private boolean started;
    private boolean applies;

    UppercaseBytesTransform(
        int withhold,
        int reject,
        String diagnostic)
    {
        this(NO_MARKER, withhold, reject, diagnostic);
    }

    UppercaseBytesTransform(
        int marker,
        int withhold,
        int reject,
        String diagnostic)
    {
        this.marker = marker;
        this.withhold = withhold;
        this.reject = reject;
        this.diagnostic = diagnostic;
        this.scratch = new ExpandableArrayBufferEx();
        this.view = new UnsafeBufferEx(new byte[0]);
        this.source = () -> view;
    }

    @Override
    public ModelStatus transform(
        BytesController control,
        BytesSource source,
        BytesEvent event,
        BytesSink sink)
    {
        ModelStatus status;
        if (event.segmented())
        {
            status = segment(control, source, event, sink, false);
        }
        else
        {
            if (event == BytesEvent.START_VALUE)
            {
                reset();
            }
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
        if (event.segmented())
        {
            status = segment(control, source, event, sink, true);
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
        started = false;
        applies = false;
    }

    private ModelStatus segment(
        BytesController control,
        BytesSource upstream,
        BytesEvent event,
        BytesSink sink,
        boolean resuming)
    {
        DirectBufferEx segment = upstream.getSegment();
        int length = segment.capacity();

        ModelStatus status;
        if (!started)
        {
            started = true;
            applies = marker == NO_MARKER || length > 0 && segment.getByte(0) == (byte) marker;

            if (length == 1 && withhold != NO_MARKER && segment.getByte(0) == (byte) withhold)
            {
                control.withhold();
                status = ModelStatus.REJECTED;
            }
            else if (length == 1 && reject != NO_MARKER && segment.getByte(0) == (byte) reject)
            {
                control.reject(diagnostic);
                status = ModelStatus.REJECTED;
            }
            else
            {
                status = forward(control, upstream, event, sink, segment, length, resuming);
            }
        }
        else
        {
            status = forward(control, upstream, event, sink, segment, length, resuming);
        }
        return status;
    }

    private ModelStatus forward(
        BytesController control,
        BytesSource upstream,
        BytesEvent event,
        BytesSink sink,
        DirectBufferEx segment,
        int length,
        boolean resuming)
    {
        BytesSource downstream = upstream;
        if (applies)
        {
            for (int i = 0; i < length; i++)
            {
                scratch.putByte(i, (byte) Character.toUpperCase((char) segment.getByte(i)));
            }
            view.wrap(scratch, 0, length);
            downstream = source;
        }

        return resuming
            ? sink.resume(control, downstream, event)
            : sink.transform(control, downstream, event);
    }
}
