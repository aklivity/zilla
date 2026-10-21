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
package io.aklivity.zilla.runtime.model.classify.internal;

import static io.aklivity.zilla.runtime.engine.util.Flags.hasFin;

import java.util.List;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.ExpandableArrayBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.engine.classifier.Detector;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;

// Presents a value to the classifiers as it arrives. Whenever no classification is in flight, the text that has arrived
// since the last one is classified together with the trailing text that the classifiers could not rule out, so a pattern
// straddling two classifications is seen whole; text that arrives meanwhile waits and is classified as one. What passes
// is relayed, apart from the trailing text that the classifiers retained, which is held until the next classification
// passes or the value ends. A rejection rejects the value, so the text relayed before it is the only part of the value
// the caller has seen. A value that is not streamed is held until it is complete and classified once.
final class ClassifyModelPipeline implements ModelPipeline
{
    private static final long NO_FIN = -1L;

    private final List<Detector> detectors;
    private final int maxLength;
    private final boolean streaming;
    private final Runnable resumed;
    private final ExpandableArrayBufferEx buffer;
    private final ModelPipelineResult result;
    private final Detector.CompletionCallback completion;

    private long base;
    private long length;
    private long drained;
    private long released;
    private long verified;
    private long detectEnd;
    private long finLength;
    private long retained;
    private String window;
    private int retainedChars;
    private int detecting;
    private boolean awaiting;
    private ModelStatus resolved;
    private long generation;

    ClassifyModelPipeline(
        List<Detector> detectors,
        int maxLength,
        boolean streaming,
        Runnable resumed)
    {
        this.detectors = detectors;
        this.maxLength = maxLength;
        this.streaming = streaming;
        this.resumed = resumed;
        this.buffer = new ExpandableArrayBufferEx();
        this.result = new ModelPipelineResult();
        this.finLength = NO_FIN;
        this.completion = new Detector.CompletionCallback()
        {
            @Override
            public void completed(
                long contextId,
                boolean detected,
                int retained)
            {
                onDetected(contextId, detected, retained);
            }

            @Override
            public void failed(
                long contextId,
                Throwable ex)
            {
                onDetected(contextId, true, 0);
            }
        };
    }

    @Override
    public ModelPipelineResult transform(
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
        ModelStatus status;
        int consumed = 0;
        int produced = 0;

        if (resolved == ModelStatus.REJECTED)
        {
            status = ModelStatus.REJECTED;
        }
        else if (awaiting)
        {
            status = ModelStatus.SUSPENDED;
        }
        else
        {
            final int available = srcLimit - srcIndex;

            if (length - drained + available > maxLength)
            {
                resolved = ModelStatus.REJECTED;
                status = ModelStatus.REJECTED;
            }
            else
            {
                buffer.putBytes((int) (length - base), src, srcIndex, available);
                length += available;
                consumed = available;

                if (hasFin(flags))
                {
                    finLength = length;
                }

                if (length - verified > 0 && (finLength != NO_FIN || streaming))
                {
                    detect(traceId, bindingId);
                    status = ModelStatus.SUSPENDED;
                }
                else
                {
                    release();
                    produced = drain(dst, dstIndex, dstLimit);

                    status = drained < released ? ModelStatus.OVERFLOW
                        : drained == finLength ? ModelStatus.COMPLETE
                        : ModelStatus.UNDERFLOW;
                }
            }
        }

        return result.set(status, consumed, produced);
    }

    @Override
    public boolean identity()
    {
        return true;
    }

    @Override
    public boolean deterministic()
    {
        return false;
    }

    @Override
    public void reset()
    {
        base = 0L;
        length = 0L;
        drained = 0L;
        released = 0L;
        verified = 0L;
        detectEnd = 0L;
        finLength = NO_FIN;
        retained = 0L;
        window = null;
        retainedChars = 0;
        detecting = 0;
        awaiting = false;
        resolved = null;
        generation++;
    }

    private void release()
    {
        final long bound = finLength != NO_FIN && verified == length ? length : verified - retained;

        released = Math.max(released, boundary(bound));
    }

    private long boundary(
        long position)
    {
        long aligned = position;

        while (aligned > drained && aligned < length && (buffer.getByte((int) (aligned - base)) & 0xC0) == 0x80)
        {
            aligned--;
        }

        return aligned;
    }

    private int drain(
        MutableDirectBufferEx dst,
        int dstIndex,
        int dstLimit)
    {
        final int remaining = (int) (released - drained);
        int limit = dstLimit;

        if (dst.isExpandable())
        {
            final int required = dstIndex + remaining;
            if (required > dst.capacity())
            {
                dst.checkLimit(required);
            }
            limit = Math.max(limit, dst.capacity());
        }

        final int available = Math.min(remaining, limit - dstIndex);

        dst.putBytes(dstIndex, buffer, (int) (drained - base), available);
        drained += available;
        compact();

        return available;
    }

    private void compact()
    {
        final int shift = (int) (drained - base);

        if (shift > 0)
        {
            final byte[] bytes = buffer.byteArray();

            System.arraycopy(bytes, shift, bytes, 0, (int) (length - drained));
            base = drained;
        }
    }

    private void detect(
        long traceId,
        long bindingId)
    {
        awaiting = true;
        detecting = detectors.size();
        detectEnd = length;

        final long contextId = ++generation;
        final int from = (int) (boundary(Math.max(verified - retained, drained)) - base);
        final String text = buffer.getStringWithoutLengthUtf8(from, (int) (detectEnd - base) - from);

        window = text;
        retainedChars = 0;

        detectors.forEach(detector -> detector.detect(traceId, bindingId, contextId, text, completion));
    }

    private void onDetected(
        long contextId,
        boolean rejected,
        int retainedByDetector)
    {
        if (contextId == generation && awaiting)
        {
            if (rejected)
            {
                resolved = ModelStatus.REJECTED;
            }
            else
            {
                retainedChars = Math.max(retainedChars, retainedByDetector);

                if (--detecting == 0)
                {
                    retained = utf8Length(window, retainedChars);
                    verified = detectEnd;
                    release();
                }
            }

            if (resolved != null || detecting == 0)
            {
                awaiting = false;
                resumed.run();
            }
        }
    }

    private static int utf8Length(
        String text,
        int chars)
    {
        int index = Math.max(text.length() - chars, 0);

        if (index > 0 && index < text.length() && Character.isLowSurrogate(text.charAt(index)))
        {
            index--;
        }

        int length = 0;

        for (; index < text.length(); index++)
        {
            final char ch = text.charAt(index);

            length += ch < 0x80 ? 1 : ch < 0x800 ? 2 : Character.isHighSurrogate(ch) ? 4 : Character.isLowSurrogate(ch) ? 0 : 3;
        }

        return length;
    }
}
