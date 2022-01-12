/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.cog.tcp.internal.streams;

import static io.aklivity.zilla.runtime.cog.tcp.internal.SocketChannelHelper.ALL;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_BUFFER_POOL_CAPACITY;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_BUFFER_SLOT_CAPACITY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.IntStream.concat;
import static java.util.stream.IntStream.generate;
import static java.util.stream.IntStream.of;
import static org.junit.Assert.assertEquals;
import static org.junit.rules.RuleChain.outerRule;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.byteman.contrib.bmunit.BMScript;
import org.jboss.byteman.contrib.bmunit.BMUnitConfig;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

import io.aklivity.zilla.runtime.cog.tcp.internal.SocketChannelHelper;
import io.aklivity.zilla.runtime.cog.tcp.internal.SocketChannelHelper.HandleWriteHelper;
import io.aklivity.zilla.runtime.cog.tcp.internal.SocketChannelHelper.OnDataHelper;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

@RunWith(org.jboss.byteman.contrib.bmunit.BMUnitRunner.class)
@BMUnitConfig(loadDirectory = "src/test/resources")
@BMScript(value = "SocketChannelHelper.btm")
public class ServerPartialWriteLimitsIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("client", "io/aklivity/zilla/specs/cog/tcp/streams/network/rfc793")
        .addScriptRoot("server", "io/aklivity/zilla/specs/cog/tcp/streams/application/rfc793");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(8192)
        // Initial window size for output to network:
        .configure(ENGINE_BUFFER_SLOT_CAPACITY, 16)
        // Overall buffer pool size same as slot size so maximum concurrent streams with partial writes = 1
        .configure(ENGINE_BUFFER_POOL_CAPACITY, 16)
        .configurationRoot("io/aklivity/zilla/specs/cog/tcp/config")
        .external("app0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(SocketChannelHelper.RULE)
                                  .around(engine).around(k3po).around(timeout);

    @Test
    @Configuration("server.json")
    @Specification({
        "${server}/server.sent.data.multiple.frames/server",
        "${client}/server.sent.data.multiple.frames/client"
    })
    public void shouldWriteWhenMoreDataArrivesWhileAwaitingSocketWritableWithoutOverflowingSlot() throws Exception
    {
        AtomicInteger dataFramesReceived = new AtomicInteger();
        OnDataHelper.fragmentWrites(generate(() -> dataFramesReceived.incrementAndGet() == 1 ? 5
                : dataFramesReceived.get() == 2 ? 6 : ALL));
        HandleWriteHelper.fragmentWrites(generate(() -> dataFramesReceived.get() >= 2 ? ALL : 0));

        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${server}/server.sent.data.multiple.streams.second.was.reset/server"
    })
    public void shouldResetStreamsExceedingPartialWriteStreamsLimit() throws Exception
    {
        OnDataHelper.fragmentWrites(concat(of(1), generate(() -> 0))); // avoid spin write for first stream write
        AtomicBoolean resetReceived = new AtomicBoolean(false);
        HandleWriteHelper.fragmentWrites(generate(() -> resetReceived.get() ? ALL : 0));

        k3po.start();

        try (SocketChannel channel1 = SocketChannel.open();
             SocketChannel channel2 = SocketChannel.open())
        {
            channel1.connect(new InetSocketAddress("127.0.0.1", 8080));
            channel2.connect(new InetSocketAddress("127.0.0.1", 8080));

            k3po.awaitBarrier("SECOND_STREAM_RESET_RECEIVED");
            resetReceived.set(true);

            ByteBuffer buf = ByteBuffer.allocate(256);
            while (buf.position() < 13)
            {
                int len = channel1.read(buf);
                if (len == -1)
                {
                    break;
                }
            }
            buf.flip();

            assertEquals("server data 1", UTF_8.decode(buf).toString());

            int len = 0;
            buf.rewind();
            while (buf.position() < 13)
            {
                len = channel2.read(buf);
                if (len == -1)
                {
                    break;
                }
            }
            buf.flip();

            assertEquals(0, buf.remaining());
            assertEquals(-1, len);

            k3po.finish();
        }
    }
}

