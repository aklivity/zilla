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

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_BUFFER_SLOT_CAPACITY;
import static java.net.StandardSocketOptions.SO_REUSEADDR;
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
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

public class ClientLimitsIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("client", "io/aklivity/zilla/specs/binding/tcp/streams/application/rfc793");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(4096)
        .configure(ENGINE_BUFFER_SLOT_CAPACITY, 16)
        .configurationRoot("io/aklivity/zilla/specs/binding/tcp/config")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("client.host.yaml")
    @Specification({
        "${client}/client.sent.data.received.reset/client"
    })
    public void shouldResetWhenWindowExceeded() throws Exception
    {
        try (ServerSocketChannel server = ServerSocketChannel.open())
        {
            server.setOption(SO_REUSEADDR, true);
            server.bind(new InetSocketAddress("127.0.0.1", 8080));

            k3po.start();

            try (SocketChannel channel = server.accept())
            {
                int len;
                ByteBuffer buf = ByteBuffer.allocate(256);

                len = channel.read(buf);

                assertEquals(-1, len);

                k3po.finish();
            }
        }
    }
}
