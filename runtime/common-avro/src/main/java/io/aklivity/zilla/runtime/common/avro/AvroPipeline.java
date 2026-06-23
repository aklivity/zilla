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
package io.aklivity.zilla.runtime.common.avro;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * A runnable, resumable {@code common-avro} pipeline assembled from an {@link AvroStream} description
 * terminated with an {@link AvroSink}. Reuse a single instance per thread: call {@link #reset()}
 * once per top-level datum, then {@link #transform(DirectBuffer, int, int)} the datum, which may arrive
 * whole or as successive input windows.
 * <p>
 * Back-pressure has two independent axes, each with its own non-terminal "call {@code transform} again"
 * status:
 * <ul>
 * <li><b>Input</b> — {@link Status#STARVED}: the input window was consumed but the datum is not yet
 * complete. The caller keeps the unconsumed tail ({@link #remaining()} bytes) and re-presents it,
 * contiguous with newly arrived bytes, passing {@code last == true} only on the final window. The
 * pipeline holds no input buffer of its own.</li>
 * <li><b>Output</b> — {@link Status#SUSPENDED}: the bounded generator filled mid-datum. The caller
 * drains the output, resets the generator, and calls {@code transform} again with the <em>same</em> input
 * window (kept stable) to resume the in-flight datum from where it paused.</li>
 * </ul>
 * Zero per-message allocation; abort on failure.
 */
public interface AvroPipeline
{
    enum Status
    {
        /** the event was consumed; the pump advances to the next — an internal sink-to-pump signal */
        ADVANCED,
        /** the bounded output filled: drain the buffer, reset the generator, then {@link #transform} again to resume */
        SUSPENDED,
        /** the input window was consumed mid-datum: {@link #transform} the next window (input back-pressure) */
        STARVED,
        /** the current top-level datum finished and was accepted */
        COMPLETED,
        /** the current top-level datum was rejected; the output must be abandoned */
        REJECTED
    }

    void reset();

    /**
     * Whether this pipeline reproduces its input bytes for every accepted datum — the composition of its
     * parser, transform stages, and terminal generator all being identity. The default is {@code false}.
     */
    default boolean identity()
    {
        return false;
    }

    /**
     * Feeds the whole datum, or the final window of a streamed one ({@code last == true}).
     */
    default Status transform(
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        return transform(buffer, offset, limit, true);
    }

    /**
     * Feeds one input window of a datum; {@code last} marks the final window. Returns
     * {@link Status#STARVED} when the window is consumed before the datum completes (input back-pressure,
     * only when {@code last == false}), {@link Status#SUSPENDED} when the bounded output fills (output
     * back-pressure), {@link Status#COMPLETED} on a clean datum end, or {@link Status#REJECTED} on
     * malformed or — under {@code last == true} — truncated input.
     */
    Status transform(
        DirectBuffer buffer,
        int offset,
        int limit,
        boolean last);

    /**
     * The number of bytes at the tail of the most recently fed window not yet consumed — exactly what the
     * caller retains and re-presents, contiguous, at the front of the next {@link #transform}. A caller buffering
     * across windows keeps this many bytes without tracking the window's absolute base. On {@link Status#STARVED}
     * it is the partial trailing unit left unconsumed; held steady while {@link Status#SUSPENDED}, since output
     * back-pressure consumes no further input.
     */
    int remaining();

    /**
     * Transforms one input window {@code src[offset, limit)} into {@code dst[dstOffset, dstLimit)},
     * re-targeting the terminal generator at {@code dst} before pumping (an {@code SSLEngine.unwrap}-style
     * src/dst shape), and returns the reused {@link AvroPipelineResult} carrying the {@link Status} with the
     * bytes {@link AvroPipelineResult#consumed() consumed} from the input window and
     * {@link AvroPipelineResult#produced() produced} into the destination this call. Drive it in a loop:
     * drain the produced output, advance the input by {@code consumed}, and call again — on
     * {@link Status#SUSPENDED} with the same window (output back-pressure), on {@link Status#STARVED} with
     * the unconsumed tail prepended to the next window (input back-pressure). Available only when the
     * pipeline was terminated with {@link AvroStream#into(AvroGenerator)} so the pipeline owns the
     * generator it re-targets.
     */
    AvroPipelineResult transform(
        DirectBuffer src,
        int offset,
        int limit,
        boolean last,
        MutableDirectBuffer dst,
        int dstOffset,
        int dstLimit);
}
