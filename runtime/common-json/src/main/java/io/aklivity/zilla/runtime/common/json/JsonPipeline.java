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
package io.aklivity.zilla.runtime.common.json;

import org.agrona.DirectBuffer;

/**
 * A runnable, resumable {@code common-json} pipeline assembled from a {@link JsonStream} description
 * terminated with a {@link JsonSink}. Reuse a single instance per thread: call {@link #reset()}
 * once per top-level value, then {@link #feed(DirectBuffer, int, int)} per frame, resuming a value left
 * {@link Status#ADVANCED} by an earlier frame.
 * <p>
 * Output is bounded by the terminal generator's limit: when it fills at an event boundary,
 * {@code feed} returns {@link Status#SUSPENDED} with a complete, drainable region in the output buffer;
 * the caller drains it, re-targets the generator at a fresh region (preserving its structural context),
 * and calls {@code feed} again with the same frame to resume the in-flight value from where it paused.
 */
public interface JsonPipeline
{
    enum Status
    {
        /** an event was consumed; the pump advances to the next — an internal sink-to-pump signal */
        ADVANCED,
        /** the bounded output filled: drain the buffer, re-target the generator, then {@link #feed} again to resume */
        SUSPENDED,
        /** the input window was consumed mid-value: {@link #feed} the next window (input back-pressure) */
        STARVED,
        /** the current top-level value finished and was accepted */
        COMPLETED,
        /** the current top-level value was rejected (malformed or truncated); the output must be abandoned */
        REJECTED
    }

    void reset();

    /**
     * Feeds a whole value in one shot (equivalent to {@link #feed(DirectBuffer, int, int, boolean)} with
     * {@code last == true}), for callers that reassemble the value before feeding it.
     */
    default Status feed(
        DirectBuffer buffer,
        int offset,
        int length)
    {
        return feed(buffer, offset, length, true);
    }

    /**
     * Feeds one input window; {@code last} marks the final window. Returns {@link Status#STARVED} when the
     * window is consumed before the value completes (input back-pressure — feed the next window),
     * {@link Status#SUSPENDED} when the bounded output fills (output back-pressure — drain and re-feed the
     * same window), {@link Status#COMPLETED} on a clean value end, or {@link Status#REJECTED} on malformed
     * or truncated input. {@code STARVED} is returned only when {@code last == false}; an incomplete value
     * under {@code last == true} yields {@code REJECTED}.
     */
    Status feed(
        DirectBuffer buffer,
        int offset,
        int length,
        boolean last);

    /**
     * The number of bytes at the tail of the most recently fed window not yet consumed — exactly what the
     * caller retains and re-presents, contiguous, at the front of the next {@link #feed}. A caller buffering
     * across windows keeps this many bytes without tracking the window's absolute base. On {@link Status#STARVED}
     * it is the partial trailing unit left unconsumed (e.g. a multibyte character split across a frame); held
     * steady while {@link Status#SUSPENDED}, since output back-pressure consumes no further input.
     */
    int remaining();
}
