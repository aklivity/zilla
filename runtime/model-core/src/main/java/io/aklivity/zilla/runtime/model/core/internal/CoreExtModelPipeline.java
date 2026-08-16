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
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;

// Per-stream pipeline for a bytes/string model with at least one installed stage. The value streams
// through the composed chain as it arrives -- value-start, one segment per fragment, value-end -- rather
// than being materialized whole first, and the chain is bound sink-to-sink at assembly, so a composition
// of N stages is one pass with no intermediate buffer per stage.
//
// Everything here is model-agnostic: the fragment and flag bookkeeping, the incremental validation, and
// the bounded-destination suspend/resume accounting. A subclass owns only its model's own stage
// vocabulary and the adapters bridging it to this state.
//
// Two axes of back-pressure meet in one call. The destination bounds the output: a chain that cannot
// place everything reports the source bytes its bounded write took and returns OVERFLOW, so the caller
// drains, advances the input by exactly that, and calls again to resume the event that suspended. The
// input bounds the value: a fragment that does not close the value returns UNDERFLOW, and the next
// fragment arrives as a further segment of the same value.
abstract class CoreExtModelPipeline implements ModelPipeline
{
    private static final int FLAGS_INIT = 0x02;
    private static final int FLAGS_FIN = 0x01;

    private enum Phase
    {
        START,
        SEGMENTS,
        END,
        DONE
    }

    private final CoreModelHandler handler;
    private final CoreModelValidator validator;
    // LENIENT: a structurally-valid value that violates a semantic constraint (INVALID) is reported then
    // streamed through unchanged rather than rejected; a parse failure (MALFORMED) always rejects
    private final boolean lenient;
    private final ModelEnvelope envelope;
    private final int padding;
    private final ModelPipelineResult result;
    private final UnsafeBufferEx segment;

    private MutableDirectBufferEx target;
    private int targetAt;
    private int targetLimit;

    private Phase phase;
    private ValueEvent pending;
    private boolean initial;
    private int reported;
    private String diagnostic;
    private boolean withheld;

    CoreExtModelPipeline(
        CoreModelHandler handler,
        CoreModelValidator validator,
        boolean lenient,
        ModelEnvelope envelope,
        int padding)
    {
        this.handler = handler;
        this.validator = validator;
        this.lenient = lenient;
        this.envelope = envelope;
        this.padding = padding;
        this.result = new ModelPipelineResult();
        this.segment = new UnsafeBufferEx(new byte[0]);
        this.phase = Phase.START;
        this.initial = true;
    }

    @Override
    public final ModelPipelineResult transform(
        long traceId,
        long bindingId,
        long authorization,
        int flags,
        DirectBufferEx src,
        int srcIndex,
        int srcLimit,
        MutableDirectBufferEx dst,
        int dstIndex,
        int dstLimit)
    {
        if ((flags & FLAGS_INIT) != 0x00)
        {
            reset();
        }

        target = dst;
        targetAt = dstIndex;
        targetLimit = dstLimit;
        reported = 0;

        int srcLength = srcLimit - srcIndex;
        boolean last = (flags & FLAGS_FIN) != 0x00;
        segment.wrap(src, srcIndex, srcLength);

        ModelStatus pumped = pump(srcLength, last);

        ModelStatus status;
        int consumed;
        int produced;
        if (pumped == ModelStatus.REJECTED)
        {
            // withholding is a stage's own decision about a value it found nothing wrong with, so it
            // raises no event; rejecting is a failure, reported with whatever diagnostic the stage gave
            if (!withheld)
            {
                report(traceId, bindingId);
            }

            status = ModelStatus.REJECTED;
            consumed = 0;
            produced = 0;
        }
        else
        {
            consumed = consumption(srcLength);
            boolean tail = last && consumed == srcLength;
            Validity validity = validate(src, srcIndex, consumed, tail);
            boolean reject = validity == Validity.MALFORMED || validity == Validity.INVALID && !lenient;
            if (validity != Validity.VALID)
            {
                handler.validationFailure(traceId, bindingId);
            }

            if (reject)
            {
                status = ModelStatus.REJECTED;
                consumed = 0;
                produced = 0;
            }
            else
            {
                status = pending != null
                    ? ModelStatus.OVERFLOW
                    : phase == Phase.DONE ? ModelStatus.COMPLETE : ModelStatus.UNDERFLOW;
                produced = targetAt - dstIndex;
            }
        }
        return result.set(status, consumed, produced);
    }

