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

public class NetworkIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/proxy/streams/network.v2");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/connected.local/client",
        "${net}/connected.local/server"})
    public void shouldConnectLocal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connected.local.discard/client",
        "${net}/connected.local.discard/server"})
    public void shouldConnectLocalDiscard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connected.local.client.sent.begin.ext/client",
        "${net}/connected.local.client.sent.begin.ext/server"})
    public void shouldConnectLocalClientSendsBeginExt() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connected.local.client.sent.close/client",
        "${net}/connected.local.client.sent.close/server"})
    public void shouldConnectLocalClientSendsClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connected.local.client.sent.abort/client",
        "${net}/connected.local.client.sent.abort/server"})
    public void shouldConnectLocalClientSendsAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connected.local.client.sent.reset/client",
        "${net}/connected.local.client.sent.reset/server"})
    public void shouldConnectLocalClientSendsReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connected.local.client.sent.data/client",
        "${net}/connected.local.client.sent.data/server"})
    public void shouldConnectLocalClientSendsData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connected.local.client.sent.flush/client",
        "${net}/connected.local.client.sent.flush/server"})
    public void shouldConnectLocalClientSendsFlush() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connected.local.client.sent.challenge/client",
        "${net}/connected.local.client.sent.challenge/server"})
    public void shouldConnectLocalClientSendsChallenge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connected.local.server.sent.close/client",
        "${net}/connected.local.server.sent.close/server"})
    public void shouldConnectLocalServerSendsClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connected.local.server.sent.abort/client",
        "${net}/connected.local.server.sent.abort/server"})
    public void shouldConnectLocalServerSendsAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connected.local.server.sent.reset/client",
        "${net}/connected.local.server.sent.reset/server"})
    public void shouldConnectLocalServerSendsReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connected.local.server.sent.data/client",
        "${net}/connected.local.server.sent.data/server"})
    public void shouldConnectLocalServerSendsData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connected.local.server.sent.flush/client",
        "${net}/connected.local.server.sent.flush/server"})
    public void shouldConnectLocalServerSendsFlush() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connected.local.server.sent.challenge/client",
        "${net}/connected.local.server.sent.challenge/server"})
    public void shouldConnectLocalServerSendsChallenge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connected.tcp4/client",
        "${net}/connected.tcp4/server"})
    public void shouldConnectTcp4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connected.tcp4.alpn/client",
        "${net}/connected.tcp4.alpn/server"})
    public void shouldConnectTcp4WithAlpn() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connected.tcp4.authority/client",
        "${net}/connected.tcp4.authority/server"})
    public void shouldConnectTcp4WithAuthority() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connected.tcp4.crc32c/client",
        "${net}/connected.tcp4.crc32c/server"})
    public void shouldConnectTcp4WithCrc32c() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connected.tcp4.identity/client",
        "${net}/connected.tcp4.identity/server"})
    public void shouldConnectTcp4WithIdentity() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connected.tcp4.namespace/client",
        "${net}/connected.tcp4.namespace/server"})
    public void shouldConnectTcp4WithNamespace() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connected.tcp4.noop/client",
        "${net}/connected.tcp4.noop/server"})
    public void shouldConnectTcp4WithNoop() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connected.tcp4.ssl/client",
        "${net}/connected.tcp4.ssl/server"})
    public void shouldConnectTcp4WithSsl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connected.tcp4.ssl.client.cert/client",
        "${net}/connected.tcp4.ssl.client.cert/server"})
    public void shouldConnectTcp4WithSslClientCertificate() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connected.tcp4.ssl.client.cert.session/client",
        "${net}/connected.tcp4.ssl.client.cert.session/server"})
    public void shouldConnectTcp4WithSslClientCertificateSession() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connected.tcp4.ssl.experimental/client",
        "${net}/connected.tcp4.ssl.experimental/server"})
    public void shouldConnectTcp4WithSslExperimental() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connected.tcp4.experimental/client",
        "${net}/connected.tcp4.experimental/server"})
    public void shouldConnectTcp4WithExperimental() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connected.udp4/client",
        "${net}/connected.udp4/server"})
    public void shouldConnectUdp4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connected.tcp6/client",
        "${net}/connected.tcp6/server"})
    public void shouldConnectTcp6() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connected.udp6/client",
        "${net}/connected.udp6/server"})
    public void shouldConnectUdp6() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connected.sock.stream/client",
        "${net}/connected.sock.stream/server"})
    public void shouldConnectSockStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connected.sock.datagram/client",
        "${net}/connected.sock.datagram/server"})
    public void shouldConnectSockDatagram() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/rejected.header.mismatch/client",
        "${net}/rejected.header.mismatch/server"})
    public void shouldRejectHeaderMismatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/rejected.header.version.mismatch/client",
        "${net}/rejected.header.version.mismatch/server"})
    public void shouldRejectHeaderVersionMismatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/rejected.tcp4.crc32c.mismatch/client",
        "${net}/rejected.tcp4.crc32c.mismatch/server"})
    public void shouldRejectTcp4WithCrc32cMismatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/rejected.tcp4.crc32c.overflow/client",
        "${net}/rejected.tcp4.crc32c.overflow/server"})
    public void shouldRejectTcp4WithCrc32cOverflow() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/rejected.tcp4.crc32c.underflow/client",
        "${net}/rejected.tcp4.crc32c.underflow/server"})
    public void shouldRejectTcp4WithCrc32cUnderflow() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/rejected.tcp4.ssl.underflow/client",
        "${net}/rejected.tcp4.ssl.underflow/server"})
    public void shouldRejectTcp4WithSslUnderflow() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/rejected.tcp4.underflow/client",
        "${net}/rejected.tcp4.underflow/server"})
    public void shouldRejectTcp4Underflow() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/rejected.tcp6.underflow/client",
        "${net}/rejected.tcp6.underflow/server"})
    public void shouldRejectTcp6Underflow() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/rejected.sock.stream.underflow/client",
        "${net}/rejected.sock.stream.underflow/server"})
    public void shouldRejectSockStreamUnderflow() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/rejected.address.family.mismatch/client",
        "${net}/rejected.address.family.mismatch/server"})
    public void shouldRejectAddressFamilyMismatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/rejected.command.mismatch/client",
        "${net}/rejected.command.mismatch/server"})
    public void shouldRejectCommandMismatch() throws Exception
    {
        k3po.finish();
    }
}
