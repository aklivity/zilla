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
 * once per top-level datum, then {@link #feed(DirectBuffer, int, int)} per frame, resuming a datum left
 * {@link Status#PENDING} by an earlier frame. No full-document buffering; zero per-message allocation;
 * abort on failure.
 */
public interface AvroPipeline
{
    enum Status
    {
        /** the current top-level datum is still in progress; feed more bytes to continue */
        PENDING,
        /** the current top-level datum finished and was accepted */
        COMPLETE,
        /** the current top-level datum was rejected; the output must be abandoned */
        REJECTED
    }

    void reset();

    Status feed(
        DirectBuffer buffer,
        int offset,
        int length);
}
