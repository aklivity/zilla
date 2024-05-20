/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.command.dump.internal;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import java.nio.file.Path;

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.RingBuffer;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

import io.aklivity.zilla.runtime.command.dump.internal.test.DumpRule;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.engine.internal.layouts.StreamsLayout;

public class TlsNetworkIT
{
    private final DataFW.Builder dataRW = new DataFW.Builder();

    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/runtime/command/dump/binding/tls/streams/network");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final DumpRule dump = new DumpRule()
        .labels("test", "net0", "tls", "server")
        .bindings("test.net0", "test.tls", "test.server", "test.0", "test.tls");

    @Rule
    public final TestRule chain = outerRule(dump).around(k3po).around(timeout);

    @Ignore
    @Test
    @Specification({
        "${net}/connection.established/client",
        "${net}/connection.established/server"})
    public void shouldEstablishConnection() throws Exception
    {
        k3po.finish();
    }

    @Test
    public void shouldWriteCustomFrame() throws Exception
    {
        StreamsLayout streamsLayout = new StreamsLayout.Builder()
            .path(Path.of("target/zilla-itests/data63"))
            .streamsCapacity(2 * 1024)
            .readonly(false)
            .build();
        RingBuffer stream = streamsLayout.streamsBuffer();
        MutableDirectBuffer frameBuffer = new UnsafeBuffer(new byte[2 * 1024]);

        // data frame with tls payload: TLSv1.3 Server Hello
        DirectBuffer tlsPayload1 = new UnsafeBuffer(BitUtil.fromHex(
            "160303007a020000760303328f126a2dc67b1d107023f088ca43560c8b1535c9d7e1be8b217b60b8cefa32209d830c3919be" +
            "a4f53b3ace6b5f6837c9914c982f1421d3e162606c3eb5907c16130200002e002b0002030400330024001d00201c00c791d3" +
            "e7b6b5dc3f191be9e29a7e220e8ea695696b281e7f92e27a05f27e"));
        DataFW tlsData = dataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x0000000100000002L)
            .routedId(0x0000000100000002L)
            .streamId(0x0000000000000001L)
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000000L)
            .traceId(0x0000000000000001L)
            .budgetId(0x0000000000000002L)
            .reserved(0x00000042)
            .payload(tlsPayload1, 0, tlsPayload1.capacity())
            .build();
        stream.write(DataFW.TYPE_ID, tlsData.buffer(), 0, tlsData.sizeof());

        streamsLayout.close();
    }
}
