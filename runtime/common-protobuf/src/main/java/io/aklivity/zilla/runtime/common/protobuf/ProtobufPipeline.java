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
import org.agrona.MutableDirectBuffer;

/**
 * A runnable {@code common-protobuf} pipeline assembled from a {@link ProtobufStream} description
 * terminated with a {@link ProtobufSink}. Reuse a single instance per worker thread: call
 * {@link #reset()} once per message, then {@link #transform} the message, which may arrive whole (the
 * bounded-buffer contract) or as successive input windows (the streaming contract).
 * <p>
 * Back-pressure has two independent axes, each with its own non-terminal "call {@code transform} again"
 * status:
 * <ul>
 * <li><b>Output</b> — {@link Status#SUSPENDED}: the bounded generator filled mid-message. The caller
 * drains the output, resets the generator, and calls {@code transform} again with the <em>same</em> input
 * window to resume the in-flight message from where it paused.</li>
 * <li><b>Input</b> — {@link Status#STARVED}: the input window was consumed but the message is not yet
 * complete. The caller calls {@code transform} again with the <em>next</em> input window, passing
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
 *     Status s = pipeline.transform(in.buffer(), in.offset(), in.length(), in.last());
 *     while (s == SUSPENDED)
 *     {
 *         drainOutput();
 *         generator.wrap(out, 0, limit);
 *         s = pipeline.transform(in.buffer(), in.offset(), in.length(), in.last());
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
        /** the bounded output filled: drain the buffer, reset the generator, then {@link #transform} again to resume */
        SUSPENDED,
        /** the input window was consumed mid-message: {@link #transform} the next window (input back-pressure) */
        STARVED,
        /** the message finished and was accepted */
        COMPLETED,
        /** the message was rejected; the output must be abandoned */
        REJECTED
    }

    void reset();

    /**
     * Whether this pipeline reproduces its input bytes for every accepted datum — the composition of its
     * parser, transform stages, and terminal generator all being identity.
     */
    boolean identity();

    /**
     * The number of bytes at the tail of the most recently fed window not yet consumed — exactly what the
     * caller retains (typically in its own reassembly slot) and re-presents, contiguous, at the front of the
     * next {@link #transform}. A caller buffering across windows keeps this many bytes without tracking the
     * window's absolute base. On {@link Status#STARVED} it is the unconsumed tail; held steady while
     * {@link Status#SUSPENDED} (re-feed the same window), since output back-pressure consumes no further input.
     */
    int remaining();

    /**
     * Transforms a whole message in one shot (equivalent to {@link #transform(DirectBuffer, int, int, boolean)} with
     * {@code last == true}), preserving the bounded-buffer contract for callers that reassemble first.
     */
    default Status transform(
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        return transform(buffer, offset, limit, true);
    }

    /**
     * Transforms one input window of a message, the bytes occupying the half-open range {@code [offset, limit)};
     * {@code last} marks the final window. Returns {@link Status#STARVED} when the window is consumed before
     * the message completes (input back-pressure), {@link Status#SUSPENDED} when the bounded output fills
     * (output back-pressure), {@link Status#COMPLETED} on a clean message end, or {@link Status#REJECTED} on
     * malformed or truncated input.
     */
    Status transform(
        DirectBuffer buffer,
        int offset,
        int limit,
        boolean last);

    /**
     * Transforms one input window {@code src[offset, limit)} into {@code dst[dstOffset, dstLimit)},
     * re-targeting the terminal generator at {@code dst} before pumping (an {@code SSLEngine.unwrap}-style
     * src/dst shape), and returns the reused {@link ProtobufPipelineResult} carrying the {@link Status} with
     * the bytes {@link ProtobufPipelineResult#consumed() consumed} from the input window and
     * {@link ProtobufPipelineResult#produced() produced} into the destination this call. Drive it in a loop:
     * drain the produced output, advance the input by {@code consumed}, and call again — on
     * {@link Status#SUSPENDED} with the same window (output back-pressure), on {@link Status#STARVED} with
     * the unconsumed tail prepended to the next window (input back-pressure). Available only when the
     * pipeline was terminated with {@link ProtobufStream#into(ProtobufGenerator)} so the pipeline owns the
     * generator it re-targets.
     */
    ProtobufPipelineResult transform(
        DirectBuffer src,
        int offset,
        int limit,
        boolean last,
        MutableDirectBuffer dst,
        int dstOffset,
        int dstLimit);
}
