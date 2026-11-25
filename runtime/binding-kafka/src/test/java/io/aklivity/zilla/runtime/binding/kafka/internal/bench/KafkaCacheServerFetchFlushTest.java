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
package io.aklivity.zilla.runtime.binding.kafka.internal.bench;

import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_SEGMENT_BYTES;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_SEGMENT_INDEX_BYTES;
import static io.aklivity.zilla.runtime.engine.test.EngineBench.DEFAULT_WINDOW;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.engine.test.EngineBench;
import io.aklivity.zilla.runtime.engine.test.EngineBench.Client;
import io.aklivity.zilla.specs.binding.kafka.internal.KafkaFunctions;

public class KafkaCacheServerFetchFlushTest
{
    private static final String CONFIGURATION = "io/aklivity/zilla/specs/binding/kafka/config/cache.options.bootstrap.yaml";
    private static final int SEGMENT_BYTES = 1024 * 1024;
    private static final int SEGMENT_INDEX_BYTES = 64 * 1024;
    private static final int MEMBERS = 32;
    private static final int RECORDS = 5;

    @Test
    public void shouldFlushEveryMemberWithTheSameProgress() throws Exception
    {
        final EngineBench bench = new EngineBench()
            .directory("target/zilla-cache-server-flush")
            .external("app1")
            .configure(KAFKA_CACHE_SEGMENT_BYTES, SEGMENT_BYTES)
            .configure(KAFKA_CACHE_SEGMENT_INDEX_BYTES, SEGMENT_INDEX_BYTES)
            .start(CONFIGURATION);

        try
        {
            final int kafkaTypeId = bench.labelId("kafka");
            final UnsafeBufferEx payload = new UnsafeBufferEx(new byte[100]);
            final KafkaCacheExitStub stub = new KafkaCacheExitStub(kafkaTypeId, 1, 0L, payload);
            bench.newServer("app1", stub);

            final UnsafeBufferEx fetchEx = new UnsafeBufferEx(KafkaFunctions.beginEx()
                .typeId(kafkaTypeId)
                .fetch()
                    .topic("test")
                    .partition(0, 0L)
                    .build()
                .build());

            final long cacheId = bench.bindingId("cache0");
            final List<List<byte[]>> flushes = new ArrayList<>();
            for (int member = 0; member < MEMBERS; member++)
            {
                final List<byte[]> memberFlushes = new ArrayList<>();
                final Client fetcher = bench.newClient(cacheId, (msgTypeId, buffer, index, length) ->
                    captureFlushExtension(msgTypeId, buffer, index, length, memberFlushes));
                fetcher.begin(fetchEx, 0, fetchEx.capacity());
                fetcher.replyWindow(DEFAULT_WINDOW);
                flushes.add(memberFlushes);
            }

            final UnsafeBufferEx produceEx = new UnsafeBufferEx(KafkaFunctions.beginEx()
                .typeId(kafkaTypeId)
                .produce()
                    .topic("test")
                    .partition(0)
                    .build()
                .build());
            final UnsafeBufferEx dataEx = new UnsafeBufferEx(KafkaFunctions.dataEx()
                .typeId(kafkaTypeId)
                .produce()
                    .timestamp(1_700_000_000_000L)
                    .build()
                .build());

            final Client producer = bench.newClient(bench.bindingId("app0"), KafkaCacheServerFetchFlushTest::ignore);
            producer.begin(produceEx, 0, produceEx.capacity());
            producer.replyWindow(DEFAULT_WINDOW);

            assertTrue("produce stream opened", bench.pollUntil(() -> producer.initialCredit() > 0));

            for (int record = 0; record < RECORDS; record++)
            {
                final int sent = record + 1;
                producer.data(payload, 0, payload.capacity(), dataEx, 0, dataEx.capacity());
                assertTrue("record populated", bench.pollUntil(() -> stub.drain() >= 0 && stub.populated() >= sent));
            }

            assertTrue("every member flushed", bench.pollUntil(() -> flushes.stream().noneMatch(List::isEmpty)));

            final List<byte[]> expected = flushes.get(0);
            for (List<byte[]> memberFlushes : flushes)
            {
                assertEquals(expected.size(), memberFlushes.size());
                for (int i = 0; i < expected.size(); i++)
                {
                    assertArrayEquals(expected.get(i), memberFlushes.get(i));
                }
            }
        }
        finally
        {
            bench.close();
        }
    }

    private static void ignore(
        int msgTypeId,
        DirectBufferEx buffer,
        int index,
        int length)
    {
    }

    private static void captureFlushExtension(
        int msgTypeId,
        DirectBufferEx buffer,
        int index,
        int length,
        List<byte[]> flushes)
    {
        if (msgTypeId == FlushFW.TYPE_ID)
        {
            final FlushFW flush = new FlushFW().wrap(buffer, index, index + length);
            final byte[] extension = new byte[flush.extension().sizeof()];
            flush.extension().buffer().getBytes(flush.extension().offset(), extension);
            flushes.add(extension);
        }
    }
}
