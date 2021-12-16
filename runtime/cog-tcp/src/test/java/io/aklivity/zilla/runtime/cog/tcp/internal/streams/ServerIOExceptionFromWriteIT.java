/*
 * Copyright 2021-2021 Aklivity Inc.
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

import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.IntStream.generate;
import static org.junit.rules.RuleChain.outerRule;

import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.BMRules;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

import io.aklivity.zilla.runtime.cog.tcp.internal.SocketChannelHelper;
import io.aklivity.zilla.runtime.cog.tcp.internal.SocketChannelHelper.OnDataHelper;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

@RunWith(org.jboss.byteman.contrib.bmunit.BMUnitRunner.class)
public class ServerIOExceptionFromWriteIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("server", "io/aklivity/zilla/specs/cog/tcp/streams/application/rfc793");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/cog/tcp/config")
        .external("app#0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(SocketChannelHelper.RULE).around(engine).around(k3po).around(timeout);

    @Test
    @Configuration("server.json")
    @Specification({
        "${server}/server.sent.data.received.reset.and.abort/server"
    })
    @BMRule(name = "onApplicationData",
        targetClass = "^java.nio.channels.SocketChannel",
        targetMethod = "write(java.nio.ByteBuffer)",
        condition = "callerEquals(\"TcpServerFactory$TcpServer.onAppData\", true, 2)",
        action = "throw new IOException(\"Simulating an IOException from write\")"
    )
    public void shouldResetWhenImmediateWriteThrowsIOException() throws Exception
    {
        k3po.start();

        try (SocketChannel channel = SocketChannel.open())
        {
            channel.connect(new InetSocketAddress("127.0.0.1", 8080));

            k3po.finish();
        }
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${server}/server.sent.data.received.reset.and.abort/server"
    })
    @BMRules(rules = {
        @BMRule(name = "onApplicationData",
        helper = "io.aklivity.zilla.runtime.cog.tcp.internal.SocketChannelHelper$OnDataHelper",
        targetClass = "^java.nio.channels.SocketChannel",
        targetMethod = "write(java.nio.ByteBuffer)",
        condition = "callerEquals(\"TcpServerFactory$TcpServer.onAppData\", true, 2)",
        action = "return doWrite($0, $1);"
        ),
        @BMRule(name = "onNetworkWritable",
        targetClass = "^java.nio.channels.SocketChannel",
        targetMethod = "write(java.nio.ByteBuffer)",
        condition = "callerEquals(\"TcpServerFactory$TcpServer.onNetWritable\", true, 2)",
        action = "throw new IOException(\"Simulating an IOException from write\")"
        )
    })
    public void shouldResetWhenDeferredWriteThrowsIOException() throws Exception
    {
        OnDataHelper.fragmentWrites(generate(() -> 0));
        k3po.start();

        try (SocketChannel channel = SocketChannel.open())
        {
            channel.connect(new InetSocketAddress("127.0.0.1", 8080));

            k3po.finish();
        }
    }

}
