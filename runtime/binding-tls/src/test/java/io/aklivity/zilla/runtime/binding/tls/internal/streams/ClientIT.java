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
package io.aklivity.zilla.runtime.binding.tls.internal.streams;

import static io.aklivity.zilla.runtime.binding.tls.internal.TlsConfigurationTest.TLS_CACERTS_STORE_NAME;
import static io.aklivity.zilla.runtime.binding.tls.internal.TlsConfigurationTest.TLS_CACERTS_STORE_PASS_NAME;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DRAIN_ON_CLOSE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.ScriptProperty;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

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
            .commandBufferCapacity(1024)
            .responseBufferCapacity(1024)
            .counterValuesBufferCapacity(8192)
            .configurationRoot("io/aklivity/zilla/specs/binding/tls/config")
            .external("net0")
            .configure(ENGINE_DRAIN_ON_CLOSE, false)
            .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/connection.established/client",
        "${net}/connection.established/server" })
    public void shouldEstablishConnection() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.cacerts.json")
    @Specification({
        "${app}/connection.established/client",
        "${net}/connection.established/server" })
    @Configure(name = TLS_CACERTS_STORE_NAME, value =  "src/test/democa/client/trust")
    @Configure(name = TLS_CACERTS_STORE_PASS_NAME, value =  "generated")
    public void shouldEstablishConnectionWithTrustcacerts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.mutual.json")
    @Specification({
        "${app}/client.auth/client",
        "${net}/client.auth/server" })
    public void shouldEstablishConnectionWithClientKey() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.mutual.signer.json")
    @Specification({
        "${app}/client.auth/client",
        "${net}/client.auth/server" })
    public void shouldEstablishConnectionWithClientSigner() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/connection.established.with.extension.data/client",
        "${net}/connection.established.with.extension.data/server" })
     public void shouldEstablishConnectionWithExtensionData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.alpn.json")
    @Specification({
        "${app}/connection.established.with.alpn/client",
        "${net}/connection.established.with.alpn/server" })
    public void shouldEstablishConnectionWithAlpn() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/connection.established.no.hostname.no.alpn/client",
        "${net}/connection.established.no.hostname.no.alpn/server" })
    public void shouldEstablishConnectionWithNoHostnameNoAlpn() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/connection.established.with.alpn/client",
        "${net}/connection.established/server" })
    public void shouldNegotiateWithNoAlpnAsNoProtocolRouteExists() throws Exception
    {
        k3po.finish();
    }

    @Ignore("https://github.com/k3po/k3po/issues/454 - Support connect aborted")
    @Test
    @Configuration("client.alpn.json")
    @Specification({
        "${app}/connection.established.with.alpn/client",
        "${net}/connection.established/server" })
    public void shouldFailNoAlpnNoDefaultRoute() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.alpn.default.json")
    @Specification({
        "${app}/connection.established.with.alpn/client",
        "${net}/connection.established/server" })
    public void shouldSucceedNoAlpnDefaultRoute() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
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
    @Configuration("client.json")
    @Specification({
        "${app}/echo.payload.length.10k/client",
        "${net}/echo.payload.length.10k/server"})
    public void shouldEchoPayloadLength10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/echo.payload.length.10k/client",
        "${net}/echo.payload.length.10k/server"})
    public void shouldEchoPayloadLength10kWithAuthorization() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/echo.payload.length.100k/client",
        "${net}/echo.payload.length.100k/server"})
    public void shouldEchoPayloadLength100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/echo.payload.length.1000k/client",
        "${net}/echo.payload.length.1000k/server"})
    public void shouldEchoPayloadLength1000k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/server.sent.write.close/client",
        "${net}/server.sent.write.close/server"})
    public void shouldReceiveServerSentWriteClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/server.sent.write.close.before.handshake/client",
        "${net}/server.sent.write.close.before.handshake/server"})
    public void shouldRejectServerSentWriteCloseBeforeHandshake() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/client.sent.write.close/client",
        "${net}/client.sent.write.close/server"})
    public void shouldReceiveClientSentWriteClose() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO: requires k3po TLS 1.3 transport to send CLOSE_NOTIFY before closing")
    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/client.sent.write.close.read.closed/client",
        "${net}/client.sent.write.close.read.closed/server"})
    public void shouldReceiveClientSentWriteCloseReadClosed() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO: throttle none implies immediately connected")
    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/client.sent.write.close.before.handshake/client",
        "${net}/client.sent.write.close.before.handshake/server"})
    public void shouldReceiveClientSentWriteCloseBeforeHandshake() throws Exception
    {
        k3po.finish();
    }

    @Ignore("GitHub Actions")
    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/server.sent.write.abort/client",
        "${net}/server.sent.write.abort/server"})
    public void shouldReceiveServerSentWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/server.sent.write.abort.before.handshake/client",
        "${net}/server.sent.write.abort.before.handshake/server"})
    public void shouldRejectServerSentWriteAbortBeforeHandshake() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/client.sent.write.abort/client",
        "${net}/client.sent.write.abort/server"})
    public void shouldReceiveClientSentWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO: throttle none implies immediately connected")
    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/client.sent.write.abort.before.handshake/client",
        "${net}/client.sent.write.abort.before.handshake/server"})
    public void shouldReceiveClientSentWriteAbortBeforeHandshake() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/server.sent.read.abort/client",
        "${net}/server.sent.read.abort/server"})
    public void shouldReceiveServerSentReadAbort() throws Exception
    {
        k3po.finish();
    }

    @Ignore("requires accepted only streams")
    @Test
    @Configuration("client.mutual.signer.json")
    @Specification({
        "${app}/client.auth.mismatched/client",
        "${net}/client.auth.mismatched/server"})
    public void shouldRejectClientAuthMismatched() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/server.sent.read.abort.before.handshake/client",
        "${net}/server.sent.read.abort.before.handshake/server"})
    public void shouldRejectServerSentReadAbortBeforeHandshake() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/client.sent.read.abort/client",
        "${net}/client.sent.read.abort/server"})
    public void shouldReceiveClientSentReadAbort() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO: throttle none implies immediately connected")
    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/client.sent.read.abort.before.handshake/client",
        "${net}/client.sent.read.abort.before.handshake/server"})
    public void shouldReceiveClientSentReadAbortBeforeHandshake() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/client.handshake.timeout/client",
        "${net}/client.handshake.timeout/server" })
    @Configure(name = TlsConfigurationTest.TLS_HANDSHAKE_TIMEOUT_NAME, value = "1")
    public void shouldTimeoutHandshake() throws Exception
    {
        k3po.finish();
    }
}
