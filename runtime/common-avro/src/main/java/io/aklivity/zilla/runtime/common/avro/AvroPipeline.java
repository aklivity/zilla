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

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

/**
 * A runnable, resumable {@code common-avro} pipeline assembled from an {@link AvroStream} description
 * terminated with an {@link AvroSink}. Reuse a single instance per thread: call {@link #reset()}
 * once per top-level datum, then {@link #feed(DirectBuffer, int, int)} the datum, which may arrive
 * whole or as successive input windows.
 * <p>
 * Back-pressure has two independent axes, each with its own non-terminal "call {@code feed} again"
 * status:
 * <ul>
 * <li><b>Input</b> — {@link Status#STARVED}: the input window was consumed but the datum is not yet
 * complete. The caller keeps the unconsumed tail ({@link #remaining()} bytes) and re-presents it,
 * contiguous with newly arrived bytes, passing {@code last == true} only on the final window. The
 * pipeline holds no input buffer of its own.</li>
 * <li><b>Output</b> — {@link Status#SUSPENDED}: the bounded generator filled mid-datum. The caller
 * drains the output, resets the generator, and calls {@code feed} again with the <em>same</em> input
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
        /** the bounded output filled: drain the buffer, reset the generator, then {@link #feed} again to resume */
        SUSPENDED,
        /** the input window was consumed mid-datum: {@link #feed} the next window (input back-pressure) */
        STARVED,
        /** the current top-level datum finished and was accepted */
        COMPLETED,
        /** the current top-level datum was rejected; the output must be abandoned */
        REJECTED
    }

    void reset();

    /**
     * Feeds the whole datum, or the final window of a streamed one ({@code last == true}).
     */
    default Status feed(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        return feed(buffer, offset, limit, true);
    }

    /**
     * Feeds one input window of a datum; {@code last} marks the final window. Returns
     * {@link Status#STARVED} when the window is consumed before the datum completes (input back-pressure,
     * only when {@code last == false}), {@link Status#SUSPENDED} when the bounded output fills (output
     * back-pressure), {@link Status#COMPLETED} on a clean datum end, or {@link Status#REJECTED} on
     * malformed or — under {@code last == true} — truncated input.
     */
    Status feed(
        DirectBufferEx buffer,
        int offset,
        int limit,
        boolean last);

    /**
     * The number of bytes at the tail of the most recently fed window not yet consumed — exactly what the
     * caller retains and re-presents, contiguous, at the front of the next {@link #feed}. A caller buffering
     * across windows keeps this many bytes without tracking the window's absolute base. On {@link Status#STARVED}
     * it is the partial trailing unit left unconsumed; held steady while {@link Status#SUSPENDED}, since output
     * back-pressure consumes no further input.
     */
    int remaining();
}
