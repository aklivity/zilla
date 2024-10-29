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
package io.aklivity.zilla.runtime.binding.mqtt.internal.stream.server.v4;

import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfiguration.PUBLISH_TIMEOUT;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfigurationTest.PUBLISH_TIMEOUT_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfigurationTest.SUBSCRIPTION_ID_NAME;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DRAIN_ON_CLOSE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Before;
import org.junit.Ignore;
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

public class PublishIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/mqtt/streams/network/v4")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/mqtt/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configure(PUBLISH_TIMEOUT, 1L)
        .configure(ENGINE_DRAIN_ON_CLOSE, false)
        .configure(SUBSCRIPTION_ID_NAME,
            "io.aklivity.zilla.runtime.binding.mqtt.internal.stream.server.v4.PublishIT::supplySubscriptionId")

        .configurationRoot("io/aklivity/zilla/specs/binding/mqtt/config")
        .external("app0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("server.validator.yaml")
    @Specification({
        "${net}/publish.invalid.message/client",
        "${app}/session.publish/server"})
    public void shouldPublishInvalidMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.validator.yaml")
    @Specification({
        "${net}/publish.valid.message/client",
        "${app}/publish.valid.message/server"})
    public void shouldPublishValidMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.retained/client",
        "${app}/publish.retained/server"})
    public void shouldPublishRetainedMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.multiple.messages/client",
        "${app}/publish.multiple.messages/server"})
    public void shouldPublishMultipleMessages() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.multiple.messages.disconnect/client",
        "${app}/publish.multiple.messages/server"})
    public void shouldPublishMultipleMessagesAndDisconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.multiple.messages.unfragmented/client",
        "${app}/publish.multiple.messages/server"})
    public void shouldPublishMultipleMessagesUnfragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.subscribe.batched/client",
        "${app}/publish.subscribe.batched/server"})
    public void shouldPublishSubscribeBatched() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.multiple.messages.with.delay/client",
        "${app}/publish.multiple.messages/server"})
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
        "${net}/publish.multiple.messages.with.delay/client",
        "${app}/publish.multiple.messages.timeout/server"})
    @Configure(name = PUBLISH_TIMEOUT_NAME, value = "1")
    public void shouldPublishMultipleMessagesTimeout() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("PUBLISHED_MESSAGE_TWO");
        Thread.sleep(2500);
        k3po.notifyBarrier("PUBLISH_MESSAGE_THREE");
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.topic.not.routed/client",
        "${app}/session.connect/server"})
    public void shouldRejectTopicNotRouted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.reject.qos0.with.packet.id/client",
        "${app}/session.connect/server"})
    public void shouldRejectPublishQos0WithPacketId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.reject.qos1.without.packet.id/client",
        "${app}/session.connect/server"})
    public void shouldRejectPublishQos1WithoutPacketId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.reject.qos2.without.packet.id/client",
        "${app}/session.connect/server"})
    public void shouldRejectPublishQos2WithoutPacketId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.empty.retained.message/client",
        "${app}/publish.empty.retained.message/server"})
    public void shouldPublishEmptyRetainedMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.empty.message/client",
        "${app}/publish.empty.message/server"})
    public void shouldPublishEmptyMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.route.non.default.yaml")
    @Specification({
        "${net}/publish.unroutable/client",
        "${app}/publish.unroutable/server"})
    public void shouldRejectUnroutable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.qos1.dup.after.puback/client",
        "${app}/publish.qos1.dup.after.puback/server"})
    public void shouldPublishQoS1Message() throws Exception
    {
        k3po.finish();
    }

    @Ignore("Github Actions aklivity/zilla#1182")
    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.qos2.no.dupicate.before.pubrel/client",
        "${app}/publish.qos2.no.dupicate.before.pubrel/server"})
    public void shouldPublishQoS2Message() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.mixture.qos/client",
        "${app}/publish.mixture.qos/server"})
    public void shouldPublishMixtureQos() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/publish.10k/client",
        "${app}/publish.10k/server"})
    public void shouldPublish10k() throws Exception
    {
        k3po.finish();
    }

    @Before
    public void setSubscriptionId()
    {
        subscriptionId = 0;
    }

    private static int subscriptionId = 0;
    public static int supplySubscriptionId()
    {
        return ++subscriptionId;
    }
}
