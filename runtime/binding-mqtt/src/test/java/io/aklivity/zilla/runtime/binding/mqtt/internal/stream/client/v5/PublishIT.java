/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.mqtt.internal.stream.client.v5;

import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfiguration.PUBLISH_TIMEOUT;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfigurationTest.TOPIC_ALIAS_MAXIMUM_NAME;
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

public class PublishIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/mqtt/streams/network/v5")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/mqtt/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configure(PUBLISH_TIMEOUT, 1L)
        .configure(ENGINE_DRAIN_ON_CLOSE, false)
        .configurationRoot("io/aklivity/zilla/specs/binding/mqtt/config")
        .external("net0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);


    //TODO: adapt this once we decided
    @Test
    @Configuration("client.yaml")
    @Specification({
        "${net}/publish.empty.message/server",
        "${app}/publish.empty.message/client"})
    public void shouldSendEmptyMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${net}/publish.empty.retained.message/server",
        "${app}/publish.empty.retained.message/client"})
    public void shouldSendEmptyRetainedMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${net}/publish.multiple.messages/server",
        "${app}/publish.multiple.messages/client"})
    public void shouldSendMultipleMessages() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${net}/publish.one.message.properties/server",
        "${app}/publish.one.message.properties/client"})
    public void shouldSendOneMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${net}/publish.retained/server",
        "${app}/publish.retained/client"})
    public void shouldPublishRetainedMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${net}/publish.with.user.properties.distinct/server",
        "${app}/publish.with.user.properties.distinct/client"})
    public void shouldSendWithDistinctUserProperties() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${net}/publish.with.user.properties.repeated/server",
        "${app}/publish.with.user.properties.repeated/client"})
    public void shouldSendWithRepeatedUserProperties() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${net}/publish.with.user.property/server",
        "${app}/publish.with.user.property/client"})
    public void shouldSendWithUserProperty() throws Exception
    {
        k3po.finish();
    }

    // 2.2.2.2 Malformed Packet, 4.13.1
    @Test
    @Configuration("client.yaml")
    @Specification({
        "${net}/server.sent.publish.invalid.property.identifier/server",
        "${app}/session.abort.on.network.error/client"})
    public void shouldServerSentPublishInvalidPropertyIdentifier() throws Exception
    {
        k3po.finish();
    }

    // 1.2 Malformed Packet, 4.13.1
    @Test
    @Configuration("client.yaml")
    @Specification({
        "${net}/server.sent.publish.too.short/server",
        "${app}/session.abort.on.network.error/client"})
    public void shouldServerSentPublishTooShort() throws Exception
    {
        k3po.finish();
    }

    // 3.3.4 Topic Alias invalid, 4.13.1
    @Test
    @Configuration("client.yaml")
    @Specification({
        "${net}/server.sent.publish.topic.alias.zero/server",
        "${app}/session.abort.on.network.error/client"})
    public void shouldServerSentPublishTopicAliasZero() throws Exception
    {
        k3po.finish();
    }

    // 3.3.2.3.4 Topic Alias, 4.13.1
    @Test
    @Configuration("client.yaml")
    @Specification({
        "${net}/server.sent.publish.topic.alias.exceeds.maximum/server",
        "${app}/session.abort.on.network.error/client"})
    @Configure(name = TOPIC_ALIAS_MAXIMUM_NAME, value = "2")
    public void shouldServerSentPublishTopicAliasExceedsMaximum() throws Exception
    {
        k3po.finish();
    }

    // 3.3.2.3.4 Topic Alias, 4.13.1
    @Test
    @Configuration("client.yaml")
    @Specification({
        "${net}/server.sent.publish.topic.alias.unmapped/server",
        "${app}/session.abort.on.network.error/client"})
    @Configure(name = TOPIC_ALIAS_MAXIMUM_NAME, value = "2")
    public void shouldServerSentPublishTopicAliasUnmapped() throws Exception
    {
        k3po.finish();
    }

    // 3.3.4 QoS not supported, 4.13.1
    @Test
    @Configuration("client.yaml")
    @Specification({
        "${net}/server.sent.publish.qos.exceeds.maximum/server",
        "${app}/server.sent.publish.qos.exceeds.maximum/client"})
    public void shouldServerSentPublishQosExceedsMaximum() throws Exception
    {
        k3po.finish();
    }
}
