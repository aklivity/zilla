/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.specs.binding.ws.streams.network;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

/**
 * RFC-6455, section 5.8 "Extensibility"
 */
public class ExtensibilityIT
{
    private final K3poRule k3po = new K3poRule()
        .setScriptRoot("io/aklivity/zilla/specs/binding/ws/streams/network/extensibility");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "client.send.text.frame.with.rsv.1/handshake.request.and.frame",
        "client.send.text.frame.with.rsv.1/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendTextFrameWithRsv1()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.binary.frame.with.rsv.1/handshake.request.and.frame",
        "client.send.binary.frame.with.rsv.1/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendBinaryFrameWithRsv1()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.close.frame.with.rsv.1/handshake.request.and.frame",
        "client.send.close.frame.with.rsv.1/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendCloseFrameWithRsv1()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.ping.frame.with.rsv.1/handshake.request.and.frame",
        "client.send.ping.frame.with.rsv.1/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendPingFrameWithRsv1()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.pong.frame.with.rsv.1/handshake.request.and.frame",
        "client.send.pong.frame.with.rsv.1/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendPongFrameWithRsv1()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.text.frame.with.rsv.2/handshake.request.and.frame",
        "client.send.text.frame.with.rsv.2/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendTextFrameWithRsv2()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.binary.frame.with.rsv.2/handshake.request.and.frame",
        "client.send.binary.frame.with.rsv.2/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendBinaryFrameWithRsv2()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.close.frame.with.rsv.2/handshake.request.and.frame",
        "client.send.close.frame.with.rsv.2/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendCloseFrameWithRsv2()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.ping.frame.with.rsv.2/handshake.request.and.frame",
        "client.send.ping.frame.with.rsv.2/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendPingFrameWithRsv2()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.pong.frame.with.rsv.2/handshake.request.and.frame",
        "client.send.pong.frame.with.rsv.2/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendPongFrameWithRsv2()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.text.frame.with.rsv.3/handshake.request.and.frame",
        "client.send.text.frame.with.rsv.3/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendTextFrameWithRsv3()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.binary.frame.with.rsv.3/handshake.request.and.frame",
        "client.send.binary.frame.with.rsv.3/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendBinaryFrameWithRsv3()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.close.frame.with.rsv.3/handshake.request.and.frame",
        "client.send.close.frame.with.rsv.3/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendCloseFrameWithRsv3()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.ping.frame.with.rsv.3/handshake.request.and.frame",
        "client.send.ping.frame.with.rsv.3/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendPingFrameWithRsv3()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.pong.frame.with.rsv.3/handshake.request.and.frame",
        "client.send.pong.frame.with.rsv.3/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendPongFrameWithRsv3()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.text.frame.with.rsv.4/handshake.request.and.frame",
        "client.send.text.frame.with.rsv.4/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendTextFrameWithRsv4()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.binary.frame.with.rsv.4/handshake.request.and.frame",
        "client.send.binary.frame.with.rsv.4/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendBinaryFrameWithRsv4()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.close.frame.with.rsv.4/handshake.request.and.frame",
        "client.send.close.frame.with.rsv.4/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendCloseFrameWithRsv4()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.ping.frame.with.rsv.4/handshake.request.and.frame",
        "client.send.ping.frame.with.rsv.4/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendPingFrameWithRsv4()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.pong.frame.with.rsv.4/handshake.request.and.frame",
        "client.send.pong.frame.with.rsv.4/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendPongFrameWithRsv4()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.text.frame.with.rsv.5/handshake.request.and.frame",
        "client.send.text.frame.with.rsv.5/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendTextFrameWithRsv5()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.binary.frame.with.rsv.5/handshake.request.and.frame",
        "client.send.binary.frame.with.rsv.5/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendBinaryFrameWithRsv5()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.close.frame.with.rsv.5/handshake.request.and.frame",
        "client.send.close.frame.with.rsv.5/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendCloseFrameWithRsv5()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.ping.frame.with.rsv.5/handshake.request.and.frame",
        "client.send.ping.frame.with.rsv.5/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendPingFrameWithRsv5()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.pong.frame.with.rsv.5/handshake.request.and.frame",
        "client.send.pong.frame.with.rsv.5/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendPongFrameWithRsv5()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.text.frame.with.rsv.6/handshake.request.and.frame",
        "client.send.text.frame.with.rsv.6/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendTextFrameWithRsv6()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.binary.frame.with.rsv.6/handshake.request.and.frame",
        "client.send.binary.frame.with.rsv.6/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendBinaryFrameWithRsv6()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.close.frame.with.rsv.6/handshake.request.and.frame",
        "client.send.close.frame.with.rsv.6/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendCloseFrameWithRsv6()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.ping.frame.with.rsv.6/handshake.request.and.frame",
        "client.send.ping.frame.with.rsv.6/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendPingFrameWithRsv6()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.pong.frame.with.rsv.6/handshake.request.and.frame",
        "client.send.pong.frame.with.rsv.6/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendPongFrameWithRsv6()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.text.frame.with.rsv.7/handshake.request.and.frame",
        "client.send.text.frame.with.rsv.7/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendTextFrameWithRsv7()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.binary.frame.with.rsv.7/handshake.request.and.frame",
        "client.send.binary.frame.with.rsv.7/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendBinaryFrameWithRsv7()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.close.frame.with.rsv.7/handshake.request.and.frame",
        "client.send.close.frame.with.rsv.7/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendCloseFrameWithRsv7()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.ping.frame.with.rsv.7/handshake.request.and.frame",
        "client.send.ping.frame.with.rsv.7/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendPingFrameWithRsv7()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.pong.frame.with.rsv.7/handshake.request.and.frame",
        "client.send.pong.frame.with.rsv.7/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendPongFrameWithRsv7()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.text.frame.with.rsv.1/handshake.request.and.frame",
        "server.send.text.frame.with.rsv.1/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendTextFrameWithRsv1()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.binary.frame.with.rsv.1/handshake.request.and.frame",
        "server.send.binary.frame.with.rsv.1/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendBinaryFrameWithRsv1()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.close.frame.with.rsv.1/handshake.request.and.frame",
        "server.send.close.frame.with.rsv.1/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendCloseFrameWithRsv1()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.ping.frame.with.rsv.1/handshake.request.and.frame",
        "server.send.ping.frame.with.rsv.1/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendPingFrameWithRsv1()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.pong.frame.with.rsv.1/handshake.request.and.frame",
        "server.send.pong.frame.with.rsv.1/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendPongFrameWithRsv1()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.text.frame.with.rsv.2/handshake.request.and.frame",
        "server.send.text.frame.with.rsv.2/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendTextFrameWithRsv2()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.binary.frame.with.rsv.2/handshake.request.and.frame",
        "server.send.binary.frame.with.rsv.2/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendBinaryFrameWithRsv2()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.close.frame.with.rsv.2/handshake.request.and.frame",
        "server.send.close.frame.with.rsv.2/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendCloseFrameWithRsv2()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.ping.frame.with.rsv.2/handshake.request.and.frame",
        "server.send.ping.frame.with.rsv.2/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendPingFrameWithRsv2()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.pong.frame.with.rsv.2/handshake.request.and.frame",
        "server.send.pong.frame.with.rsv.2/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendPongFrameWithRsv2()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.text.frame.with.rsv.3/handshake.request.and.frame",
        "server.send.text.frame.with.rsv.3/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendTextFrameWithRsv3()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.binary.frame.with.rsv.3/handshake.request.and.frame",
        "server.send.binary.frame.with.rsv.3/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendBinaryFrameWithRsv3()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.close.frame.with.rsv.3/handshake.request.and.frame",
        "server.send.close.frame.with.rsv.3/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendCloseFrameWithRsv3()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.ping.frame.with.rsv.3/handshake.request.and.frame",
        "server.send.ping.frame.with.rsv.3/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendPingFrameWithRsv3()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.pong.frame.with.rsv.3/handshake.request.and.frame",
        "server.send.pong.frame.with.rsv.3/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendPongFrameWithRsv3()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.text.frame.with.rsv.4/handshake.request.and.frame",
        "server.send.text.frame.with.rsv.4/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendTextFrameWithRsv4()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.binary.frame.with.rsv.4/handshake.request.and.frame",
        "server.send.binary.frame.with.rsv.4/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendBinaryFrameWithRsv4()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.close.frame.with.rsv.4/handshake.request.and.frame",
        "server.send.close.frame.with.rsv.4/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendCloseFrameWithRsv4()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.ping.frame.with.rsv.4/handshake.request.and.frame",
        "server.send.ping.frame.with.rsv.4/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendPingFrameWithRsv4()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.pong.frame.with.rsv.4/handshake.request.and.frame",
        "server.send.pong.frame.with.rsv.4/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendPongFrameWithRsv4()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.text.frame.with.rsv.5/handshake.request.and.frame",
        "server.send.text.frame.with.rsv.5/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendTextFrameWithRsv5()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.binary.frame.with.rsv.5/handshake.request.and.frame",
        "server.send.binary.frame.with.rsv.5/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendBinaryFrameWithRsv5()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.close.frame.with.rsv.5/handshake.request.and.frame",
        "server.send.close.frame.with.rsv.5/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendCloseFrameWithRsv5()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.ping.frame.with.rsv.5/handshake.request.and.frame",
        "server.send.ping.frame.with.rsv.5/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendPingFrameWithRsv5()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.pong.frame.with.rsv.5/handshake.request.and.frame",
        "server.send.pong.frame.with.rsv.5/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendPongFrameWithRsv5()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.text.frame.with.rsv.6/handshake.request.and.frame",
        "server.send.text.frame.with.rsv.6/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendTextFrameWithRsv6()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.binary.frame.with.rsv.6/handshake.request.and.frame",
        "server.send.binary.frame.with.rsv.6/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendBinaryFrameWithRsv6()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.close.frame.with.rsv.6/handshake.request.and.frame",
        "server.send.close.frame.with.rsv.6/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendCloseFrameWithRsv6()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.ping.frame.with.rsv.6/handshake.request.and.frame",
        "server.send.ping.frame.with.rsv.6/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendPingFrameWithRsv6()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.pong.frame.with.rsv.6/handshake.request.and.frame",
        "server.send.pong.frame.with.rsv.6/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendPongFrameWithRsv6()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.text.frame.with.rsv.7/handshake.request.and.frame",
        "server.send.text.frame.with.rsv.7/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendTextFrameWithRsv7()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.binary.frame.with.rsv.7/handshake.request.and.frame",
        "server.send.binary.frame.with.rsv.7/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendBinaryFrameWithRsv7()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.close.frame.with.rsv.7/handshake.request.and.frame",
        "server.send.close.frame.with.rsv.7/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendCloseFrameWithRsv7()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.ping.frame.with.rsv.7/handshake.request.and.frame",
        "server.send.ping.frame.with.rsv.7/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendPingFrameWithRsv7()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.pong.frame.with.rsv.7/handshake.request.and.frame",
        "server.send.pong.frame.with.rsv.7/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendPongFrameWithRsv7()
            throws Exception
    {
        k3po.finish();
    }
}
