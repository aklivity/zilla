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
 * then drive the parse with {@link #hasNext()} / {@link #nextEvent()}. A datum is framed by
 * {@link AvroEvent#START_MESSAGE} and {@link AvroEvent#END_MESSAGE}; {@link #hasNext()} returns
 * {@code false} when the buffered bytes are exhausted, so feed more and continue. Malformed binary
 * throws {@link AvroValidationException}. Within a pipeline, the value at each event is read through an
 * {@link AvroSource} layered over the parser; {@link #stream()} begins such a pipeline. Not
 * thread-safe; reuse one per thread.
 */
public interface AvroParser
{
    void wrap(
        DirectBuffer buffer,
        int offset,
        int length);

    boolean hasNext();

    AvroEvent nextEvent();

    /**
     * The {@link AvroType} of the value or composite at the current event — the record at
     * {@link AvroEvent#START_RECORD}, the selected branch at {@link AvroEvent#UNION_BRANCH}, the field
     * type at {@link AvroEvent#FIELD_NAME}, the scalar at a value event; {@code null} before the first
     * event, at {@link AvroEvent#END_MESSAGE}, and on segment events. The same view an
     * {@link AvroSource} exposes to a pipeline stage.
     */
    AvroType type();

    AvroStream stream();
}
