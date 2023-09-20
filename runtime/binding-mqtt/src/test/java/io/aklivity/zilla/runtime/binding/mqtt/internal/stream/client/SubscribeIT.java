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
package io.aklivity.zilla.runtime.binding.mqtt.internal.stream.client;

import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfiguration.PUBLISH_TIMEOUT;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DRAIN_ON_CLOSE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

public class SubscribeIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/mqtt/streams/network")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/mqtt/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(8192)
        .configure(PUBLISH_TIMEOUT, 1L)
        .configure(ENGINE_DRAIN_ON_CLOSE, false)
        .configurationRoot("io/aklivity/zilla/specs/binding/mqtt/config")
        .external("net0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${net}/session.subscribe/server",
        "${app}/session.subscribe/client"})
    public void shouldSubscribe() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${net}/subscribe.one.message/server",
        "${app}/subscribe.one.message/client"})
    public void shouldReceiveOneMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${net}/subscribe.retain.as.published/server",
        "${app}/subscribe.retain.as.published/client"})
    public void shouldReceiveRetainAsPublished() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${net}/subscribe.one.message.receive.response.topic.and.correlation.data/server",
        "${app}/subscribe.one.message.receive.response.topic.and.correlation.data/client"})
    public void shouldReceiveCorrelationData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${net}/subscribe.one.message.user.properties.unaltered/server",
        "${app}/subscribe.one.message.user.properties.unaltered/client"})
    public void shouldReceiveOneMessageWithUserPropertiesUnaltered() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${net}/subscribe.publish.no.local/server",
        "${app}/subscribe.publish.no.local/client"})
    public void shouldNotReceivePublishLocal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${net}/subscribe.qos0.publish.retained.no.replay/server",
        "${app}/subscribe.qos0.publish.retained.no.replay/client"})
    public void shouldNotReplayRetained() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${net}/subscribe.qos0.replay.retained.no.packet.id/server",
        "${app}/subscribe.qos0.replay.retained.no.packet.id/client"})
    public void shouldReceiveAndReplayRetainedQos0() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${net}/subscribe.receive.message/server",
        "${app}/subscribe.receive.message/client"})
    public void shouldReceiveOneMessageAfterPublish() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${net}/subscribe.receive.messages.topic.alias.repeated/server",
        "${app}/subscribe.receive.messages.topic.alias.repeated/client"})
    public void shouldReceiveMessagesTopicAliasRepeated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${net}/subscribe.receive.message.overlapping.wildcard/server",
        "${app}/subscribe.receive.message.overlapping.wildcard/client"})
    public void shouldReceiveMessageOverlappingWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${net}/subscribe.receive.message.wildcard/server",
        "${app}/subscribe.receive.message.wildcard/client"})
    public void shouldReceiveOneMessageWithPatternTopic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${net}/subscribe.topic.filter.multi.level.wildcard/server",
        "${app}/subscribe.topic.filter.multi.level.wildcard/client"})
    public void shouldFilterMultiLevelWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${net}/subscribe.topic.filter.single.and.multi.level.wildcard/server",
        "${app}/subscribe.topic.filter.single.and.multi.level.wildcard/client"})
    public void shouldFilterSingleAndMultiLevelWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${net}/subscribe.topic.filter.single.exact/server",
        "${app}/subscribe.topic.filter.single.exact/client"})
    public void shouldFilterExact() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${net}/subscribe.topic.filter.two.single.level.wildcard/server",
        "${app}/subscribe.topic.filter.two.single.level.wildcard/client"})
    public void shouldFilterTwoSingleLevelWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${net}/subscribe.topic.filters.aggregated.both.exact/server",
        "${app}/subscribe.topic.filters.aggregated.both.exact/client"})
    public void shouldFilterAggregatedBothExact() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${net}/subscribe.topic.filters.aggregated.exact.and.wildcard/server",
        "${app}/subscribe.topic.filters.aggregated.exact.and.wildcard/client"})
    public void shouldFilterAggregatedExactAndWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${net}/subscribe.topic.filter.single.level.wildcard/server",
        "${app}/subscribe.topic.filter.single.level.wildcard/client"})
    public void shouldFilterSingleLevelWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${net}/subscribe.topic.filters.disjoint.wildcards/server",
        "${app}/subscribe.topic.filters.disjoint.wildcards/client"})
    public void shouldFilterDisjointWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${net}/subscribe.topic.filters.isolated.both.exact/server",
        "${app}/subscribe.topic.filters.isolated.both.exact/client"})
    public void shouldFilterIsolatedBothExact() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${net}/subscribe.topic.filters.isolated.both.wildcard/server",
        "${app}/subscribe.topic.filters.isolated.both.wildcard/client"})
    public void shouldFilterIsolatedBothWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${net}/subscribe.topic.filters.isolated.exact.and.wildcard/server",
        "${app}/subscribe.topic.filters.isolated.exact.and.wildcard/client"})
    public void shouldFilterIsolatedExactAndWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${net}/subscribe.topic.filters.overlapping.wildcards/server",
        "${app}/subscribe.topic.filters.overlapping.wildcards/client"})
    public void shouldFilterOverlappingWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${net}/subscribe.topic.filters.non.successful/server",
        "${app}/subscribe.topic.filters.non.successful/client"})
    public void shouldFilterNonSuccessful() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${net}/subscribe.reconnect.publish.no.subscription/server",
        "${app}/subscribe.reconnect.publish.no.subscription/client"})
    public void shouldReceiveReconnectNoSubscription() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/subscribe.publish.invalid.affinity/client"})
    public void shouldAbortSubscribeAndPublishInvalidAffinity() throws Exception
    {
        k3po.finish();
    }
}
