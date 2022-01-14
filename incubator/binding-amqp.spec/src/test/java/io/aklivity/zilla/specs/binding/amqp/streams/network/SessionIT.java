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
package io.aklivity.zilla.specs.binding.amqp.streams.network;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

public class SessionIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/amqp/streams/network/session");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/begin.exchange/client",
        "${net}/begin.exchange/server"})
    public void shouldExchangeBegin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/end.exchange/client",
        "${net}/end.exchange/server"})
    public void shouldExchangeEnd() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/begin.channel.max.exceeded/client",
        "${net}/begin.channel.max.exceeded/server"})
    public void shouldRejectBeginWhenChannelMaxExceeded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/begin.then.close/client",
        "${net}/begin.then.close/server"})
    public void shouldExchangeBeginThenClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/begin.multiple.sessions/client",
        "${net}/begin.multiple.sessions/server"})
    public void shouldExchangeMultipleBegin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/incoming.window.exceeded/client",
        "${net}/incoming.window.exceeded/server"})
    public void shouldEndSessionWhenIncomingWindowExceeded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/send.to.client.multiple.sessions/client",
        "${net}/send.to.client.multiple.sessions/server"})
    public void shouldSendToClientMultipleSessions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.when.sessions.interleaved/client",
        "${net}/transfer.to.client.when.sessions.interleaved/server"})
    public void shouldTransferToClientWhenSessionsInterleaved() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.when.sessions.interleaved/client",
        "${net}/transfer.to.server.when.sessions.interleaved/server"})
    public void shouldTransferToServerWhenSessionsInterleaved() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.when.sessions.interleaved.and.max.frame.size.exceeded/client",
        "${net}/transfer.to.client.when.sessions.interleaved.and.max.frame.size.exceeded/server"})
    public void shouldTransferToClientWhenSessionsInterleavedAndMaxFrameSizeExceeded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.when.sessions.interleaved.and.fragmented/client",
        "${net}/transfer.to.client.when.sessions.interleaved.and.fragmented/server"})
    public void shouldTransferToClientWhenSessionsInterleavedAndFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.when.sessions.interleaved.and.fragmented/client",
        "${net}/transfer.to.server.when.sessions.interleaved.and.fragmented/server"})
    public void shouldTransferToServerWhenSessionsInterleavedAndFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/incoming.window.reduced/client",
        "${net}/incoming.window.reduced/server"})
    public void shouldHandleReducedIncomingWindow() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/end.exchange.simultaneous/client",
        "${net}/end.exchange.simultaneous/server"})
    public void shouldEndSessionSimultaneously() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/discard.after.end/client",
        "${net}/discard.after.end/server"})
    public void shouldDiscardInboundAfterOutboundEnd() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reject.errant.link/client",
        "${net}/reject.errant.link/server"})
    public void shouldRejectErrantLink() throws Exception
    {
        k3po.finish();
    }
}
