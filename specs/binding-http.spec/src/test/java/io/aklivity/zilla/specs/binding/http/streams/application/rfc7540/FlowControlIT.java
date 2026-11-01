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
package io.aklivity.zilla.specs.binding.http.streams.application.rfc7540;

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
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/http/streams/application/rfc7540/flow.control");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/stream.flow/client",
        "${app}/stream.flow/server"
    })
    public void streamFlow() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.rst.stream.last.frame/client",
        "${app}/client.rst.stream.last.frame/server"
    })
    public void clientRstStreamLastFrame() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.sent.100k.message/client",
        "${app}/client.sent.100k.message/server"
    })
    public void clientSent100kMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/server.sent.100k.message/client",
        "${app}/server.sent.100k.message/server",
    })
    public void serverSent100kMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connection.window.bounded/client",
        "${app}/connection.window.bounded/server",
    })
    public void connectionWindowBounded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.sent.connection.window.topup/client",
        "${app}/client.sent.connection.window.topup/server",
    })
    public void shouldClientSentConnectionWindowTopup() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/server.sent.connection.window.topup/client",
        "${app}/server.sent.connection.window.topup/server",
    })
    public void shouldServerSentConnectionWindowTopup() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/server.sent.unknown.stream.connection.window.topup/client",
        "${app}/server.sent.unknown.stream.connection.window.topup/server",
    })
    public void shouldServerSentUnknownStreamConnectionWindowTopup() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.sent.padded.connection.window.topup/client",
        "${app}/client.sent.padded.connection.window.topup/server",
    })
    public void shouldClientSentPaddedConnectionWindowTopup() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/server.sent.padded.connection.window.topup/client",
        "${app}/server.sent.padded.connection.window.topup/server",
    })
    public void shouldServerSentPaddedConnectionWindowTopup() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.sent.padding.only.stream.window.update/client",
        "${app}/client.sent.padding.only.stream.window.update/server",
    })
    public void shouldClientSentPaddingOnlyStreamWindowUpdate() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/server.sent.padding.only.stream.window.update/client",
        "${app}/server.sent.padding.only.stream.window.update/server",
    })
    public void shouldServerSentPaddingOnlyStreamWindowUpdate() throws Exception
    {
        k3po.finish();
    }
}
