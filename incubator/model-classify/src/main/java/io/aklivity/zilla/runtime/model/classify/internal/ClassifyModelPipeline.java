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

final class ClassifyModelPipeline implements ModelPipeline
{
    private final List<Detector> detectors;
    private final int maxLength;
    private final Runnable resumed;
    private final ExpandableArrayBufferEx buffer;
    private final ModelPipelineResult result;
    private final Detector.CompletionCallback completion;

    private int length;
    private int drained;
    private int detecting;
    private boolean awaiting;
    private ModelStatus resolved;
    private long generation;

    ClassifyModelPipeline(
        List<Detector> detectors,
        int maxLength,
        Runnable resumed)
    {
        this.detectors = detectors;
        this.maxLength = maxLength;
        this.resumed = resumed;
        this.buffer = new ExpandableArrayBufferEx();
        this.result = new ModelPipelineResult();
        this.completion = new Detector.CompletionCallback()
        {
            @Override
            public void completed(
                long contextId,
                boolean detected)
            {
                onDetected(contextId, detected);
            }

            @Override
            public void failed(
                long contextId,
                Throwable ex)
            {
                onDetected(contextId, true);
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
        else if (resolved == ModelStatus.OK)
        {
            int remaining = length - drained;

            if (dst.isExpandable())
            {
                final int required = dstIndex + remaining;
                if (required > dst.capacity())
                {
                    dst.checkLimit(required);
                }
                dstLimit = Math.max(dstLimit, dst.capacity());
            }

            int available = Math.min(remaining, dstLimit - dstIndex);
            dst.putBytes(dstIndex, buffer, drained, available);
            drained += available;
            produced = available;
            status = drained < length ? ModelStatus.OVERFLOW : ModelStatus.COMPLETE;
        }
        else if (awaiting)
        {
            status = ModelStatus.SUSPENDED;
        }
        else
        {
            int available = srcLimit - srcIndex;

            if (length + available > maxLength)
            {
                resolved = ModelStatus.REJECTED;
                status = ModelStatus.REJECTED;
            }
            else
            {
                buffer.putBytes(length, src, srcIndex, available);
                length += available;
                consumed = available;

                if (hasFin(flags))
                {
                    detect(traceId, bindingId);
                    status = ModelStatus.SUSPENDED;
                }
                else
                {
                    status = ModelStatus.UNDERFLOW;
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
    public void reset()
    {
        length = 0;
        drained = 0;
        detecting = 0;
        awaiting = false;
        resolved = null;
        generation++;
    }

    private void detect(
        long traceId,
        long bindingId)
    {
        awaiting = true;
        detecting = detectors.size();
        final long contextId = ++generation;
        final String text = buffer.getStringWithoutLengthUtf8(0, length);
        detectors.forEach(detector -> detector.detect(traceId, bindingId, contextId, text, completion));
    }

    private void onDetected(
        long contextId,
        boolean rejected)
    {
        if (contextId == generation && awaiting)
        {
            if (rejected)
            {
                resolved = ModelStatus.REJECTED;
            }
            else if (--detecting == 0)
            {
                resolved = ModelStatus.OK;
            }

            if (resolved != null)
            {
                awaiting = false;
                resumed.run();
            }
        }
    }
}
