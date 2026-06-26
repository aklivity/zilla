/*
 * Copyright 2021-2024 Aklivity Inc
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
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;

// Per-stream identity transform for a core model: validate the bytes about to be forwarded, copy them
// into the caller's destination unchanged, and report progress. The per-stream validation state lives
// on the supplied CoreModelValidator, so interleaved streams on one worker never corrupt each other.
final class CoreModelPipeline implements ModelPipeline
{
    private static final int FLAGS_INIT = 0x02;
    private static final int FLAGS_FIN = 0x01;

    private final CoreModelHandler handler;
    private final CoreModelValidator validator;
    // LENIENT: a structurally-valid value that violates a semantic constraint (INVALID) is reported then
    // copied through unchanged rather than rejected; a parse failure (MALFORMED) always rejects
    private final boolean lenient;
    private final ModelPipelineResult result;

    CoreModelPipeline(
        CoreModelHandler handler,
        CoreModelValidator validator,
        boolean lenient)
    {
        this.handler = handler;
        this.validator = validator;
        this.lenient = lenient;
        this.result = new ModelPipelineResult();
    }

    @Override
    public ModelPipelineResult transform(
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
        int srcLength = srcLimit - srcIndex;
        int dstLength = dstLimit - dstIndex;
        int available = Math.min(srcLength, dstLength);
        // only the tail of the final fragment closes the value; a bounded dst defers FIN to a later call
        boolean tail = (flags & FLAGS_FIN) != 0 && available == srcLength;
        int fragmentFlags = (flags & FLAGS_INIT) | (tail ? FLAGS_FIN : 0);

        Validity validity = validator.validate(fragmentFlags, src, srcIndex, available);
        // MALFORMED always rejects; INVALID rejects under STRICT but, under LENIENT, reports then passes the
        // structurally-valid value through (the identity copy below); VALID passes through silently
        boolean reject = validity == Validity.MALFORMED || validity == Validity.INVALID && !lenient;
        if (validity != Validity.VALID)
        {
            handler.validationFailure(traceId, bindingId);
        }

        ModelStatus status;
        int consumed;
        int produced;
        if (reject)
        {
            status = ModelStatus.REJECTED;
            consumed = 0;
            produced = 0;
        }
        else
        {
            dst.putBytes(dstIndex, src, srcIndex, available);
            consumed = available;
            produced = available;
            if (available < srcLength)
            {
                status = ModelStatus.OVERFLOW;
            }
            else if (tail)
            {
                status = ModelStatus.COMPLETE;
            }
            else
            {
                status = ModelStatus.UNDERFLOW;
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
    }
}
