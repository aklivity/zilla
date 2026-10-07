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

import static io.aklivity.zilla.runtime.engine.util.Flags.FIN;
import static io.aklivity.zilla.runtime.engine.util.Flags.INIT;
import static io.aklivity.zilla.runtime.engine.util.Flags.hasFin;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.ExpandableArrayBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;

final class ModelPipelineChain implements ModelPipeline
{
    private static final int INITIAL_CAPACITY = 4096;
    private static final int MINIMUM_ROOM = 256;

    private enum Stage
    {
        NONE,
        FIRST,
        SECOND
    }

    private final ModelPipeline first;
    private final ModelPipeline second;
    private final boolean verdict;
    private final ModelPipelineResult result;
    private final ExpandableArrayBufferEx carry;
    private final ExpandableArrayBufferEx pending;

    private int carryLength;
    private int pendingLength;
    private boolean firstInit;
    private boolean secondInit;
    private boolean firstDone;
    private boolean firstDraining;
    private boolean firstStarved;
    private boolean secondDraining;
    private boolean inputFin;
    private Stage suspended;

    private long traceId;
    private long bindingId;
    private long authorization;
    private MutableDirectBufferEx dst;
    private int dstIndex;
    private int dstLimit;
    private int produced;

    private DirectBufferEx window;
    private int windowAt;
    private int windowLimit;

    private DirectBufferEx forwardBuffer;
    private int forwardIndex;
    private int forwardLimit;

    ModelPipelineChain(
        ModelPipeline first,
        ModelPipeline second)
    {
        this.first = first;
        this.second = second;
        this.verdict = first.identity();
        this.result = new ModelPipelineResult();
        this.carry = new ExpandableArrayBufferEx(INITIAL_CAPACITY);
        this.pending = new ExpandableArrayBufferEx(INITIAL_CAPACITY);
        this.suspended = Stage.NONE;
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
        this.traceId = traceId;
        this.bindingId = bindingId;
        this.authorization = authorization;
        this.dst = dst;
        this.dstIndex = dstIndex;
        this.dstLimit = dstLimit;
        this.produced = 0;
        this.inputFin |= hasFin(flags);
        this.forwardBuffer = null;
        this.firstStarved = false;

        final int srcLength = suspended != Stage.NONE ? 0 : srcLimit - srcIndex;
        windowOver(src, srcIndex, srcLength);

        final boolean resumeSecond = suspended == Stage.SECOND;
        boolean forward = resumeSecond || pendingLength > 0 || secondDraining || firstDone;
        boolean suspendedFirst = false;
        ModelStatus status = null;

        while (status == null)
        {
            if (forward)
            {
                forward = false;
                status = driveSecond();
            }

            if (status == null && firstDone && secondDraining)
            {
                forward = true;
            }
            else if (status == null)
            {
                if (suspendedFirst)
                {
                    status = ModelStatus.SUSPENDED;
                }
                else if (firstDone)
                {
                    status = ModelStatus.UNDERFLOW;
                }
                else if (firstStarved ||
                    suspended != Stage.FIRST && windowAt == windowLimit && !inputFin && !firstDraining)
                {
                    status = ModelStatus.UNDERFLOW;
                }
                else
                {
                    status = driveFirst();
                    suspendedFirst = suspended == Stage.FIRST;
                    forward = forwardBuffer != null || pendingLength > 0 || firstDone;
                }
            }
        }

        final int consumed;
        switch (status)
        {
        case REJECTED:
            consumed = 0;
            produced = 0;
            break;
        case COMPLETE:
            consumed = srcLength;
            clear();
            break;
        default:
            consumed = srcLength;
            retainCarry();
            break;
        }

        return result.set(status, consumed, produced);
    }

    @Override
    public boolean identity()
    {
        return first.identity() && second.identity();
    }

    @Override
    public boolean deterministic()
    {
        return first.deterministic() && second.deterministic();
    }

    @Override
    public int padding(
        DirectBufferEx data,
        int index,
        int length)
    {
        return first.padding(data, index, length) + second.padding(data, index, length);
    }

    @Override
    public void reset()
    {
        first.reset();
        second.reset();
        clear();
    }

    private void windowOver(
        DirectBufferEx src,
        int srcIndex,
        int srcLength)
    {
        if (carryLength > 0)
        {
            if (srcLength > 0)
            {
                carry.putBytes(carryLength, src, srcIndex, srcLength);
                carryLength += srcLength;
            }

            window = carry;
            windowAt = 0;
            windowLimit = carryLength;
        }
        else
        {
            window = src;
            windowAt = srcIndex;
            windowLimit = srcIndex + srcLength;
        }
    }

