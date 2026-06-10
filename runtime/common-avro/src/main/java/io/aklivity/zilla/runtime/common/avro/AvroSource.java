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
 * Immutable, read-only view of the value observed at the current {@link AvroEvent} as an
 * {@link AvroDecodePipeline} pumps events to its {@link AvroSink}. An {@code AvroSource} exposes
 * only the typed accessor that matches the current event; it has no cursor-advancing method, so a
 * consumer cannot disturb the pump. The buffer accessors for {@link AvroEvent#BYTES} and
 * {@link AvroEvent#FIXED} expose the value in place for zero-copy reads and are valid only for the
 * duration of the {@link AvroSink#feed(AvroEvent, AvroSource)} call.
 */
public interface AvroSource
{
    boolean getBoolean();

    int getInt();

    long getLong();

    float getFloat();

    double getDouble();

    String getString();

    DirectBuffer buffer();

    int offset();

    int length();

    /**
     * @return the byte position of the current event within the value, for diagnostics
     */
    long position();
}
