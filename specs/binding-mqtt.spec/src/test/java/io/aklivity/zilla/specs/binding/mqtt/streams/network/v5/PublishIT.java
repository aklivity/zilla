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
package io.aklivity.zilla.specs.binding.mqtt.streams.network.v5;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class PublishIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/mqtt/streams/network/v5");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

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
        "${net}/publish.qos1.reject.retain.not.supported/client",
        "${net}/publish.qos1.reject.retain.not.supported/server"})
    public void shouldRejectQos1RetainedRetainNotSupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.qos2.reject.retain.not.supported/client",
        "${net}/publish.qos2.reject.retain.not.supported/server"})
    public void shouldRejectQos2RetainedRetainNotSupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.qos1.reset.mid.payload/client",
        "${net}/publish.qos1.reset.mid.payload/server"})
    public void shouldAcknowledgeQos1PublishResetMidPayload() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.qos1.reset.in.flight/client",
        "${net}/publish.qos1.reset.in.flight/server"})
    public void shouldAcknowledgeQos1PublishesInFlightReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.qos2.reset/client",
        "${net}/publish.qos2.reset/server"})
    public void shouldAcknowledgeQos2PublishReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.qos1.reset/client",
        "${net}/publish.qos1.reset/server"})
    public void shouldAcknowledgeQos1PublishReset() throws Exception
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
        "${net}/publish.retained.multiple.topic/client",
        "${net}/publish.retained.multiple.topic/server"})
    public void shouldPublishRetainedMessageMultipleTopic() throws Exception
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
        "${net}/publish.valid.user.property/client",
        "${net}/publish.valid.user.property/server"})
    public void shouldPublishValidUserProperty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.invalid.user.property/client",
        "${net}/publish.invalid.user.property/server"})
    public void shouldNotPublishInvalidUserProperty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.message.transform.grow/client",
        "${net}/publish.message.transform.grow/server"})
    public void shouldPublishMessageTransformGrow() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.message.transform.shrink/client",
        "${net}/publish.message.transform.shrink/server"})
    public void shouldPublishMessageTransformShrink() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.user.property.transform.shrink/client",
        "${net}/publish.user.property.transform.shrink/server"})
    public void shouldPublishUserPropertyTransformShrink() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.user.property.transform.grow/client",
        "${net}/publish.user.property.transform.grow/server"})
    public void shouldPublishUserPropertyTransformGrow() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.message.too.large/client",
        "${net}/publish.message.too.large/server"})
    public void shouldRejectPublishMessageTooLarge() throws Exception
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
        "${net}/publish.qos1.two.topics.interleaved.acks/client",
        "${net}/publish.qos1.two.topics.interleaved.acks/server"})
    public void shouldPublishQoS1MessagesToTwoTopicsWithInterleavedAcks() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.qos1.two.messages.one.ack/client",
        "${net}/publish.qos1.two.messages.one.ack/server"})
    public void shouldPublishTwoQoS1MessagesAcknowledgedTogether() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.qos2.two.topics.interleaved.acks/client",
        "${net}/publish.qos2.two.topics.interleaved.acks/server"})
    public void shouldPublishQoS2MessagesToTwoTopicsWithInterleavedAcks() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.qos2.two.messages.one.ack/client",
        "${net}/publish.qos2.two.messages.one.ack/server"})
    public void shouldPublishTwoQoS2MessagesAcknowledgedTogether() throws Exception
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
        "${net}/publish.pipelined.qos1.ack.out.of.order/client",
        "${net}/publish.pipelined.qos1.ack.out.of.order/server"})
    public void shouldPublishPipelinedQos1AckOutOfOrder() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.pipelined.qos2.ack.out.of.order/client",
        "${net}/publish.pipelined.qos2.ack.out.of.order/server"})
    public void shouldPublishPipelinedQos2AckOutOfOrder() throws Exception
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

    @Test
    @Specification({
        "${net}/publish.reject.large.message/client",
        "${net}/publish.reject.large.message/server"})
    public void shouldRejectLargeMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.topic.guarded.identity.param/client",
        "${net}/publish.topic.guarded.identity.param/server"})
    public void shouldPublishToTopicWithGuardedIdentityParam() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.invalid.topic.guarded.identity.param/client",
        "${net}/publish.invalid.topic.guarded.identity.param/server"})
    public void shouldRejectTopicWithInvalidGuardedIdentityParam() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.retained.per.connection/client",
        "${net}/publish.retained.per.connection/server"})
    public void shouldPublishNonRetainedAfterRetainedOnOtherConnection() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/ack.pubrel.unknown.packet.id/client",
        "${net}/ack.pubrel.unknown.packet.id/server"})
    public void shouldCompletePubrelWithUnknownPacketId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/recovery.pubrel.unknown.packet.id/client",
        "${net}/recovery.pubrel.unknown.packet.id/server"})
    public void shouldCompleteRecoveredPubrelWithUnknownPacketId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.qos1.unroutable/client",
        "${net}/publish.qos1.unroutable/server"})
    public void shouldPublishQos1Unroutable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.qos2.unroutable/client",
        "${net}/publish.qos2.unroutable/server"})
    public void shouldPublishQos2Unroutable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.qos1.message.rejected/client",
        "${net}/publish.qos1.message.rejected/server"})
    public void shouldPublishQos1MessageRejected() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.qos1.message.rejected.in.flight.acknowledged/client",
        "${net}/publish.qos1.message.rejected.in.flight.acknowledged/server"})
    public void shouldPublishQos1MessageRejectedInFlightAcknowledged() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.qos2.message.rejected/client",
        "${net}/publish.qos2.message.rejected/server"})
    public void shouldPublishQos2MessageRejected() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.reject.invalid.property.identifier/client",
        "${net}/publish.reject.invalid.property.identifier/server"})
    public void shouldPublishRejectInvalidPropertyIdentifier() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.sent.publish.invalid.property.identifier/client",
        "${net}/server.sent.publish.invalid.property.identifier/server"})
    public void shouldServerSentPublishInvalidPropertyIdentifier() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.sent.publish.too.short/client",
        "${net}/server.sent.publish.too.short/server"})
    public void shouldServerSentPublishTooShort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.sent.publish.topic.alias.zero/client",
        "${net}/server.sent.publish.topic.alias.zero/server"})
    public void shouldServerSentPublishTopicAliasZero() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.sent.publish.topic.alias.exceeds.maximum/client",
        "${net}/server.sent.publish.topic.alias.exceeds.maximum/server"})
    public void shouldServerSentPublishTopicAliasExceedsMaximum() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.sent.publish.topic.alias.unmapped/client",
        "${net}/server.sent.publish.topic.alias.unmapped/server"})
    public void shouldServerSentPublishTopicAliasUnmapped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.sent.publish.qos.exceeds.maximum/client",
        "${net}/server.sent.publish.qos.exceeds.maximum/server"})
    public void shouldServerSentPublishQosExceedsMaximum() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.topic.multibyte/client",
        "${net}/publish.topic.multibyte/server"})
    public void shouldSendOneMessageWithMultibyteTopic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.reject.topic.name.wildcard/client",
        "${net}/publish.reject.topic.name.wildcard/server"})
    public void shouldRejectPublishTopicNameWithWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.reject.topic.name.empty/client",
        "${net}/publish.reject.topic.name.empty/server"})
    public void shouldRejectPublishTopicNameEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.reject.topic.name.null.character/client",
        "${net}/publish.reject.topic.name.null.character/server"})
    public void shouldRejectPublishTopicNameWithNullCharacter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.reject.response.topic.wildcard/client",
        "${net}/publish.reject.response.topic.wildcard/server"})
    public void shouldRejectPublishResponseTopicWithWildcard() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-1.5.4-1]
    @Test
    @Specification({
        "${net}/publish.reject.topic.name.ill.formed.utf8/client",
        "${net}/publish.reject.topic.name.ill.formed.utf8/server"})
    public void shouldRejectPublishTopicNameIllFormedUtf8() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-1.5.4-1]
    @Test
    @Specification({
        "${net}/publish.reject.content.type.ill.formed.utf8/client",
        "${net}/publish.reject.content.type.ill.formed.utf8/server"})
    public void shouldRejectPublishContentTypeIllFormedUtf8() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-1.5.4-1]
    @Test
    @Specification({
        "${net}/publish.reject.user.property.key.ill.formed.utf8/client",
        "${net}/publish.reject.user.property.key.ill.formed.utf8/server"})
    public void shouldRejectPublishUserPropertyKeyIllFormedUtf8() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-1.5.4-1]
    @Test
    @Specification({
        "${net}/publish.reject.user.property.value.ill.formed.utf8/client",
        "${net}/publish.reject.user.property.value.ill.formed.utf8/server"})
    public void shouldRejectPublishUserPropertyValueIllFormedUtf8() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-1.5.4-2]
    @Test
    @Specification({
        "${net}/publish.reject.content.type.null.character/client",
        "${net}/publish.reject.content.type.null.character/server"})
    public void shouldRejectPublishContentTypeNullCharacter() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-1.5.4-2]
    @Test
    @Specification({
        "${net}/publish.reject.user.property.key.null.character/client",
        "${net}/publish.reject.user.property.key.null.character/server"})
    public void shouldRejectPublishUserPropertyKeyNullCharacter() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-1.5.4-2]
    @Test
    @Specification({
        "${net}/publish.reject.user.property.value.null.character/client",
        "${net}/publish.reject.user.property.value.null.character/server"})
    public void shouldRejectPublishUserPropertyValueNullCharacter() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-1.5.4-1]
    @Test
    @Specification({
        "${net}/publish.reject.response.topic.ill.formed.utf8/client",
        "${net}/publish.reject.response.topic.ill.formed.utf8/server"})
    public void shouldRejectPublishResponseTopicIllFormedUtf8() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-1.5.4-1]
    @Test
    @Specification({
        "${net}/server.sent.publish.topic.name.ill.formed.utf8/client",
        "${net}/server.sent.publish.topic.name.ill.formed.utf8/server"})
    public void shouldServerSentPublishTopicNameIllFormedUtf8() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-1.5.4-1]
    @Test
    @Specification({
        "${net}/server.sent.publish.content.type.ill.formed.utf8/client",
        "${net}/server.sent.publish.content.type.ill.formed.utf8/server"})
    public void shouldServerSentPublishContentTypeIllFormedUtf8() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-1.5.4-1]
    @Test
    @Specification({
        "${net}/server.sent.publish.response.topic.ill.formed.utf8/client",
        "${net}/server.sent.publish.response.topic.ill.formed.utf8/server"})
    public void shouldServerSentPublishResponseTopicIllFormedUtf8() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-1.5.4-1]
    @Test
    @Specification({
        "${net}/server.sent.publish.user.property.key.ill.formed.utf8/client",
        "${net}/server.sent.publish.user.property.key.ill.formed.utf8/server"})
    public void shouldServerSentPublishUserPropertyKeyIllFormedUtf8() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-1.5.4-1]
    @Test
    @Specification({
        "${net}/server.sent.publish.user.property.value.ill.formed.utf8/client",
        "${net}/server.sent.publish.user.property.value.ill.formed.utf8/server"})
    public void shouldServerSentPublishUserPropertyValueIllFormedUtf8() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-1.5.4-2]
    @Test
    @Specification({
        "${net}/server.sent.publish.topic.name.null.character/client",
        "${net}/server.sent.publish.topic.name.null.character/server"})
    public void shouldServerSentPublishTopicNameNullCharacter() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-1.5.4-2]
    @Test
    @Specification({
        "${net}/server.sent.publish.content.type.null.character/client",
        "${net}/server.sent.publish.content.type.null.character/server"})
    public void shouldServerSentPublishContentTypeNullCharacter() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-1.5.4-2]
    @Test
    @Specification({
        "${net}/server.sent.publish.response.topic.null.character/client",
        "${net}/server.sent.publish.response.topic.null.character/server"})
    public void shouldServerSentPublishResponseTopicNullCharacter() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-1.5.4-2]
    @Test
    @Specification({
        "${net}/server.sent.publish.user.property.key.null.character/client",
        "${net}/server.sent.publish.user.property.key.null.character/server"})
    public void shouldServerSentPublishUserPropertyKeyNullCharacter() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-1.5.4-2]
    @Test
    @Specification({
        "${net}/server.sent.publish.user.property.value.null.character/client",
        "${net}/server.sent.publish.user.property.value.null.character/server"})
    public void shouldServerSentPublishUserPropertyValueNullCharacter() throws Exception
    {
        k3po.finish();
    }

    // MQTT 5.0, 3.1.2.11.4 Maximum Packet Size, 3.2.2.3.6 Packet too large
    @Test
    @Specification({
        "${net}/publish.reject.maximum.packet.size/client",
        "${net}/publish.reject.maximum.packet.size/server"})
    public void shouldRejectPublishExceedingMaximumPacketSize() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.3.1-4]
    @Test
    @Specification({
        "${net}/publish.reject.qos.both.bits/client",
        "${net}/publish.reject.qos.both.bits/server"})
    public void shouldRejectPublishWithBothQosBitsSet() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.3.1-2]
    @Test
    @Specification({
        "${net}/publish.reject.dup.qos0/client",
        "${net}/publish.reject.dup.qos0/server"})
    public void shouldRejectPublishDupWithQos0() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-2.2.1-3]
    @Test
    @Specification({
        "${net}/publish.reject.packet.id.zero/client",
        "${net}/publish.reject.packet.id.zero/server"})
    public void shouldRejectPublishPacketIdZero() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-4.13.1-1]
    @Test
    @Specification({
        "${net}/publish.reject.truncated.body/client",
        "${net}/publish.reject.truncated.body/server"})
    public void shouldRejectPublishTruncatedBody() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-4.13.1-1]
    @Test
    @Specification({
        "${net}/puback.reject.truncated.body/client",
        "${net}/puback.reject.truncated.body/server"})
    public void shouldRejectPubackTruncatedBody() throws Exception
    {
        k3po.finish();
    }
}
