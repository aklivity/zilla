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
package io.aklivity.zilla.runtime.engine.test.internal.model;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.List;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.ExpandableArrayBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelFieldBridge;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;

// Per-stream transform mirroring the test model's whole-value length check: the value is accepted only when
// the total length across fragments equals the configured length. A length mismatch is treated as a
// constraint violation (well-formed but invalid): under strict validation it is REJECTED, under lenient
// validation the original bytes are forwarded verbatim and the value completes. By default an accepted value
// is copied through into dst unchanged (identity); when a transformed length is configured, the accepted
// value is padded or truncated to that length so a non-identity, length-changing transform can be exercised.
// When a real transform is wired, every configured top-level field surfaces a fixed token to it as its
// value when an accepted value completes; the same fields are written to the supplied envelope under their
// own paths, so a caller supplying a real envelope observes what the model surfaced without wiring a
// transform at all. State lives on the pipeline so interleaved streams stay isolated.
//
// When the handler is configured with an ordered `transformAuthorizations` list, each completed value --
// across every pipeline this handler ever supplies, not just this one -- consumes the next entry from that
// list as its expected authorization: message order, not pipeline-instance order, is what the list tracks.
// A completed value whose `authorization` argument doesn't match is rejected, giving callers a way to assert
// which authorization value actually reached a given encode/decode call without any extra observability
// machinery -- the mismatch surfaces as an ordinary REJECTED status, same as a length violation. Binding the
// check to pipeline-construction order instead would miss exactly the bug this exists to catch: a single
// pipeline instance shared across multiple producers only ever sees one expected value if it were fixed at
// construction, even though it processes several messages, each with its own authorization.
//
// When the handler is configured with a `reject` list instead, the length check above is bypassed entirely:
// the whole value is buffered and, at the tail fragment, matched verbatim against that list -- REJECTED on a
// match, otherwise identity-accepted. When `suspend` is also set, the match is resolved asynchronously via
// EngineContext#dispatch (mirroring an async model's CompletionCallback), returning SUSPENDED first and
// resolving -- and invoking the caller's resume callback -- once dispatched; without `suspend` the same
// decision resolves inline within the same transform() call.
//
// When the handler is configured with `discloseAuthorized`/`discloseRedacted`, a completed value is
// disclosed rather than copied through verbatim: the real bytes pass through when the pipeline's own
// `authorization` argument is in the configured set, otherwise the configured redacted bytes are
// substituted. The cache-populate path always constructs its pipeline with both `null`, so it never
// discloses; the per-consumer decode path passes the handler's real configured values, so only it can
// ever reveal or redact.
final class TestModelPipeline implements ModelPipeline
{
    private static final int FLAGS_INIT = 0x02;
    private static final int FLAGS_FIN = 0x01;
    private static final DirectBufferEx EMPTY_SRC = new UnsafeBufferEx(new byte[0]);

    private final DirectBufferEx extractedValue = new UnsafeBufferEx("1234".getBytes(UTF_8));

    private final int length;
    private final int transformLength;
    private final List<String> fields;
    private final boolean lenient;
    private final ModelEnvelope envelope;
    private final ModelFieldBridge bridge;
    private final ModelPipelineResult result;
    private final TestModelHandler handler;
    private final List<String> reject;
    private final boolean suspend;
    private final Runnable resumed;
    private final EngineContext context;
    private final ExpandableArrayBufferEx buffer;
    private final List<Long> discloseAuthorized;
    private final DirectBufferEx discloseRedacted;

    private int processed;
    private int contentLength;
    private int contentDrained;
    private boolean awaiting;
    private ModelStatus resolved;

    TestModelPipeline(
        int length,
        int transformLength,
        List<String> fields,
        boolean lenient,
        ModelEnvelope envelope,
        ModelTransform transform,
        TestModelHandler handler,
        List<String> reject,
        boolean suspend,
        Runnable resumed,
        EngineContext context,
        List<Long> discloseAuthorized,
        DirectBufferEx discloseRedacted)
    {
        this.length = length;
        this.transformLength = transformLength;
        this.fields = fields;
        this.lenient = lenient;
        this.envelope = envelope;
        this.bridge = transform != ModelTransform.NONE ? new ModelFieldBridge(transform) : null;
        this.result = new ModelPipelineResult();
        this.handler = handler;
        this.reject = reject;
        this.suspend = suspend;
        this.resumed = resumed;
        this.context = context;
        this.buffer = reject != null ? new ExpandableArrayBufferEx() : null;
        this.discloseAuthorized = discloseAuthorized;
        this.discloseRedacted = discloseRedacted;
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
        return reject != null
            ? transformReject(src, srcIndex, srcLimit, dst, dstIndex, dstLimit, flags)
            : transformLength(traceId, bindingId, authorization, flags, src, srcIndex, srcLimit, dst, dstIndex, dstLimit);
    }

