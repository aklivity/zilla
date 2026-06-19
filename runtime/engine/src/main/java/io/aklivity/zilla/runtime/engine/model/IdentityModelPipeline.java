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
package io.aklivity.zilla.runtime.engine.model;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * Pass-through pipeline that copies input to output unchanged. Backs {@link ModelHandler#NONE} and is
 * safe to use as a no-op; callers that only need the bytes forwarded should bypass the transform loop
 * and forward the original buffer directly.
 */
final class IdentityModelPipeline implements ModelPipeline
{
    private final ModelPipelineResult result = new ModelPipelineResult();

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
        ModelStatus status;
        int consumed;
        int produced;
        if (dstLength == 0)
        {
            consumed = srcLength;
            produced = 0;
            status = (flags & FLAGS_FIN) != 0 ? ModelStatus.COMPLETE : ModelStatus.OK;
        }
        else
        {
            int count = Math.min(srcLength, dstLength);
            dst.putBytes(dstIndex, src, srcIndex, count);
            consumed = count;
            produced = count;
            status = count < srcLength
                ? ModelStatus.OVERFLOW
                : (flags & FLAGS_FIN) != 0 ? ModelStatus.COMPLETE : ModelStatus.OK;
        }
        return result.set(status, consumed, produced);
    }

    @Override
    public void reset()
    {
    }
}
