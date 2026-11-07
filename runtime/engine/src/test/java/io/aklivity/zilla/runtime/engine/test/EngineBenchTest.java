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

import java.io.ByteArrayOutputStream;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.engine.test.EngineBench.Client;
import io.aklivity.zilla.runtime.engine.test.EngineBench.FrameCounter;
import io.aklivity.zilla.runtime.engine.test.Responder.Response;
import io.aklivity.zilla.runtime.engine.test.Responder.Trigger;

public class EngineBenchTest
{
    private static final String CONFIGURATION =
        "io/aklivity/zilla/runtime/engine/test/EngineBenchTest-passthrough.yaml";

    private static final int PAYLOAD_LENGTH = 16;

    @Test
    public void shouldExchangeFramesThroughExternalServer() throws Exception
    {
        final Responder server = new Responder(Trigger.END, Response.EMPTY);
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
            client.send(payload, 0, PAYLOAD_LENGTH);
            client.end();
            client.awaitReplyEnd();
        }

        assertThat(server.counter().begins(), equalTo(1));
        assertThat(server.counter().datas(), equalTo(1));
        assertThat(server.counter().dataBytes(), equalTo((long) PAYLOAD_LENGTH));
        assertThat(server.counter().ends(), equalTo(1));
        assertThat(replies.begins(), equalTo(1));
        assertThat(replies.windows(), greaterThan(0));
        assertThat(replies.ends(), equalTo(1));
    }

    @Test
    public void shouldReplayResponseWithinReplyCredit() throws Exception
    {
        final byte[] beginEx = { 1, 2, 3 };
        final Response response = new Response(
            beginEx,
            new byte[][] { { 4, 5 }, { 6 } },
            new byte[][] { "abcdefghijklmnopqrst".getBytes(), "uvwxy".getBytes() },
            new byte[] { 7, 8 },
            8);
        final Responder server = new Responder(Trigger.END, response);
        final Reassembler reassembler = new Reassembler();

        try (EngineBench bench = new EngineBench()
            .directory("target/zilla-bench-test")
            .external("app0")
            .start(CONFIGURATION))
        {
            bench.newServer("app0", server);

            final Client client = bench.newClient("net0", reassembler);

            client.begin();
            client.replyWindow(8);
            client.end();

            assertThat(bench.pollUntil(() -> reassembler.counter.ends() == 1), equalTo(true));
        }

        assertThat(reassembler.counter.begins(), equalTo(1));
        assertThat(reassembler.beginEx.toByteArray(), equalTo(beginEx));
        assertThat(reassembler.counter.datas(), equalTo(5));
        assertThat(reassembler.payload.toString(), equalTo("abcdefghijklmnopqrstuvwxy"));
    }

    private static final class Reassembler implements MessageConsumer
    {
        private final FrameCounter counter = new FrameCounter();
        private final BeginFW beginRO = new BeginFW();
        private final DataFW dataRO = new DataFW();
        private final ByteArrayOutputStream beginEx = new ByteArrayOutputStream();
        private final StringBuilder payload = new StringBuilder();

        @Override
        public void accept(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            counter.accept(msgTypeId, buffer, index, length);

            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                final byte[] extension = new byte[begin.extension().sizeof()];
                begin.extension().buffer().getBytes(begin.extension().offset(), extension);
                beginEx.writeBytes(extension);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                final byte[] fragment = new byte[data.length()];
                data.payload().buffer().getBytes(data.payload().offset(), fragment);
                payload.append(new String(fragment));
                break;
            default:
                break;
            }
        }
    }
}
