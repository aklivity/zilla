/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.mqtt.internal.stream.server.v5;

import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfiguration.PUBLISH_TIMEOUT;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfigurationTest.PUBLISH_TIMEOUT_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfigurationTest.TOPIC_ALIAS_MAXIMUM_NAME;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DRAIN_ON_CLOSE;
import static io.aklivity.zilla.runtime.engine.test.EngineRule.ENGINE_BUFFER_SLOT_CAPACITY_NAME;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;
import io.aklivity.zilla.runtime.engine.test.annotation.Configure;

public class PublishIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/mqtt/streams/network/v5")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/mqtt/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configure(PUBLISH_TIMEOUT, 1L)
        .configure(ENGINE_DRAIN_ON_CLOSE, false)
        .configurationRoot("io/aklivity/zilla/specs/binding/mqtt/config")
        .external("app0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.one.message.properties/client",
        "${app}/publish.one.message.properties/server"})
    public void shouldPublishOneMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.session.takeover/client",
        "${app}/publish.session.takeover/server"})
    public void shouldPublishAfterSessionTakeover() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.one.message.disconnect/client",
        "${app}/publish.one.message.properties/server"})
    public void shouldPublishOneMessageAndDisconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.validator.yaml")
    @Specification({
        "${net}/publish.invalid.message/client",
        "${app}/session.publish/server"})
    public void shouldPublishInvalidMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.validator.yaml")
    @Specification({
        "${net}/publish.valid.message/client",
        "${app}/publish.valid.message/server"})
    public void shouldPublishValidMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.user.properties.validator.yaml")
    @Specification({
        "${net}/publish.valid.user.property/client",
        "${app}/publish.valid.user.property/server"})
    public void shouldPublishValidUserProperty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.user.properties.validator.yaml")
    @Specification({
        "${net}/publish.invalid.user.property/client",
        "${app}/session.connect/server"})
    public void shouldNotPublishInvalidUserProperty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.retained/client",
        "${app}/publish.retained/server"})
    public void shouldPublishRetainedMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.retained.multiple.topic/client",
        "${app}/publish.retained.multiple.topic/server"})
    public void shouldPublishRetainedMessageMultipleTopic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.message.with.topic.alias/client",
        "${app}/publish.message.with.topic.alias/server"})
    @Configure(name = TOPIC_ALIAS_MAXIMUM_NAME, value = "2")
    public void shouldPublishMessageWithTopicAlias() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.multiple.messages/client",
        "${app}/publish.multiple.messages/server"})
    public void shouldPublishMultipleMessages() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.multiple.clients/client",
        "${app}/publish.multiple.clients/server"})
    public void shouldPublishMultipleClients() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.multiple.messages.unfragmented/client",
        "${app}/publish.multiple.messages/server"})
    public void shouldPublishMultipleMessagesUnfragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.subscribe.batched/client",
        "${app}/publish.subscribe.batched/server"})
    public void shouldPublishSubscribeBatched() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.multiple.messages.with.delay/client",
        "${app}/publish.multiple.messages/server"})
    @Configure(name = PUBLISH_TIMEOUT_NAME, value = "5")
    public void shouldPublishMultipleMessagesWithDelay() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("PUBLISHED_MESSAGE_TWO");
        Thread.sleep(500);
        k3po.notifyBarrier("PUBLISH_MESSAGE_THREE");
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.multiple.messages.with.delay/client",
        "${app}/publish.multiple.messages.timeout/server"})
    @Configure(name = PUBLISH_TIMEOUT_NAME, value = "1")
    public void shouldPublishMultipleMessagesTimeout() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("PUBLISHED_MESSAGE_TWO");
        Thread.sleep(2500);
        k3po.notifyBarrier("PUBLISH_MESSAGE_THREE");
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.messages.with.topic.alias.distinct/client",
        "${app}/publish.messages.with.topic.alias.distinct/server"})
    @Configure(name = TOPIC_ALIAS_MAXIMUM_NAME, value = "2")
    public void shouldPublishMessagesWithTopicAliasDistinct() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.messages.with.topic.alias.repeated/client",
        "${app}/publish.messages.with.topic.alias.repeated/server"})
    @Configure(name = TOPIC_ALIAS_MAXIMUM_NAME, value = "2")
    public void shouldPublishMessagesWithTopicAliasRepeated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.messages.with.topic.alias.replaced/client",
        "${app}/publish.messages.with.topic.alias.replaced/server"})
    @Configure(name = TOPIC_ALIAS_MAXIMUM_NAME, value = "1")
    public void shouldPublishMessagesWithTopicAliasReplaced() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.messages.with.topic.alias.invalid.scope/client",
        "${app}/publish.messages.with.topic.alias.invalid.scope/server"})
    @Configure(name = TOPIC_ALIAS_MAXIMUM_NAME, value = "1")
    public void shouldSendMessagesWithTopicAliasInvalidScope() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.topic.not.routed/client",
        "${app}/session.connect/server"})
    public void shouldRejectTopicNotRouted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.reject.topic.alias.exceeds.maximum/client",
        "${app}/session.connect/server"})
    public void shouldRejectPublishWhenTopicAliasExceedsMaximum() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.reject.topic.alias.repeated/client",
        "${app}/session.connect/server"})
    @Configure(name = TOPIC_ALIAS_MAXIMUM_NAME, value = "2")
    public void shouldRejectPublishWithMultipleTopicAliases() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.reject.client.sent.subscription.id/client",
        "${app}/session.connect/server"})
    @Configure(name = TOPIC_ALIAS_MAXIMUM_NAME, value = "2")
    public void shouldRejectPublishClientSentSubscriptionId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.reject.invalid.payload.format/client",
        "${app}/session.publish/server"})
    public void shouldRejectPublishInvalidPayloadFormat() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.reject.qos1.not.supported/client",
        "${app}/publish.reject.qos.not.supported/server"})
    public void shouldRejectPublishQos1NotSupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.reject.qos2.not.supported/client",
        "${app}/publish.reject.qos.not.supported/server"})
    public void shouldRejectPublishQos2NotSupported() throws Exception
    {
        k3po.finish();
    }

    @Ignore
    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.reject.qos0.with.packet.id/client"})
    public void shouldRejectPublishQos0WithPacketId() throws Exception
    {
        k3po.finish();
    }

    @Ignore
    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.reject.qos1.without.packet.id/client"})
    public void shouldRejectPublishQos1WithoutPacketId() throws Exception
    {
        k3po.finish();
    }

    @Ignore
    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.reject.qos2.without.packet.id/client"})
    public void shouldRejectPublishQos2WithoutPacketId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.reject.retain.not.supported/client",
        "${app}/publish.reject.retain.not.supported/server"})
    public void shouldRejectPublishRetainNotSupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.with.user.property/client",
        "${app}/publish.with.user.property/server"})
    public void shouldPublishWithUserProperty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.with.user.properties.distinct/client",
        "${app}/publish.with.user.properties.distinct/server"})
    public void shouldPublishWithDistinctUserProperties() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.with.user.properties.repeated/client",
        "${app}/publish.with.user.properties.repeated/server"})
    public void shouldPublishWithRepeatedUserProperties() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.empty.retained.message/client",
        "${app}/publish.empty.retained.message/server"})
    public void shouldPublishEmptyRetainedMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.empty.message/client",
        "${app}/publish.empty.message/server"})
    public void shouldPublishEmptyMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.route.non.default.yaml")
    @Specification({
        "${net}/publish.unroutable/client",
        "${app}/publish.unroutable/server"})
    public void shouldRejectUnroutable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.qos1.dup.after.puback/client",
        "${app}/publish.qos1.dup.after.puback/server"})
    public void shouldPublishQoS1Message() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.qos2.no.dupicate.before.pubrel/client",
        "${app}/publish.qos2.no.dupicate.before.pubrel/server"})
    public void shouldPublishQoS2Message() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.qos2.ack.with.reasoncode/client",
        "${app}/publish.qos2.ack.with.reasoncode/server"})
    public void shouldPublishQoS2MessageAckWithReasoncode() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.qos2.recovery/client",
        "${app}/publish.qos2.recovery/server"})
    public void shouldReleaseQos2PacketIdDuringRecovery() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.mixture.qos/client",
        "${app}/publish.mixture.qos/server"})
    public void shouldPublishMixtureQos() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.10k/client",
        "${app}/publish.10k/server"})
    @Configure(name = ENGINE_BUFFER_SLOT_CAPACITY_NAME, value = "8192")
    public void shouldPublish10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.reject.large.message/client",
        "${app}/publish.reject.large.message/server"})
    public void shouldRejectLargeMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.when.topic.params.yaml")
    @Specification({
        "${net}/publish.topic.guarded.identity.param/client",
        "${app}/publish.topic.guarded.identity.param/server"})
    public void shouldPublishToTopicWithGuardedIdentityParam() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.when.topic.params.yaml")
    @Specification({
        "${net}/publish.invalid.topic.guarded.identity.param/client",
        "${app}/publish.invalid.topic.guarded.identity.param/server"})
    public void shouldRejectTopicWithInvalidGuardedIdentityParam() throws Exception
    {
        k3po.finish();
    }
}
