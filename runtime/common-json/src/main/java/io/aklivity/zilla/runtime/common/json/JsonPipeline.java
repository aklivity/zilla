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
        /** the pump advanced through all available input mid-value; feed the next frame to continue */
        ADVANCED,
        /** the bounded output filled: drain the buffer, re-target the generator, then {@link #feed} again to resume */
        SUSPENDED,
        /** the current top-level value finished and was accepted */
        COMPLETED,
        /** the current top-level value was rejected; the output must be abandoned */
        REJECTED
    }

    void reset();

    Status feed(
        DirectBuffer buffer,
        int offset,
        int length);
}
