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

// A section is an object whose primary member is a JSON value. The bytes of that member's value are presented to the
// chain as one continuous value and what the chain produces is written in their place. Every other byte of the
// object is relayed unmodified and in order: the bytes before the value as they arrive, and the bytes after it once
// the chain has finished the value.
final class LlmMemberModelPipeline implements LlmGuardPipeline
{
    private static final DirectBufferEx EMPTY = new UnsafeBufferEx(new byte[0]);

    private static final int LAG_CAPACITY = 1 << 16;

    private final ModelPipeline pipeline;
    private final boolean identity;
    private final LlmLockstep lockstep;
    private final LlmObjectScanner scanner;
    private final LlmObjectScanner probe;
    private final ModelPipelineResult result;

    private boolean started;
    private ModelStatus valueStatus;
    private int values;
    private int matched;

    LlmMemberModelPipeline(
        ModelPipeline pipeline,
        String member)
    {
        this.pipeline = pipeline;
        this.identity = pipeline.identity();
        this.lockstep = new LlmLockstep(LAG_CAPACITY);
        this.scanner = LlmObjectScanner.member(member);
        this.probe = LlmObjectScanner.member(member);
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
        ModelStatus status = ModelStatus.UNDERFLOW;
        boolean stopped = false;

        values = 0;
        matched = 0;

        while (!stopped && position < srcLimit)
        {
            final int region = scanner.region();

            if (region == LlmObjectScanner.VALUE)
            {
                probe.copy(scanner);

                final int end = probe.scan(src, position, srcLimit);
                final ModelPipelineResult inner = pipeline.transform(traceId, bindingId, authorization, flags(0), src,
                    position, end, dst, dstIndex + produced, dstLimit);

                follow(src, position, inner.consumed(), dst, dstIndex + produced, inner.produced());

                final boolean partial = inner.consumed() < end - position;

                if (!partial)
                {
                    scanner.copy(probe);
                }
                else
                {
                    scanner.scan(src, position, position + inner.consumed());
                }

                position += inner.consumed();
                produced += inner.produced();
                status = inner.status();
                valueStatus = status;
                stopped = partial || isBlocked(status);
            }
            else if (region == LlmObjectScanner.SUFFIX)
            {
                final ModelPipelineResult inner = finishValue(traceId, bindingId, authorization, dst, dstIndex + produced,
                    dstLimit);

                produced += inner.produced();
                status = inner.status();
                stopped = isBlocked(status);
            }
            else
            {
                final int limit = Math.min(srcLimit, position + dstLimit - dstIndex - produced);
                final int end = scanner.scan(src, position, limit);

                dst.putBytes(dstIndex + produced, src, position, end - position);
                produced += end - position;
                position = end;
                stopped = position == limit && limit < srcLimit;
            }
        }

        if (srcIndex == srcLimit && valueStatus == ModelStatus.SUSPENDED && scanner.region() == LlmObjectScanner.VALUE)
        {
            final ModelPipelineResult inner = pipeline.transform(traceId, bindingId, authorization, flags(0), EMPTY, 0, 0, dst,
                dstIndex + produced, dstLimit);

            follow(EMPTY, 0, 0, dst, dstIndex + produced, inner.produced());

            produced += inner.produced();
            status = inner.status();
            valueStatus = status;
            stopped = isBlocked(status);
        }

        if (position < srcLimit && status != ModelStatus.REJECTED && status != ModelStatus.SUSPENDED)
        {
            status = ModelStatus.OVERFLOW;
        }
        else if (sectionFin && !stopped && status != ModelStatus.REJECTED && status != ModelStatus.OVERFLOW)
        {
            if (scanner.region() != LlmObjectScanner.TAIL)
            {
                final ModelPipelineResult inner = finishValue(traceId, bindingId, authorization, dst, dstIndex + produced,
                    dstLimit);

                produced += inner.produced();
                status = inner.status();
            }
            else
            {
                status = ModelStatus.COMPLETE;
            }
        }

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
        int produced = 0;
        ModelStatus status = ModelStatus.COMPLETE;

        values = 0;
        matched = 0;

        if (scanner.region() != LlmObjectScanner.TAIL)
        {
            final ModelPipelineResult inner = finishValue(traceId, bindingId, authorization, dst, dstIndex, dstLimit);

            produced = inner.produced();
            status = inner.status();
        }

        return result.set(status, 0, produced);
    }

    @Override
    public boolean finishes()
    {
        return true;
    }

    @Override
    public boolean identity()
    {
        return identity;
    }

    @Override
    public int values()
    {
        return values;
    }

    @Override
    public int matched()
    {
        return matched;
    }

    @Override
    public boolean diverged()
    {
        return !identity && lockstep.diverged();
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
        scanner.begin();
        lockstep.begin();
        started = false;
        valueStatus = ModelStatus.UNDERFLOW;
    }

    private ModelPipelineResult finishValue(
        long traceId,
        long bindingId,
        long authorization,
        MutableDirectBufferEx dst,
        int dstIndex,
        int dstLimit)
    {
        final ModelPipelineResult inner = pipeline.transform(traceId, bindingId, authorization, flags(Flags.FIN), EMPTY, 0, 0,
            dst, dstIndex, dstLimit);

        follow(EMPTY, 0, 0, dst, dstIndex, inner.produced());

        if (!isBlocked(inner.status()))
        {
            scanner.settle();
        }

        return inner;
    }

    private void follow(
        DirectBufferEx src,
        int srcIndex,
        int consumed,
        MutableDirectBufferEx dst,
        int dstIndex,
        int produced)
    {
        started = true;
        values += produced;

        if (!identity)
        {
            lockstep.consumed(src, srcIndex, consumed);
            matched += lockstep.produced(dst, dstIndex, produced);
        }
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
