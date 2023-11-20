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
package io.aklivity.zilla.runtime.binding.mqtt.internal.stream.server.v4;

import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfiguration.PUBLISH_TIMEOUT;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfigurationTest.CLIENT_ID_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfigurationTest.CONNECT_TIMEOUT_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfigurationTest.SUBSCRIPTION_ID_NAME;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DRAIN_ON_CLOSE;
import static io.aklivity.zilla.runtime.engine.test.EngineRule.ENGINE_BUFFER_SLOT_CAPACITY_NAME;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Before;
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
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/mqtt/streams/network/v4")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/mqtt/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configure(PUBLISH_TIMEOUT, 1L)
        .configure(ENGINE_DRAIN_ON_CLOSE, false)
        .configurationRoot("io/aklivity/zilla/specs/binding/mqtt/config")
        .configure(SUBSCRIPTION_ID_NAME,
            "io.aklivity.zilla.runtime.binding.mqtt.internal.stream.server.v4.ConnectionIT::supplySubscriptionId")
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
        "${net}/connect.reject.no.client.id.no.clean.session/client"})
    public void shouldRejectNoClientIdNoCleanSessionSet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.reject.password.flag.no.username.flag/client"})
    public void shouldRejectPasswordFlagNoUsernameFlag() throws Exception
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
        "${net}/disconnect.after.subscribe.and.publish/client",
        "${app}/disconnect.after.subscribe.and.publish/server"})
    public void shouldDisconnectAfterSubscribeAndPublish() throws Exception
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
        "${net}/connect.timeout.before.connect/client"})
    @Configure(name = CONNECT_TIMEOUT_NAME, value = "1")
    public void shouldTimeoutBeforeConnect() throws Exception
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
        "${net}/connect.subscribe.batched/client",
        "${app}/subscribe.topic.filter.single.exact/server"})
    public void shouldConnectAndSubscribeFalseStart() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connect.reject.packet.too.large/client"})
    @Configure(name = ENGINE_BUFFER_SLOT_CAPACITY_NAME, value = "8192")
    public void shouldRejectPacketTooLarge() throws Exception
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
