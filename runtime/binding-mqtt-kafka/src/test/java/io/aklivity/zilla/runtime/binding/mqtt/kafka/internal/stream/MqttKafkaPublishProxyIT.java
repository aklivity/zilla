/*
 * Copyright 2021-2023 Aklivity Inc
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

import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfigurationTest.BOOTSTRAP_AVAILABLE_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfigurationTest.INSTANCE_ID_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfigurationTest.SESSION_ID_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfigurationTest.WILL_AVAILABLE_NAME;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_BUFFER_SLOT_CAPACITY;
import static io.aklivity.zilla.runtime.engine.test.EngineRule.ENGINE_BUFFER_SLOT_CAPACITY_NAME;
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

public class MqttKafkaPublishProxyIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("mqtt", "io/aklivity/zilla/specs/binding/mqtt/kafka/streams/mqtt")
        .addScriptRoot("kafka", "io/aklivity/zilla/specs/binding/mqtt/kafka/streams/kafka");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configure(ENGINE_BUFFER_SLOT_CAPACITY, 8192)
        .configure(SESSION_ID_NAME,
            "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplySessionId")
        .configure(INSTANCE_ID_NAME,
            "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplyInstanceId")
        .configurationRoot("io/aklivity/zilla/specs/binding/mqtt/kafka/config")
        .external("kafka0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/publish.client.sent.abort/client",
        "${kafka}/publish.client.sent.abort/server"})
    public void shouldReceiveClientSentAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/publish.client.sent.reset/client",
        "${kafka}/publish.client.sent.reset/server"})
    public void shouldReceiveClientSentReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/publish.server.sent.abort/client",
        "${kafka}/publish.server.sent.abort/server"})
    public void shouldReceiveServerSentAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/publish.server.sent.flush/client",
        "${kafka}/publish.server.sent.flush/server"})
    public void shouldReceiveServerSentFlush() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/publish.server.sent.reset/client",
        "${kafka}/publish.server.sent.reset/server"})
    public void shouldReceiveServerSentReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/publish.server.sent.data/client",
        "${kafka}/publish.server.sent.data/server"})
    public void shouldAbortWhenServerSentData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/publish.retained.server.sent.abort/client",
        "${kafka}/publish.retained.server.sent.abort/server"})
    public void shouldPublishRetainedThenReceiveServerSentAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/publish.retained.server.sent.flush/client",
        "${kafka}/publish.retained.server.sent.flush/server"})
    public void shouldPublishRetainedThenReceiveServerSentFlush() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/publish.retained.server.sent.reset/client",
        "${kafka}/publish.retained.server.sent.reset/server"})
    public void shouldPublishRetainedThenReceiveServerSentReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/publish.retained.server.sent.data/client",
        "${kafka}/publish.retained.server.sent.data/server"})
    public void shouldPublishRetainedThenAbortWhenServerSentData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/publish.one.message/client",
        "${kafka}/publish.one.message/server"})
    public void shouldSendOneMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.options.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/publish.one.message/client",
        "${kafka}/publish.one.message.changed.topic.name/server"})
    public void shouldSendOneMessageWithChangedTopicName() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.when.publish.topic.with.messages.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/publish.topic.space/client",
        "${kafka}/publish.topic.space/server"})
    public void shouldSendUsingTopicSpace() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.when.client.topic.space.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = BOOTSTRAP_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/publish.client.topic.space/client",
        "${kafka}/publish.client.topic.space/server"})
    public void shouldSendUsingClientTopicSpace() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/publish.retained/client",
        "${kafka}/publish.retained/server"})
    public void shouldPublishRetainedMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/publish.empty.message/client",
        "${kafka}/publish.empty.message/server"})
    public void shouldSendEmptyMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/publish.multiple.messages/client",
        "${kafka}/publish.multiple.messages/server"})
    public void shouldSendMultipleMessages() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/publish.multiple.clients/client",
        "${kafka}/publish.multiple.clients/server"})
    public void shouldSendMultipleClients() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/publish.with.user.property/client",
        "${kafka}/publish.with.user.property/server"})
    public void shouldSendWithUserProperty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/publish.with.user.properties.distinct/client",
        "${kafka}/publish.with.user.properties.distinct/server"})
    public void shouldSendWithDistinctUserProperties() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/publish.with.user.properties.repeated/client",
        "${kafka}/publish.with.user.properties.repeated/server"})
    public void shouldSendWithRepeatedUserProperties() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/publish.qos1/client",
        "${kafka}/publish.qos1/server"})
    public void shouldSendMessageQos1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/publish.qos2/client",
        "${kafka}/publish.qos2/server"})
    public void shouldSendMessageQos2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/publish.qos2.retained/client",
        "${kafka}/publish.qos2.retained/server"})
    public void shouldSendMessageQos2Retained() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/publish.mixture.qos/client",
        "${kafka}/publish.mixture.qos/server"})
    public void shouldSendMessageMixtureQos() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = ENGINE_BUFFER_SLOT_CAPACITY_NAME, value = "8192")
    @Specification({
        "${mqtt}/publish.10k/client",
        "${kafka}/publish.10k/server"})
    public void shouldSendMessage10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/publish.retained.10k/client",
        "${kafka}/publish.retained.10k/server"})
    public void shouldSendRetainedMessageM10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/publish.reject.large.message/client",
        "${kafka}/publish.reject.large.message/server"})
    public void shouldRejectLargeMessage() throws Exception
    {
        k3po.finish();
    }
}
