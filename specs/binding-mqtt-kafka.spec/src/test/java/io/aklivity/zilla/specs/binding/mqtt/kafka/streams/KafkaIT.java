/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.specs.binding.mqtt.kafka.streams;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class KafkaIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("kafka", "io/aklivity/zilla/specs/binding/mqtt/kafka/streams/kafka");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${kafka}/publish.client.sent.abort/client",
        "${kafka}/publish.client.sent.abort/server"})
    public void shouldPublishReceiveClientSentAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.client.sent.reset/client",
        "${kafka}/publish.client.sent.reset/server"})
    public void shouldPublishReceiveClientSentReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.server.sent.abort/client",
        "${kafka}/publish.server.sent.abort/server"})
    public void shouldPublishReceiveServerSentAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.server.sent.reset/client",
        "${kafka}/publish.server.sent.reset/server"})
    public void shouldPublishReceiveServerSentReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.server.sent.data/client",
        "${kafka}/publish.server.sent.data/server"})
    public void shouldPublishAbortWhenServerSentData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.retained.server.sent.abort/client",
        "${kafka}/publish.retained.server.sent.abort/server"})
    public void shouldPublishReceiveServerSentRetainedAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.retained.server.sent.reset/client",
        "${kafka}/publish.retained.server.sent.reset/server"})
    public void shouldPublishReceiveServerSentRetainedReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.retained.server.sent.data/client",
        "${kafka}/publish.retained.server.sent.data/server"})
    public void shouldPublishAbortWhenServerSentRetainedData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.empty.message/client",
        "${kafka}/publish.empty.message/server"})
    public void shouldSendEmptyMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.one.message/client",
        "${kafka}/publish.one.message/server"})
    public void shouldSendOneMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.topic.trailing.empty.level/client",
        "${kafka}/publish.topic.trailing.empty.level/server"})
    public void shouldSendTopicWithTrailingEmptyLevel() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.one.message.changed.topic.name/client",
        "${kafka}/publish.one.message.changed.topic.name/server"})
    public void shouldSendOneMessageWithChangedTopicName() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.one.message.resolve.topic.params/client",
        "${kafka}/publish.one.message.resolve.topic.params/server"})
    public void shouldSendOneMessageWithResolvingParams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.10k/client",
        "${kafka}/publish.10k/server"})
    public void shouldSendMessage10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.retained.10k/client",
        "${kafka}/publish.retained.10k/server"})
    public void shouldSendRetainedMessage10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.retained/client",
        "${kafka}/publish.retained/server"})
    public void shouldPublishRetainedMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.many.messages.retain.available/client",
        "${kafka}/publish.many.messages.retain.available/server"})
    public void shouldPublishManyMessagesRetainAvailable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.multiple.messages/client",
        "${kafka}/publish.multiple.messages/server"})
    public void shouldSendMultipleMessages() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.multiple.clients/client",
        "${kafka}/publish.multiple.clients/server"})
    public void shouldSendMultipleClients() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.topic.space/client",
        "${kafka}/publish.topic.space/server"})
    public void shouldSendUsingTopicSpace() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.topic.space.with.params/client",
        "${kafka}/publish.topic.space.with.params/server"})
    public void shouldSendUsingTopicSpaceWithParams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.reject.qos2/client",
        "${kafka}/publish.reject.qos2/server"})
    public void shouldRejectPublishWhenTopicSpaceWithParams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.client.topic.space/client",
        "${kafka}/publish.client.topic.space/server"})
    public void shouldSendUsingClientTopicSpace() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.with.user.properties.distinct/client",
        "${kafka}/publish.with.user.properties.distinct/server"})
    public void shouldSendWithDistinctUserProperties() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.with.user.properties.repeated/client",
        "${kafka}/publish.with.user.properties.repeated/server"})
    public void shouldSendWithRepeatedUserProperties() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.with.user.property/client",
        "${kafka}/publish.with.user.property/server"})
    public void shouldSendWithUserProperty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.client.sent.abort/client",
        "${kafka}/subscribe.client.sent.abort/server"})
    public void shouldSubscribeReceiveClientSentAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.client.sent.data/client",
        "${kafka}/subscribe.client.sent.data/server"})
    public void shouldSubscribeAbortWhenClientSentData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.client.sent.reset/client",
        "${kafka}/subscribe.client.sent.reset/server"})
    public void shouldSubscribeReceiveClientSentReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.server.sent.abort/client",
        "${kafka}/subscribe.server.sent.abort/server"})
    public void shouldSubscribeReceiveServerSentAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.server.sent.flush/client",
        "${kafka}/subscribe.server.sent.flush/server"})
    public void shouldSubscribeReceiveServerSentFlush() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.server.sent.reset/client",
        "${kafka}/subscribe.server.sent.reset/server"})
    public void shouldSubscribeReceiveServerSentReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.retained.server.sent.abort/client",
        "${kafka}/subscribe.retained.server.sent.abort/server"})
    public void shouldSubscribeReceiveServerSentRetainedAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.retained.server.sent.reset/client",
        "${kafka}/subscribe.retained.server.sent.reset/server"})
    public void shouldSubscribeReceiveServerSentRetainedReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.filter.change.retain/client",
        "${kafka}/subscribe.filter.change.retain/server"})
    public void shouldReceiveRetainedAfterFilterChange() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.filter.change.retain.no.local/client",
        "${kafka}/subscribe.filter.change.retain.no.local/server"})
    public void shouldApplyConditionsToRetainedAfterFilterChange() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.deferred.filter.change.retain/client",
        "${kafka}/subscribe.deferred.filter.change.retain/server"})
    public void shouldReceiveRetainedAfterFilterDeferred() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.filter.change.retain.buffer/client",
        "${kafka}/subscribe.filter.change.retain.buffer/server"})
    public void shouldReceiveRetainedAfterFilterChangeBuffer() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.filter.change.retain.resubscribe/client",
        "${kafka}/subscribe.filter.change.retain.resubscribe/server"})
    public void shouldReceiveRetainedAfterFilterChangeResubscribe() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.one.message/client",
        "${kafka}/subscribe.one.message/server"})
    public void shouldReceiveOneMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.publish.retained.retain.as.published/client",
        "${kafka}/subscribe.publish.retained.retain.as.published/server"})
    public void shouldSubscribePublishRetainedRetainAsPublished() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.publish.retained.no.retain.as.published/client",
        "${kafka}/subscribe.publish.retained.no.retain.as.published/server"})
    public void shouldSubscribePublishRetainedNoRetainAsPublished() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.publish.not.retained.retain.as.published/client",
        "${kafka}/subscribe.publish.not.retained.retain.as.published/server"})
    public void shouldSubscribePublishNotRetainedRetainAsPublished() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.publish.retained.retain.as.published.multiple.filters/client",
        "${kafka}/subscribe.publish.retained.retain.as.published.multiple.filters/server"})
    public void shouldSubscribePublishRetainedRetainAsPublishedMultipleFilters() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.retain.shared.subscription.id/client",
        "${kafka}/subscribe.retain.shared.subscription.id/server"})
    public void shouldReceiveRetainedWithSharedSubscriptionId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.filter.change.retain.reordered/client",
        "${kafka}/subscribe.filter.change.retain.reordered/server"})
    public void shouldReceiveRetainedAfterFilterChangeReordered() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.one.message.fragmented/client",
        "${kafka}/subscribe.one.message.fragmented/server"})
    public void shouldReceiveOneMessageFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.one.message.changed.topic.name/client",
        "${kafka}/subscribe.one.message.changed.topic.name/server"})
    public void shouldReceiveOneMessageWithChangedTopicName() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.one.message.resolve.topic.params/client",
        "${kafka}/subscribe.one.message.resolve.topic.params/server"})
    public void shouldReceiveOneMessageWithResolvingParams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.one.message.receive.response.topic.and.correlation.data/client",
        "${kafka}/subscribe.one.message.receive.response.topic.and.correlation.data/server"})
    public void shouldReceiveCorrelationData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.one.message.user.properties.unaltered/client",
        "${kafka}/subscribe.one.message.user.properties.unaltered/server"})
    public void shouldReceiveOneMessageWithUserPropertiesUnaltered() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.publish.no.local/client",
        "${kafka}/subscribe.publish.no.local/server"})
    public void shouldNotReceiveLocal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.retain/client",
        "${kafka}/subscribe.retain/server"})
    public void shouldReceiveRetained() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.retain.fragmented/client",
        "${kafka}/subscribe.retain.fragmented/server"})
    public void shouldReceiveRetainedFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.receive.message.wildcard/client",
        "${kafka}/subscribe.receive.message.wildcard/server"})
    public void shouldReceiveOneMessageWithPatternTopic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.receive.message.overlapping.wildcard/client",
        "${kafka}/subscribe.receive.message.overlapping.wildcard/server"})
    public void shouldReceiveMessageOverlappingWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.topic.filter.multi.level.wildcard/client",
        "${kafka}/subscribe.topic.filter.multi.level.wildcard/server"})
    public void shouldFilterMultiLevelWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.topic.filter.single.level.wildcard/client",
        "${kafka}/subscribe.topic.filter.single.level.wildcard/server"})
    public void shouldFilterSingleLevelWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.topic.filter.trailing.empty.level/client",
        "${kafka}/subscribe.topic.filter.trailing.empty.level/server"})
    public void shouldFilterTrailingEmptyLevel() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.topic.filter.single.and.multi.level.wildcard/client",
        "${kafka}/subscribe.topic.filter.single.and.multi.level.wildcard/server"})
    public void shouldFilterSingleAndMultiLevelWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.topic.filter.two.single.level.wildcard/client",
        "${kafka}/subscribe.topic.filter.two.single.level.wildcard/server"})
    public void shouldFilterTwoSingleLevelWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.topic.filters.aggregated.both.exact/client",
        "${kafka}/subscribe.topic.filters.aggregated.both.exact/server"})
    public void shouldFilterAggregatedBothExact() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.topic.filters.isolated.both.exact/client",
        "${kafka}/subscribe.topic.filters.isolated.both.exact/server"})
    public void shouldFilterIsolatedBothExact() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.topic.filters.overlapping.wildcards/client",
        "${kafka}/subscribe.topic.filters.overlapping.wildcards/server"})
    public void shouldFilterOverlappingWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.topic.filters.aggregated.exact.and.wildcard/client",
        "${kafka}/subscribe.topic.filters.aggregated.exact.and.wildcard/server"})
    public void shouldFilterAggregatedExactAndWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.topic.filters.isolated.exact.and.wildcard/client",
        "${kafka}/subscribe.topic.filters.isolated.exact.and.wildcard/server"})
    public void shouldFilterIsolatedExactAndWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.topic.space/client",
        "${kafka}/subscribe.topic.space/server"})
    public void shouldFilterTopicSpace() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.client.topic.space/client",
        "${kafka}/subscribe.client.topic.space/server"})
    public void shouldFilterClientTopicSpace() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.bootstrap.stream.end.reconnect/client",
        "${kafka}/subscribe.bootstrap.stream.end.reconnect/server"})
    public void shouldReconnectBootstrapStreamOnKafkaEnd() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.bootstrap.stream.abort.reconnect/client",
        "${kafka}/subscribe.bootstrap.stream.abort.reconnect/server"})
    public void shouldReconnectBootstrapStreamOnKafkaAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.bootstrap.stream.reset.reconnect/client",
        "${kafka}/subscribe.bootstrap.stream.reset.reconnect/server"})
    public void shouldReconnectBootstrapStreamOnKafkaReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/unsubscribe.after.subscribe/client",
        "${kafka}/unsubscribe.after.subscribe/server"})
    public void shouldAcknowledge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/unsubscribe.topic.filter.single/client",
        "${kafka}/unsubscribe.topic.filter.single/server"})
    public void shouldAcknowledgeSingleTopicFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.close.reconnect.non.expiring/client",
        "${kafka}/session.close.reconnect.non.expiring/server"})
    public void shouldReconnectNonExpiringSession() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.close.expire.session.state.large.interval/client",
        "${kafka}/session.close.expire.session.state.large.interval/server"})
    public void shouldExpireSessionOnCloseAfterLargeInterval() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.close.expire.session.state.immediately/client",
        "${kafka}/session.close.expire.session.state.immediately/server"})
    public void shouldExpireSessionOnCloseImmediately() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.close.reconnect.clean.start/client",
        "${kafka}/session.close.reconnect.clean.start/server"})
    public void shouldReconnectCleanStartAfterNonExpiringSession() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.abort.reconnect.non.clean.start/client",
        "${kafka}/session.abort.reconnect.non.clean.start/server"})
    public void shouldReconnectNonCleanStart() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.subscribe/client",
        "${kafka}/session.subscribe/server"})
    public void shouldSubscribeSaveSubscriptionsInSession() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.subscribe.hash.key/client",
        "${kafka}/session.subscribe.hash.key/server"})
    public void shouldSaveSessionStateWithHashKey() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.reject.non.compacted.sessions.topic/client",
        "${kafka}/session.reject.non.compacted.sessions.topic/server"})
    public void shouldRejectSessionNonCompactedSessionsTopic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.subscribe.via.session.state/client",
        "${kafka}/session.subscribe.via.session.state/server"})
    public void shouldReceiveMessageSubscribedViaSessionState() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.subscribe.via.session.state.multiple.partitions/client",
        "${kafka}/session.subscribe.via.session.state.multiple.partitions/server"})
    public void shouldRestoreSessionStateFromMultiplePartitions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.subscribe.shared.via.session.state/client",
        "${kafka}/session.subscribe.shared.via.session.state/server"})
    public void shouldReceiveSharedSubscriptionViaSessionState() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.unsubscribe.after.subscribe/client",
        "${kafka}/session.unsubscribe.after.subscribe/server"})
    public void shouldUnsubscribeAndUpdateSessionState() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.unsubscribe.via.session.state/client",
        "${kafka}/session.unsubscribe.via.session.state/server"})
    public void shouldUnsubscribeViaSessionState() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.client.sent.reset/client",
        "${kafka}/session.client.sent.reset/server"})
    public void shouldSessionStreamReceiveClientSentReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.server.sent.reset/client",
        "${kafka}/session.server.sent.reset/server"})
    public void shouldSessionStreamReceiveServerSentReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.close.expire.session.offsets.immediately/client",
        "${kafka}/session.close.expire.session.offsets.immediately/server"})
    public void shouldExpireSessionAndOffsetsImmediately() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.close.expire.session.reset.group.offsets/client",
        "${kafka}/session.close.expire.session.reset.group.offsets/server"})
    public void shouldResetGroupOffsetsOnSessionExpiry() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.close.expire.session.reset.group.offsets.small.window/client",
        "${kafka}/session.close.expire.session.reset.group.offsets.small.window/server"})
    public void shouldResetGroupOffsetsOnSessionExpiryAfterSmallWindow() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.reconnect.during.expire.reset.group.offsets/client",
        "${kafka}/session.reconnect.during.expire.reset.group.offsets/server"})
    public void shouldReconnectDuringSessionExpiryGroupOffsetsReset() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("CONNECT_SENT");
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.close.expire.session.offsets.large.interval/client",
        "${kafka}/session.close.expire.session.offsets.large.interval/server"})
    public void shouldExpireSessionAndOffsetsAfterLargeInterval() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.close.expire.session.state/client",
        "${kafka}/session.close.expire.session.state/server"})
    public void shouldExpireSessionOnClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.close.expiry.zero/client",
        "${kafka}/session.close.expiry.zero/server"})
    public void shouldSessionCloseExpiryZero() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.store.clean.start/client",
        "${kafka}/session.store.clean.start/server"})
    public void shouldSessionStoreCleanStart() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.store.clean.start.abort/client",
        "${kafka}/session.store.clean.start.abort/server"})
    public void shouldSessionStoreCleanStartAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.store.clean.start.qos.max.1/client",
        "${kafka}/session.store.clean.start.qos.max.1/server"})
    public void shouldSessionStoreCleanStartQosMax1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.store.persistent.clean.start.reset.group.offsets.coordinator.not.available.qos.max.1/client",
        "${kafka}/session.store.persistent.clean.start.reset.group.offsets.coordinator.not.available.qos.max.1/server"})
    public void shouldSessionStorePersistentCleanStartResetGroupOffsetsCoordinatorNotAvailableQosMax1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.store.persistent.clean.start.discard.offsets/client",
        "${kafka}/session.store.persistent.clean.start.discard.offsets/server"})
    public void shouldSessionStorePersistentCleanStartDiscardOffsets() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.store.persistent.close.reconnect.non.expiring/client",
        "${kafka}/session.store.persistent.close.reconnect.non.expiring/server"})
    public void shouldSessionStorePersistentCloseReconnectNonExpiring() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.store.will.message.normal.disconnect/client",
        "${kafka}/session.store.will.message.normal.disconnect/server"})
    public void shouldSessionStoreWillMessageNormalDisconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.store.will.message.clean.start/client",
        "${kafka}/session.store.will.message.clean.start/server"})
    public void shouldSessionStoreWillMessageCleanStart() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.store.will.message.10k.abort.deliver.will/client",
        "${kafka}/session.store.will.message.10k.abort.deliver.will/server"})
    public void shouldSessionStoreWillMessage10kAbortDeliverWill() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.store.will.message.qos2.abort.deliver.will/client",
        "${kafka}/session.store.will.message.qos2.abort.deliver.will/server"})
    public void shouldSessionStoreWillMessageQos2AbortDeliverWill() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.will.message.qos2.abort.deliver.will/client",
        "${kafka}/session.will.message.qos2.abort.deliver.will/server"})
    public void shouldSessionWillMessageQos2AbortDeliverWill() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.store.subscribe.qos.max.1/client",
        "${kafka}/session.store.subscribe.qos.max.1/server"})
    public void shouldSessionStoreSubscribeQosMax1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.store.unsubscribe.after.subscribe.qos.max.1/client",
        "${kafka}/session.store.unsubscribe.after.subscribe.qos.max.1/server"})
    public void shouldSessionStoreUnsubscribeAfterSubscribeQosMax1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.abort.expire.session.state/client",
        "${kafka}/session.abort.expire.session.state/server"})
    public void shouldExpireSessionOnAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.cancel.session.expiry/client",
        "${kafka}/session.cancel.session.expiry/server"})
    public void shouldCancelSessionExpiry() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.will.message.abort.deliver.will/client",
        "${kafka}/session.will.message.abort.deliver.will/server"})
    public void shouldSendWillMessageOnAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.will.message.abort.deliver.will.signal.buffer.reuse/client",
        "${kafka}/session.will.message.abort.deliver.will.signal.buffer.reuse/server"})
    public void shouldSendWillMessageOnAbortAfterSignalBufferReuse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.will.message.topic.trailing.empty.level/client",
        "${kafka}/session.will.message.topic.trailing.empty.level/server"})
    public void shouldSendWillMessageWithTrailingEmptyLevelTopic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.will.message.abort.deliver.will.non.expiring/client",
        "${kafka}/session.will.message.abort.deliver.will.non.expiring/server"})
    public void shouldSendWillMessageOnAbortNonExpiring() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.will.message.max.delay/client",
        "${kafka}/session.will.message.max.delay/server"})
    public void shouldSendWillMessageWithMaximumDelay() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.will.message.10k.abort.deliver.will/client",
        "${kafka}/session.will.message.10k.abort.deliver.will/server"})
    public void shouldSendWillMessage10kOnAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.will.message.normal.disconnect/client",
        "${kafka}/session.will.message.normal.disconnect/server"})
    public void shouldNotSendWillMessageOnNormalDisconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.will.message.abort.deliver.will.retain/client",
        "${kafka}/session.will.message.abort.deliver.will.retain/server"})
    public void shouldSaveWillMessageAsRetain() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.will.message.clean.start/client",
        "${kafka}/session.will.message.clean.start/server"})
    public void shouldGenerateLifeTimeIdOnCleanStart() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.will.message.will.id.mismatch.skip.delivery/client",
        "${kafka}/session.will.message.will.id.mismatch.skip.delivery/server"})
    public void shouldNotSendWillMessageOnWillIdMismatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.will.message.qos2.abort.before.session.established/client",
        "${kafka}/session.will.message.qos2.abort.before.session.established/server"})
    public void shouldSendWillSignalOnQos2AbortBeforeSessionEstablished() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.qos2.end.before.session.established/client",
        "${kafka}/session.qos2.end.before.session.established/server"})
    public void shouldEndQos2SessionBeforeSessionStreamEstablished() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.qos2.meta.reset/client",
        "${kafka}/session.qos2.meta.reset/server"})
    public void shouldResetMqttSessionWhenMetaStreamReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.qos2.init.producer.reset/client",
        "${kafka}/session.qos2.init.producer.reset/server"})
    public void shouldResetMqttSessionWhenInitProducerStreamReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.qos2.offset.fetch.reset/client",
        "${kafka}/session.qos2.offset.fetch.reset/server"})
    public void shouldResetMqttSessionWhenOffsetFetchStreamReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.qos2.offset.fetch.data.then.caught.up/client",
        "${kafka}/session.qos2.offset.fetch.data.then.caught.up/server"})
    public void shouldSessionQos2OffsetFetchDataThenCaughtUp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.will.message.clean.start.abort.zero.window/client",
        "${kafka}/session.will.message.clean.start.abort.zero.window/server"})
    public void shouldSkipWillSignalOnAbortBeforeKafkaSessionStreamWindowGranted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.will.message.cancel.delivery/client",
        "${kafka}/session.will.message.cancel.delivery/server"})
    public void shouldCancelWillDelivery() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.will.stream.end.reconnect/client",
        "${kafka}/session.will.stream.end.reconnect/server"})
    public void shouldReconnectWillStreamOnKafkaEnd() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.will.stream.abort.reconnect/client",
        "${kafka}/session.will.stream.abort.reconnect/server"})
    public void shouldReconnectWillStreamOnKafkaAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.will.stream.reset.reconnect/client",
        "${kafka}/session.will.stream.reset.reconnect/server"})
    public void shouldReconnectWillStreamOnKafkaReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.session.expiry.fragmented/client",
        "${kafka}/session.session.expiry.fragmented/server"})
    public void shouldDecodeSessionExpirySignalFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.expiry.after.signal.stream.restart/client",
        "${kafka}/session.expiry.after.signal.stream.restart/server"})
    public void shouldExpireSessionAfterSignalStreamRestart() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.expiry.after.signal.stream.restart.multiple.partitions/client",
        "${kafka}/session.expiry.after.signal.stream.restart.multiple.partitions/server"})
    public void shouldExpireSessionAfterSignalStreamRestartFromMultiplePartitions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.signal.stream.credit/client",
        "${kafka}/session.signal.stream.credit/server"})
    public void shouldSessionSignalStreamCredit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.expiry.signal.fragmented.local.ignored/client",
        "${kafka}/session.expiry.signal.fragmented.local.ignored/server"})
    public void shouldDecodeSessionSignalAfterFragmentedLocalSignalIgnored() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.expiry.signal.malformed.dropped/client",
        "${kafka}/session.expiry.signal.malformed.dropped/server"})
    public void shouldDropMalformedSessionSignal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.ignore.non.fetch.signal.stream.flush/client",
        "${kafka}/session.ignore.non.fetch.signal.stream.flush/server"})
    public void shouldIgnoreNonFetchSignalStreamFlush() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.qos1/client",
        "${kafka}/publish.qos1/server"})
    public void shouldPublishQoS1Message() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.qos2/client",
        "${kafka}/publish.qos2/server"})
    public void shouldPublishQoS2Message() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.qos2.session.store/client",
        "${kafka}/publish.qos2.session.store/server"})
    public void shouldPublishQoS2SessionStore() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.qos2.offset.commit.credit.window/client",
        "${kafka}/publish.qos2.offset.commit.credit.window/server"})
    public void shouldPublishQoS2MessageOffsetCommitCreditWindow() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.qos2.complete.after.acknowledge/client",
        "${kafka}/publish.qos2.complete.after.acknowledge/server"})
    public void shouldPublishQoS2CompleteAfterAcknowledge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.clean.start.discard.offsets/client",
        "${kafka}/session.clean.start.discard.offsets/server"})
    public void shouldDiscardOffsetsOnCleanStart() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.clean.start.reset.group.offsets.negotiated/client",
        "${kafka}/session.clean.start.reset.group.offsets.negotiated/server"})
    public void shouldResetGroupOffsetsOnCleanStartNegotiated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.clean.start.reset.group.offsets.partition.error/client",
        "${kafka}/session.clean.start.reset.group.offsets.partition.error/server"})
    public void shouldResetGroupOffsetsOnCleanStartPartitionError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.clean.start.reset.group.offsets.group.not.found/client",
        "${kafka}/session.clean.start.reset.group.offsets.group.not.found/server"})
    public void shouldResetGroupOffsetsOnCleanStartGroupNotFound() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.clean.start.reset.group.offsets.coordinator.not.available/client",
        "${kafka}/session.clean.start.reset.group.offsets.coordinator.not.available/server"})
    public void shouldResetGroupOffsetsOnCleanStartCoordinatorNotAvailable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.clean.start.reset.group.offsets.coordinator.not.available.qos.max.1/client",
        "${kafka}/session.clean.start.reset.group.offsets.coordinator.not.available.qos.max.1/server"})
    public void shouldResetGroupOffsetsOnCleanStartCoordinatorNotAvailableWhenPublishQosMax1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.clean.start.reset.group.offsets.broker.zero/client",
        "${kafka}/session.clean.start.reset.group.offsets.broker.zero/server"})
    public void shouldResetGroupOffsetsOnCleanStartBrokerZero() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.qos2.no.prior.offsets/client",
        "${kafka}/publish.qos2.no.prior.offsets/server"})
    public void shouldPublishQoS2MessageNoPriorOffsets() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.qos2.retained/client",
        "${kafka}/publish.qos2.retained/server"})
    public void shouldPublishQoS2MessageRetained() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.qos2.recovery/client",
        "${kafka}/publish.qos2.recovery/server"})
    public void shouldPublishQoS2MessageDuringRecovery() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.qos2.abort.after.publish/client",
        "${kafka}/publish.qos2.abort.after.publish/server"})
    public void shouldPublishQoS2MessageAbortAfterPublish() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.qos2.abort.after.pubrel/client",
        "${kafka}/publish.qos2.abort.after.pubrel/server"})
    public void shouldPublishQoS2MessageAbortAfterPubrel() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.qos2.meta.abort/client",
        "${kafka}/publish.qos2.meta.abort/server"})
    public void shouldSessionReceiveQos2MetaSentAbort() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.qos2.offset.fetch.abort/client",
        "${kafka}/publish.qos2.offset.fetch.abort/server"})
    public void shouldSessionReceiveQos2OffsetFetchSentAbort() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.qos2.init.producer.abort/client",
        "${kafka}/publish.qos2.init.producer.abort/server"})
    public void shouldSessionReceiveQos2InitProducerSentAbort() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.qos2.offset.commit.abort.phase1/client",
        "${kafka}/publish.qos2.offset.commit.abort.phase1/server"})
    public void shouldPublishReceiveQos2OffsetCommitSentAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.qos2.offset.commit.abort.phase2/client",
        "${kafka}/publish.qos2.offset.commit.abort.phase2/server"})
    public void shouldSessionReceiveQos2OffsetCommitSentAbort() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.mixture.qos/client",
        "${kafka}/publish.mixture.qos/server"})
    public void shouldSendMessageMixtureQos() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.reject.large.message/client",
        "${kafka}/publish.reject.large.message/server"})
    public void shouldRejectLargeMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.receive.message.qos1/client",
        "${kafka}/subscribe.receive.message.qos1/server"})
    public void shouldReceiveMessageQoS1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.receive.message.qos1.session.store/client",
        "${kafka}/subscribe.receive.message.qos1.session.store/server"})
    public void shouldReceiveMessageQoS1WithSessionStateStore() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.resume.qos1.outbound/client",
        "${kafka}/subscribe.resume.qos1.outbound/server"})
    public void shouldResumeSubscribeQoS1Outbound() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.mixed.qos.second.client.begin.keeps.pending.offset.commit/client",
        "${kafka}/subscribe.mixed.qos.second.client.begin.keeps.pending.offset.commit/server"})
    public void shouldKeepPendingOffsetCommitWhenSecondClientBeginsSubscribe() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.qos2.second.client.restore.keeps.packet.id.offset/client",
        "${kafka}/subscribe.qos2.second.client.restore.keeps.packet.id.offset/server"})
    public void shouldKeepPacketIdOffsetWhenSecondClientRestoresQoS2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.receive.message.qos1.multiple.clients/client",
        "${kafka}/subscribe.receive.message.qos1.multiple.clients/server"})
    public void shouldReceiveMessageQoS1FromMultipleClients() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.receive.message.qos1.published.qos2/client",
        "${kafka}/subscribe.receive.message.qos1.published.qos2/server"})
    public void shouldReceiveMessageQoS1PublishedAsQoS2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.receive.message.qos2/client",
        "${kafka}/subscribe.receive.message.qos2/server"})
    public void shouldReceiveMessageQoS2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.receive.message.qos2.session.store/client",
        "${kafka}/subscribe.receive.message.qos2.session.store/server"})
    public void shouldReceiveMessageQoS2WithSessionStateStore() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.receive.messages.qos2.sparse.offsets/client",
        "${kafka}/subscribe.receive.messages.qos2.sparse.offsets/server"})
    public void shouldReceiveMessagesQoS2SparseOffsets() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.qos2.version1.offset.metadata/client",
        "${kafka}/subscribe.qos2.version1.offset.metadata/server"})
    public void shouldReceiveMessageQoS2WithVersion1OffsetMetadata() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.receive.messages.mixture.qos/client",
        "${kafka}/subscribe.receive.messages.mixture.qos/server"})
    public void shouldReceiveMessagesMixtureQos() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.reconnect.replay.qos1.unacked.message/client",
        "${kafka}/subscribe.reconnect.replay.qos1.unacked.message/server"})
    public void shouldReplayUnackedQoS1MessageAtReconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.reconnect.replay.qos2.unreceived.message/client",
        "${kafka}/subscribe.reconnect.replay.qos2.unreceived.message/server"})
    public void shouldReplayUnreceivedQoS2MessageAtReconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.reconnect.replay.qos2.incomplete.message/client",
        "${kafka}/subscribe.reconnect.replay.qos2.incomplete.message/server"})
    public void shouldReplayIncompleteQoS2MessageAtReconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.replay.retained.message.qos1/client",
        "${kafka}/subscribe.replay.retained.message.qos1/server"})
    public void shouldReplayRetainedQos1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.replay.retained.message.qos2/client",
        "${kafka}/subscribe.replay.retained.message.qos2/server"})
    public void shouldReplayRetainedQos2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.receive.message.overlapping.wildcard.mixed.qos/client",
        "${kafka}/subscribe.receive.message.overlapping.wildcard.mixed.qos/server"})
    public void shouldReceiveMessageOverlappingWildcardMixedQos() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.expire.message/client",
        "${kafka}/subscribe.expire.message/server"})
    public void shouldExpireMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.expire.message.fragmented/client",
        "${kafka}/subscribe.expire.message.fragmented/server"})
    public void shouldExpireMessageFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.message.expiry.interval.reduced/client",
        "${kafka}/subscribe.message.expiry.interval.reduced/server"})
    public void shouldReceiveMessageWithReducedExpiryInterval() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.retained.message.expiry.interval.reduced/client",
        "${kafka}/subscribe.retained.message.expiry.interval.reduced/server"})
    public void shouldReceiveRetainedMessageWithReducedExpiryInterval() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.retained.expire.message/client",
        "${kafka}/subscribe.retained.expire.message/server"})
    public void shouldExpireRetainedMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.server.sent.abort.before.begin/client",
        "${kafka}/publish.server.sent.abort.before.begin/server"})
    public void shouldReceiveServerSentAbortBeforeBeginPublish() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.server.sent.abort.before.begin/client",
        "${kafka}/subscribe.server.sent.abort.before.begin/server"})
    public void shouldReceiveServerSentAbortBeforeBeginSubscribe() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.publish.qos.max.1/client",
        "${kafka}/session.publish.qos.max.1/server"})
    public void shouldAdvertisePublishQosMax1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.capabilities/client",
        "${kafka}/session.capabilities/server"})
    public void shouldSessionCapabilities() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.retained.topic.not.compacted/client",
        "${kafka}/session.retained.topic.not.compacted/server"})
    public void shouldAdvertiseRetainedUnavailableWhenRetainedTopicNotCompacted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.reconnect.shared.subscription/client",
        "${kafka}/session.reconnect.shared.subscription/server"})
    public void shouldSessionReconnectSharedSubscription() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.publish.qos.max.0/client",
        "${kafka}/session.publish.qos.max.0/server"})
    public void shouldAdvertisePublishQosMax0() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.empty.retained.message/client",
        "${kafka}/publish.empty.retained.message/server"})
    public void shouldPublishEmptyRetainedMessageAsTombstone() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.shared.and.non.shared.receive.messages/client",
        "${kafka}/subscribe.shared.and.non.shared.receive.messages/server"})
    public void shouldSubscribeSharedAndNonSharedReceiveMessages() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.retained.tombstone/client",
        "${kafka}/subscribe.retained.tombstone/server"})
    public void shouldSkipTombstoneOnRetainedReplay() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.shared.client.sent.abort/client",
        "${kafka}/subscribe.shared.client.sent.abort/server"})
    public void shouldSubscribeSharedClientSentAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.retained.empty.value/client",
        "${kafka}/subscribe.retained.empty.value/server"})
    public void shouldSkipEmptyValueOnRetainedReplay() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.shared.client.sent.reset/client",
        "${kafka}/subscribe.shared.client.sent.reset/server"})
    public void shouldSubscribeSharedClientSentReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.flush.unknown.packet.id/client",
        "${kafka}/session.flush.unknown.packet.id/server"})
    public void shouldAnswerSessionFlushWithUnknownPacketId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.shared.distinct.filters/client",
        "${kafka}/subscribe.shared.distinct.filters/server"})
    public void shouldSubscribeSharedDistinctFilters() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.qos1.server.sent.reset/client",
        "${kafka}/publish.qos1.server.sent.reset/server"})
    public void shouldPublishQos1ServerSentReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.shared.distinct.share.names/client",
        "${kafka}/subscribe.shared.distinct.share.names/server"})
    public void shouldSubscribeSharedDistinctShareNames() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.qos1.server.sent.reset.in.flight/client",
        "${kafka}/publish.qos1.server.sent.reset.in.flight/server"})
    public void shouldPublishQos1ServerSentResetInFlight() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.shared.expire.message/client",
        "${kafka}/subscribe.shared.expire.message/server"})
    public void shouldSubscribeSharedExpireMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.shared.message.expiry.interval.reduced/client",
        "${kafka}/subscribe.shared.message.expiry.interval.reduced/server"})
    public void shouldSubscribeSharedMessageExpiryIntervalReduced() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.qos1.retain.available.server.sent.reset.in.flight/client",
        "${kafka}/publish.qos1.retain.available.server.sent.reset.in.flight/server"})
    public void shouldPublishQos1RetainAvailableServerSentResetInFlight() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.shared.receive.message.qos0/client",
        "${kafka}/subscribe.shared.receive.message.qos0/server"})
    public void shouldSubscribeSharedReceiveMessageQos0() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.qos1.server.sent.reset.after.acknowledged/client",
        "${kafka}/publish.qos1.server.sent.reset.after.acknowledged/server"})
    public void shouldPublishQos1ServerSentResetAfterAcknowledged() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.shared.receive.message.qos1/client",
        "${kafka}/subscribe.shared.receive.message.qos1/server"})
    public void shouldSubscribeSharedReceiveMessageQos1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.qos1.reject.large.message/client",
        "${kafka}/publish.qos1.reject.large.message/server"})
    public void shouldPublishQos1RejectLargeMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.shared.receive.message.qos1.granted.qos0/client",
        "${kafka}/subscribe.shared.receive.message.qos1.granted.qos0/server"})
    public void shouldSubscribeSharedReceiveMessageQos1GrantedQos0() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.qos1.retained.server.sent.reset/client",
        "${kafka}/publish.qos1.retained.server.sent.reset/server"})
    public void shouldPublishQos1RetainedServerSentReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.shared.receive.message.qos1.published.qos2/client",
        "${kafka}/subscribe.shared.receive.message.qos1.published.qos2/server"})
    public void shouldSubscribeSharedReceiveMessageQos1PublishedQos2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.subscribe.unmatched.filter/client",
        "${kafka}/session.subscribe.unmatched.filter/server"})
    public void shouldSessionSubscribeUnmatchedFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.shared.receive.message.qos1.reject.with.reasoncode/client",
        "${kafka}/subscribe.shared.receive.message.qos1.reject.with.reasoncode/server"})
    public void shouldSubscribeSharedReceiveMessageQos1RejectWithReasoncode() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.subscribe.unmatched.all/client",
        "${kafka}/session.subscribe.unmatched.all/server"})
    public void shouldSessionSubscribeUnmatchedAll() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.shared.reject.filter.mismatch/client",
        "${kafka}/subscribe.shared.reject.filter.mismatch/server"})
    public void shouldSubscribeSharedRejectFilterMismatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.subscribe.unmatched.resume/client",
        "${kafka}/session.subscribe.unmatched.resume/server"})
    public void shouldSessionSubscribeUnmatchedResume() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.one.message.subscription.id.127/client",
        "${kafka}/subscribe.one.message.subscription.id.127/server"})
    public void shouldSubscribeOneMessageSubscriptionId127() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.receive.message.parent.level/client",
        "${kafka}/subscribe.receive.message.parent.level/server"})
    public void shouldReceiveMessageAtParentLevel() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.receive.message.parent.level.overlapping/client",
        "${kafka}/subscribe.receive.message.parent.level.overlapping/server"})
    public void shouldReceiveMessageAtParentLevelWithOverlappingFilters() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.retain.parent.level/client",
        "${kafka}/subscribe.retain.parent.level/server"})
    public void shouldReceiveRetainedAtParentLevel() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.shared.reject.unsupported.version/client",
        "${kafka}/subscribe.shared.reject.unsupported.version/server"})
    public void shouldSubscribeSharedRejectUnsupportedVersion() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.shared.resubscribe.change.qos/client",
        "${kafka}/subscribe.shared.resubscribe.change.qos/server"})
    public void shouldSubscribeSharedResubscribeChangeQos() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.shared.server.sent.abort/client",
        "${kafka}/subscribe.shared.server.sent.abort/server"})
    public void shouldSubscribeSharedServerSentAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.shared.expire.message.fragmented/client",
        "${kafka}/subscribe.shared.expire.message.fragmented/server"})
    public void shouldSubscribeSharedExpireMessageFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.shared.receive.message.qos0.fragmented/client",
        "${kafka}/subscribe.shared.receive.message.qos0.fragmented/server"})
    public void shouldSubscribeSharedReceiveMessageQos0Fragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.shared.receive.message.qos1.fragmented/client",
        "${kafka}/subscribe.shared.receive.message.qos1.fragmented/server"})
    public void shouldSubscribeSharedReceiveMessageQos1Fragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.shared.receive.message.qos1.reject.with.reasoncode.fragmented/client",
        "${kafka}/subscribe.shared.receive.message.qos1.reject.with.reasoncode.fragmented/server"})
    public void shouldSubscribeSharedReceiveMessageQos1RejectWithReasoncodeFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.subscribe.shared.granted.qos2/client",
        "${kafka}/session.subscribe.shared.granted.qos2/server"})
    public void shouldSessionSubscribeSharedGrantedQos2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.shared.expire.message.qos2/client",
        "${kafka}/subscribe.shared.expire.message.qos2/server"})
    public void shouldSubscribeSharedExpireMessageQos2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.shared.receive.message.qos2/client",
        "${kafka}/subscribe.shared.receive.message.qos2/server"})
    public void shouldSubscribeSharedReceiveMessageQos2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.shared.receive.message.qos2.existing.tickets/client",
        "${kafka}/subscribe.shared.receive.message.qos2.existing.tickets/server"})
    public void shouldSubscribeSharedReceiveMessageQos2ExistingTickets() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.shared.recover.qos2.pubrel.pending/client",
        "${kafka}/subscribe.shared.recover.qos2.pubrel.pending/server"})
    public void shouldSubscribeSharedRecoverQos2PubrelPending() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.shared.unsubscribe.inflight.qos2.disconnect.recover.publish.sent/client",
        "${kafka}/subscribe.shared.unsubscribe.inflight.qos2.disconnect.recover.publish.sent/server"})
    public void shouldSubscribeSharedUnsubscribeInflightQos2DisconnectRecoverPublishSent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.shared.unsubscribe.inflight.qos2.disconnect.recover.pubrel.pending/client",
        "${kafka}/subscribe.shared.unsubscribe.inflight.qos2.disconnect.recover.pubrel.pending/server"})
    public void shouldSubscribeSharedUnsubscribeInflightQos2DisconnectRecoverPubrelPending() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.shared.unsubscribe.release.qos1/client",
        "${kafka}/subscribe.shared.unsubscribe.release.qos1/server"})
    public void shouldSubscribeSharedUnsubscribeReleaseQos1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.shared.unsubscribe.release.qos2/client",
        "${kafka}/subscribe.shared.unsubscribe.release.qos2/server"})
    public void shouldSubscribeSharedUnsubscribeReleaseQos2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.shared.unsubscribe.inflight.then.release/client",
        "${kafka}/subscribe.shared.unsubscribe.inflight.then.release/server"})
    public void shouldSubscribeSharedUnsubscribeInflightThenRelease() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.shared.recover.qos2.publish.sent/client",
        "${kafka}/subscribe.shared.recover.qos2.publish.sent/server"})
    public void shouldSubscribeSharedRecoverQos2PublishSent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.shared.recover.qos2.publish.sent.fragmented/client",
        "${kafka}/subscribe.shared.recover.qos2.publish.sent.fragmented/server"})
    public void shouldSubscribeSharedRecoverQos2PublishSentFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.shared.recover.qos2.publish.sent.offset.mismatch/client",
        "${kafka}/subscribe.shared.recover.qos2.publish.sent.offset.mismatch/server"})
    public void shouldSubscribeSharedRecoverQos2PublishSentOffsetMismatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.shared.recover.qos2.publish.sent.expired/client",
        "${kafka}/subscribe.shared.recover.qos2.publish.sent.expired/server"})
    public void shouldSubscribeSharedRecoverQos2PublishSentExpired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.shared.recover.qos2.publish.sent.missing/client",
        "${kafka}/subscribe.shared.recover.qos2.publish.sent.missing/server"})
    public void shouldSubscribeSharedRecoverQos2PublishSentMissing() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.shared.recover.qos2.publish.sent.beyond.end/client",
        "${kafka}/subscribe.shared.recover.qos2.publish.sent.beyond.end/server"})
    public void shouldSubscribeSharedRecoverQos2PublishSentBeyondEnd() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.shared.recover.qos2.then.receive.message/client",
        "${kafka}/subscribe.shared.recover.qos2.then.receive.message/server"})
    public void shouldSubscribeSharedRecoverQos2ThenReceiveMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.shared.receive.message.qos2.fragmented/client",
        "${kafka}/subscribe.shared.receive.message.qos2.fragmented/server"})
    public void shouldSubscribeSharedReceiveMessageQos2Fragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.shared.receive.message.qos2.reject.with.reasoncode/client",
        "${kafka}/subscribe.shared.receive.message.qos2.reject.with.reasoncode/server"})
    public void shouldSubscribeSharedReceiveMessageQos2RejectWithReasoncode() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.shared.receive.messages.qos2/client",
        "${kafka}/subscribe.shared.receive.messages.qos2/server"})
    public void shouldSubscribeSharedReceiveMessagesQos2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.unsubscribe.shared/client",
        "${kafka}/session.unsubscribe.shared/server"})
    public void shouldUnsubscribeSharedAndLeave() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.resubscribe.shared/client",
        "${kafka}/session.resubscribe.shared/server"})
    public void shouldResubscribeSharedAfterLeaving() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.shared.restore.membership/client",
        "${kafka}/subscribe.shared.restore.membership/server"})
    public void shouldSubscribeSharedRestoreMembership() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/subscribe.shared.join.new.life/client",
        "${kafka}/subscribe.shared.join.new.life/server"})
    public void shouldSubscribeSharedJoinNewLife() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.close.end.expiry.zero/client",
        "${kafka}/session.close.end.expiry.zero/server"})
    public void shouldCloseSessionWithEndExpiryZero() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.expiry.signal.replaced/client",
        "${kafka}/session.expiry.signal.replaced/server"})
    public void shouldReplaceSessionExpiryTimerForSameClient() throws Exception
    {
        k3po.finish();
    }
}
