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
package io.aklivity.zilla.specs.binding.mqtt.streams.network.v4;

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
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/mqtt/streams/network/v4");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

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
        "${net}/subscribe.receive.message.wildcard/client",
        "${net}/subscribe.receive.message.wildcard/server"})
    public void shouldReceiveOneMessageWithPatternTopic() throws Exception
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
        "${net}/subscribe.receive.message/client",
        "${net}/subscribe.receive.message/server"})
    public void shouldReceiveOneMessageAfterPublish() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.8.3-5]
    @Test
    @Specification({
        "${net}/subscribe.reject.malformed.subscription.options/client",
        "${net}/subscribe.reject.malformed.subscription.options/server"})
    public void shouldRejectMalformedSubscriptionOptions() throws Exception
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
        "${net}/subscribe.unroutable/client",
        "${net}/subscribe.unroutable/server"})
    public void shouldRejectUnroutable() throws Exception
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
        "${net}/subscribe.topic.guarded.identity.param/client",
        "${net}/subscribe.topic.guarded.identity.param/server"})
    public void shouldFilterGuardedIdentityParam() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.invalid.topic.guarded.identity.param/client",
        "${net}/subscribe.invalid.topic.guarded.identity.param/server"})
    public void shouldRejectInvalidGuardedIdentityParam() throws Exception
    {
        k3po.finish();
    }
}
