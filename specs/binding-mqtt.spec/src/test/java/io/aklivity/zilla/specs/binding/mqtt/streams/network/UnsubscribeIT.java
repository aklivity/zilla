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

public class UnsubscribeIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/mqtt/streams/network/5.0");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    // [MQTT-2.2.1-3]
    @Test
    @Specification({
        "${net}/unsubscribe.reject.missing.packet.id/client",
        "${net}/unsubscribe.reject.missing.packet.id/server"})
    public void shouldRejectWithoutPacketId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/unsubscribe.after.subscribe/client",
        "${net}/unsubscribe.after.subscribe/server"})
    public void shouldAcknowledge() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.10.4-5]
    @Test
    @Specification({
        "${net}/unsubscribe.no.matching.subscription/client",
        "${net}/unsubscribe.no.matching.subscription/server"})
    public void shouldAcknowledgeNoMatchingSubscription() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/unsubscribe.aggregated.topic.filters.both.exact/client",
        "${net}/unsubscribe.aggregated.topic.filters.both.exact/server"})
    public void shouldAcknowledgeAggregatedTopicFiltersBothExact() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/unsubscribe.topic.filter.single/client",
        "${net}/unsubscribe.topic.filter.single/server"})
    public void shouldAcknowledgeSingleTopicFilters() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.10.1-1]
    @Test
    @Specification({
        "${net}/unsubscribe.reject.invalid.fixed.header.flags/client",
        "${net}/unsubscribe.reject.invalid.fixed.header.flags/server"})
    public void shouldRejectMalformedPacket() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.10.3-2]
    @Test
    @Specification({
        "${net}/unsubscribe.reject.no.topic.filter/client",
        "${net}/unsubscribe.reject.no.topic.filter/server"})
    public void shouldRejectNoTopicFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/unsubscribe.publish.unfragmented/client",
        "${net}/unsubscribe.publish.unfragmented/server"})
    public void shouldAcknowledgeAndPublishUnfragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/unsubscribe.topic.filters.non.successful/client",
        "${net}/unsubscribe.topic.filters.non.successful/server"})
    public void shouldAcknowledgeNonSuccessful() throws Exception
    {
        k3po.finish();
    }
}