    private void retainCarry()
    {
        final int leftover = windowLimit - windowAt;

        if (leftover > 0)
        {
            carry.putBytes(0, window, windowAt, leftover);
        }

        carryLength = leftover;
    }

    private ModelStatus driveFirst()
    {
        ModelStatus status = null;

        final boolean resuming = suspended == Stage.FIRST;
        suspended = Stage.NONE;

        ensureRoom();

        final int flags = (firstInit ? 0 : INIT) | (inputFin ? FIN : 0);
        final int available = windowLimit - windowAt;

        final ModelPipelineResult stage = first.transform(traceId, bindingId, authorization, flags,
            window, windowAt, windowLimit, pending, pendingLength, pending.capacity());

        firstInit = true;

        final ModelStatus outcome = stage.status();
        final int consumed = stage.consumed();
        final int output = stage.produced();

        if (outcome == ModelStatus.REJECTED)
        {
            status = ModelStatus.REJECTED;
        }
        else
        {
            if (verdict)
            {
                forwardVerdict(consumed);
            }
            else
            {
                pendingLength += output;
            }

            windowAt += consumed;
            firstDraining = outcome == ModelStatus.OK || outcome == ModelStatus.OVERFLOW;
            firstStarved = outcome == ModelStatus.UNDERFLOW;

            switch (outcome)
            {
            case COMPLETE:
                firstDone = true;
                break;
            case SUSPENDED:
                suspended = Stage.FIRST;
                break;
            case UNDERFLOW:
                break;
            default:
                if (!resuming && consumed == 0 && output == 0 && available > 0)
                {
                    status = outcome;
                }
                break;
            }
        }

        return status;
    }

    private void forwardVerdict(
        int consumed)
    {
        if (consumed > 0)
        {
            if (pendingLength == 0)
            {
                forwardBuffer = window;
                forwardIndex = windowAt;
                forwardLimit = windowAt + consumed;
            }
            else
            {
                pending.putBytes(pendingLength, window, windowAt, consumed);
                pendingLength += consumed;
            }
        }
    }

    private ModelStatus driveSecond()
    {
        ModelStatus status = null;

        final boolean resuming = suspended == Stage.SECOND;
        suspended = resuming ? Stage.NONE : suspended;

        final DirectBufferEx buffer;
        final int index;
        final int limit;

        if (forwardBuffer != null)
        {
            buffer = forwardBuffer;
            index = forwardIndex;
            limit = forwardLimit;
        }
        else
        {
            buffer = pending;
            index = 0;
            limit = pendingLength;
        }

        forwardBuffer = null;

        if (limit > index || firstDone || secondDraining || resuming)
        {
            final int flags = (secondInit ? 0 : INIT) | (firstDone ? FIN : 0);

            final ModelPipelineResult stage = second.transform(traceId, bindingId, authorization, flags,
                buffer, index, limit, dst, dstIndex + produced, dstLimit);

            secondInit = true;

            final ModelStatus outcome = stage.status();
            final int consumed = stage.consumed();
            final int output = stage.produced();

            produced += output;

            if (outcome != ModelStatus.REJECTED && outcome != ModelStatus.COMPLETE)
            {
                retainPending(buffer, index + consumed, limit);
            }

            secondDraining = outcome == ModelStatus.OK || outcome == ModelStatus.OVERFLOW;

            switch (outcome)
            {
            case REJECTED:
            case COMPLETE:
            case OVERFLOW:
                status = outcome;
                break;
            case SUSPENDED:
                suspended = Stage.SECOND;
                status = outcome;
                break;
            default:
                if (consumed == 0 && output == 0 && limit > index && secondDraining)
                {
                    status = outcome;
                }
                break;
            }
        }

        return status;
    }

    private void retainPending(
        DirectBufferEx buffer,
        int index,
        int limit)
    {
        final int leftover = limit - index;

        if (leftover > 0)
        {
            pending.putBytes(0, buffer, index, leftover);
        }

        pendingLength = leftover;
    }

    private void ensureRoom()
    {
        if (pending.capacity() - pendingLength < MINIMUM_ROOM)
        {
            pending.checkLimit(pendingLength + Math.max(MINIMUM_ROOM, pending.capacity()));
        }
    }

    private void clear()
    {
        carryLength = 0;
        pendingLength = 0;
        firstInit = false;
        secondInit = false;
        firstDone = false;
        firstDraining = false;
        secondDraining = false;
        inputFin = false;
        suspended = Stage.NONE;
        forwardBuffer = null;
    }
}
