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
package io.aklivity.zilla.specs.binding.mqtt.kafka.streams;

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
        "${kafka}/publish.one.message.changed.topic.name/client",
        "${kafka}/publish.one.message.changed.topic.name/server"})
    public void shouldSendOneMessageWithChangedTopicName() throws Exception
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
        "${kafka}/session.abort.reconnect.non.clean.start/client",
        "${kafka}/session.abort.reconnect.non.clean.start/server"})
    public void shouldReconnectNonCleanStart() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.client.takeover/client",
        "${kafka}/session.client.takeover/server"})
    public void shouldTakeOverSession() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.exists.clean.start/client",
        "${kafka}/session.exists.clean.start/server"})
    public void shouldRemoveSessionAtCleanStart() throws Exception
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
        "${kafka}/session.subscribe.via.session.state/client",
        "${kafka}/session.subscribe.via.session.state/server"})
    public void shouldReceiveMessageSubscribedViaSessionState() throws Exception
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
        "${kafka}/session.group.server.sent.reset/client",
        "${kafka}/session.group.server.sent.reset/server"})
    public void shouldGroupStreamReceiveServerSentReset() throws Exception
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
        "${kafka}/session.will.message.takeover.deliver.will/client",
        "${kafka}/session.will.message.takeover.deliver.will/server"})
    public void shouldSendWillMessageOnSessionTakeover() throws Exception
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
        "${kafka}/session.connect.override.max.session.expiry/client",
        "${kafka}/session.connect.override.max.session.expiry/server"})
    public void shouldConnectServerOverridesSessionExpiryTooBig() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.connect.override.min.session.expiry/client",
        "${kafka}/session.connect.override.min.session.expiry/server"})
    public void shouldConnectServerOverridesSessionExpiryTooSmall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/session.redirect/client",
        "${kafka}/session.redirect/server"})
    public void shouldRedirect() throws Exception
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
        "${kafka}/publish.qos2.retained/client",
        "${kafka}/publish.qos2.retained/server"})
    public void shouldPublishQoS2MessageRetained() throws Exception
    {
        k3po.start();
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/publish.qos2.recover/client",
        "${kafka}/publish.qos2.recover/server"})
    public void shouldPublishQoS2MessageRecover() throws Exception
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
        "${kafka}/publish.qos2.offset.commit.abort/client",
        "${kafka}/publish.qos2.offset.commit.abort/server"})
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
}
