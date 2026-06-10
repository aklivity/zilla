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
 * terminated with a {@link JsonSink}. Reuse a single instance per worker thread: call {@link #reset()}
 * once per top-level value, then {@link #feed(DirectBuffer, int, int)} per frame, resuming a value left
 * {@link Status#PENDING} by an earlier frame.
 */
public interface JsonPipeline
{
    enum Status
    {
        /** the current top-level value is still in progress; feed more bytes to continue */
        PENDING,
        /** the current top-level value finished and was accepted */
        COMPLETE,
        /** the current top-level value was rejected; the output must be abandoned */
        REJECTED
    }

    void reset();

    Status feed(
        DirectBuffer buffer,
        int offset,
        int length);
}
