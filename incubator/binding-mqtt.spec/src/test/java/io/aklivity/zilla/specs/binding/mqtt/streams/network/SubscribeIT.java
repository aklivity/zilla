/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.specs.binding.mqtt.streams.network;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

public class SubscribeIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/mqtt/streams/network");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/subscribe.one.message/client",
        "${net}/subscribe.one.message/server"})
    public void shouldSubscribeOneMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.one.message.user.properties.unaltered/client",
        "${net}/subscribe.one.message.user.properties.unaltered/server"})
    public void shouldSubscribeOneMessageWithUserPropertiesUnaltered() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.receive.message.wildcard/client",
        "${net}/subscribe.receive.message.wildcard/server"})
    public void shouldSubscribeOneMessageWithPatternTopic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.one.message.then.publish.message/client",
        "${net}/subscribe.one.message.then.publish.message/server"})
    public void shouldSubscriberOneMessageThenPublishMessage() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-2.2.1-3]
    @Test
    @Specification({
        "${net}/subscribe.reject.missing.packet.id/client",
        "${net}/subscribe.reject.missing.packet.id/server"})
    public void shouldRejectWithoutPacketId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.reject.missing.topic.filters/client",
        "${net}/subscribe.reject.missing.topic.filters/server"})
    public void shouldRejectSubscribeWithMissingTopicFilters() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.invalid.topic.filter/client",
        "${net}/subscribe.invalid.topic.filter/server"})
    public void shouldRejectSubscribeWithInvalidTopicFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.topic.filter.single.exact/client",
        "${net}/subscribe.topic.filter.single.exact/server"})
    public void shouldSubscribeToExactTopicFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.topic.filter.multi.level.wildcard/client",
        "${net}/subscribe.topic.filter.multi.level.wildcard/server"})
    public void shouldSubscribeToWildcardTopicFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.topic.filter.single.level.wildcard/client",
        "${net}/subscribe.topic.filter.single.level.wildcard/server"})
    public void shouldSubscribeToSingleLevelWildcardTopicFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.topic.filter.two.single.level.wildcard/client",
        "${net}/subscribe.topic.filter.two.single.level.wildcard/server"})
    public void shouldSubscribeToTwoSingleLevelWildcardTopicFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.topic.filter.single.and.multi.level.wildcard/client",
        "${net}/subscribe.topic.filter.single.and.multi.level.wildcard/server"})
    public void shouldSubscribeToSingleAndMultiLevelWildcardTopicFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.topic.filters.disjoint.wildcards/client",
        "${net}/subscribe.topic.filters.disjoint.wildcards/server"})
    public void shouldSubscribeToDisjointWildcardTopicFilters() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.topic.filters.overlapping.wildcards/client",
        "${net}/subscribe.topic.filters.overlapping.wildcards/server"})
    public void shouldSubscribeToOverlappingWildcardTopicFilters() throws Exception
    {
        k3po.finish();
    }


    @Test
    @Specification({
        "${net}/subscribe.reject.topic.filter.invalid.wildcard/client",
        "${net}/subscribe.reject.topic.filter.invalid.wildcard/server"})
    public void shouldRejectSubscribeTopicFilterInvalidWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.topic.filters.aggregated.both.exact/client",
        "${net}/subscribe.topic.filters.aggregated.both.exact/server"})
    public void shouldSubscribeToAggregatedTopicFiltersBothExact() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.topic.filters.isolated.both.exact/client",
        "${net}/subscribe.topic.filters.isolated.both.exact/server"})
    public void shouldSubscribeToIsolatedTopicFiltersBothExact() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.topic.filters.isolated.both.wildcard/client",
        "${net}/subscribe.topic.filters.isolated.both.wildcard/server"})
    public void shouldSubscribeToIsolatedTopicFiltersBothWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.topic.filters.aggregated.exact.and.wildcard/client",
        "${net}/subscribe.topic.filters.aggregated.exact.and.wildcard/server"})
    public void shouldSubscribeToAggregatedExactAndWildcardTopicFilters() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.topic.filters.isolated.exact.and.wildcard/client",
        "${net}/subscribe.topic.filters.isolated.exact.and.wildcard/server"})
    public void shouldSubscribeToIsolatedExactAndWildcardTopicFilters() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.invalid.fixed.header.flags/client",
        "${net}/subscribe.invalid.fixed.header.flags/server"})
    public void shouldRejectMalformedSubscribePacket() throws Exception
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
    public void shouldReceiveCorrelationDataAfterSendingSubscribe() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.one.message.with.invalid.subscription.id/client",
        "${net}/subscribe.one.message.with.invalid.subscription.id/server"})
    public void shouldSubscribeOneMessageWithInvalidSubscriptionId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.one.message.get.local/client",
        "${net}/subscribe.one.message.get.local/server"})
    public void shouldSubscribeGetLocalPublishedMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.get.retained.as.published/client",
        "${net}/subscribe.get.retained.as.published/server"})
    public void shouldSubscribeGetRetainedMessageAsPublished() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.8.3-5]
    @Test
    @Specification({
        "${net}/subscribe.reject.malformed.reserved.subscription.options/client",
        "${net}/subscribe.reject.malformed.reserved.subscription.options/server"})
    public void shouldRejectSubscribeMalformedReservedSubscriptionOptions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.reject.wildcard.subscriptions.not.supported/client",
        "${net}/subscribe.reject.wildcard.subscriptions.not.supported/server"})
    public void shouldRejectSubscribeWithWildcardSubscriptionsNotSupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.reject.subscription.id.not.supported/client",
        "${net}/subscribe.reject.subscription.id.not.supported/server"})
    public void shouldConnectWithSubscriptionIdentifiersNotSupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.reject.shared.subscription.not.supported/client",
        "${net}/subscribe.reject.shared.subscription.not.supported/server"})
    public void shouldConnectWithSharedSubscriptionsNotSupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.qos0.replay.retained.no.packet.id/client",
        "${net}/subscribe.qos0.replay.retained.no.packet.id/server"})
    public void shouldSubscribeAndReplayRetainedQos0() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.qos0.publish.retained.no.replay/client",
        "${net}/subscribe.qos0.publish.retained.no.replay/server"})
    public void shouldSubscribeNoReplayRetained() throws Exception
    {
        k3po.finish();
    }
}
