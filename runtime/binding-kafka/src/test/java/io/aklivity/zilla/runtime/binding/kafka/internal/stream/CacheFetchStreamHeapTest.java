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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertTrue;

import java.lang.management.ManagementFactory;

import org.junit.Test;

import com.sun.management.ThreadMXBean;

import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaOffsetType;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaBeginExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.test.EngineBench;
import io.aklivity.zilla.runtime.engine.test.EngineBench.Client;
import io.aklivity.zilla.runtime.engine.test.EngineBench.FrameCounter;

public class CacheFetchStreamHeapTest
{
    private static final String CONFIGURATION = "io/aklivity/zilla/specs/binding/kafka/config/cache.client.yaml";
    private static final int PARTITIONS = 16;
    private static final int STREAMS = 400;
    private static final long MAXIMUM_BYTES_PER_STREAM = 8 * 1024;

    @Test
    public void shouldNotAllocateBufferPerFetchStreamWithoutValueModel() throws Exception
    {
        final ThreadMXBean threads = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        final MutableDirectBufferEx extension = new UnsafeBufferEx(new byte[256]);
        final FrameCounter replies = new FrameCounter();

        try (EngineBench bench = new EngineBench()
            .directory("target/zilla-bench-fetch-heap")
            .external("app1")
            .start(CONFIGURATION))
        {
            final long workerId = bench.workerThreadId();
            final int kafkaTypeId = bench.labelId("kafka");

            for (int partitionId = 0; partitionId < PARTITIONS; partitionId++)
            {
                begin(bench, replies, extension, kafkaTypeId, partitionId);
            }
            assertTrue(bench.pollUntil(() -> replies.resets() >= PARTITIONS));

            final long allocatedBefore = threads.getThreadAllocatedBytes(workerId);

            for (int stream = 0; stream < STREAMS; stream++)
            {
                begin(bench, replies, extension, kafkaTypeId, stream % PARTITIONS);
            }
            assertTrue(bench.pollUntil(() -> replies.resets() >= PARTITIONS + STREAMS));

            final long allocatedAfter = threads.getThreadAllocatedBytes(workerId);

            assertThat((allocatedAfter - allocatedBefore) / STREAMS, lessThan(MAXIMUM_BYTES_PER_STREAM));
        }
    }

    private static void begin(
        EngineBench bench,
        FrameCounter replies,
        MutableDirectBufferEx extension,
        int kafkaTypeId,
        int partitionId)
    {
        final KafkaBeginExFW beginEx = new KafkaBeginExFW.Builder().wrap(extension, 0, extension.capacity())
            .typeId(kafkaTypeId)
            .fetch(f -> f.topic("test")
                         .partition(p -> p.partitionId(partitionId)
                                          .partitionOffset(KafkaOffsetType.HISTORICAL.value())
                                          .latestOffset(KafkaOffsetType.LIVE.value())))
            .build();

        final Client client = bench.newClient("app0", replies);
        client.begin(beginEx.buffer(), beginEx.offset(), beginEx.sizeof());
    }
}
