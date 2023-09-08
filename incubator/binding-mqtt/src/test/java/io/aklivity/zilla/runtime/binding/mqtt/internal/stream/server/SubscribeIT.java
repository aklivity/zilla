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
package io.aklivity.zilla.runtime.binding.mqtt.internal.stream.server;

import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfiguration.PUBLISH_TIMEOUT;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfigurationTest.MAXIMUM_QOS_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfigurationTest.NO_LOCAL_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfigurationTest.SHARED_SUBSCRIPTION_AVAILABLE_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfigurationTest.SUBSCRIPTION_IDENTIFIERS_AVAILABLE_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfigurationTest.WILDCARD_SUBSCRIPTION_AVAILABLE_NAME;
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
import io.aklivity.zilla.runtime.engine.test.annotation.Configure;

public class SubscribeIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/mqtt/streams/network")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/mqtt/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(8192)
        .configure(PUBLISH_TIMEOUT, 1L)
        .configure(ENGINE_DRAIN_ON_CLOSE, false)
        .configurationRoot("io/aklivity/zilla/specs/binding/mqtt/config")
        .external("app0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.one.message/client",
        "${app}/subscribe.one.message/server"})
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    public void shouldReceiveOneMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.one.message.receive.response.topic.and.correlation.data/client",
        "${app}/subscribe.one.message.receive.response.topic.and.correlation.data/server"})
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    public void shouldReceiveCorrelationData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.one.message.user.properties.unaltered/client",
        "${app}/subscribe.one.message.user.properties.unaltered/server"})
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    public void shouldReceiveOneMessageWithUserPropertiesUnaltered() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.one.message.with.invalid.subscription.id/client",
        "${app}/session.connect/server"})
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    public void shouldReceiveOneMessageWithInvalidSubscriptionId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.topic.filter.single.exact/client",
        "${app}/subscribe.topic.filter.single.exact/server"})
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    public void shouldFilterExact() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.topic.filter.multi.level.wildcard/client",
        "${app}/subscribe.topic.filter.multi.level.wildcard/server"})
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    public void shouldFilterMultiLevelWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.topic.filter.single.and.multi.level.wildcard/client",
        "${app}/subscribe.topic.filter.single.and.multi.level.wildcard/server"})
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    public void shouldFilterSingleAndMultiLevelWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.topic.filter.single.level.wildcard/client",
        "${app}/subscribe.topic.filter.single.level.wildcard/server"})
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    public void shouldFilterSingleLevelWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.topic.filter.two.single.level.wildcard/client",
        "${app}/subscribe.topic.filter.two.single.level.wildcard/server"})
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    public void shouldFilterTwoSingleLevelWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.topic.filters.aggregated.both.exact/client",
        "${app}/subscribe.topic.filters.aggregated.both.exact/server"})
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    public void shouldFilterAggregatedBothExact() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.topic.filters.isolated.both.exact/client",
        "${app}/subscribe.topic.filters.isolated.both.exact/server"})
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    public void shouldFilterIsolatedBothExact() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.topic.filters.isolated.both.wildcard/client",
        "${app}/subscribe.topic.filters.isolated.both.wildcard/server"})
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    public void shouldFilterIsolatedBothWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.topic.filters.aggregated.exact.and.wildcard/client",
        "${app}/subscribe.topic.filters.aggregated.exact.and.wildcard/server"})
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    public void shouldFilterAggregatedExactAndWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.topic.filters.disjoint.wildcards/client",
        "${app}/subscribe.topic.filters.disjoint.wildcards/server"})
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    public void shouldFilterDisjointWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.topic.filters.isolated.exact.and.wildcard/client",
        "${app}/subscribe.topic.filters.isolated.exact.and.wildcard/server"})
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    public void shouldFilterIsolatedExactAndWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.topic.filters.overlapping.wildcards/client",
        "${app}/subscribe.topic.filters.overlapping.wildcards/server"})
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    public void shouldFilterOverlappingWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.get.retained.as.published/client",
        "${app}/subscribe.get.retained.as.published/server"})
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    public void shouldReceiveRetainedAsPublished() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.qos0.publish.retained.no.replay/client",
        "${app}/subscribe.qos0.publish.retained.no.replay/server"})
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    public void shouldNotReplayRetained() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.qos0.replay.retained.no.packet.id/client",
        "${app}/subscribe.qos0.replay.retained.no.packet.id/server"})
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    public void shouldReceiveAndReplayRetainedQos0() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.reject.no.local/client",
        "${app}/session.connect/server"})
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = NO_LOCAL_NAME, value = "false")
    public void shouldRejectNoLocal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.receive.message/client",
        "${app}/subscribe.receive.message/server"})
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    public void shouldReceiveOneMessageAfterPublish() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.receive.message.overlapping.wildcard/client",
        "${app}/subscribe.receive.message.overlapping.wildcard/server"})
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    public void shouldReceiveMessageOverlappingWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.receive.message.wildcard/client",
        "${app}/subscribe.receive.message.wildcard/server"})
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    public void shouldReceiveOneMessageWithPatternTopic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.retain.as.published/client",
        "${app}/subscribe.retain.as.published/server"})
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    public void shouldReceiveRetainAsPublished() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.publish.no.local/client",
        "${app}/subscribe.publish.no.local/server"})
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    public void shouldNotReceivePublishLocal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.invalid.fixed.header.flags/client",
        "${app}/session.connect/server"})
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    public void shouldRejectMalformedPacket() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.invalid.topic.filter/client",
        "${app}/session.connect/server"})
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    public void shouldRejectInvalidTopicFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.reject.wildcard.subscriptions.not.supported/client",
        "${app}/session.connect/server"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "false")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    public void shouldRejectWildcardSubscriptionsNotSupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.reject.subscription.ids.not.supported/client",
        "${app}/session.connect/server"})
    @Configure(name = SUBSCRIPTION_IDENTIFIERS_AVAILABLE_NAME, value = "false")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    public void shouldRejectSubscriptionIdentifiersNotSupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.reject.shared.subscriptions.not.supported/client",
        "${app}/session.connect/server"})
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "false")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    public void shouldRejectSharedSubscriptionsNotSupported() throws Exception
    {
        k3po.finish();
    }
}
