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

import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;
import java.util.zip.CRC32C;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class KafkaCacheChecksumTest
{
    private final CRC32C checksum = new CRC32C();

    @Test
    public void shouldComputeChecksumOfRegion() throws Exception
    {
        ByteBuffer shared = ByteBuffer.allocateDirect(64);
        UnsafeBufferEx buffer = new UnsafeBufferEx(shared);
        buffer.putStringWithoutLengthAscii(10, "hello world");

        long actual = KafkaCacheChecksum.compute(checksum, buffer, 16, 5);

        CRC32C expected = new CRC32C();
        expected.update("world".getBytes(), 0, 5);
        assertEquals(expected.getValue(), actual);
    }

    @Test
    public void shouldNotChangePositionOrLimitOfSharedBuffer() throws Exception
    {
        ByteBuffer shared = ByteBuffer.allocateDirect(64);
        UnsafeBufferEx buffer = new UnsafeBufferEx(shared);
        shared.limit(48);
        shared.position(3);

        KafkaCacheChecksum.compute(checksum, buffer, 16, 5);

        assertEquals(3, shared.position());
        assertEquals(48, shared.limit());
    }

    @Test
    public void shouldComputeChecksumWhenSharedBufferStateIsArbitrary() throws Exception
    {
        ByteBuffer shared = ByteBuffer.allocateDirect(64);
        UnsafeBufferEx buffer = new UnsafeBufferEx(shared);
        buffer.putStringWithoutLengthAscii(16, "world");
        shared.limit(4);
        shared.position(2);

        long actual = KafkaCacheChecksum.compute(checksum, buffer, 16, 5);

        CRC32C expected = new CRC32C();
        expected.update("world".getBytes(), 0, 5);
        assertEquals(expected.getValue(), actual);
    }
}
