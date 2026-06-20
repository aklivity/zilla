/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.test.internal.model;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;

// Per-stream identity transform mirroring the test model's whole-value length check: the value bytes are
// copied through into dst unchanged and accepted only when the total length across fragments equals the
// configured length. State lives on the pipeline so interleaved streams stay isolated.
final class TestModelPipeline implements ModelPipeline
{
    private final int length;
    private final ModelPipelineResult result;

    private int processed;

    TestModelPipeline(
        int length)
    {
        this.length = length;
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
        if ((flags & FLAGS_INIT) != 0)
        {
            processed = 0;
        }

        int available = Math.min(srcLength, dstLength);
        boolean tail = (flags & FLAGS_FIN) != 0 && available == srcLength;
        int total = processed + available;
        boolean valid = tail ? total == length : total <= length;

        ModelStatus status;
        int consumed;
        int produced;
        if (!valid)
        {
            status = ModelStatus.REJECTED;
            consumed = 0;
            produced = 0;
        }
        else
        {
            processed = total;
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
        processed = 0;
    }
}
