/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.model.core.ext;

/**
 * The event currency of a {@code bytes} pipeline: value framing plus the value's bytes as they arrive.
 * <p>
 * {@code bytes} has no internal structure, so this is a strict subset of what a structured format needs —
 * there is no field event, and no scalar typing. A value opens with {@link #START_VALUE}, delivers its
 * bytes as a run of {@link #SEGMENT} events (one per fragment the value arrives in, so a stage sees the
 * value as it flows rather than only once it is whole), and closes with {@link #END_VALUE}.
 * </p>
 */
public enum BytesEvent
{
    /** start of a value; {@link #SEGMENT} events follow until {@link #END_VALUE} */
    START_VALUE,
    /** a contiguous run of the current value's bytes, readable via {@link BytesSource#getSegment()} */
    SEGMENT,
    /** end of a value; no further bytes follow */
    END_VALUE;

    /**
     * Whether this event carries value bytes, as opposed to framing a value.
     *
     * @return {@code true} for {@link #SEGMENT}; {@code false} otherwise
     */
    public boolean segmented()
    {
        return this == SEGMENT;
    }
}
