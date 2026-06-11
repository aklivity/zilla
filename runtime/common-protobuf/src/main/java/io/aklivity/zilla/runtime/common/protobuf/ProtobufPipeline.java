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
 * {@link #reset()} once per message, then {@link #feed(DirectBuffer, int, int)} with the fully
 * buffered message (the bounded-buffer contract).
 * <p>
 * Output is bounded by the generator's limit: when it fills, {@code feed} returns {@link Status#SUSPENDED}
 * with a complete, drainable region in the output buffer; the caller drains it, resets the generator, and
 * calls {@code feed} again to resume the in-flight message from where it paused.
 */
public interface ProtobufPipeline
{
    enum Status
    {
        /** the message is in progress and can continue immediately — an internal sink-to-pump signal */
        RESUMABLE,
        /** the bounded output filled: drain the buffer, reset the generator, then {@link #feed} again to resume */
        SUSPENDED,
        /** the message finished and was accepted */
        COMPLETE,
        /** the message was rejected; the output must be abandoned */
        REJECTED
    }

    void reset();

    Status feed(
        DirectBuffer buffer,
        int offset,
        int length);
}
