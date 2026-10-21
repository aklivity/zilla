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
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.util.Flags;

// A media section is an object holding the media, or a reference to it, at `container.member`. The bytes of the section
// are relayed unmodified and in order. The decoded media is presented to the chain as one continuous value, and only the
// verdict of the chain applies: what it produces is discarded. The bytes that carry the end of the media wait for the
// verdict of the chain, so a rejected section never completes downstream.
final class LlmMediaGuardPipeline implements LlmGuardPipeline
{
    private static final DirectBufferEx EMPTY = new UnsafeBufferEx(new byte[0]);

    private static final int STEP = 4096;
    private static final int VERDICT_CAPACITY = 1 << 14;

    private final ModelPipeline pipeline;
    private final LlmObjectScanner container;
    private final LlmObjectScanner member;
    private final LlmMediaDecoder decoder;
    private final MutableDirectBufferEx verdictBuffer;
    private final UnsafeBufferEx decodedRO;
    private final ModelPipelineResult result;

    private boolean started;
    private int observed;
    private int decodedProgress;
    private int values;

    LlmMediaGuardPipeline(
        ModelPipeline pipeline,
        String container,
        String member,
        boolean dataUri)
    {
        this.pipeline = pipeline;
        this.container = LlmObjectScanner.member(container);
        this.member = LlmObjectScanner.string(member);
        this.decoder = new LlmMediaDecoder(dataUri);
        this.verdictBuffer = new UnsafeBufferEx(new byte[VERDICT_CAPACITY]);
        this.decodedRO = new UnsafeBufferEx();
        this.result = new ModelPipelineResult();
        begin();
    }

    @Override
    public void reset()
    {
        pipeline.reset();
        begin();
    }

    @Override
    public ModelPipelineResult accept(
        long traceId,
        long bindingId,
        long authorization,
        boolean sectionInit,
        boolean sectionFin,
        DirectBufferEx src,
        int srcIndex,
        int srcLimit,
        MutableDirectBufferEx dst,
        int dstIndex,
        int dstLimit)
    {
        int position = srcIndex;
        int produced = 0;
        ModelStatus status = verdict(traceId, bindingId, authorization, false);
        boolean stopped = isBlocked(status);

        while (!stopped && position < srcLimit)
        {
            final int step = Math.min(Math.min(srcLimit - position, dstLimit - dstIndex - produced), STEP);

            if (step > 0)
            {
                status = observed < step && !observe(src, position + observed, position + step)
                    ? ModelStatus.REJECTED
                    : verdict(traceId, bindingId, authorization, false);
                stopped = isBlocked(status);
                observed = stopped ? step : 0;

                if (!stopped)
                {
                    dst.putBytes(dstIndex + produced, src, position, step);
                    position += step;
                    produced += step;
                }
            }
            else
            {
                stopped = true;
            }
        }

        if (position < srcLimit && status != ModelStatus.REJECTED && status != ModelStatus.SUSPENDED)
        {
            status = ModelStatus.OVERFLOW;
        }
        else if (sectionFin && !stopped)
        {
            status = verdict(traceId, bindingId, authorization, true);
        }

        values = produced;

        return result.set(status, position - srcIndex, produced);
    }

    @Override
    public ModelPipelineResult finish(
        long traceId,
        long bindingId,
        long authorization,
        MutableDirectBufferEx dst,
        int dstIndex,
        int dstLimit)
    {
        values = 0;

        return result.set(verdict(traceId, bindingId, authorization, true), 0, 0);
    }

    @Override
    public boolean finishes()
    {
        return true;
    }

    @Override
    public boolean identity()
    {
        return true;
    }

    @Override
    public int values()
    {
        return values;
    }

    @Override
    public int matched()
    {
        return values;
    }

    @Override
    public boolean diverged()
    {
        return false;
    }

    @Override
    public int closeWidth()
    {
        return 0;
    }

    @Override
    public int close(
        MutableDirectBufferEx dst,
        int dstIndex)
    {
        return 0;
    }

    private void begin()
    {
        container.begin();
        member.begin();
        decoder.begin();
        started = false;
        observed = 0;
        decodedProgress = 0;
        values = 0;
    }

