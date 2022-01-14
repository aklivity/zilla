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
package io.aklivity.zilla.specs.binding.tls.stream;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.ScriptProperty;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

public class ApplicationIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/tls/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/connection.established/client",
        "${app}/connection.established/server"})
    public void shouldEstablishConnection() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connection.established.with.extension.data/client",
        "${app}/connection.established.with.extension.data/server"})
    public void shouldEstablishConnectionWithExtensionData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connection.established.no.hostname.no.alpn/client",
        "${app}/connection.established.no.hostname.no.alpn/server"})
    public void shouldEstablishConnectionWithNoHostnameNoAlpn() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connection.established.with.alpn/client",
        "${app}/connection.established.with.alpn/server"})
    public void shouldEstablishConnectionWithAlpn() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connection.established/client",
        "${app}/connection.established/server"})
    @ScriptProperty("authorization 0x0001_000000000000L")
    public void shouldEstablishConnectionWithAuthorization() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/echo.payload.length.10k/client",
        "${app}/echo.payload.length.10k/server"})
    public void shouldEchoPayloadLength10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.auth/client",
        "${app}/client.auth/server"})
    public void shouldEstablishConnectionWithClientAuth() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/echo.payload.length.10k/client",
        "${app}/echo.payload.length.10k/server"})
    @ScriptProperty("authorization 0x0001_000000000000L")
    public void shouldEchoPayloadLength10kWithAuthorization() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/echo.payload.length.100k/client",
        "${app}/echo.payload.length.100k/server"})
    public void shouldEchoPayloadLength100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/echo.payload.length.1000k/client",
        "${app}/echo.payload.length.1000k/server"})
    public void shouldEchoPayloadLength1000k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/server.sent.flush/client",
        "${app}/server.sent.flush/server"})
    public void shouldReceiveServerSentFlush() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/server.sent.write.close/client",
        "${app}/server.sent.write.close/server"})
    public void shouldReceiveServerSentWriteClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/server.sent.write.close.before.handshake/client",
        "${app}/server.sent.write.close.before.handshake/server"})
    public void shouldRejectServerSentWriteCloseBeforeHandshake() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.sent.flush/client",
        "${app}/client.sent.flush/server"})
    public void shouldReceiveClientSentFlush() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.sent.write.close/client",
        "${app}/client.sent.write.close/server"})
    public void shouldReceiveClientSentWriteClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.sent.write.close.read.closed/client",
        "${app}/client.sent.write.close.read.closed/server"})
    public void shouldReceiveClientSentWriteCloseReadClosed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.sent.write.close.before.handshake/client",
        "${app}/client.sent.write.close.before.handshake/server"})
    public void shouldReceiveClientSentWriteCloseBeforeHandshake() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/server.sent.write.abort/client",
        "${app}/server.sent.write.abort/server"})
    public void shouldReceiveServerSentWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/server.sent.write.abort.before.handshake/client",
        "${app}/server.sent.write.abort.before.handshake/server"})
    public void shouldRejectServerSentWriteAbortBeforeHandshake() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.sent.write.abort/client",
        "${app}/client.sent.write.abort/server"})
    public void shouldReceiveClientSentWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.sent.write.abort.before.handshake/client",
        "${app}/client.sent.write.abort.before.handshake/server"})
    public void shouldReceiveClientSentWriteAbortBeforeHandshake() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/server.sent.read.abort/client",
        "${app}/server.sent.read.abort/server"})
    public void shouldReceiveServerSentReadAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.auth.mismatched/client",
        "${app}/client.auth.mismatched/server"})
    public void shouldRejectClientAuthMismatched() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/server.sent.read.abort.before.handshake/client",
        "${app}/server.sent.read.abort.before.handshake/server"})
    public void shouldRejectServerSentReadAbortBeforeHandshake() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.sent.read.abort/client",
        "${app}/client.sent.read.abort/server"})
    public void shouldReceiveClientSentReadAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.sent.read.abort.before.handshake/client",
        "${app}/client.sent.read.abort.before.handshake/server"})
    public void shouldReceiveClientSentReadAbortBeforeHandshake() throws Exception
    {
        k3po.finish();
    }
}
