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

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;

// Per-stream identity transform for a core model: validate the bytes about to be forwarded, copy them
// into the caller's destination unchanged, and report progress. The per-stream validation state lives
// on the supplied CoreModelValidator, so interleaved streams on one worker never corrupt each other.
final class CoreModelPipeline implements ModelPipeline
{
    private final CoreModelHandler handler;
    private final CoreModelValidator validator;
    private final ModelPipelineResult result;

    CoreModelPipeline(
        CoreModelHandler handler,
        CoreModelValidator validator)
    {
        this.handler = handler;
        this.validator = validator;
        this.result = new ModelPipelineResult();
    }

    @Override
    public ModelPipelineResult transform(
        long traceId,
        long bindingId,
        int flags,
        DirectBuffer src,
        int srcIndex,
        int srcLength,
        MutableDirectBuffer dst,
        int dstIndex,
        int dstLength)
    {
        int available = Math.min(srcLength, dstLength);
        // only the tail of the final fragment closes the value; a bounded dst defers FIN to a later call
        boolean tail = (flags & FLAGS_FIN) != 0 && available == srcLength;
        int fragmentFlags = (flags & FLAGS_INIT) | (tail ? FLAGS_FIN : 0);

        ModelStatus status;
        int consumed;
        int produced;
        if (!validator.validate(fragmentFlags, src, srcIndex, available))
        {
            handler.validationFailure(traceId, bindingId);
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
    public void reset()
    {
    }
}
