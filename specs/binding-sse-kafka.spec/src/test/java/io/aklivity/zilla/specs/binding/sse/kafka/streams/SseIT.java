/*
 * Copyright 2021-2022 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.specs.binding.sse.kafka.streams;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

public class SseIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("sse", "io/aklivity/zilla/specs/binding/sse/kafka/streams/sse");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${sse}/handshake/client",
        "${sse}/handshake/server"})
    public void shouldCompleteHandshake() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${sse}/handshake.with.filters.dynamic/client",
        "${sse}/handshake.with.filters.dynamic/server"})
    public void shouldCompleteHandshakeWithDynamicFilters() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${sse}/handshake.rejected/client",
        "${sse}/handshake.rejected/server"})
    public void shouldRejectHandshake() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${sse}/handshake.reconnect/client",
        "${sse}/handshake.reconnect/server"})
    public void shouldCompleteHandshakeThenReconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${sse}/handshake.reconnect.with.etag/client",
        "${sse}/handshake.reconnect.with.etag/server"})
    public void shouldCompleteHandshakeThenReconnectWithEtag() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${sse}/handshake.reconnect.with.key.and.etag/client",
        "${sse}/handshake.reconnect.with.key.and.etag/server"})
    public void shouldCompleteHandshakeThenReconnectWithKeyAndEtag() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${sse}/server.sent.messages/client",
        "${sse}/server.sent.messages/server"})
    public void shouldReceiveServerSentMessages() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${sse}/server.sent.messages.with.etag/client",
        "${sse}/server.sent.messages.with.etag/server"})
    public void shouldReceiveServerSentMessagesWithEtag() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${sse}/server.sent.messages.with.null.key/client",
        "${sse}/server.sent.messages.with.null.key/server"})
    public void shouldReceiveServerSentMessagesWithNullKey() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${sse}/server.sent.null/client",
        "${sse}/server.sent.null/server"})
    public void shouldReceiveServerSentNull() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${sse}/server.sent.abort/client",
        "${sse}/server.sent.abort/server"})
    public void shouldReceiveServerSentAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${sse}/server.sent.reset/client",
        "${sse}/server.sent.reset/server"})
    public void shouldReceiveServerSentReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${sse}/server.sent.flush/client",
        "${sse}/server.sent.flush/server"})
    public void shouldReceiveServerSentFlush() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${sse}/client.sent.reset/client",
        "${sse}/client.sent.reset/server"})
    public void shouldReceiveClientSentReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${sse}/client.sent.abort/client",
        "${sse}/client.sent.abort/server"})
    public void shouldReceiveClientSentAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${sse}/client.sent.message/client",
        "${sse}/client.sent.message/server"})
    public void shouldRejectClientSentMessage() throws Exception
    {
        k3po.finish();
    }
}
