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
 * RFC-6455, section 5.5 "Control Frames"
 */
public class ControlIT
{
    private final K3poRule k3po = new K3poRule()
        .setScriptRoot("io/aklivity/zilla/specs/binding/ws/streams/network/control");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "client.send.close.payload.length.0/handshake.request.and.frame",
        "client.send.close.payload.length.0/handshake.response.and.frame" })
    public void shouldEchoClientCloseFrameWithEmptyPayload() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.close.payload.length.1/handshake.request.and.frame",
        "client.send.close.payload.length.1/handshake.response.and.frame" })
    public void shouldEchoClientCloseFrameWithPayloadSize1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.close.payload.length.125/handshake.request.and.frame",
        "client.send.close.payload.length.125/handshake.response.and.frame" })
    public void shouldEchoClientCloseFrameWithPayload() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.close.payload.length.126/handshake.request.and.frame",
        "client.send.close.payload.length.126/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendCloseFrameWithPayloadTooLong()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.ping.payload.length.0/handshake.request.and.frame",
        "client.send.ping.payload.length.0/handshake.response.and.frame" })
    public void shouldPongClientPingFrameWithEmptyPayload() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.ping.payload.length.125/handshake.request.and.frame",
        "client.send.ping.payload.length.125/handshake.response.and.frame" })
    public void shouldPongClientPingFrameWithPayload() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.ping.payload.length.126/handshake.request.and.frame",
        "client.send.ping.payload.length.126/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendPingFrameWithPayloadTooLong()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.pong.payload.length.0/handshake.request.and.frame",
        "client.send.pong.payload.length.0/handshake.response.and.frame" })
    public void shouldReceiveClientPongFrameWithEmptyPayload() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.pong.payload.length.125/handshake.request.and.frame",
        "client.send.pong.payload.length.125/handshake.response.and.frame" })
    public void shouldReceiveClientPongFrameWithPayload() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.pong.payload.length.126/handshake.request.and.frame",
        "client.send.pong.payload.length.126/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendPongFrameWithPayloadTooLong()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.opcode.0x0b/handshake.request.and.frame",
        "client.send.opcode.0x0b/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendOpcode11Frame()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.opcode.0x0c/handshake.request.and.frame",
        "client.send.opcode.0x0c/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendOpcode12Frame()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.opcode.0x0d/handshake.request.and.frame",
        "client.send.opcode.0x0d/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendOpcode13Frame()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.opcode.0x0e/handshake.request.and.frame",
        "client.send.opcode.0x0e/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendOpcode14Frame()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.opcode.0x0f/handshake.request.and.frame",
        "client.send.opcode.0x0f/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendOpcode15Frame()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.close.payload.length.0/handshake.request.and.frame",
        "server.send.close.payload.length.0/handshake.response.and.frame" })
    public void shouldEchoServerCloseFrameWithEmptyPayload() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.close.payload.length.125/handshake.request.and.frame",
        "server.send.close.payload.length.125/handshake.response.and.frame" })
    public void shouldEchoServerCloseFrameWithPayload() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.close.payload.length.126/handshake.request.and.frame",
        "server.send.close.payload.length.126/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendCloseFrameWithPayloadTooLong()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.ping.payload.length.0/handshake.request.and.frame",
        "server.send.ping.payload.length.0/handshake.response.and.frame" })
    public void shouldPongServerPingFrameWithEmptyPayload() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.ping.payload.length.125/handshake.request.and.frame",
        "server.send.ping.payload.length.125/handshake.response.and.frame" })
    public void shouldPongServerPingFrameWithPayload() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.ping.payload.length.126/handshake.request.and.frame",
        "server.send.ping.payload.length.126/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendPingFrameWithPayloadTooLong()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.pong.payload.length.0/handshake.request.and.frame",
        "server.send.pong.payload.length.0/handshake.response.and.frame" })
    public void shouldReceiveServerPongFrameWithEmptyPayload() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.pong.payload.length.125/handshake.request.and.frame",
        "server.send.pong.payload.length.125/handshake.response.and.frame" })
    public void shouldReceiveServerPongFrameWithPayload() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.pong.payload.length.126/handshake.request.and.frame",
        "server.send.pong.payload.length.126/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendPongFrameWithPayloadTooLong()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.opcode.0x0b/handshake.request.and.frame",
        "server.send.opcode.0x0b/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendOpcode11Frame()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.opcode.0x0c/handshake.request.and.frame",
        "server.send.opcode.0x0c/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendOpcode12Frame()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.opcode.0x0d/handshake.request.and.frame",
        "server.send.opcode.0x0d/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendOpcode13Frame()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.opcode.0x0e/handshake.request.and.frame",
        "server.send.opcode.0x0e/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendOpcode14Frame()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.opcode.0x0f/handshake.request.and.frame",
        "server.send.opcode.0x0f/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendOpcode15Frame()
            throws Exception
    {
        k3po.finish();
    }
}
