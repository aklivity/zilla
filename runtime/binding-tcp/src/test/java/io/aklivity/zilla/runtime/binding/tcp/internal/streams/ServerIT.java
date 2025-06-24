/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.tcp.internal.streams;

import static io.aklivity.zilla.runtime.engine.test.EngineRule.ENGINE_WORKER_CAPACITY_NAME;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.rules.RuleChain.outerRule;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.function.LongSupplier;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.ScriptProperty;
import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;
import io.aklivity.zilla.runtime.engine.test.annotation.Configure;

public class ServerIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/tcp/streams/network/rfc793")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/tcp/streams/application/rfc793");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/tcp/config")
        .external("app0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${app}/client.and.server.sent.data.multiple.frames/server",
        "${net}/client.and.server.sent.data.multiple.frames/client"
    })
    public void shouldSendAndReceiveData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${app}/client.and.server.sent.data.with.padding/server",
        "${net}/client.and.server.sent.data.with.padding/client"
    })
    public void shouldSendAndReceiveDataWithPadding() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${app}/client.close/server",
        "${net}/client.close/client"
    })
    public void shouldInitiateClientClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${app}/client.sent.data/server",
        "${net}/client.sent.data/client"
    })
    public void shouldReceiveClientSentData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
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
    @Configuration("server.yaml")
    @Specification({
        "${app}/client.sent.data.multiple.frames/server",
        "${net}/client.sent.data.multiple.frames/client"
    })
    public void shouldReceiveClientSentDataMultipleFrames() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${app}/client.sent.data.multiple.streams/server",
        "${net}/client.sent.data.multiple.streams/client"
    })
    public void shouldReceiveClientSentDataMultipleStreams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${app}/client.sent.data.then.end/server"
        // No support for "write close" in k3po tcp
    })
    public void shouldReceiveClientSentDataAndEnd() throws Exception
    {
        k3po.start();

        try (SocketChannel channel = SocketChannel.open())
        {
            channel.connect(new InetSocketAddress("127.0.0.1", 12345));
            channel.write(UTF_8.encode("client data"));
            channel.shutdownOutput();

            k3po.finish();
        }
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${app}/client.sent.end.then.received.data/server"
        // No support for "write close" in k3po tcp
    })
    public void shouldWriteDataAfterReceiveEnd() throws Exception
    {
        k3po.start();

        try (SocketChannel channel = SocketChannel.open())
        {
            channel.connect(new InetSocketAddress("127.0.0.1", 12345));
            channel.shutdownOutput();

            ByteBuffer buf = ByteBuffer.allocate(256);
            channel.read(buf);
            buf.flip();

            assertEquals("server data", UTF_8.decode(buf).toString());

            k3po.finish();
        }
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${app}/concurrent.connections/server",
        "${net}/concurrent.connections/client"
    })
    public void shouldEstablishConcurrentFullDuplexConnection() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
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
    @Configuration("server.yaml")
    @Specification({
        "${app}/connection.established/server",
        "${net}/connection.established/client"
    })
    @ScriptProperty("address \"tcp://0.0.0.0:12345\"")
    public void shouldEstablishConnectionToAddressAnyIPv4() throws Exception
    {
        k3po.finish();
    }

    @Ignore("GitHub Actions")
    @Test
    @Configuration("server.ipv6.yaml")
    @Specification({
        "${app}/connection.established.ipv6/server",
        "${net}/connection.established/client"
    })
    @ScriptProperty("address \"tcp://[::0]:12345\"")
    public void shouldEstablishConnectionToAddressAnyIPv6() throws Exception
    {
        k3po.finish();
    }

    @Test(expected = IOException.class)
    @Configuration("server.yaml")
    @Specification({
        "${app}/connection.failed/server"
    })
    public void connectionFailed() throws Exception
    {
        k3po.start();

        try (SocketChannel channel = SocketChannel.open())
        {
            channel.connect(new InetSocketAddress("127.0.0.1", 12345));

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
    @Configuration("server.yaml")
    @Specification({
        "${app}/server.close/server",
        "${net}/server.close/client"
    })
    public void shouldInitiateServerClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${app}/server.sent.data/server",
        "${net}/server.sent.data/client"
    })
    public void shouldReceiveServerSentData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${app}/server.sent.data/server"
    })
    public void shouldNotGetRepeatedIOExceptionsFromReaderStreamRead() throws Exception
    {
        k3po.start();

        try (Socket socket = new Socket("127.0.0.1", 12345))
        {
            socket.shutdownInput();
            Thread.sleep(500);
        }

        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${app}/server.sent.data.multiple.frames/server",
        "${net}/server.sent.data.multiple.frames/client"
    })
    public void shouldReceiveServerSentDataMultipleFrames() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${app}/server.sent.data.multiple.streams/server",
        "${net}/server.sent.data.multiple.streams/client"
    })
    public void shouldReceiveServerSentDataMultipleStreams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${app}/server.sent.data.then.end/server"
        // No support for "read closed" in k3po tcp
    })
    public void shouldReceiveServerSentDataAndEnd() throws Exception
    {
        k3po.start();

        try (SocketChannel channel = SocketChannel.open())
        {
            channel.connect(new InetSocketAddress("127.0.0.1", 12345));

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
    @Configuration("server.yaml")
    @Specification({
        "${app}/server.sent.end.then.received.data/server"
        // No support for "read closed" in k3po tcp
    })
    public void shouldReceiveDataAfterSendingEnd() throws Exception
    {
        k3po.start();

        try (SocketChannel channel = SocketChannel.open())
        {
            channel.connect(new InetSocketAddress("127.0.0.1", 12345));

            ByteBuffer buf = ByteBuffer.allocate(256);
            int len = channel.read(buf);
            buf.flip();

            assertEquals(-1, len);

            channel.write(UTF_8.encode("client data"));

            k3po.finish();
        }
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${app}/max.connections/server"
    })
    @Configure(name = ENGINE_WORKER_CAPACITY_NAME, value = "4")
    public void shouldRejectOnConnectionLimit() throws Exception
    {
        final LongSupplier utilization = engine.utilization();

        k3po.start();

        SocketChannel client1 = SocketChannel.open();
        client1.connect(new InetSocketAddress("127.0.0.1", 12345));

        SocketChannel client2 = SocketChannel.open();
        client2.connect(new InetSocketAddress("127.0.0.1", 12345));

        SocketChannel client3 = SocketChannel.open();
        client3.connect(new InetSocketAddress("127.0.0.1", 12345));

        SocketChannel client4 = SocketChannel.open();
        client4.connect(new InetSocketAddress("127.0.0.1", 12345));

        k3po.awaitBarrier("CONNECTION_ACCEPTED_1");
        k3po.awaitBarrier("CONNECTION_ACCEPTED_2");
        k3po.awaitBarrier("CONNECTION_ACCEPTED_3");
        k3po.awaitBarrier("CONNECTION_ACCEPTED_4");

        while (utilization.getAsLong() != 100L)
        {
            Thread.onSpinWait();
        }

        SocketChannel client5 = SocketChannel.open();
        try
        {
            client5.connect(new InetSocketAddress("127.0.0.1", 12345));
            ByteBuffer buf = ByteBuffer.allocate(1);
            assertTrue(client5.read(buf) == -1);
            client5.close();
        }
        catch (IOException ioe)
        {
            ioe.printStackTrace();
            // expected, engine at capacity
        }

        client1.close();
        client2.close();
        client3.close();
        client4.close();

        k3po.awaitBarrier("CLOSED");

        while (utilization.getAsLong() != 0L)
        {
            Thread.onSpinWait();
        }

        SocketChannel client6 = SocketChannel.open();
        client6.connect(new InetSocketAddress("127.0.0.1", 12345));

        SocketChannel client7 = SocketChannel.open();
        client7.connect(new InetSocketAddress("127.0.0.1", 12345));

        SocketChannel client8 = SocketChannel.open();
        client8.connect(new InetSocketAddress("127.0.0.1", 12345));

        SocketChannel client9 = SocketChannel.open();
        client9.connect(new InetSocketAddress("127.0.0.1", 12345));

        k3po.awaitBarrier("CONNECTION_ACCEPTED_6");
        k3po.awaitBarrier("CONNECTION_ACCEPTED_7");
        k3po.awaitBarrier("CONNECTION_ACCEPTED_8");
        k3po.awaitBarrier("CONNECTION_ACCEPTED_9");

        while (utilization.getAsLong() != 100L)
        {
            Thread.onSpinWait();
        }

        client6.close();
        client7.close();
        client8.close();
        client9.close();

        k3po.finish();
    }
}
