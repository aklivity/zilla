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
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfigurationTest.CONNECT_TIMEOUT_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfigurationTest.MAXIMUM_QOS_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfigurationTest.RETAIN_AVAILABLE_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfigurationTest.SESSION_EXPIRY_INTERVAL_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfigurationTest.SHARED_SUBSCRIPTION_AVAILABLE_NAME;
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

public class ConnectionIT
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
        "${net}/connect.successful/client"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldExchangeConnectAndConnackPackets() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.server.assigned.client.id/client"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldExchangeConnectAndConnackPacketsWithServerAssignedClientId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.reject.missing.client.id/client"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldRejectMissingClientId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/disconnect/client"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldExchangeConnectionPacketsThenDisconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.invalid.protocol.version/client"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldRejectInvalidMqttProtocolVersion() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.invalid.flags/client"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldRejectMalformedConnectPacket() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.invalid.authentication.method/client"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldRejectBadAuthenticationMethod() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/disconnect.invalid.fixed.header.flags/client"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldRejectMalformedDisconnectPacket() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.1.0-2]
    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.reject.second.connect/client"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldRejectSecondConnectPacket() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.successful.fragmented/client"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldProcessFragmentedConnectPacket() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.when.topic.publish.only.yaml")
    @Specification({
        "${net}/connect.reject.topic.alias.maximum.repeated/client"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldRejectConnectWhenTopicAliasMaximumRepeated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/client.sent.close/client",
        "${app}/client.sent.abort/server"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldReceiveClientSentClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/client.sent.abort/client",
        "${app}/client.sent.abort/server"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldReceiveClientSentAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/client.sent.reset/client",
        "${app}/client.sent.abort/server"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldReceiveClientSentReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/disconnect.after.keep.alive.timeout/client"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldDisconnectClientAfterKeepAliveTimeout() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.timeout.before.connect/client"})
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    @Configure(name = CONNECT_TIMEOUT_NAME, value = "1")
    public void shouldTimeoutBeforeConnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.maximum.qos.0/client"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldConnectWithMaximumQos0() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.retain.unavailable/client"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = RETAIN_AVAILABLE_NAME, value = "false")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldConnectWithNoRetain() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.reject.username.not.authorized/client"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldRejectConnectWithUsername() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.reject.password.not.authorized/client"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldRejectConnectWithPassword() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.will.invalid.will.qos/client"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldRejectInvalidWillQos() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.will.reject.will.qos.1.without.will.flag/client"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldRejectWillQos1WithoutWillFlag() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.will.reject.will.qos.2.without.will.flag/client"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldRejectWillQos2WithoutWillFlag() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.will.reject.will.retain.without.will.flag/client"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldRejectWillRetainWithoutWillFlag() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.when.topic.or.sessions.yaml")
    @Specification({
        "${net}/connect.will.message.with.abrupt.disconnect/client",
        "${app}/connect.will.message.with.abrupt.disconnect/server"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldPublishWillMessageAfterAbruptClientDisconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.when.topic.or.sessions.yaml")
    @Specification({
        "${net}/connect.will.message.with.normal.disconnect/client",
        "${app}/connect.will.message.with.normal.disconnect/server"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldNotPublishWillMessageAfterNormalClientDisconnect() throws Exception
    {
        k3po.finish();
    }

    //TODO: can this be deleted? I think in the current form, this scenario is not valid
    // If the first connection is rejected, the will topic won't be populated, hence the publish on app side cannot happen
    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.with.will.message.then.publish.one.message/client",
        "${app}/connect.with.will.message.then.publish.one.message/server"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "0")
    public void shouldConnectWithWillMessageThenPublishOneMessage() throws Exception
    {
        k3po.finish();
    }
}
