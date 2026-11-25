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
package io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream;

import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfigurationTest.BOOTSTRAP_STREAM_RECONNECT_DELAY_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfigurationTest.SESSION_STATE_STORE_ENABLED_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfigurationTest.WILL_AVAILABLE_NAME;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_BUFFER_SLOT_CAPACITY;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DRAIN_ON_CLOSE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;
import io.aklivity.zilla.runtime.engine.test.annotation.Configure;

public class MqttKafkaSubscribeProxyIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("mqtt", "io/aklivity/zilla/specs/binding/mqtt/kafka/streams/mqtt")
        .addScriptRoot("kafka", "io/aklivity/zilla/specs/binding/mqtt/kafka/streams/kafka");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configure(ENGINE_BUFFER_SLOT_CAPACITY, 8192)
        .configure(ENGINE_DRAIN_ON_CLOSE, false)
        .configurationRoot("io/aklivity/zilla/specs/binding/mqtt/kafka/config")
        .external("kafka0")
        .aroundStart(k3po::deferStartable)
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.client.sent.abort/client",
        "${kafka}/subscribe.client.sent.abort/server"})
    public void shouldReceiveClientSentAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.client.sent.data/client",
        "${kafka}/subscribe.client.sent.data/server"})
    public void shouldAbortWhenClientSentData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.client.sent.reset/client",
        "${kafka}/subscribe.client.sent.reset/server"})
    public void shouldReceiveClientSentReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.server.sent.abort/client",
        "${kafka}/subscribe.server.sent.abort/server"})
    public void shouldReceiveServerSentAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.server.sent.flush/client",
        "${kafka}/subscribe.server.sent.flush/server"})
    public void shouldReceiveServerSentFlush() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.server.sent.reset/client",
        "${kafka}/subscribe.server.sent.reset/server"})
    public void shouldReceiveServerSentReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.retained.server.sent.abort/client",
        "${kafka}/subscribe.retained.server.sent.abort/server"})
    public void shouldReceiveServerSentRetainedAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.retained.server.sent.reset/client",
        "${kafka}/subscribe.retained.server.sent.reset/server"})
    public void shouldReceiveServerSentRetainedReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.one.message/client",
        "${kafka}/subscribe.one.message/server"})
    public void shouldReceiveOneMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.publish.retained.retain.as.published/client",
        "${kafka}/subscribe.publish.retained.retain.as.published/server"})
    public void shouldSubscribePublishRetainedRetainAsPublished() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.publish.retained.no.retain.as.published/client",
        "${kafka}/subscribe.publish.retained.no.retain.as.published/server"})
    public void shouldSubscribePublishRetainedNoRetainAsPublished() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.publish.not.retained.retain.as.published/client",
        "${kafka}/subscribe.publish.not.retained.retain.as.published/server"})
    public void shouldSubscribePublishNotRetainedRetainAsPublished() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.publish.retained.retain.as.published.multiple.filters/client",
        "${kafka}/subscribe.publish.retained.retain.as.published.multiple.filters/server"})
    public void shouldSubscribePublishRetainedRetainAsPublishedMultipleFilters() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.retain.shared.subscription.id/client",
        "${kafka}/subscribe.retain.shared.subscription.id/server"})
    public void shouldReceiveRetainedWithSharedSubscriptionId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.filter.change.retain.reordered/client",
        "${kafka}/subscribe.filter.change.retain.reordered/server"})
    public void shouldReceiveRetainedAfterFilterChangeReordered() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.one.message/client",
        "${kafka}/subscribe.one.message.fragmented/server"})
    public void shouldReceiveOneMessageFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.options.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.one.message/client",
        "${kafka}/subscribe.one.message.changed.topic.name/server"})
    public void shouldReceiveOneMessageWithChangedTopicName() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.when.subscribe.topic.with.messages.params.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.one.message/client",
        "${kafka}/subscribe.one.message.resolve.topic.params/server"})
    public void shouldReceiveOneMessageWithResolvingParams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.when.subscribe.topic.with.messages.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.topic.space/client",
        "${kafka}/subscribe.topic.space/server"})
    public void shouldFilterTopicSpace() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.when.client.topic.space.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.client.topic.space/client",
        "${kafka}/subscribe.client.topic.space/server"})
    public void shouldFilterClientTopicSpace() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.when.client.topic.space.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = BOOTSTRAP_STREAM_RECONNECT_DELAY_NAME, value = "1")
    @Specification({
        "${kafka}/subscribe.bootstrap.stream.end.reconnect/server"})
    public void shouldReconnectBootstrapStreamOnKafkaEnd() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.when.client.topic.space.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = BOOTSTRAP_STREAM_RECONNECT_DELAY_NAME, value = "1")
    @Specification({
        "${kafka}/subscribe.bootstrap.stream.abort.reconnect/server"})
    public void shouldReconnectBootstrapStreamOnKafkaAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.when.client.topic.space.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = BOOTSTRAP_STREAM_RECONNECT_DELAY_NAME, value = "1")
    @Specification({
        "${kafka}/subscribe.bootstrap.stream.reset.reconnect/server"})
    public void shouldReconnectBootstrapStreamOnKafkaReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.multiple.message/client",
        "${kafka}/subscribe.multiple.message/server"})
    public void shouldReceiveMultipleMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.retain.as.published/client",
        "${kafka}/subscribe.retain/server"})
    public void shouldReceiveRetainAsPublished() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.retain/client",
        "${kafka}/subscribe.retain/server"})
    public void shouldReceiveRetainedNoRetainAsPublished() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.retain/client",
        "${kafka}/subscribe.retain.fragmented/server"})
    public void shouldReceiveRetainedFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.filter.change.retain/client",
        "${kafka}/subscribe.filter.change.retain/server"})
    public void shouldReceiveRetainedAfterFilterChange() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.filter.change.retain.no.local/client",
        "${kafka}/subscribe.filter.change.retain.no.local/server"})
    public void shouldApplyConditionsToRetainedAfterFilterChange() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.filter.change.retain/client",
        "${kafka}/subscribe.filter.change.retain.buffer/server"})
    public void shouldReceiveRetainedAfterFilterChangeBufferMessages() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.filter.change.retain.buffer.fragmented/client",
        "${kafka}/subscribe.filter.change.retain.buffer.fragmented/server"})
    public void shouldReceiveBufferedMessagesWithOwnFlags() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.filter.change.retain.buffer.fragment.completed/client",
        "${kafka}/subscribe.filter.change.retain.buffer.fragment.completed/server"})
    public void shouldReceiveBufferedFragmentCompletedWithOwnFlags() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.deferred.filter.change.retain/client",
        "${kafka}/subscribe.deferred.filter.change.retain/server"})
    public void shouldReceiveRetainedAfterDeferredFilterChange() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.filter.change.retain.resubscribe/client",
        "${kafka}/subscribe.filter.change.retain.resubscribe/server"})
    public void shouldReceiveRetainedAfterResubscribe() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.one.message.receive.response.topic.and.correlation.data/client",
        "${kafka}/subscribe.one.message.receive.response.topic.and.correlation.data/server"})
    public void shouldReceiveCorrelationData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.one.message.user.properties.unaltered/client",
        "${kafka}/subscribe.one.message.user.properties.unaltered/server"})
    public void shouldReceiveOneMessageWithUserPropertiesUnaltered() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.publish.no.local/client",
        "${kafka}/subscribe.publish.no.local/server"})
    public void shouldNotReceiveLocal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.receive.message.overlapping.wildcard/client",
        "${kafka}/subscribe.receive.message.overlapping.wildcard/server"})
    public void shouldReceiveMessageOverlappingWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.receive.message.wildcard/client",
        "${kafka}/subscribe.receive.message.wildcard/server"})
    public void shouldReceiveOneMessageWithPatternTopic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.topic.filter.multi.level.wildcard/client",
        "${kafka}/subscribe.topic.filter.multi.level.wildcard/server"})
    public void shouldFilterMultiLevelWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.topic.filter.single.and.multi.level.wildcard/client",
        "${kafka}/subscribe.topic.filter.single.and.multi.level.wildcard/server"})
    public void shouldFilterSingleAndMultiLevelWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.topic.filter.single.level.wildcard/client",
        "${kafka}/subscribe.topic.filter.single.level.wildcard/server"})
    public void shouldFilterSingleLevelWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.topic.filter.trailing.empty.level/client",
        "${kafka}/subscribe.topic.filter.trailing.empty.level/server"})
    public void shouldFilterTrailingEmptyLevel() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.topic.filter.two.single.level.wildcard/client",
        "${kafka}/subscribe.topic.filter.two.single.level.wildcard/server"})
    public void shouldFilterTwoSingleLevelWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.topic.filters.aggregated.both.exact/client",
        "${kafka}/subscribe.topic.filters.aggregated.both.exact/server"})
    public void shouldFilterAggregatedBothExact() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.topic.filters.aggregated.exact.and.wildcard/client",
        "${kafka}/subscribe.topic.filters.aggregated.exact.and.wildcard/server"})
    public void shouldFilterAggregatedExactAndWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.topic.filters.isolated.both.exact/client",
        "${kafka}/subscribe.topic.filters.isolated.both.exact/server"})
    public void shouldFilterIsolatedBothExact() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.topic.filters.isolated.exact.and.wildcard/client",
        "${kafka}/subscribe.topic.filters.isolated.exact.and.wildcard/server"})
    public void shouldFilterIsolatedExactAndWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.topic.filters.overlapping.wildcards/client",
        "${kafka}/subscribe.topic.filters.overlapping.wildcards/server"})
    public void shouldFilterOverlappingWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/unsubscribe.after.subscribe/client",
        "${kafka}/unsubscribe.after.subscribe/server"})
    public void shouldAcknowledge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/unsubscribe.topic.filter.single/client",
        "${kafka}/unsubscribe.topic.filter.single/server"})
    public void shouldAcknowledgeSingleTopicFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.receive.message.qos1/client",
        "${kafka}/subscribe.receive.message.qos1/server"})
    public void shouldReceiveMessageQoS1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = SESSION_STATE_STORE_ENABLED_NAME, value = "true")
    @Specification({
        "${mqtt}/subscribe.receive.message.qos1/client",
        "${kafka}/subscribe.receive.message.qos1.session.store/server"})
    public void shouldReceiveMessageQoS1WithSessionStateStore() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = SESSION_STATE_STORE_ENABLED_NAME, value = "true")
    @Specification({
        "${mqtt}/subscribe.resume.qos1.outbound/client",
        "${kafka}/subscribe.resume.qos1.outbound/server"})
    public void shouldResumeSubscribeQoS1Outbound() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = SESSION_STATE_STORE_ENABLED_NAME, value = "true")
    @Specification({
        "${mqtt}/subscribe.resume.qos1.outbound/client",
        "${kafka}/subscribe.resume.qos1.outbound.stale.offset/server"})
    public void shouldResumeSubscribeQoS1OutboundStaleOffset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = SESSION_STATE_STORE_ENABLED_NAME, value = "true")
    @Specification({
        "${mqtt}/subscribe.resume.retained.qos1.outbound.stale.offset/client",
        "${kafka}/subscribe.resume.retained.qos1.outbound.stale.offset/server"})
    public void shouldResumeSubscribeRetainedQoS1OutboundStaleOffset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = SESSION_STATE_STORE_ENABLED_NAME, value = "true")
    @Specification({
        "${mqtt}/subscribe.close.qos1.outbound/client",
        "${kafka}/subscribe.close.qos1.outbound/server"})
    public void shouldCloseSubscribeQoS1Outbound() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.mixed.qos.second.client.begin.keeps.pending.offset.commit/client",
        "${kafka}/subscribe.mixed.qos.second.client.begin.keeps.pending.offset.commit/server"})
    public void shouldKeepPendingOffsetCommitWhenSecondClientBeginsSubscribe() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.qos2.second.client.restore.keeps.packet.id.offset/client",
        "${kafka}/subscribe.qos2.second.client.restore.keeps.packet.id.offset/server"})
    public void shouldKeepPacketIdOffsetWhenSecondClientRestoresQoS2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.receive.message.qos2/client",
        "${kafka}/subscribe.receive.message.qos2/server"})
    public void shouldReceiveMessageQoS2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = SESSION_STATE_STORE_ENABLED_NAME, value = "true")
    @Specification({
        "${mqtt}/subscribe.receive.message.qos2/client",
        "${kafka}/subscribe.receive.message.qos2.session.store/server"})
    public void shouldReceiveMessageQoS2WithSessionStateStore() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.receive.messages.qos2.sparse.offsets/client",
        "${kafka}/subscribe.receive.messages.qos2.sparse.offsets/server"})
    public void shouldReceiveMessagesQoS2SparseOffsets() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.qos2.version1.offset.metadata/client",
        "${kafka}/subscribe.qos2.version1.offset.metadata/server"})
    public void shouldReceiveMessageQoS2WithVersion1OffsetMetadata() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.receive.message.qos1.multiple.clients/client",
        "${kafka}/subscribe.receive.message.qos1.multiple.clients/server"})
    public void shouldReceiveMessageQoS1FromMultipleClients() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.receive.message.qos1.published.qos2/client",
        "${kafka}/subscribe.receive.message.qos1.published.qos2/server"})
    public void shouldReceiveMessageQoS1PublishedAsQoS2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.receive.messages.mixture.qos/client",
        "${kafka}/subscribe.receive.messages.mixture.qos/server"})
    public void shouldReceiveMessagesMixtureQos() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.receive.message.overlapping.wildcard.mixed.qos/client",
        "${kafka}/subscribe.receive.message.overlapping.wildcard.mixed.qos/server"})
    public void shouldReceiveMessageOverlappingWildcardMixedQos() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.reconnect.replay.qos1.unacked.message/client",
        "${kafka}/subscribe.reconnect.replay.qos1.unacked.message/server"})
    public void shouldReplayUnackedQoS1MessageAtReconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.reconnect.replay.qos2.unreceived.message/client",
        "${kafka}/subscribe.reconnect.replay.qos2.unreceived.message/server"})
    public void shouldReplayUnreceivedQoS2MessageAtReconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.reconnect.replay.qos2.incomplete.message/client",
        "${kafka}/subscribe.reconnect.replay.qos2.incomplete.message/server"})
    public void shouldReplayIncompleteQoS2MessageAtReconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.replay.retained.message.qos1/client",
        "${kafka}/subscribe.replay.retained.message.qos1/server"})
    public void shouldReplayRetainedQos1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.replay.retained.message.qos2/client",
        "${kafka}/subscribe.replay.retained.message.qos2/server"})
    public void shouldReplayRetainedQos2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.expire.message/client",
        "${kafka}/subscribe.expire.message/server"})
    public void shouldExpireMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.expire.message.then.deliver/client",
        "${kafka}/subscribe.expire.message.then.deliver/server"})
    public void shouldExpireMessageThenDeliver() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.expire.message/client",
        "${kafka}/subscribe.expire.message.fragmented/server"})
    public void shouldExpireMessageFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.message.expiry.interval.reduced/client",
        "${kafka}/subscribe.message.expiry.interval.reduced/server"})
    public void shouldReceiveMessageWithReducedExpiryInterval() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.retained.message.expiry.interval.reduced/client",
        "${kafka}/subscribe.retained.message.expiry.interval.reduced/server"})
    public void shouldReceiveRetainedMessageWithReducedExpiryInterval() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.retained.expire.message/client",
        "${kafka}/subscribe.retained.expire.message/server"})
    public void shouldExpireRetainedMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.retained.expire.message.then.deliver/client",
        "${kafka}/subscribe.retained.expire.message.then.deliver/server"})
    public void shouldExpireRetainedMessageThenDeliver() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.server.sent.abort.before.begin/client",
        "${kafka}/subscribe.server.sent.abort.before.begin/server"})
    public void shouldReceiveServerSentAbortBeforeBegin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.skip.removed.retained.message/client",
        "${kafka}/subscribe.retained.tombstone/server"})
    public void shouldSkipTombstoneOnRetainedReplay() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.and.non.shared.receive.messages/client",
        "${kafka}/subscribe.shared.and.non.shared.receive.messages/server"})
    public void shouldSubscribeSharedAndNonSharedReceiveMessages() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.skip.removed.retained.message/client",
        "${kafka}/subscribe.retained.empty.value/server"})
    public void shouldSkipEmptyValueOnRetainedReplay() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.client.sent.abort/client",
        "${kafka}/subscribe.shared.client.sent.abort/server"})
    public void shouldSubscribeSharedClientSentAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.one.message.subscription.id.127/client",
        "${kafka}/subscribe.one.message.subscription.id.127/server"})
    public void shouldSubscribeOneMessageSubscriptionId127() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.client.sent.reset/client",
        "${kafka}/subscribe.shared.client.sent.reset/server"})
    public void shouldSubscribeSharedClientSentReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.receive.message.parent.level/client",
        "${kafka}/subscribe.receive.message.parent.level/server"})
    public void shouldReceiveMessageAtParentLevel() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.distinct.filters/client",
        "${kafka}/subscribe.shared.distinct.filters/server"})
    public void shouldSubscribeSharedDistinctFilters() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.receive.message.parent.level.overlapping/client",
        "${kafka}/subscribe.receive.message.parent.level.overlapping/server"})
    public void shouldReceiveMessageAtParentLevelWithOverlappingFilters() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.distinct.share.names/client",
        "${kafka}/subscribe.shared.distinct.share.names/server"})
    public void shouldSubscribeSharedDistinctShareNames() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.retain.parent.level/client",
        "${kafka}/subscribe.retain.parent.level/server"})
    public void shouldReceiveRetainedAtParentLevel() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.expire.message/client",
        "${kafka}/subscribe.shared.expire.message/server"})
    public void shouldSubscribeSharedExpireMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.message.expiry.interval.reduced/client",
        "${kafka}/subscribe.shared.message.expiry.interval.reduced/server"})
    public void shouldSubscribeSharedMessageExpiryIntervalReduced() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.expire.message.fragmented/client",
        "${kafka}/subscribe.shared.expire.message.fragmented/server"})
    public void shouldSubscribeSharedExpireMessageFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.receive.message.qos0/client",
        "${kafka}/subscribe.shared.receive.message.qos0/server"})
    public void shouldSubscribeSharedReceiveMessageQos0() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.receive.message.qos0.fragmented/client",
        "${kafka}/subscribe.shared.receive.message.qos0.fragmented/server"})
    public void shouldSubscribeSharedReceiveMessageQos0Fragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.receive.message.qos1/client",
        "${kafka}/subscribe.shared.receive.message.qos1/server"})
    public void shouldSubscribeSharedReceiveMessageQos1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.receive.message.qos1.fragmented/client",
        "${kafka}/subscribe.shared.receive.message.qos1.fragmented/server"})
    public void shouldSubscribeSharedReceiveMessageQos1Fragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.receive.message.qos1.redelivered/client",
        "${kafka}/subscribe.shared.receive.message.qos1.redelivered/server"})
    public void shouldSubscribeSharedReceiveMessageQos1Redelivered() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.receive.message.qos1.granted.qos0/client",
        "${kafka}/subscribe.shared.receive.message.qos1.granted.qos0/server"})
    public void shouldSubscribeSharedReceiveMessageQos1GrantedQos0() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.receive.message.qos1.published.qos2/client",
        "${kafka}/subscribe.shared.receive.message.qos1.published.qos2/server"})
    public void shouldSubscribeSharedReceiveMessageQos1PublishedQos2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.receive.message.qos1.reject.with.reasoncode/client",
        "${kafka}/subscribe.shared.receive.message.qos1.reject.with.reasoncode/server"})
    public void shouldSubscribeSharedReceiveMessageQos1RejectWithReasoncode() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.receive.message.qos1.reject.with.reasoncode.fragmented/client",
        "${kafka}/subscribe.shared.receive.message.qos1.reject.with.reasoncode.fragmented/server"})
    public void shouldSubscribeSharedReceiveMessageQos1RejectWithReasoncodeFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.reject.filter.mismatch/client",
        "${kafka}/subscribe.shared.reject.filter.mismatch/server"})
    public void shouldSubscribeSharedRejectFilterMismatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.reject.before.begin/client",
        "${kafka}/subscribe.shared.reject.filter.mismatch/server"})
    public void shouldSubscribeSharedRejectBeforeBegin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.expire.message.qos2/client",
        "${kafka}/subscribe.shared.expire.message.qos2/server"})
    public void shouldSubscribeSharedExpireMessageQos2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.receive.message.qos2/client",
        "${kafka}/subscribe.shared.receive.message.qos2/server"})
    public void shouldSubscribeSharedReceiveMessageQos2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.receive.message.qos2.existing.tickets/client",
        "${kafka}/subscribe.shared.receive.message.qos2.existing.tickets/server"})
    public void shouldSubscribeSharedReceiveMessageQos2ExistingTickets() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.recover.qos2.pubrel.pending/client",
        "${kafka}/subscribe.shared.recover.qos2.pubrel.pending/server"})
    public void shouldSubscribeSharedRecoverQos2PubrelPending() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.unsubscribe.inflight.qos2.disconnect.recover.publish.sent/client",
        "${kafka}/subscribe.shared.unsubscribe.inflight.qos2.disconnect.recover.publish.sent/server"})
    public void shouldSubscribeSharedUnsubscribeInflightQos2DisconnectRecoverPublishSent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.unsubscribe.inflight.qos2.disconnect.recover.pubrel.pending/client",
        "${kafka}/subscribe.shared.unsubscribe.inflight.qos2.disconnect.recover.pubrel.pending/server"})
    public void shouldSubscribeSharedUnsubscribeInflightQos2DisconnectRecoverPubrelPending() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.unsubscribe.release.qos1/client",
        "${kafka}/subscribe.shared.unsubscribe.release.qos1/server"})
    public void shouldSubscribeSharedUnsubscribeReleaseQos1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.unsubscribe.release.qos2/client",
        "${kafka}/subscribe.shared.unsubscribe.release.qos2/server"})
    public void shouldSubscribeSharedUnsubscribeReleaseQos2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.unsubscribe.inflight.then.release/client",
        "${kafka}/subscribe.shared.unsubscribe.inflight.then.release/server"})
    public void shouldSubscribeSharedUnsubscribeInflightThenRelease() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.recover.qos2.publish.sent/client",
        "${kafka}/subscribe.shared.recover.qos2.publish.sent/server"})
    public void shouldSubscribeSharedRecoverQos2PublishSent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.recover.qos2.publish.sent.fragmented/client",
        "${kafka}/subscribe.shared.recover.qos2.publish.sent.fragmented/server"})
    public void shouldSubscribeSharedRecoverQos2PublishSentFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.recover.qos2.publish.sent.offset.mismatch/client",
        "${kafka}/subscribe.shared.recover.qos2.publish.sent.offset.mismatch/server"})
    public void shouldSubscribeSharedRecoverQos2PublishSentOffsetMismatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.recover.qos2.publish.sent.expired/client",
        "${kafka}/subscribe.shared.recover.qos2.publish.sent.expired/server"})
    public void shouldSubscribeSharedRecoverQos2PublishSentExpired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.recover.qos2.publish.sent.missing/client",
        "${kafka}/subscribe.shared.recover.qos2.publish.sent.missing/server"})
    public void shouldSubscribeSharedRecoverQos2PublishSentMissing() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.recover.qos2.publish.sent.beyond.end/client",
        "${kafka}/subscribe.shared.recover.qos2.publish.sent.beyond.end/server"})
    public void shouldSubscribeSharedRecoverQos2PublishSentBeyondEnd() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.recover.qos2.then.receive.message/client",
        "${kafka}/subscribe.shared.recover.qos2.then.receive.message/server"})
    public void shouldSubscribeSharedRecoverQos2ThenReceiveMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.receive.message.qos2.fragmented/client",
        "${kafka}/subscribe.shared.receive.message.qos2.fragmented/server"})
    public void shouldSubscribeSharedReceiveMessageQos2Fragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.receive.message.qos2.reject.with.reasoncode/client",
        "${kafka}/subscribe.shared.receive.message.qos2.reject.with.reasoncode/server"})
    public void shouldSubscribeSharedReceiveMessageQos2RejectWithReasoncode() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.receive.messages.qos2/client",
        "${kafka}/subscribe.shared.receive.messages.qos2/server"})
    public void shouldSubscribeSharedReceiveMessagesQos2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.reject.unreadable.metadata/client"})
    public void shouldSubscribeSharedRejectUnreadableMetadata() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.reject.unsupported.version/client",
        "${kafka}/subscribe.shared.reject.unsupported.version/server"})
    public void shouldSubscribeSharedRejectUnsupportedVersion() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.resubscribe.change.qos/client",
        "${kafka}/subscribe.shared.resubscribe.change.qos/server"})
    public void shouldSubscribeSharedResubscribeChangeQos() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.server.sent.abort/client",
        "${kafka}/subscribe.shared.server.sent.abort/server"})
    public void shouldSubscribeSharedServerSentAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.when.subscribe.share.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.receive.message.qos0/client",
        "${kafka}/subscribe.shared.receive.message.qos0/server"})
    public void shouldSubscribeSharedReceiveMessageQos0WhenShareMatchesRoute() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.when.subscribe.share.wildcard.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.receive.message.qos0/client",
        "${kafka}/subscribe.shared.receive.message.qos0/server"})
    public void shouldSubscribeSharedReceiveMessageQos0WhenShareMatchesWildcardRoute() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.when.subscribe.share.mismatch.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.reject.share.mismatch/client"})
    public void shouldSubscribeSharedRejectShareMismatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.restore.membership/client",
        "${kafka}/subscribe.shared.restore.membership/server"})
    public void shouldSubscribeSharedRestoreMembershipWithoutJoining() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/subscribe.shared.join.new.life/client",
        "${kafka}/subscribe.shared.join.new.life/server"})
    public void shouldSubscribeSharedJoinNewLife() throws Exception
    {
        k3po.finish();
    }
}
