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
package io.aklivity.zilla.specs.cog.amqp.streams.network;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

public class ConnectionIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/cog/amqp/streams/network/connection");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/header.exchange/handshake.client",
        "${net}/header.exchange/handshake.server"})
    public void shouldExchangeHeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/protocol.header.unmatched/client",
        "${net}/protocol.header.unmatched/server"})
    public void shouldCloseStreamWhenHeaderUnmatched() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/open.exchange/client",
        "${net}/open.exchange/server"})
    public void shouldExchangeOpen() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/close.exchange/client",
        "${net}/close.exchange/server"})
    public void shouldExchangeClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.idle.timeout.does.not.expire/client",
        "${net}/client.idle.timeout.does.not.expire/server"})
    public void shouldPreventTimeoutSentByClient() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.idle.timeout.expires/client",
        "${net}/client.idle.timeout.expires/server"})
    public void shouldCloseConnectionWithTimeoutSentByClient() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.idle.timeout.does.not.expire/client",
        "${net}/server.idle.timeout.does.not.expire/server"})
    public void shouldPreventTimeoutSentByServer() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.idle.timeout.expires/client",
        "${net}/server.idle.timeout.expires/server"})
    public void shouldCloseConnectionWithTimeout() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/sasl.exchange/client",
        "${net}/sasl.exchange/server"})
    public void shouldExchangeSasl() throws Exception

    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/open.exchange.pipelined/client",
        "${net}/open.exchange.pipelined/server"})
    public void shouldSendOpenPipelined() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/sasl.exchange.then.open.exchange.pipelined/client",
        "${net}/sasl.exchange.then.open.exchange.pipelined/server"})
    public void shouldSendOpenPipelinedAfterSasl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/close.exchange.server.abandoned/client",
        "${net}/close.exchange.server.abandoned/server"})
    public void shouldCloseConnectionWhenServerAbandoned() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/close.exchange.simultaneous/client",
        "${net}/close.exchange.simultaneous/server"})
    public void shouldCloseSimultaneously() throws Exception

    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/open.with.outgoing.locales.negotiated.default/client",
        "${net}/open.with.outgoing.locales.negotiated.default/server"})
    public void shouldOpenWithOutgoingLocalesNegotiatedDefault() throws Exception

    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/open.with.outgoing.locales.negotiated.non.default/client",
        "${net}/open.with.outgoing.locales.negotiated.non.default/server"})
    public void shouldOpenWithOutgoingLocalesNegatiatedNonDefault() throws Exception

    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reject.incorrect.fields.key.type/client",
        "${net}/reject.incorrect.fields.key.type/server"})
    public void shouldRejectIncorrectFieldsKeyType() throws Exception
    {
        k3po.finish();
    }
}
