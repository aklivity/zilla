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
package io.aklivity.zilla.specs.binding.mqtt.streams.network.v5;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

public class PublishIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/mqtt/streams/network/v5");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/publish.one.message.properties/client",
        "${net}/publish.one.message.properties/server"})
    public void shouldSendOneMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.multiple.messages/client",
        "${net}/publish.multiple.messages/server"})
    public void shouldSendMultipleMessages() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.multiple.messages.unfragmented/client",
        "${net}/publish.multiple.messages.unfragmented/server"})
    public void shouldSendMultipleMessagesUnfragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.multiple.messages.with.delay/client",
        "${net}/publish.multiple.messages.with.delay/server"})
    public void shouldSendMultipleMessagesWithDelay() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("PUBLISHED_MESSAGE_TWO");
        k3po.notifyBarrier("PUBLISH_MESSAGE_THREE");
        k3po.finish();
    }

    // [MQTT-2.2.1-2]
    @Test
    @Specification({
        "${net}/publish.reject.qos0.with.packet.id/client",
        "${net}/publish.reject.qos0.with.packet.id/server"})
    public void shouldRejectQos0WithPackedId() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.2.2-9], [MQTT-3.2.2-12]
    @Test
    @Specification({
        "${net}/publish.reject.qos1.not.supported/client",
        "${net}/publish.reject.qos1.not.supported/server"})
    public void shouldRejectQos1NotSupported() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.2.2-9], [MQTT-3.2.2-12]
    @Test
    @Specification({
        "${net}/publish.reject.qos2.not.supported/client",
        "${net}/publish.reject.qos2.not.supported/server"})
    public void shouldRejectQos2NotSupported() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-2.2.1-3]
    @Test
    @Specification({
        "${net}/publish.reject.qos1.without.packet.id/client",
        "${net}/publish.reject.qos1.without.packet.id/server"})
    public void shouldRejectQos1WithoutPackedId() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-2.2.1-3]
    @Test
    @Specification({
        "${net}/publish.reject.qos2.without.packet.id/client",
        "${net}/publish.reject.qos2.without.packet.id/server"})
    public void shouldRejectQos2WithoutPackedId() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-2.2.1-3]
    @Test
    @Specification({
        "${net}/publish.reject.invalid.payload.format/client",
        "${net}/publish.reject.invalid.payload.format/server"})
    public void shouldRejectInvalidPayloadFormat() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.3.4-6]
    @Test
    @Specification({
        "${net}/publish.reject.client.sent.subscription.id/client",
        "${net}/publish.reject.client.sent.subscription.id/server"})
    public void shouldRejectClientSentSubscriptionId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.reject.topic.alias.exceeds.maximum/client",
        "${net}/publish.reject.topic.alias.exceeds.maximum/server"})
    public void shouldRejectWhenTopicAliasExceedsThanMaximum() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.2.2-14]
    @Test
    @Specification({
        "${net}/publish.reject.retain.not.supported/client",
        "${net}/publish.reject.retain.not.supported/server"})
    public void shouldRejectRetainedRetainNotSupported() throws Exception
    {
        k3po.finish();
    }


    @Test
    @Specification({
        "${net}/publish.reject.topic.alias.repeated/client",
        "${net}/publish.reject.topic.alias.repeated/server"})
    public void shouldRejectWhenTopicAliasRepeated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.topic.not.routed/client",
        "${net}/publish.topic.not.routed/server"})
    public void shouldRejectTopicNotRouted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.with.user.property/client",
        "${net}/publish.with.user.property/server"})
    public void shouldSendWithUserProperty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.with.user.properties.repeated/client",
        "${net}/publish.with.user.properties.repeated/server"})
    public void shouldSendWithRepeatedUserProperties() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.with.user.properties.distinct/client",
        "${net}/publish.with.user.properties.distinct/server"})
    public void shouldSendWithDistinctUserProperties() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.message.with.topic.alias/client",
        "${net}/publish.message.with.topic.alias/server"})
    public void shouldSendMessageWithTopicAlias() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.messages.with.topic.alias.distinct/client",
        "${net}/publish.messages.with.topic.alias.distinct/server"})
    public void shouldSendMessagesWithTopicAliasDistinct() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.messages.with.topic.alias.repeated/client",
        "${net}/publish.messages.with.topic.alias.repeated/server"})
    public void shouldSendMessagesWithTopicAliasRepeated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.messages.with.topic.alias.replaced/client",
        "${net}/publish.messages.with.topic.alias.replaced/server"})
    public void shouldSendMessagesWithTopicAliasReplaced() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.3.2-7]
    @Test
    @Specification({
        "${net}/publish.messages.with.topic.alias.invalid.scope/client",
        "${net}/publish.messages.with.topic.alias.invalid.scope/server"})
    public void shouldSendMessagesWithTopicAliasInvalidScope() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.retained/client",
        "${net}/publish.retained/server"})
    public void shouldPublishRetainedMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.empty.retained.message/client",
        "${net}/publish.empty.retained.message/server"})
    public void shouldSendEmptyRetainedMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.empty.message/client",
        "${net}/publish.empty.message/server"})
    public void shouldSendEmptyMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.publish.no.local/client",
        "${net}/subscribe.publish.no.local/server"})
    public void shouldSubscribeThenSendNoLocal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.subscribe.batched/client",
        "${net}/publish.subscribe.batched/server"})
    public void shouldPublishSubscribeBatched() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.reject.packet.too.large/client",
        "${net}/publish.reject.packet.too.large/server"})
    public void shouldRejectPacketTooLarge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.invalid.message/client",
        "${net}/publish.invalid.message/server"})
    public void shouldPublishInvalidMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.valid.message/client",
        "${net}/publish.valid.message/server"})
    public void shouldPublishValidMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.unroutable/client",
        "${net}/publish.unroutable/server"})
    public void shouldRejectUnroutable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.qos1.dup.after.puback/client",
        "${net}/publish.qos1.dup.after.puback/server"})
    public void shouldPublishQoS1Message() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.qos2.no.dupicate.before.pubrel/client",
        "${net}/publish.qos2.no.dupicate.before.pubrel/server"})
    public void shouldPublishQoS2NoDupBeforePubrel() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.qos2.ack.with.reasoncode/client",
        "${net}/publish.qos2.ack.with.reasoncode/server"})
    public void shouldPublishQoS2MessageAckWithReasoncode() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.mixture.qos/client",
        "${net}/publish.mixture.qos/server"})
    public void shouldPublishMixtureQos() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.10k/client",
        "${net}/publish.10k/server"})
    public void shouldPublish10k() throws Exception
    {
        k3po.finish();
    }
}
