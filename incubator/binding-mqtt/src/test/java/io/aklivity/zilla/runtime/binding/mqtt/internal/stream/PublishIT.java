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
package io.aklivity.zilla.runtime.binding.mqtt.internal.stream;

import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfiguration.PUBLISH_TIMEOUT;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfigurationTest.MAXIMUM_QOS_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfigurationTest.PUBLISH_TIMEOUT_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfigurationTest.RETAIN_AVAILABLE_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfigurationTest.SESSION_AVAILABLE_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfigurationTest.SESSION_EXPIRY_INTERVAL_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfigurationTest.SHARED_SUBSCRIPTION_AVAILABLE_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfigurationTest.TOPIC_ALIAS_MAXIMUM_NAME;
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

public class PublishIT
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
        "${net}/publish.one.message/client",
        "${app}/publish.one.message/server"})
    @Configure(name = SESSION_AVAILABLE_NAME, value = "false")
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldPublishOneMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.retained/client",
        "${app}/publish.retained/server"})
    @Configure(name = SESSION_AVAILABLE_NAME, value = "false")
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldPublishRetainedMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.message.with.topic.alias/client",
        "${app}/publish.message.with.topic.alias/server"})
    @Configure(name = SESSION_AVAILABLE_NAME, value = "false")
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = TOPIC_ALIAS_MAXIMUM_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldPublishMessageWithTopicAlias() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.multiple.messages/client",
        "${app}/publish.multiple.messages/server"})
    @Configure(name = SESSION_AVAILABLE_NAME, value = "false")
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldPublishMultipleMessages() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.multiple.messages.unfragmented/client",
        "${app}/publish.multiple.messages/server"})
    @Configure(name = SESSION_AVAILABLE_NAME, value = "false")
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldPublishMultipleMessagesUnfragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.one.message.subscribe.unfragmented/client",
        "${app}/publish.one.message.subscribe.unfragmented/server"})
    @Configure(name = SESSION_AVAILABLE_NAME, value = "false")
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldPublishOneMessageSubscribeUnfragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.multiple.messages.with.delay/client",
        "${app}/publish.multiple.messages/server"})
    @Configure(name = SESSION_AVAILABLE_NAME, value = "false")
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    @Configure(name = PUBLISH_TIMEOUT_NAME, value = "5")
    public void shouldPublishMultipleMessagesWithDelay() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("PUBLISHED_MESSAGE_TWO");
        Thread.sleep(500);
        k3po.notifyBarrier("PUBLISH_MESSAGE_THREE");
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.messages.with.topic.alias.distinct/client",
        "${app}/publish.messages.with.topic.alias.distinct/server"})
    @Configure(name = SESSION_AVAILABLE_NAME, value = "false")
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = TOPIC_ALIAS_MAXIMUM_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldPublishMessagesWithTopicAliasDistinct() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.messages.with.topic.alias.repeated/client",
        "${app}/publish.messages.with.topic.alias.repeated/server"})
    @Configure(name = SESSION_AVAILABLE_NAME, value = "false")
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = TOPIC_ALIAS_MAXIMUM_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldPublishMessagesWithTopicAliasRepeated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.messages.with.topic.alias.replaced/client",
        "${app}/publish.messages.with.topic.alias.replaced/server"})
    @Configure(name = SESSION_AVAILABLE_NAME, value = "false")
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = TOPIC_ALIAS_MAXIMUM_NAME, value = "1")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldPublishMessagesWithTopicAliasReplaced() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.messages.with.topic.alias.invalid.scope/client",
        "${app}/publish.messages.with.topic.alias.invalid.scope/server"})
    @Configure(name = SESSION_AVAILABLE_NAME, value = "false")
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = TOPIC_ALIAS_MAXIMUM_NAME, value = "1")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldSendMessagesWithTopicAliasInvalidScope() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.topic.not.routed/client"})
    @Configure(name = SESSION_AVAILABLE_NAME, value = "false")
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldRejectTopicNotRouted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.reject.topic.alias.exceeds.maximum/client"})
    @Configure(name = SESSION_AVAILABLE_NAME, value = "false")
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldRejectPublishWhenTopicAliasExceedsMaximum() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.reject.topic.alias.repeated/client"})
    @Configure(name = SESSION_AVAILABLE_NAME, value = "false")
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = TOPIC_ALIAS_MAXIMUM_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldRejectPublishWithMultipleTopicAliases() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.reject.client.sent.subscription.id/client"})
    @Configure(name = SESSION_AVAILABLE_NAME, value = "false")
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = TOPIC_ALIAS_MAXIMUM_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldRejectPublishClientSentSubscriptionId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.reject.invalid.payload.format/client"})
    @Configure(name = SESSION_AVAILABLE_NAME, value = "false")
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = TOPIC_ALIAS_MAXIMUM_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldRejectPublishInvalidPayloadFormat() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.reject.qos1.not.supported/client"})
    @Configure(name = SESSION_AVAILABLE_NAME, value = "false")
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "0")
    @Configure(name = TOPIC_ALIAS_MAXIMUM_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldRejectPublisQos1NotSupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.reject.qos2.not.supported/client"})
    @Configure(name = SESSION_AVAILABLE_NAME, value = "false")
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "0")
    @Configure(name = TOPIC_ALIAS_MAXIMUM_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldRejectPublisQos2NotSupported() throws Exception
    {
        k3po.finish();
    }

    @Ignore
    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.reject.qos0.with.packet.id/client"})
    @Configure(name = SESSION_AVAILABLE_NAME, value = "false")
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = TOPIC_ALIAS_MAXIMUM_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldRejectPublishQos0WithPacketId() throws Exception
    {
        k3po.finish();
    }

    @Ignore
    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.reject.qos1.without.packet.id/client"})
    @Configure(name = SESSION_AVAILABLE_NAME, value = "false")
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = TOPIC_ALIAS_MAXIMUM_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldRejectPublishQos1WithoutPacketId() throws Exception
    {
        k3po.finish();
    }

    @Ignore
    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.reject.qos2.without.packet.id/client"})
    @Configure(name = SESSION_AVAILABLE_NAME, value = "false")
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = TOPIC_ALIAS_MAXIMUM_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldRejectPublishQos2WithoutPacketId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.reject.retain.not.supported/client"})
    @Configure(name = SESSION_AVAILABLE_NAME, value = "false")
    @Configure(name = RETAIN_AVAILABLE_NAME, value = "false")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = TOPIC_ALIAS_MAXIMUM_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldRejectPublishRetainNotSupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.with.user.property/client",
        "${app}/publish.with.user.property/server"})
    @Configure(name = SESSION_AVAILABLE_NAME, value = "false")
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldPublishWithUserProperty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.with.user.properties.distinct/client",
        "${app}/publish.with.user.properties.distinct/server"})
    @Configure(name = SESSION_AVAILABLE_NAME, value = "false")
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldPublishWithDistinctUserProperties() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.with.user.properties.repeated/client",
        "${app}/publish.with.user.properties.repeated/server"})
    @Configure(name = SESSION_AVAILABLE_NAME, value = "false")
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldPublishWithRepeatedUserProperties() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.empty.retained.message/client",
        "${app}/publish.empty.retained.message/server"})
    @Configure(name = SESSION_AVAILABLE_NAME, value = "false")
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldPublishEmptyRetainedMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.empty.message/client",
        "${app}/publish.empty.message/server"})
    @Configure(name = SESSION_AVAILABLE_NAME, value = "false")
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldPublishEmptyMessage() throws Exception
    {
        k3po.finish();
    }
}
