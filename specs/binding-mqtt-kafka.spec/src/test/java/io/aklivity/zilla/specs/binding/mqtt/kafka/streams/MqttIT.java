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
        "${mqtt}/publish.server.sent.flush/client",
        "${mqtt}/publish.server.sent.flush/server"})
    public void shouldPublishReceiveServerSentFlush() throws Exception
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
        "${mqtt}/subscribe.server.sent.flush/client",
        "${mqtt}/subscribe.server.sent.flush/server"})
    public void shouldSubscribeReceiveServerSentFlush() throws Exception
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
        "${mqtt}/session.connect.override.max.session.expiry/client",
        "${mqtt}/session.connect.override.max.session.expiry/server"})
    public void shouldConnectServerOverridesSessionExpiryTooBig() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.connect.override.min.session.expiry/client",
        "${mqtt}/session.connect.override.min.session.expiry/server"})
    public void shouldConnectServerOverridesSessionExpiryTooSmall() throws Exception
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
        "${mqtt}/session.client.takeover/client",
        "${mqtt}/session.client.takeover/server"})
    public void shouldTakeOverSession() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/session.exists.clean.start/client",
        "${mqtt}/session.exists.clean.start/server"})
    public void shouldRemoveSessionAtCleanStart() throws Exception
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
        "${mqtt}/session.subscribe.via.session.state/client",
        "${mqtt}/session.subscribe.via.session.state/server"})
    public void shouldReceiveMessageSubscribedViaSessionState() throws Exception
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
        "${mqtt}/session.will.message.takeover.deliver.will/client",
        "${mqtt}/session.will.message.takeover.deliver.will/server"})
    public void shouldDeliverWillMessageOnSessionTakeover() throws Exception
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
        "${mqtt}/publish.mixture.qos/client",
        "${mqtt}/publish.mixture.qos/server"})
    public void shouldSendMessageMixtureQos() throws Exception
    {
        k3po.start();
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
}