    private ModelPipelineResult transformReject(
        DirectBufferEx src,
        int srcIndex,
        int srcLimit,
        MutableDirectBufferEx dst,
        int dstIndex,
        int dstLimit,
        int flags)
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
            int remaining = contentLength - contentDrained;
            int available = Math.min(remaining, dstLimit - dstIndex);
            dst.putBytes(dstIndex, buffer, contentDrained, available);
            contentDrained += available;
            produced = available;
            status = contentDrained < contentLength ? ModelStatus.OVERFLOW : ModelStatus.COMPLETE;
        }
        else if (awaiting)
        {
            status = ModelStatus.SUSPENDED;
        }
        else
        {
            int available = srcLimit - srcIndex;
            buffer.putBytes(contentLength, src, srcIndex, available);
            contentLength += available;
            consumed = available;

            if ((flags & FLAGS_FIN) != 0)
            {
                String text = buffer.getStringWithoutLengthUtf8(0, contentLength);
                boolean matched = reject.contains(text);

                if (suspend)
                {
                    awaiting = true;
                    context.dispatch(() ->
                    {
                        resolve(matched);
                        resumed.run();
                    });
                    status = ModelStatus.SUSPENDED;
                }
                else
                {
                    resolve(matched);
                    ModelPipelineResult inner = transformReject(EMPTY_SRC, 0, 0, dst, dstIndex, dstLimit, 0x00);
                    status = inner.status();
                    produced = inner.produced();
                }
            }
            else
            {
                status = ModelStatus.UNDERFLOW;
            }
        }

        return result.set(status, consumed, produced);
    }

    private void resolve(
        boolean matched)
    {
        resolved = matched ? ModelStatus.REJECTED : ModelStatus.OK;
        awaiting = false;
    }

    private ModelPipelineResult transformLength(
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
        else if (lengthValid && tail && discloseAuthorized != null)
        {
            // whole-value disclosure: reveal the real bytes only when this pipeline's own authorization is
            // in the configured set, otherwise substitute the configured redacted bytes
            processed = total;
            final boolean authorized = discloseAuthorized.contains(authorization);
            final DirectBufferEx disclosed = authorized ? src : discloseRedacted;
            final int disclosedIndex = authorized ? srcIndex : 0;
            final int disclosedLength = authorized ? available : discloseRedacted.capacity();
            dst.putBytes(dstIndex, disclosed, disclosedIndex, disclosedLength);
            consumed = available;
            produced = disclosedLength;
            status = ModelStatus.COMPLETE;
            visitExtracted(authorization);
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
            visitExtracted(authorization);
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
                    visitExtracted(authorization);
                }
            }
            else
            {
                status = ModelStatus.UNDERFLOW;
            }
        }

        if (status == ModelStatus.COMPLETE)
        {
            final Long expectedAuthorization = handler.nextTransformAuthorization();
            if (expectedAuthorization != null && authorization != expectedAuthorization)
            {
                status = ModelStatus.REJECTED;
            }
        }

        return result.set(status, consumed, produced);
    }

    @Override
    public boolean identity()
    {
        return transformLength < 0 && discloseAuthorized == null;
    }

    @Override
    public void reset()
    {
        processed = 0;
        contentLength = 0;
        contentDrained = 0;
        awaiting = false;
        resolved = null;
    }

    private void visitExtracted(
        long authorization)
    {
        if (bridge != null)
        {
            bridge.start(authorization);
        }

        for (int i = 0; i < fields.size(); i++)
        {
            final String path = "$." + fields.get(i);

            if (bridge != null)
            {
                bridge.field(path, extractedValue, 0, extractedValue.capacity());
            }

            envelope.set(path, extractedValue);
        }

        if (bridge != null)
        {
            bridge.end();
        }
    }
}
