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
package io.aklivity.zilla.runtime.binding.kafka.internal.cache;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.ExpandableArrayBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.engine.model.ModelCache;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;

public final class KafkaCacheModel
{
    public static final KafkaCacheModel NONE = new KafkaCacheModel();

    private static final int FLAGS_INIT = 0x02;
    private static final int FLAGS_FIN = 0x01;

    @FunctionalInterface
    public interface Output
    {
        void accept(
            DirectBufferEx buffer,
            int index,
            int length);
    }

    // the outcome of a single fragment-wise transform() call; COMPLETE and REJECTED both reset the
    // pipeline and are terminal for the value, UNDERFLOW means the pipeline is still open and this
    // fragment's unconsumed tail (if any) has already been retained internally for the next call
    public static final class Result
    {
        private ModelStatus status;
        private int consumed;
        private int produced;

        public ModelStatus status()
        {
            return status;
        }

        public int consumed()
        {
            return consumed;
        }

        public int produced()
        {
            return produced;
        }

        private Result set(
            ModelStatus status,
            int consumed,
            int produced)
        {
            this.status = status;
            this.consumed = consumed;
            this.produced = produced;
            return this;
        }
    }

    private final ModelPipeline pipeline;
    private final MutableDirectBufferEx scratch;
    private final ExpandableArrayBufferEx carry;
    private final Result result;

    private boolean started;
    private int carried;

    public static KafkaCacheModel decoder(
        ModelHandler handler,
        ModelTransform transform,
        MutableDirectBufferEx scratch)
    {
        return handler != null
            ? new KafkaCacheModel(handler.supplyDecoder(ModelEnvelope.NONE, transform, ModelCache.NONE), scratch)
            : NONE;
    }

    // populate time: decodes wire bytes ahead of any specific consumer's request, producing the value the
    // local cache persists; envelope collects whatever metadata a composed transform writes -- e.g. the
    // key model's own envelope recognizing the reserved ":key" pseudo-name to override the persisted key
    public static KafkaCacheModel writer(
        ModelHandler handler,
        ModelTransform transform,
        ModelEnvelope envelope,
        MutableDirectBufferEx scratch)
    {
        return handler != null
            ? new KafkaCacheModel(handler.supplyDecoder(envelope, transform, ModelCache.WRITE), scratch)
            : NONE;
    }

    // per-consumer fetch time: resolves a value already in the form a writer pipeline produced, for the
    // consumer requesting it now; envelope is backed by the cached record's real headers so a composed
    // transform can read metadata that was present on produce
    public static KafkaCacheModel reader(
        ModelHandler handler,
        ModelTransform transform,
        ModelEnvelope envelope,
        MutableDirectBufferEx scratch)
    {
        return handler != null
            ? new KafkaCacheModel(handler.supplyDecoder(envelope, transform, ModelCache.READ), scratch)
            : NONE;
    }

    // producer encode time; envelope collects whatever metadata a composed transform writes, for the
    // storage write path to materialize as real headers once encode completes
    public static KafkaCacheModel encoder(
        ModelHandler handler,
        ModelEnvelope envelope,
        MutableDirectBufferEx scratch)
    {
        return handler != null
            ? new KafkaCacheModel(handler.supplyEncoder(envelope, ModelTransform.NONE), scratch)
            : NONE;
    }

    private KafkaCacheModel()
    {
        this.pipeline = null;
        this.scratch = null;
        this.carry = new ExpandableArrayBufferEx();
        this.result = new Result();
    }

    KafkaCacheModel(
        ModelPipeline pipeline,
        MutableDirectBufferEx scratch)
    {
        this.pipeline = pipeline;
        this.scratch = scratch;
        this.carry = new ExpandableArrayBufferEx();
        this.result = new Result();
    }

