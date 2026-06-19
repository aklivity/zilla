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
 * A per-stream, resumable transform session.
 * <p>
 * A {@code ModelPipeline} is created per stream by {@link ModelHandler#supplyPipeline} and confined
 * to a single I/O thread. It holds all in-flight state for the value being transformed (parser and
 * generator context, unconsumed input carried across fragments, output position), so concurrent
 * streams on the same thread do not interfere.
 * </p>
 * <p>
 * The caller drives it with a loop modelled on {@code SSLEngine}: each {@link #transform} call
 * consumes from {@code src} and produces into {@code dst}, reporting progress in a reused
 * {@link ModelStatus}. On {@link ModelStatus.Kind#OVERFLOW} the caller drains {@code dst} and calls
 * again; on {@link ModelStatus.Kind#UNDERFLOW} it supplies the next input fragment; on
 * {@link ModelStatus.Kind#COMPLETE} the value is done and any extracted fields have been visited; on
 * {@link ModelStatus.Kind#REJECTED} the stream should be reset. A validate-only caller passes a
 * zero-length {@code dst} and forwards the original {@code src} bytes on success.
 * </p>
 *
 * @see ModelHandler
 * @see ModelStatus
 */
public interface ModelPipeline
{
    /**
     * Flag value indicating the first fragment of a value.
     */
    int FLAGS_INIT = 0x02;

    /**
     * Flag value indicating the final fragment of a value.
     */
    int FLAGS_FIN = 0x01;

    /**
     * Flag value indicating a complete, unfragmented value (both INIT and FIN bits set).
     */
    int FLAGS_COMPLETE = 0x03;

    /**
     * Transforms input from {@code src[srcIndex..srcIndex+srcLength)} into
     * {@code dst[dstIndex..dstIndex+dstLength)} and reports progress.
     *
     * @param traceId    the trace identifier for diagnostics
     * @param bindingId  the binding identifier
     * @param flags      the fragment flags ({@link #FLAGS_INIT}, {@link #FLAGS_FIN})
     * @param src        the source buffer
     * @param srcIndex   the offset of the input in {@code src}
     * @param srcLength  the length of the input
     * @param dst        the destination buffer
     * @param dstIndex   the offset to write output at in {@code dst}
     * @param dstLength  the number of output bytes available in {@code dst}
     * @return the reused {@link ModelStatus} describing the outcome, consumed, and produced bytes
     */
    ModelStatus transform(
        long traceId,
        long bindingId,
        int flags,
        DirectBuffer src,
        int srcIndex,
        int srcLength,
        MutableDirectBuffer dst,
        int dstIndex,
        int dstLength);

    /**
     * Resets this pipeline so it is ready to transform the next value, discarding any in-flight state.
     */
    void reset();
}
