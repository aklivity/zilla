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
package io.aklivity.zilla.runtime.binding.kafka.internal.stream;

import static io.aklivity.zilla.runtime.binding.kafka.internal.stream.KafkaChecksum.combineCRC32C;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.lang.management.ManagementFactory;
import java.util.Random;
import java.util.zip.CRC32C;

import org.junit.Test;

import com.sun.management.ThreadMXBean;

public class KafkaChecksumTest
{
    private static final int[] FIRST_LENGTHS = {0, 1, 61, 1024};
    private static final int[] SECOND_LENGTHS = {0, 1, 2, 7, 8, 9, 61, 255, 256, 257, 1000, 1024, 4096, 65535, 65536, 1 << 20};
    private static final int CALLS = 10_000;
    private static final long MAXIMUM_BYTES_PER_CALL = 16L;

    @Test
    public void shouldCombineAsChecksumOfConcatenatedBytes()
    {
        final Random random = new Random(0x5eed);

        for (int first : FIRST_LENGTHS)
        {
            for (int second : SECOND_LENGTHS)
            {
                final byte[] bytes = new byte[first + second];
                random.nextBytes(bytes);

                final long expected = checksum(bytes, 0, bytes.length);
                final long actual = combineCRC32C(checksum(bytes, 0, first), checksum(bytes, first, second), second);

                assertEquals("first " + first + " second " + second, expected, actual);
            }
        }
    }

    @Test
    public void shouldCombineWhenSecondChecksumIsOfEmptyBytes()
    {
        final byte[] bytes = new byte[61];
        new Random(0x5eed).nextBytes(bytes);
        final long crc = checksum(bytes, 0, bytes.length);

        assertEquals(crc, combineCRC32C(crc, 0L, 0L));
    }

    @Test
    public void shouldNotAllocateWhenCombining()
    {
        final ThreadMXBean threads = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        final long threadId = Thread.currentThread().getId();
        final long crc1 = 0x1234_5678L;
        final long crc2 = 0x9abc_def0L;

        long sink = 0L;
        for (int call = 0; call < CALLS; call++)
        {
            sink += combineCRC32C(crc1, crc2, 1024L);
        }

        final long allocatedBefore = threads.getThreadAllocatedBytes(threadId);

        for (int call = 0; call < CALLS; call++)
        {
            sink += combineCRC32C(crc1, crc2, 1024L);
        }

        final long allocatedAfter = threads.getThreadAllocatedBytes(threadId);

        assertThat((allocatedAfter - allocatedBefore) / CALLS, lessThan(MAXIMUM_BYTES_PER_CALL));
        assertNotEquals(0L, sink);
    }

    private static long checksum(
        byte[] bytes,
        int offset,
        int length)
    {
        final CRC32C crc = new CRC32C();
        crc.update(bytes, offset, length);
        return crc.getValue();
    }
}
