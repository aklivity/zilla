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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.List;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelVisitor;

// Per-stream transform mirroring the test model's whole-value length check: the value is accepted only when
// the total length across fragments equals the configured length. A length mismatch is treated as a
// constraint violation (well-formed but invalid): under strict validation it is REJECTED, under lenient
// validation the original bytes are forwarded verbatim and the value completes. By default an accepted value
// is copied through into dst unchanged (identity); when a transformed length is configured, the accepted
// value is padded or truncated to that length so a non-identity, length-changing transform can be exercised.
// When a real visitor is wired, every configured top-level field surfaces a fixed token to the visitor as its
// value when an accepted value completes. State lives on the pipeline so interleaved streams stay isolated.
final class TestModelPipeline implements ModelPipeline
{
    private static final int FLAGS_INIT = 0x02;
    private static final int FLAGS_FIN = 0x01;

    private final DirectBufferEx extractedValue = new UnsafeBufferEx("1234".getBytes(UTF_8));

    private final int length;
    private final int transformLength;
    private final List<String> fields;
    private final boolean lenient;
    private final ModelVisitor visitor;
    private final ModelPipelineResult result;

    private int processed;

    TestModelPipeline(
        int length,
        int transformLength,
        List<String> fields,
        boolean lenient,
        ModelVisitor visitor)
    {
        this.length = length;
        this.transformLength = transformLength;
        this.fields = fields;
        this.lenient = lenient;
        this.visitor = visitor;
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
        if ((flags & FLAGS_INIT) != 0)
        {
            processed = 0;
        }

        int srcLength = srcLimit - srcIndex;
        int dstLength = dstLimit - dstIndex;
        int available = Math.min(srcLength, dstLength);
        boolean tail = (flags & FLAGS_FIN) != 0 && available == srcLength;
        int total = processed + available;
        boolean lengthValid = tail ? total == length : total <= length;

        ModelStatus status;
        int consumed;
        int produced;
        if (!lengthValid && !lenient)
        {
            status = ModelStatus.REJECTED;
            consumed = 0;
            produced = 0;
        }
        else if (lengthValid && tail && transformLength >= 0)
        {
            // whole-value transform: truncate, or grow by stamping the original value repeatedly,
            // clipped to the configured transformed length
            processed = total;
            final int copy = Math.min(available, transformLength);
            dst.putBytes(dstIndex, src, srcIndex, copy);
            for (int index = copy; index < transformLength; index++)
            {
                final byte stamp = available > 0 ? src.getByte(srcIndex + index % available) : (byte) 0;
                dst.putByte(dstIndex + index, stamp);
            }
            consumed = available;
            produced = transformLength;
            status = ModelStatus.COMPLETE;
            visitExtracted();
        }
        else
        {
            // identity copy of an accepted value, or verbatim forward of a constraint-invalid value under lenient
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
                if (lengthValid)
                {
                    visitExtracted();
                }
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
        return transformLength < 0;
    }

    @Override
    public void reset()
    {
        processed = 0;
    }

    private void visitExtracted()
    {
        if (visitor != ModelVisitor.NONE)
        {
            for (int i = 0; i < fields.size(); i++)
            {
                visitor.onField("$." + fields.get(i), extractedValue, 0, extractedValue.capacity());
            }
        }
    }
}
