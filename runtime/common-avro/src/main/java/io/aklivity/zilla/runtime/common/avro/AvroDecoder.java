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
 * A schema-bound Avro pull parser. Feed each frame's bytes with {@link #wrap(DirectBuffer, int, int)},
 * then drive the parse with {@link #hasNextEvent()} / {@link #nextEvent()}, reading the current value
 * through the {@link AvroSource} accessors this parser exposes. A datum is framed by
 * {@link AvroEvent#START_MESSAGE} and {@link AvroEvent#END_MESSAGE}; {@link #hasNextEvent()} returns
 * {@code false} when the buffered bytes are exhausted, so feed more and continue. Malformed binary
 * throws {@link AvroValidationException}. {@link #stream()} begins a composable pipeline that drives
 * this parser internally. Not thread-safe; reuse one per thread.
 */
public interface AvroDecoder extends AvroSource
{
    void wrap(
        DirectBuffer buffer,
        int offset,
        int length);

    boolean hasNextEvent();

    AvroEvent nextEvent();

    AvroStream stream();
}
