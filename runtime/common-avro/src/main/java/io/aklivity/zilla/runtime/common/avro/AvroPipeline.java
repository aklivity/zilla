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

/**
 * A runnable, resumable {@code common-avro} pipeline assembled from an {@link AvroStream} description
 * terminated with an {@link AvroSink}. Reuse a single instance per thread: call {@link #reset()}
 * once per top-level datum, then {@link #feed(DirectBuffer, int, int)} to drive it.
 * <p>
 * The caller owns the input bytes and presents them; the pipeline reads in place without copying. On
 * {@link Status#ADVANCED} the parser has consumed up to {@link #position()} and needs more input — the
 * caller may drop the consumed prefix and re-present the remainder plus newly arrived bytes. Zero
 * per-message allocation; abort on failure.
 * <p>
 * Output is bounded by the generator's limit: when it fills, {@code feed} returns {@link Status#SUSPENDED}
 * with a complete, drainable region in the output buffer; the caller drains it, resets the generator, and
 * calls {@code feed} again to resume the in-flight datum from where it paused. {@link #position()} does
 * not advance while suspended, and the caller must keep the input buffer stable across the resume so the
 * in-flight value remains readable.
 */
public interface AvroPipeline
{
    enum Status
    {
        /** the current datum is in progress and can continue immediately — feed more bytes to continue */
        ADVANCED,
        /** the bounded output filled: drain the buffer, reset the generator, then {@link #feed} again to resume */
        SUSPENDED,
        /** the current top-level datum finished and was accepted */
        COMPLETED,
        /** the current top-level datum was rejected; the output must be abandoned */
        REJECTED
    }

    void reset();

    Status feed(
        DirectBuffer buffer,
        int offset,
        int length);

    /**
     * The aggregate count of input bytes consumed since {@link #reset()} — the watermark up to which the
     * caller may drop bytes before re-presenting the remainder. Advances on {@link Status#ADVANCED}; held
     * steady while {@link Status#SUSPENDED}.
     */
    long position();
}
