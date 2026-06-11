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

import org.agrona.MutableDirectBuffer;

/**
 * A buffer-backed Protobuf wire generator — the output edge a {@link ProtobufSink#of(ProtobufGenerator,
 * ProtobufSchema, String)} sink writes through, peer to {@code common-json}'s buffer-backed generator.
 * Reuse a single instance per worker thread; {@link #wrap(MutableDirectBuffer, int)} retargets it and
 * {@link #length()} reports the bytes written since.
 */
public interface ProtobufGenerator
{
    ProtobufGenerator wrap(
        MutableDirectBuffer buffer,
        int offset);

    int length();
}
