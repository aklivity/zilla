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
package io.aklivity.zilla.runtime.binding.mqtt.internal.stream.pipeline;

import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfiguration.PUBLISH_TIMEOUT;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DRAIN_ON_CLOSE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

public class PipelineIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/mqtt/streams/network/v5")
        .addScriptRoot("upstream", "io/aklivity/zilla/specs/binding/mqtt/streams/upstream");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configure(PUBLISH_TIMEOUT, 1L)
        .configure(ENGINE_DRAIN_ON_CLOSE, false)
        .configurationRoot("io/aklivity/zilla/specs/binding/mqtt/config")
        .external("net1")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("server.client.yaml")
    @Specification({
        "${net}/subscribe.shared.subscription/client",
        "${upstream}/subscribe.shared.subscription/server"})
    public void shouldSubscribeSharedSubscription() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.client.yaml")
    @Specification({
        "${net}/unsubscribe.shared.subscription/client",
        "${upstream}/unsubscribe.shared.subscription/server"})
    public void shouldUnsubscribeSharedSubscription() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.client.yaml")
    @Specification({
        "${net}/subscribe.shared.receive.message.qos1/client",
        "${upstream}/subscribe.shared.receive.message.qos1/server"})
    public void shouldSubscribeSharedReceiveMessageQos1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.client.yaml")
    @Specification({
        "${net}/subscribe.shared.receive.message.qos2/client",
        "${upstream}/subscribe.shared.receive.message.qos2/server"})
    public void shouldSubscribeSharedReceiveMessageQos2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.client.yaml")
    @Specification({
        "${net}/subscribe.shared.receive.message.qos1.ack.with.reasoncode/client",
        "${upstream}/subscribe.shared.receive.message.qos1.ack.with.reasoncode/server"})
    public void shouldSubscribeSharedReceiveMessageQos1AckWithReasoncode() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.client.yaml")
    @Specification({
        "${net}/subscribe.shared.and.non.shared/client",
        "${upstream}/subscribe.shared.and.non.shared/server"})
    public void shouldSubscribeSharedAndNonShared() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.client.yaml")
    @Specification({
        "${net}/subscribe.receive.message.qos1/client",
        "${upstream}/subscribe.receive.message.qos1/server"})
    public void shouldSubscribeReceiveMessageQos1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.client.yaml")
    @Specification({
        "${net}/subscribe.receive.message.qos2/client",
        "${upstream}/subscribe.receive.message.qos2/server"})
    public void shouldSubscribeReceiveMessageQos2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.client.yaml")
    @Specification({
        "${net}/subscribe.receive.message.qos1.ack.with.reasoncode/client",
        "${upstream}/subscribe.receive.message.qos1.ack.with.reasoncode/server"})
    public void shouldSubscribeReceiveMessageQos1AckWithReasoncode() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.client.yaml")
    @Specification({
        "${net}/subscribe.receive.message.qos2.ack.with.reasoncode/client",
        "${upstream}/subscribe.receive.message.qos2.ack.with.reasoncode/server"})
    public void shouldSubscribeReceiveMessageQos2AckWithReasoncode() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.client.yaml")
    @Specification({
        "${net}/subscribe.receive.messages.qos2.deferred.ack/client",
        "${upstream}/subscribe.receive.message.qos2.duplicate.before.ack/server"})
    public void shouldSubscribeReceiveMessageQos2DuplicateBeforeAck() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.client.yaml")
    @Specification({
        "${net}/subscribe.receive.messages.qos2.ack.with.success.reasoncode/client",
        "${upstream}/subscribe.receive.message.qos2.duplicate.after.ack/server"})
    public void shouldSubscribeReceiveMessageQos2DuplicateAfterAck() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.client.yaml")
    @Specification({
        "${net}/subscribe.receive.message.qos2.reuse.packet.id.after.complete/client",
        "${upstream}/subscribe.receive.message.qos2.reuse.packet.id.after.complete/server"})
    public void shouldSubscribeReceiveMessageQos2ReusePacketIdAfterComplete() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.client.yaml")
    @Specification({
        "${net}/subscribe.receive.message.qos2.reuse.packet.id.after.error/client",
        "${upstream}/subscribe.receive.message.qos2.reuse.packet.id.after.error/server"})
    public void shouldSubscribeReceiveMessageQos2ReusePacketIdAfterError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.client.yaml")
    @Specification({
        "${net}/subscribe.reject.shared.subscriptions.not.supported/client",
        "${upstream}/connect.shared.subscriptions.not.supported/server"})
    public void shouldSubscribeRejectSharedSubscriptionsNotSupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.client.yaml")
    @Specification({
        "${net}/subscribe.shared.and.non.shared.receive.messages/client",
        "${upstream}/subscribe.shared.and.non.shared.receive.messages/server"})
    public void shouldSubscribeSharedAndNonSharedReceiveMessages() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.client.yaml")
    @Specification({
        "${net}/unsubscribe.shared.subscription.qos1/client",
        "${upstream}/unsubscribe.shared.subscription.qos1/server"})
    public void shouldUnsubscribeSharedSubscriptionQos1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.client.yaml")
    @Specification({
        "${net}/unsubscribe.shared.subscription.qos2/client",
        "${upstream}/unsubscribe.shared.subscription.qos2/server"})
    public void shouldUnsubscribeSharedSubscriptionQos2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.client.yaml")
    @Specification({
        "${net}/unsubscribe.shared.inflight.qos1/client",
        "${upstream}/unsubscribe.shared.inflight.qos1/server"})
    public void shouldUnsubscribeSharedInflightQos1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.client.yaml")
    @Specification({
        "${net}/unsubscribe.shared.inflight.qos2/client",
        "${upstream}/unsubscribe.shared.inflight.qos2/server"})
    public void shouldUnsubscribeSharedInflightQos2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.client.yaml")
    @Specification({
        "${net}/subscribe.shared.sequential.receive.messages.qos1/client",
        "${upstream}/subscribe.shared.sequential.receive.messages.qos1/server"})
    public void shouldSubscribeSharedSequentialReceiveMessagesQos1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.client.yaml")
    @Specification({
        "${net}/subscribe.shared.then.non.shared.receive.messages.qos1/client",
        "${upstream}/subscribe.shared.then.non.shared.receive.messages.qos1/server"})
    public void shouldSubscribeSharedThenNonSharedReceiveMessagesQos1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.client.yaml")
    @Specification({
        "${net}/subscribe.non.shared.then.shared.receive.messages.qos1/client",
        "${upstream}/subscribe.non.shared.then.shared.receive.messages.qos1/server"})
    public void shouldSubscribeNonSharedThenSharedReceiveMessagesQos1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.client.yaml")
    @Specification({
        "${net}/subscribe.non.shared.sequential.receive.messages.qos1/client",
        "${upstream}/subscribe.non.shared.sequential.receive.messages.qos1/server"})
    public void shouldSubscribeNonSharedSequentialReceiveMessagesQos1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.client.yaml")
    @Specification({
        "${net}/subscribe.non.shared.sequential.receive.messages.qos2/client",
        "${upstream}/subscribe.non.shared.sequential.receive.messages.qos2/server"})
    public void shouldSubscribeNonSharedSequentialReceiveMessagesQos2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.client.yaml")
    @Specification({
        "${net}/subscribe.shared.resubscribe.change.qos.receive.messages/client",
        "${upstream}/subscribe.shared.resubscribe.change.qos.receive.messages/server"})
    public void shouldSubscribeSharedResubscribeChangeQosReceiveMessages() throws Exception
    {
        k3po.finish();
    }
}