    // whole-value convenience: presents data[index..limit) as the complete value in one call.
    // NONE's pipeline-less branch is handled directly here rather than via the fragment-wise overload
    // below, since NONE is a single shared instance called unconditionally (and concurrently, across
    // worker threads) by every key transform whether or not a key model is configured -- routing it
    // through the fragment-wise method's reused Result field would make that field genuinely shared
    // mutable state across threads
    public int transform(
        long traceId,
        long bindingId,
        long authorization,
        DirectBufferEx data,
        int index,
        int limit,
        Output next)
    {
        final int total;
        if (pipeline == null)
        {
            final int length = limit - index;
            next.accept(data, index, length);
            total = length;
        }
        else
        {
            final Result whole = transform(traceId, bindingId, authorization, FLAGS_INIT | FLAGS_FIN,
                data, index, limit, next);
            total = whole.status() == ModelStatus.REJECTED ? -1 : whole.produced();
        }
        return total;
    }

    // fragment-wise: flags are the caller's own DATA-frame FLAGS_INIT / FLAGS_FIN, so a value may be
    // presented across any number of calls; every byte of data[index..limit) is absorbed by this call --
    // a caller never sees or manages a carried-over tail, even when the pipeline underflows mid-value.
    // Every caller of this overload must gate on != NONE first, as the whole-value overload above does
    // for its own pipeline-less branch -- this method's Result is reused per instance, and NONE is a
    // single instance shared across worker threads
    public Result transform(
        long traceId,
        long bindingId,
        long authorization,
        int flags,
        DirectBufferEx data,
        int index,
        int limit,
        Output next)
    {
        final int inputLength = limit - index;
        final Result outcome;
        if (pipeline == null)
        {
            next.accept(data, index, inputLength);
            outcome = result.set((flags & FLAGS_FIN) != 0 ? ModelStatus.COMPLETE : ModelStatus.UNDERFLOW,
                inputLength, inputLength);
        }
        else
        {
            DirectBufferEx src = data;
            int srcAt = index;
            int srcLimit = limit;
            if (carried > 0)
            {
                carry.putBytes(carried, data, index, inputLength);
                src = carry;
                srcAt = 0;
                srcLimit = carried + inputLength;
                carried = 0;
            }

            int total = 0;
            int callFlags = (started ? 0 : FLAGS_INIT) | (flags & FLAGS_FIN);
            started = true;
            ModelStatus finalStatus = null;
            while (finalStatus == null)
            {
                final ModelPipelineResult step = pipeline.transform(traceId, bindingId, authorization, callFlags,
                    src, srcAt, srcLimit, scratch, 0, scratch.capacity());
                final ModelStatus status = step.status();
                final int consumed = step.consumed();
                final int produced = step.produced();

                if (produced > 0)
                {
                    next.accept(scratch, 0, produced);
                    total += produced;
                }

                if (status == ModelStatus.REJECTED)
                {
                    pipeline.reset();
                    started = false;
                    finalStatus = ModelStatus.REJECTED;
                }
                else if (status == ModelStatus.COMPLETE)
                {
                    pipeline.reset();
                    started = false;
                    finalStatus = ModelStatus.COMPLETE;
                }
                else if (status == ModelStatus.UNDERFLOW && (callFlags & FLAGS_FIN) != 0)
                {
                    // contract violation: a FIN call must resolve to COMPLETE or REJECTED, never
                    // UNDERFLOW -- every shipped pipeline maps an incomplete value under FIN to
                    // REJECTED, so this defends against a non-compliant pipeline rather than a real path
                    pipeline.reset();
                    started = false;
                    finalStatus = ModelStatus.REJECTED;
                }
                else if (status == ModelStatus.UNDERFLOW || consumed == 0 && produced == 0)
                {
                    final int tailAt = srcAt + consumed;
                    final int tailLength = srcLimit - tailAt;
                    if (tailLength > 0)
                    {
                        carry.putBytes(0, src, tailAt, tailLength);
                    }
                    carried = tailLength;
                    finalStatus = ModelStatus.UNDERFLOW;
                }
                else
                {
                    srcAt += consumed;
                    callFlags &= ~FLAGS_INIT;
                }
            }

            outcome = result.set(finalStatus, inputLength, total);
        }
        return outcome;
    }

    public int padding(
        DirectBufferEx data,
        int index,
        int length)
    {
        return pipeline != null ? pipeline.padding(data, index, length) : 0;
    }

    public void reset()
    {
        if (pipeline != null)
        {
            pipeline.reset();
        }
        started = false;
        carried = 0;
    }

    public boolean identity()
    {
        return pipeline == null || pipeline.identity();
    }
}
