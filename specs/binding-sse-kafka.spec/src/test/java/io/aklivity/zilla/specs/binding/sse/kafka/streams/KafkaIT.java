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

public class KafkaIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("kafka", "io/aklivity/zilla/specs/binding/sse/kafka/streams/kafka");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${kafka}/handshake/client",
        "${kafka}/handshake/server"})
    public void shouldCompleteHandshake() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/handshake.with.filters/client",
        "${kafka}/handshake.with.filters/server"})
    public void shouldCompleteHandshakeWithFilters() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/handshake.reconnect/client",
        "${kafka}/handshake.reconnect/server"})
    public void shouldCompleteHandshakeThenReconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/handshake.reconnect.with.etag/client",
        "${kafka}/handshake.reconnect.with.etag/server"})
    public void shouldCompleteHandshakeThenReconnectWithEtag() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/server.sent.messages/client",
        "${kafka}/server.sent.messages/server"})
    public void shouldReceiveServerSentMessages() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/server.sent.messages.with.etag/client",
        "${kafka}/server.sent.messages.with.etag/server"})
    public void shouldReceiveServerSentMessagesWithEtag() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/server.sent.messages.with.null.key/client",
        "${kafka}/server.sent.messages.with.null.key/server"})
    public void shouldReceiveServerSentMessagesWithNullKey() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/server.sent.null/client",
        "${kafka}/server.sent.null/server"})
    public void shouldReceiveServerSentNull() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/server.sent.abort/client",
        "${kafka}/server.sent.abort/server"})
    public void shouldReceiveServerSentAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/server.sent.reset/client",
        "${kafka}/server.sent.reset/server"})
    public void shouldReceiveServerSentReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/server.sent.flush/client",
        "${kafka}/server.sent.flush/server"})
    public void shouldReceiveServerSentFlush() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/client.sent.reset/client",
        "${kafka}/client.sent.reset/server"})
    public void shouldReceiveClientSentReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/client.sent.abort/client",
        "${kafka}/client.sent.abort/server"})
    public void shouldReceiveClientSentAbort() throws Exception
    {
        k3po.finish();
    }
}
