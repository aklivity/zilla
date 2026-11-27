/*
 * Copyright 2021-2026 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.kafka.internal.cache;

import java.nio.ByteBuffer;
import java.util.zip.CRC32C;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

public final class KafkaCacheChecksum
{
    // a mapped log buffer is read by several workers, so its position and limit must never change:
    // the checksum is computed over a private view of the requested region instead
    public static long compute(
        CRC32C checksum,
        DirectBufferEx buffer,
        int index,
        int length)
    {
        final ByteBuffer view = buffer.byteBuffer().duplicate().clear().limit(index + length).position(index);

        checksum.reset();
        checksum.update(view);

        return checksum.getValue();
    }

    private KafkaCacheChecksum()
    {
    }
}
