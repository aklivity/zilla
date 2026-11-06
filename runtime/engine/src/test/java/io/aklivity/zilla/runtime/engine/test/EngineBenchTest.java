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
package io.aklivity.zilla.runtime.engine.test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.test.EngineBench.Client;
import io.aklivity.zilla.runtime.engine.test.EngineBench.CountingHandler;
import io.aklivity.zilla.runtime.engine.test.EngineBench.FrameCounter;

public class EngineBenchTest
{
    private static final String CONFIGURATION =
        "io/aklivity/zilla/runtime/engine/test/EngineBenchTest-passthrough.yaml";

    private static final int PAYLOAD_LENGTH = 16;

    @Test
    public void shouldExchangeFramesThroughExternalServer() throws Exception
    {
        final CountingHandler server = new CountingHandler();
        final FrameCounter replies = new FrameCounter();
        final MutableDirectBufferEx payload = new UnsafeBufferEx(new byte[PAYLOAD_LENGTH]);

        try (EngineBench bench = new EngineBench()
            .directory("target/zilla-bench-test")
            .external("app0")
            .start(CONFIGURATION))
        {
            bench.newServer("app0", server);

            final Client client = bench.newClient("net0", replies);

            client.begin();
            assertThat(bench.pollUntil(() -> client.initialCredit() >= PAYLOAD_LENGTH), equalTo(true));

            client.data(payload, 0, PAYLOAD_LENGTH);
            client.end();

            assertThat(bench.pollUntil(() -> server.counter().ends() == 1 && replies.ends() == 1), equalTo(true));
        }

        assertThat(server.counter().begins(), equalTo(1));
        assertThat(server.counter().datas(), equalTo(1));
        assertThat(server.counter().dataBytes(), equalTo((long) PAYLOAD_LENGTH));
        assertThat(server.counter().ends(), equalTo(1));
        assertThat(replies.begins(), equalTo(1));
        assertThat(replies.windows(), greaterThan(0));
        assertThat(replies.ends(), equalTo(1));
    }
}
