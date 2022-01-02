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

import static io.aklivity.zilla.runtime.cog.tcp.internal.TcpConfiguration.TCP_MAX_CONNECTIONS;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DRAIN_ON_CLOSE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.rules.RuleChain.outerRule;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.ScriptProperty;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

import io.aklivity.zilla.runtime.engine.EngineStats;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

public class ServerIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/cog/tcp/streams/network/rfc793")
        .addScriptRoot("app", "io/aklivity/zilla/specs/cog/tcp/streams/application/rfc793");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(8192)
        .configure(TCP_MAX_CONNECTIONS, 3)
        .configure(ENGINE_DRAIN_ON_CLOSE, false)
        .configurationRoot("io/aklivity/zilla/specs/cog/tcp/config")
        .external("app#0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("server.json")
    @Specification({
        "${app}/client.and.server.sent.data.multiple.frames/server",
        "${net}/client.and.server.sent.data.multiple.frames/client"
    })
    public void shouldSendAndReceiveData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${app}/client.and.server.sent.data.with.padding/server",
        "${net}/client.and.server.sent.data.with.padding/client"
    })
    public void shouldSendAndReceiveDataWithPadding() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${app}/client.close/server",
        "${net}/client.close/client"
    })
    public void shouldInitiateClientClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${app}/client.sent.data/server",
        "${net}/client.sent.data/client"
    })
    public void shouldReceiveClientSentData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${app}/client.sent.data/server",
        "${net}/client.sent.data/client"
    })
    @ScriptProperty("serverInitialWindow \"6\"")
    public void shouldReceiveClientSentDataWithFlowControl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${app}/client.sent.data.multiple.frames/server",
        "${net}/client.sent.data.multiple.frames/client"
    })
    public void shouldReceiveClientSentDataMultipleFrames() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${app}/client.sent.data.multiple.streams/server",
        "${net}/client.sent.data.multiple.streams/client"
    })
    public void shouldReceiveClientSentDataMultipleStreams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${app}/client.sent.data.then.end/server"
        // No support for "write close" in k3po tcp
    })
    public void shouldReceiveClientSentDataAndEnd() throws Exception
    {
        k3po.start();

        try (SocketChannel channel = SocketChannel.open())
        {
            channel.connect(new InetSocketAddress("127.0.0.1", 8080));
            channel.write(UTF_8.encode("client data"));
            channel.shutdownOutput();

            k3po.finish();
        }
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${app}/client.sent.end.then.received.data/server"
        // No support for "write close" in k3po tcp
    })
    public void shouldWriteDataAfterReceiveEnd() throws Exception
    {
        k3po.start();

        try (SocketChannel channel = SocketChannel.open())
        {
            channel.connect(new InetSocketAddress("127.0.0.1", 8080));
            channel.shutdownOutput();

            ByteBuffer buf = ByteBuffer.allocate(256);
            channel.read(buf);
            buf.flip();

            assertEquals("server data", UTF_8.decode(buf).toString());

            k3po.finish();
        }
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${app}/concurrent.connections/server",
        "${net}/concurrent.connections/client"
    })
    public void shouldEstablishConcurrentFullDuplexConnection() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${app}/connection.established/server",
        "${net}/connection.established/client"
    })
    public void shouldEstablishConnection() throws Exception
    {
        k3po.finish();
    }

    @Ignore("GitHub Actions")
    @Test
    @Configuration("server.json")
    @Specification({
        "${app}/connection.established/server",
        "${net}/connection.established/client"
    })
    @ScriptProperty("address \"tcp://0.0.0.0:8080\"")
    public void shouldEstablishConnectionToAddressAnyIPv4() throws Exception
    {
        k3po.finish();
    }

    @Ignore("GitHub Actions")
    @Test
    @Configuration("server.ipv6.json")
    @Specification({
        "${app}/connection.established.ipv6/server",
        "${net}/connection.established/client"
    })
    @ScriptProperty("address \"tcp://[::0]:8080\"")
    public void shouldEstablishConnectionToAddressAnyIPv6() throws Exception
    {
        k3po.finish();
    }

    @Test(expected = IOException.class)
    @Configuration("server.json")
    @Specification({
        "${app}/connection.failed/server"
    })
    public void connectionFailed() throws Exception
    {
        k3po.start();

        try (SocketChannel channel = SocketChannel.open())
        {
            channel.connect(new InetSocketAddress("127.0.0.1", 8080));

            ByteBuffer buf = ByteBuffer.allocate(256);
            try
            {
                channel.read(buf);
            }
            finally
            {
                k3po.finish();
            }
        }
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${app}/server.close/server",
        "${net}/server.close/client"
    })
    public void shouldInitiateServerClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${app}/server.sent.data/server",
        "${net}/server.sent.data/client"
    })
    public void shouldReceiveServerSentData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${app}/server.sent.data/server"
    })
    public void shouldNotGetRepeatedIOExceptionsFromReaderStreamRead() throws Exception
    {
        k3po.start();

        try (Socket socket = new Socket("127.0.0.1", 8080))
        {
            socket.shutdownInput();
            Thread.sleep(500);
        }

        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${app}/server.sent.data.multiple.frames/server",
        "${net}/server.sent.data.multiple.frames/client"
    })
    public void shouldReceiveServerSentDataMultipleFrames() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${app}/server.sent.data.multiple.streams/server",
        "${net}/server.sent.data.multiple.streams/client"
    })
    public void shouldReceiveServerSentDataMultipleStreams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${app}/server.sent.data.then.end/server"
        // No support for "read closed" in k3po tcp
    })
    public void shouldReceiveServerSentDataAndEnd() throws Exception
    {
        k3po.start();

        try (SocketChannel channel = SocketChannel.open())
        {
            channel.connect(new InetSocketAddress("127.0.0.1", 8080));

            ByteBuffer buf = ByteBuffer.allocate(256);
            channel.read(buf);
            buf.flip();

            assertEquals("server data", UTF_8.decode(buf).toString());

            buf.rewind();
            int len = channel.read(buf);

            assertEquals(-1, len);

            k3po.finish();
        }
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${app}/server.sent.end.then.received.data/server"
        // No support for "read closed" in k3po tcp
    })
    public void shouldReceiveDataAfterSendingEnd() throws Exception
    {
        k3po.start();

        try (SocketChannel channel = SocketChannel.open())
        {
            channel.connect(new InetSocketAddress("127.0.0.1", 8080));

            ByteBuffer buf = ByteBuffer.allocate(256);
            int len = channel.read(buf);
            buf.flip();

            assertEquals(-1, len);

            channel.write(UTF_8.encode("client data"));

            k3po.finish();
        }
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${app}/max.connections/server"
    })
    public void shouldUnbindRebind() throws Exception
    {
        k3po.start();

        SocketChannel channel1 = SocketChannel.open();
        channel1.connect(new InetSocketAddress("127.0.0.1", 8080));

        SocketChannel channel2 = SocketChannel.open();
        channel2.connect(new InetSocketAddress("127.0.0.1", 8080));

        SocketChannel channel3 = SocketChannel.open();
        channel3.connect(new InetSocketAddress("127.0.0.1", 8080));

        k3po.awaitBarrier("CONNECTION_ACCEPTED_1");
        k3po.awaitBarrier("CONNECTION_ACCEPTED_2");
        k3po.awaitBarrier("CONNECTION_ACCEPTED_3");

        EngineStats stats = engine.stats("default", "net#0");

        assertEquals(3, stats.initialOpens());
        assertEquals(0, stats.initialCloses());
        assertEquals(3, stats.replyOpens());
        assertEquals(0, stats.replyCloses());

        SocketChannel channel4 = SocketChannel.open();
        try
        {
            channel4.connect(new InetSocketAddress("127.0.0.1", 8080));
            fail("4th connect shouldn't succeed as max.connections = 3");
        }
        catch (IOException ioe)
        {
            // expected
        }

        assertEquals(3, stats.initialOpens());
        assertEquals(0, stats.initialCloses());
        assertEquals(3, stats.replyOpens());
        assertEquals(0, stats.replyCloses());

        channel1.close();
        channel4.close();

        k3po.awaitBarrier("CLOSED");

        // sleep so that rebind happens
        Thread.sleep(200);
        assertEquals(3, stats.initialOpens());
        assertEquals(1, stats.initialCloses());
        assertEquals(3, stats.replyOpens());
        assertEquals(1, stats.replyCloses());

        SocketChannel channel5 = SocketChannel.open();
        channel5.connect(new InetSocketAddress("127.0.0.1", 8080));
        k3po.awaitBarrier("CONNECTION_ACCEPTED_4");
        assertEquals(4, stats.initialOpens());
        assertEquals(1, stats.initialCloses());
        assertEquals(4, stats.replyOpens());
        assertEquals(1, stats.replyCloses());

        channel2.close();
        channel3.close();
        channel5.close();
        Thread.sleep(500);
        assertEquals(4, stats.initialOpens());
        assertEquals(4, stats.initialCloses());
        assertEquals(4, stats.replyOpens());
        assertEquals(4, stats.replyCloses());

        k3po.finish();
    }
}
