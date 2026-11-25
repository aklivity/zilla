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

public class MqttIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("mqtt", "io/aklivity/zilla/specs/binding/mqtt/kafka/streams/mqtt");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${mqtt}/publish.client.sent.abort/client",
        "${mqtt}/publish.client.sent.abort/server"})
    public void shouldReceiveClientSentAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.client.sent.reset/client",
        "${mqtt}/publish.client.sent.reset/server"})
    public void shouldReceiveClientSentReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.server.sent.abort/client",
        "${mqtt}/publish.server.sent.abort/server"})
    public void shouldPublishReceiveServerSentAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.server.sent.reset/client",
        "${mqtt}/publish.server.sent.reset/server"})
    public void shouldPublishReceiveServerSentReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.server.sent.data/client",
        "${mqtt}/publish.server.sent.data/server"})
    public void shouldPublishAbortWhenServerSentData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.empty.message/client",
        "${mqtt}/publish.empty.message/server"})
    public void shouldSendEmptyMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.one.message/client",
        "${mqtt}/publish.one.message/server"})
    public void shouldSendOneMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.topic.trailing.empty.level/client",
        "${mqtt}/publish.topic.trailing.empty.level/server"})
    public void shouldSendTopicWithTrailingEmptyLevel() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.10k/client",
        "${mqtt}/publish.10k/server"})
    public void shouldSendMessage10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.retained.10k/client",
        "${mqtt}/publish.retained.10k/server"})
    public void shouldSendRetainedMessage10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.multiple.messages/client",
        "${mqtt}/publish.multiple.messages/server"})
    public void shouldSendMultipleMessages() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.multiple.clients/client",
        "${mqtt}/publish.multiple.clients/server"})
    public void shouldSendMultipleClients() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.topic.space/client",
        "${mqtt}/publish.topic.space/server"})
    public void shouldSendUsingTopicSpace() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.topic.space.with.params/client",
        "${mqtt}/publish.topic.space.with.params/server"})
    public void shouldSendUsingTopicSpaceWithParams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.reject.qos2/client",
        "${mqtt}/publish.reject.qos2/server"})
    public void shouldRejectPublishWhenTopicSpaceWithParams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.client.topic.space/client",
        "${mqtt}/publish.client.topic.space/server"})
    public void shouldSendUsingClientTopicSpace() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.retained/client",
        "${mqtt}/publish.retained/server"})
    public void shouldPublishRetainedMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.many.messages.retain.available/client",
        "${mqtt}/publish.many.messages.retain.available/server"})
    public void shouldPublishManyMessagesRetainAvailable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.with.user.properties.distinct/client",
        "${mqtt}/publish.with.user.properties.distinct/server"})
    public void shouldSendWithDistinctUserProperties() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.with.user.properties.repeated/client",
        "${mqtt}/publish.with.user.properties.repeated/server"})
    public void shouldSendWithRepeatedUserProperties() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.with.user.property/client",
        "${mqtt}/publish.with.user.property/server"})
    public void shouldSendWithUserProperty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.client.sent.abort/client",
        "${mqtt}/subscribe.client.sent.abort/server"})
    public void shouldSubscribeReceiveClientSentAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.client.sent.data/client",
        "${mqtt}/subscribe.client.sent.data/server"})
    public void shouldSubscribeAbortWhenClientSentData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.client.sent.reset/client",
        "${mqtt}/subscribe.client.sent.reset/server"})
    public void shouldSubscribeReceiveClientSentReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.server.sent.abort/client",
        "${mqtt}/subscribe.server.sent.abort/server"})
    public void shouldSubscribeReceiveServerSentAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.server.sent.reset/client",
        "${mqtt}/subscribe.server.sent.reset/server"})
    public void shouldSubscribeReceiveServerSentReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.one.message/client",
        "${mqtt}/subscribe.one.message/server"})
    public void shouldReceiveOneMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.publish.retained.retain.as.published/client",
        "${mqtt}/subscribe.publish.retained.retain.as.published/server"})
    public void shouldSubscribePublishRetainedRetainAsPublished() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.publish.retained.no.retain.as.published/client",
        "${mqtt}/subscribe.publish.retained.no.retain.as.published/server"})
    public void shouldSubscribePublishRetainedNoRetainAsPublished() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.publish.not.retained.retain.as.published/client",
        "${mqtt}/subscribe.publish.not.retained.retain.as.published/server"})
    public void shouldSubscribePublishNotRetainedRetainAsPublished() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.publish.retained.retain.as.published.multiple.filters/client",
        "${mqtt}/subscribe.publish.retained.retain.as.published.multiple.filters/server"})
    public void shouldSubscribePublishRetainedRetainAsPublishedMultipleFilters() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.retain.shared.subscription.id/client",
        "${mqtt}/subscribe.retain.shared.subscription.id/server"})
    public void shouldReceiveRetainedWithSharedSubscriptionId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.filter.change.retain.reordered/client",
        "${mqtt}/subscribe.filter.change.retain.reordered/server"})
    public void shouldReceiveRetainedAfterFilterChangeReordered() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.one.message.receive.response.topic.and.correlation.data/client",
        "${mqtt}/subscribe.one.message.receive.response.topic.and.correlation.data/server"})
    public void shouldReceiveCorrelationData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.retain/client",
        "${mqtt}/subscribe.retain/server"})
    public void shouldReceiveRetainedNoRetainAsPublished() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.retain.as.published/client",
        "${mqtt}/subscribe.retain.as.published/server"})
    public void shouldReceiveRetainAsPublished() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.filter.change.retain/client",
        "${mqtt}/subscribe.filter.change.retain/server"})
    public void shouldReceiveRetainedAfterFilterChange() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.filter.change.retain.buffer.fragmented/client",
        "${mqtt}/subscribe.filter.change.retain.buffer.fragmented/server"})
    public void shouldReceiveRetainedAfterFilterChangeBufferFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.filter.change.retain.buffer.fragment.completed/client",
        "${mqtt}/subscribe.filter.change.retain.buffer.fragment.completed/server"})
    public void shouldReceiveRetainedAfterFilterChangeBufferFragmentCompleted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.filter.change.retain.no.local/client",
        "${mqtt}/subscribe.filter.change.retain.no.local/server"})
    public void shouldApplyConditionsToRetainedAfterFilterChange() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.deferred.filter.change.retain/client",
        "${mqtt}/subscribe.deferred.filter.change.retain/server"})
    public void shouldReceiveRetainedAfterFilterChangeDeferred() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.filter.change.retain.resubscribe/client",
        "${mqtt}/subscribe.filter.change.retain.resubscribe/server"})
    public void shouldReceiveRetainedAfterFilterChangeResubscribe() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.publish.no.local/client",
        "${mqtt}/subscribe.publish.no.local/server"})
    public void shouldNotReceiveLocal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.receive.message.overlapping.wildcard/client",
        "${mqtt}/subscribe.receive.message.overlapping.wildcard/server"})
    public void shouldReceiveMessageOverlappingWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.receive.message.wildcard/client",
        "${mqtt}/subscribe.receive.message.wildcard/server"})
    public void shouldReceiveOneMessageWithPatternTopic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.topic.filter.multi.level.wildcard/client",
        "${mqtt}/subscribe.topic.filter.multi.level.wildcard/server"})
    public void shouldFilterMultiLevelWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.topic.filter.single.level.wildcard/client",
        "${mqtt}/subscribe.topic.filter.single.level.wildcard/server"})
    public void shouldFilterSingleLevelWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.topic.filter.trailing.empty.level/client",
        "${mqtt}/subscribe.topic.filter.trailing.empty.level/server"})
    public void shouldFilterTrailingEmptyLevel() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.topic.filter.single.and.multi.level.wildcard/client",
        "${mqtt}/subscribe.topic.filter.single.and.multi.level.wildcard/server"})
    public void shouldFilterSingleAndMultiLevelWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.one.message.user.properties.unaltered/client",
        "${mqtt}/subscribe.one.message.user.properties.unaltered/server"})
    public void shouldReceiveOneMessageWithUserPropertiesUnaltered() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.topic.filter.two.single.level.wildcard/client",
        "${mqtt}/subscribe.topic.filter.two.single.level.wildcard/server"})
    public void shouldFilterTwoSingleLevelWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.topic.filters.aggregated.both.exact/client",
        "${mqtt}/subscribe.topic.filters.aggregated.both.exact/server"})
    public void shouldFilterAggregatedBothExact() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.topic.filters.isolated.both.exact/client",
        "${mqtt}/subscribe.topic.filters.isolated.both.exact/server"})
    public void shouldFilterIsolatedBothExact() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.topic.filters.overlapping.wildcards/client",
        "${mqtt}/subscribe.topic.filters.overlapping.wildcards/server"})
    public void shouldFilterOverlappingWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.topic.filters.aggregated.exact.and.wildcard/client",
        "${mqtt}/subscribe.topic.filters.aggregated.exact.and.wildcard/server"})
    public void shouldFilterAggregatedExactAndWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.topic.filters.isolated.exact.and.wildcard/client",
        "${mqtt}/subscribe.topic.filters.isolated.exact.and.wildcard/server"})
    public void shouldFilterIsolatedExactAndWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.topic.space/client",
        "${mqtt}/subscribe.topic.space/server"})
    public void shouldFilterTopicSpace() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.client.topic.space/client",
        "${mqtt}/subscribe.client.topic.space/server"})
    public void shouldFilterClientTopicSpace() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("RECEIVED_BOOTSTRAP_CONNECTED");
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/unsubscribe.after.subscribe/client",
        "${mqtt}/unsubscribe.after.subscribe/server"})
    public void shouldAcknowledge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/unsubscribe.topic.filter.single/client",
        "${mqtt}/unsubscribe.topic.filter.single/server"})
    public void shouldAcknowledgeSingleTopicFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.abort.reconnect.non.clean.start/client",
        "${mqtt}/session.abort.reconnect.non.clean.start/server"})
    public void shouldReconnectNonCleanStart() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.subscribe/client",
        "${mqtt}/session.subscribe/server"})
    public void shouldSubscribeSaveSubscriptionsInSession() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.subscribe.hash.key/client",
        "${mqtt}/session.subscribe.hash.key/server"})
    public void shouldSaveSessionStateWithHashKey() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.reject.non.compacted.sessions.topic/client",
        "${mqtt}/session.reject.non.compacted.sessions.topic/server"})
    public void shouldRejectSessionNonCompactedSessionsTopic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.subscribe.via.session.state/client",
        "${mqtt}/session.subscribe.via.session.state/server"})
    public void shouldReceiveMessageSubscribedViaSessionState() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.subscribe.via.session.state.multiple.partitions/client",
        "${mqtt}/session.subscribe.via.session.state.multiple.partitions/server"})
    public void shouldRestoreSessionStateFromMultiplePartitions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.subscribe.shared.via.session.state/client",
        "${mqtt}/session.subscribe.shared.via.session.state/server"})
    public void shouldReceiveSharedSubscriptionViaSessionState() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.unsubscribe.after.subscribe/client",
        "${mqtt}/session.unsubscribe.after.subscribe/server"})
    public void shouldUnsubscribeAndUpdateSessionState() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.unsubscribe.via.session.state/client",
        "${mqtt}/session.unsubscribe.via.session.state/server"})
    public void shouldUnsubscribeViaSessionState() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.client.sent.reset/client",
        "${mqtt}/session.client.sent.reset/server"})
    public void shouldSessionStreamReceiveClientSentReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.server.sent.reset/client",
        "${mqtt}/session.server.sent.reset/server"})
    public void shouldSessionStreamReceiveServerSentReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.close.reconnect.non.expiring/client",
        "${mqtt}/session.close.reconnect.non.expiring/server"})
    public void shouldReconnectNonExpiringSession() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.close.expire.session.state.large.interval/client",
        "${mqtt}/session.close.expire.session.state.large.interval/server"})
    public void shouldExpireSessionOnCloseAfterLargeInterval() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.close.expire.session.state.immediately/client",
        "${mqtt}/session.close.expire.session.state.immediately/server"})
    public void shouldExpireSessionOnCloseImmediately() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.close.reconnect.clean.start/client",
        "${mqtt}/session.close.reconnect.clean.start/server"})
    public void shouldReconnectCleanStartAfterNonExpiringSession() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.reconnect.during.expire.reset.group.offsets/client",
        "${mqtt}/session.reconnect.during.expire.reset.group.offsets/server"})
    public void shouldReconnectDuringSessionExpiryGroupOffsetsReset() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("OFFSET_DELETE_RECEIVED");
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.close.expire.session.offsets.immediately/client",
        "${mqtt}/session.close.expire.session.offsets.immediately/server"})
    public void shouldExpireSessionAndOffsetsImmediately() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("SIGNAL_STREAM_STARTED");
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.close.expire.session.offsets.large.interval/client",
        "${mqtt}/session.close.expire.session.offsets.large.interval/server"})
    public void shouldExpireSessionAndOffsetsAfterLargeInterval() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("SIGNAL_STREAM_STARTED");
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.close.expire.session.state/client",
        "${mqtt}/session.close.expire.session.state/server"})
    public void shouldExpireSessionOnClose() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("SIGNAL_STREAM_STARTED");
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.close.expiry.zero/client",
        "${mqtt}/session.close.expiry.zero/server"})
    public void shouldSessionCloseExpiryZero() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.store.clean.start/client",
        "${mqtt}/session.store.clean.start/server"})
    public void shouldSessionStoreCleanStart() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.store.clean.start.abort/client",
        "${mqtt}/session.store.clean.start.abort/server"})
    public void shouldSessionStoreCleanStartAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.store.clean.start.qos.max.1/client",
        "${mqtt}/session.store.clean.start.qos.max.1/server"})
    public void shouldSessionStoreCleanStartQosMax1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.store.subscribe.qos.max.1/client",
        "${mqtt}/session.store.subscribe.qos.max.1/server"})
    public void shouldSessionStoreSubscribeQosMax1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.store.unsubscribe.after.subscribe.qos.max.1/client",
        "${mqtt}/session.store.unsubscribe.after.subscribe.qos.max.1/server"})
    public void shouldSessionStoreUnsubscribeAfterSubscribeQosMax1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.store.persistent.subscribe.qos.max.1/client",
        "${mqtt}/session.store.persistent.subscribe.qos.max.1/server"})
    public void shouldSessionStorePersistentSubscribeQosMax1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.store.persistent.close.reconnect.non.expiring/client",
        "${mqtt}/session.store.persistent.close.reconnect.non.expiring/server"})
    public void shouldSessionStorePersistentCloseReconnectNonExpiring() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.store.will.message.subscribe/client",
        "${mqtt}/session.store.will.message.subscribe/server"})
    public void shouldSessionStoreWillMessageSubscribe() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("WILL_STREAM_STARTED");
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.abort.expire.session.state/client",
        "${mqtt}/session.abort.expire.session.state/server"})
    public void shouldExpireSessionOnAbort() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("SIGNAL_STREAM_STARTED");
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.will.message.abort.deliver.will/client",
        "${mqtt}/session.will.message.abort.deliver.will/server"})
    public void shouldSendWillMessageOnAbort() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("WILL_STREAM_STARTED");
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.will.message.abort.deliver.will.signal.buffer.reuse/client",
        "${mqtt}/session.will.message.abort.deliver.will.signal.buffer.reuse/server"})
    public void shouldSendWillMessageOnAbortAfterSignalBufferReuse() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("WILL_STREAM_STARTED");
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.will.message.topic.trailing.empty.level/client",
        "${mqtt}/session.will.message.topic.trailing.empty.level/server"})
    public void shouldSendWillMessageWithTrailingEmptyLevelTopic() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("WILL_STREAM_STARTED");
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.will.message.abort.deliver.will.non.expiring/client",
        "${mqtt}/session.will.message.abort.deliver.will.non.expiring/server"})
    public void shouldSendWillMessageOnAbortNonExpiring() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("WILL_STREAM_STARTED");
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.will.message.max.delay/client",
        "${mqtt}/session.will.message.max.delay/server"})
    public void shouldSendWillMessageWithMaximumDelay() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("WILL_STREAM_STARTED");
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.will.message.10k.abort.deliver.will/client",
        "${mqtt}/session.will.message.10k.abort.deliver.will/server"})
    public void shouldSendWillMessage10kOnAbort() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("WILL_STREAM_STARTED");
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.will.message.normal.disconnect/client",
        "${mqtt}/session.will.message.normal.disconnect/server"})
    public void shouldNotSendWillMessageOnNormalDisconnect() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("WILL_STREAM_STARTED");
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.will.message.abort.deliver.will.retain/client",
        "${mqtt}/session.will.message.abort.deliver.will.retain/server"})
    public void shouldSaveWillMessageAsRetain() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("WILL_STREAM_STARTED");
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.will.message.clean.start/client",
        "${mqtt}/session.will.message.clean.start/server"})
    public void shouldSendWillMessageOnClientReconnectCleanStart() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("WILL_STREAM_STARTED");
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.will.message/client",
        "${mqtt}/session.will.message/server"})
    public void shouldSaveWillMessage() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("WILL_STREAM_STARTED");
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.store.will.message.qos2.abort.deliver.will/client",
        "${mqtt}/session.store.will.message.qos2.abort.deliver.will/server"})
    public void shouldSessionStoreWillMessageQos2AbortDeliverWill() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("WILL_STREAM_STARTED");
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.will.message.qos2.abort.deliver.will/client",
        "${mqtt}/session.will.message.qos2.abort.deliver.will/server"})
    public void shouldSessionWillMessageQos2AbortDeliverWill() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("WILL_STREAM_STARTED");
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.will.message.qos2.abort.before.session.established/client",
        "${mqtt}/session.will.message.qos2.abort.before.session.established/server"})
    public void shouldSendWillSignalOnQos2AbortBeforeSessionEstablished() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("SESSION_META_STREAMS_OPENED");
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.qos2.end.before.session.established/client",
        "${mqtt}/session.qos2.end.before.session.established/server"})
    public void shouldEndQos2SessionBeforeSessionStreamEstablished() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("SESSION_META_STREAMS_OPENED");
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.qos2.meta.reset/client",
        "${mqtt}/session.qos2.meta.reset/server"})
    public void shouldResetMqttSessionWhenMetaStreamReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.qos2.init.producer.reset/client",
        "${mqtt}/session.qos2.init.producer.reset/server"})
    public void shouldResetMqttSessionWhenInitProducerStreamReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.qos2.offset.fetch.reset/client",
        "${mqtt}/session.qos2.offset.fetch.reset/server"})
    public void shouldResetMqttSessionWhenOffsetFetchStreamReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.qos2.offset.fetch.data.then.caught.up/client",
        "${mqtt}/session.qos2.offset.fetch.data.then.caught.up/server"})
    public void shouldSessionQos2OffsetFetchDataThenCaughtUp() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.will.message.clean.start.abort.zero.window/client",
        "${mqtt}/session.will.message.clean.start.abort.zero.window/server"})
    public void shouldSkipWillSignalOnAbortBeforeKafkaSessionStreamWindowGranted() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("SESSION_STREAM_WINDOW_GRANTED");
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.qos1/client",
        "${mqtt}/publish.qos1/server"})
    public void shouldPublishQoS1Message() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.qos2/client",
        "${mqtt}/publish.qos2/server"})
    public void shouldPublishQoS2Message() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.qos2.before.session.offsets/client",
        "${mqtt}/publish.qos2.before.session.offsets/server"})
    public void shouldPublishQoS2MessageBeforeSessionOffsets() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.qos2.pipelined/client",
        "${mqtt}/publish.qos2.pipelined/server"})
    public void shouldPublishQoS2MessagesPipelined() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.qos2.pipelined.abort/client",
        "${mqtt}/publish.qos2.pipelined.abort/server"})
    public void shouldPublishQoS2MessagesPipelinedAbort() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.qos2.pipelined.exhausted/client",
        "${mqtt}/publish.qos2.pipelined.exhausted/server"})
    public void shouldPublishQoS2MessagesPipelinedExhausted() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.qos2.in.flight/client",
        "${mqtt}/publish.qos2.in.flight/server"})
    public void shouldPublishQoS2InFlight() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.qos2.in.flight.reverse.release/client",
        "${mqtt}/publish.qos2.in.flight.reverse.release/server"})
    public void shouldPublishQoS2InFlightReverseRelease() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.qos2.session.store/client",
        "${mqtt}/publish.qos2.session.store/server"})
    public void shouldPublishQoS2SessionStore() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.qos2.session.store.before.offsets/client",
        "${mqtt}/publish.qos2.session.store.before.offsets/server"})
    public void shouldPublishQoS2SessionStoreBeforeOffsets() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.qos2.session.store.in.flight/client",
        "${mqtt}/publish.qos2.session.store.in.flight/server"})
    public void shouldPublishQoS2SessionStoreInFlight() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.qos2.session.store.persistent.resume/client",
        "${mqtt}/publish.qos2.session.store.persistent.resume/server"})
    public void shouldPublishQoS2SessionStorePersistentResume() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.qos2.session.store.persistent.no.inbound/client",
        "${mqtt}/publish.qos2.session.store.persistent.no.inbound/server"})
    public void shouldPublishQoS2SessionStorePersistentNoInbound() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.qos2.session.store.persistent.close/client",
        "${mqtt}/publish.qos2.session.store.persistent.close/server"})
    public void shouldPublishQoS2SessionStorePersistentClose() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.qos2.session.store.never.expires.close/client",
        "${mqtt}/publish.qos2.session.store.never.expires.close/server"})
    public void shouldPublishQoS2SessionStoreNeverExpiresClose() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.qos2.session.store.never.expires.resume/client",
        "${mqtt}/publish.qos2.session.store.never.expires.resume/server"})
    public void shouldPublishQoS2SessionStoreNeverExpiresResume() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.qos2.session.store.clean.start.close.unacknowledged/client",
        "${mqtt}/publish.qos2.session.store.clean.start.close.unacknowledged/server"})
    public void shouldPublishQoS2SessionStoreCleanStartCloseUnacknowledged() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.qos2.complete.after.acknowledge/client",
        "${mqtt}/publish.qos2.complete.after.acknowledge/server"})
    public void shouldPublishQoS2CompleteAfterAcknowledge() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.clean.start.discard.offsets/client",
        "${mqtt}/session.clean.start.discard.offsets/server"})
    public void shouldDiscardOffsetsOnCleanStart() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.qos2.no.prior.offsets/client",
        "${mqtt}/publish.qos2.no.prior.offsets/server"})
    public void shouldPublishQoS2MessageNoPriorOffsets() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.qos2.retained/client",
        "${mqtt}/publish.qos2.retained/server"})
    public void shouldPublishQoS2MessageRetained() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.qos2.retained.in.flight/client",
        "${mqtt}/publish.qos2.retained.in.flight/server"})
    public void shouldPublishQoS2RetainedInFlight() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.qos2.recovery/client",
        "${mqtt}/publish.qos2.recovery/server"})
    public void shouldPublishQoS2MessageDuringRecovery() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.qos2.recovery.in.flight/client",
        "${mqtt}/publish.qos2.recovery.in.flight/server"})
    public void shouldPublishQoS2RecoveryInFlight() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.qos2.abort.after.publish/client",
        "${mqtt}/publish.qos2.abort.after.publish/server"})
    public void shouldPublishQoS2MessageAbortAfterPublish() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.qos2.abort.after.pubrel/client",
        "${mqtt}/publish.qos2.abort.after.pubrel/server"})
    public void shouldPublishQoS2MessageAbortAfterPubrel() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.qos2.abort/client",
        "${mqtt}/publish.qos2.abort/server"})
    public void shouldSessionReceiveQos2Abort() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.qos2.non.clean.abort/client",
        "${mqtt}/publish.qos2.non.clean.abort/server"})
    public void shouldSessionReceiveQos2NonCleanAbort() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.qos2.offset.commit.abort.phase1/client",
        "${mqtt}/publish.qos2.offset.commit.abort.phase1/server"})
    public void shouldPublishReceiveQos2OffsetCommitSentAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.qos2.offset.commit.abort.phase2/client",
        "${mqtt}/publish.qos2.offset.commit.abort.phase2/server"})
    public void shouldSessionReceiveQos2OffsetCommitAbort() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.mixture.qos/client",
        "${mqtt}/publish.mixture.qos/server"})
    public void shouldSendMessageMixtureQos() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.reject.large.message/client",
        "${mqtt}/publish.reject.large.message/server"})
    public void shouldRejectLargeMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.receive.message.qos1/client",
        "${mqtt}/subscribe.receive.message.qos1/server"})
    public void shouldReceiveMessageQoS1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.mixed.qos.second.client.begin.keeps.pending.offset.commit/client",
        "${mqtt}/subscribe.mixed.qos.second.client.begin.keeps.pending.offset.commit/server"})
    public void shouldKeepPendingOffsetCommitWhenSecondClientBeginsSubscribe() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.qos2.second.client.restore.keeps.packet.id.offset/client",
        "${mqtt}/subscribe.qos2.second.client.restore.keeps.packet.id.offset/server"})
    public void shouldKeepPacketIdOffsetWhenSecondClientRestoresQoS2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.receive.message.qos1.multiple.clients/client",
        "${mqtt}/subscribe.receive.message.qos1.multiple.clients/server"})
    public void shouldReceiveMessageQoS1FromMultipleClients() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.receive.message.qos1.published.qos2/client",
        "${mqtt}/subscribe.receive.message.qos1.published.qos2/server"})
    public void shouldReceiveMessageQoS1PublishedAsQoS2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.receive.message.qos2/client",
        "${mqtt}/subscribe.receive.message.qos2/server"})
    public void shouldReceiveMessageQoS2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.receive.messages.qos2.sparse.offsets/client",
        "${mqtt}/subscribe.receive.messages.qos2.sparse.offsets/server"})
    public void shouldReceiveMessagesQoS2SparseOffsets() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.qos2.version1.offset.metadata/client",
        "${mqtt}/subscribe.qos2.version1.offset.metadata/server"})
    public void shouldReceiveMessageQoS2WithVersion1OffsetMetadata() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.receive.messages.mixture.qos/client",
        "${mqtt}/subscribe.receive.messages.mixture.qos/server"})
    public void shouldReceiveMessagesMixtureQos() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.reconnect.replay.qos1.unacked.message/client",
        "${mqtt}/subscribe.reconnect.replay.qos1.unacked.message/server"})
    public void shouldReplayUnackedQoS1MessageAtReconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.reconnect.replay.qos2.unreceived.message/client",
        "${mqtt}/subscribe.reconnect.replay.qos2.unreceived.message/server"})
    public void shouldReplayUnreceivedQoS2MessageAtReconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.reconnect.replay.qos2.incomplete.message/client",
        "${mqtt}/subscribe.reconnect.replay.qos2.incomplete.message/server"})
    public void shouldReplayIncompleteQoS2MessageAtReconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.replay.retained.message.qos1/client",
        "${mqtt}/subscribe.replay.retained.message.qos1/server"})
    public void shouldReplayRetainedQos1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.replay.retained.message.qos2/client",
        "${mqtt}/subscribe.replay.retained.message.qos2/server"})
    public void shouldReplayRetainedQos2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.receive.message.overlapping.wildcard.mixed.qos/client",
        "${mqtt}/subscribe.receive.message.overlapping.wildcard.mixed.qos/server"})
    public void shouldReceiveMessageOverlappingWildcardMixedQos() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.expire.message/client",
        "${mqtt}/subscribe.expire.message/server"})
    public void shouldExpireMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.message.expiry.interval.reduced/client",
        "${mqtt}/subscribe.message.expiry.interval.reduced/server"})
    public void shouldReceiveMessageWithReducedExpiryInterval() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.retained.message.expiry.interval.reduced/client",
        "${mqtt}/subscribe.retained.message.expiry.interval.reduced/server"})
    public void shouldReceiveRetainedMessageWithReducedExpiryInterval() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.retained.expire.message/client",
        "${mqtt}/subscribe.retained.expire.message/server"})
    public void shouldExpireRetainedMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.will.message.qos2.reply.window.before.begin/client",
        "${mqtt}/session.will.message.qos2.reply.window.before.begin/server"})
    public void shouldSendWillSignalOnQos2AbortWhenReplyWindowBeforeReplyBegin() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("SESSION_META_STREAMS_OPENED");
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.server.sent.abort.before.begin/client",
        "${mqtt}/publish.server.sent.abort.before.begin/server"})
    public void shouldReceiveServerSentAbortBeforeBeginPublish() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.server.sent.abort.before.begin/client",
        "${mqtt}/subscribe.server.sent.abort.before.begin/server"})
    public void shouldReceiveServerSentAbortBeforeBeginSubscribe() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.publish.qos.max.1/client",
        "${mqtt}/session.publish.qos.max.1/server"})
    public void shouldAdvertisePublishQosMax1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.capabilities.shared.available/client",
        "${mqtt}/session.capabilities.shared.available/server"})
    public void shouldSessionCapabilitiesSharedAvailable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.clean.start.qos.max.1/client",
        "${mqtt}/session.clean.start.qos.max.1/server"})
    public void shouldResetGroupOffsetsOnCleanStartPublishQosMax1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.capabilities.shared.unavailable.disabled/client",
        "${mqtt}/session.capabilities.shared.unavailable.disabled/server"})
    public void shouldSessionCapabilitiesSharedUnavailableDisabled() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.retained.topic.not.compacted/client",
        "${mqtt}/session.retained.topic.not.compacted/server"})
    public void shouldAdvertiseRetainedUnavailableWhenRetainedTopicNotCompacted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.publish.qos.max.0/client",
        "${mqtt}/session.publish.qos.max.0/server"})
    public void shouldAdvertisePublishQosMax0() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.reconnect.shared.subscription/client",
        "${mqtt}/session.reconnect.shared.subscription/server"})
    public void shouldSessionReconnectSharedSubscription() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.empty.retained.message/client",
        "${mqtt}/publish.empty.retained.message/server"})
    public void shouldSendEmptyRetainedMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.skip.removed.retained.message/client",
        "${mqtt}/subscribe.skip.removed.retained.message/server"})
    public void shouldSkipRemovedRetainedMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.and.non.shared.receive.messages/client",
        "${mqtt}/subscribe.shared.and.non.shared.receive.messages/server"})
    public void shouldSubscribeSharedAndNonSharedReceiveMessages() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.flush.unknown.packet.id/client",
        "${mqtt}/session.flush.unknown.packet.id/server"})
    public void shouldAnswerSessionFlushWithUnknownPacketId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.client.sent.abort/client",
        "${mqtt}/subscribe.shared.client.sent.abort/server"})
    public void shouldSubscribeSharedClientSentAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.qos1.server.sent.reset/client",
        "${mqtt}/publish.qos1.server.sent.reset/server"})
    public void shouldPublishQos1ServerSentReset() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.qos1.server.sent.reset.in.flight/client",
        "${mqtt}/publish.qos1.server.sent.reset.in.flight/server"})
    public void shouldPublishQos1ServerSentResetInFlight() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.qos1.retain.available.server.sent.reset.in.flight/client",
        "${mqtt}/publish.qos1.retain.available.server.sent.reset.in.flight/server"})
    public void shouldPublishQos1RetainAvailableServerSentResetInFlight() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.client.sent.reset/client",
        "${mqtt}/subscribe.shared.client.sent.reset/server"})
    public void shouldSubscribeSharedClientSentReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.qos1.server.sent.reset.after.acknowledged/client",
        "${mqtt}/publish.qos1.server.sent.reset.after.acknowledged/server"})
    public void shouldPublishQos1ServerSentResetAfterAcknowledged() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.qos1.reject.large.message/client",
        "${mqtt}/publish.qos1.reject.large.message/server"})
    public void shouldPublishQos1RejectLargeMessage() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/publish.qos1.retained.server.sent.reset/client",
        "${mqtt}/publish.qos1.retained.server.sent.reset/server"})
    public void shouldPublishQos1RetainedServerSentReset() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.subscribe.unmatched.filter/client",
        "${mqtt}/session.subscribe.unmatched.filter/server"})
    public void shouldSessionSubscribeUnmatchedFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.distinct.filters/client",
        "${mqtt}/subscribe.shared.distinct.filters/server"})
    public void shouldSubscribeSharedDistinctFilters() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.subscribe.unmatched.all/client",
        "${mqtt}/session.subscribe.unmatched.all/server"})
    public void shouldSessionSubscribeUnmatchedAll() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.distinct.share.names/client",
        "${mqtt}/subscribe.shared.distinct.share.names/server"})
    public void shouldSubscribeSharedDistinctShareNames() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.subscribe.unmatched.resume/client",
        "${mqtt}/session.subscribe.unmatched.resume/server"})
    public void shouldSessionSubscribeUnmatchedResume() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.one.message.subscription.id.127/client",
        "${mqtt}/subscribe.one.message.subscription.id.127/server"})
    public void shouldSubscribeOneMessageSubscriptionId127() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.receive.message.parent.level/client",
        "${mqtt}/subscribe.receive.message.parent.level/server"})
    public void shouldReceiveMessageAtParentLevel() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.expire.message/client",
        "${mqtt}/subscribe.shared.expire.message/server"})
    public void shouldSubscribeSharedExpireMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.message.expiry.interval.reduced/client",
        "${mqtt}/subscribe.shared.message.expiry.interval.reduced/server"})
    public void shouldSubscribeSharedMessageExpiryIntervalReduced() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.receive.message.parent.level.overlapping/client",
        "${mqtt}/subscribe.receive.message.parent.level.overlapping/server"})
    public void shouldReceiveMessageAtParentLevelWithOverlappingFilters() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.receive.message.qos0/client",
        "${mqtt}/subscribe.shared.receive.message.qos0/server"})
    public void shouldSubscribeSharedReceiveMessageQos0() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.retain.parent.level/client",
        "${mqtt}/subscribe.retain.parent.level/server"})
    public void shouldReceiveRetainedAtParentLevel() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.receive.message.qos1/client",
        "${mqtt}/subscribe.shared.receive.message.qos1/server"})
    public void shouldSubscribeSharedReceiveMessageQos1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.receive.message.qos1.granted.qos0/client",
        "${mqtt}/subscribe.shared.receive.message.qos1.granted.qos0/server"})
    public void shouldSubscribeSharedReceiveMessageQos1GrantedQos0() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.receive.message.qos1.published.qos2/client",
        "${mqtt}/subscribe.shared.receive.message.qos1.published.qos2/server"})
    public void shouldSubscribeSharedReceiveMessageQos1PublishedQos2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.receive.message.qos1.reject.with.reasoncode/client",
        "${mqtt}/subscribe.shared.receive.message.qos1.reject.with.reasoncode/server"})
    public void shouldSubscribeSharedReceiveMessageQos1RejectWithReasoncode() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.reject.filter.mismatch/client",
        "${mqtt}/subscribe.shared.reject.filter.mismatch/server"})
    public void shouldSubscribeSharedRejectFilterMismatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.reject.before.begin/client",
        "${mqtt}/subscribe.shared.reject.before.begin/server"})
    public void shouldSubscribeSharedRejectBeforeBegin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.reject.share.mismatch/client",
        "${mqtt}/subscribe.shared.reject.share.mismatch/server"})
    public void shouldSubscribeSharedRejectShareMismatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.reject.unreadable.metadata/client",
        "${mqtt}/subscribe.shared.reject.unreadable.metadata/server"})
    public void shouldSubscribeSharedRejectUnreadableMetadata() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.reject.unsupported.version/client",
        "${mqtt}/subscribe.shared.reject.unsupported.version/server"})
    public void shouldSubscribeSharedRejectUnsupportedVersion() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.resubscribe.change.qos/client",
        "${mqtt}/subscribe.shared.resubscribe.change.qos/server"})
    public void shouldSubscribeSharedResubscribeChangeQos() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.server.sent.abort/client",
        "${mqtt}/subscribe.shared.server.sent.abort/server"})
    public void shouldSubscribeSharedServerSentAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.expire.message.fragmented/client",
        "${mqtt}/subscribe.shared.expire.message.fragmented/server"})
    public void shouldSubscribeSharedExpireMessageFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.receive.message.qos0.fragmented/client",
        "${mqtt}/subscribe.shared.receive.message.qos0.fragmented/server"})
    public void shouldSubscribeSharedReceiveMessageQos0Fragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.receive.message.qos1.fragmented/client",
        "${mqtt}/subscribe.shared.receive.message.qos1.fragmented/server"})
    public void shouldSubscribeSharedReceiveMessageQos1Fragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.receive.message.qos1.reject.with.reasoncode.fragmented/client",
        "${mqtt}/subscribe.shared.receive.message.qos1.reject.with.reasoncode.fragmented/server"})
    public void shouldSubscribeSharedReceiveMessageQos1RejectWithReasoncodeFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.subscribe.shared.granted.qos2/client",
        "${mqtt}/session.subscribe.shared.granted.qos2/server"})
    public void shouldSessionSubscribeSharedGrantedQos2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.expire.message.qos2/client",
        "${mqtt}/subscribe.shared.expire.message.qos2/server"})
    public void shouldSubscribeSharedExpireMessageQos2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.receive.message.qos2/client",
        "${mqtt}/subscribe.shared.receive.message.qos2/server"})
    public void shouldSubscribeSharedReceiveMessageQos2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.receive.message.qos2.existing.tickets/client",
        "${mqtt}/subscribe.shared.receive.message.qos2.existing.tickets/server"})
    public void shouldSubscribeSharedReceiveMessageQos2ExistingTickets() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.recover.qos2.pubrel.pending/client",
        "${mqtt}/subscribe.shared.recover.qos2.pubrel.pending/server"})
    public void shouldSubscribeSharedRecoverQos2PubrelPending() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.unsubscribe.inflight.qos2.disconnect.recover.publish.sent/client",
        "${mqtt}/subscribe.shared.unsubscribe.inflight.qos2.disconnect.recover.publish.sent/server"})
    public void shouldSubscribeSharedUnsubscribeInflightQos2DisconnectRecoverPublishSent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.unsubscribe.inflight.qos2.disconnect.recover.pubrel.pending/client",
        "${mqtt}/subscribe.shared.unsubscribe.inflight.qos2.disconnect.recover.pubrel.pending/server"})
    public void shouldSubscribeSharedUnsubscribeInflightQos2DisconnectRecoverPubrelPending() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.unsubscribe.release.qos1/client",
        "${mqtt}/subscribe.shared.unsubscribe.release.qos1/server"})
    public void shouldSubscribeSharedUnsubscribeReleaseQos1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.unsubscribe.release.qos2/client",
        "${mqtt}/subscribe.shared.unsubscribe.release.qos2/server"})
    public void shouldSubscribeSharedUnsubscribeReleaseQos2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.unsubscribe.inflight.then.release/client",
        "${mqtt}/subscribe.shared.unsubscribe.inflight.then.release/server"})
    public void shouldSubscribeSharedUnsubscribeInflightThenRelease() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.recover.qos2.publish.sent/client",
        "${mqtt}/subscribe.shared.recover.qos2.publish.sent/server"})
    public void shouldSubscribeSharedRecoverQos2PublishSent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.recover.qos2.publish.sent.fragmented/client",
        "${mqtt}/subscribe.shared.recover.qos2.publish.sent.fragmented/server"})
    public void shouldSubscribeSharedRecoverQos2PublishSentFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.recover.qos2.publish.sent.offset.mismatch/client",
        "${mqtt}/subscribe.shared.recover.qos2.publish.sent.offset.mismatch/server"})
    public void shouldSubscribeSharedRecoverQos2PublishSentOffsetMismatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.recover.qos2.publish.sent.expired/client",
        "${mqtt}/subscribe.shared.recover.qos2.publish.sent.expired/server"})
    public void shouldSubscribeSharedRecoverQos2PublishSentExpired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.recover.qos2.publish.sent.missing/client",
        "${mqtt}/subscribe.shared.recover.qos2.publish.sent.missing/server"})
    public void shouldSubscribeSharedRecoverQos2PublishSentMissing() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.recover.qos2.publish.sent.beyond.end/client",
        "${mqtt}/subscribe.shared.recover.qos2.publish.sent.beyond.end/server"})
    public void shouldSubscribeSharedRecoverQos2PublishSentBeyondEnd() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.recover.qos2.then.receive.message/client",
        "${mqtt}/subscribe.shared.recover.qos2.then.receive.message/server"})
    public void shouldSubscribeSharedRecoverQos2ThenReceiveMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.receive.message.qos2.fragmented/client",
        "${mqtt}/subscribe.shared.receive.message.qos2.fragmented/server"})
    public void shouldSubscribeSharedReceiveMessageQos2Fragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.receive.message.qos2.reject.with.reasoncode/client",
        "${mqtt}/subscribe.shared.receive.message.qos2.reject.with.reasoncode/server"})
    public void shouldSubscribeSharedReceiveMessageQos2RejectWithReasoncode() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.receive.messages.qos2/client",
        "${mqtt}/subscribe.shared.receive.messages.qos2/server"})
    public void shouldSubscribeSharedReceiveMessagesQos2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.unsubscribe.shared/client",
        "${mqtt}/session.unsubscribe.shared/server"})
    public void shouldUnsubscribeSharedAndLeave() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.resubscribe.shared/client",
        "${mqtt}/session.resubscribe.shared/server"})
    public void shouldResubscribeSharedAfterLeaving() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.restore.membership/client",
        "${mqtt}/subscribe.shared.restore.membership/server"})
    public void shouldSubscribeSharedRestoreMembership() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.shared.join.new.life/client",
        "${mqtt}/subscribe.shared.join.new.life/server"})
    public void shouldSubscribeSharedJoinNewLife() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.close.end.expiry.zero/client",
        "${mqtt}/session.close.end.expiry.zero/server"})
    public void shouldCloseSessionWithEndExpiryZero() throws Exception
    {
        k3po.finish();
    }
}
