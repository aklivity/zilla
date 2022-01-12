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

import static java.net.StandardSocketOptions.SO_REUSEADDR;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.rules.RuleChain.outerRule;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.ScriptProperty;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

public class ClientIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/cog/tcp/streams/network/rfc793")
        .addScriptRoot("app", "io/aklivity/zilla/specs/cog/tcp/streams/application/rfc793");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(4096)
        .configurationRoot("io/aklivity/zilla/specs/cog/tcp/config")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("client.host.json")
    @Specification({
        "${app}/client.and.server.sent.data.multiple.frames/client",
        "${net}/client.and.server.sent.data.multiple.frames/server"
    })
    public void shouldSendAndReceiveData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.host.json")
    @Specification({
        "${app}/client.and.server.sent.data.with.padding/client",
        "${net}/client.and.server.sent.data.with.padding/server"
    })
    public void shouldSendAndReceiveDataWithPadding() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.host.json")
    @Specification({
        "${app}/client.close/client",
        "${net}/client.close/server"
    })
    public void shouldInitiateClientClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.host.json")
    @Specification({
        "${app}/client.sent.data/client",
        "${net}/client.sent.data/server"
    })
    public void shouldReceiveClientSentData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.host.json")
    @Specification({
        "${app}/client.sent.data.multiple.frames/client",
        "${net}/client.sent.data.multiple.frames/server"
    })
    public void shouldReceiveClientSentDataMultipleFrames() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.host.json")
    @Specification({
        "${app}/client.sent.data.multiple.streams/client",
        "${net}/client.sent.data.multiple.streams/server"
    })
    public void shouldReceiveClientSentDataMultipleStreams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.host.json")
    @Specification({
        "${app}/client.sent.data.then.end/client"
        // No support for "read closed" in k3po tcp
    })
    public void shouldReceiveClientSentDataAndEnd() throws Exception
    {
        try (ServerSocketChannel server = ServerSocketChannel.open())
        {
            server.setOption(SO_REUSEADDR, true);
            server.bind(new InetSocketAddress("127.0.0.1", 8080));

            k3po.start();

            try (SocketChannel channel = server.accept())
            {
                ByteBuffer buf = ByteBuffer.allocate(256);
                channel.read(buf);
                buf.flip();
                assertEquals("client data", UTF_8.decode(buf).toString());

                buf.rewind();
                int len = channel.read(buf);

                assertEquals(-1, len);

                k3po.finish();
            }
        }
    }

    @Test
    @Configuration("client.host.json")
    @Specification({
        "${app}/client.sent.end.then.received.data/client"
        // No support for "read closed" in k3po tcp
    })
    public void shouldWriteDataAfterReceivingEndOfRead() throws Exception
    {
        try (ServerSocketChannel server = ServerSocketChannel.open())
        {
            server.setOption(SO_REUSEADDR, true);
            server.bind(new InetSocketAddress("127.0.0.1", 8080));

            k3po.start();

            try (SocketChannel channel = server.accept())
            {
                ByteBuffer buf = ByteBuffer.allocate(256);
                int len = channel.read(buf);

                assertEquals(-1, len);

                channel.write(UTF_8.encode("server data"));

                k3po.finish();
            }
        }
    }

    @Test
    @Configuration("client.host.json")
    @Specification({
        "${app}/connection.established/client",
        "${net}/connection.established/server"
    })
    public void shouldEstablishConnection() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.ipv6.json")
    @Specification({
        "${app}/connection.established.ipv6/client",
        "${net}/connection.established/server"
    })
    @ScriptProperty("address \"tcp://[::1]:8080\"")
    public void shouldEstablishConnectionIPv6() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.host.json")
    @Specification({
        "${app}/connection.failed/client"
    })
    public void connnectionFailed() throws Exception
    {
        k3po.finish();
        Thread.sleep(250); // TODO: engine quiesce instead of close
        assertEquals(1, engine.stats("default", "app0").initialErrors());
    }

    @Test
    @Configuration("client.host.json")
    @Specification({
        "${app}/server.close/client",
        "${net}/server.close/server"
    })
    public void shouldInitiateServerClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.host.json")
    @Specification({
        "${app}/server.sent.data/client",
        "${net}/server.sent.data/server"
    })
    public void shouldReceiveServerSentData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.host.json")
    @Specification({
        "${app}/server.sent.data/client",
        "${net}/server.sent.data/server"
    })
    @ScriptProperty("clientInitialWindow \"6\"")
    public void shouldReceiveServerSentDataWithFlowControl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.host.json")
    @Specification({
        "${app}/server.sent.data.multiple.frames/client",
        "${net}/server.sent.data.multiple.frames/server"
    })
    public void shouldReceiveServerSentDataMultipleFrames() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.host.json")
    @Specification({
        "${app}/server.sent.data.multiple.streams/client",
        "${net}/server.sent.data.multiple.streams/server"
    })
    public void shouldReceiveServerSentDataMultipleStreams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.host.json")
    @Specification({
        "${app}/server.sent.data.then.end/client"
        // No support for half close output in k3po tcp
    })
    public void shouldReceiveServerSentDataAndEnd() throws Exception
    {
        try (ServerSocketChannel server = ServerSocketChannel.open())
        {
            server.setOption(SO_REUSEADDR, true);
            server.bind(new InetSocketAddress("127.0.0.1", 8080));

            k3po.start();

            try (SocketChannel channel = server.accept())
            {
                channel.write(UTF_8.encode("server data"));

                channel.shutdownOutput();

                k3po.finish();
            }
        }
    }

    @Test
    @Configuration("client.host.json")
    @Specification({
        "${app}/server.sent.end.then.received.data/client"
        // No support for "write close" in k3po tcp
    })
    public void shouldWriteDataAfterReceiveEnd() throws Exception
    {
        try (ServerSocketChannel server = ServerSocketChannel.open())
        {
            server.setOption(SO_REUSEADDR, true);
            server.bind(new InetSocketAddress("127.0.0.1", 8080));

            k3po.start();

            try (SocketChannel channel = server.accept())
            {
                channel.shutdownOutput();

                ByteBuffer buf = ByteBuffer.allocate(256);
                channel.read(buf);
                buf.flip();

                assertEquals("client data", UTF_8.decode(buf).toString());

                k3po.finish();
            }
        }
    }
}
