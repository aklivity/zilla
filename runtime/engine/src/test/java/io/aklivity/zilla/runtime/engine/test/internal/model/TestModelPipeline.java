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
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
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
// When the handler is configured with `discloseAuthorized`/`discloseRedacted`, a completed value is
// disclosed rather than copied through verbatim: the real bytes pass through when the pipeline's own
// `authorization` argument is in the configured set, otherwise the configured redacted bytes are
// substituted. `supplyCacheable` always constructs its pipeline with both `null`, so the cache-populate
// path never discloses; `supplyDecoder` passes the handler's real configured values, so only the
// per-consumer decode path can ever reveal or redact.
//
// When the handler is configured with `envelopeDisclose`/`discloseRedacted`, disclosure is instead gated
// on the supplied envelope: the real bytes pass through when `envelope.count(envelopeDisclose)` is
// non-zero (i.e. the envelope is backed by a real, non-`NONE` source that holds at least one value under
// that name), otherwise the redacted bytes are substituted. Like `discloseAuthorized`, `supplyCacheable`
// always constructs its pipeline with `null` here too.
final class TestModelPipeline implements ModelPipeline
{
    private static final int FLAGS_INIT = 0x02;
    private static final int FLAGS_FIN = 0x01;

    private final DirectBufferEx extractedValue = new UnsafeBufferEx("1234".getBytes(UTF_8));

    private final int length;
    private final int transformLength;
    private final List<String> fields;
    private final boolean lenient;
    private final ModelEnvelope envelope;
    private final ModelFieldBridge bridge;
    private final ModelPipelineResult result;
    private final TestModelHandler handler;
    private final List<Long> discloseAuthorized;
    private final DirectBufferEx discloseRedacted;
    private final String envelopeDiscloseName;

    private int processed;

    TestModelPipeline(
        int length,
        int transformLength,
        List<String> fields,
        boolean lenient,
        ModelEnvelope envelope,
        ModelTransform transform,
        TestModelHandler handler,
        List<Long> discloseAuthorized,
        DirectBufferEx discloseRedacted,
        String envelopeDiscloseName)
    {
        this.length = length;
        this.transformLength = transformLength;
        this.fields = fields;
        this.lenient = lenient;
        this.envelope = envelope;
        this.bridge = transform != ModelTransform.NONE ? new ModelFieldBridge(transform) : null;
        this.result = new ModelPipelineResult();
        this.handler = handler;
        this.discloseAuthorized = discloseAuthorized;
        this.discloseRedacted = discloseRedacted;
        this.envelopeDiscloseName = envelopeDiscloseName;
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
        else if (lengthValid && tail && envelopeDiscloseName != null)
        {
            // whole-value disclosure: reveal the real bytes only when the envelope supplied to this
            // pipeline holds at least one value under the configured name, otherwise substitute the
            // configured redacted bytes -- proves a real, headers-backed envelope reached this transform
            // rather than ModelEnvelope.NONE, which always reports a count of zero
            processed = total;
            final boolean disclosed = envelope.count(envelopeDiscloseName) > 0;
            final DirectBufferEx source = disclosed ? src : discloseRedacted;
            final int sourceIndex = disclosed ? srcIndex : 0;
            final int sourceLength = disclosed ? available : discloseRedacted.capacity();
            dst.putBytes(dstIndex, source, sourceIndex, sourceLength);
            consumed = available;
            produced = sourceLength;
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
        return transformLength < 0 && discloseAuthorized == null && envelopeDiscloseName == null;
    }

    @Override
    public void reset()
    {
        processed = 0;
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
