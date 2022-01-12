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
import static io.aklivity.zilla.runtime.cog.tcp.internal.TcpCog.WRITE_SPIN_COUNT;
import static java.net.StandardSocketOptions.SO_REUSEADDR;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.IntStream.concat;
import static java.util.stream.IntStream.generate;
import static java.util.stream.IntStream.of;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.rules.RuleChain.outerRule;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

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
public class ClientPartialWriteIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("server", "io/aklivity/zilla/specs/cog/tcp/streams/network/rfc793")
        .addScriptRoot("client", "io/aklivity/zilla/specs/cog/tcp/streams/application/rfc793");

    private final TestRule timeout = new DisableOnDebug(new Timeout(15, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(4096)
        .configurationRoot("io/aklivity/zilla/specs/cog/tcp/config")
        .external("app0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(SocketChannelHelper.RULE).around(engine).around(k3po).around(timeout);

    @Test
    @Configuration("client.host.json")
    @Specification({
        "${client}/client.sent.data/client",
        "${server}/client.sent.data/server"
    })
    public void shouldSpinWrite() throws Exception
    {
        OnDataHelper.fragmentWrites(generate(() -> 0).limit(WRITE_SPIN_COUNT - 1));
        HandleWriteHelper.fragmentWrites(generate(() -> 0));
        k3po.finish();
    }

    @Test
    @Configuration("client.host.json")
    @Specification({
        "${client}/client.sent.data/client",
        "${server}/client.sent.data/server"
    })
    public void shouldFinishWriteWhenSocketIsWritableAgain() throws Exception
    {
        OnDataHelper.fragmentWrites(IntStream.of(5));
        k3po.finish();
    }

    @Test
    @Configuration("client.host.json")
    @Specification({
        "${client}/client.sent.data/client",
        "${server}/client.sent.data/server"
    })
    public void shouldHandleMultiplePartialWrites() throws Exception
    {
        OnDataHelper.fragmentWrites(IntStream.of(2));
        HandleWriteHelper.fragmentWrites(IntStream.of(3, 1));
        k3po.finish();
    }

    @Test
    @Configuration("client.host.json")
    @Specification({
        "${client}/client.sent.data.multiple.frames/client",
        "${server}/client.sent.data.multiple.frames/server"
    })
    public void shouldWriteWhenMoreDataArrivesWhileAwaitingSocketWritable() throws Exception
    {
        // processData will be called for each of the two data frames. Make the first
        // do a partial write, then write nothing until handleWrite is called after the
        // second processData call, when we write everything.
        AtomicBoolean finishWrite = new AtomicBoolean(false);

        OnDataHelper.fragmentWrites(concat(of(5), generate(() -> finishWrite.getAndSet(true) ? 0 : 0)));
        HandleWriteHelper.fragmentWrites(generate(() -> finishWrite.get() ? ALL : 0));
        k3po.finish();
    }

    @Test
    @Configuration("client.host.json")
    @Specification({
        "${client}/client.sent.data.then.end/client"
    })
    public void shouldHandleEndOfStreamWithPendingWrite() throws Exception
    {
        AtomicBoolean endWritten = new AtomicBoolean(false);
        OnDataHelper.fragmentWrites(concat(of(5), generate(() -> 0)));
        HandleWriteHelper.fragmentWrites(generate(() -> endWritten.get() ? ALL : 0));

        try (ServerSocketChannel server = ServerSocketChannel.open())
        {
            server.setOption(SO_REUSEADDR, true);
            server.bind(new InetSocketAddress("127.0.0.1", 8080));

            k3po.start();

            try (SocketChannel channel = server.accept())
            {
                k3po.awaitBarrier("END_WRITTEN");
                endWritten.set(true);

                ByteBuffer buf = ByteBuffer.allocate("client data".length() + 10);
                boolean closed = false;
                do
                {
                    int len = channel.read(buf);
                    if (len == -1)
                    {
                        closed = true;
                        break;
                    }
                } while (buf.position() < "client data".length());
                buf.flip();

                assertEquals("client data", UTF_8.decode(buf).toString());

                if (!closed)
                {
                    buf.rewind();
                    closed = channel.read(buf) == -1;
                }

                assertTrue("Stream was not closed", closed);

                k3po.finish();
            }
        }
    }

}
