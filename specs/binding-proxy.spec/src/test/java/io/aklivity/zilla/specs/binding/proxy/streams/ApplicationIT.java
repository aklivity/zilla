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
package io.aklivity.zilla.specs.binding.proxy.streams;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

public class ApplicationIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/proxy/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/connected.local/client",
        "${app}/connected.local/server"})
    public void shouldConnectLocal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connected.local.discard/client",
        "${app}/connected.local.discard/server"})
    public void shouldConnectLocalDiscard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connected.local.client.sent.begin.ext/client",
        "${app}/connected.local.client.sent.begin.ext/server"})
    public void shouldConnectLocalClientSendsBeginExt() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connected.local.client.sent.data/client",
        "${app}/connected.local.client.sent.data/server"})
    public void shouldConnectLocalClientSendsData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connected.local.client.sent.flush/client",
        "${app}/connected.local.client.sent.flush/server"})
    public void shouldConnectLocalClientSendsFlush() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connected.local.client.sent.challenge/client",
        "${app}/connected.local.client.sent.challenge/server"})
    public void shouldConnectLocalClientSendsChallenge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connected.local.client.sent.close/client",
        "${app}/connected.local.client.sent.close/server"})
    public void shouldConnectLocalClientSendsClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connected.local.client.sent.abort/client",
        "${app}/connected.local.client.sent.abort/server"})
    public void shouldConnectLocalClientSendsAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connected.local.client.sent.reset/client",
        "${app}/connected.local.client.sent.reset/server"})
    public void shouldConnectLocalClientSendsReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connected.local.server.sent.data/client",
        "${app}/connected.local.server.sent.data/server"})
    public void shouldConnectLocalServerSendsData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connected.local.server.sent.flush/client",
        "${app}/connected.local.server.sent.flush/server"})
    public void shouldConnectLocalServerSendsFlush() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connected.local.server.sent.challenge/client",
        "${app}/connected.local.server.sent.challenge/server"})
    public void shouldConnectLocalServerSendsChallenge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connected.local.server.sent.close/client",
        "${app}/connected.local.server.sent.close/server"})
    public void shouldConnectLocalServerSendsClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connected.local.server.sent.abort/client",
        "${app}/connected.local.server.sent.abort/server"})
    public void shouldConnectLocalServerSendsAbort() throws Exception
    {
        k3po.finish();
    }
    @Test
    @Specification({
        "${app}/connected.local.server.sent.reset/client",
        "${app}/connected.local.server.sent.reset/server"})
    public void shouldConnectLocalServerSendsReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connected.tcp4/client",
        "${app}/connected.tcp4/server"})
    public void shouldConnectTcp4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connected.tcp4.alpn/client",
        "${app}/connected.tcp4.alpn/server"})
    public void shouldConnectTcp4WithAlpn() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connected.tcp4.authority/client",
        "${app}/connected.tcp4.authority/server"})
    public void shouldConnectTcp4WithAuthority() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connected.tcp4.crc32c/client",
        "${app}/connected.tcp4.crc32c/server"})
    public void shouldConnectTcp4WithCrc32c() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connected.tcp4.identity/client",
        "${app}/connected.tcp4.identity/server"})
    public void shouldConnectTcp4WithIdentity() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connected.tcp4.namespace/client",
        "${app}/connected.tcp4.namespace/server"})
    public void shouldConnectTcp4WithNamespace() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connected.tcp4.noop/client",
        "${app}/connected.tcp4.noop/server"})
    public void shouldConnectTcp4WithNoop() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connected.tcp4.ssl/client",
        "${app}/connected.tcp4.ssl/server"})
    public void shouldConnectTcp4WithSsl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connected.tcp4.ssl.client.cert/client",
        "${app}/connected.tcp4.ssl.client.cert/server"})
    public void shouldConnectTcp4WithSslClientCertificate() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connected.tcp4.ssl.client.cert.session/client",
        "${app}/connected.tcp4.ssl.client.cert.session/server"})
    public void shouldConnectTcp4WithSslClientCertificateSession() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connected.tcp4.ssl.experimental/client",
        "${app}/connected.tcp4.ssl.experimental/server"})
    public void shouldConnectTcp4WithSslExperimental() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connected.tcp4.experimental/client",
        "${app}/connected.tcp4.experimental/server"})
    public void shouldConnectTcp4WithExperimental() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connected.tcp4.unresolved/client",
        "${app}/connected.tcp4.unresolved/server"})
    public void shouldConnectTcp4Unresolved() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connected.udp4/client",
        "${app}/connected.udp4/server"})
    public void shouldConnectUdp4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connected.tcp6/client",
        "${app}/connected.tcp6/server"})
    public void shouldConnectTcp6() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connected.tcp6.unresolved/client",
        "${app}/connected.tcp6.unresolved/server"})
    public void shouldConnectTcp6Unresolved() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connected.udp6/client",
        "${app}/connected.udp6/server"})
    public void shouldConnectUdp6() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connected.sock.stream/client",
        "${app}/connected.sock.stream/server"})
    public void shouldConnectSockStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connected.sock.datagram/client",
        "${app}/connected.sock.datagram/server"})
    public void shouldConnectSockDatagram() throws Exception
    {
        k3po.finish();
    }
}
