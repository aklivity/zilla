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
     * @param traceId    the trace identifier for diagnostics
     * @param bindingId  the binding identifier
     * @param flags      the per-fragment stream flags
     * @param src        the source buffer
     * @param srcIndex   the offset of the input in {@code src}
     * @param srcLimit   the offset just past the input in {@code src}
     * @param dst        the destination buffer
     * @param dstIndex   the offset to write output at in {@code dst}
     * @param dstLimit   the offset just past the available output in {@code dst}
     * @return the reused {@link ModelPipelineResult} describing the outcome, consumed, and produced bytes
     */
    ModelPipelineResult transform(
        long traceId,
        long bindingId,
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
     *
     * @return {@code true} if accepted values pass through unchanged; {@code false} otherwise
     */
    boolean identity();

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
        DirectBuffer data,
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
