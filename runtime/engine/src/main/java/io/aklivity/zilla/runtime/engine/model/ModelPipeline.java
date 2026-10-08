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

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;

/**
 * A per-stream, resumable transform session.
 * <p>
 * A {@code ModelPipeline} is created per stream by {@link ModelHandler#supplyDecoder} and confined
 * to a single I/O thread. It holds all in-flight state for the value being transformed (parser and
 * generator context, unconsumed input carried across fragments, output position), so concurrent
 * streams on the same thread do not interfere.
 * </p>
 * <p>
 * The caller drives it with a loop modelled on {@code SSLEngine}: each {@link #transform} call
 * consumes from {@code src} and produces into {@code dst}, reporting progress in a reused
 * {@link ModelPipelineResult}. On {@link ModelStatus#OVERFLOW} the caller drains {@code dst} and calls
 * again; on {@link ModelStatus#UNDERFLOW} it supplies the next input fragment; on
 * {@link ModelStatus#COMPLETE} the value is done and any extracted fields have been visited; on
 * {@link ModelStatus#REJECTED} the stream should be reset.
 * </p>
 *
 * @see ModelHandler
 * @see ModelPipelineResult
 * @see ModelStatus
 */
public interface ModelPipeline
{
    /**
     * Transforms input from {@code src[srcIndex..srcLimit)} into {@code dst[dstIndex..dstLimit)} and
     * reports progress.
     *
     * @param traceId       the trace identifier for diagnostics
     * @param bindingId     the binding identifier
     * @param authorization the authorization in effect for the message being transformed
     * @param flags         the per-fragment stream flags
     * @param src           the source buffer
     * @param srcIndex      the offset of the input in {@code src}
     * @param srcLimit      the offset just past the input in {@code src}
     * @param dst           the destination buffer
     * @param dstIndex      the offset to write output at in {@code dst}
     * @param dstLimit      the offset just past the available output in {@code dst}
     * @return the reused {@link ModelPipelineResult} describing the outcome, consumed, and produced bytes
     */
    ModelPipelineResult transform(
        long traceId,
        long bindingId,
        long authorization,
        int flags,
        DirectBufferEx src,
        int srcIndex,
        int srcLimit,
        MutableDirectBufferEx dst,
        int dstIndex,
        int dstLimit);

    /**
     * Indicates whether this pipeline leaves every accepted value byte-for-byte unchanged.
     * <p>
     * A pipeline that only validates — accepting or rejecting a value without rewriting its bytes or
     * changing its length — returns {@code true}. This lets a caller that forwards a length-prefixed or
     * checksum-protected wire format stream the original bytes through untouched, rather than buffering
     * the whole value to recompute its framing and checksum. A pipeline that may rewrite bytes or resize
     * the value returns {@code false}.
     * </p>
     * <p>
     * The answer is a property of the supplied pipeline, available as soon as it is supplied, before any
     * value has been transformed, and stable for its lifetime. It is conservative: it reflects the installed
     * stages and the intrinsic behavior of the pipeline's parser and generator, never a per-value selection
     * made while transforming, and is {@code false} whenever any installed stage could change bytes for any
     * schema, since {@code false} only ever means the bytes may change.
     * </p>
     *
     * @return {@code true} if accepted values pass through unchanged; {@code false} otherwise
     */
    boolean identity();

    /**
     * Indicates whether this pipeline is a pure function of its input, configuration and authorization.
     * <p>
     * A deterministic pipeline always produces the same output for the same input, configuration and
     * authorization. A pipeline whose output may also depend on randomness, time or external state
     * returns {@code false}. A caller that re-drives the same input, or composes pipelines, can rely on
     * observing the same output only when every stage is deterministic.
     * </p>
     * <p>
     * Like {@link #identity()}, the answer is a property of the supplied pipeline, available before any
     * data is transformed and conservative.
     * </p>
     *
     * @return {@code true} if the output is repeatable for the same input, configuration and authorization;
     *         {@code false} otherwise
     */
    boolean deterministic();

    /**
     * Returns a pipeline that applies this pipeline and then {@code next} to the same value.
     * <p>
     * The result owns the driving loop between the two stages: the unconsumed tail of the input,
     * the {@code INIT} flag (delivered to each stage exactly once), the {@code FIN} flag (delivered to
     * {@code next} only after this pipeline reports {@link ModelStatus#COMPLETE}), draining on
     * {@link ModelStatus#OVERFLOW} and supplying input on {@link ModelStatus#UNDERFLOW}. A
     * {@link ModelStatus#REJECTED} from either stage rejects the chain, and {@link #reset()} resets both.
     * A stage that reports {@link ModelStatus#SUSPENDED} is resumed through the callback it was supplied
     * with; the caller then re-drives the chain with an empty source and the chain re-enters the
     * suspended stage.
     * </p>
     * <p>
     * A stage that reports {@link #identity()} is a verdict: it observes the bytes presented to it and
     * accepts or rejects them, and its output is not forwarded. The chain passes the original bytes on, and
     * only a stage that is not identity feeds transformed bytes to the next stage, through a single
     * intermediate buffer allocated when the chain is built. The chain preserves the order it is given;
     * which stage rejects first, and which side effects run on rejection, are not specified.
     * </p>
     * <p>
     * {@link #identity()} and {@link #deterministic()} of the result combine with logical AND, and
     * {@link #padding} sums. An implementation may override this method to compose more efficiently with a
     * pipeline of the same kind, provided the observable behavior is identical.
     * </p>
     *
     * @param next  the pipeline to apply to the output of this pipeline
     * @return a pipeline that applies this pipeline and then {@code next}
     */
    default ModelPipeline andThen(
        ModelPipeline next)
    {
        return new ModelPipelineChain(this, next);
    }

    /**
     * Returns the number of additional bytes required in the output buffer to accommodate any framing
     * overhead this pipeline's transform may add (e.g., schema id prefix bytes) for the given input.
     *
     * @param data   the source buffer containing the untransformed input
     * @param index  the offset of the input
     * @param length the length of the input
     * @return the padding byte count (0 for transforms that do not expand the input)
     */
    default int padding(
        DirectBufferEx data,
        int index,
        int length)
    {
        return 0;
    }

    /**
     * Resets this pipeline so it is ready to transform the next value, discarding any in-flight state.
     */
    void reset();
}