    private boolean observe(
        DirectBufferEx src,
        int from,
        int limit)
    {
        boolean valid = true;
        int position = from;

        while (valid && position < limit)
        {
            final int region = container.region();

            if (region == LlmObjectScanner.PREFIX)
            {
                position = container.scan(src, position, limit);

                if (container.region() == LlmObjectScanner.VALUE)
                {
                    member.begin();
                }
            }
            else if (region == LlmObjectScanner.VALUE)
            {
                final int end = container.scan(src, position, limit);

                valid = observeMember(src, position, end);
                position = end;
            }
            else
            {
                position = limit;
            }
        }

        return valid;
    }

    private boolean observeMember(
        DirectBufferEx src,
        int from,
        int limit)
    {
        boolean valid = true;
        int position = from;

        while (valid && position < limit)
        {
            final int region = member.region();

            if (region == LlmObjectScanner.PREFIX)
            {
                position = member.scan(src, position, limit);

                if (member.region() == LlmObjectScanner.VALUE)
                {
                    decoder.begin();
                }
            }
            else if (region == LlmObjectScanner.VALUE)
            {
                final int end = member.scan(src, position, limit);

                valid = decoder.feed(src, position, end - position, false);

                if (valid && member.region() == LlmObjectScanner.SUFFIX)
                {
                    valid = decoder.feed(EMPTY, 0, 0, true);
                }

                position = end;
            }
            else
            {
                position = limit;
            }
        }

        return valid;
    }

    private ModelStatus verdict(
        long traceId,
        long bindingId,
        long authorization,
        boolean sectionFin)
    {
        ModelStatus status = drain(traceId, bindingId, authorization);

        if (!isBlocked(status) && started && member.region() != LlmObjectScanner.TAIL &&
            (sectionFin || member.region() == LlmObjectScanner.SUFFIX))
        {
            status = finishChain(traceId, bindingId, authorization);
        }
        else if (!isBlocked(status) && member.region() == LlmObjectScanner.SUFFIX)
        {
            member.settle();
        }

        return !isBlocked(status) && sectionFin ? ModelStatus.COMPLETE : status;
    }

    private ModelStatus drain(
        long traceId,
        long bindingId,
        long authorization)
    {
        ModelStatus status = ModelStatus.UNDERFLOW;
        boolean stalled = false;

        while (decodedProgress < decoder.length() && !isBlocked(status) && !stalled)
        {
            decodedRO.wrap(decoder.decoded(), 0, decoder.length());

            final ModelPipelineResult inner = pipeline.transform(traceId, bindingId, authorization, flags(0), decodedRO,
                decodedProgress, decoder.length(), verdictBuffer, 0, verdictBuffer.capacity());

            started = true;
            decodedProgress += inner.consumed();
            status = inner.status();
            stalled = inner.consumed() == 0 && inner.produced() == 0;
        }

        status = stalled && !isBlocked(status) ? ModelStatus.OVERFLOW : status;

        if (decodedProgress == decoder.length())
        {
            decoder.drain();
            decodedProgress = 0;
        }

        return status;
    }

    private ModelStatus finishChain(
        long traceId,
        long bindingId,
        long authorization)
    {
        ModelStatus status = ModelStatus.OVERFLOW;
        boolean progressed = true;

        while (status == ModelStatus.OVERFLOW && progressed)
        {
            final ModelPipelineResult inner = pipeline.transform(traceId, bindingId, authorization, flags(Flags.FIN), EMPTY, 0, 0,
                verdictBuffer, 0, verdictBuffer.capacity());

            started = true;
            status = inner.status();
            progressed = inner.produced() > 0;
        }

        if (!isBlocked(status))
        {
            member.settle();
        }

        return status;
    }

    private int flags(
        int flags)
    {
        return started ? flags : flags | Flags.INIT;
    }

    private static boolean isBlocked(
        ModelStatus status)
    {
        return status == ModelStatus.OVERFLOW || status == ModelStatus.REJECTED || status == ModelStatus.SUSPENDED;
    }
}
