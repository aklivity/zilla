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
package io.aklivity.zilla.specs.binding.mqtt.streams.application;

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
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/mqtt/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

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
        "${app}/subscribe.qos0.publish.retained.no.replay/client",
        "${app}/subscribe.qos0.publish.retained.no.replay/server"})
    public void shouldNotReplayRetained() throws Exception
    {
        k3po.finish();
    }

}
