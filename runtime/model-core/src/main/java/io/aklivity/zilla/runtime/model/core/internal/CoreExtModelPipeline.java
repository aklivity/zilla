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

import java.util.List;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.ExpandableArrayBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;

// Per-stream decode pipeline for a bytes/string model with at least one installed extension. Unlike
// CoreModelPipeline's fragment-at-a-time identity copy, an installed extension's transform needs the
// complete decoded value (there is no schema to partially validate against), so this pipeline accumulates
// every fragment up to FLAGS_FIN, validates the whole value in one pass, then folds it through every
// installed extension's transform in discovery order before copying the result to the caller's destination.
// A transformed value that does not fit dst in one call is drained across subsequent calls from outputBuffer.
final class CoreExtModelPipeline implements ModelPipeline
{
    private static final int FLAGS_INIT = 0x02;
    private static final int FLAGS_FIN = 0x01;

    private final CoreModelHandler handler;
    private final CoreModelValidator validator;
    private final boolean lenient;
    private final List<ValueTransform> transforms;
    private final int extPadding;

    private final ExpandableArrayBufferEx valueBuffer;
    private final ExpandableArrayBufferEx scratchA;
    private final ExpandableArrayBufferEx scratchB;
    private final ExpandableArrayBufferEx outputBuffer;

    private final ModelPipelineResult result;

    private int valueLength;
    private int pendingOffset;
    private int pendingLength;

    CoreExtModelPipeline(
        CoreModelHandler handler,
        CoreModelValidator validator,
        boolean lenient,
        List<ValueTransform> transforms,
        int extPadding)
    {
        this.handler = handler;
        this.validator = validator;
        this.lenient = lenient;
        this.transforms = transforms;
        this.extPadding = extPadding;
        this.valueBuffer = new ExpandableArrayBufferEx();
        this.scratchA = new ExpandableArrayBufferEx();
        this.scratchB = new ExpandableArrayBufferEx();
        this.outputBuffer = new ExpandableArrayBufferEx();
        this.result = new ModelPipelineResult();
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
        ModelPipelineResult outcome;
        if (pendingLength > 0)
        {
            outcome = drain(dst, dstIndex, dstLimit);
        }
        else
        {
            outcome = accumulate(traceId, bindingId, flags, src, srcIndex, srcLimit, dst, dstIndex, dstLimit);
        }
        return outcome;
    }

    @Override
    public boolean identity()
    {
        return false;
    }

    @Override
    public int padding(
        DirectBufferEx data,
        int index,
        int length)
    {
        return extPadding;
    }

    @Override
    public void reset()
    {
        valueLength = 0;
        pendingOffset = 0;
        pendingLength = 0;
    }

    private ModelPipelineResult accumulate(
        long traceId,
        long bindingId,
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
            valueLength = 0;
        }

        int srcLength = srcLimit - srcIndex;
        valueBuffer.putBytes(valueLength, src, srcIndex, srcLength);
        valueLength += srcLength;

        ModelPipelineResult outcome;
        if ((flags & FLAGS_FIN) == 0x00)
        {
            outcome = result.set(ModelStatus.UNDERFLOW, srcLength, 0);
        }
        else
        {
            Validity validity = validator.validate(CoreModelValidator.FLAGS_COMPLETE, valueBuffer, 0, valueLength);
            boolean reject = validity == Validity.MALFORMED || validity == Validity.INVALID && !lenient;
            if (validity != Validity.VALID)
            {
                handler.validationFailure(traceId, bindingId);
            }

            if (reject)
            {
                outcome = result.set(ModelStatus.REJECTED, srcLength, 0);
            }
            else
            {
                outcome = extend(srcLength, dst, dstIndex, dstLimit);
            }
        }
        return outcome;
    }

    // folds the accumulated whole value through every installed extension's transform, in discovery
    // order; an extension signalling ValueTransform.OMIT withholds the value from delivery entirely
    private ModelPipelineResult extend(
        int consumed,
        MutableDirectBufferEx dst,
        int dstIndex,
        int dstLimit)
    {
        DirectBufferEx buf = valueBuffer;
        int off = 0;
        int len = valueLength;
        boolean omitted = false;

        int count = transforms.size();
        for (int i = 0; i < count; i++)
        {
            ValueTransform transform = transforms.get(i);
            ExpandableArrayBufferEx target = i == count - 1 ? outputBuffer : i % 2 == 0 ? scratchA : scratchB;
            int produced = transform.transform(buf, off, len, target, 0);
            if (produced == ValueTransform.OMIT)
            {
                omitted = true;
                break;
            }
            buf = target;
            off = 0;
            len = produced;
        }

        ModelPipelineResult outcome;
        if (omitted)
        {
            outcome = result.set(ModelStatus.REJECTED, consumed, 0);
        }
        else
        {
            pendingOffset = 0;
            pendingLength = len;
            outcome = drainInto(dst, dstIndex, dstLimit, consumed);
        }
        return outcome;
    }

    private ModelPipelineResult drain(
        MutableDirectBufferEx dst,
        int dstIndex,
        int dstLimit)
    {
        return drainInto(dst, dstIndex, dstLimit, 0);
    }

    private ModelPipelineResult drainInto(
        MutableDirectBufferEx dst,
        int dstIndex,
        int dstLimit,
        int consumed)
    {
        int dstAvailable = dstLimit - dstIndex;
        int produced = Math.min(pendingLength, dstAvailable);
        dst.putBytes(dstIndex, outputBuffer, pendingOffset, produced);
        pendingOffset += produced;
        pendingLength -= produced;

        ModelStatus status = pendingLength > 0 ? ModelStatus.OVERFLOW : ModelStatus.COMPLETE;
        return result.set(status, consumed, produced);
    }
}
