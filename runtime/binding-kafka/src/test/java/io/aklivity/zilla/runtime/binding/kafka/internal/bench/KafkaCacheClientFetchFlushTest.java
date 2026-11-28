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

import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_FETCH_SEGMENT_BYTES;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_FETCH_SEGMENT_INDEX_BYTES;
import static io.aklivity.zilla.runtime.engine.test.EngineBench.DEFAULT_WINDOW;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.engine.test.EngineBench;
import io.aklivity.zilla.runtime.engine.test.EngineBench.Client;
import io.aklivity.zilla.specs.binding.kafka.internal.KafkaFunctions;

public class KafkaCacheClientFetchFlushTest
{
    private static final String CONFIGURATION = "io/aklivity/zilla/specs/binding/kafka/config/cache.options.bootstrap.yaml";
    private static final int SEGMENT_BYTES = 1024 * 1024;
    private static final int SEGMENT_INDEX_BYTES = 64 * 1024;
    private static final long SETTLE_NANOS = MILLISECONDS.toNanos(500L);

    @Test
    public void shouldChangeFilterBeforeReplyBegin() throws Exception
    {
        final EngineBench bench = new EngineBench()
            .directory("target/zilla-cache-client-flush")
            .external("app1")
            .configure(KAFKA_CACHE_FETCH_SEGMENT_BYTES, SEGMENT_BYTES)
            .configure(KAFKA_CACHE_FETCH_SEGMENT_INDEX_BYTES, SEGMENT_INDEX_BYTES)
            .start(CONFIGURATION);

        try
        {
            final int kafkaTypeId = bench.labelId("kafka");
            final KafkaCacheExitStub stub =
                new KafkaCacheExitStub(kafkaTypeId, 1, 0L, new UnsafeBufferEx(new byte[16]));
            stub.holdFetchReply();
            bench.newServer("app1", stub);

            assertTrue("bootstrap fetch reached the exit", bench.pollUntil(() -> stub.fetchBegins() > 0));

            final UnsafeBufferEx beginEx = new UnsafeBufferEx(KafkaFunctions.beginEx()
                .typeId(kafkaTypeId)
                .fetch()
                    .topic("test")
                    .partition(0, 0)
                    .filter()
                        .headers("header3")
                            .sequence("one")
                            .build()
                        .build()
                    .build()
                .build());

            final UnsafeBufferEx flushEx = new UnsafeBufferEx(KafkaFunctions.flushEx()
                .typeId(kafkaTypeId)
                .fetch()
                    .filter()
                        .headers("header3")
                            .sequence("one")
                            .build()
                        .build()
                    .filter()
                        .headers("header2")
                            .sequence("value2")
                            .build()
                        .build()
                    .build()
                .build());

            final boolean[] replyBegun = new boolean[1];
            final Client client = bench.newClient(bench.bindingId("app0"),
                (msgTypeId, buffer, index, length) -> replyBegun[0] |= msgTypeId == BeginFW.TYPE_ID);
            client.begin(beginEx, 0, beginEx.capacity());
            client.replyWindow(DEFAULT_WINDOW);
            client.flush(flushEx, 0, flushEx.capacity());

            final long settled = System.nanoTime() + SETTLE_NANOS;
            while (System.nanoTime() < settled)
            {
                bench.poll();
            }

            stub.releaseFetchReply();

            assertTrue("reply opened after the filter change", bench.pollUntil(() -> replyBegun[0]));
        }
        finally
        {
            bench.close();
        }
    }
}
