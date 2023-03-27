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
package io.aklivity.zilla.runtime.binding.mqtt.internal.stream;

import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfiguration.PUBLISH_TIMEOUT;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfigurationTest.MAXIMUM_QOS_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfigurationTest.NO_LOCAL_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfigurationTest.SESSION_EXPIRY_INTERVAL_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfigurationTest.SHARED_SUBSCRIPTION_AVAILABLE_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfigurationTest.SUBSCRIPTION_IDENTIFIERS_AVAILABLE_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfigurationTest.WILDCARD_SUBSCRIPTION_AVAILABLE_NAME;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DRAIN_ON_CLOSE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Ignore;
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

    private final TestRule timeout = new DisableOnDebug(new Timeout(20, SECONDS));

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
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldReceivePublishAfterSendingSubscribe() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.when.topic.subscribe.only.yaml")
    @Specification({
        "${net}/subscribe.one.message/client",
        "${app}/subscribe.one.message/server"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldReceivePublishAfterSendingSubscribeWithRouteExtension() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.one.message.then.publish.message/client",
        "${app}/subscribe.one.message.then.publish.message/server"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldSubscribeOneMessageThenPublishMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.one.message.with.null.payload/client",
        "${app}/subscribe.one.message.with.null.payload/server"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldReceivePublishWithNullPayloadAfterSendingSubscribe() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.one.message.receive.response.topic.and.correlation.data/client",
        "${app}/subscribe.one.message.receive.response.topic.and.correlation.data/server"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldReceiveCorrelationDataAfterSendingSubscribe() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.one.message.with.invalid.subscription.id/client"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldReceivePublishWithInvalidSubscriptionIdAfterSendingSubscribe() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.topic.filter.single.exact/client",
        "${app}/subscribe.topic.filter.single.exact/server"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldSubscribeToOneExactTopic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.topic.filter.multi.level.wildcard/client",
        "${app}/subscribe.topic.filter.multi.level.wildcard/server"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldSubscribeToWildcardTopic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.topic.filters.aggregated.both.exact/client",
        "${app}/subscribe.topic.filters.aggregated.both.exact/server"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldSubscribeWithTwoTopicsBothExactOneSubscribePacket() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.topic.filters.isolated.both.exact/client",
        "${app}/subscribe.topic.filters.isolated.both.exact/server"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldSubscribeWithTwoTopicsBothExactTwoSubscribePackets() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.topic.filters.aggregated.both.wildcard/client",
        "${app}/subscribe.topic.filters.aggregated.both.wildcard/server"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldSubscribeWithTwoTopicsBothWildcardOneSubscribePacket() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.topic.filters.isolated.both.wildcard/client",
        "${app}/subscribe.topic.filters.isolated.both.wildcard/server"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldSubscribeWithTwoTopicsBothWildcardTwoSubscribePackets() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.topic.filters.aggregated.exact.and.wildcard/client",
        "${app}/subscribe.topic.filters.aggregated.exact.and.wildcard/server"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldSubscribeWithTwoTopicsOneExactOneSubscribePacket() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.topic.filters.isolated.exact.and.wildcard/client",
        "${app}/subscribe.topic.filters.isolated.exact.and.wildcard/server"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldSubscribeWithTwoTopicsOneExactTwoSubscribePackets() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.retain.as.published/client",
        "${app}/subscribe.retain.as.published/server"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldSubscribeRetainAsPublished() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.get.retained.as.published/client",
        "${app}/subscribe.get.retained.as.published/server"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldSubscribeIgnoreRetainAsPublished() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.reject.no.local/client"})
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    @Configure(name = NO_LOCAL_NAME, value = "false")
    public void shouldRejectSubscribeWithNoLocal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.one.message.get.local/client",
        "${app}/subscribe.one.message.get.local/server"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldSubscribeGetLocalPublishedMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.publish.no.local/client",
        "${app}/subscribe.publish.no.local/server"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldSubscribeThenPublishNoLocal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.after.publish.no.local/client",
        "${app}/subscribe.after.publish.no.local/server"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldSubscribeAfterPublishNoLocal() throws Exception
    {
        k3po.finish();
    }

    @Ignore("GitHub Actions")
    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.messages.aggregated.topic.filters.both.exact/client",
        "${app}/subscribe.messages.with.aggregated.topic.filters.both.exact/server"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldSubscribeMessagesWithTwoTopicsBothExactOneSubscribePacket() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.invalid.fixed.header.flags/client"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldRejectMalformedSubscribePacket() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.invalid.topic.filter/client"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldRejectSubscribePacketWithInvalidTopicFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.reject.wildcard.subscriptions.unavailable/client"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "false")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldRejectSubscribeWithWildcardSubscriptionsUnavailable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.reject.wildcard.subscriptions.unavailable/client"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SUBSCRIPTION_IDENTIFIERS_AVAILABLE_NAME, value = "false")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldRejectSubscribeWithSubscriptionIdentifiersUnavailable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/subscribe.reject.shared.subscription.unavailable/client"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "false")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldConnectWithNoSharedSubscriptions() throws Exception
    {
        k3po.finish();
    }
}
