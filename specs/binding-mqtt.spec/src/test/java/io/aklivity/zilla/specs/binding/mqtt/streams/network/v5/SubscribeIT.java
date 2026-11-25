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

public class SubscribeIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/mqtt/streams/network/v5");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/subscribe.retained.not.supported/client",
        "${net}/subscribe.retained.not.supported/server"})
    public void shouldSubscribeWithoutRetainedReplayWhenRetainNotSupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.one.message/client",
        "${net}/subscribe.one.message/server"})
    public void shouldReceiveOneMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.one.message.user.properties.unaltered/client",
        "${net}/subscribe.one.message.user.properties.unaltered/server"})
    public void shouldReceiveOneMessageWithUserPropertiesUnaltered() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.receive.message.wildcard/client",
        "${net}/subscribe.receive.message.wildcard/server"})
    public void shouldReceiveOneMessageWithPatternTopic() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-2.2.1-3]

    @Test
    @Specification({
        "${net}/subscribe.reject.omitted.packet.id/client",
        "${net}/subscribe.reject.omitted.packet.id/server"})
    public void shouldRejectOmittedPacketId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.reject.missing.topic.filters/client",
        "${net}/subscribe.reject.missing.topic.filters/server"})
    public void shouldRejectWithMissingTopicFilters() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.invalid.topic.filter/client",
        "${net}/subscribe.invalid.topic.filter/server"})
    public void shouldRejectInvalidTopicFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.topic.filter.single.exact/client",
        "${net}/subscribe.topic.filter.single.exact/server"})
    public void shouldFilterExact() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.topic.filter.multi.level.wildcard/client",
        "${net}/subscribe.topic.filter.multi.level.wildcard/server"})
    public void shouldFilterMultiLevelWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.topic.filter.single.level.wildcard/client",
        "${net}/subscribe.topic.filter.single.level.wildcard/server"})
    public void shouldFilterSingleLevelWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.topic.filter.two.single.level.wildcard/client",
        "${net}/subscribe.topic.filter.two.single.level.wildcard/server"})
    public void shouldFilterTwoSingleLevelWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.topic.filter.single.and.multi.level.wildcard/client",
        "${net}/subscribe.topic.filter.single.and.multi.level.wildcard/server"})
    public void shouldFilterSingleAndMultiLevelWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.topic.filters.disjoint.wildcards/client",
        "${net}/subscribe.topic.filters.disjoint.wildcards/server"})
    public void shouldFilterDisjointWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.topic.filters.overlapping.wildcards/client",
        "${net}/subscribe.topic.filters.overlapping.wildcards/server"})
    public void shouldFilterOverlappingWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.reject.topic.filter.invalid.wildcard/client",
        "${net}/subscribe.reject.topic.filter.invalid.wildcard/server"})
    public void shouldRejectTopicFilterInvalidWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.topic.filters.aggregated.both.exact/client",
        "${net}/subscribe.topic.filters.aggregated.both.exact/server"})
    public void shouldFilterAggregatedBothExact() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.topic.filters.isolated.both.exact/client",
        "${net}/subscribe.topic.filters.isolated.both.exact/server"})
    public void shouldFilterIsolatedBothExact() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.topic.filters.isolated.both.exact.no.subscription.id/client",
        "${net}/subscribe.topic.filters.isolated.both.exact.no.subscription.id/server"})
    public void shouldFilterIsolatedBothExactNoSubscriptionId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.topic.filters.isolated.both.wildcard/client",
        "${net}/subscribe.topic.filters.isolated.both.wildcard/server"})
    public void shouldFilterIsolatedBothWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.topic.filters.aggregated.exact.and.wildcard/client",
        "${net}/subscribe.topic.filters.aggregated.exact.and.wildcard/server"})
    public void shouldFilterAggregatedExactAndWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.topic.filters.isolated.exact.and.wildcard/client",
        "${net}/subscribe.topic.filters.isolated.exact.and.wildcard/server"})
    public void shouldFilterIsolatedExactAndWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.invalid.fixed.header.flags/client",
        "${net}/subscribe.invalid.fixed.header.flags/server"})
    public void shouldRejectMalformedPacket() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.reject.no.local/client",
        "${net}/subscribe.reject.no.local/server"})
    public void shouldRejectNoLocal() throws Exception
    {
        k3po.finish();
    }


    //  [MQTT-3.3.2-15], [MQTT-3.3.2-16]

    @Test
    @Specification({
        "${net}/subscribe.one.message.receive.response.topic.and.correlation.data/client",
        "${net}/subscribe.one.message.receive.response.topic.and.correlation.data/server"})
    public void shouldReceiveCorrelationData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.one.message.with.invalid.subscription.id/client",
        "${net}/subscribe.one.message.with.invalid.subscription.id/server"})
    public void shouldReceiveOneMessageWithInvalidSubscriptionId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.receive.message/client",
        "${net}/subscribe.receive.message/server"})
    public void shouldReceiveOneMessageAfterPublish() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.get.retained.as.published/client",
        "${net}/subscribe.get.retained.as.published/server"})
    public void shouldReceiveRetainedAsPublished() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.retain.as.published.not.retained/client",
        "${net}/subscribe.retain.as.published.not.retained/server"})
    public void shouldNotReceiveRetainedAsPublishedWhenNotRetained() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.reject.wildcard.subscriptions.not.supported/client",
        "${net}/subscribe.reject.wildcard.subscriptions.not.supported/server"})
    public void shouldRejectWildcardSubscriptionsNotSupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.reject.subscription.ids.not.supported/client",
        "${net}/subscribe.reject.subscription.ids.not.supported/server"})
    public void shouldRejectSubscriptionIdentifiersNotSupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.reject.shared.subscriptions.not.supported/client",
        "${net}/subscribe.reject.shared.subscriptions.not.supported/server"})
    public void shouldRejectSharedSubscriptionsNotSupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.qos0.replay.retained.no.packet.id/client",
        "${net}/subscribe.qos0.replay.retained.no.packet.id/server"})
    public void shouldReceiveAndReplayRetainedQos0() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.retain.handling.1.new.filter/client",
        "${net}/subscribe.retain.handling.1.new.filter/server"})
    public void shouldSubscribeRetainHandling1NewFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.retain.handling.1.existing.filter/client",
        "${net}/subscribe.retain.handling.1.existing.filter/server"})
    public void shouldSubscribeRetainHandling1ExistingFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.retain.handling.0.existing.filter/client",
        "${net}/subscribe.retain.handling.0.existing.filter/server"})
    public void shouldSubscribeRetainHandling0ExistingFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.qos0.publish.retained.no.replay/client",
        "${net}/subscribe.qos0.publish.retained.no.replay/server"})
    public void shouldNotReplayRetained() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.receive.messages.topic.alias.repeated/client",
        "${net}/subscribe.receive.messages.topic.alias.repeated/server"})
    public void shouldReceiveMessagesTopicAliasRepeated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.topic.filters.non.successful/client",
        "${net}/subscribe.topic.filters.non.successful/server"})
    public void shouldFilterNonSuccessful() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.reconnect.publish.no.subscription/client",
        "${net}/subscribe.reconnect.publish.no.subscription/server"})
    public void shouldReceiveReconnectNoSubscription() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.unroutable/client",
        "${net}/subscribe.unroutable/server"})
    public void shouldSubscribeUnroutable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.receive.message.qos1/client",
        "${net}/subscribe.receive.message.qos1/server"})
    public void shouldReceiveMessageQoS1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.receive.message.qos0.published.qos/client",
        "${net}/subscribe.receive.message.qos0.published.qos/server"})
    public void shouldReceiveMessageQoS0PublishedAsQoS1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.receive.message.qos2/client",
        "${net}/subscribe.receive.message.qos2/server"})
    public void shouldReceiveMessageQoS2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.pubrel.while.publish.fragmented/client",
        "${net}/subscribe.pubrel.while.publish.fragmented/server"})
    public void shouldDeferPubrelWhilePublishFragmentedQoS2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.pingresp.while.publish.fragmented/client",
        "${net}/subscribe.pingresp.while.publish.fragmented/server"})
    public void shouldDeferPingRespWhilePublishFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.end.while.publish.fragmented/client",
        "${net}/subscribe.end.while.publish.fragmented/server"})
    public void shouldAbortNetworkWhenSubscribeEndsWhilePublishFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.abort.while.publish.fragmented/client",
        "${net}/subscribe.abort.while.publish.fragmented/server"})
    public void shouldAbortNetworkWhenSubscribeAbortsWhilePublishFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.receive.message.qos2.pubrec.failure.reason.codes/client",
        "${net}/subscribe.receive.message.qos2.pubrec.failure.reason.codes/server"})
    public void shouldReceiveMessageQoS2AfterPubrecFailureReasonCodes() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.receive.message.qos1.puback.failure.reason.code/client",
        "${net}/subscribe.receive.message.qos1.puback.failure.reason.code/server"})
    public void shouldReceiveMessageQoS1AfterPubackFailureReasonCode() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.receive.messages.mixture.qos/client",
        "${net}/subscribe.receive.messages.mixture.qos/server"})
    public void shouldReceiveMessagesMixtureQos() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.reconnect.replay.qos1.unacked.message/client",
        "${net}/subscribe.reconnect.replay.qos1.unacked.message/server"})
    public void shouldReplayUnackedQoS1MessageAtReconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.reconnect.replay.qos2.unreceived.message/client",
        "${net}/subscribe.reconnect.replay.qos2.unreceived.message/server"})
    public void shouldReplayUnreceivedQoS2MessageAtReconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.reconnect.replay.qos2.incomplete.message/client",
        "${net}/subscribe.reconnect.replay.qos2.incomplete.message/server"})
    public void shouldReplayIncompleteQoS2MessageAtReconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.qos1.reject.subscription.ids.not.supported/client",
        "${net}/subscribe.qos1.reject.subscription.ids.not.supported/server"})
    public void shouldRejectQos1SubscriptionIdentifiersNotSupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.qos2.reject.subscription.ids.not.supported/client",
        "${net}/subscribe.qos2.reject.subscription.ids.not.supported/server"})
    public void shouldRejectQos2SubscriptionIdentifiersNotSupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.replay.retained.message.qos1/client",
        "${net}/subscribe.replay.retained.message.qos1/server"})
    public void shouldReplayRetainedQos1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.replay.retained.message.qos2/client",
        "${net}/subscribe.replay.retained.message.qos2/server"})
    public void shouldReplayRetainedQos2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.receive.message.overlapping.wildcard.mixed.qos/client",
        "${net}/subscribe.receive.message.overlapping.wildcard.mixed.qos/server"})
    public void shouldReceiveMessageOverlappingWildcardMixedQos() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.receive.message.dollar.topic/client",
        "${net}/subscribe.receive.message.dollar.topic/server"})
    public void shouldReceiveMessageDollarTopic() throws Exception
    {
        k3po.finish();
    }

    @Specification({
        "${net}/subscribe.topic.guarded.identity.param/client",
        "${net}/subscribe.topic.guarded.identity.param/server"})
    public void shouldFilterGuardedIdentityParam() throws Exception
    {
        k3po.finish();
    }

    @Specification({
        "${net}/subscribe.invalid.topic.guarded.identity.param/client",
        "${net}/subscribe.invalid.topic.guarded.identity.param/server"})
    public void shouldSubscribeInvalidTopicGuardedIdentityParam() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.qos1.drop.exceeds.max.packet.size/client",
        "${net}/subscribe.qos1.drop.exceeds.max.packet.size/server"})
    public void shouldCompleteDroppedQoS1MessageExceedingMaximumPacketSize() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.qos2.drop.exceeds.max.packet.size/client",
        "${net}/subscribe.qos2.drop.exceeds.max.packet.size/server"})
    public void shouldCompleteDroppedQoS2MessageExceedingMaximumPacketSize() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/ack.puback.unknown.packet.id/client",
        "${net}/ack.puback.unknown.packet.id/server"})
    public void shouldIgnorePubackWithUnknownPacketId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/ack.pubrec.unknown.packet.id/client",
        "${net}/ack.pubrec.unknown.packet.id/server"})
    public void shouldReleasePubrecWithUnknownPacketId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/ack.pubcomp.unknown.packet.id/client",
        "${net}/ack.pubcomp.unknown.packet.id/server"})
    public void shouldIgnorePubcompWithUnknownPacketId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/ack.pubrec.failure.reason.code.unknown.packet.id/client",
        "${net}/ack.pubrec.failure.reason.code.unknown.packet.id/server"})
    public void shouldIgnorePubrecFailureWithUnknownPacketId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.topic.filters.unroutable.mixed/client",
        "${net}/subscribe.topic.filters.unroutable.mixed/server"})
    public void shouldSubscribeTopicFiltersUnroutableMixed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.topic.filters.unroutable.all/client",
        "${net}/subscribe.topic.filters.unroutable.all/server"})
    public void shouldSubscribeTopicFiltersUnroutableAll() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.topic.filter.unauthorized/client",
        "${net}/subscribe.topic.filter.unauthorized/server"})
    public void shouldSubscribeTopicFilterUnauthorized() throws Exception
    {
        k3po.finish();
    }

    // 3.8.3.1 Maximum QoS 3 is a Protocol Error

    @Test
    @Specification({
        "${net}/subscribe.reject.maximum.qos.3/client",
        "${net}/subscribe.reject.maximum.qos.3/server"})
    public void shouldRejectMaximumQos3() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.8.3-5]

    @Test
    @Specification({
        "${net}/subscribe.reject.reserved.option.bit.6/client",
        "${net}/subscribe.reject.reserved.option.bit.6/server"})
    public void shouldRejectReservedOptionBit6() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.8.3-5]

    @Test
    @Specification({
        "${net}/subscribe.reject.reserved.option.bit.7/client",
        "${net}/subscribe.reject.reserved.option.bit.7/server"})
    public void shouldRejectReservedOptionBit7() throws Exception
    {
        k3po.finish();
    }

    // 3.8.3.1 Retain Handling 3 is a Protocol Error

    @Test
    @Specification({
        "${net}/subscribe.reject.retain.handling.3/client",
        "${net}/subscribe.reject.retain.handling.3/server"})
    public void shouldRejectRetainHandling3() throws Exception
    {
        k3po.finish();
    }

    // 2.2.1 [MQTT-2.2.1-3], 4.13.1 Protocol Error

    @Test
    @Specification({
        "${net}/subscribe.reject.packet.id.zero/client",
        "${net}/subscribe.reject.packet.id.zero/server"})
    public void shouldRejectPacketIdZero() throws Exception
    {
        k3po.finish();
    }

    // 3.8.2.1.2 Subscription Identifier more than once is a Protocol Error

    @Test
    @Specification({
        "${net}/subscribe.reject.subscription.id.repeated/client",
        "${net}/subscribe.reject.subscription.id.repeated/server"})
    public void shouldRejectSubscriptionIdRepeated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.topic.filters.non.successful.unsubscribe/client",
        "${net}/subscribe.topic.filters.non.successful.unsubscribe/server"})
    public void shouldSubscribeTopicFiltersNonSuccessfulUnsubscribe() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.downgrade.qos2.maximum.qos.1/client",
        "${net}/subscribe.downgrade.qos2.maximum.qos.1/server"})
    public void shouldSubscribeDowngradeQos2MaximumQos1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.downgrade.qos2.maximum.qos.0/client",
        "${net}/subscribe.downgrade.qos2.maximum.qos.0/server"})
    public void shouldSubscribeDowngradeQos2MaximumQos0() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.replace.same.qos/client",
        "${net}/subscribe.replace.same.qos/server"})
    public void shouldSubscribeReplaceSameQos() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.replace.same.options/client",
        "${net}/subscribe.replace.same.options/server"})
    public void shouldSubscribeReplaceSameOptions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.replace.downgrade.qos/client",
        "${net}/subscribe.replace.downgrade.qos/server"})
    public void shouldSubscribeReplaceDowngradeQos() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.replace.upgrade.qos/client",
        "${net}/subscribe.replace.upgrade.qos/server"})
    public void shouldSubscribeReplaceUpgradeQos() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.replace.add.subscription.id/client",
        "${net}/subscribe.replace.add.subscription.id/server"})
    public void shouldSubscribeReplaceAddSubscriptionId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.replace.remove.subscription.id/client",
        "${net}/subscribe.replace.remove.subscription.id/server"})
    public void shouldSubscribeReplaceRemoveSubscriptionId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.receive.message.subscription.id.112/client",
        "${net}/subscribe.receive.message.subscription.id.112/server"})
    public void shouldSubscribeReceiveMessageSubscriptionId112() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.receive.message.subscription.id.127/client",
        "${net}/subscribe.receive.message.subscription.id.127/server"})
    public void shouldSubscribeReceiveMessageSubscriptionId127() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.receive.message.subscription.id.255/client",
        "${net}/subscribe.receive.message.subscription.id.255/server"})
    public void shouldSubscribeReceiveMessageSubscriptionId255() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.sent.publish.payload.format.invalid/client",
        "${net}/server.sent.publish.payload.format.invalid/server"})
    public void shouldServerSentPublishPayloadFormatInvalid() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.8.3-5] reject discards the whole SUBSCRIBE, earlier filters not subscribed

    @Test
    @Specification({
        "${net}/subscribe.reject.second.filter.reserved.option.bit.6/client",
        "${net}/subscribe.reject.second.filter.reserved.option.bit.6/server"})
    public void shouldRejectSecondFilterReservedOptionBit6() throws Exception
    {
        k3po.finish();
    }

    // 1.2 Malformed Packet, 4.13.1 [MQTT-4.13.1-1], 3.8.3 filter that does not fit in the packet

    @Test
    @Specification({
        "${net}/subscribe.reject.second.filter.length.overruns.packet/client",
        "${net}/subscribe.reject.second.filter.length.overruns.packet/server"})
    public void shouldRejectSecondFilterLengthOverrunsPacket() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.sent.suback.too.short/client",
        "${net}/server.sent.suback.too.short/server"})
    public void shouldServerSentSubackTooShort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.sent.suback.invalid.fixed.header.flags/client",
        "${net}/server.sent.suback.invalid.fixed.header.flags/server"})
    public void shouldServerSentSubackInvalidFixedHeaderFlags() throws Exception
    {
        k3po.finish();
    }

    // 3.8.3.1 Maximum QoS 3 is a Protocol Error, earlier filters not subscribed

    @Test
    @Specification({
        "${net}/subscribe.reject.second.filter.maximum.qos.3/client",
        "${net}/subscribe.reject.second.filter.maximum.qos.3/server"})
    public void shouldRejectSecondFilterMaximumQos3() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.topic.multibyte/client",
        "${net}/subscribe.topic.multibyte/server"})
    public void shouldReceiveOneMessageWithMultibyteTopic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.reject.topic.filter.empty/client",
        "${net}/subscribe.reject.topic.filter.empty/server"})
    public void shouldRejectTopicFilterEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.reject.topic.filter.null.character/client",
        "${net}/subscribe.reject.topic.filter.null.character/server"})
    public void shouldRejectTopicFilterWithNullCharacter() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-1.5.4-1]

    @Test
    @Specification({
        "${net}/subscribe.reject.topic.filter.ill.formed.utf8/client",
        "${net}/subscribe.reject.topic.filter.ill.formed.utf8/server"})
    public void shouldRejectSubscribeTopicFilterIllFormedUtf8() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.shared.subscription/client",
        "${net}/subscribe.shared.subscription/server"})
    public void shouldSubscribeSharedSubscription() throws Exception
    {
        k3po.finish();
    }

    // MQTT 5.0, 3.3.4 [MQTT-3.3.4-9]
    @Test
    @Specification({
        "${net}/subscribe.shared.receive.maximum.two/client",
        "${net}/subscribe.shared.receive.maximum.two/server"})
    public void shouldSubscribeSharedReceiveMaximumTwo() throws Exception
    {
        k3po.finish();
    }

    // MQTT 5.0, 3.3.4 [MQTT-3.3.4-9]
    @Test
    @Specification({
        "${net}/subscribe.shared.receive.maximum.qos2/client",
        "${net}/subscribe.shared.receive.maximum.qos2/server"})
    public void shouldSubscribeSharedReceiveMaximumQos2() throws Exception
    {
        k3po.finish();
    }

    // MQTT 5.0, 3.3.4 [MQTT-3.3.4-9]
    @Test
    @Specification({
        "${net}/subscribe.shared.receive.maximum/client",
        "${net}/subscribe.shared.receive.maximum/server"})
    public void shouldSubscribeSharedReceiveMaximum() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.shared.distinct.share.names/client",
        "${net}/subscribe.shared.distinct.share.names/server"})
    public void shouldSubscribeSharedDistinctShareNames() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.shared.distinct.filters/client",
        "${net}/subscribe.shared.distinct.filters/server"})
    public void shouldSubscribeSharedDistinctFilters() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.shared.and.non.shared/client",
        "${net}/subscribe.shared.and.non.shared/server"})
    public void shouldSubscribeSharedAndNonShared() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.shared.resubscribe.change.qos/client",
        "${net}/subscribe.shared.resubscribe.change.qos/server"})
    public void shouldSubscribeSharedResubscribeChangeQos() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.resubscribe.identical/client",
        "${net}/subscribe.resubscribe.identical/server"})
    public void shouldSubscribeResubscribeIdentical() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.shared.resubscribe.identical/client",
        "${net}/subscribe.shared.resubscribe.identical/server"})
    public void shouldSubscribeSharedResubscribeIdentical() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.reject.shared.subscription.name.empty/client",
        "${net}/subscribe.reject.shared.subscription.name.empty/server"})
    public void shouldSubscribeRejectSharedSubscriptionNameEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.reject.shared.subscription.name.wildcard/client",
        "${net}/subscribe.reject.shared.subscription.name.wildcard/server"})
    public void shouldSubscribeRejectSharedSubscriptionNameWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.reject.shared.subscription.filter.missing/client",
        "${net}/subscribe.reject.shared.subscription.filter.missing/server"})
    public void shouldSubscribeRejectSharedSubscriptionFilterMissing() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.reject.shared.subscription.filter.empty/client",
        "${net}/subscribe.reject.shared.subscription.filter.empty/server"})
    public void shouldSubscribeRejectSharedSubscriptionFilterEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.reject.shared.subscription.no.local/client",
        "${net}/subscribe.reject.shared.subscription.no.local/server"})
    public void shouldSubscribeRejectSharedSubscriptionNoLocal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.unroutable.shared.subscription/client",
        "${net}/subscribe.unroutable.shared.subscription/server"})
    public void shouldSubscribeUnroutableSharedSubscription() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.shared.receive.message.qos2.granted.qos1/client",
        "${net}/subscribe.shared.receive.message.qos2.granted.qos1/server"})
    public void shouldSubscribeSharedReceiveMessageQos2GrantedQos1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.shared.receive.message.qos2/client",
        "${net}/subscribe.shared.receive.message.qos2/server"})
    public void shouldSubscribeSharedReceiveMessageQos2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.shared.receive.message.qos1.granted.qos0/client",
        "${net}/subscribe.shared.receive.message.qos1.granted.qos0/server"})
    public void shouldSubscribeSharedReceiveMessageQos1GrantedQos0() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.reconnect.shared.subscription/client",
        "${net}/subscribe.reconnect.shared.subscription/server"})
    public void shouldSubscribeReconnectSharedSubscription() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.reconnect.legacy.session.state/client",
        "${net}/subscribe.reconnect.legacy.session.state/server"})
    public void shouldSubscribeReconnectLegacySessionState() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.receive.message.qos1.ack.with.reasoncode/client",
        "${net}/subscribe.receive.message.qos1.ack.with.reasoncode/server"})
    public void shouldSubscribeReceiveMessageQos1AckWithReasoncode() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.receive.message.qos2.ack.with.reasoncode/client",
        "${net}/subscribe.receive.message.qos2.ack.with.reasoncode/server"})
    public void shouldSubscribeReceiveMessageQos2AckWithReasoncode() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.receive.message.qos2.duplicate.before.ack/client",
        "${net}/subscribe.receive.message.qos2.duplicate.before.ack/server"})
    public void shouldSubscribeReceiveMessageQos2DuplicateBeforeAck() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.receive.message.qos2.duplicate.after.ack/client",
        "${net}/subscribe.receive.message.qos2.duplicate.after.ack/server"})
    public void shouldSubscribeReceiveMessageQos2DuplicateAfterAck() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.receive.message.qos2.reuse.packet.id.after.complete/client",
        "${net}/subscribe.receive.message.qos2.reuse.packet.id.after.complete/server"})
    public void shouldSubscribeReceiveMessageQos2ReusePacketIdAfterComplete() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.receive.message.qos2.reuse.packet.id.after.error/client",
        "${net}/subscribe.receive.message.qos2.reuse.packet.id.after.error/server"})
    public void shouldSubscribeReceiveMessageQos2ReusePacketIdAfterError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.receive.messages.qos2.deferred.ack/client",
        "${net}/subscribe.receive.messages.qos2.deferred.ack/server"})
    public void shouldSubscribeReceiveMessagesQos2DeferredAck() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.receive.messages.qos2.ack.with.success.reasoncode/client",
        "${net}/subscribe.receive.messages.qos2.ack.with.success.reasoncode/server"})
    public void shouldSubscribeReceiveMessagesQos2AckWithSuccessReasoncode() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.shared.receive.message.qos1/client",
        "${net}/subscribe.shared.receive.message.qos1/server"})
    public void shouldSubscribeSharedReceiveMessageQos1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.shared.receive.message.dollar.topic/client",
        "${net}/subscribe.shared.receive.message.dollar.topic/server"})
    public void shouldSubscribeSharedReceiveMessageDollarTopic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.shared.receive.message.dollar.topic.filter/client",
        "${net}/subscribe.shared.receive.message.dollar.topic.filter/server"})
    public void shouldSubscribeSharedReceiveMessageDollarTopicFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.shared.receive.message.qos1.ack.with.reasoncode/client",
        "${net}/subscribe.shared.receive.message.qos1.ack.with.reasoncode/server"})
    public void shouldSubscribeSharedReceiveMessageQos1AckWithReasoncode() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.shared.subscription.upstream/client",
        "${net}/subscribe.shared.subscription.upstream/server"})
    public void shouldSubscribeSharedSubscriptionUpstream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.shared.and.non.shared.receive.messages/client",
        "${net}/subscribe.shared.and.non.shared.receive.messages/server"})
    public void shouldSubscribeSharedAndNonSharedReceiveMessages() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.suback.after.stream.begin/client",
        "${net}/subscribe.suback.after.stream.begin/server"})
    public void shouldSubackAfterStreamBegin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.qos1.suback.after.streams.begin/client",
        "${net}/subscribe.qos1.suback.after.streams.begin/server"})
    public void shouldSubackQos1AfterStreamsBegin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.shared.suback.after.stream.begin/client",
        "${net}/subscribe.shared.suback.after.stream.begin/server"})
    public void shouldSubackSharedAfterStreamBegin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.shared.filters.suback.after.streams.begin/client",
        "${net}/subscribe.shared.filters.suback.after.streams.begin/server"})
    public void shouldSubackSharedFiltersAfterStreamsBegin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.shared.and.non.shared.suback.after.streams.begin/client",
        "${net}/subscribe.shared.and.non.shared.suback.after.streams.begin/server"})
    public void shouldSubackSharedAndNonSharedAfterStreamsBegin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.reject.stream.reset.before.begin/client",
        "${net}/subscribe.reject.stream.reset.before.begin/server"})
    public void shouldRejectSubscribeWhenStreamResetBeforeBegin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.shared.reject.stream.reset.before.begin/client",
        "${net}/subscribe.shared.reject.stream.reset.before.begin/server"})
    public void shouldRejectSharedSubscribeWhenStreamResetBeforeBegin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.shared.filters.reject.one.stream.reset.before.begin/client",
        "${net}/subscribe.shared.filters.reject.one.stream.reset.before.begin/server"})
    public void shouldRejectOneOfSharedFiltersWhenStreamResetBeforeBegin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.reject.reconnect.non.clean.start/client",
        "${net}/subscribe.reject.reconnect.non.clean.start/server"})
    public void shouldNotRestoreRejectedSubscriptionOnReconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.shared.filters.reject.one.reconnect.non.clean.start/client",
        "${net}/subscribe.shared.filters.reject.one.reconnect.non.clean.start/server"})
    public void shouldRestoreOnlyAcceptedSharedFilterOnReconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.reject.qos2.stream.reset.before.begin.deliver.qos1/client",
        "${net}/subscribe.reject.qos2.stream.reset.before.begin.deliver.qos1/server"})
    public void shouldDeliverQos1WhenQos2StreamResetBeforeBegin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.restored.filter.reject.stream.reset.before.begin/client",
        "${net}/subscribe.restored.filter.reject.stream.reset.before.begin/server"})
    public void shouldDisconnectWhenResetStreamCarriesRestoredFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.back.to.back.reject.stream.reset.before.begin/client",
        "${net}/subscribe.back.to.back.reject.stream.reset.before.begin/server"})
    public void shouldRejectBackToBackSubscribesWhenStreamResetBeforeBegin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.restored.filter.reject.other.stream.reset.before.begin/client",
        "${net}/subscribe.restored.filter.reject.other.stream.reset.before.begin/server"})
    public void shouldRejectOtherStreamWithoutDisconnectingRestoredFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.reject.stream.abort.before.begin/client",
        "${net}/subscribe.reject.stream.abort.before.begin/server"})
    public void shouldRejectSubscribeWhenStreamAbortedBeforeBegin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.shared.reject.stream.abort.before.begin/client",
        "${net}/subscribe.shared.reject.stream.abort.before.begin/server"})
    public void shouldRejectSharedSubscribeWhenStreamAbortedBeforeBegin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.shared.filters.reject.one.stream.abort.before.begin/client",
        "${net}/subscribe.shared.filters.reject.one.stream.abort.before.begin/server"})
    public void shouldRejectOneOfSharedFiltersWhenStreamAbortedBeforeBegin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.restored.filter.reject.stream.abort.before.begin/client",
        "${net}/subscribe.restored.filter.reject.stream.abort.before.begin/server"})
    public void shouldDisconnectWhenAbortedStreamCarriesRestoredFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.shared.sequential.receive.messages.qos1/client",
        "${net}/subscribe.shared.sequential.receive.messages.qos1/server"})
    public void shouldSubscribeSharedSequentialReceiveMessagesQos1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.shared.then.non.shared.receive.messages.qos1/client",
        "${net}/subscribe.shared.then.non.shared.receive.messages.qos1/server"})
    public void shouldSubscribeSharedThenNonSharedReceiveMessagesQos1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.non.shared.then.shared.receive.messages.qos1/client",
        "${net}/subscribe.non.shared.then.shared.receive.messages.qos1/server"})
    public void shouldSubscribeNonSharedThenSharedReceiveMessagesQos1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.non.shared.sequential.receive.messages.qos1/client",
        "${net}/subscribe.non.shared.sequential.receive.messages.qos1/server"})
    public void shouldSubscribeNonSharedSequentialReceiveMessagesQos1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.non.shared.sequential.receive.messages.qos2/client",
        "${net}/subscribe.non.shared.sequential.receive.messages.qos2/server"})
    public void shouldSubscribeNonSharedSequentialReceiveMessagesQos2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.shared.resubscribe.change.qos.receive.messages/client",
        "${net}/subscribe.shared.resubscribe.change.qos.receive.messages/server"})
    public void shouldSubscribeSharedResubscribeChangeQosReceiveMessages() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.shared.subscription.disconnect/client",
        "${net}/subscribe.shared.subscription.disconnect/server"})
    public void shouldSubscribeSharedSubscriptionDisconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.redeliver.message.qos1/client",
        "${net}/subscribe.redeliver.message.qos1/server"})
    public void shouldRedeliverMessageQoS1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.redeliver.message.qos2/client",
        "${net}/subscribe.redeliver.message.qos2/server"})
    public void shouldRedeliverMessageQoS2() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-4.13.1-1]
    @Test
    @Specification({
        "${net}/subscribe.reject.truncated.body/client",
        "${net}/subscribe.reject.truncated.body/server"})
    public void shouldRejectSubscribeTruncatedBody() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.shared.reconnect.replay.qos2.unreceived.message/client",
        "${net}/subscribe.shared.reconnect.replay.qos2.unreceived.message/server"})
    public void shouldSubscribeSharedReconnectReplayQos2UnreceivedMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.shared.reconnect.replay.qos2.incomplete.message/client",
        "${net}/subscribe.shared.reconnect.replay.qos2.incomplete.message/server"})
    public void shouldSubscribeSharedReconnectReplayQos2IncompleteMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.shared.reconnect.abandon.qos2.unreceived.message/client",
        "${net}/subscribe.shared.reconnect.abandon.qos2.unreceived.message/server"})
    public void shouldSubscribeSharedReconnectAbandonQos2UnreceivedMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.shared.reconnect.clean.start.discard.qos2.packets/client",
        "${net}/subscribe.shared.reconnect.clean.start.discard.qos2.packets/server"})
    public void shouldSubscribeSharedReconnectCleanStartDiscardQos2Packets() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.shared.reconnect.session.end.discard.qos2.packets/client",
        "${net}/subscribe.shared.reconnect.session.end.discard.qos2.packets/server"})
    public void shouldSubscribeSharedReconnectSessionEndDiscardQos2Packets() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.shared.rejoin.after.disconnect.new.generation/client",
        "${net}/subscribe.shared.rejoin.after.disconnect.new.generation/server"})
    public void shouldSubscribeSharedRejoinAfterDisconnectNewGeneration() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.shared.rejoin.after.session.expiry.new.generation/client",
        "${net}/subscribe.shared.rejoin.after.session.expiry.new.generation/server"})
    public void shouldSubscribeSharedRejoinAfterSessionExpiryNewGeneration() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("FIRST_CLOSED");
        k3po.notifyBarrier("SESSION_EXPIRED");
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.shared.reconnect.replay.qos2.after.idle/client",
        "${net}/subscribe.shared.reconnect.replay.qos2.after.idle/server"})
    public void shouldSubscribeSharedReconnectReplayQos2AfterIdle() throws Exception
    {
        k3po.finish();
    }
}
