/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.sse.kafka.internal.stream;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_BUFFER_SLOT_CAPACITY;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

public class SseKafkaProxyIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("sse", "io/aklivity/zilla/specs/binding/sse/kafka/streams/sse")
        .addScriptRoot("kafka", "io/aklivity/zilla/specs/binding/sse/kafka/streams/kafka");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(8192)
        .configure(ENGINE_BUFFER_SLOT_CAPACITY, 8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/sse/kafka/config")
        .external("kafka0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("proxy.with.topic.yaml")
    @Specification({
        "${sse}/handshake/client",
        "${kafka}/handshake/server"})
    public void shouldCompleteHandshake() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.topic.and.filters.yaml")
    @Specification({
        "${sse}/handshake/client",
        "${kafka}/handshake.with.filters/server"})
    public void shouldCompleteHandshakeWithFilters() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.topic.dynamic.yaml")
    @Specification({
        "${sse}/handshake/client",
        "${kafka}/handshake/server"})
    public void shouldCompleteHandshakeWithDyanamicTopic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.topic.and.filters.dynamic.yaml")
    @Specification({
        "${sse}/handshake.with.filters.dynamic/client",
        "${kafka}/handshake.with.filters/server"})
    public void shouldCompleteHandshakeWithTopicAndDynamicFilters() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.topic.yaml")
    @Specification({
        "${sse}/handshake.reconnect/client",
        "${kafka}/handshake.reconnect/server"})
    public void shouldCompleteHandshakeThenReconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.topic.yaml")
    @Specification({
        "${sse}/handshake.reconnect.with.etag/client",
        "${kafka}/handshake.reconnect.with.etag/server"})
    public void shouldCompleteHandshakeThenReconnectWithEtag() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.topic.and.event.id.yaml")
    @Specification({
        "${sse}/handshake.reconnect.with.key.and.etag/client",
        "${kafka}/handshake.reconnect.with.etag/server"})
    public void shouldCompleteHandshakeThenReconnectWithKeyAndEtag() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.topic.yaml")
    @Specification({
        "${sse}/handshake.rejected/client"})
    public void shouldRejectHandshakeWithNoBinding() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${sse}/handshake.rejected/client"})
    public void shouldRejectHandshakeWithNoRoute() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.topic.yaml")
    @Specification({
        "${sse}/server.sent.messages/client",
        "${kafka}/server.sent.messages/server"})
    public void shouldReceiveServerSentMessages() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.topic.yaml")
    @Specification({
        "${sse}/server.sent.messages.with.etag/client",
        "${kafka}/server.sent.messages.with.etag/server"})
    public void shouldReceiveServerSentMessagesWithEtag() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.topic.yaml")
    @Specification({
        "${sse}/server.sent.messages.with.null.etag/client",
        "${kafka}/server.sent.messages.with.null.etag/server"})
    public void shouldReceiveServerSentMessagesWithNullEtag() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.topic.and.event.id.yaml")
    @Specification({
        "${sse}/server.sent.messages.with.key.and.etag/client",
        "${kafka}/server.sent.messages.with.etag/server"})
    public void shouldReceiveServerSentMessagesWithKeyAndEtag() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.topic.and.event.id.yaml")
    @Specification({
        "${sse}/server.sent.messages.with.null.key/client",
        "${kafka}/server.sent.messages.with.null.key/server"})
    public void shouldReceiveServerSentMessagesWithNullKey() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.topic.yaml")
    @Specification({
        "${sse}/server.sent.null/client",
        "${kafka}/server.sent.null/server"})
    public void shouldReceiveServerSentNull() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.topic.yaml")
    @Specification({
        "${sse}/server.sent.abort/client",
        "${kafka}/server.sent.abort/server"})
    public void shouldReceiveServerSentAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.topic.yaml")
    @Specification({
        "${sse}/server.sent.reset/client",
        "${kafka}/server.sent.reset/server"})
    public void shouldReceiveServerSentReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.topic.yaml")
    @Specification({
        "${sse}/server.sent.flush/client",
        "${kafka}/server.sent.flush/server"})
    public void shouldReceiveServerSentFlush() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.topic.yaml")
    @Specification({
        "${sse}/client.sent.reset/client",
        "${kafka}/client.sent.reset/server"})
    public void shouldReceiveClientSentReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.topic.yaml")
    @Specification({
        "${sse}/client.sent.abort/client",
        "${kafka}/client.sent.abort/server"})
    public void shouldReceiveClientSentAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.topic.yaml")
    @Specification({
        "${sse}/client.sent.message/client",
        "${kafka}/client.sent.abort/server"})
    public void shouldRejectClientSentMessage() throws Exception
    {
        k3po.finish();
    }
}