    @Override
    public final int padding(
        DirectBufferEx data,
        int index,
        int length)
    {
        return padding;
    }

    @Override
    public final void reset()
    {
        phase = Phase.START;
        pending = null;
        initial = true;
        reported = 0;
        diagnostic = null;
        withheld = false;
        resetChain();
    }

    // feeds one event to the head of the composed chain
    protected abstract ModelStatus feed(
        ValueEvent event);

    // continues the event a prior OVERFLOW left in flight, once the caller has drained the destination
    protected abstract ModelStatus resume(
        ValueEvent event);

    protected abstract void resetChain();

    // the terminal write, bounded by the caller's destination; the caller reports what it took so the
    // segment's remainder is re-exposed when the event resumes
    protected final int write(
        DirectBufferEx buffer,
        int index,
        int length)
    {
        int written = Math.min(targetLimit - targetAt, length);
        target.putBytes(targetAt, buffer, index, written);
        targetAt += written;
        return written;
    }

    protected final DirectBufferEx segment()
    {
        return segment;
    }

    protected final ModelEnvelope envelope()
    {
        return envelope;
    }

    protected final void consumed(
        int sourceBytes)
    {
        reported += sourceBytes;
    }

    protected final void reject(
        String diagnostic)
    {
        this.diagnostic = diagnostic;
    }

    protected final void withhold()
    {
        withheld = true;
    }

    // one call delivers at most value-start, one segment, and value-end, in that order; whichever of them
    // a bounded destination stopped becomes the event the next call resumes
    private ModelStatus pump(
        int srcLength,
        boolean last)
    {
        ModelStatus status = ModelStatus.OK;

        if (phase == Phase.START)
        {
            status = advance(ValueEvent.START_VALUE);
            if (status == ModelStatus.OK)
            {
                phase = Phase.SEGMENTS;
            }
        }

        if (status == ModelStatus.OK && phase == Phase.SEGMENTS)
        {
            if (srcLength > 0 || pending == ValueEvent.SEGMENT)
            {
                status = advance(ValueEvent.SEGMENT);
            }

            if (status == ModelStatus.OK && last)
            {
                phase = Phase.END;
            }
        }

        if (status == ModelStatus.OK && phase == Phase.END)
        {
            status = advance(ValueEvent.END_VALUE);
            if (status == ModelStatus.OK)
            {
                phase = Phase.DONE;
            }
        }

        return status;
    }

    private ModelStatus advance(
        ValueEvent event)
    {
        ModelStatus status = pending == event ? resume(event) : feed(event);
        pending = status == ModelStatus.OVERFLOW ? event : null;
        return status;
    }

    // accepting the segment event accepts the whole window; a bounded write that suspended mid-segment
    // consumed only what it reported, and nothing before the segment consumes input at all
    private int consumption(
        int srcLength)
    {
        int consumed;
        if (pending == ValueEvent.START_VALUE)
        {
            consumed = 0;
        }
        else if (pending == ValueEvent.SEGMENT)
        {
            consumed = reported;
        }
        else
        {
            consumed = srcLength;
        }
        return consumed;
    }

    // validates exactly the bytes the chain took this call, so a window consumed across several calls is
    // decoded once end to end; FIN applies only once the whole window is consumed on the final fragment
    private Validity validate(
        DirectBufferEx src,
        int srcIndex,
        int consumed,
        boolean tail)
    {
        Validity validity = Validity.VALID;
        if (consumed > 0 || tail)
        {
            int fragmentFlags = (initial ? FLAGS_INIT : 0x00) | (tail ? FLAGS_FIN : 0x00);
            validity = validator.validate(fragmentFlags, src, srcIndex, consumed);
            initial = false;
        }
        return validity;
    }

    private void report(
        long traceId,
        long bindingId)
    {
        if (diagnostic != null)
        {
            handler.validationFailure(traceId, bindingId, diagnostic);
        }
        else
        {
            handler.validationFailure(traceId, bindingId);
        }
    }
}
