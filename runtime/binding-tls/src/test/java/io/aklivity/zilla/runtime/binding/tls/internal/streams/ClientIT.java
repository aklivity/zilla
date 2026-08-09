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
package io.aklivity.zilla.runtime.binding.tls.internal.streams;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DRAIN_ON_CLOSE;
import static io.aklivity.zilla.runtime.engine.test.EngineRule.ENGINE_CACERTS_STORE_NAME;
import static io.aklivity.zilla.runtime.engine.test.EngineRule.ENGINE_CACERTS_STORE_PASS_NAME;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.ScriptProperty;
import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;
import io.aklivity.zilla.runtime.binding.tls.internal.TlsConfigurationTest;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;
import io.aklivity.zilla.runtime.engine.test.annotation.Configure;

public class ClientIT
{
    private final K3poRule k3po = new K3poRule()
            .addScriptRoot("app", "io/aklivity/zilla/specs/binding/tls/streams/application")
            .addScriptRoot("net", "io/aklivity/zilla/specs/binding/tls/streams/network");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
            .directory("target/zilla-itests")
            .countersBufferCapacity(8192)
            .configurationRoot("io/aklivity/zilla/specs/binding/tls/config")
            .external("net0")
            .configure(ENGINE_DRAIN_ON_CLOSE, false)
            .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/connection.established/client",
        "${net}/connection.established/server" })
    public void shouldEstablishConnection() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.cacerts.yaml")
    @Specification({
        "${app}/connection.established/client",
        "${net}/connection.established/server" })
    @Configure(name = ENGINE_CACERTS_STORE_NAME, value =  "src/test/democa/client/trust")
    @Configure(name = ENGINE_CACERTS_STORE_PASS_NAME, value =  "generated")
    public void shouldEstablishConnectionWithTrustcacerts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.mutual.yaml")
    @Specification({
        "${app}/client.auth/client",
        "${net}/client.auth/server" })
    public void shouldEstablishConnectionWithClientKey() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.mutual.signer.yaml")
    @Specification({
        "${app}/client.auth/client",
        "${net}/client.auth/server" })
    public void shouldEstablishConnectionWithClientSigner() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.with.certificate.subject.cn.yaml")
    @Specification({
        "${app}/client.auth.with.certificate/client",
        "${net}/client.auth/server" })
    public void shouldEstablishConnectionWithCertificateSubjectCommonName() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.with.certificate.subject.dn.yaml")
    @Specification({
        "${app}/client.auth.with.certificate/client",
        "${net}/client.auth/server" })
    public void shouldEstablishConnectionWithCertificateSubjectDistinguishedName() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.with.certificate.guarded.yaml")
    @Specification({
        "${app}/client.auth.with.certificate/client",
        "${net}/client.auth/server" })
    public void shouldEstablishConnectionWithCertificateGuardedIdentity() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.with.certificate.attribute.yaml")
    @Specification({
        "${app}/client.auth.with.certificate/client",
        "${net}/client.auth/server" })
    public void shouldEstablishConnectionWithCertificateGuardedAttribute() throws Exception
    {
        k3po.finish();
    }

    // Two candidate keys, each signed by a different client ca, and the far end trusts only one
    // of them. The handshake completes only if the route selected the key the far end trusts, so
    // the pair pins which certificate reached the wire rather than merely that one did.
    @Test
    @Configuration("client.with.certificate.select.first.yaml")
    @Specification({
        "${app}/client.auth.with.certificate/client",
        "${net}/client.auth.trusted.first/server" })
    public void shouldEstablishConnectionWithCertificateSelectedFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.with.certificate.select.second.yaml")
    @Specification({
        "${app}/client.auth.with.certificate/client",
        "${net}/client.auth.trusted.second/server" })
    public void shouldEstablishConnectionWithCertificateSelectedSecond() throws Exception
    {
        k3po.finish();
    }

    // both candidates carry subject.cn client1, so the selection is decided by issue date; the
    // later one is signed by the client ca the far end trusts and the earlier one is not
    @Test
    @Configuration("client.with.certificate.select.newest.yaml")
    @Specification({
        "${app}/client.auth.with.certificate/client",
        "${net}/client.auth.trusted.second/server" })
    public void shouldEstablishConnectionWithCertificateSelectedNewest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.with.certificate.match.all.yaml")
    @Specification({
        "${app}/client.auth.with.certificate/client",
        "${net}/client.auth.trusted.first/server" })
    public void shouldEstablishConnectionWithCertificateMatchingAllProperties() throws Exception
    {
        k3po.finish();
    }

    // subject.cn names one candidate and subject.dn the other, so the properties are satisfiable
    // only apart; matching them together selects nothing and the event names the whole selector
    @Test
    @Configuration("client.with.certificate.match.all.no.match.yaml")
    @Specification({
        "${app}/client.auth.with.certificate.not.matched/client",
        "${net}/client.auth.with.certificate.not.matched/server" })
    public void shouldLogClientCertificateNotMatchedEventWhenPropertiesMatchApart() throws Exception
    {
        k3po.finish();
    }

    // a failed TLS handshake cannot be scripted against a k3po tls:// accept, which completes
    // only on success; `rejected` does not help, since no child channel is ever bound. Same
    // limitation as shouldRejectClientAuthMismatched below. Covered by
    // TlsClientX509ExtendedKeyManagerTest instead.
    @Ignore("requires accepted only streams")
    @Test
    @Configuration("client.with.certificate.no.match.yaml")
    @Specification({
        "${app}/client.auth.with.certificate.rejected/client",
        "${net}/client.auth.mismatched/server" })
    public void shouldRejectConnectionWithCertificateNotMatched() throws Exception
    {
        k3po.finish();
    }

    // The far end requests rather than requires the client certificate, so the handshake
    // completes with none presented, and the far end then closes as a mutual: requested
    // server finding no guarded route would. The application stream is already connected by
    // the time that close arrives -- the connect completes as soon as the handshake does --
    // so this is an orderly close, not an abort. Asserts that the close propagates to the
    // application stream. It does NOT assert which certificate was presented: k3po's
    // tls transport exposes no peer-certificate assertion, so with wantClientAuth the
    // script cannot tell a matching certificate from none at all, and this would still
    // pass if the wrong key were selected. TlsClientX509ExtendedKeyManagerTest is the
    // authority for selection.
    @Test
    @Configuration("client.with.certificate.no.match.yaml")
    @Specification({
        "${app}/client.auth.with.certificate.not.matched/client",
        "${net}/client.auth.with.certificate.not.matched/server" })
    public void shouldCloseWhenCertificateNotMatched() throws Exception
    {
        k3po.finish();
    }

    // the far end does not request a certificate, so an unresolved property is observable
    // as the logged event rather than as a handshake failure
    @Test
    @Configuration("client.with.certificate.unresolved.yaml")
    @Specification({
        "${app}/connection.established/client",
        "${net}/connection.established/server" })
    public void shouldLogClientCertificateNotResolvedEvent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/connection.established.with.extension.data/client",
        "${net}/connection.established.with.extension.data/server" })
     public void shouldEstablishConnectionWithExtensionData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.alpn.yaml")
    @Specification({
        "${app}/connection.established.with.alpn/client",
        "${net}/connection.established.with.alpn/server" })
    public void shouldEstablishConnectionWithAlpn() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.ports.yaml")
    @Specification({
        "${app}/connection.established.with.port/client",
        "${net}/connection.established/server"
    })
    public void shouldEstablishedConnectionWithPort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/connection.established.no.hostname.no.alpn/client",
        "${net}/connection.established.no.hostname.no.alpn/server" })
    public void shouldEstablishConnectionWithNoHostnameNoAlpn() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/connection.established.with.alpn/client",
        "${net}/connection.established/server" })
    public void shouldNegotiateWithNoAlpnAsNoProtocolRouteExists() throws Exception
    {
        k3po.finish();
    }

    @Ignore("https://github.com/k3po/k3po/issues/454 - Support connect aborted")
    @Test
    @Configuration("client.alpn.yaml")
    @Specification({
        "${app}/connection.established.with.alpn/client",
        "${net}/connection.established/server" })
    public void shouldFailNoAlpnNoDefaultRoute() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.alpn.default.yaml")
    @Specification({
        "${app}/connection.established.with.alpn/client",
        "${net}/connection.established/server" })
    public void shouldSucceedNoAlpnDefaultRoute() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/connection.established/client",
        "${net}/connection.established/server" })
    @ScriptProperty({
        "authorization 0x0001_000000000000L"})
    public void shouldEstablishConnectionWithAuthorization() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/echo.payload.length.10k/client",
        "${net}/echo.payload.length.10k/server"})
    public void shouldEchoPayloadLength10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/echo.payload.length.10k/client",
        "${net}/echo.payload.length.10k/server"})
    public void shouldEchoPayloadLength10kWithAuthorization() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/echo.payload.length.100k/client",
        "${net}/echo.payload.length.100k/server"})
    public void shouldEchoPayloadLength100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/echo.payload.length.1000k/client",
        "${net}/echo.payload.length.1000k/server"})
    public void shouldEchoPayloadLength1000k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/server.sent.write.close/client",
        "${net}/server.sent.write.close/server"})
    public void shouldReceiveServerSentWriteClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/server.sent.write.close.before.handshake/client",
        "${net}/server.sent.write.close.before.handshake/server"})
    public void shouldRejectServerSentWriteCloseBeforeHandshake() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/client.sent.write.close/client",
        "${net}/client.sent.write.close/server"})
    public void shouldReceiveClientSentWriteClose() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO: requires k3po TLS 1.3 transport to send CLOSE_NOTIFY before closing")
    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/client.sent.write.close.read.closed/client",
        "${net}/client.sent.write.close.read.closed/server"})
    public void shouldReceiveClientSentWriteCloseReadClosed() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO: throttle none implies immediately connected")
    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/client.sent.write.close.before.handshake/client",
        "${net}/client.sent.write.close.before.handshake/server"})
    public void shouldReceiveClientSentWriteCloseBeforeHandshake() throws Exception
    {
        k3po.finish();
    }

    @Ignore("GitHub Actions")
    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/server.sent.write.abort/client",
        "${net}/server.sent.write.abort/server"})
    public void shouldReceiveServerSentWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/server.sent.write.abort.before.handshake/client",
        "${net}/server.sent.write.abort.before.handshake/server"})
    public void shouldRejectServerSentWriteAbortBeforeHandshake() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/client.sent.write.abort/client",
        "${net}/client.sent.write.abort/server"})
    public void shouldReceiveClientSentWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO: throttle none implies immediately connected")
    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/client.sent.write.abort.before.handshake/client",
        "${net}/client.sent.write.abort.before.handshake/server"})
    public void shouldReceiveClientSentWriteAbortBeforeHandshake() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/server.sent.read.abort/client",
        "${net}/server.sent.read.abort/server"})
    public void shouldReceiveServerSentReadAbort() throws Exception
    {
        k3po.finish();
    }

    @Ignore("requires accepted only streams")
    @Test
    @Configuration("client.mutual.signer.yaml")
    @Specification({
        "${app}/client.auth.mismatched/client",
        "${net}/client.auth.mismatched/server"})
    public void shouldRejectClientAuthMismatched() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/server.sent.read.abort.before.handshake/client",
        "${net}/server.sent.read.abort.before.handshake/server"})
    public void shouldRejectServerSentReadAbortBeforeHandshake() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/client.sent.read.abort/client",
        "${net}/client.sent.read.abort/server"})
    public void shouldReceiveClientSentReadAbort() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO: throttle none implies immediately connected")
    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/client.sent.read.abort.before.handshake/client",
        "${net}/client.sent.read.abort.before.handshake/server"})
    public void shouldReceiveClientSentReadAbortBeforeHandshake() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/client.handshake.timeout/client",
        "${net}/client.handshake.timeout/server" })
    @Configure(name = TlsConfigurationTest.TLS_HANDSHAKE_TIMEOUT_NAME, value = "1")
    public void shouldTimeoutHandshake() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.event.yaml")
    @Specification({
        "${app}/client.handshake.timeout/client",
        "${net}/client.handshake.timeout/server" })
    @Configure(name = TlsConfigurationTest.TLS_HANDSHAKE_TIMEOUT_NAME, value = "1")
    public void shouldLogHandshakeTimeoutEvent() throws Exception
    {
        k3po.finish();
    }
}
