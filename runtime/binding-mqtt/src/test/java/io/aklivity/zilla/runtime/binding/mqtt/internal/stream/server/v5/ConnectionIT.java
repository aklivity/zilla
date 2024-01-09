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
package io.aklivity.zilla.runtime.binding.mqtt.internal.stream.server.v5;

import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfiguration.PUBLISH_TIMEOUT;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfigurationTest.CLIENT_ID_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfigurationTest.CONNECT_TIMEOUT_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfigurationTest.KEEP_ALIVE_MINIMUM_NAME;
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

public class ConnectionIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/mqtt/streams/network/v5")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/mqtt/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
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
        "${net}/connect.successful/client",
        "${app}/session.connect/server"})
    public void shouldConnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.credentials.username.yaml")
    @Specification({
        "${net}/connect.username.authentication.successful/client",
        "${app}/session.connect.authorization/server"})
    public void shouldAuthenticateUsernameAndConnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.credentials.username.yaml")
    @Specification({
        "${net}/connect.username.authentication.failed/client"})
    public void shouldFailUsernameAuthentication() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.credentials.password.yaml")
    @Specification({
        "${net}/connect.password.authentication.successful/client",
        "${app}/session.connect.authorization/server"})
    public void shouldAuthenticatePasswordAndConnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.credentials.password.yaml")
    @Specification({
        "${net}/connect.password.authentication.failed/client"})
    public void shouldFailPasswordAuthentication() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.server.assigned.client.id/client",
        "${app}/session.connect/server"})
    @Configure(name = CLIENT_ID_NAME, value = "client")
    public void shouldConnectWithServerAssignedClientId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.reject.missing.client.id/client"})
    public void shouldRejectMissingClientId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/disconnect/client",
        "${app}/session.connect/server"})
    public void shouldConnectThenDisconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/disconnect.no.reasoncode.no.properties/client",
        "${app}/session.connect/server"})
    public void shouldConnectThenDisconnectWithNoReasonCodeNoProperties() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/disconnect.after.subscribe.and.publish/client",
        "${app}/disconnect.after.subscribe.and.publish/server"})
    public void shouldDisconnectAfterSubscribeAndPublish() throws Exception
    {
        k3po.finish();
    }


    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.invalid.protocol.version/client"})
    public void shouldRejectInvalidProtocolVersion() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.invalid.flags/client"})
    public void shouldRejectMalformedConnectPacket() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.invalid.authentication.method/client"})
    public void shouldRejectBadAuthenticationMethod() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/disconnect.reject.invalid.fixed.header.flags/client",
        "${app}/session.connect/server"})
    public void shouldRejectMalformedDisconnectPacket() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.1.0-2]
    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.reject.second.connect/client",
        "${app}/session.connect/server"})
    public void shouldRejectSecondConnectPacket() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.successful.fragmented/client",
        "${app}/session.connect/server"})
    public void shouldConnectFragmented() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.1.0-1]
    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.reject.other.packet.before.connect/client"})
    public void shouldRejectOtherPacketBeforeConnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.reject.topic.alias.maximum.repeated/client"})
    public void shouldRejectConnectWhenTopicAliasMaximumRepeated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/client.sent.close/client",
        "${app}/client.sent.abort/server"})
    public void shouldReceiveClientSentClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/client.sent.abort/client",
        "${app}/client.sent.abort/server"})
    public void shouldReceiveClientSentAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/client.sent.reset/client",
        "${app}/client.sent.abort/server"})
    public void shouldReceiveClientSentReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/disconnect.after.keep.alive.timeout/client",
        "${app}/session.connect/server"})
    @Configure(name = KEEP_ALIVE_MINIMUM_NAME, value = "1")
    public void shouldDisconnectClientAfterKeepAliveTimeout() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.timeout.before.connect/client"})
    @Configure(name = CONNECT_TIMEOUT_NAME, value = "1")
    public void shouldTimeoutBeforeConnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.maximum.qos.0/client",
        "${app}/connect.maximum.qos.0/server"})
    public void shouldConnectWithMaximumQos0() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.retain.not.supported/client",
        "${app}/connect.retain.not.supported/server"})
    public void shouldConnectWithRetainNotSupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.reject.will.retain.not.supported/client",
        "${app}/connect.reject.will.retain.not.supported/server"})
    public void shouldRejectConnectWillRetainNotSupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.reject.password.flag.no.password/client"})
    public void shouldRejectConnectWithPasswordFlagSetNoPassword() throws Exception
    {
        k3po.finish();
    }

    @Ignore
    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.reject.password.no.password.flag/client"})
    public void shouldRejectConnectWithPasswordNoPasswordFlag() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.reject.username.flag.only/client"})
    public void shouldRejectConnectWithUsernameFlagNoUsername() throws Exception
    {
        k3po.finish();
    }

    @Ignore
    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.reject.username.flag.missing/client"})
    public void shouldRejectConnectWithUsernameNoUsernameFlag() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.reject.will.payload.missing/client"})
    public void shouldRejectConnectWillPayloadMissing() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.reject.will.properties.missing/client"})
    public void shouldRejectConnectWillPropertiesMissing() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.reject.will.topic.missing/client"})
    public void shouldRejectConnectWillTopicMissing() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.will.invalid.will.qos/client"})
    public void shouldRejectInvalidWillQos() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.will.reject.will.qos.1.without.will.flag/client"})
    public void shouldRejectWillQos1WithoutWillFlag() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.will.reject.will.qos.2.without.will.flag/client"})
    public void shouldRejectWillQos2WithoutWillFlag() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.will.reject.will.retain.without.will.flag/client"})
    public void shouldRejectWillRetainWithoutWillFlag() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.max.packet.size.exceeded/client",
        "${app}/connect.max.packet.size.exceeded/server"})
    public void shouldRejectMaxPacketSizeExceeded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.server.defined.keep.alive/client",
        "${app}/session.connect/server"})
    @Configure(name = KEEP_ALIVE_MINIMUM_NAME, value = "10")
    public void shouldConnectWithServerDefinedKeepAlive() throws Exception
    {
        k3po.start();
        Thread.sleep(2000);
        k3po.notifyBarrier("WAIT_2_SECONDS");
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.subscribe.batched/client",
        "${app}/subscribe.topic.filter.single.exact/server"})
    public void shouldConnectAndSubscribeFalseStart() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/disconnect.invalid.session.expiry/client",
        "${app}/session.connect/server"})
    public void shouldRejectInvalidSessionExpiryOnDisconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.non.successful.connack/client",
        "${app}/connect.non.successful.connack/server"})
    public void shouldResetWithReasonCodeOnNonSuccessfulConnack() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.non.successful.disconnect/client",
        "${app}/connect.non.successful.disconnect/server"})
    public void shouldResetWithReasonCodeOnNonSuccessfulDisconnect() throws Exception
    {
        k3po.finish();
    }
}
