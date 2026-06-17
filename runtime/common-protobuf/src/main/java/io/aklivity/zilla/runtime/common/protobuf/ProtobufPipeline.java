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
package io.aklivity.zilla.runtime.common.protobuf;

import org.agrona.DirectBuffer;

/**
 * A runnable {@code common-protobuf} pipeline assembled from a {@link ProtobufStream} description
 * terminated with a {@link ProtobufSink}. Reuse a single instance per worker thread: call
 * {@link #reset()} once per message, then {@link #feed} the message, which may arrive whole (the
 * bounded-buffer contract) or as successive input windows (the streaming contract).
 * <p>
 * Back-pressure has two independent axes, each with its own non-terminal "call {@code feed} again"
 * status:
 * <ul>
 * <li><b>Output</b> — {@link Status#SUSPENDED}: the bounded generator filled mid-message. The caller
 * drains the output, resets the generator, and calls {@code feed} again with the <em>same</em> input
 * window to resume the in-flight message from where it paused.</li>
 * <li><b>Input</b> — {@link Status#STARVED}: the input window was consumed but the message is not yet
 * complete. The caller calls {@code feed} again with the <em>next</em> input window, passing
 * {@code last == true} only on the final window. {@code STARVED} is returned only when
 * {@code last == false}; a clean message end under {@code last} yields {@link Status#COMPLETED} and an
 * incomplete one yields {@link Status#REJECTED}.</li>
 * </ul>
 * The canonical caller loop honours both axes:
 * <pre>{@code
 * pipeline.reset();
 * for (boolean done = false; !done; )
 * {
 *     Window in = nextWindow();
 *     Status s = pipeline.feed(in.buffer(), in.offset(), in.length(), in.last());
 *     while (s == SUSPENDED)
 *     {
 *         drainOutput();
 *         generator.wrap(out, 0, limit);
 *         s = pipeline.feed(in.buffer(), in.offset(), in.length(), in.last());
 *     }
 *     switch (s)
 *     {
 *     case STARVED: break;                 // feed the next window
 *     case COMPLETED: done = true; break;  // emit the final output
 *     case REJECTED: abort(); done = true; break;
 *     default: break;
 *     }
 * }
 * }</pre>
 */
public interface ProtobufPipeline
{
    enum Status
    {
        /** the event was consumed; the pump advances to the next — an internal sink-to-pump signal */
        ADVANCED,
        /** the bounded output filled: drain the buffer, reset the generator, then {@link #feed} again to resume */
        SUSPENDED,
        /** the input window was consumed mid-message: {@link #feed} the next window (input back-pressure) */
        STARVED,
        /** the message finished and was accepted */
        COMPLETED,
        /** the message was rejected; the output must be abandoned */
        REJECTED
    }

    void reset();

    /**
     * A short, human-readable reason for the most recent {@link Status#REJECTED} — e.g. an unknown
     * field, an unknown enum value, or truncated input — or {@code null} when the last feed did not
     * reject. Cleared by {@link #reset()}.
     */
    default String reason()
    {
        return null;
    }

    /**
     * The number of input bytes committed since the message began — always at a whole-unit boundary. On
     * {@link Status#STARVED} everything at or after this position is the unconsumed tail: the caller
     * retains it (typically in its own reassembly slot) and re-presents it, contiguous with the next
     * window, on the following {@link #feed}. The pipeline holds no input buffer of its own. The value is
     * unspecified after {@link Status#SUSPENDED} (re-feed the same window) and need not be consulted then.
     */
    long position();

    /**
     * Feeds a whole message in one shot (equivalent to {@link #feed(DirectBuffer, int, int, boolean)} with
     * {@code last == true}), preserving the bounded-buffer contract for callers that reassemble first.
     */
    default Status feed(
        DirectBuffer buffer,
        int offset,
        int length)
    {
        return feed(buffer, offset, length, true);
    }

    /**
     * Feeds one input window of a message; {@code last} marks the final window. Returns
     * {@link Status#STARVED} when the window is consumed before the message completes (input
     * back-pressure), {@link Status#SUSPENDED} when the bounded output fills (output back-pressure),
     * {@link Status#COMPLETED} on a clean message end, or {@link Status#REJECTED} on malformed or
     * truncated input.
     */
    Status feed(
        DirectBuffer buffer,
        int offset,
        int length,
        boolean last);
}
