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

/**
 * The event currency of a {@link ProtobufStream} pipeline. Structured events frame a message
 * ({@link #START_MESSAGE} / {@link #END_MESSAGE}) and deliver one occurrence of a field as a
 * {@link #FIELD} (which positions {@link ProtobufSource#field()}) followed by a {@link #VALUE} for a
 * scalar or a nested {@code START_MESSAGE}…{@code END_MESSAGE} for a composite. A proto2 group is framed
 * by {@link #START_GROUP} / {@link #END_GROUP} instead — the same structured body, but delimited on the
 * wire by start/end-group tags rather than a length prefix. A {@link #SEGMENT} delivers a composite value
 * as raw wire bytes rather than as structured events; {@link #segmented()} distinguishes it, and
 * {@link ProtobufSource#bytesDeferred()} carries how much of the value is still to come — a value larger
 * than the input window arrives as repeated {@code SEGMENT}s with a decreasing {@code bytesDeferred()},
 * the last (or only) piece reporting {@code 0}.
 * <p>
 * There is no document frame: the bounded-buffer contract is exactly one fully-buffered message per
 * {@link ProtobufPipeline#feed}, so the root {@link #START_MESSAGE} / {@link #END_MESSAGE} is the
 * top-level boundary.
 */
public enum ProtobufEvent
{
    START_MESSAGE,
    END_MESSAGE,
    START_GROUP,
    END_GROUP,
    FIELD,
    VALUE,
    SEGMENT;

    public boolean segmented()
    {
        return this == SEGMENT;
    }
}
