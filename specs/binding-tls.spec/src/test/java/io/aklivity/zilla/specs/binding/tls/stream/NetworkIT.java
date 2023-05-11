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
package io.aklivity.zilla.specs.binding.tls.stream;

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

public class NetworkIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/tls/streams/network");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/connection.established/client",
        "${net}/connection.established/server"})
    public void shouldEstablishConnection() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connection.established.with.alpn/client",
        "${net}/connection.established.with.alpn/server"})
    public void shouldEstablishConnectionWithAlpn() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connection.not.established.with.wrong.alpn/client",
        "${net}/connection.not.established.with.wrong.alpn/server"})
    public void shouldRejectConnectionWithWrongAlpn() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connection.established.no.hostname.no.alpn/client",
        "${net}/connection.established.no.hostname.no.alpn/server"})
    public void shouldEstablishConnectionWithNoHostnameNoAlpn() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connection.established/client",
        "${net}/connection.established/server"})
    @ScriptProperty("authorization 0x0001_000000000000L")
    public void shouldEstablishConnectionWithAuthorization() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/echo.payload.length.10k/client",
        "${net}/echo.payload.length.10k/server"})
    public void shouldEchoPayloadLength10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.auth/client",
        "${net}/client.auth/server"})
    public void shouldEstablishConnectionWithClientAuth() throws Exception
    {
        k3po.finish();
    }

    @Ignore("requires accepted only streams")
    @Test
    @Specification({
        "${net}/client.auth.mismatched/client",
        "${net}/client.auth.mismatched/server"})
    public void shouldRejectClientAuthMismatched() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/echo.payload.length.10k/client",
        "${net}/echo.payload.length.10k/server"})
    @ScriptProperty("authorization 0x0001_000000000000L")
    public void shouldEchoPayloadLength10kWithAuthorization() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/echo.payload.length.100k/client",
        "${net}/echo.payload.length.100k/server"})
    public void shouldEchoPayloadLength100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/echo.payload.length.1000k/client",
        "${net}/echo.payload.length.1000k/server"})
    public void shouldEchoPayloadLength1000k() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Specification({
        "${net}/server.sent.flush/client",
        "${net}/server.sent.flush/server"})
    public void shouldReceiveServerSentFlush() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.sent.write.close.before.handshake/client",
        "${net}/server.sent.write.close.before.handshake/server"})
    public void shouldReceiveServerSentWriteCloseBeforeHandshake() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.sent.write.close/client",
        "${net}/server.sent.write.close/server"})
    public void shouldReceiveServerSentWriteClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.sent.write.close.before.handshake/client",
        "${net}/client.sent.write.close.before.handshake/server"})
    public void shouldReceiveClientSentWriteCloseBeforeHandshake() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Specification({
        "${net}/client.sent.flush/client",
        "${net}/client.sent.flush/server"})
    public void shouldReceiveClientSentFlush() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.sent.write.close/client",
        "${net}/client.sent.write.close/server"})
    public void shouldReceiveClientSentWriteClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.sent.write.close.read.closed/client",
        "${net}/client.sent.write.close.read.closed/server"})
    public void shouldReceiveClientSentWriteCloseReadClosed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.sent.write.abort/client",
        "${net}/server.sent.write.abort/server"})
    public void shouldReceiveServerSentWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.sent.write.abort.before.handshake/client",
        "${net}/client.sent.write.abort.before.handshake/server"})
    public void shouldReceiveClientSentWriteAbortBeforeHandshake() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.sent.write.abort/client",
        "${net}/client.sent.write.abort/server"})
    public void shouldReceiveClientSentWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.sent.write.abort.before.handshake/client",
        "${net}/server.sent.write.abort.before.handshake/server"})
    public void shouldReceiveServerSentWriteAbortBeforeHandshake() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.sent.read.abort/client",
        "${net}/server.sent.read.abort/server"})
    public void shouldReceiveServerSentReadAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.sent.read.abort.before.handshake/client",
        "${net}/server.sent.read.abort.before.handshake/server"})
    public void shouldReceiveServerSentReadAbortBeforeHandshake() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.sent.read.abort.before.handshake/client",
        "${net}/client.sent.read.abort.before.handshake/server"})
    public void shouldReceiveClientSentReadAbortBeforeHandshake() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.sent.read.abort/client",
        "${net}/client.sent.read.abort/server"})
    public void shouldReceiveClientSentReadAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.hello.malformed/client",
        "${net}/client.hello.malformed/server"})
    public void shouldResetMalformedClientHello() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.handshake.timeout/client",
        "${net}/server.handshake.timeout/server"})
    public void shouldRejectServerHandshakeTimeout() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.handshake.timeout/client",
        "${net}/client.handshake.timeout/server"})
    public void shouldRejectClientHandshakeTimeout() throws Exception
    {
        k3po.finish();
    }
}
