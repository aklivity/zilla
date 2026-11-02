/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.specs.binding.http.streams.network.rfc7540;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class FlowControlIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/http/streams/network/rfc7540/flow.control");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/client.stream.flow/client",
        "${net}/client.stream.flow/server",
    })
    public void clientStreamFlow() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.stream.flow/client",
        "${net}/server.stream.flow/server",
    })
    public void serverStreamFlow() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.sent.100k.message/client",
        "${net}/client.sent.100k.message/server",
    })
    public void clientSent100kMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.sent.100k.message/client",
        "${net}/server.sent.100k.message/server",
    })
    public void serverSent100kMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.rst.stream.last.frame/client",
        "${net}/client.rst.stream.last.frame/server"
    })
    public void clientRstStreamLastFrame() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.rst.stream.last.frame/client",
        "${net}/server.rst.stream.last.frame/server"
    })
    public void serverRstStreamLastFrame() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.connection.window.bounded/client",
        "${net}/server.connection.window.bounded/server",
    })
    public void serverConnectionWindowBounded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.connection.window.bounded/client",
        "${net}/client.connection.window.bounded/server",
    })
    public void clientConnectionWindowBounded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.sent.connection.window.topup/client",
        "${net}/client.sent.connection.window.topup/server",
    })
    public void shouldClientSentConnectionWindowTopup() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.sent.connection.window.topup/client",
        "${net}/server.sent.connection.window.topup/server",
    })
    public void shouldServerSentConnectionWindowTopup() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.sent.unknown.stream.connection.window.topup/client",
        "${net}/client.sent.unknown.stream.connection.window.topup/server",
    })
    public void shouldClientSentUnknownStreamConnectionWindowTopup() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.sent.unknown.stream.connection.window.topup/client",
        "${net}/server.sent.unknown.stream.connection.window.topup/server",
    })
    public void shouldServerSentUnknownStreamConnectionWindowTopup() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.sent.padded.connection.window.topup/client",
        "${net}/client.sent.padded.connection.window.topup/server",
    })
    public void shouldClientSentPaddedConnectionWindowTopup() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.sent.padded.connection.window.topup/client",
        "${net}/server.sent.padded.connection.window.topup/server",
    })
    public void shouldServerSentPaddedConnectionWindowTopup() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.sent.padding.only.stream.window.update/client",
        "${net}/client.sent.padding.only.stream.window.update/server",
    })
    public void shouldClientSentPaddingOnlyStreamWindowUpdate() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.sent.padding.only.stream.window.update/client",
        "${net}/server.sent.padding.only.stream.window.update/server",
    })
    public void shouldServerSentPaddingOnlyStreamWindowUpdate() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.sent.data.on.closed.stream.after.flow.control.error/client",
        "${net}/server.sent.data.on.closed.stream.after.flow.control.error/server"
    })
    public void shouldServerSentDataOnClosedStreamAfterFlowControlError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.sent.data.on.closed.stream.after.flow.control.error/client",
        "${net}/client.sent.data.on.closed.stream.after.flow.control.error/server"
    })
    public void shouldClientSentDataOnClosedStreamAfterFlowControlError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.sent.100k.message.up.front.window/client",
        "${net}/server.sent.100k.message.up.front.window/server"
    })
    public void shouldServerSent100kMessageUpFrontWindow() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.sent.second.stream.window.grants/client",
        "${net}/client.sent.second.stream.window.grants/server"
    })
    public void shouldClientSentSecondStreamWindowGrants() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.sent.second.stream.window.grants/client",
        "${net}/server.sent.second.stream.window.grants/server"
    })
    public void shouldServerSentSecondStreamWindowGrants() throws Exception
    {
        k3po.finish();
    }
}
