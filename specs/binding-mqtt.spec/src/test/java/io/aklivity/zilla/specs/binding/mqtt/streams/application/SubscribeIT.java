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
package io.aklivity.zilla.specs.binding.mqtt.streams.application;

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
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/mqtt/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/subscribe.retained.not.supported/client",
        "${app}/subscribe.retained.not.supported/server"})
    public void shouldSubscribeWithoutRetainedReplayWhenRetainNotSupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.topic.filter.single.exact/client",
        "${app}/subscribe.topic.filter.single.exact/server"})
    public void shouldFilterExact() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.topic.filter.multi.level.wildcard/client",
        "${app}/subscribe.topic.filter.multi.level.wildcard/server"})
    public void shouldFilterMultiLevelWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.topic.filter.single.level.wildcard/client",
        "${app}/subscribe.topic.filter.single.level.wildcard/server"})
    public void shouldFilterSingleLevelWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.topic.filter.single.and.multi.level.wildcard/client",
        "${app}/subscribe.topic.filter.single.and.multi.level.wildcard/server"})
    public void shouldFilterSingleAndMultiLevelWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.topic.filter.two.single.level.wildcard/client",
        "${app}/subscribe.topic.filter.two.single.level.wildcard/server"})
    public void shouldFilterTwoSingleLevelWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.topic.filters.aggregated.both.exact/client",
        "${app}/subscribe.topic.filters.aggregated.both.exact/server"})
    public void shouldFilterAggregatedBothExact() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.topic.filters.isolated.both.exact/client",
        "${app}/subscribe.topic.filters.isolated.both.exact/server"})
    public void shouldFilterIsolatedBothExact() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.topic.filters.disjoint.wildcards/client",
        "${app}/subscribe.topic.filters.disjoint.wildcards/server"})
    public void shouldFilterDisjointWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.topic.filters.overlapping.wildcards/client",
        "${app}/subscribe.topic.filters.overlapping.wildcards/server"})
    public void shouldFilterOverlappingWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.topic.filters.isolated.both.wildcard/client",
        "${app}/subscribe.topic.filters.isolated.both.wildcard/server"})
    public void shouldFilterIsolatedBothWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.topic.filters.aggregated.exact.and.wildcard/client",
        "${app}/subscribe.topic.filters.aggregated.exact.and.wildcard/server"})
    public void shouldFilterAggregatedExactAndWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.topic.filters.isolated.exact.and.wildcard/client",
        "${app}/subscribe.topic.filters.isolated.exact.and.wildcard/server"})
    public void shouldFilterIsolatedExactAndWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.one.message/client",
        "${app}/subscribe.one.message/server"})
    public void shouldReceiveOneMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.one.message.user.properties.unaltered/client",
        "${app}/subscribe.one.message.user.properties.unaltered/server"})
    public void shouldReceiveOneMessageWithUserPropertiesUnaltered() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.publish.no.local/client",
        "${app}/subscribe.publish.no.local/server"})
    public void shouldNotReceivePublishLocal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.receive.message/client",
        "${app}/subscribe.receive.message/server"})
    public void shouldReceiveOneMessageAfterPublish() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.receive.message.wildcard/client",
        "${app}/subscribe.receive.message.wildcard/server"})
    public void shouldReceiveOneMessageWithPatternTopic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.get.retained.as.published/client",
        "${app}/subscribe.get.retained.as.published/server"})
    public void shouldReceiveRetainedAsPublished() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.retain.as.published.not.retained/client",
        "${app}/subscribe.retain.as.published.not.retained/server"})
    public void shouldNotReceiveRetainedAsPublishedWhenNotRetained() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.one.message.receive.response.topic.and.correlation.data/client",
        "${app}/subscribe.one.message.receive.response.topic.and.correlation.data/server"})
    public void shouldReceiveCorrelationData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.qos0.replay.retained.no.packet.id/client",
        "${app}/subscribe.qos0.replay.retained.no.packet.id/server"})
    public void shouldReceiveAndReplayRetainedQos0() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.retain.handling.1.new.filter/client",
        "${app}/subscribe.retain.handling.1.new.filter/server"})
    public void shouldSubscribeRetainHandling1NewFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.retain.handling.1.existing.filter/client",
        "${app}/subscribe.retain.handling.1.existing.filter/server"})
    public void shouldSubscribeRetainHandling1ExistingFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.retain.handling.0.existing.filter/client",
        "${app}/subscribe.retain.handling.0.existing.filter/server"})
    public void shouldSubscribeRetainHandling0ExistingFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.qos0.publish.retained.no.replay/client",
        "${app}/subscribe.qos0.publish.retained.no.replay/server"})
    public void shouldNotReplayRetained() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.receive.messages.topic.alias.repeated/client",
        "${app}/subscribe.receive.messages.topic.alias.repeated/server"})
    public void shouldReceiveMessagesTopicAliasRepeated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.topic.filters.non.successful/client",
        "${app}/subscribe.topic.filters.non.successful/server"})
    public void shouldFilterNonSuccessful() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.reconnect.publish.no.subscription/client",
        "${app}/subscribe.reconnect.publish.no.subscription/server"})
    public void shouldReceiveReconnectNoSubscription() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.publish.invalid.affinity/client",
        "${app}/subscribe.publish.invalid.affinity/server"})
    public void shouldAbortSubscribeAndPublishInvalidAffinity() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.unroutable/client",
        "${app}/subscribe.unroutable/server"})
    public void shouldSubscribeUnroutable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.receive.message.qos1/client",
        "${app}/subscribe.receive.message.qos1/server"})
    public void shouldReceiveMessageQoS1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.receive.message.qos0.published.qos1/client",
        "${app}/subscribe.receive.message.qos0.published.qos1/server"})
    public void shouldReceiveMessageQoS0PublishedAsQoS1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.receive.message.qos1.published.qos2/client",
        "${app}/subscribe.receive.message.qos1.published.qos2/server"})
    public void shouldReceiveMessageQoS1PublishedAsQoS2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.receive.message.qos2/client",
        "${app}/subscribe.receive.message.qos2/server"})
    public void shouldReceiveMessageQoS2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.pubrel.while.publish.fragmented/client",
        "${app}/subscribe.pubrel.while.publish.fragmented/server"})
    public void shouldDeferPubrelWhilePublishFragmentedQoS2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.pingresp.while.publish.fragmented/client",
        "${app}/subscribe.pingresp.while.publish.fragmented/server"})
    public void shouldDeferPingRespWhilePublishFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.receive.message.qos2.pubrec.failure.reason.codes/client",
        "${app}/subscribe.receive.message.qos2.pubrec.failure.reason.codes/server"})
    public void shouldReceiveMessageQoS2AfterPubrecFailureReasonCodes() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.receive.message.qos1.puback.failure.reason.code/client",
        "${app}/subscribe.receive.message.qos1.puback.failure.reason.code/server"})
    public void shouldReceiveMessageQoS1AfterPubackFailureReasonCode() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.receive.messages.mixture.qos/client",
        "${app}/subscribe.receive.messages.mixture.qos/server"})
    public void shouldReceiveMessagesMixtureQos() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.reconnect.replay.qos1.unacked.message/client",
        "${app}/subscribe.reconnect.replay.qos1.unacked.message/server"})
    public void shouldReplayUnackedQoS1MessageAtReconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.reconnect.replay.qos2.unreceived.message/client",
        "${app}/subscribe.reconnect.replay.qos2.unreceived.message/server"})
    public void shouldReplayUnreceivedQoS2MessageAtReconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.reconnect.replay.qos2.incomplete.message/client",
        "${app}/subscribe.reconnect.replay.qos2.incomplete.message/server"})
    public void shouldReplayIncompleteQoS2MessageAtReconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.replay.retained.message.qos1/client",
        "${app}/subscribe.replay.retained.message.qos1/server"})
    public void shouldReplayRetainedQos1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.replay.retained.message.qos2/client",
        "${app}/subscribe.replay.retained.message.qos2/server"})
    public void shouldReplayRetainedQos2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.replay.retained.message.qos1.v4/client",
        "${app}/subscribe.replay.retained.message.qos1.v4/server"})
    public void shouldReplayRetainedQos1V4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.replay.retained.message.qos2.v4/client",
        "${app}/subscribe.replay.retained.message.qos2.v4/server"})
    public void shouldReplayRetainedQos2V4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.receive.message.overlapping.wildcard.mixed.qos/client",
        "${app}/subscribe.receive.message.overlapping.wildcard.mixed.qos/server"})
    public void shouldReceiveMessageOverlappingWildcardMixedQos() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.receive.message.dollar.topic/client",
        "${app}/subscribe.receive.message.dollar.topic/server"})
    public void shouldReceiveMessageDollarTopic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.topic.guarded.identity.param/client",
        "${app}/subscribe.topic.guarded.identity.param/server"})
    public void shouldFilterGuardedIdentityParam() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.invalid.topic.guarded.identity.param/client",
        "${app}/subscribe.invalid.topic.guarded.identity.param/server"})
    public void shouldSubscribeInvalidTopicGuardedIdentityParam() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.qos1.drop.exceeds.max.packet.size/client",
        "${app}/subscribe.qos1.drop.exceeds.max.packet.size/server"})
    public void shouldCompleteDroppedQoS1MessageExceedingMaximumPacketSize() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.qos2.drop.exceeds.max.packet.size/client",
        "${app}/subscribe.qos2.drop.exceeds.max.packet.size/server"})
    public void shouldCompleteDroppedQoS2MessageExceedingMaximumPacketSize() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.topic.filters.unroutable.mixed/client",
        "${app}/subscribe.topic.filters.unroutable.mixed/server"})
    public void shouldSubscribeTopicFiltersUnroutableMixed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.topic.filters.unroutable.all/client",
        "${app}/subscribe.topic.filters.unroutable.all/server"})
    public void shouldSubscribeTopicFiltersUnroutableAll() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.topic.filter.unauthorized/client",
        "${app}/subscribe.topic.filter.unauthorized/server"})
    public void shouldSubscribeTopicFilterUnauthorized() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.topic.filters.non.successful.unsubscribe/client",
        "${app}/subscribe.topic.filters.non.successful.unsubscribe/server"})
    public void shouldSubscribeTopicFiltersNonSuccessfulUnsubscribe() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.downgrade.qos2.maximum.qos.1/client",
        "${app}/subscribe.downgrade.qos2.maximum.qos.1/server"})
    public void shouldSubscribeDowngradeQos2MaximumQos1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.downgrade.qos2.configured.maximum.qos.1/client",
        "${app}/subscribe.downgrade.qos2.configured.maximum.qos.1/server"})
    public void shouldSubscribeDowngradeQos2ConfiguredMaximumQos1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.downgrade.qos2.maximum.qos.0/client",
        "${app}/subscribe.downgrade.qos2.maximum.qos.0/server"})
    public void shouldSubscribeDowngradeQos2MaximumQos0() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.request.qos1.maximum.qos.1/client",
        "${app}/subscribe.request.qos1.maximum.qos.1/server"})
    public void shouldSubscribeRequestQos1MaximumQos1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.replace.same.qos/client",
        "${app}/subscribe.replace.same.qos/server"})
    public void shouldSubscribeReplaceSameQos() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.replace.same.options/client",
        "${app}/subscribe.replace.same.options/server"})
    public void shouldSubscribeReplaceSameOptions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.replace.downgrade.qos/client",
        "${app}/subscribe.replace.downgrade.qos/server"})
    public void shouldSubscribeReplaceDowngradeQos() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.replace.upgrade.qos/client",
        "${app}/subscribe.replace.upgrade.qos/server"})
    public void shouldSubscribeReplaceUpgradeQos() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.replace.add.subscription.id/client",
        "${app}/subscribe.replace.add.subscription.id/server"})
    public void shouldSubscribeReplaceAddSubscriptionId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.replace.remove.subscription.id/client",
        "${app}/subscribe.replace.remove.subscription.id/server"})
    public void shouldSubscribeReplaceRemoveSubscriptionId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.receive.message.subscription.id.112/client",
        "${app}/subscribe.receive.message.subscription.id.112/server"})
    public void shouldSubscribeReceiveMessageSubscriptionId112() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.receive.message.subscription.id.127/client",
        "${app}/subscribe.receive.message.subscription.id.127/server"})
    public void shouldSubscribeReceiveMessageSubscriptionId127() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.receive.message.subscription.id.255/client",
        "${app}/subscribe.receive.message.subscription.id.255/server"})
    public void shouldSubscribeReceiveMessageSubscriptionId255() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.one.message.v4/client",
        "${app}/subscribe.one.message.v4/server"})
    public void shouldSubscribeOneMessageV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.receive.message.overlapping.wildcard.mixed.qos.v4/client",
        "${app}/subscribe.receive.message.overlapping.wildcard.mixed.qos.v4/server"})
    public void shouldSubscribeReceiveMessageOverlappingWildcardMixedQosV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.receive.message.dollar.topic.v4/client",
        "${app}/subscribe.receive.message.dollar.topic.v4/server"})
    public void shouldReceiveMessageDollarTopicV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.receive.message.qos0.published.qos1.v4/client",
        "${app}/subscribe.receive.message.qos0.published.qos1.v4/server"})
    public void shouldSubscribeReceiveMessageQos0PublishedQos1V4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.receive.message.qos1.published.qos2.v4/client",
        "${app}/subscribe.receive.message.qos1.published.qos2.v4/server"})
    public void shouldSubscribeReceiveMessageQos1PublishedQos2V4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.receive.message.qos1.v4/client",
        "${app}/subscribe.receive.message.qos1.v4/server"})
    public void shouldSubscribeReceiveMessageQos1V4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.receive.message.qos2.v4/client",
        "${app}/subscribe.receive.message.qos2.v4/server"})
    public void shouldSubscribeReceiveMessageQos2V4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.pubrel.while.publish.fragmented.v4/client",
        "${app}/subscribe.pubrel.while.publish.fragmented.v4/server"})
    public void shouldDeferPubrelWhilePublishFragmentedQoS2V4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.pingresp.while.publish.fragmented.v4/client",
        "${app}/subscribe.pingresp.while.publish.fragmented.v4/server"})
    public void shouldDeferPingRespWhilePublishFragmentedV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.receive.message.v4/client",
        "${app}/subscribe.receive.message.v4/server"})
    public void shouldSubscribeReceiveMessageV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.receive.message.wildcard.v4/client",
        "${app}/subscribe.receive.message.wildcard.v4/server"})
    public void shouldSubscribeReceiveMessageWildcardV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.receive.messages.mixture.qos.v4/client",
        "${app}/subscribe.receive.messages.mixture.qos.v4/server"})
    public void shouldSubscribeReceiveMessagesMixtureQosV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.reconnect.replay.qos1.unacked.message.v4/client",
        "${app}/subscribe.reconnect.replay.qos1.unacked.message.v4/server"})
    public void shouldSubscribeReconnectReplayQos1UnackedMessageV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.reconnect.replay.qos2.incomplete.message.v4/client",
        "${app}/subscribe.reconnect.replay.qos2.incomplete.message.v4/server"})
    public void shouldSubscribeReconnectReplayQos2IncompleteMessageV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.reconnect.replay.qos2.unreceived.message.v4/client",
        "${app}/subscribe.reconnect.replay.qos2.unreceived.message.v4/server"})
    public void shouldSubscribeReconnectReplayQos2UnreceivedMessageV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.retained.not.supported.v4/client",
        "${app}/subscribe.retained.not.supported.v4/server"})
    public void shouldSubscribeRetainedNotSupportedV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.topic.filter.multi.level.wildcard.v4/client",
        "${app}/subscribe.topic.filter.multi.level.wildcard.v4/server"})
    public void shouldSubscribeTopicFilterMultiLevelWildcardV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.topic.filter.single.and.multi.level.wildcard.v4/client",
        "${app}/subscribe.topic.filter.single.and.multi.level.wildcard.v4/server"})
    public void shouldSubscribeTopicFilterSingleAndMultiLevelWildcardV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.topic.filter.single.exact.v4/client",
        "${app}/subscribe.topic.filter.single.exact.v4/server"})
    public void shouldSubscribeTopicFilterSingleExactV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.topic.filter.single.level.wildcard.v4/client",
        "${app}/subscribe.topic.filter.single.level.wildcard.v4/server"})
    public void shouldSubscribeTopicFilterSingleLevelWildcardV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.topic.filter.two.single.level.wildcard.v4/client",
        "${app}/subscribe.topic.filter.two.single.level.wildcard.v4/server"})
    public void shouldSubscribeTopicFilterTwoSingleLevelWildcardV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.topic.filter.unauthorized.v4/client",
        "${app}/subscribe.topic.filter.unauthorized.v4/server"})
    public void shouldSubscribeTopicFilterUnauthorizedV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.topic.filters.aggregated.both.exact.v4/client",
        "${app}/subscribe.topic.filters.aggregated.both.exact.v4/server"})
    public void shouldSubscribeTopicFiltersAggregatedBothExactV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.topic.filters.aggregated.exact.and.wildcard.v4/client",
        "${app}/subscribe.topic.filters.aggregated.exact.and.wildcard.v4/server"})
    public void shouldSubscribeTopicFiltersAggregatedExactAndWildcardV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.topic.filters.disjoint.wildcards.v4/client",
        "${app}/subscribe.topic.filters.disjoint.wildcards.v4/server"})
    public void shouldSubscribeTopicFiltersDisjointWildcardsV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.topic.filters.isolated.both.exact.no.subscription.id/client",
        "${app}/subscribe.topic.filters.isolated.both.exact.no.subscription.id/server"})
    public void shouldSubscribeTopicFiltersIsolatedBothExactNoSubscriptionId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.topic.filters.isolated.both.exact.v4/client",
        "${app}/subscribe.topic.filters.isolated.both.exact.v4/server"})
    public void shouldSubscribeTopicFiltersIsolatedBothExactV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.topic.filters.isolated.both.wildcard.v4/client",
        "${app}/subscribe.topic.filters.isolated.both.wildcard.v4/server"})
    public void shouldSubscribeTopicFiltersIsolatedBothWildcardV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.topic.filters.isolated.exact.and.wildcard.v4/client",
        "${app}/subscribe.topic.filters.isolated.exact.and.wildcard.v4/server"})
    public void shouldSubscribeTopicFiltersIsolatedExactAndWildcardV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.topic.filters.overlapping.wildcards.v4/client",
        "${app}/subscribe.topic.filters.overlapping.wildcards.v4/server"})
    public void shouldSubscribeTopicFiltersOverlappingWildcardsV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.topic.filters.unroutable.mixed.v4/client",
        "${app}/subscribe.topic.filters.unroutable.mixed.v4/server"})
    public void shouldSubscribeTopicFiltersUnroutableMixedV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.topic.guarded.identity.param.v4/client",
        "${app}/subscribe.topic.guarded.identity.param.v4/server"})
    public void shouldSubscribeTopicGuardedIdentityParamV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.receive.message.overlapping.wildcard.v4/client",
        "${app}/subscribe.receive.message.overlapping.wildcard.v4/server"})
    public void shouldSubscribeReceiveMessageOverlappingWildcardV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.receive.message.qos0.published.qos2.v4/client",
        "${app}/subscribe.receive.message.qos0.published.qos2.v4/server"})
    public void shouldSubscribeReceiveMessageQos0PublishedQos2V4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.downgrade.qos2.configured.maximum.qos.1.v4/client",
        "${app}/subscribe.downgrade.qos2.configured.maximum.qos.1.v4/server"})
    public void shouldSubscribeDowngradeQos2ConfiguredMaximumQos1V4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.downgrade.qos2.maximum.qos.1.v4/client",
        "${app}/subscribe.downgrade.qos2.maximum.qos.1.v4/server"})
    public void shouldSubscribeDowngradeQos2MaximumQos1V4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.replace.downgrade.qos.v4/client",
        "${app}/subscribe.replace.downgrade.qos.v4/server"})
    public void shouldSubscribeReplaceDowngradeQosV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.replace.same.qos.v4/client",
        "${app}/subscribe.replace.same.qos.v4/server"})
    public void shouldSubscribeReplaceSameQosV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.replace.upgrade.qos.v4/client",
        "${app}/subscribe.replace.upgrade.qos.v4/server"})
    public void shouldSubscribeReplaceUpgradeQosV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.request.qos1.maximum.qos.1.v4/client",
        "${app}/subscribe.request.qos1.maximum.qos.1.v4/server"})
    public void shouldSubscribeRequestQos1MaximumQos1V4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.topic.filters.non.successful.unsubscribe.v4/client",
        "${app}/subscribe.topic.filters.non.successful.unsubscribe.v4/server"})
    public void shouldSubscribeTopicFiltersNonSuccessfulUnsubscribeV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/server.sent.publish.payload.format.invalid/client",
        "${app}/server.sent.publish.payload.format.invalid/server"})
    public void shouldServerSentPublishPayloadFormatInvalid() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.reject.second.filter/client",
        "${app}/subscribe.reject.second.filter/server"})
    public void shouldRejectSecondFilterWithoutSubscribingFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.topic.multibyte/client",
        "${app}/subscribe.topic.multibyte/server"})
    public void shouldReceiveOneMessageWithMultibyteTopic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.topic.multibyte.v4/client",
        "${app}/subscribe.topic.multibyte.v4/server"})
    public void shouldReceiveOneMessageWithMultibyteTopicV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.shared.subscription/client",
        "${app}/subscribe.shared.subscription/server"})
    public void shouldSubscribeSharedSubscription() throws Exception
    {
        k3po.finish();
    }

    // MQTT 5.0, 3.3.4 [MQTT-3.3.4-9]
    @Test
    @Specification({
        "${app}/subscribe.shared.receive.maximum.two/client",
        "${app}/subscribe.shared.receive.maximum.two/server"})
    public void shouldSubscribeSharedReceiveMaximumTwo() throws Exception
    {
        k3po.finish();
    }

    // MQTT 5.0, 3.3.4 [MQTT-3.3.4-9]
    @Test
    @Specification({
        "${app}/subscribe.shared.receive.maximum.qos2/client",
        "${app}/subscribe.shared.receive.maximum.qos2/server"})
    public void shouldSubscribeSharedReceiveMaximumQos2() throws Exception
    {
        k3po.finish();
    }

    // MQTT 5.0, 3.3.4 [MQTT-3.3.4-9]
    @Test
    @Specification({
        "${app}/subscribe.shared.receive.maximum/client",
        "${app}/subscribe.shared.receive.maximum/server"})
    public void shouldSubscribeSharedReceiveMaximum() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.shared.distinct.share.names/client",
        "${app}/subscribe.shared.distinct.share.names/server"})
    public void shouldSubscribeSharedDistinctShareNames() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.shared.distinct.filters/client",
        "${app}/subscribe.shared.distinct.filters/server"})
    public void shouldSubscribeSharedDistinctFilters() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.shared.and.non.shared/client",
        "${app}/subscribe.shared.and.non.shared/server"})
    public void shouldSubscribeSharedAndNonShared() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.shared.resubscribe.change.qos/client",
        "${app}/subscribe.shared.resubscribe.change.qos/server"})
    public void shouldSubscribeSharedResubscribeChangeQos() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.resubscribe.identical/client",
        "${app}/subscribe.resubscribe.identical/server"})
    public void shouldSubscribeResubscribeIdentical() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.shared.resubscribe.identical/client",
        "${app}/subscribe.shared.resubscribe.identical/server"})
    public void shouldSubscribeSharedResubscribeIdentical() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.unroutable.shared.subscription/client",
        "${app}/subscribe.unroutable.shared.subscription/server"})
    public void shouldSubscribeUnroutableSharedSubscription() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.shared.receive.message.qos2.granted.qos1/client",
        "${app}/subscribe.shared.receive.message.qos2.granted.qos1/server"})
    public void shouldSubscribeSharedReceiveMessageQos2GrantedQos1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.shared.receive.message.qos2/client",
        "${app}/subscribe.shared.receive.message.qos2/server"})
    public void shouldSubscribeSharedReceiveMessageQos2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.shared.receive.message.qos2.inflight/client",
        "${app}/subscribe.shared.receive.message.qos2.inflight/server"})
    public void shouldSubscribeSharedReceiveMessageQos2Inflight() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.shared.receive.message.qos1.granted.qos0/client",
        "${app}/subscribe.shared.receive.message.qos1.granted.qos0/server"})
    public void shouldSubscribeSharedReceiveMessageQos1GrantedQos0() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.reconnect.shared.subscription/client",
        "${app}/subscribe.reconnect.shared.subscription/server"})
    public void shouldSubscribeReconnectSharedSubscription() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.reconnect.legacy.session.state/client",
        "${app}/subscribe.reconnect.legacy.session.state/server"})
    public void shouldSubscribeReconnectLegacySessionState() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.receive.message.qos1.ack.with.reasoncode/client",
        "${app}/subscribe.receive.message.qos1.ack.with.reasoncode/server"})
    public void shouldSubscribeReceiveMessageQos1AckWithReasoncode() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.receive.message.qos2.ack.with.reasoncode/client",
        "${app}/subscribe.receive.message.qos2.ack.with.reasoncode/server"})
    public void shouldSubscribeReceiveMessageQos2AckWithReasoncode() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.receive.message.qos2.duplicate.before.ack/client",
        "${app}/subscribe.receive.message.qos2.duplicate.before.ack/server"})
    public void shouldSubscribeReceiveMessageQos2DuplicateBeforeAck() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.receive.message.qos2.duplicate.after.ack/client",
        "${app}/subscribe.receive.message.qos2.duplicate.after.ack/server"})
    public void shouldSubscribeReceiveMessageQos2DuplicateAfterAck() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.receive.message.qos2.reuse.packet.id.after.complete/client",
        "${app}/subscribe.receive.message.qos2.reuse.packet.id.after.complete/server"})
    public void shouldSubscribeReceiveMessageQos2ReusePacketIdAfterComplete() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.receive.message.qos2.reuse.packet.id.after.error/client",
        "${app}/subscribe.receive.message.qos2.reuse.packet.id.after.error/server"})
    public void shouldSubscribeReceiveMessageQos2ReusePacketIdAfterError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.shared.receive.message.qos1/client",
        "${app}/subscribe.shared.receive.message.qos1/server"})
    public void shouldSubscribeSharedReceiveMessageQos1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.shared.receive.message.dollar.topic/client",
        "${app}/subscribe.shared.receive.message.dollar.topic/server"})
    public void shouldSubscribeSharedReceiveMessageDollarTopic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.shared.receive.message.dollar.topic.filter/client",
        "${app}/subscribe.shared.receive.message.dollar.topic.filter/server"})
    public void shouldSubscribeSharedReceiveMessageDollarTopicFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.shared.receive.message.qos1.ack.with.reasoncode/client",
        "${app}/subscribe.shared.receive.message.qos1.ack.with.reasoncode/server"})
    public void shouldSubscribeSharedReceiveMessageQos1AckWithReasoncode() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.shared.and.non.shared.receive.messages/client",
        "${app}/subscribe.shared.and.non.shared.receive.messages/server"})
    public void shouldSubscribeSharedAndNonSharedReceiveMessages() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.suback.after.stream.begin/client",
        "${app}/subscribe.suback.after.stream.begin/server"})
    public void shouldSubackAfterStreamBegin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.qos1.suback.after.streams.begin/client",
        "${app}/subscribe.qos1.suback.after.streams.begin/server"})
    public void shouldSubackQos1AfterStreamsBegin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.shared.suback.after.stream.begin/client",
        "${app}/subscribe.shared.suback.after.stream.begin/server"})
    public void shouldSubackSharedAfterStreamBegin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.shared.filters.suback.after.streams.begin/client",
        "${app}/subscribe.shared.filters.suback.after.streams.begin/server"})
    public void shouldSubackSharedFiltersAfterStreamsBegin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.shared.and.non.shared.suback.after.streams.begin/client",
        "${app}/subscribe.shared.and.non.shared.suback.after.streams.begin/server"})
    public void shouldSubackSharedAndNonSharedAfterStreamsBegin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.reject.stream.reset.before.begin/client",
        "${app}/subscribe.reject.stream.reset.before.begin/server"})
    public void shouldRejectSubscribeWhenStreamResetBeforeBegin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.shared.reject.stream.reset.before.begin/client",
        "${app}/subscribe.shared.reject.stream.reset.before.begin/server"})
    public void shouldRejectSharedSubscribeWhenStreamResetBeforeBegin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.shared.filters.reject.one.stream.reset.before.begin/client",
        "${app}/subscribe.shared.filters.reject.one.stream.reset.before.begin/server"})
    public void shouldRejectOneOfSharedFiltersWhenStreamResetBeforeBegin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.suback.after.stream.begin.v4/client",
        "${app}/subscribe.suback.after.stream.begin.v4/server"})
    public void shouldSubackAfterStreamBeginV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.reject.reconnect.non.clean.start/client",
        "${app}/subscribe.reject.reconnect.non.clean.start/server"})
    public void shouldNotRestoreRejectedSubscriptionOnReconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.reject.stream.reset.before.begin.v4/client",
        "${app}/subscribe.reject.stream.reset.before.begin.v4/server"})
    public void shouldRejectSubscribeWhenStreamResetBeforeBeginV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.shared.filters.reject.one.reconnect.non.clean.start/client",
        "${app}/subscribe.shared.filters.reject.one.reconnect.non.clean.start/server"})
    public void shouldRestoreOnlyAcceptedSharedFilterOnReconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.reject.qos2.stream.reset.before.begin.deliver.qos1/client",
        "${app}/subscribe.reject.qos2.stream.reset.before.begin.deliver.qos1/server"})
    public void shouldDeliverQos1WhenQos2StreamResetBeforeBegin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.restored.filter.reject.stream.reset.before.begin/client",
        "${app}/subscribe.restored.filter.reject.stream.reset.before.begin/server"})
    public void shouldDisconnectWhenResetStreamCarriesRestoredFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.back.to.back.reject.stream.reset.before.begin/client",
        "${app}/subscribe.back.to.back.reject.stream.reset.before.begin/server"})
    public void shouldRejectBackToBackSubscribesWhenStreamResetBeforeBegin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.restored.filter.reject.other.stream.reset.before.begin/client",
        "${app}/subscribe.restored.filter.reject.other.stream.reset.before.begin/server"})
    public void shouldRejectOtherStreamWithoutDisconnectingRestoredFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.reject.stream.abort.before.begin/client",
        "${app}/subscribe.reject.stream.abort.before.begin/server"})
    public void shouldRejectSubscribeWhenStreamAbortedBeforeBegin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.shared.reject.stream.abort.before.begin/client",
        "${app}/subscribe.shared.reject.stream.abort.before.begin/server"})
    public void shouldRejectSharedSubscribeWhenStreamAbortedBeforeBegin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.shared.filters.reject.one.stream.abort.before.begin/client",
        "${app}/subscribe.shared.filters.reject.one.stream.abort.before.begin/server"})
    public void shouldRejectOneOfSharedFiltersWhenStreamAbortedBeforeBegin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.restored.filter.reject.stream.abort.before.begin/client",
        "${app}/subscribe.restored.filter.reject.stream.abort.before.begin/server"})
    public void shouldDisconnectWhenAbortedStreamCarriesRestoredFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.reject.reconnect.non.clean.start.v4/client",
        "${app}/subscribe.reject.reconnect.non.clean.start.v4/server"})
    public void shouldNotRestoreRejectedSubscriptionOnReconnectV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.restored.filter.reject.stream.reset.before.begin.v4/client",
        "${app}/subscribe.restored.filter.reject.stream.reset.before.begin.v4/server"})
    public void shouldDisconnectWhenResetStreamCarriesRestoredFilterV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.back.to.back.reject.stream.reset.before.begin.v4/client",
        "${app}/subscribe.back.to.back.reject.stream.reset.before.begin.v4/server"})
    public void shouldRejectBackToBackSubscribesWhenStreamResetBeforeBeginV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.restored.filter.reject.other.stream.reset.before.begin.v4/client",
        "${app}/subscribe.restored.filter.reject.other.stream.reset.before.begin.v4/server"})
    public void shouldRejectOtherStreamWithoutDisconnectingRestoredFilterV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.reject.stream.abort.before.begin.v4/client",
        "${app}/subscribe.reject.stream.abort.before.begin.v4/server"})
    public void shouldRejectSubscribeWhenStreamAbortedBeforeBeginV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.restored.filter.reject.stream.abort.before.begin.v4/client",
        "${app}/subscribe.restored.filter.reject.stream.abort.before.begin.v4/server"})
    public void shouldDisconnectWhenAbortedStreamCarriesRestoredFilterV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.redeliver.message.qos1/client",
        "${app}/subscribe.redeliver.message.qos1/server"})
    public void shouldRedeliverMessageQoS1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.redeliver.message.qos2/client",
        "${app}/subscribe.redeliver.message.qos2/server"})
    public void shouldRedeliverMessageQoS2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.redeliver.message.qos2.v4/client",
        "${app}/subscribe.redeliver.message.qos2.v4/server"})
    public void shouldRedeliverMessageQoS2V4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.shared.reconnect.replay.qos2.unreceived.message/client",
        "${app}/subscribe.shared.reconnect.replay.qos2.unreceived.message/server"})
    public void shouldSubscribeSharedReconnectReplayQos2UnreceivedMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.shared.reconnect.replay.qos2.incomplete.message/client",
        "${app}/subscribe.shared.reconnect.replay.qos2.incomplete.message/server"})
    public void shouldSubscribeSharedReconnectReplayQos2IncompleteMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.shared.reconnect.abandon.qos2.unreceived.message/client",
        "${app}/subscribe.shared.reconnect.abandon.qos2.unreceived.message/server"})
    public void shouldSubscribeSharedReconnectAbandonQos2UnreceivedMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.shared.reconnect.clean.start.discard.qos2.packets/client",
        "${app}/subscribe.shared.reconnect.clean.start.discard.qos2.packets/server"})
    public void shouldSubscribeSharedReconnectCleanStartDiscardQos2Packets() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.shared.reconnect.session.end.discard.qos2.packets/client",
        "${app}/subscribe.shared.reconnect.session.end.discard.qos2.packets/server"})
    public void shouldSubscribeSharedReconnectSessionEndDiscardQos2Packets() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.shared.rejoin.after.disconnect.new.generation/client",
        "${app}/subscribe.shared.rejoin.after.disconnect.new.generation/server"})
    public void shouldSubscribeSharedRejoinAfterDisconnectNewGeneration() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.shared.rejoin.after.session.expiry.new.generation/client",
        "${app}/subscribe.shared.rejoin.after.session.expiry.new.generation/server"})
    public void shouldSubscribeSharedRejoinAfterSessionExpiryNewGeneration() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.shared.reconnect.replay.qos2.after.idle/client",
        "${app}/subscribe.shared.reconnect.replay.qos2.after.idle/server"})
    public void shouldSubscribeSharedReconnectReplayQos2AfterIdle() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("MESSAGE_ALLOWED");
        k3po.finish();
    }
}
