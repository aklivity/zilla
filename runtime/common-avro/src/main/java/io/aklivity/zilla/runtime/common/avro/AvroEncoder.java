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
import org.agrona.MutableDirectBuffer;

/**
 * A buffer-backed Avro binary writer driven by an {@link AvroSink}. {@link #wrap(MutableDirectBuffer,
 * int)} targets the output buffer; {@link #encode(AvroEvent, AvroSource)} writes one structured event;
 * {@link #writeSegment(DirectBuffer, int, int)} appends a verbatim raw slice; {@link #length()} reports
 * the bytes written for the current datum. Not thread-safe; reuse one per thread.
 */
public interface AvroEncoder
{
    void wrap(
        MutableDirectBuffer buffer,
        int offset);

    void encode(
        AvroEvent event,
        AvroSource source);

    void writeSegment(
        DirectBuffer buffer,
        int offset,
        int length);

    int length();
}
