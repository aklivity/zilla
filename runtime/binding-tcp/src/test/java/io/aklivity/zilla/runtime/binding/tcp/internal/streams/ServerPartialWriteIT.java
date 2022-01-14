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
package io.aklivity.zilla.runtime.binding.tcp.internal.streams;

import static io.aklivity.zilla.runtime.binding.tcp.internal.SocketChannelHelper.ALL;
import static io.aklivity.zilla.runtime.binding.tcp.internal.TcpBinding.WRITE_SPIN_COUNT;
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

import io.aklivity.zilla.runtime.binding.tcp.internal.SocketChannelHelper;
import io.aklivity.zilla.runtime.binding.tcp.internal.SocketChannelHelper.HandleWriteHelper;
import io.aklivity.zilla.runtime.binding.tcp.internal.SocketChannelHelper.OnDataHelper;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

@RunWith(org.jboss.byteman.contrib.bmunit.BMUnitRunner.class)
@BMUnitConfig(loadDirectory = "src/test/resources")
@BMScript(value = "SocketChannelHelper.btm")
public class ServerPartialWriteIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("client", "io/aklivity/zilla/specs/binding/tcp/streams/network/rfc793")
        .addScriptRoot("server", "io/aklivity/zilla/specs/binding/tcp/streams/application/rfc793");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/tcp/config")
        .external("app0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(SocketChannelHelper.RULE).around(engine).around(k3po).around(timeout);

    @Test
    @Configuration("server.json")
    @Specification({
        "${server}/server.sent.data/server",
        "${client}/server.sent.data/client"
    })
    public void shouldSpinWrite() throws Exception
    {
        OnDataHelper.fragmentWrites(generate(() -> 0).limit(WRITE_SPIN_COUNT - 1));
        HandleWriteHelper.fragmentWrites(generate(() -> 0));
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${server}/server.sent.data/server",
        "${client}/server.sent.data/client"
    })
    public void shouldFinishWriteWhenSocketIsWritableAgain() throws Exception
    {
        OnDataHelper.fragmentWrites(IntStream.of(5));
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${server}/server.sent.data/server",
        "${client}/server.sent.data/client"
    })
    public void shouldHandleMultiplePartialWrites() throws Exception
    {
        OnDataHelper.fragmentWrites(IntStream.of(2));
        HandleWriteHelper.fragmentWrites(IntStream.of(3, 1));
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${server}/server.sent.data.multiple.frames/server",
        "${client}/server.sent.data.multiple.frames/client"
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
    @Configuration("server.json")
    @Specification({
        "${server}/server.sent.data.multiple.frames/server",
        "${client}/server.sent.data.multiple.frames/client"
    })
    public void shouldPartiallyWriteWhenMoreDataArrivesWhileAwaitingSocketWritable() throws Exception
    {
        // processData will be called for each of the two data frames. Make the first and second
        // each do a partial write, then write nothing until handleWrite is called after the
        // second processData call, when we write everything.
        AtomicBoolean finishWrite = new AtomicBoolean(false);

        OnDataHelper.fragmentWrites(concat(of(5), generate(() -> finishWrite.getAndSet(true) ? 0 : 15)));
        HandleWriteHelper.fragmentWrites(generate(() -> finishWrite.get() ? ALL : 0));

        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${server}/server.sent.data.then.end/server"
    })
    public void shouldHandleEndOfStreamWithPendingWrite() throws Exception
    {
        AtomicBoolean endWritten = new AtomicBoolean(false);
        OnDataHelper.fragmentWrites(concat(of(5), generate(() -> 0)));
        HandleWriteHelper.fragmentWrites(generate(() -> endWritten.get() ? ALL : 0));

        k3po.start();

        try (SocketChannel channel = SocketChannel.open())
        {
            channel.connect(new InetSocketAddress("127.0.0.1", 8080));

            k3po.awaitBarrier("END_WRITTEN");
            endWritten.set(true);

            ByteBuffer buf = ByteBuffer.allocate("server data".length() + 10);
            boolean closed = false;
            do
            {
                int len = channel.read(buf);
                if (len == -1)
                {
                    closed = true;
                    break;
                }
            } while (buf.position() < "server data".length());
            buf.flip();

            assertEquals("server data", UTF_8.decode(buf).toString());

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
